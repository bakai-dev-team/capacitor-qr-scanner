import AVFoundation
import Vision
import UIKit

final class QrCodeScanner: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {

    private let session = AVCaptureSession()

    // ✅ ОЧЕРЕДЬ ТОЛЬКО ДЛЯ session begin/commit/start/stop + add/remove input/output
    private let sessionQueue = DispatchQueue(label: "qr.session.queue")

    // ✅ ОЧЕРЕДЬ ТОЛЬКО ДЛЯ sampleBuffer/Vision
    private let videoQueue = DispatchQueue(label: "qr.video.queue")

    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var overlay: QRScanOverlayView?
    private var paused = false
    private var currentDevice: AVCaptureDevice?

    // чтобы не было гонок start/stop подряд
    private var startStopToken = UUID()

    var onResult: (([VNBarcodeObservation]) -> Void)?
    var onError: ((String) -> Void)?

    private func attachOverlay(to previewView: UIView) {
        overlay?.removeFromSuperview()

        let ov = QRScanOverlayView(frame: previewView.bounds)
        ov.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        previewView.addSubview(ov)

        overlay = ov
        ov.startAnimating()
    }

    // MARK: - Start

    func start(previewView: UIView, lens: String, resolution: Int) throws {
        let token = UUID()
        startStopToken = token

        paused = false

        // lens
        let position: AVCaptureDevice.Position = (lens == "FRONT") ? .front : .back

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) else {
            throw NSError(domain: "QrCodeScanner", code: 1, userInfo: [NSLocalizedDescriptionKey: "No camera device"])
        }

        currentDevice = device

        sessionQueue.async { [weak self, weak previewView] in
            guard let self = self else { return }
            guard self.startStopToken == token else { return }

            // 1) CONFIGURE (begin/commit) — НИКАКИХ start/stop внутри
            self.session.beginConfiguration()

            // preset
            switch resolution {
            case 0:
                self.session.sessionPreset = .vga640x480
            case 2:
                self.session.sessionPreset = .hd1920x1080
            case 3:
                if self.session.canSetSessionPreset(.hd4K3840x2160) {
                    self.session.sessionPreset = .hd4K3840x2160
                } else {
                    self.session.sessionPreset = .hd1920x1080
                }
            default:
                self.session.sessionPreset = .hd1280x720
            }

            // clear old
            for input in self.session.inputs { self.session.removeInput(input) }
            for output in self.session.outputs { self.session.removeOutput(output) }

            // input
            do {
                let input = try AVCaptureDeviceInput(device: device)
                guard self.session.canAddInput(input) else {
                    self.session.commitConfiguration()
                    DispatchQueue.main.async { [weak self] in self?.onError?("Cannot add camera input") }
                    return
                }
                self.session.addInput(input)
            } catch {
                self.session.commitConfiguration()
                DispatchQueue.main.async { [weak self] in self?.onError?(error.localizedDescription) }
                return
            }

            // output
            let output = AVCaptureVideoDataOutput()
            output.alwaysDiscardsLateVideoFrames = true
            output.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
            ]
            output.setSampleBufferDelegate(self, queue: self.videoQueue)

            guard self.session.canAddOutput(output) else {
                self.session.commitConfiguration()
                DispatchQueue.main.async { [weak self] in self?.onError?("Cannot add camera output") }
                return
            }
            self.session.addOutput(output)

            // ✅ commit ДО startRunning
            self.session.commitConfiguration()

            // 2) START — только после commit
            if self.startStopToken != token { return }
            if !self.session.isRunning {
                self.session.startRunning()
            }

            // 3) UI — строго main
            DispatchQueue.main.async { [weak self, weak previewView] in
                guard let self = self, let previewView = previewView else { return }
                guard self.startStopToken == token else { return }

                let layer = AVCaptureVideoPreviewLayer(session: self.session)
                layer.videoGravity = .resizeAspectFill
                layer.frame = previewView.bounds

                previewView.layer.sublayers?.forEach { $0.removeFromSuperlayer() }
                previewView.layer.addSublayer(layer)
                self.previewLayer = layer

                self.attachOverlay(to: previewView)
            }
        }
    }


    func updatePreviewFrame(_ frame: CGRect) {
        DispatchQueue.main.async { [weak self] in
            self?.previewLayer?.frame = frame
            self?.overlay?.frame = frame
        }
    }

    // MARK: - Stop / Pause / Resume

    func stop() {
        paused = true
        let token = UUID()
        startStopToken = token

        // ✅ stopRunning только на sessionQueue — тогда он никогда не попадёт внутрь begin/commit
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            if self.session.isRunning {
                self.session.stopRunning()
            }
        }

        // UI чистим на main
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            self.previewLayer?.removeFromSuperlayer()
            self.previewLayer = nil

            self.overlay?.stopAnimating()
            self.overlay?.removeFromSuperview()
            self.overlay = nil
        }
    }

    func pause() { paused = true }
    func resume() { paused = false }

    // MARK: - Torch

    func isTorchAvailable() -> Bool { currentDevice?.hasTorch == true }
    func isTorchEnabled() -> Bool { currentDevice?.torchMode == .on }

    func enableTorch() {
        guard let device = currentDevice, device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            device.torchMode = .on
            device.unlockForConfiguration()
        } catch { onError?("Torch error") }
    }

    func disableTorch() {
        guard let device = currentDevice, device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            device.torchMode = .off
            device.unlockForConfiguration()
        } catch { onError?("Torch error") }
    }

    func toggleTorch() {
        guard let device = currentDevice, device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            device.torchMode = (device.torchMode == .on) ? .off : .on
            device.unlockForConfiguration()
        } catch { onError?("Torch error") }
    }

    // MARK: - Zoom

    func setZoom(_ ratio: CGFloat) {
        guard let device = currentDevice else { return }
        let maxZoom = device.activeFormat.videoMaxZoomFactor
        let clamped = min(max(1.0, ratio), maxZoom)
        do {
            try device.lockForConfiguration()
            device.videoZoomFactor = clamped
            device.unlockForConfiguration()
        } catch { onError?("Zoom error") }
    }

    func getZoomRatio() -> CGFloat { currentDevice?.videoZoomFactor ?? 1.0 }
    func getMinZoomRatio() -> CGFloat { 1.0 }
    func getMaxZoomRatio() -> CGFloat { currentDevice?.activeFormat.videoMaxZoomFactor ?? 1.0 }

    // MARK: - Capture output (Vision)

    func captureOutput(_ output: AVCaptureOutput,
                       didOutput sampleBuffer: CMSampleBuffer,
                       from connection: AVCaptureConnection) {
        if paused { return }

        let request = VNDetectBarcodesRequest { [weak self] req, err in
            if let err = err {
                self?.onError?(err.localizedDescription)
                return
            }
            let results = req.results as? [VNBarcodeObservation] ?? []
            self?.onResult?(results)
        }

        let handler = VNImageRequestHandler(cmSampleBuffer: sampleBuffer, orientation: .up)
        do { try handler.perform([request]) }
        catch { onError?(error.localizedDescription) }
    }
}

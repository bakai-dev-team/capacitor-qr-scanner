import AVFoundation
import Vision
import UIKit

final class QrCodeScanner: NSObject {

    private let session = AVCaptureSession()

    private let sessionQueue = DispatchQueue(label: "qr.session.queue")
    private let videoQueue = DispatchQueue(label: "qr.video.queue")

    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var overlay: QRScanOverlayView?

    private var paused = false
    private var currentDevice: AVCaptureDevice?
    private var currentPosition: AVCaptureDevice.Position = .back

    private var startStopToken = UUID()

    // Video output (Vision)
    private var videoOutput: AVCaptureVideoDataOutput?
    private var videoConnection: AVCaptureConnection?

    // Photo output (Freeze)
    private var photoOutput: AVCapturePhotoOutput?
    private var isCapturingFreezePhoto = false
    private var pendingDisableVideoAfterPhoto = false

    // Freeze UI
    private var freezeView: UIImageView?

    // ✅ SAFETY: configuration depth (0 => safe to stopRunning)
    private var configDepth: Int = 0
    private var pendingStop: Bool = false

    // ✅ ensure stopRunning is never called in same tick as begin/commit
    private var stopScheduled: Bool = false

    var onResult: (([VNBarcodeObservation]) -> Void)?
    var onError: ((String) -> Void)?

    // MARK: - Overlay

    private func attachOverlay(to previewView: UIView) {
        overlay?.removeFromSuperview()

        let ov = QRScanOverlayView(frame: previewView.bounds)
        ov.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        previewView.addSubview(ov)

        overlay = ov
        ov.startAnimating()
    }

    // MARK: - StopRunning scheduler (✅ no crash on iOS 26)

    // Must be called ONLY on sessionQueue
    private func scheduleStopRunning() {
        if stopScheduled { return }
        stopScheduled = true

        sessionQueue.async { [weak self] in
            guard let self = self else { return }

            // Still configuring -> retry later
            if self.configDepth > 0 {
                self.stopScheduled = false
                self.pendingStop = true
                self.scheduleStopRunning()
                return
            }

            self.stopScheduled = false
            self.pendingStop = false

            if self.session.isRunning {
                self.session.stopRunning()
            }

            self.videoConnection = nil
            self.videoOutput = nil
            self.photoOutput = nil
        }
    }

    // MARK: - Start

    func start(previewView: UIView, lens: String, resolution: Int) throws {
        let token = UUID()
        startStopToken = token

        paused = false
        isCapturingFreezePhoto = false
        pendingDisableVideoAfterPhoto = false

        let position: AVCaptureDevice.Position = (lens == "FRONT") ? .front : .back
        currentPosition = position

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) else {
            throw NSError(domain: "QrCodeScanner", code: 1, userInfo: [NSLocalizedDescriptionKey: "No camera device"])
        }
        currentDevice = device

        sessionQueue.async { [weak self, weak previewView] in
            guard let self = self else { return }
            guard self.startStopToken == token else { return }

            // ✅ BEGIN CONFIGURE (commit guaranteed)
            self.configDepth += 1
            self.session.beginConfiguration()

            defer {
                self.session.commitConfiguration()
                self.configDepth = max(0, self.configDepth - 1)

                // ✅ if stop requested during configuration, stop safely AFTER commit (next tick)
                if self.pendingStop && self.configDepth == 0 {
                    self.scheduleStopRunning()
                }
            }

            // preset
            switch resolution {
            case 0: self.session.sessionPreset = .vga640x480
            case 2: self.session.sessionPreset = .hd1920x1080
            case 3:
                if self.session.canSetSessionPreset(.hd4K3840x2160) {
                    self.session.sessionPreset = .hd4K3840x2160
                } else {
                    self.session.sessionPreset = .hd1920x1080
                }
            default: self.session.sessionPreset = .hd1280x720
            }

            // clear old
            for input in self.session.inputs { self.session.removeInput(input) }
            for output in self.session.outputs { self.session.removeOutput(output) }

            // input
            do {
                let input = try AVCaptureDeviceInput(device: device)
                guard self.session.canAddInput(input) else {
                    DispatchQueue.main.async { [weak self] in self?.onError?("Cannot add camera input") }
                    return
                }
                self.session.addInput(input)
            } catch {
                DispatchQueue.main.async { [weak self] in self?.onError?(error.localizedDescription) }
                return
            }

            // video output (Vision)
            let vOut = AVCaptureVideoDataOutput()
            vOut.alwaysDiscardsLateVideoFrames = true
            vOut.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
            vOut.setSampleBufferDelegate(self, queue: self.videoQueue)

            guard self.session.canAddOutput(vOut) else {
                DispatchQueue.main.async { [weak self] in self?.onError?("Cannot add camera output") }
                return
            }
            self.session.addOutput(vOut)
            self.videoOutput = vOut
            self.videoConnection = vOut.connection(with: .video)
            self.videoConnection?.isEnabled = true

            // photo output (Freeze)
            let pOut = AVCapturePhotoOutput()
            guard self.session.canAddOutput(pOut) else {
                DispatchQueue.main.async { [weak self] in self?.onError?("Cannot add photo output") }
                return
            }
            self.session.addOutput(pOut)
            self.photoOutput = pOut

            // ✅ startRunning must be after commit; schedule next tick
            self.sessionQueue.async { [weak self, weak previewView] in
                guard let self = self else { return }
                guard self.startStopToken == token else { return }
                guard self.configDepth == 0 else { return }

                if !self.session.isRunning {
                    self.session.startRunning()
                }

                DispatchQueue.main.async { [weak self, weak previewView] in
                    guard let self = self, let previewView = previewView else { return }
                    guard self.startStopToken == token else { return }

                    let layer = AVCaptureVideoPreviewLayer(session: self.session)
                    layer.videoGravity = .resizeAspectFill
                    layer.frame = previewView.bounds

                    // portrait-only
                    if let c = layer.connection, c.isVideoOrientationSupported {
                        c.videoOrientation = .portrait
                    }
                    // mirror preview for front
                    if let c = layer.connection, c.isVideoMirroringSupported {
                        c.automaticallyAdjustsVideoMirroring = false
                        c.isVideoMirrored = (self.currentPosition == .front)
                    }

                    previewView.layer.sublayers?.forEach { $0.removeFromSuperlayer() }
                    previewView.layer.addSublayer(layer)
                    self.previewLayer = layer

                    self.attachOverlay(to: previewView)
                }
            }
        }
    }

    func updatePreviewFrame(_ frame: CGRect) {
        DispatchQueue.main.async { [weak self] in
            self?.previewLayer?.frame = frame
            self?.overlay?.frame = frame
            self?.freezeView?.frame = frame
        }
    }

    // MARK: - Stop (✅ no crash)

    func stop() {
        paused = true
        startStopToken = UUID()

        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            self.pendingStop = true
            self.scheduleStopRunning()
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            self.previewLayer?.removeFromSuperlayer()
            self.previewLayer = nil

            self.overlay?.stopAnimating()
            self.overlay?.removeFromSuperview()
            self.overlay = nil

            self.freezeView?.removeFromSuperview()
            self.freezeView = nil
        }
    }

    // MARK: - Pause / Resume (no stopRunning/startRunning)

    func pause(previewHostView: UIView?) {
        paused = true

        DispatchQueue.main.async { [weak self, weak previewHostView] in
            guard let self = self, let host = previewHostView else { return }

            self.overlay?.pauseAnimating()

            if self.freezeView == nil {
                let iv = UIImageView(frame: host.bounds)
                iv.autoresizingMask = [.flexibleWidth, .flexibleHeight]
                iv.contentMode = .scaleAspectFill
                iv.isUserInteractionEnabled = false
                iv.backgroundColor = .clear
                host.addSubview(iv)
                self.freezeView = iv
            }
            self.freezeView?.isHidden = false

            self.captureFreezePhoto()
        }
    }

    func resume() {
        sessionQueue.async { [weak self] in
            self?.videoConnection?.isEnabled = true
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.freezeView?.image = nil
            self.freezeView?.isHidden = true
            self.overlay?.resumeAnimating()
        }

        paused = false
    }

    private func captureFreezePhoto() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            guard !self.isCapturingFreezePhoto else { return }
            guard let pOut = self.photoOutput else { return }

            self.isCapturingFreezePhoto = true
            self.pendingDisableVideoAfterPhoto = true

            let settings = AVCapturePhotoSettings()
            settings.flashMode = .off
            pOut.capturePhoto(with: settings, delegate: self)
        }
    }

    // MARK: - Torch / Zoom

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
}

// MARK: - Vision frames

extension QrCodeScanner: AVCaptureVideoDataOutputSampleBufferDelegate {

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

// MARK: - Freeze photo

extension QrCodeScanner: AVCapturePhotoCaptureDelegate {

    func photoOutput(_ output: AVCapturePhotoOutput,
                     didFinishProcessingPhoto photo: AVCapturePhoto,
                     error: Error?) {

        defer {
            sessionQueue.async { [weak self] in
                guard let self = self else { return }
                self.isCapturingFreezePhoto = false

                // after freeze, disable video frames (CPU 0)
                if self.pendingDisableVideoAfterPhoto {
                    self.pendingDisableVideoAfterPhoto = false
                    self.videoConnection?.isEnabled = false
                }
            }
        }

        if let error = error {
            DispatchQueue.main.async { [weak self] in self?.onError?(error.localizedDescription) }
            return
        }

        guard let data = photo.fileDataRepresentation(),
              let img = UIImage(data: data) else {
            DispatchQueue.main.async { [weak self] in self?.onError?("Freeze photo failed") }
            return
        }

        DispatchQueue.main.async { [weak self] in
            self?.freezeView?.image = img
        }
    }
}

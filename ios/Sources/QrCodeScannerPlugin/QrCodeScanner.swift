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

    // Freeze UI
    private var freezeView: UIImageView?

    var onResult: (([VNBarcodeObservation]) -> Void)?
    var onError: ((String) -> Void)?

    // =========================
    // OPT: Vision reuse + throttle + no parallel
    // =========================
    private let sequenceHandler = VNSequenceRequestHandler()
    private var detectRequest: VNDetectBarcodesRequest?
    private var isProcessingFrame = false
    private var lastProcessTime = CFAbsoluteTimeGetCurrent()
    private var minProcessInterval: CFTimeInterval = 1.0 / 12.0 // 12fps обработки

    // =========================
    // DOUBLE BUFFER for freeze (STRICTLY last QR-detect)
    // =========================
    // Два "последних успешных детекта"
    private var detectedImages: [UIImage?] = [nil, nil]
    private var detectedIndex: Int = 0
    private var hasDetectedAtLeastOnce: Bool = false

    // Последний видеокадр (fallback если детекта ещё не было)
    private var lastFrameImage: UIImage?

    // CIContext держим 1 раз
    private let ciContext = CIContext(options: nil)

    // MARK: - Overlay

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

        // reset perf state
        isProcessingFrame = false
        lastProcessTime = CFAbsoluteTimeGetCurrent()

        // reset buffers
        detectedImages = [nil, nil]
        detectedIndex = 0
        hasDetectedAtLeastOnce = false
        lastFrameImage = nil

        // init request once
        let req = VNDetectBarcodesRequest { [weak self] req, err in
            guard let self = self else { return }
            self.isProcessingFrame = false

            if let err = err {
                self.onError?(err.localizedDescription)
                return
            }

            let results = req.results as? [VNBarcodeObservation] ?? []

            // ✅ Привязка кадра строго к детекту:
            // если найдено хотя бы 1 — сохраняем текущий lastFrameImage в double-buffer
            if !results.isEmpty, let img = self.lastFrameImage {
                self.detectedIndex = 1 - self.detectedIndex
                self.detectedImages[self.detectedIndex] = img
                self.hasDetectedAtLeastOnce = true
            }

            self.onResult?(results)
        }

        // Если нужен только QR — можно ускорить:
        // req.symbologies = [.QR]
        detectRequest = req

        let position: AVCaptureDevice.Position = (lens == "FRONT") ? .front : .back
        currentPosition = position

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) else {
            throw NSError(domain: "QrCodeScanner", code: 1, userInfo: [NSLocalizedDescriptionKey: "No camera device"])
        }
        currentDevice = device

        sessionQueue.async { [weak self, weak previewView] in
            guard let self = self else { return }
            guard self.startStopToken == token else { return }

            self.session.beginConfiguration()
            defer { self.session.commitConfiguration() }

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

            // clear old graph
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

            vOut.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
            ]

            vOut.setSampleBufferDelegate(self, queue: self.videoQueue)

            guard self.session.canAddOutput(vOut) else {
                DispatchQueue.main.async { [weak self] in self?.onError?("Cannot add camera output") }
                return
            }
            self.session.addOutput(vOut)

            self.videoOutput = vOut
            self.videoConnection = vOut.connection(with: .video)
            if let c = self.videoConnection {
                c.isEnabled = true
                if c.isVideoOrientationSupported { c.videoOrientation = .portrait }
                if c.isVideoMirroringSupported {
                    c.automaticallyAdjustsVideoMirroring = false
                    c.isVideoMirrored = (self.currentPosition == .front)
                }
            }

            // start running + UI
            self.sessionQueue.async { [weak self, weak previewView] in
                guard let self = self else { return }
                guard self.startStopToken == token else { return }

                if !self.session.isRunning {
                    self.session.startRunning()
                }

                DispatchQueue.main.async { [weak self, weak previewView] in
                    guard let self = self, let previewView = previewView else { return }
                    guard self.startStopToken == token else { return }

                    let layer = AVCaptureVideoPreviewLayer(session: self.session)
                    layer.videoGravity = .resizeAspectFill
                    layer.frame = previewView.bounds

                    if let c = layer.connection, c.isVideoOrientationSupported {
                        c.videoOrientation = .portrait
                    }
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

    // MARK: - Stop

    func stop() {
        paused = true
        startStopToken = UUID()

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

        sessionQueue.async { [weak self] in
            guard let self = self else { return }

            self.videoConnection?.isEnabled = false
            self.videoConnection = nil
            self.videoOutput = nil

            self.detectRequest = nil
            self.isProcessingFrame = false

            self.detectedImages = [nil, nil]
            self.lastFrameImage = nil
            self.hasDetectedAtLeastOnce = false

            self.session.beginConfiguration()
            defer { self.session.commitConfiguration() }

            for output in self.session.outputs { self.session.removeOutput(output) }
            for input in self.session.inputs { self.session.removeInput(input) }

            self.currentDevice = nil
        }
    }

    // MARK: - Pause / Resume

    func pause(previewHostView: UIView?) {
        paused = true

        // 1) Выключаем поток кадров (CPU ~ 0)
        sessionQueue.async { [weak self] in
            self?.videoConnection?.isEnabled = false
            self?.isProcessingFrame = false
        }

        // 2) Freeze: строго последний QR-детект, иначе fallback
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

            let detected = self.detectedImages[self.detectedIndex]
            let fallback = self.lastFrameImage

            self.freezeView?.image = detected ?? fallback
            self.freezeView?.isHidden = false
        }
    }

    func resume() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            self.videoConnection?.isEnabled = true
            self.isProcessingFrame = false
            self.lastProcessTime = CFAbsoluteTimeGetCurrent()
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.freezeView?.image = nil
            self.freezeView?.isHidden = true
            self.overlay?.resumeAnimating()
        }

        paused = false
    }

    // MARK: - Torch / Zoom

    func isTorchAvailable() -> Bool { currentDevice?.hasTorch == true }
    func isTorchEnabled() -> Bool { currentDevice?.torchMode == .on }

    func enableTorch() {
        sessionQueue.async { [weak self] in
            guard let self = self, let device = self.currentDevice, device.hasTorch else { return }
            do {
                try device.lockForConfiguration()
                device.torchMode = .on
                device.unlockForConfiguration()
            } catch { DispatchQueue.main.async { [weak self] in self?.onError?("Torch error") } }
        }
    }

    func disableTorch() {
        sessionQueue.async { [weak self] in
            guard let self = self, let device = self.currentDevice, device.hasTorch else { return }
            do {
                try device.lockForConfiguration()
                device.torchMode = .off
                device.unlockForConfiguration()
            } catch { DispatchQueue.main.async { [weak self] in self?.onError?("Torch error") } }
        }
    }

    func toggleTorch() {
        sessionQueue.async { [weak self] in
            guard let self = self, let device = self.currentDevice, device.hasTorch else { return }
            do {
                try device.lockForConfiguration()
                device.torchMode = (device.torchMode == .on) ? .off : .on
                device.unlockForConfiguration()
            } catch { DispatchQueue.main.async { [weak self] in self?.onError?("Torch error") } }
        }
    }

    func setZoom(_ ratio: CGFloat) {
        sessionQueue.async { [weak self] in
            guard let self = self, let device = self.currentDevice else { return }
            let maxZoom = device.activeFormat.videoMaxZoomFactor
            let clamped = min(max(1.0, ratio), maxZoom)
            do {
                try device.lockForConfiguration()
                device.videoZoomFactor = clamped
                device.unlockForConfiguration()
            } catch {
                DispatchQueue.main.async { [weak self] in self?.onError?("Zoom error") }
            }
        }
    }

    func getZoomRatio() -> CGFloat { currentDevice?.videoZoomFactor ?? 1.0 }
    func getMinZoomRatio() -> CGFloat { 1.0 }
    func getMaxZoomRatio() -> CGFloat { currentDevice?.activeFormat.videoMaxZoomFactor ?? 1.0 }

    // MARK: - Frame to UIImage (FIX: no extra rotation)

    private func makeUIImage(from sampleBuffer: CMSampleBuffer) -> UIImage? {
        guard let pb = CMSampleBufferGetImageBuffer(sampleBuffer) else { return nil }
        let ci = CIImage(cvPixelBuffer: pb)
        guard let cg = ciContext.createCGImage(ci, from: ci.extent) else { return nil }

        // ✅ ВАЖНО: .up — иначе получишь +90° относительно previewLayer (portrait)
        // Зеркалирование фронта уже делает previewLayer, а freeze нам нужен "как на экране"
        return UIImage(cgImage: cg, scale: UIScreen.main.scale, orientation: .up)
    }
}

// MARK: - Vision frames

extension QrCodeScanner: AVCaptureVideoDataOutputSampleBufferDelegate {

    func captureOutput(_ output: AVCaptureOutput,
                       didOutput sampleBuffer: CMSampleBuffer,
                       from connection: AVCaptureConnection) {

        if paused { return }
        guard let request = detectRequest else { return }

        let now = CFAbsoluteTimeGetCurrent()
        if isProcessingFrame { return }
        if (now - lastProcessTime) < minProcessInterval { return }

        isProcessingFrame = true
        lastProcessTime = now

        // Кадр этого прогона — будет привязан к детекту
        if let img = makeUIImage(from: sampleBuffer) {
            lastFrameImage = img
        }

        do {
            // previewLayer уже в portrait, поэтому Vision оставляем .up
            try sequenceHandler.perform([request], on: sampleBuffer, orientation: .up)
        } catch {
            isProcessingFrame = false
            onError?(error.localizedDescription)
        }
    }
}

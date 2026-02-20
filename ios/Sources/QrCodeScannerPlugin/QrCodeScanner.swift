import AVFoundation
import Vision
import UIKit
import ImageIO

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
    private var minProcessInterval: CFTimeInterval = 1.0 / 18.0 // faster reaction for blurred handheld scans

    // fallback and adaptive zoom for difficult branded QR
    private var consecutiveDecodeMisses = 0
    private var lastEnhancedAttemptTime = CFAbsoluteTimeGetCurrent()
    private var lastAutoZoomAt = CFAbsoluteTimeGetCurrent()
    private var manualZoomLocked = false

    // =========================
    // DOUBLE BUFFER for freeze (STRICTLY last QR-detect)
    // =========================
    private var detectedImages: [UIImage?] = [nil, nil]
    private var detectedIndex: Int = 0
    private var hasDetectedAtLeastOnce: Bool = false

    private var lastFrameImage: UIImage?

    private let ciContext = CIContext(options: nil)

    // =========================
    // TORCH STATE (FIX)
    // =========================
    // Держим своё согласованное состояние (обновляется только в sessionQueue)
    private var torchEnabled: Bool = false

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
        consecutiveDecodeMisses = 0
        lastEnhancedAttemptTime = CFAbsoluteTimeGetCurrent()
        lastAutoZoomAt = CFAbsoluteTimeGetCurrent()
        manualZoomLocked = false

        // reset buffers
        detectedImages = [nil, nil]
        detectedIndex = 0
        hasDetectedAtLeastOnce = false
        lastFrameImage = nil

        // reset torch cached state
        torchEnabled = false

        detectRequest = createQrDetectRequest()

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

            self.configureDeviceForScan(device)

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

    func stop(completion: (() -> Void)? = nil) {
        paused = true
        startStopToken = UUID()

        sessionQueue.async { [weak self] in
            guard let self = self else {
                DispatchQueue.main.async {
                    completion?()
                }
                return
            }

            // Stop frame callbacks first to avoid race with teardown.
            self.videoOutput?.setSampleBufferDelegate(nil, queue: nil)
            self.videoConnection?.isEnabled = false
            self.videoConnection = nil

            if self.session.isRunning {
                self.session.stopRunning()
            }

            self.detectRequest = nil
            self.isProcessingFrame = false
            self.consecutiveDecodeMisses = 0

            self.detectedImages = [nil, nil]
            self.lastFrameImage = nil
            self.hasDetectedAtLeastOnce = false

            self.torchEnabled = false

            self.session.beginConfiguration()
            defer { self.session.commitConfiguration() }

            for output in self.session.outputs { self.session.removeOutput(output) }
            for input in self.session.inputs { self.session.removeInput(input) }

            self.videoOutput = nil
            self.currentDevice = nil

            DispatchQueue.main.async { [weak self] in
                guard let self = self else {
                    completion?()
                    return
                }

                self.previewLayer?.removeFromSuperlayer()
                self.previewLayer = nil

                self.overlay?.stopAnimating()
                self.overlay?.removeFromSuperview()
                self.overlay = nil

                self.freezeView?.removeFromSuperview()
                self.freezeView = nil

                completion?()
            }
        }
    }

    // MARK: - Pause / Resume

    func pause(previewHostView: UIView?) {
        paused = true

        // 1) Выключаем поток кадров (CPU ~ 0)
        sessionQueue.async { [weak self] in
            self?.videoConnection?.isEnabled = false
            self?.isProcessingFrame = false
            self?.consecutiveDecodeMisses = 0
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
            self.consecutiveDecodeMisses = 0
            self.lastEnhancedAttemptTime = CFAbsoluteTimeGetCurrent()
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.freezeView?.image = nil
            self.freezeView?.isHidden = true
            self.overlay?.resumeAnimating()
        }

        paused = false
    }

    // MARK: - Torch / Zoom (FIXED)

    func isTorchAvailable() -> Bool { currentDevice?.hasTorch == true }

    /// Возвращаем кэш (согласованное состояние)
    func isTorchEnabled() -> Bool { torchEnabled }

    /// Включить torch и вызвать completion когда реально применилось
    func enableTorch(completion: (() -> Void)? = nil) {
        sessionQueue.async { [weak self] in
            guard let self = self, let device = self.currentDevice, device.hasTorch else {
                DispatchQueue.main.async { completion?() }
                return
            }
            do {
                try device.lockForConfiguration()
                try device.setTorchModeOn(level: AVCaptureDevice.maxAvailableTorchLevel)
                device.unlockForConfiguration()
                self.torchEnabled = true
            } catch {
                self.torchEnabled = false
                DispatchQueue.main.async { [weak self] in self?.onError?("Torch error") }
            }
            DispatchQueue.main.async { completion?() }
        }
    }

    func disableTorch(completion: (() -> Void)? = nil) {
        sessionQueue.async { [weak self] in
            guard let self = self, let device = self.currentDevice, device.hasTorch else {
                DispatchQueue.main.async { completion?() }
                return
            }
            do {
                try device.lockForConfiguration()
                device.torchMode = .off
                device.unlockForConfiguration()
                self.torchEnabled = false
            } catch {
                DispatchQueue.main.async { [weak self] in self?.onError?("Torch error") }
            }
            DispatchQueue.main.async { completion?() }
        }
    }

    func toggleTorch(completion: (() -> Void)? = nil) {
        if torchEnabled {
            disableTorch(completion: completion)
        } else {
            enableTorch(completion: completion)
        }
    }

    // MARK: - Zoom

    func setZoom(_ ratio: CGFloat) {
        sessionQueue.async { [weak self] in
            guard let self = self, let device = self.currentDevice else { return }
            let maxZoom = device.activeFormat.videoMaxZoomFactor
            let clamped = min(max(1.0, ratio), maxZoom)
            do {
                try device.lockForConfiguration()
                device.videoZoomFactor = clamped
                device.unlockForConfiguration()
                self.manualZoomLocked = true
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

        // ✅ .up — иначе получишь +90° относительно previewLayer (portrait)
        return UIImage(cgImage: cg, scale: UIScreen.main.scale, orientation: .up)
    }

    private func createQrDetectRequest() -> VNDetectBarcodesRequest {
        let request = VNDetectBarcodesRequest()
        request.symbologies = [.qr]

        if #available(iOS 16.0, *) {
            request.revision = VNDetectBarcodesRequestRevision3
        }

        return request
    }

    private func configureDeviceForScan(_ device: AVCaptureDevice) {
        do {
            try device.lockForConfiguration()

            if device.isFocusModeSupported(.continuousAutoFocus) {
                device.focusMode = .continuousAutoFocus
            }
            if device.isSmoothAutoFocusSupported {
                device.isSmoothAutoFocusEnabled = true
            }
            if device.isExposureModeSupported(.continuousAutoExposure) {
                device.exposureMode = .continuousAutoExposure
            }
            if device.isLowLightBoostSupported {
                device.automaticallyEnablesLowLightBoostWhenAvailable = true
            }
            if device.hasTorch {
                device.torchMode = .off
            }

            device.unlockForConfiguration()
        } catch {}

        torchEnabled = false
    }

    private func orientationForVision(from connection: AVCaptureConnection) -> CGImagePropertyOrientation {
        let orientation = connection.videoOrientation
        let isFront = (currentPosition == .front)

        switch orientation {
        case .portrait:
            return isFront ? .leftMirrored : .right
        case .portraitUpsideDown:
            return isFront ? .rightMirrored : .left
        case .landscapeLeft:
            return isFront ? .downMirrored : .up
        case .landscapeRight:
            return isFront ? .upMirrored : .down
        @unknown default:
            return isFront ? .leftMirrored : .right
        }
    }

    private func shouldRunEnhancedPass(now: CFAbsoluteTime) -> Bool {
        guard consecutiveDecodeMisses >= 2 else { return false }
        if (now - lastEnhancedAttemptTime) < 0.20 { return false }
        lastEnhancedAttemptTime = now
        return true
    }

    private func runEnhancedDetection(
        from sampleBuffer: CMSampleBuffer,
        orientation: CGImagePropertyOrientation
    ) -> [VNBarcodeObservation] {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return [] }

        var ciImage = CIImage(cvPixelBuffer: pixelBuffer)

        if let colorControls = CIFilter(name: "CIColorControls") {
            colorControls.setValue(ciImage, forKey: kCIInputImageKey)
            colorControls.setValue(1.45, forKey: kCIInputContrastKey)
            colorControls.setValue(0.03, forKey: kCIInputBrightnessKey)
            colorControls.setValue(0.0, forKey: kCIInputSaturationKey)
            if let output = colorControls.outputImage {
                ciImage = output
            }
        }

        if let sharpen = CIFilter(name: "CISharpenLuminance") {
            sharpen.setValue(ciImage, forKey: kCIInputImageKey)
            sharpen.setValue(0.65, forKey: kCIInputSharpnessKey)
            if let output = sharpen.outputImage {
                ciImage = output
            }
        }

        guard let cgImage = ciContext.createCGImage(ciImage, from: ciImage.extent) else { return [] }

        let request = createQrDetectRequest()
        let handler = VNImageRequestHandler(cgImage: cgImage, orientation: orientation, options: [:])

        do {
            try handler.perform([request])
            return request.results as? [VNBarcodeObservation] ?? []
        } catch {
            return []
        }
    }

    private func maybeAutoZoom(now: CFAbsoluteTime) {
        guard !manualZoomLocked else { return }
        guard consecutiveDecodeMisses >= 10 else { return }
        guard (now - lastAutoZoomAt) >= 0.6 else { return }
        guard let device = currentDevice else { return }
        guard currentPosition == .back else { return }

        let maxZoom = min(device.activeFormat.videoMaxZoomFactor, 3.0)
        let current = device.videoZoomFactor
        guard current < (maxZoom - 0.01) else { return }

        let target = min(maxZoom, current + 0.18)
        do {
            try device.lockForConfiguration()
            device.videoZoomFactor = target
            device.unlockForConfiguration()
            lastAutoZoomAt = now
        } catch {}
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
        defer { isProcessingFrame = false }

        // Кадр этого прогона — будет привязан к детекту
        if let img = makeUIImage(from: sampleBuffer) {
            lastFrameImage = img
        }

        let orientation = orientationForVision(from: connection)
        var results: [VNBarcodeObservation] = []

        do {
            try sequenceHandler.perform([request], on: sampleBuffer, orientation: orientation)
            results = request.results as? [VNBarcodeObservation] ?? []
        } catch {
            onError?(error.localizedDescription)
            return
        }

        if results.isEmpty && shouldRunEnhancedPass(now: now) {
            results = runEnhancedDetection(from: sampleBuffer, orientation: orientation)
        }

        if !results.isEmpty {
            consecutiveDecodeMisses = 0
            if let img = lastFrameImage {
                detectedIndex = 1 - detectedIndex
                detectedImages[detectedIndex] = img
                hasDetectedAtLeastOnce = true
            }
        } else {
            consecutiveDecodeMisses += 1
            maybeAutoZoom(now: now)
        }

        onResult?(results)
    }
}

import Capacitor
import AVFoundation
import UIKit
import Vision
import CoreImage

@objc(QrCodeScannerPlugin)
public class QrCodeScannerPlugin: CAPPlugin, CAPBridgedPlugin {

    public let identifier = "QrCodeScannerPlugin"
    public let jsName = "QrCodeScanner"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "startScan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopScan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "pauseScan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "resumeScan", returnType: CAPPluginReturnPromise),

        CAPPluginMethod(name: "readBarcodesFromImage", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "scan", returnType: CAPPluginReturnPromise),

        CAPPluginMethod(name: "enableTorch", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "disableTorch", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "toggleTorch", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isTorchEnabled", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isTorchAvailable", returnType: CAPPluginReturnPromise),

        CAPPluginMethod(name: "setZoomRatio", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getZoomRatio", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getMinZoomRatio", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getMaxZoomRatio", returnType: CAPPluginReturnPromise),

        CAPPluginMethod(name: "openSettings", returnType: CAPPluginReturnPromise),

        CAPPluginMethod(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise)
    ]

    private let scanner = QrCodeScanner()
    private let ciContext = CIContext(options: nil)
    private var previewView: UIView?
    private var originalWebViewIsOpaque: Bool?
    private var originalWebViewBackgroundColor: UIColor?
    private var originalWebViewScrollBackgroundColor: UIColor?

    @objc func startScan(_ call: CAPPluginCall) {
        let options = mergedOptions(from: call)
        let lens = (options["lensFacing"] as? String) ?? "BACK"

        let resolution: Int = {
            if let v = options["resolution"] as? Int { return v }
            if let v = options["resolution"] as? Double { return Int(v) }
            if let v = options["resolution"] as? NSNumber { return v.intValue }
            return 2
        }()

        DispatchQueue.main.async {
            guard let bridge = self.bridge,
                  let webView = bridge.webView,
                  let container = webView.superview else {
                call.reject("Bridge/WebView not available")
                return
            }

            if self.originalWebViewIsOpaque == nil {
                self.originalWebViewIsOpaque = webView.isOpaque
                self.originalWebViewBackgroundColor = webView.backgroundColor
                self.originalWebViewScrollBackgroundColor = webView.scrollView.backgroundColor
            }

            webView.isOpaque = false
            webView.backgroundColor = .clear
            webView.scrollView.backgroundColor = .clear

            self.previewView?.removeFromSuperview()

            let pv = UIView(frame: container.bounds)
            pv.backgroundColor = .clear
            pv.isUserInteractionEnabled = false
            self.previewView = pv

            let webIndex = container.subviews.firstIndex(of: webView) ?? container.subviews.count
            container.insertSubview(pv, at: max(0, webIndex))

            pv.autoresizingMask = [.flexibleWidth, .flexibleHeight]

            self.scanner.onResult = { [weak self] barcodes in
                guard let self = self else { return }
                if barcodes.isEmpty { return }
                DispatchQueue.main.async {
                    self.notifyListeners("barcodesScanned", data: BarcodeMapper.toJS(barcodes))
                }
            }

            self.scanner.onError = { [weak self] message in
                guard let self = self else { return }
                DispatchQueue.main.async {
                    self.notifyListeners("scanError", data: ["message": message])
                }
            }

            do {
                try self.scanner.start(previewView: pv, lens: lens, resolution: resolution)
                call.resolve()
            } catch {
                call.reject("Camera error")
            }
        }
    }

    @objc func stopScan(_ call: CAPPluginCall) {
        scanner.stop { [weak self] in
            guard let self = self else {
                call.resolve()
                return
            }

            self.previewView?.removeFromSuperview()
            self.previewView = nil

            if let webView = self.bridge?.webView {
                if let originalOpaque = self.originalWebViewIsOpaque {
                    webView.isOpaque = originalOpaque
                }
                webView.backgroundColor = self.originalWebViewBackgroundColor
                webView.scrollView.backgroundColor = self.originalWebViewScrollBackgroundColor
            }

            self.originalWebViewIsOpaque = nil
            self.originalWebViewBackgroundColor = nil
            self.originalWebViewScrollBackgroundColor = nil

            call.resolve()
        }
    }

    @objc func pauseScan(_ call: CAPPluginCall) {
        scanner.pause(previewHostView: previewView)
        call.resolve()
    }

    @objc func resumeScan(_ call: CAPPluginCall) {
        scanner.resume()
        call.resolve()
    }

    // MARK: - Zoom

    @objc func setZoomRatio(_ call: CAPPluginCall) {
        let ratio = call.getFloat("zoomRatio") ?? 1
        scanner.setZoom(CGFloat(ratio))
        call.resolve()
    }

    @objc func getZoomRatio(_ call: CAPPluginCall) {
        call.resolve(["zoomRatio": scanner.getZoomRatio()])
    }

    @objc func getMinZoomRatio(_ call: CAPPluginCall) {
        call.resolve(["zoomRatio": scanner.getMinZoomRatio()])
    }

    @objc func getMaxZoomRatio(_ call: CAPPluginCall) {
        call.resolve(["zoomRatio": scanner.getMaxZoomRatio()])
    }

    // MARK: - Torch (FIXED)

    @objc func enableTorch(_ call: CAPPluginCall) {
        scanner.enableTorch {
            call.resolve(["enabled": self.scanner.isTorchEnabled()])
        }
    }

    @objc func disableTorch(_ call: CAPPluginCall) {
        scanner.disableTorch {
            call.resolve(["enabled": self.scanner.isTorchEnabled()])
        }
    }

    @objc func toggleTorch(_ call: CAPPluginCall) {
        scanner.toggleTorch {
            call.resolve(["enabled": self.scanner.isTorchEnabled()])
        }
    }

    @objc func isTorchEnabled(_ call: CAPPluginCall) {
        call.resolve(["enabled": scanner.isTorchEnabled()])
    }

    @objc func isTorchAvailable(_ call: CAPPluginCall) {
        call.resolve(["available": scanner.isTorchAvailable()])
    }

    // MARK: - Read barcodes from image

    @objc func readBarcodesFromImage(_ call: CAPPluginCall) {

        guard let path = call.getString("path") else {
            call.reject("No path provided")
            return
        }

        guard let sourceURL = normalizeImageURL(path),
              let sourceImage = loadImage(from: sourceURL) else {
            call.reject("Failed to load image")
            return
        }

        let canonical = canonicalImage(sourceImage)
        var candidates: [UIImage] = [canonical]

        if let normalized = normalizedForDecode(canonical) {
            candidates.append(normalized)
        }

        if let boosted = highContrastImage(canonical) {
            candidates.append(boosted)
        }

        if let binary = binaryImage(canonical) {
            candidates.append(binary)
        }

        let center = centeredSquareImage(canonical, ratio: 0.88)
        if let center = center {
            candidates.append(center)
        }

        if let centerBoosted = center.flatMap({ highContrastImage($0) }) {
            candidates.append(centerBoosted)
        }

        for image in candidates {
            let barcodes = detectDecodedBarcodes(in: image)
            if !barcodes.isEmpty {
                call.resolve(BarcodeMapper.toJS(barcodes))
                return
            }
        }

        call.resolve(BarcodeMapper.toJS([]))
    }

    // MARK: - One-shot scan

    @objc func scan(_ call: CAPPluginCall) {
        let options = mergedOptions(from: call)
        let lens = (options["lensFacing"] as? String) ?? "BACK"
        let resolution: Int = {
            if let v = options["resolution"] as? Int { return v }
            if let v = options["resolution"] as? Double { return Int(v) }
            if let v = options["resolution"] as? NSNumber { return v.intValue }
            return 2
        }()

        DispatchQueue.main.async {
            let view = UIView(frame: UIScreen.main.bounds)
            view.backgroundColor = .clear
            self.bridge?.viewController?.view.addSubview(view)

            self.scanner.onResult = { barcodes in
                if barcodes.isEmpty { return }
                DispatchQueue.main.async {
                    call.resolve(BarcodeMapper.toJS(barcodes))
                    self.scanner.stop()
                    view.removeFromSuperview()
                }
            }

            self.scanner.onError = { message in
                DispatchQueue.main.async {
                    call.reject(message)
                    self.scanner.stop()
                    view.removeFromSuperview()
                }
            }

            do {
                try self.scanner.start(previewView: view, lens: lens, resolution: resolution)
            } catch {
                call.reject("Camera error")
                view.removeFromSuperview()
            }
        }
    }

    private func mergedOptions(from call: CAPPluginCall) -> [String: Any] {
        if let nested = call.getObject("options"), !nested.isEmpty {
            return nested
        }
        return call.options as? [String: Any] ?? [:]
    }

    private func normalizeImageURL(_ path: String) -> URL? {
        if path.hasPrefix("file://"), let url = URL(string: path) {
            return url
        }
        return URL(fileURLWithPath: path)
    }

    private func loadImage(from url: URL) -> UIImage? {
        if let image = UIImage(contentsOfFile: url.path) {
            return image
        }

        guard let data = try? Data(contentsOf: url) else {
            return nil
        }
        return UIImage(data: data)
    }

    private func canonicalImage(_ image: UIImage) -> UIImage {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: image.size, format: format)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
    }

    private func normalizedForDecode(_ image: UIImage) -> UIImage? {
        let width = image.size.width
        let height = image.size.height
        if width <= 0 || height <= 0 { return nil }

        let minSide = min(width, height)
        let maxSide = max(width, height)

        var scale: CGFloat = 1
        if maxSide > 2200 {
            scale = 2200 / maxSide
        } else if minSide < 1200 {
            scale = 1200 / minSide
        }

        if abs(scale - 1) < 0.01 {
            return nil
        }

        let targetSize = CGSize(width: width * scale, height: height * scale)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }

    private func highContrastImage(_ image: UIImage) -> UIImage? {
        guard var ciImage = CIImage(image: image) else { return nil }

        if let colorControls = CIFilter(name: "CIColorControls") {
            colorControls.setValue(ciImage, forKey: kCIInputImageKey)
            colorControls.setValue(1.55, forKey: kCIInputContrastKey)
            colorControls.setValue(0.04, forKey: kCIInputBrightnessKey)
            colorControls.setValue(0.0, forKey: kCIInputSaturationKey)
            if let output = colorControls.outputImage {
                ciImage = output
            }
        }

        if let sharpen = CIFilter(name: "CISharpenLuminance") {
            sharpen.setValue(ciImage, forKey: kCIInputImageKey)
            sharpen.setValue(0.7, forKey: kCIInputSharpnessKey)
            if let output = sharpen.outputImage {
                ciImage = output
            }
        }

        guard let cgImage = ciContext.createCGImage(ciImage, from: ciImage.extent) else { return nil }
        return UIImage(cgImage: cgImage, scale: 1, orientation: .up)
    }

    private func binaryImage(_ image: UIImage) -> UIImage? {
        guard var ciImage = CIImage(image: image) else { return nil }

        if let noir = CIFilter(name: "CIPhotoEffectNoir") {
            noir.setValue(ciImage, forKey: kCIInputImageKey)
            if let output = noir.outputImage {
                ciImage = output
            }
        }

        if let controls = CIFilter(name: "CIColorControls") {
            controls.setValue(ciImage, forKey: kCIInputImageKey)
            controls.setValue(2.2, forKey: kCIInputContrastKey)
            controls.setValue(0.0, forKey: kCIInputSaturationKey)
            if let output = controls.outputImage {
                ciImage = output
            }
        }

        guard let cgImage = ciContext.createCGImage(ciImage, from: ciImage.extent) else { return nil }
        return UIImage(cgImage: cgImage, scale: 1, orientation: .up)
    }

    private func centeredSquareImage(_ image: UIImage, ratio: CGFloat) -> UIImage? {
        guard ratio > 0, ratio <= 1 else { return nil }
        guard let cgImage = image.cgImage else { return nil }

        let pixelWidth = CGFloat(cgImage.width)
        let pixelHeight = CGFloat(cgImage.height)
        let side = min(pixelWidth, pixelHeight) * ratio

        if side < 1 { return nil }
        if abs(side - min(pixelWidth, pixelHeight)) < 1 { return nil }

        let rect = CGRect(
            x: (pixelWidth - side) * 0.5,
            y: (pixelHeight - side) * 0.5,
            width: side,
            height: side
        ).integral

        guard let cropped = cgImage.cropping(to: rect) else { return nil }
        return UIImage(cgImage: cropped, scale: 1, orientation: .up)
    }

    private func createDetectRequest() -> VNDetectBarcodesRequest {
        let request = VNDetectBarcodesRequest()
        request.symbologies = [.qr]

        if #available(iOS 16.0, *) {
            request.revision = VNDetectBarcodesRequestRevision3
        }

        return request
    }

    private func detectDecodedBarcodes(in image: UIImage) -> [VNBarcodeObservation] {
        guard let cgImage = image.cgImage else { return [] }

        var observations: [VNBarcodeObservation] = []
        var tried = Set<Int>()

        let base = cgOrientation(from: image.imageOrientation)
        let orientations: [CGImagePropertyOrientation] = [base, .up, .right, .left, .down]

        for orientation in orientations {
            let key = Int(orientation.rawValue)
            if tried.contains(key) { continue }
            tried.insert(key)

            let request = createDetectRequest()
            let handler = VNImageRequestHandler(cgImage: cgImage, orientation: orientation, options: [:])
            do {
                try handler.perform([request])
                observations = request.results as? [VNBarcodeObservation] ?? []
            } catch {
                continue
            }

            let decoded = observations.filter { obs in
                guard let value = obs.payloadStringValue else { return false }
                return !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            }
            if !decoded.isEmpty {
                return decoded
            }
        }

        return []
    }

    private func cgOrientation(from orientation: UIImage.Orientation) -> CGImagePropertyOrientation {
        switch orientation {
        case .up: return .up
        case .down: return .down
        case .left: return .left
        case .right: return .right
        case .upMirrored: return .upMirrored
        case .downMirrored: return .downMirrored
        case .leftMirrored: return .leftMirrored
        case .rightMirrored: return .rightMirrored
        @unknown default: return .up
        }
    }

    // MARK: - Permissions

    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        let status = AVCaptureDevice.authorizationStatus(for: .video)

        let value: String
        switch status {
        case .authorized:
            value = "granted"
        case .notDetermined:
            value = "prompt"
        case .denied, .restricted:
            value = "denied"
        @unknown default:
            value = "denied"
        }

        call.resolve(["camera": value])
    }

    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        AVCaptureDevice.requestAccess(for: .video) { _ in
            DispatchQueue.main.async {
                self.checkPermissions(call)
            }
        }
    }

    // MARK: - Settings

    @objc func openSettings(_ call: CAPPluginCall) {
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            call.reject("Cannot open settings")
            return
        }
        UIApplication.shared.open(url)
        call.resolve()
    }
}

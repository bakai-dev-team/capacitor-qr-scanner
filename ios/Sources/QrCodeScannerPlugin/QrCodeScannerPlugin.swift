import Capacitor
import AVFoundation
import UIKit
import Vision

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
    private var previewView: UIView?

    @objc func startScan(_ call: CAPPluginCall) {

        let options = call.getObject("options") ?? [:]
        let lens = (options["lensFacing"] as? String) ?? "BACK"

        let resolution: Int = {
            if let v = options["resolution"] as? Int { return v }
            if let v = options["resolution"] as? Double { return Int(v) }
            if let v = options["resolution"] as? NSNumber { return v.intValue }
            return 1
        }()

        DispatchQueue.main.async {
            guard let bridge = self.bridge,
                  let webView = bridge.webView,
                  let container = webView.superview else {
                call.reject("Bridge/WebView not available")
                return
            }

            webView.isOpaque = false
            webView.backgroundColor = .clear
            webView.scrollView.backgroundColor = .clear

            let pv = UIView(frame: container.bounds)
            pv.backgroundColor = .clear
            pv.isUserInteractionEnabled = false
            self.previewView = pv

            let webIndex = container.subviews.firstIndex(of: webView) ?? container.subviews.count
            container.insertSubview(pv, at: max(0, webIndex))

            pv.autoresizingMask = [.flexibleWidth, .flexibleHeight]

            self.scanner.onResult = { [weak self] barcodes in
                guard let self = self else { return }
                self.notifyListeners("barcodesScanned", data: BarcodeMapper.toJS(barcodes))
            }

            self.scanner.onError = { [weak self] message in
                guard let self = self else { return }
                self.notifyListeners("scanError", data: ["message": message])
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
        scanner.stop()
        DispatchQueue.main.async {
            self.previewView?.removeFromSuperview()
            self.previewView = nil
            call.resolve()
        }
    }

    @objc func pauseScan(_ call: CAPPluginCall) {
        scanner.pause(previewHostView: previewView) // ✅ без звука, строго по последнему детекту
        call.resolve()
    }

    @objc func resumeScan(_ call: CAPPluginCall) {
        scanner.resume()
        call.resolve()
    }

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

    @objc func enableTorch(_ call: CAPPluginCall) {
        scanner.enableTorch()
        call.resolve()
    }

    @objc func disableTorch(_ call: CAPPluginCall) {
        scanner.disableTorch()
        call.resolve()
    }

    @objc func toggleTorch(_ call: CAPPluginCall) {
        scanner.toggleTorch()
        call.resolve()
    }

    @objc func isTorchEnabled(_ call: CAPPluginCall) {
        call.resolve(["enabled": scanner.isTorchEnabled()])
    }

    @objc func isTorchAvailable(_ call: CAPPluginCall) {
        call.resolve(["available": scanner.isTorchAvailable()])
    }

    @objc func readBarcodesFromImage(_ call: CAPPluginCall) {

        guard let path = call.getString("path") else {
            call.reject("No path provided")
            return
        }

        let url = URL(fileURLWithPath: path)

        let request = VNDetectBarcodesRequest { req, _ in
            let results = req.results as? [VNBarcodeObservation] ?? []
            call.resolve(BarcodeMapper.toJS(results))
        }

        let handler = VNImageRequestHandler(url: url)
        do {
            try handler.perform([request])
        } catch {
            call.reject("Failed to read barcodes from image")
        }
    }

    @objc func scan(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let view = UIView(frame: UIScreen.main.bounds)
            view.backgroundColor = .clear
            self.bridge?.viewController?.view.addSubview(view)

            self.scanner.onResult = { barcodes in
                call.resolve(BarcodeMapper.toJS(barcodes))
                self.scanner.stop()
                view.removeFromSuperview()
            }

            self.scanner.onError = { message in
                call.reject(message)
                self.scanner.stop()
                view.removeFromSuperview()
            }

            do {
                try self.scanner.start(previewView: view, lens: "BACK", resolution: 1)
            } catch {
                call.reject("Camera error")
                view.removeFromSuperview()
            }
        }
    }

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

    @objc func openSettings(_ call: CAPPluginCall) {
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            call.reject("Cannot open settings")
            return
        }
        UIApplication.shared.open(url)
        call.resolve()
    }
}

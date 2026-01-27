import Vision
import CoreGraphics

enum BarcodeMapper {

    static func toJS(_ barcodes: [VNBarcodeObservation]) -> [String: Any] {
        return [
            "barcodes": barcodes.map { toJS($0) }
        ]
    }

    static func toJS(_ b: VNBarcodeObservation) -> [String: Any] {

        var result: [String: Any] = [
            "displayValue": b.payloadStringValue ?? "",
            "rawValue": b.payloadStringValue ?? "",
            "format": mapFormat(b.symbology),
            "valueType": "TEXT"
        ]

        // ---- cornerPoints ----
        let box = b.boundingBox
        let points: [[CGFloat]] = [
            [box.minX, box.minY],
            [box.maxX, box.minY],
            [box.maxX, box.maxY],
            [box.minX, box.maxY]
        ]
        result["cornerPoints"] = points

        return result
    }

    private static func mapFormat(_ symbology: VNBarcodeSymbology) -> String {
        switch symbology {
        case .QR: return "QR_CODE"
        case .Code128: return "CODE_128"
        case .Code39: return "CODE_39"
        case .Code93: return "CODE_93"
        case .EAN8: return "EAN_8"
        case .EAN13: return "EAN_13"
        case .UPCE: return "UPC_E"
        case .PDF417: return "PDF_417"
        case .Aztec: return "AZTEC"
        case .DataMatrix: return "DATA_MATRIX"
        default: return "UNKNOWN"
        }
    }
}

import XCTest
@testable import QrCodeScannerPlugin

final class QrCodeScannerTests: XCTestCase {
    func testStopWithoutStartInvokesCompletion() {
        let scanner = QrCodeScanner()
        let completion = expectation(description: "stop completion")

        scanner.stop {
            completion.fulfill()
        }

        wait(for: [completion], timeout: 1.0)
    }

    func testStopIsIdempotent() {
        let scanner = QrCodeScanner()
        let firstCompletion = expectation(description: "first stop completion")
        let secondCompletion = expectation(description: "second stop completion")

        scanner.stop {
            firstCompletion.fulfill()
        }
        scanner.stop {
            secondCompletion.fulfill()
        }

        wait(for: [firstCompletion, secondCompletion], timeout: 1.0)
    }
}

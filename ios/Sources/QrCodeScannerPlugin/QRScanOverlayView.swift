import UIKit

final class QRScanOverlayView: UIView {

    var lineWidthFactor: CGFloat = 0.90
    var lineHeight: CGFloat = 5
    var lineColor: UIColor = .systemBlue
    var statusBarOffsetFactor: CGFloat = 0.10

    private let scanLine = CAGradientLayer()
    private let animKey = "wechat.scan.line.only"

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        isUserInteractionEnabled = false
        setup()
    }

    private func setup() {
        scanLine.startPoint = CGPoint(x: 0, y: 0.5)
        scanLine.endPoint   = CGPoint(x: 1, y: 0.5)
        scanLine.locations = [0, 0.5, 1]

        scanLine.shadowOpacity = 0.5
        scanLine.shadowRadius = 10
        scanLine.shadowOffset = .zero

        layer.addSublayer(scanLine)
        updateColors()
    }

    private func updateColors() {
        scanLine.colors = [
            UIColor.clear.cgColor,
            lineColor.withAlphaComponent(0.95).cgColor,
            UIColor.clear.cgColor
        ]
        scanLine.shadowColor = lineColor.cgColor
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layoutLine()
    }

    private func layoutLine() {
        let width = bounds.width * lineWidthFactor
        let x = (bounds.width - width) / 2

        let statusBarHeight = window?.windowScene?
            .statusBarManager?
            .statusBarFrame.height ?? 0

        let yTop = statusBarHeight * (1.0 + statusBarOffsetFactor)

        scanLine.frame = CGRect(x: x, y: yTop, width: width, height: lineHeight)
    }

    func startAnimating() {
        stopAnimating()

        let width = bounds.width * lineWidthFactor
        let x = (bounds.width - width) / 2

        let statusBarHeight = window?.windowScene?
            .statusBarManager?
            .statusBarFrame.height ?? 0

        let yTop = statusBarHeight * (3.0 + statusBarOffsetFactor)
        let yBottom = bounds.height * 0.70

        scanLine.frame = CGRect(x: x, y: yTop, width: width, height: lineHeight)

        let fromY = yTop + lineHeight / 2
        let toY   = yBottom - lineHeight / 2

        let anim = CABasicAnimation(keyPath: "position.y")
        anim.fromValue = fromY
        anim.toValue = toY
        anim.duration = 2.0
        anim.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        anim.autoreverses = true
        anim.repeatCount = .infinity
        anim.isRemovedOnCompletion = false

        scanLine.add(anim, forKey: animKey)

        scanLine.speed = 1
        scanLine.timeOffset = 0
        scanLine.beginTime = 0
    }

    func stopAnimating() {
        scanLine.removeAnimation(forKey: animKey)
        scanLine.speed = 1
        scanLine.timeOffset = 0
        scanLine.beginTime = 0
    }

    func pauseAnimating() {
        let pausedTime = scanLine.convertTime(CACurrentMediaTime(), from: nil)
        scanLine.speed = 0
        scanLine.timeOffset = pausedTime
    }

    func resumeAnimating() {
        let pausedTime = scanLine.timeOffset
        scanLine.speed = 1
        scanLine.timeOffset = 0
        scanLine.beginTime = 0

        let timeSincePause = scanLine.convertTime(CACurrentMediaTime(), from: nil) - pausedTime
        scanLine.beginTime = timeSincePause
    }
}

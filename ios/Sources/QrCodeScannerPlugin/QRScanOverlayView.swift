import UIKit

final class QRScanOverlayView: UIView {

    // 90% ширины
    var lineWidthFactor: CGFloat = 0.90

    // Толщина линии
    var lineHeight: CGFloat = 2

    // Белая линия
    var lineColor: UIColor = .white

    // Тень как на Android:
    // near = 0x55 = 85/255 = 0.3333
    // mid  ≈ 0x2A = 42/255 = 0.1647
    var trailAlphaNear: CGFloat = 85.0 / 255.0
    var trailAlphaMid: CGFloat = 42.0 / 255.0
    var trailLength: CGFloat = 130

    var statusBarOffsetFactor: CGFloat = 0.10

    // Время как на Android (durationMs)
    var durationMs: Double = 2000

    // Android PathInterpolator(0.42, 0, 0.58, 1)
    private let bezierX1: Double = 0.42
    private let bezierY1: Double = 0.0
    private let bezierX2: Double = 0.58
    private let bezierY2: Double = 1.0

    private var displayLink: CADisplayLink?
    private var startTime: CFTimeInterval = 0
    private var pausedTime: CFTimeInterval = 0
    private var accumulatedPause: CFTimeInterval = 0
    private var isRunning = false
    private var isPausedAnim = false

    private var currentY: CGFloat = 0
    private var goingDown: Bool = true
    private var lastY: CGFloat?

    override init(frame: CGRect) {
        super.init(frame: frame)
        commonInit()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        commonInit()
    }

    private func commonInit() {
        isUserInteractionEnabled = false
        isOpaque = false
        contentMode = .redraw
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if !isRunning {
            currentY = yTop()
            lastY = nil
            setNeedsDisplay()
        }
    }

    // MARK: - Controls

    func startAnimating() {
        stopAnimating()

        isRunning = true
        isPausedAnim = false
        accumulatedPause = 0
        pausedTime = 0
        startTime = CACurrentMediaTime()

        currentY = yTop()
        lastY = nil
        goingDown = true

        let dl = CADisplayLink(target: self, selector: #selector(tick))
        dl.add(to: .main, forMode: .common)
        displayLink = dl
    }

    func stopAnimating() {
        displayLink?.invalidate()
        displayLink = nil

        isRunning = false
        isPausedAnim = false
        accumulatedPause = 0
        pausedTime = 0
        startTime = 0
        lastY = nil

        currentY = yTop()
        setNeedsDisplay()
    }

    func pauseAnimating() {
        guard isRunning, !isPausedAnim else { return }
        isPausedAnim = true
        pausedTime = CACurrentMediaTime()
        displayLink?.isPaused = true
    }

    func resumeAnimating() {
        guard isRunning, isPausedAnim else { return }
        isPausedAnim = false
        let now = CACurrentMediaTime()
        accumulatedPause += (now - pausedTime)
        pausedTime = 0
        displayLink?.isPaused = false
    }

    // MARK: - Tick (точно как Android: duration + autoreverse + PathInterpolator)

    @objc private func tick() {
        let now = CACurrentMediaTime()
        let t = max(0, now - startTime - accumulatedPause)

        let halfPeriod = max(0.2, durationMs / 1000.0) // 2.0s как на Android
        let cycle = t / halfPeriod

        // фаза в [0, 2)
        let phase = cycle.truncatingRemainder(dividingBy: 2.0)

        // линейный прогресс половины хода [0..1]
        let u = (phase < 1.0) ? phase : (phase - 1.0)

        // easing как PathInterpolator (cubic-bezier)
        let eased = cubicBezierEase(x: u,
                                    x1: bezierX1, y1: bezierY1,
                                    x2: bezierX2, y2: bezierY2)

        // autoreverse: вниз (0->1), вверх (1->0)
        let progress = (phase < 1.0) ? eased : (1.0 - eased)

        let top = Double(yTop())
        let bottom = Double(yBottom())

        let newY = CGFloat(top + (bottom - top) * progress)

        if let last = lastY {
            goingDown = newY >= last
        } else {
            goingDown = true
        }
        lastY = newY

        currentY = newY
        setNeedsDisplay()
    }

    // MARK: - Drawing

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        if bounds.width <= 0 || bounds.height <= 0 { return }

        let width = bounds.width * lineWidthFactor
        let x = (bounds.width - width) / 2.0

        // Snap по пикселям (как мы делали на Android: целые px => нет “двоения”)
        let y = snapToPixel(currentY)
        let halfLine = lineHeight / 2.0

        let lineTop = y - halfLine
        let lineBottom = y + halfLine

        // Тень: НЕ заходит под линию (иначе кажется что линия “двойная”)
        if goingDown {
            let trailBottom = lineTop
            let trailTop = max(trailBottom - trailLength, bounds.minY)
            let h = max(1.0 / UIScreen.main.scale, trailBottom - trailTop)
            drawTrail(ctx: ctx, rect: CGRect(x: x, y: trailTop, width: width, height: h), goingDown: true)
        } else {
            let trailTop = lineBottom
            let trailBottom = min(trailTop + trailLength, bounds.maxY)
            let h = max(1.0 / UIScreen.main.scale, trailBottom - trailTop)
            drawTrail(ctx: ctx, rect: CGRect(x: x, y: trailTop, width: width, height: h), goingDown: false)
        }

        // Линия
        ctx.setFillColor(lineColor.cgColor)
        ctx.fill(CGRect(x: x, y: lineTop, width: width, height: lineHeight))
    }

    private func drawTrail(ctx: CGContext, rect: CGRect, goingDown: Bool) {
        if rect.height <= 0.5 { return }

        let far  = lineColor.withAlphaComponent(0.0).cgColor
        let mid  = lineColor.withAlphaComponent(trailAlphaMid).cgColor
        let near = lineColor.withAlphaComponent(trailAlphaNear).cgColor

        let colors: [CGColor]
        let locations: [CGFloat]

        if goingDown {
            colors = [far, mid, near]
            locations = [0.0, 0.65, 1.0]
        } else {
            colors = [near, mid, far]
            locations = [0.0, 0.35, 1.0]
        }

        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let gradient = CGGradient(colorsSpace: space, colors: colors as CFArray, locations: locations)
        else { return }

        ctx.saveGState()
        ctx.addRect(rect)
        ctx.clip()

        let start = CGPoint(x: rect.midX, y: rect.minY)
        let end   = CGPoint(x: rect.midX, y: rect.maxY)
        ctx.drawLinearGradient(gradient, start: start, end: end, options: [])

        ctx.restoreGState()
    }

    // MARK: - Android-like bezier easing solver
    // Возвращает y для заданного x=t, где кривая (0,0)-(1,1) с контрольными точками (x1,y1),(x2,y2)

    private func cubicBezierEase(x: Double, x1: Double, y1: Double, x2: Double, y2: Double) -> Double {
        // solve for s where BezierX(s) = x, then return BezierY(s)
        let s = solveBezierParameter(forX: x, x1: x1, x2: x2)
        return bezier(s, a1: y1, a2: y2)
    }

    // Bezier for one axis with P0=0, P1=a1, P2=a2, P3=1
    private func bezier(_ t: Double, a1: Double, a2: Double) -> Double {
        let c = 3.0 * a1
        let b = 3.0 * (a2 - a1) - c
        let a = 1.0 - c - b
        return ((a * t + b) * t + c) * t
    }

    private func bezierDerivative(_ t: Double, a1: Double, a2: Double) -> Double {
        let c = 3.0 * a1
        let b = 3.0 * (a2 - a1) - c
        let a = 1.0 - c - b
        return (3.0 * a * t + 2.0 * b) * t + c
    }

    private func solveBezierParameter(forX x: Double, x1: Double, x2: Double) -> Double {
        // Newton-Raphson
        var t = x
        for _ in 0..<8 {
            let xEst = bezier(t, a1: x1, a2: x2) - x
            let d = bezierDerivative(t, a1: x1, a2: x2)
            if abs(d) < 1e-6 { break }
            let tNext = t - xEst / d
            if abs(tNext - t) < 1e-7 { t = tNext; break }
            t = min(1.0, max(0.0, tNext))
        }

        // fallback binary search (на всякий случай)
        var lo = 0.0, hi = 1.0
        for _ in 0..<12 {
            let mid = (lo + hi) * 0.5
            let xMid = bezier(mid, a1: x1, a2: x2)
            if xMid < x { lo = mid } else { hi = mid }
        }
        let tBin = (lo + hi) * 0.5

        // Если Ньютона “унесло”, берём бинпоиск
        if t.isNaN || t < 0.0 || t > 1.0 { return tBin }
        return t
    }

    // MARK: - Geometry (top/bottom как у тебя)

    private func yTop() -> CGFloat {
        let statusBarHeight = window?.windowScene?.statusBarManager?.statusBarFrame.height ?? 0
        return statusBarHeight * (3.0 + statusBarOffsetFactor)
    }

    private func yBottom() -> CGFloat {
        return bounds.height * 0.70
    }

    private func snapToPixel(_ y: CGFloat) -> CGFloat {
        let scale = UIScreen.main.scale
        return round(y * scale) / scale
    }
}

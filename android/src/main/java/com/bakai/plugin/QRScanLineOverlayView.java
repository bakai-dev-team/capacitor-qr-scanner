package com.bakai.plugin;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.annotation.Nullable;

public final class QRScanLineOverlayView extends View {

    private ValueAnimator animator;

    private boolean isPaused = false;

    // 90% ширины
    private float lineWidthFactor = 0.90f;

    // top/bottom оставляем как было
    private float bottomFactor = 0.70f;
    private float statusBarFactor = 1.20f;
    private float extraTopDp = 100f;

    private long durationMs = 2000;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;

    // размеры
    private float lineHeightPx; // толщина линии
    private float trailLengthPx; // длина следа
    private float trailAttachPx; // "прилипание" тени к линии

    // позиция
    private float currentY;
    private float lastY = Float.NaN;
    private float pausedY = Float.NaN;
    private boolean goingDown = true;

    // геометрия
    private float leftX, rightX;
    private float yTop, yBottom;

    private float halfLine;
    private float turnBlendPx;

    public QRScanLineOverlayView(Context context) {
        super(context);
        init();
    }

    public QRScanLineOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);

        density = getResources().getDisplayMetrics().density;

        // Чуть толще и заметнее, но без мерцаний:
        // делаем толщину в целых пикселях
        float desired = 1.75f * density;
        lineHeightPx = Math.max(1f, Math.round(desired)); // <= ключ к отсутствию "двойной" линии
        halfLine = lineHeightPx / 2f;

        // След длиннее
        trailLengthPx = 130f * density;
        trailAttachPx = 2.5f * density;
        // Небольшая зона для плавного "переворота" тени на развороте
        turnBlendPx = 12f * density;

        // Линия — белая, без скругления
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setColor(0xFFFFFFFF);

        // Важно: чтобы линия не "двоилось" — выключаем AA именно для линии
        linePaint.setAntiAlias(false);

        // След — мягкий градиент (AA можно оставить)
        trailPaint.setStyle(Paint.Style.FILL);
        trailPaint.setAntiAlias(true);
    }

    private int getStatusBarHeight() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? getResources().getDimensionPixelSize(resId) : 0;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w <= 0 || h <= 0) return;

        float lineW = w * lineWidthFactor;
        leftX = (w - lineW) / 2f;
        rightX = leftX + lineW;

        int sb = getStatusBarHeight();
        yTop = (sb * statusBarFactor) + (extraTopDp * density);
        yBottom = h * bottomFactor;

        if (isPaused) {
            if (Float.isNaN(pausedY)) pausedY = yTop;
            currentY = clamp(pausedY, yTop, yBottom);
            postInvalidateOnAnimation();
            return;
        }

        restartAnimatorFrom(Float.isNaN(currentY) ? Float.NaN : currentY);
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;

        // направление движения линии (устойчиво к микродрожанию в точке разворота)
        if (!Float.isNaN(lastY)) {
            float delta = currentY - lastY;
            if (Math.abs(delta) > 0f) {
                goingDown = delta >= 0f;
            }
        }

        // --- SNAP к пиксельной сетке, чтобы линия не "двоилось"
        // Для прямоугольника лучше всего рисовать по целым пикселям
        float yLine = Math.round(currentY);

        float lineTop = yLine - halfLine;
        float lineBottom = yLine + halfLine;

        // ----- 1) след (плавный crossfade возле разворота, чтобы убрать резкий flip) -----
        float travel = Math.max(1f, yBottom - yTop);
        float blendWindow = Math.max(lineHeightPx * 1.5f, Math.min(turnBlendPx, travel * 0.08f));
        float distToTurn = goingDown ? (yBottom - yLine) : (yLine - yTop);
        float blendToNext = 0f;
        if (distToTurn < blendWindow) {
            float t = 1f - clamp(distToTurn / blendWindow, 0f, 1f);
            // smoothstep: мягкий вход в смешивание только у самого разворота
            blendToNext = t * t * (3f - 2f * t);
        }

        float primaryAlpha = 1f - blendToNext;
        float secondaryAlpha = blendToNext;
        float position01 = clamp((yLine - yTop) / travel, 0f, 1f);
        float nowSec = (SystemClock.uptimeMillis() % 100000L) / 1000f;
        float shimmer = 0.5f + 0.5f * (float) Math.sin((nowSec * 4.2f) + (position01 * (float) (Math.PI * 2.0)));
        float trailAlphaPulse = 0.90f + (0.15f * shimmer);
        float trailLengthScale = 0.88f + (0.22f * shimmer);
        float gradientShift = 0.08f * (shimmer - 0.5f);

        if (goingDown) {
            drawTrailForDirection(
                canvas,
                yLine,
                lineTop,
                lineBottom,
                true,
                primaryAlpha * trailAlphaPulse,
                trailLengthScale,
                gradientShift
            );
            drawTrailForDirection(
                canvas,
                yLine,
                lineTop,
                lineBottom,
                false,
                secondaryAlpha * trailAlphaPulse,
                trailLengthScale,
                gradientShift
            );
        } else {
            drawTrailForDirection(
                canvas,
                yLine,
                lineTop,
                lineBottom,
                false,
                primaryAlpha * trailAlphaPulse,
                trailLengthScale,
                gradientShift
            );
            drawTrailForDirection(
                canvas,
                yLine,
                lineTop,
                lineBottom,
                true,
                secondaryAlpha * trailAlphaPulse,
                trailLengthScale,
                gradientShift
            );
        }

        // ----- 2) сама линия (без скругления) -----
        // linePaint без AA + yLine округлён => исчезает "раздвоение"
        canvas.drawRect(leftX, lineTop, rightX, lineBottom, linePaint);

        lastY = currentY;
    }

    private void restartAnimatorFrom(float startY) {
        stopInternal(false);

        if (getWidth() == 0 || getHeight() == 0) return;
        if (yBottom <= yTop + 20f) return;

        float begin = Float.isNaN(startY) ? yTop : clamp(startY, yTop, yBottom);

        currentY = begin;
        lastY = Float.NaN;
        goingDown = true;

        postInvalidateOnAnimation();

        animator = ValueAnimator.ofFloat(begin, yBottom);
        animator.setDuration(durationMs);

        if (Build.VERSION.SDK_INT >= 21) {
            animator.setInterpolator(new android.view.animation.PathInterpolator(0.42f, 0f, 0.58f, 1f));
        } else {
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
        }

        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);

        animator.addUpdateListener((a) -> {
            if (isPaused) return;
            currentY = (float) a.getAnimatedValue();
            postInvalidateOnAnimation();
        });

        animator.start();
    }

    public void start() {
        isPaused = false;
        pausedY = Float.NaN;
        post(() -> restartAnimatorFrom(Float.NaN));
    }

    public void pause() {
        if (isPaused) return;
        isPaused = true;
        pausedY = currentY;

        if (animator != null) {
            if (Build.VERSION.SDK_INT >= 19) animator.pause();
            else animator.cancel();
        }

        postInvalidateOnAnimation();
    }

    public void resume() {
        if (!isPaused) return;
        isPaused = false;

        if (animator != null && Build.VERSION.SDK_INT >= 19) {
            animator.resume();
            return;
        }

        float startY = Float.isNaN(pausedY) ? currentY : pausedY;
        pausedY = Float.NaN;
        restartAnimatorFrom(startY);
    }

    public void stop() {
        stopInternal(true);
        postInvalidateOnAnimation();
    }

    private void stopInternal(boolean resetPos) {
        if (animator != null) {
            animator.cancel();
            animator.removeAllUpdateListeners();
            animator = null;
        }
        if (resetPos) {
            isPaused = false;
            pausedY = Float.NaN;
            currentY = Float.NaN;
            lastY = Float.NaN;
            goingDown = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopInternal(true);
        super.onDetachedFromWindow();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int scaleAlpha(int color, float factor) {
        float k = clamp(factor, 0f, 1f);
        int baseA = (color >>> 24) & 0xFF;
        int scaledA = Math.round(baseA * k);
        return (color & 0x00FFFFFF) | (scaledA << 24);
    }

    private void drawTrailForDirection(
        Canvas canvas,
        float yLine,
        float lineTop,
        float lineBottom,
        boolean trailAboveLine,
        float alphaScale,
        float lengthScale,
        float gradientShift
    ) {
        if (alphaScale <= 0.01f) return;

        float effectiveTrailLength = trailLengthPx * clamp(lengthScale, 0.75f, 1.3f);
        float trailTop;
        float trailBottom;

        if (trailAboveLine) {
            trailTop = yLine - effectiveTrailLength;
            trailBottom = lineTop;
        } else {
            trailTop = lineBottom;
            trailBottom = yLine + effectiveTrailLength;
        }

        trailTop = Math.max(trailTop, yTop);
        trailBottom = Math.min(trailBottom, yBottom);

        if (trailBottom <= trailTop + 1f) return;

        int near = scaleAlpha(0x66FFFFFF, (alphaScale * 0.95f) + 0.05f);
        int mid = scaleAlpha(0x2AFFFFFF, alphaScale * 0.90f);
        int far = 0x00FFFFFF;
        float midLocation = trailAboveLine ? clamp(0.68f + gradientShift, 0.2f, 0.8f) : clamp(0.32f - gradientShift, 0.2f, 0.8f);
        int[] colors = trailAboveLine ? new int[] { far, mid, near } : new int[] { near, mid, far };
        float[] locations = new float[] { 0f, midLocation, 1f };

        trailPaint.setShader(new LinearGradient(0f, trailTop, 0f, trailBottom, colors, locations, Shader.TileMode.CLAMP));

        canvas.drawRect(leftX, trailTop, rightX, trailBottom, trailPaint);

        // Тонкий "прилипший" участок прямо у линии, чтобы убрать визуальное отставание тени.
        float attach = Math.min(trailAttachPx, trailBottom - trailTop);
        if (attach > 0.5f) {
            int attachColor = scaleAlpha(0x99FFFFFF, (alphaScale * 0.90f) + 0.10f);
            trailPaint.setShader(null);
            trailPaint.setColor(attachColor);
            if (trailAboveLine) {
                canvas.drawRect(leftX, trailBottom - attach, rightX, trailBottom, trailPaint);
            } else {
                canvas.drawRect(leftX, trailTop, rightX, trailTop + attach, trailPaint);
            }
        }
    }

    // ---- опциональные сеттеры ----

    public void setDurationMs(long durationMs) {
        this.durationMs = Math.max(200L, durationMs);
        if (!isPaused) restartAnimatorFrom(currentY);
    }

    public void setTrailLengthDp(float dp) {
        trailLengthPx = Math.max(10f, dp) * density;
        invalidate();
    }

    public void setLineThicknessDp(float dp) {
        float desired = Math.max(0.5f, dp) * density;
        lineHeightPx = Math.max(1f, Math.round(desired)); // тоже в целых px
        halfLine = lineHeightPx / 2f;
        invalidate();
    }
}

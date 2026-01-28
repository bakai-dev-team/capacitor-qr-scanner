package com.bakai.plugin;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
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
    private float bottomFactor = 0.73f;
    private float statusBarFactor = 1.20f;
    private float extraTopDp = 100f;

    private long durationMs = 2000;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;

    // размеры
    private float lineHeightPx; // толщина линии
    private float trailLengthPx; // длина следа

    // позиция
    private float currentY;
    private float lastY = Float.NaN;
    private float pausedY = Float.NaN;

    // геометрия
    private float leftX, rightX;
    private float yTop, yBottom;

    private float halfLine;

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

        // направление движения линии
        boolean goingDown = true;
        if (!Float.isNaN(lastY)) goingDown = currentY >= lastY;

        // --- SNAP к пиксельной сетке, чтобы линия не "двоилось"
        // Для прямоугольника лучше всего рисовать по целым пикселям
        float yLine = Math.round(currentY);

        float lineTop = yLine - halfLine;
        float lineBottom = yLine + halfLine;

        // ----- 1) след (НЕ заходит под линию, чтобы не давал эффект второй полосы) -----
        float trailTop, trailBottom;

        if (goingDown) {
            // линия вниз -> след сверху, но заканчивается ПЕРЕД линией
            trailTop = yLine - trailLengthPx;
            trailBottom = lineTop; // <-- важно: не до yLine
        } else {
            // линия вверх -> след снизу, но начинается ПОСЛЕ линии
            trailTop = lineBottom; // <-- важно: не от yLine
            trailBottom = yLine + trailLengthPx;
        }

        trailTop = Math.max(trailTop, yTop);
        trailBottom = Math.min(trailBottom, yBottom);

        if (trailBottom > trailTop + 1f) {
            int near = 0x55FFFFFF; // плотнее
            int far = 0x00FFFFFF;

            int[] colors = goingDown ? new int[] { far, near } : new int[] { near, far };

            trailPaint.setShader(new LinearGradient(0f, trailTop, 0f, trailBottom, colors, new float[] { 0f, 1f }, Shader.TileMode.CLAMP));

            canvas.drawRect(leftX, trailTop, rightX, trailBottom, trailPaint);
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

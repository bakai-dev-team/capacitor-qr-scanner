package com.bakai.plugin;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
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

    private static final int IOS_SYSTEM_BLUE = 0xFF007AFF;

    private ValueAnimator animator;

    private boolean isPaused = false;

    private float lineWidthFactor = 0.90f;
    private float bottomFactor = 0.73f;
    private float statusBarFactor = 1.20f;
    private float extraTopDp = 100f;

    private long durationMs = 2000;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;
    private float lineHeightPx;

    private float currentY;
    private float pausedY = Float.NaN;

    private float leftX, rightX;
    private float yTop, yBottom;

    // Кешируем чтобы не пересчитывать в onDraw
    private float halfLine;
    private float glowExtra1;
    private float glowExtra2;

    // Большой радиус не нужен: достаточно половины ширины линии
    private float cornerRadius;

    // Если надо отключать blur на слабых устройствах:
    private boolean enableGlow = true;

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
        lineHeightPx = 2f * density;

        linePaint.setStyle(Paint.Style.FILL);
        glowPaint1.setStyle(Paint.Style.FILL);
        glowPaint2.setStyle(Paint.Style.FILL);

        // BlurMaskFilter требует software layer, но включать его на весь View — дорого.
        // Поэтому включаем только если glow реально нужен.
        if (enableGlow) {
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            glowPaint1.setMaskFilter(new BlurMaskFilter(2f * density, BlurMaskFilter.Blur.NORMAL));
            glowPaint2.setMaskFilter(new BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL));
        }
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

        // Кеш для onDraw
        halfLine = lineHeightPx / 2f;
        glowExtra1 = lineHeightPx * 1.2f;
        glowExtra2 = lineHeightPx * 2.2f;
        cornerRadius = (rightX - leftX) * 0.5f; // достаточно, чтобы закруглить края

        // Горизонтальные градиенты (по X). Y не важен.
        linePaint.setShader(makeGradient(0x00000000, IOS_SYSTEM_BLUE, 0x00000000));
        if (enableGlow) {
            glowPaint1.setShader(makeGradient(0x00000000, 0xCC007AFF, 0x00000000));
            glowPaint2.setShader(makeGradient(0x00000000, 0x88007AFF, 0x00000000));
        }

        // Если пауза — просто выставляем позицию и рисуем
        if (isPaused) {
            if (Float.isNaN(pausedY)) pausedY = yTop;
            currentY = clamp(pausedY, yTop, yBottom);
            postInvalidateOnAnimation();
            return;
        }

        // Перезапуск с текущей позиции (если уже была), иначе с верха
        float start = Float.isNaN(currentY) ? Float.NaN : currentY;
        restartAnimatorFrom(start);
    }

    private Shader makeGradient(int c0, int c1, int c2) {
        return new LinearGradient(leftX, 0f, rightX, 0f, new int[] { c0, c1, c2 }, new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getWidth() == 0 || getHeight() == 0) return;

        float top = currentY - halfLine;
        float bottom = currentY + halfLine;

        if (enableGlow) {
            canvas.drawRoundRect(leftX, top - glowExtra2, rightX, bottom + glowExtra2, cornerRadius, cornerRadius, glowPaint2);
            canvas.drawRoundRect(leftX, top - glowExtra1, rightX, bottom + glowExtra1, cornerRadius, cornerRadius, glowPaint1);
        }

        canvas.drawRoundRect(leftX, top, rightX, bottom, cornerRadius, cornerRadius, linePaint);
    }

    private void restartAnimatorFrom(float startY) {
        stopInternal(false);

        if (getWidth() == 0 || getHeight() == 0) return;
        if (yBottom <= yTop + 20f) return;

        float begin = Float.isNaN(startY) ? yTop : clamp(startY, yTop, yBottom);
        currentY = begin;
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

        // Без рекурсии через post(this::start) — просто один отложенный старт
        post(() -> restartAnimatorFrom(Float.NaN));
    }

    public void pause() {
        if (isPaused) return;
        isPaused = true;
        pausedY = currentY;

        if (animator != null) {
            if (Build.VERSION.SDK_INT >= 19) animator.pause();
            else animator.cancel(); // на старых — пересоздадим на resume
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
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // чтобы не было утечек/лишней анимации в фоне
        stopInternal(true);
        super.onDetachedFromWindow();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // (опционально) быстрые сеттеры, если нужно менять параметры без пересоздания view:
    public void setDurationMs(long durationMs) {
        this.durationMs = Math.max(200L, durationMs);
        if (!isPaused) restartAnimatorFrom(currentY);
    }

    public void setEnableGlow(boolean enable) {
        if (this.enableGlow == enable) return;
        this.enableGlow = enable;
        // проще: пересоздать вью-параметры; если glow отключили — можно вернуть HW layer:
        setLayerType(enableGlow ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_HARDWARE, null);
        requestLayout();
        invalidate();
    }
}

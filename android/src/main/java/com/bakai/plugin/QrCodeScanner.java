package com.bakai.plugin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.TorchState;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class QrCodeScanner {

    private final Context context;
    private final BarcodeScanner scanner;

    private ExecutorService cameraExecutor;
    private final Executor mainExecutor;

    private ProcessCameraProvider provider;
    private Camera camera;
    private ImageAnalysis analysis;
    private Preview preview;

    private volatile boolean paused = false;

    // zoom
    private volatile Float pendingZoomRatio = null;
    private volatile Float lastRequestedZoomRatio = null;

    // analyzer
    private ImageAnalysis.Analyzer analyzer;
    private boolean analyzerAttached = false;

    // MLKit guard
    private volatile boolean processing = false;

    // perf throttle
    private volatile long lastAnalyzeAtMs = 0L;
    private volatile long cooldownUntilMs = 0L;
    private static final long ANALYZE_INTERVAL_MS = 70L;
    private static final long SUCCESS_COOLDOWN_MS = 350L;

    // zoom observer
    private LifecycleOwner lastOwner = null;
    private Callback lastCallback = null;
    private Observer<ZoomState> zoomObserver = null;

    // ✅ main handler for zoom retry
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean zoomRetryScheduled = false;
    private long zoomRetryStartMs = 0L;
    private static final long ZOOM_RETRY_INTERVAL_MS = 50L;
    private static final long ZOOM_RETRY_TIMEOUT_MS = 1500L; // 1.5s

    private final Runnable zoomRetryRunnable = new Runnable() {
        @Override
        public void run() {
            zoomRetryScheduled = false;
            if (pendingZoomRatio == null) return;

            boolean applied = tryApplyPendingZoomNow();
            if (applied) return;

            long now = SystemClock.elapsedRealtime();
            if (zoomRetryStartMs == 0L) zoomRetryStartMs = now;

            if ((now - zoomRetryStartMs) >= ZOOM_RETRY_TIMEOUT_MS) {
                // таймаут — оставим pending (может примениться позже через observe), но прекращаем активный спам
                return;
            }

            scheduleZoomRetry();
        }
    };

    public interface Callback {
        void onBarcodes(List<Barcode> barcodes);
        void onError(String message);
        void onZoomReady(float minRatio, float maxRatio, float currentRatio);
    }

    public QrCodeScanner(Context context) {
        this.context = context.getApplicationContext();

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();

        scanner = BarcodeScanning.getClient(options);

        cameraExecutor = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(
                        () -> {
                            try {
                                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE);
                            } catch (Exception ignored) {}
                            r.run();
                        },
                        "QrCodeScannerAnalysis"
                    );
                }
            }
        );

        mainExecutor = ContextCompat.getMainExecutor(this.context);
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void start(LifecycleOwner owner, PreviewView previewView, String lensFacing, int resolution, Callback callback) {
        if (callback == null) return;

        lastOwner = owner;
        lastCallback = callback;

        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }

        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);

        future.addListener(
            () -> {
                try {
                    provider = future.get();

                    Size targetSize;
                    switch (resolution) {
                        case 0:
                            targetSize = new Size(640, 480);
                            break;
                        case 2:
                            targetSize = new Size(1920, 1080);
                            break;
                        default:
                            targetSize = new Size(1280, 720);
                    }

                    CameraSelector selector = "FRONT".equals(lensFacing)
                        ? CameraSelector.DEFAULT_FRONT_CAMERA
                        : CameraSelector.DEFAULT_BACK_CAMERA;

                    preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    int rotation = Surface.ROTATION_0;
                    try {
                        if (previewView.getDisplay() != null) rotation = previewView.getDisplay().getRotation();
                    } catch (Exception ignored) {}

                    analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(targetSize)
                        .setTargetRotation(rotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setImageQueueDepth(1)
                        .build();

                    processing = false;
                    lastAnalyzeAtMs = 0L;
                    cooldownUntilMs = 0L;

                    analyzer = (imageProxy) -> {
                        try {
                            if (paused || imageProxy.getImage() == null) {
                                imageProxy.close();
                                return;
                            }

                            final long now = SystemClock.elapsedRealtime();

                            if (now < cooldownUntilMs) {
                                imageProxy.close();
                                return;
                            }

                            if (ANALYZE_INTERVAL_MS > 0 && (now - lastAnalyzeAtMs) < ANALYZE_INTERVAL_MS) {
                                imageProxy.close();
                                return;
                            }

                            if (processing) {
                                imageProxy.close();
                                return;
                            }

                            processing = true;
                            lastAnalyzeAtMs = now;

                            InputImage inputImage = InputImage.fromMediaImage(
                                imageProxy.getImage(),
                                imageProxy.getImageInfo().getRotationDegrees()
                            );

                            scanner
                                .process(inputImage)
                                .addOnSuccessListener((barcodes) -> {
                                    if (barcodes != null && !barcodes.isEmpty()) {
                                        cooldownUntilMs = SystemClock.elapsedRealtime() + SUCCESS_COOLDOWN_MS;
                                        callback.onBarcodes(barcodes);
                                    }
                                })
                                .addOnFailureListener((e) -> callback.onError(e != null ? String.valueOf(e.getMessage()) : "Unknown error"))
                                .addOnCompleteListener((t) -> {
                                    try {
                                        imageProxy.close();
                                    } catch (Exception ignored) {}
                                    processing = false;
                                });
                        } catch (Exception e) {
                            try {
                                imageProxy.close();
                            } catch (Exception ignored) {}
                            processing = false;
                            callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
                        }
                    };

                    analysis.setAnalyzer(cameraExecutor, analyzer);
                    analyzerAttached = true;

                    provider.unbindAll();
                    camera = provider.bindToLifecycle(owner, selector, preview, analysis);

                    observeZoomState(owner, callback);

                    // ✅ если zoom уже просили раньше — попробуем применить сразу
                    if (pendingZoomRatio != null) {
                        tryApplyPendingZoomNow();
                        scheduleZoomRetry();
                    }
                } catch (Exception e) {
                    callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to start camera");
                }
            },
            mainExecutor
        );
    }

    private void observeZoomState(LifecycleOwner owner, Callback callback) {
        if (camera == null) return;

        try {
            if (zoomObserver != null) {
                camera.getCameraInfo().getZoomState().removeObserver(zoomObserver);
            }
        } catch (Exception ignored) {}

        zoomObserver = (zs) -> {
            if (zs == null) return;

            // уведомление о границах/текущем
            callback.onZoomReady(zs.getMinZoomRatio(), zs.getMaxZoomRatio(), zs.getZoomRatio());

            // ✅ главное: как только ZoomState появился — применяем pending
            if (pendingZoomRatio != null) {
                tryApplyPendingZoomNow();
            }
        };

        camera.getCameraInfo().getZoomState().observe(owner, zoomObserver);
    }

    private void scheduleZoomRetry() {
        if (zoomRetryScheduled) return;
        zoomRetryScheduled = true;
        mainHandler.postDelayed(zoomRetryRunnable, ZOOM_RETRY_INTERVAL_MS);
    }

    /**
     * Пытаемся применить pendingZoomRatio прямо сейчас.
     * Возвращает true если применили.
     */
    private boolean tryApplyPendingZoomNow() {
        final Camera cam = camera;
        final Float want = pendingZoomRatio;
        if (cam == null || want == null) return false;

        ZoomState zs = cam.getCameraInfo().getZoomState().getValue();
        if (zs == null) return false;

        float clamped = Math.max(zs.getMinZoomRatio(), Math.min(zs.getMaxZoomRatio(), want));
        try {
            cam.getCameraControl().setZoomRatio(clamped);
            pendingZoomRatio = null;
            zoomRetryStartMs = 0L;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void stop() {
        paused = true;
        processing = false;

        // остановить zoom retry
        try {
            mainHandler.removeCallbacks(zoomRetryRunnable);
        } catch (Exception ignored) {}
        zoomRetryScheduled = false;
        zoomRetryStartMs = 0L;

        mainExecutor.execute(() -> {
            try {
                try {
                    if (camera != null && zoomObserver != null) {
                        camera.getCameraInfo().getZoomState().removeObserver(zoomObserver);
                    }
                } catch (Exception ignored) {}
                zoomObserver = null;

                if (analysis != null) {
                    try {
                        analysis.clearAnalyzer();
                    } catch (Exception ignored) {}
                }
                if (provider != null) provider.unbindAll();
            } catch (Exception ignored) {}

            provider = null;
            camera = null;
            analysis = null;
            preview = null;

            analyzer = null;
            analyzerAttached = false;

            pendingZoomRatio = null;
            lastRequestedZoomRatio = null;

            lastOwner = null;
            lastCallback = null;

            lastAnalyzeAtMs = 0L;
            cooldownUntilMs = 0L;
        });

        try {
            scanner.close();
        } catch (Exception ignored) {}

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
    }

    /** PAUSE: останавливаем анализатор */
    public void pause() {
        paused = true;
        processing = false;

        final ImageAnalysis localAnalysis = analysis;
        if (localAnalysis != null && analyzerAttached) {
            mainExecutor.execute(() -> {
                try {
                    localAnalysis.clearAnalyzer();
                } catch (Exception ignored) {}
                analyzerAttached = false;
            });
        }

        // zoom retry не трогаем: можно оставить, но чтобы не крутился зря — остановим
        try {
            mainHandler.removeCallbacks(zoomRetryRunnable);
        } catch (Exception ignored) {}
        zoomRetryScheduled = false;
        zoomRetryStartMs = 0L;
    }

    /** RESUME: возвращаем анализатор и перезапускаем применение zoom */
    public void resume() {
        paused = false;

        final ImageAnalysis localAnalysis = analysis;
        final ExecutorService localExecutor = cameraExecutor;
        final ImageAnalysis.Analyzer localAnalyzer = analyzer;

        mainExecutor.execute(() -> {
            // вернуть analyzer если снимали
            if (localAnalysis != null && localExecutor != null && localAnalyzer != null && !analyzerAttached) {
                try {
                    localAnalysis.setAnalyzer(localExecutor, localAnalyzer);
                } catch (Exception ignored) {}
                analyzerAttached = true;
            }

            // ✅ ре-обсервер zoom (на случай внутреннего реинициализа)
            if (lastOwner != null && lastCallback != null && camera != null) {
                observeZoomState(lastOwner, lastCallback);
            }

            // ✅ если до паузы был zoom — применим снова
            if (lastRequestedZoomRatio != null) {
                pendingZoomRatio = lastRequestedZoomRatio;
                tryApplyPendingZoomNow();
                scheduleZoomRetry();
            }
        });
    }

    // ===== Torch =====

    public boolean isTorchAvailable() {
        return camera != null && camera.getCameraInfo().hasFlashUnit();
    }

    public boolean isZoomReady() {
        return camera != null && camera.getCameraInfo().getZoomState().getValue() != null;
    }

    public boolean isTorchEnabled() {
        if (camera == null) return false;
        Integer state = camera.getCameraInfo().getTorchState().getValue();
        return state != null && state == TorchState.ON;
    }

    public void enableTorch(boolean enabled) {
        if (camera == null) return;
        mainExecutor.execute(() -> {
            if (camera == null) return;
            camera.getCameraControl().enableTorch(enabled);
        });
    }

    public void enableTorch() {
        enableTorch(true);
    }

    public void disableTorch() {
        enableTorch(false);
    }

    public void toggleTorch() {
        if (!isTorchAvailable()) return;
        enableTorch(!isTorchEnabled());
    }

    // ===== Zoom =====

    public float getZoomRatio() {
        if (camera == null) return 1f;
        ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
        return zs != null ? zs.getZoomRatio() : 1f;
    }

    public float getMinZoomRatio() {
        if (camera == null) return 1f;
        ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
        return zs != null ? zs.getMinZoomRatio() : 1f;
    }

    public float getMaxZoomRatio() {
        if (camera == null) return 1f;
        ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
        return zs != null ? zs.getMaxZoomRatio() : 1f;
    }

    public void setZoomRatio(float ratio) {
        lastRequestedZoomRatio = ratio;
        pendingZoomRatio = ratio;

        // пробуем сразу
        boolean applied = tryApplyPendingZoomNow();
        if (!applied) {
            // ✅ если сейчас ZoomState недоступен — включаем автоприменение
            zoomRetryStartMs = 0L;
            scheduleZoomRetry();
        }
    }
}

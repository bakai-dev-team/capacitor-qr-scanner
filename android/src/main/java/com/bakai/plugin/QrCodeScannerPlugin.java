package com.bakai.plugin;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.Settings;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.camera.view.PreviewView;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@CapacitorPlugin(name = "QrCodeScanner", permissions = { @Permission(strings = Manifest.permission.CAMERA, alias = "camera") })
public class QrCodeScannerPlugin extends Plugin {

    private static final int IMAGE_MIN_SIDE_FOR_DECODE = 1200;
    private static final int IMAGE_MAX_SIDE_FOR_DECODE = 2200;

    private QrCodeScanner scanner;
    private PreviewView previewView;
    private FrameLayout cameraContainer;
    private QRScanLineOverlayView scanOverlay;

    // ✅ слой “заморозки”
    private ImageView freezeView;

    @Override
    public void load() {
        super.load();
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @PluginMethod
    public void startScan(PluginCall call) {
        final JSObject options = getOptions(call);

        final String lensFacing = options != null ? options.getString("lensFacing", "BACK") : "BACK";

        // 1080p by default gives better recognition for branded/partially-occluded QR codes.
        final int resolution = options != null ? options.getInteger("resolution", 2) : 2;

        if (getActivity() == null) {
            call.reject("Activity is null");
            return;
        }

        getActivity().runOnUiThread(() -> {
            try {
                FrameLayout root = (FrameLayout) getActivity().getWindow().getDecorView().findViewById(android.R.id.content);

                cleanupUi();
                cleanupScanner();

                cameraContainer = new FrameLayout(getContext());
                cameraContainer.setLayoutParams(
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                );
                cameraContainer.setBackgroundColor(Color.TRANSPARENT);
                cameraContainer.setClickable(false);
                cameraContainer.setFocusable(false);

                previewView = new PreviewView(getContext());
                previewView.setLayoutParams(
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                );
                previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
                previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
                previewView.setClickable(false);
                previewView.setFocusable(false);
                cameraContainer.addView(previewView);

                // overlay scanline
                scanOverlay = new QRScanLineOverlayView(getContext());
                scanOverlay.setLayoutParams(
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                );
                scanOverlay.setClickable(false);
                scanOverlay.setFocusable(false);
                cameraContainer.addView(scanOverlay);

                // камера вниз, UI вверх
                root.addView(cameraContainer, 0);

                if (getBridge() != null && getBridge().getWebView() != null) {
                    getBridge().getWebView().setBackgroundColor(Color.TRANSPARENT);
                    getBridge().getWebView().bringToFront();
                }

                scanOverlay.start();

                scanner = new QrCodeScanner(getContext());
                scanner.start(
                    getActivity(),
                    previewView,
                    lensFacing,
                    resolution,
                    new QrCodeScanner.Callback() {
                        @Override
                        public void onBarcodes(List<Barcode> barcodes) {
                            if (barcodes == null || barcodes.isEmpty()) return;
                            notifyListeners("barcodesScanned", BarcodeMapper.toJS(barcodes));
                        }

                        @Override
                        public void onError(String message) {
                            JSObject err = new JSObject();
                            err.put("message", message != null ? message : "Unknown error");
                            notifyListeners("scanError", err);
                        }

                        @Override
                        public void onZoomReady(float minRatio, float maxRatio, float currentRatio) {
                            JSObject data = new JSObject();
                            data.put("currentZoomRatio", currentRatio);
                            data.put("minZoomRatio", minRatio);
                            data.put("maxZoomRatio", maxRatio);
                            notifyListeners("zoomReady", data);
                        }
                    }
                );

                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage() != null ? e.getMessage() : "Failed to start scan");
            }
        });
    }

    @PluginMethod
    public void stopScan(PluginCall call) {
        if (getActivity() == null) {
            call.reject("Activity is null");
            return;
        }

        getActivity().runOnUiThread(() -> {
            try {
                cleanupScanner();
                cleanupUi();
                previewView = null;
                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage() != null ? e.getMessage() : "Failed to stop scan");
            }
        });
    }

    // ✅ PAUSE: незаметно (камера продолжает работать), но “как будто на паузе”
    @PluginMethod
    public void pauseScan(PluginCall call) {
        if (getActivity() == null) {
            call.reject("Activity is null");
            return;
        }

        getActivity().runOnUiThread(() -> {
            try {
                // 1) выключаем сканирование (анализ)
                if (scanner != null) scanner.pause();

                // 2) замораживаем картинку поверх preview
                if (cameraContainer != null && previewView != null) {
                    Bitmap bmp = previewView.getBitmap(); // быстрый снимок
                    if (bmp != null) {
                        if (freezeView == null) {
                            freezeView = new ImageView(getContext());
                            freezeView.setLayoutParams(
                                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                            );
                            freezeView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            freezeView.setClickable(false);
                            freezeView.setFocusable(false);

                            // Добавляем поверх preview, но под scanOverlay (если scanOverlay последний)
                            int overlayIndex = Math.max(0, cameraContainer.getChildCount() - 1);
                            cameraContainer.addView(freezeView, overlayIndex);
                        }

                        freezeView.setImageBitmap(bmp);
                        freezeView.setVisibility(ImageView.VISIBLE);
                    }
                }

                // 3) пауза scanline
                if (scanOverlay != null) scanOverlay.pause();

                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage() != null ? e.getMessage() : "Failed to pause scan");
            }
        });
    }

    // ✅ RESUME: мгновенно, без “выкл/вкл”
    @PluginMethod
    public void resumeScan(PluginCall call) {
        if (getActivity() == null) {
            call.reject("Activity is null");
            return;
        }

        getActivity().runOnUiThread(() -> {
            try {
                // 1) убираем freeze слой
                if (freezeView != null) {
                    freezeView.setImageDrawable(null);
                    freezeView.setVisibility(ImageView.GONE);
                }

                // 2) продолжаем scanline
                if (scanOverlay != null) scanOverlay.resume();

                // 3) включаем анализ обратно
                if (scanner != null) scanner.resume();

                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage() != null ? e.getMessage() : "Failed to resume scan");
            }
        });
    }

    // ===== readBarcodesFromImage / scan =====

    @PluginMethod
    public void readBarcodesFromImage(PluginCall call) {
        String path = call.getString("path");
        if (path == null || path.trim().isEmpty()) {
            call.reject("path is required");
            return;
        }

        final Uri uri = normalizePathToUri(path);
        if (uri == null) {
            call.reject("Invalid image path");
            return;
        }

        final List<InputImage> candidates = new ArrayList<>();
        final Set<Bitmap> recyclableBitmaps = new HashSet<>();

        BarcodeScannerOptions scannerOptions = new BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAllPotentialBarcodes()
            .build();

        final BarcodeScanner imageScanner = BarcodeScanning.getClient(scannerOptions);

        try {
            // 1) Native file-path decode (includes EXIF orientation handling in ML Kit).
            candidates.add(InputImage.fromFilePath(getContext(), uri));

            // 2) Fallback variants for stylized/low-contrast/angled QR codes.
            Bitmap source = loadBitmapFromUri(uri);
            if (source != null) {
                Bitmap normalized = normalizeBitmapForDecode(source);
                recyclableBitmaps.add(normalized);

                addCandidate(candidates, normalized, 0);
                addCandidate(candidates, normalized, 90);
                addCandidate(candidates, normalized, 180);
                addCandidate(candidates, normalized, 270);

                Bitmap boosted = createHighContrastBitmap(normalized);
                if (boosted != null) {
                    recyclableBitmaps.add(boosted);
                    addCandidate(candidates, boosted, 0);
                    addCandidate(candidates, boosted, 90);
                    addCandidate(candidates, boosted, 270);
                }

                Bitmap binary = createBinaryBitmap(normalized);
                if (binary != null) {
                    recyclableBitmaps.add(binary);
                    addCandidate(candidates, binary, 0);
                }

                Bitmap centerCrop = createCenteredSquare(normalized, 0.88f);
                if (centerCrop != null) {
                    recyclableBitmaps.add(centerCrop);
                    addCandidate(candidates, centerCrop, 0);
                    addCandidate(candidates, centerCrop, 90);
                }
            }
        } catch (Exception e) {
            imageScanner.close();
            recycleBitmaps(recyclableBitmaps);
            call.reject(e.getMessage() != null ? e.getMessage() : "Failed to read barcodes");
            return;
        }

        if (candidates.isEmpty()) {
            imageScanner.close();
            recycleBitmaps(recyclableBitmaps);
            call.resolve(BarcodeMapper.toJS(new ArrayList<>()));
            return;
        }

        processImageCandidates(imageScanner, candidates, 0, recyclableBitmaps, call);
    }

    @PluginMethod
    public void scan(PluginCall call) {
        JSObject options = getOptions(call);
        boolean autoZoom = options != null && options.optBoolean("autoZoom", false);

        GmsBarcodeScannerOptions.Builder builder = new GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE);
        if (autoZoom) {
            builder.enableAutoZoom();
        }

        GmsBarcodeScanner gms = GmsBarcodeScanning.getClient(getContext(), builder.build());

        gms
            .startScan()
            .addOnSuccessListener((barcode) -> {
                List<Barcode> list = new ArrayList<>();
                list.add(barcode);
                call.resolve(BarcodeMapper.toJS(list));
            })
            .addOnFailureListener((e) -> call.reject(e.getMessage() != null ? e.getMessage() : "Scan cancelled/failed"));
    }

    private JSObject getOptions(PluginCall call) {
        JSObject nested = call.getObject("options");
        if (nested != null) return nested;

        JSObject data = call.getData();
        return data != null ? data : new JSObject();
    }

    private Uri normalizePathToUri(String path) {
        if (path == null || path.trim().isEmpty()) return null;

        Uri uri = Uri.parse(path);
        if (uri.getScheme() == null || uri.getScheme().trim().isEmpty()) {
            return Uri.fromFile(new File(path));
        }
        return uri;
    }

    private Bitmap loadBitmapFromUri(Uri uri) {
        if (uri == null) return null;

        ContentResolver resolver = getContext().getContentResolver();
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return null;
            return BitmapFactory.decodeStream(input, null, decodeOptions);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap normalizeBitmapForDecode(Bitmap source) {
        if (source == null) return null;

        int width = source.getWidth();
        int height = source.getHeight();
        int minSide = Math.min(width, height);
        int maxSide = Math.max(width, height);

        float scale = 1f;
        if (maxSide > IMAGE_MAX_SIDE_FOR_DECODE) {
            scale = IMAGE_MAX_SIDE_FOR_DECODE / (float) maxSide;
        } else if (minSide < IMAGE_MIN_SIDE_FOR_DECODE) {
            scale = IMAGE_MIN_SIDE_FOR_DECODE / (float) minSide;
        }

        if (Math.abs(scale - 1f) < 0.01f) return source;

        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
            if (scaled != source) {
                try {
                    source.recycle();
                } catch (Exception ignored) {}
            }
            return scaled;
        } catch (Exception ignored) {
            return source;
        }
    }

    private Bitmap createHighContrastBitmap(Bitmap source) {
        if (source == null || source.isRecycled()) return null;

        try {
            Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0f);

            float contrast = 1.55f;
            float translate = (-0.5f * contrast + 0.5f) * 255f;
            ColorMatrix contrastMatrix = new ColorMatrix(
                new float[] { contrast, 0, 0, 0, translate, 0, contrast, 0, 0, translate, 0, 0, contrast, 0, translate, 0, 0, 0, 1, 0 }
            );
            matrix.postConcat(contrastMatrix);

            paint.setColorFilter(new ColorMatrixColorFilter(matrix));
            canvas.drawBitmap(source, 0f, 0f, paint);
            return output;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap createBinaryBitmap(Bitmap source) {
        if (source == null || source.isRecycled()) return null;

        final int width = source.getWidth();
        final int height = source.getHeight();
        final int total = width * height;
        if (total <= 0) return null;

        try {
            int[] pixels = new int[total];
            source.getPixels(pixels, 0, width, 0, 0, width, height);

            long sum = 0L;
            for (int i = 0; i < total; i++) {
                int c = pixels[i];
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;
                int luma = (int) (0.299f * r + 0.587f * g + 0.114f * b);
                sum += luma;
            }

            int threshold = (int) (sum / total);
            threshold = Math.max(80, Math.min(190, threshold));

            for (int i = 0; i < total; i++) {
                int c = pixels[i];
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;
                int luma = (int) (0.299f * r + 0.587f * g + 0.114f * b);
                pixels[i] = luma >= threshold ? Color.WHITE : Color.BLACK;
            }

            Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            output.setPixels(pixels, 0, width, 0, 0, width, height);
            return output;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap createCenteredSquare(Bitmap source, float ratio) {
        if (source == null || source.isRecycled()) return null;
        if (ratio <= 0f || ratio > 1f) return null;

        int width = source.getWidth();
        int height = source.getHeight();
        int side = Math.max(1, Math.round(Math.min(width, height) * ratio));

        if (side >= width && side >= height) return null;

        int left = Math.max(0, (width - side) / 2);
        int top = Math.max(0, (height - side) / 2);

        try {
            return Bitmap.createBitmap(source, left, top, side, side);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void addCandidate(List<InputImage> candidates, Bitmap bitmap, int rotationDegrees) {
        if (bitmap == null || bitmap.isRecycled()) return;
        if (candidates == null) return;

        try {
            candidates.add(InputImage.fromBitmap(bitmap, rotationDegrees));
        } catch (Exception ignored) {}
    }

    private List<Barcode> filterDecodedBarcodes(List<Barcode> barcodes) {
        List<Barcode> decoded = new ArrayList<>();
        if (barcodes == null || barcodes.isEmpty()) return decoded;

        for (Barcode barcode : barcodes) {
            if (barcode == null) continue;
            if (!hasBarcodePayload(barcode)) continue;
            decoded.add(barcode);
        }
        return decoded;
    }

    private boolean hasBarcodePayload(Barcode barcode) {
        String raw = barcode.getRawValue();
        if (raw != null && !raw.trim().isEmpty()) return true;

        String display = barcode.getDisplayValue();
        return display != null && !display.trim().isEmpty();
    }

    private void recycleBitmaps(Set<Bitmap> bitmaps) {
        if (bitmaps == null || bitmaps.isEmpty()) return;

        for (Bitmap bitmap : bitmaps) {
            if (bitmap == null || bitmap.isRecycled()) continue;
            try {
                bitmap.recycle();
            } catch (Exception ignored) {}
        }
        bitmaps.clear();
    }

    private void processImageCandidates(
        BarcodeScanner imageScanner,
        List<InputImage> candidates,
        int index,
        Set<Bitmap> recyclableBitmaps,
        PluginCall call
    ) {
        if (index >= candidates.size()) {
            try {
                imageScanner.close();
            } catch (Exception ignored) {}
            recycleBitmaps(recyclableBitmaps);
            call.resolve(BarcodeMapper.toJS(new ArrayList<>()));
            return;
        }

        imageScanner
            .process(candidates.get(index))
            .addOnSuccessListener((barcodes) -> {
                List<Barcode> decoded = filterDecodedBarcodes(barcodes);
                if (!decoded.isEmpty()) {
                    try {
                        imageScanner.close();
                    } catch (Exception ignored) {}
                    recycleBitmaps(recyclableBitmaps);
                    call.resolve(BarcodeMapper.toJS(decoded));
                    return;
                }

                processImageCandidates(imageScanner, candidates, index + 1, recyclableBitmaps, call);
            })
            .addOnFailureListener((e) -> processImageCandidates(imageScanner, candidates, index + 1, recyclableBitmaps, call))
            .addOnCanceledListener(() -> processImageCandidates(imageScanner, candidates, index + 1, recyclableBitmaps, call));
    }

    // ===== Permissions =====

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        JSObject result = new JSObject();
        result.put("camera", getPermissionState("camera") == PermissionState.GRANTED ? "granted" : "denied");
        call.resolve(result);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        requestPermissionForAlias("camera", call, "permissionsCallback");
    }

    @PermissionCallback
    private void permissionsCallback(PluginCall call) {
        checkPermissions(call);
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        call.resolve();
    }

    // ===== Torch =====

    @PluginMethod
    public void enableTorch(PluginCall call) {
        if (getActivity() == null) {
            call.reject("Activity is null");
            return;
        }
        getActivity().runOnUiThread(() -> {
            if (scanner != null) scanner.enableTorch();
            call.resolve();
        });
    }

    @PluginMethod
    public void disableTorch(PluginCall call) {
        if (getActivity() == null) {
            call.reject("Activity is null");
            return;
        }
        getActivity().runOnUiThread(() -> {
            if (scanner != null) scanner.disableTorch();
            call.resolve();
        });
    }

    @PluginMethod
    public void toggleTorch(PluginCall call) {
        if (getActivity() == null) {
            call.reject("Activity is null");
            return;
        }
        getActivity().runOnUiThread(() -> {
            if (scanner != null) scanner.toggleTorch();
            call.resolve();
        });
    }

    @PluginMethod
    public void isTorchEnabled(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("enabled", scanner != null && scanner.isTorchEnabled());
        call.resolve(ret);
    }

    @PluginMethod
    public void isTorchAvailable(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("available", scanner != null && scanner.isTorchAvailable());
        call.resolve(ret);
    }

    // ===== Zoom =====

    @PluginMethod
    public void setZoomRatio(PluginCall call) {
        Double ratioD = call.getDouble("zoomRatio");
        if (ratioD == null) {
            call.reject("zoomRatio is required");
            return;
        }

        float ratio = ratioD.floatValue();

        if (getActivity() == null) {
            call.reject("Activity is null");
            return;
        }

        getActivity().runOnUiThread(() -> {
            if (scanner == null) {
                call.reject("Scanner not started");
                return;
            }

            scanner.setZoomRatio(ratio);

            JSObject ret = new JSObject();
            ret.put("zoomRatio", scanner.getZoomRatio());
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void getZoomRatio(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("zoomRatio", scanner != null ? scanner.getZoomRatio() : 1);
        call.resolve(ret);
    }

    @PluginMethod
    public void getMinZoomRatio(PluginCall call) {
        if (getActivity() == null) {
            call.reject("Activity is null");
            return;
        }
        getActivity().runOnUiThread(() -> {
            JSObject ret = new JSObject();
            if (scanner == null) {
                ret.put("ready", false);
                ret.put("zoomRatio", 1);
                call.resolve(ret);
                return;
            }
            ret.put("ready", scanner.isZoomReady());
            ret.put("zoomRatio", scanner.getMinZoomRatio());
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void getMaxZoomRatio(PluginCall call) {
        if (getActivity() == null) {
            call.reject("Activity is null");
            return;
        }
        getActivity().runOnUiThread(() -> {
            JSObject ret = new JSObject();
            if (scanner == null) {
                ret.put("ready", false);
                ret.put("zoomRatio", 1);
                call.resolve(ret);
                return;
            }
            ret.put("ready", scanner.isZoomReady());
            ret.put("zoomRatio", scanner.getMaxZoomRatio());
            call.resolve(ret);
        });
    }

    // ===== Internal cleanup =====

    private void cleanupScanner() {
        if (scanner != null) {
            try {
                scanner.stop();
            } catch (Exception ignored) {}
            scanner = null;
        }
    }

    private void cleanupUi() {
        if (scanOverlay != null) {
            try {
                scanOverlay.stop();
            } catch (Exception ignored) {}
            scanOverlay = null;
        }

        if (cameraContainer != null) {
            if (freezeView != null) {
                try {
                    freezeView.setImageDrawable(null);
                    cameraContainer.removeView(freezeView);
                } catch (Exception ignored) {}
                freezeView = null;
            }

            try {
                cameraContainer.removeAllViews();
                ViewParent parent = cameraContainer.getParent();
                if (parent instanceof FrameLayout) {
                    ((FrameLayout) parent).removeView(cameraContainer);
                }
            } catch (Exception ignored) {}

            cameraContainer = null;
        }
    }
}

package com.bakai.plugin;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import java.util.ArrayList;
import java.util.List;

@CapacitorPlugin(name = "QrCodeScanner", permissions = { @Permission(strings = Manifest.permission.CAMERA, alias = "camera") })
public class QrCodeScannerPlugin extends Plugin {

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
        final JSObject options = call.getObject("options");

        final String lensFacing = options != null ? options.getString("lensFacing", "BACK") : "BACK";

        final int resolution = options != null ? options.getInteger("resolution", 1) : 1;

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

        try {
            InputImage image = InputImage.fromFilePath(getContext(), Uri.parse(path));
            BarcodeScanner sc = BarcodeScanning.getClient();

            sc
                .process(image)
                .addOnSuccessListener((barcodes) -> call.resolve(BarcodeMapper.toJS(barcodes)))
                .addOnFailureListener((e) -> call.reject(e.getMessage() != null ? e.getMessage() : "Failed to read barcodes"));
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Failed to read barcodes");
        }
    }

    @PluginMethod
    public void scan(PluginCall call) {
        GmsBarcodeScanner gms = GmsBarcodeScanning.getClient(getContext());

        gms
            .startScan()
            .addOnSuccessListener((barcode) -> {
                List<Barcode> list = new ArrayList<>();
                list.add(barcode);
                call.resolve(BarcodeMapper.toJS(list));
            })
            .addOnFailureListener((e) -> call.reject(e.getMessage() != null ? e.getMessage() : "Scan cancelled/failed"));
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

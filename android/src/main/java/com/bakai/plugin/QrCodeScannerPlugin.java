package com.bakai.plugin;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.view.ViewParent;
import android.widget.FrameLayout;
import androidx.camera.view.PreviewView;
import com.getcapacitor.*;
import com.getcapacitor.annotation.*;
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

    @Override
    public void load() {
        super.load();
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @PluginMethod
    public void startScan(PluginCall call) {
        String lensFacing = call.getObject("options") != null ? call.getObject("options").getString("lensFacing", "BACK") : "BACK";

        int resolution = call.getObject("options") != null ? call.getObject("options").getInteger("resolution", 1) : 1;

        getActivity().runOnUiThread(() -> {
            try {
                FrameLayout root = (FrameLayout) getActivity().getWindow().getDecorView().findViewById(android.R.id.content);

                cameraContainer = new FrameLayout(getContext());
                cameraContainer.setLayoutParams(
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                );

                previewView = new PreviewView(getContext());
                previewView.setLayoutParams(
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                );
                previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
                previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

                cameraContainer.addView(previewView);

                // под WebView
                root.addView(cameraContainer, 0);

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
                            err.put("message", message);
                            notifyListeners("scanError", err);
                        }

                        @Override
                        public void onZoomReady(float minRatio, float maxRatio, float currentRatio) {
                            JSObject data = new JSObject();
                            data.put("zoomRatio", currentRatio); // текущее значение
                            data.put("zoomRatio", minRatio); // диапазон
                            data.put("zoomRatio", maxRatio);
                            notifyListeners("zoomReady", data);
                        }
                    }
                );

                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage());
            }
        });
    }

    @PluginMethod
    public void stopScan(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                if (scanner != null) {
                    scanner.stop();
                    scanner = null;
                }

                if (cameraContainer != null) {
                    cameraContainer.removeAllViews();
                    ViewParent parent = cameraContainer.getParent();
                    if (parent instanceof FrameLayout) {
                        ((FrameLayout) parent).removeView(cameraContainer);
                    }
                    cameraContainer = null;
                }

                previewView = null;
                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage());
            }
        });
    }

    @PluginMethod
    public void pauseScan(PluginCall call) {
        if (scanner != null) scanner.pause();
        call.resolve();
    }

    @PluginMethod
    public void resumeScan(PluginCall call) {
        if (scanner != null) scanner.resume();
        call.resolve();
    }

    @PluginMethod
    public void readBarcodesFromImage(PluginCall call) {
        String path = call.getString("path");
        try {
            InputImage image = InputImage.fromFilePath(getContext(), Uri.parse(path));
            BarcodeScanner sc = BarcodeScanning.getClient();

            sc
                .process(image)
                .addOnSuccessListener((barcodes) -> call.resolve(BarcodeMapper.toJS(barcodes)))
                .addOnFailureListener((e) -> call.reject(e.getMessage()));
        } catch (Exception e) {
            call.reject(e.getMessage());
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
            .addOnFailureListener((e) -> call.reject(e.getMessage()));
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
        getActivity().runOnUiThread(() -> {
            if (scanner != null) scanner.enableTorch();
            call.resolve();
        });
    }

    @PluginMethod
    public void disableTorch(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (scanner != null) scanner.disableTorch();
            call.resolve();
        });
    }

    @PluginMethod
    public void toggleTorch(PluginCall call) {
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

        getActivity().runOnUiThread(() -> {
            if (scanner == null) {
                call.reject("Scanner not started");
                return;
            }

            scanner.setZoomRatio(ratio);

            JSObject ret = new JSObject();
            ret.put("zoomRatio", scanner.getZoomRatio()); // единый ключ
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
}

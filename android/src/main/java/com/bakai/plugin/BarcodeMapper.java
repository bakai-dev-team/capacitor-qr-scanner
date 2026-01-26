package com.bakai.plugin;

import android.graphics.Point;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.google.mlkit.vision.barcode.common.Barcode;
import java.util.List;

public final class BarcodeMapper {

    private BarcodeMapper() {}

    public static JSObject toJS(List<Barcode> barcodes) {
        JSArray array = new JSArray();
        for (Barcode barcode : barcodes) {
            array.put(toJS(barcode));
        }

        JSObject result = new JSObject();
        result.put("barcodes", array);
        return result;
    }

    public static JSObject toJS(Barcode b) {
        JSObject o = new JSObject();

        o.put("displayValue", safe(b.getDisplayValue()));
        o.put("rawValue", safe(b.getRawValue()));
        o.put("format", mapFormat(b.getFormat()));
        o.put("valueType", mapValueType(b.getValueType()));

        if (b.getRawBytes() != null) {
            JSArray bytes = new JSArray();
            for (byte byt : b.getRawBytes()) {
                bytes.put((int) byt & 0xff);
            }
            o.put("bytes", bytes);
        }

        Point[] points = b.getCornerPoints();
        if (points != null && points.length == 4) {
            JSArray corners = new JSArray();
            for (Point p : points) {
                JSArray point = new JSArray();
                point.put(p.x);
                point.put(p.y);
                corners.put(point);
            }
            o.put("cornerPoints", corners);
        }

        switch (b.getValueType()) {
            case Barcode.TYPE_URL:
                if (b.getUrl() != null) {
                    JSObject url = new JSObject();
                    url.put("url", safe(b.getUrl().getUrl()));
                    url.put("title", safe(b.getUrl().getTitle()));
                    o.put("urlBookmark", url);
                }
                break;
            case Barcode.TYPE_WIFI:
                if (b.getWifi() != null) {
                    JSObject wifi = new JSObject();
                    wifi.put("ssid", safe(b.getWifi().getSsid()));
                    wifi.put("password", safe(b.getWifi().getPassword()));
                    wifi.put("encryptionType", mapWifiEncryption(b.getWifi().getEncryptionType()));
                    o.put("wifi", wifi);
                }
                break;
            case Barcode.TYPE_EMAIL:
                if (b.getEmail() != null) {
                    JSObject email = new JSObject();
                    email.put("address", safe(b.getEmail().getAddress()));
                    email.put("subject", safe(b.getEmail().getSubject()));
                    email.put("body", safe(b.getEmail().getBody()));
                    email.put("type", mapEmailType(b.getEmail().getType()));
                    o.put("email", email);
                }
                break;
            case Barcode.TYPE_PHONE:
                if (b.getPhone() != null) {
                    JSObject phone = new JSObject();
                    phone.put("number", safe(b.getPhone().getNumber()));
                    phone.put("type", mapPhoneType(b.getPhone().getType()));
                    o.put("phone", phone);
                }
                break;
            case Barcode.TYPE_GEO:
                if (b.getGeoPoint() != null) {
                    JSObject geo = new JSObject();
                    geo.put("latitude", b.getGeoPoint().getLat());
                    geo.put("longitude", b.getGeoPoint().getLng());
                    o.put("geoPoint", geo);
                }
                break;
            case Barcode.TYPE_SMS:
                if (b.getSms() != null) {
                    JSObject sms = new JSObject();
                    sms.put("phoneNumber", safe(b.getSms().getPhoneNumber()));
                    sms.put("message", safe(b.getSms().getMessage()));
                    o.put("sms", sms);
                }
                break;
        }

        return o;
    }

    private static String mapFormat(int f) {
        switch (f) {
            case Barcode.FORMAT_QR_CODE:
                return "QR_CODE";
            case Barcode.FORMAT_CODE_128:
                return "CODE_128";
            case Barcode.FORMAT_CODE_39:
                return "CODE_39";
            case Barcode.FORMAT_CODE_93:
                return "CODE_93";
            case Barcode.FORMAT_EAN_8:
                return "EAN_8";
            case Barcode.FORMAT_EAN_13:
                return "EAN_13";
            case Barcode.FORMAT_UPC_A:
                return "UPC_A";
            case Barcode.FORMAT_UPC_E:
                return "UPC_E";
            case Barcode.FORMAT_PDF417:
                return "PDF_417";
            case Barcode.FORMAT_DATA_MATRIX:
                return "DATA_MATRIX";
            case Barcode.FORMAT_AZTEC:
                return "AZTEC";
            case Barcode.FORMAT_ITF:
                return "ITF";
            case Barcode.FORMAT_CODABAR:
                return "CODABAR";
            default:
                return "UNKNOWN";
        }
    }

    private static String mapValueType(int t) {
        switch (t) {
            case Barcode.TYPE_TEXT:
                return "TEXT";
            case Barcode.TYPE_URL:
                return "URL";
            case Barcode.TYPE_WIFI:
                return "WIFI";
            case Barcode.TYPE_PHONE:
                return "PHONE";
            case Barcode.TYPE_EMAIL:
                return "EMAIL";
            case Barcode.TYPE_SMS:
                return "SMS";
            case Barcode.TYPE_GEO:
                return "GEO";
            default:
                return "UNKNOWN";
        }
    }

    private static int mapWifiEncryption(int t) {
        switch (t) {
            case Barcode.WiFi.TYPE_OPEN:
                return 1;
            case Barcode.WiFi.TYPE_WEP:
                return 2;
            case Barcode.WiFi.TYPE_WPA:
                return 3;
            default:
                return 1;
        }
    }

    private static int mapEmailType(int t) {
        switch (t) {
            case Barcode.Email.TYPE_HOME:
                return 0;
            case Barcode.Email.TYPE_WORK:
                return 2;
            default:
                return 1;
        }
    }

    private static int mapPhoneType(int t) {
        switch (t) {
            case Barcode.Phone.TYPE_MOBILE:
                return 2;
            case Barcode.Phone.TYPE_HOME:
                return 1;
            case Barcode.Phone.TYPE_WORK:
                return 4;
            default:
                return 3;
        }
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }
}

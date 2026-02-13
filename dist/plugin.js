var capacitorQrCodeScanner = (function (exports, core) {
    'use strict';

    /**
     * Enums
     */
    exports.BarcodeFormat = void 0;
    (function (BarcodeFormat) {
        BarcodeFormat["Aztec"] = "AZTEC";
        BarcodeFormat["Codabar"] = "CODABAR";
        BarcodeFormat["Code39"] = "CODE_39";
        BarcodeFormat["Code93"] = "CODE_93";
        BarcodeFormat["Code128"] = "CODE_128";
        BarcodeFormat["DataMatrix"] = "DATA_MATRIX";
        BarcodeFormat["Ean8"] = "EAN_8";
        BarcodeFormat["Ean13"] = "EAN_13";
        BarcodeFormat["Itf"] = "ITF";
        BarcodeFormat["Pdf417"] = "PDF_417";
        BarcodeFormat["QrCode"] = "QR_CODE";
        BarcodeFormat["UpcA"] = "UPC_A";
        BarcodeFormat["UpcE"] = "UPC_E";
    })(exports.BarcodeFormat || (exports.BarcodeFormat = {}));
    exports.BarcodeValueType = void 0;
    (function (BarcodeValueType) {
        BarcodeValueType["CalendarEvent"] = "CALENDAR_EVENT";
        BarcodeValueType["ContactInfo"] = "CONTACT_INFO";
        BarcodeValueType["DriversLicense"] = "DRIVERS_LICENSE";
        BarcodeValueType["Email"] = "EMAIL";
        BarcodeValueType["Geo"] = "GEO";
        BarcodeValueType["Isbn"] = "ISBN";
        BarcodeValueType["Phone"] = "PHONE";
        BarcodeValueType["Product"] = "PRODUCT";
        BarcodeValueType["Sms"] = "SMS";
        BarcodeValueType["Text"] = "TEXT";
        BarcodeValueType["Url"] = "URL";
        BarcodeValueType["Wifi"] = "WIFI";
        BarcodeValueType["Unknown"] = "UNKNOWN";
    })(exports.BarcodeValueType || (exports.BarcodeValueType = {}));
    exports.Resolution = void 0;
    (function (Resolution) {
        Resolution[Resolution["640x480"] = 0] = "640x480";
        Resolution[Resolution["1280x720"] = 1] = "1280x720";
        Resolution[Resolution["1920x1080"] = 2] = "1920x1080";
        Resolution[Resolution["3840x2160"] = 3] = "3840x2160";
    })(exports.Resolution || (exports.Resolution = {}));
    exports.LensFacing = void 0;
    (function (LensFacing) {
        LensFacing["Front"] = "FRONT";
        LensFacing["Back"] = "BACK";
    })(exports.LensFacing || (exports.LensFacing = {}));
    exports.GoogleBarcodeScannerModuleInstallState = void 0;
    (function (GoogleBarcodeScannerModuleInstallState) {
        GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["UNKNOWN"] = 0] = "UNKNOWN";
        GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["PENDING"] = 1] = "PENDING";
        GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["DOWNLOADING"] = 2] = "DOWNLOADING";
        GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["CANCELED"] = 3] = "CANCELED";
        GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["COMPLETED"] = 4] = "COMPLETED";
        GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["FAILED"] = 5] = "FAILED";
        GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["INSTALLING"] = 6] = "INSTALLING";
        GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["DOWNLOAD_PAUSED"] = 7] = "DOWNLOAD_PAUSED";
    })(exports.GoogleBarcodeScannerModuleInstallState || (exports.GoogleBarcodeScannerModuleInstallState = {}));
    exports.AddressType = void 0;
    (function (AddressType) {
        AddressType[AddressType["HOME"] = 0] = "HOME";
        AddressType[AddressType["UNKNOWN"] = 1] = "UNKNOWN";
        AddressType[AddressType["WORK"] = 2] = "WORK";
    })(exports.AddressType || (exports.AddressType = {}));
    exports.EmailFormatType = void 0;
    (function (EmailFormatType) {
        EmailFormatType[EmailFormatType["HOME"] = 0] = "HOME";
        EmailFormatType[EmailFormatType["UNKNOWN"] = 1] = "UNKNOWN";
        EmailFormatType[EmailFormatType["WORK"] = 2] = "WORK";
    })(exports.EmailFormatType || (exports.EmailFormatType = {}));
    exports.PhoneFormatType = void 0;
    (function (PhoneFormatType) {
        PhoneFormatType[PhoneFormatType["FAX"] = 0] = "FAX";
        PhoneFormatType[PhoneFormatType["HOME"] = 1] = "HOME";
        PhoneFormatType[PhoneFormatType["MOBILE"] = 2] = "MOBILE";
        PhoneFormatType[PhoneFormatType["UNKNOWN"] = 3] = "UNKNOWN";
        PhoneFormatType[PhoneFormatType["WORK"] = 4] = "WORK";
    })(exports.PhoneFormatType || (exports.PhoneFormatType = {}));
    exports.WifiEncryptionType = void 0;
    (function (WifiEncryptionType) {
        WifiEncryptionType[WifiEncryptionType["OPEN"] = 1] = "OPEN";
        WifiEncryptionType[WifiEncryptionType["WEP"] = 2] = "WEP";
        WifiEncryptionType[WifiEncryptionType["WPA"] = 3] = "WPA";
    })(exports.WifiEncryptionType || (exports.WifiEncryptionType = {}));

    const QrCodeScanner = core.registerPlugin('QrCodeScanner', {
        web: () => Promise.resolve().then(function () { return web; }).then((m) => new m.QrCodeScannerWeb()),
    });

    class QrCodeScannerWeb extends core.WebPlugin {
        async startScan(_options) {
            throw this.unavailable('Camera preview not available on web');
        }
        async stopScan() {
            // no-op
        }
        async pauseScan() {
            // no-op
        }
        async resumeScan() {
            // no-op
        }
        async readBarcodesFromImage(_options) {
            throw this.unavailable('readBarcodesFromImage not supported on web');
        }
        async scan(_options) {
            throw this.unavailable('scan not supported on web');
        }
        async isSupported() {
            return { supported: false };
        }
        async enableTorch() {
            // no-op
        }
        async disableTorch() {
            // no-op
        }
        async toggleTorch() {
            // no-op
        }
        async isTorchEnabled() {
            return { enabled: false };
        }
        async isTorchAvailable() {
            return { available: false };
        }
        async setZoomRatio(_options) {
            // no-op
        }
        async getZoomRatio() {
            return { zoomRatio: 1 };
        }
        async getMinZoomRatio() {
            return { zoomRatio: 1 };
        }
        async getMaxZoomRatio() {
            return { zoomRatio: 1 };
        }
        async openSettings() {
            // no-op
        }
        async isGoogleBarcodeScannerModuleAvailable() {
            return { available: false };
        }
        async installGoogleBarcodeScannerModule() {
            // no-op
        }
        async checkPermissions() {
            // web fallback: treat as denied
            return { camera: 'denied' };
        }
        async requestPermissions() {
            // web fallback: treat as denied
            return { camera: 'denied' };
        }
    }

    var web = /*#__PURE__*/Object.freeze({
        __proto__: null,
        QrCodeScannerWeb: QrCodeScannerWeb
    });

    exports.QrCodeScanner = QrCodeScanner;

    return exports;

})({}, capacitorExports);
//# sourceMappingURL=plugin.js.map

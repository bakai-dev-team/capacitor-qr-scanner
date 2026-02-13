/**
 * Enums
 */
export var BarcodeFormat;
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
})(BarcodeFormat || (BarcodeFormat = {}));
export var BarcodeValueType;
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
})(BarcodeValueType || (BarcodeValueType = {}));
export var Resolution;
(function (Resolution) {
    Resolution[Resolution["640x480"] = 0] = "640x480";
    Resolution[Resolution["1280x720"] = 1] = "1280x720";
    Resolution[Resolution["1920x1080"] = 2] = "1920x1080";
    Resolution[Resolution["3840x2160"] = 3] = "3840x2160";
})(Resolution || (Resolution = {}));
export var LensFacing;
(function (LensFacing) {
    LensFacing["Front"] = "FRONT";
    LensFacing["Back"] = "BACK";
})(LensFacing || (LensFacing = {}));
export var GoogleBarcodeScannerModuleInstallState;
(function (GoogleBarcodeScannerModuleInstallState) {
    GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["UNKNOWN"] = 0] = "UNKNOWN";
    GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["PENDING"] = 1] = "PENDING";
    GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["DOWNLOADING"] = 2] = "DOWNLOADING";
    GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["CANCELED"] = 3] = "CANCELED";
    GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["COMPLETED"] = 4] = "COMPLETED";
    GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["FAILED"] = 5] = "FAILED";
    GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["INSTALLING"] = 6] = "INSTALLING";
    GoogleBarcodeScannerModuleInstallState[GoogleBarcodeScannerModuleInstallState["DOWNLOAD_PAUSED"] = 7] = "DOWNLOAD_PAUSED";
})(GoogleBarcodeScannerModuleInstallState || (GoogleBarcodeScannerModuleInstallState = {}));
export var AddressType;
(function (AddressType) {
    AddressType[AddressType["HOME"] = 0] = "HOME";
    AddressType[AddressType["UNKNOWN"] = 1] = "UNKNOWN";
    AddressType[AddressType["WORK"] = 2] = "WORK";
})(AddressType || (AddressType = {}));
export var EmailFormatType;
(function (EmailFormatType) {
    EmailFormatType[EmailFormatType["HOME"] = 0] = "HOME";
    EmailFormatType[EmailFormatType["UNKNOWN"] = 1] = "UNKNOWN";
    EmailFormatType[EmailFormatType["WORK"] = 2] = "WORK";
})(EmailFormatType || (EmailFormatType = {}));
export var PhoneFormatType;
(function (PhoneFormatType) {
    PhoneFormatType[PhoneFormatType["FAX"] = 0] = "FAX";
    PhoneFormatType[PhoneFormatType["HOME"] = 1] = "HOME";
    PhoneFormatType[PhoneFormatType["MOBILE"] = 2] = "MOBILE";
    PhoneFormatType[PhoneFormatType["UNKNOWN"] = 3] = "UNKNOWN";
    PhoneFormatType[PhoneFormatType["WORK"] = 4] = "WORK";
})(PhoneFormatType || (PhoneFormatType = {}));
export var WifiEncryptionType;
(function (WifiEncryptionType) {
    WifiEncryptionType[WifiEncryptionType["OPEN"] = 1] = "OPEN";
    WifiEncryptionType[WifiEncryptionType["WEP"] = 2] = "WEP";
    WifiEncryptionType[WifiEncryptionType["WPA"] = 3] = "WPA";
})(WifiEncryptionType || (WifiEncryptionType = {}));
//# sourceMappingURL=definitions.js.map
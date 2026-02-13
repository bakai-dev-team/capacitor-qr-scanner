import type { PermissionState, PluginListenerHandle } from '@capacitor/core';
export interface QrCodeScannerPlugin {
    startScan(options?: StartScanOptions): Promise<void>;
    stopScan(): Promise<void>;
    pauseScan(): Promise<void>;
    resumeScan(): Promise<void>;
    readBarcodesFromImage(options: ReadBarcodesFromImageOptions): Promise<ReadBarcodesFromImageResult>;
    scan(options?: ScanOptions): Promise<ScanResult>;
    isSupported(): Promise<IsSupportedResult>;
    enableTorch(): Promise<void>;
    disableTorch(): Promise<void>;
    toggleTorch(): Promise<void>;
    isTorchEnabled(): Promise<IsTorchEnabledResult>;
    isTorchAvailable(): Promise<IsTorchAvailableResult>;
    setZoomRatio(options: SetZoomRatioOptions): Promise<void>;
    getZoomRatio(): Promise<GetZoomRatioResult>;
    getMinZoomRatio(): Promise<GetMinZoomRatioResult>;
    getMaxZoomRatio(): Promise<GetMaxZoomRatioResult>;
    openSettings(): Promise<void>;
    isGoogleBarcodeScannerModuleAvailable(): Promise<IsGoogleBarcodeScannerModuleAvailableResult>;
    installGoogleBarcodeScannerModule(): Promise<void>;
    checkPermissions(): Promise<PermissionStatus>;
    requestPermissions(): Promise<PermissionStatus>;
    addListener(eventName: 'barcodesScanned', listenerFunc: (event: BarcodesScannedEvent) => void): Promise<PluginListenerHandle>;
    addListener(eventName: 'scanError', listenerFunc: (event: ScanErrorEvent) => void): Promise<PluginListenerHandle>;
    addListener(eventName: 'googleBarcodeScannerModuleInstallProgress', listenerFunc: (event: GoogleBarcodeScannerModuleInstallProgressEvent) => void): Promise<PluginListenerHandle>;
    removeAllListeners(): Promise<void>;
}
/**
 * Options
 */
export interface StartScanOptions {
    formats?: BarcodeFormat[];
    lensFacing?: LensFacing;
    resolution?: Resolution;
    enableMultitaskingCameraAccess?: boolean;
    videoElement?: HTMLVideoElement;
}
export interface ReadBarcodesFromImageOptions {
    formats?: BarcodeFormat[];
    path: string;
}
export interface ScanOptions {
    formats?: BarcodeFormat[];
    autoZoom?: boolean;
}
/**
 * Results
 */
export interface ReadBarcodesFromImageResult {
    barcodes: Barcode[];
}
export interface ScanResult {
    barcodes: Barcode[];
}
export interface IsSupportedResult {
    supported: boolean;
}
export interface IsTorchEnabledResult {
    enabled: boolean;
}
export interface IsTorchAvailableResult {
    available: boolean;
}
export interface SetZoomRatioOptions {
    zoomRatio: number;
}
export interface GetZoomRatioResult {
    zoomRatio: number;
}
export interface GetMinZoomRatioResult {
    zoomRatio: number;
}
export interface GetMaxZoomRatioResult {
    zoomRatio: number;
}
export interface IsGoogleBarcodeScannerModuleAvailableResult {
    available: boolean;
}
/**
 * Permissions
 */
export type CameraPermissionState = PermissionState | 'limited';
export interface PermissionStatus {
    camera: CameraPermissionState;
}
/**
 * Events
 */
export interface BarcodesScannedEvent {
    barcodes: Barcode[];
}
export interface ScanErrorEvent {
    message: string;
}
export interface GoogleBarcodeScannerModuleInstallProgressEvent {
    state: GoogleBarcodeScannerModuleInstallState;
    progress?: number;
}
/**
 * Barcode model
 */
export interface Barcode {
    bytes?: number[];
    calendarEvent?: BarcodeCalendarEvent;
    contactInfo?: BarcodeContactInfo;
    cornerPoints?: [[number, number], [number, number], [number, number], [number, number]];
    displayValue: string;
    driverLicense?: BarcodeDriverLicense;
    email?: BarcodeEmail;
    format: BarcodeFormat;
    geoPoint?: BarcodeGeoPoint;
    phone?: BarcodePhone;
    rawValue: string;
    sms?: BarcodeSms;
    urlBookmark?: BarcodeUrlBookmark;
    valueType: BarcodeValueType;
    wifi?: BarcodeWifi;
}
export interface BarcodeCalendarEvent {
    description?: string;
    end?: string;
    location?: string;
    organizer?: string;
    start?: string;
    status?: string;
    summary?: string;
}
export interface BarcodeContactInfo {
    addresses?: Address[];
    emails?: BarcodeEmail[];
    personName?: PersonName;
    organization?: string;
    phones?: BarcodePhone[];
    title?: string;
    urls?: string[];
}
export interface Address {
    addressLines?: string[];
    type?: AddressType;
}
export interface BarcodeDriverLicense {
    addressCity?: string;
    addressState?: string;
    addressStreet?: string;
    addressZip?: string;
    birthDate?: string;
    documentType?: string;
    expiryDate?: string;
    firstName?: string;
    gender?: string;
    issueDate?: string;
    issuingCountry?: string;
    lastName?: string;
    licenseNumber?: string;
    middleName?: string;
}
export interface BarcodeEmail {
    address?: string;
    body?: string;
    subject?: string;
    type: EmailFormatType;
}
export interface BarcodeGeoPoint {
    latitude?: number;
    longitude?: number;
}
export interface BarcodePhone {
    number?: string;
    type?: PhoneFormatType;
}
export interface PersonName {
    first?: string;
    formattedName?: string;
    last?: string;
    middle?: string;
    prefix?: string;
    pronunciation?: string;
    suffix?: string;
}
export interface BarcodeSms {
    phoneNumber?: string;
    message?: string;
}
export interface BarcodeUrlBookmark {
    url?: string;
    title?: string;
}
export interface BarcodeWifi {
    encryptionType: WifiEncryptionType;
    password?: string;
    ssid?: string;
}
/**
 * Enums
 */
export declare enum BarcodeFormat {
    Aztec = "AZTEC",
    Codabar = "CODABAR",
    Code39 = "CODE_39",
    Code93 = "CODE_93",
    Code128 = "CODE_128",
    DataMatrix = "DATA_MATRIX",
    Ean8 = "EAN_8",
    Ean13 = "EAN_13",
    Itf = "ITF",
    Pdf417 = "PDF_417",
    QrCode = "QR_CODE",
    UpcA = "UPC_A",
    UpcE = "UPC_E"
}
export declare enum BarcodeValueType {
    CalendarEvent = "CALENDAR_EVENT",
    ContactInfo = "CONTACT_INFO",
    DriversLicense = "DRIVERS_LICENSE",
    Email = "EMAIL",
    Geo = "GEO",
    Isbn = "ISBN",
    Phone = "PHONE",
    Product = "PRODUCT",
    Sms = "SMS",
    Text = "TEXT",
    Url = "URL",
    Wifi = "WIFI",
    Unknown = "UNKNOWN"
}
export declare enum Resolution {
    '640x480' = 0,
    '1280x720' = 1,
    '1920x1080' = 2,
    '3840x2160' = 3
}
export declare enum LensFacing {
    Front = "FRONT",
    Back = "BACK"
}
export declare enum GoogleBarcodeScannerModuleInstallState {
    UNKNOWN = 0,
    PENDING = 1,
    DOWNLOADING = 2,
    CANCELED = 3,
    COMPLETED = 4,
    FAILED = 5,
    INSTALLING = 6,
    DOWNLOAD_PAUSED = 7
}
export declare enum AddressType {
    HOME = 0,
    UNKNOWN = 1,
    WORK = 2
}
export declare enum EmailFormatType {
    HOME = 0,
    UNKNOWN = 1,
    WORK = 2
}
export declare enum PhoneFormatType {
    FAX = 0,
    HOME = 1,
    MOBILE = 2,
    UNKNOWN = 3,
    WORK = 4
}
export declare enum WifiEncryptionType {
    OPEN = 1,
    WEP = 2,
    WPA = 3
}

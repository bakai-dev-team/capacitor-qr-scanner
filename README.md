# capacitor-qr-scanner

For scanning qr

## Install

```bash
npm install capacitor-qr-scanner
npx cap sync
```

## API

<docgen-index>

* [`startScan(...)`](#startscan)
* [`stopScan()`](#stopscan)
* [`pauseScan()`](#pausescan)
* [`resumeScan()`](#resumescan)
* [`readBarcodesFromImage(...)`](#readbarcodesfromimage)
* [`scan(...)`](#scan)
* [`isSupported()`](#issupported)
* [`enableTorch()`](#enabletorch)
* [`disableTorch()`](#disabletorch)
* [`toggleTorch()`](#toggletorch)
* [`isTorchEnabled()`](#istorchenabled)
* [`isTorchAvailable()`](#istorchavailable)
* [`setZoomRatio(...)`](#setzoomratio)
* [`getZoomRatio()`](#getzoomratio)
* [`getMinZoomRatio()`](#getminzoomratio)
* [`getMaxZoomRatio()`](#getmaxzoomratio)
* [`openSettings()`](#opensettings)
* [`isGoogleBarcodeScannerModuleAvailable()`](#isgooglebarcodescannermoduleavailable)
* [`installGoogleBarcodeScannerModule()`](#installgooglebarcodescannermodule)
* [`checkPermissions()`](#checkpermissions)
* [`requestPermissions()`](#requestpermissions)
* [`addListener('barcodesScanned', ...)`](#addlistenerbarcodesscanned-)
* [`addListener('scanError', ...)`](#addlistenerscanerror-)
* [`addListener('previewReady', ...)`](#addlistenerpreviewready-)
* [`addListener('googleBarcodeScannerModuleInstallProgress', ...)`](#addlistenergooglebarcodescannermoduleinstallprogress-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### startScan(...)

```typescript
startScan(options?: StartScanOptions | undefined) => Promise<void>
```

| Param         | Type                                                          |
| ------------- | ------------------------------------------------------------- |
| **`options`** | <code><a href="#startscanoptions">StartScanOptions</a></code> |

--------------------


### stopScan()

```typescript
stopScan() => Promise<void>
```

--------------------


### pauseScan()

```typescript
pauseScan() => Promise<void>
```

--------------------


### resumeScan()

```typescript
resumeScan() => Promise<void>
```

--------------------


### readBarcodesFromImage(...)

```typescript
readBarcodesFromImage(options: ReadBarcodesFromImageOptions) => Promise<ReadBarcodesFromImageResult>
```

| Param         | Type                                                                                  |
| ------------- | ------------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#readbarcodesfromimageoptions">ReadBarcodesFromImageOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#readbarcodesfromimageresult">ReadBarcodesFromImageResult</a>&gt;</code>

--------------------


### scan(...)

```typescript
scan(options?: ScanOptions | undefined) => Promise<ScanResult>
```

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#scanoptions">ScanOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#scanresult">ScanResult</a>&gt;</code>

--------------------


### isSupported()

```typescript
isSupported() => Promise<IsSupportedResult>
```

**Returns:** <code>Promise&lt;<a href="#issupportedresult">IsSupportedResult</a>&gt;</code>

--------------------


### enableTorch()

```typescript
enableTorch() => Promise<void>
```

--------------------


### disableTorch()

```typescript
disableTorch() => Promise<void>
```

--------------------


### toggleTorch()

```typescript
toggleTorch() => Promise<void>
```

--------------------


### isTorchEnabled()

```typescript
isTorchEnabled() => Promise<IsTorchEnabledResult>
```

**Returns:** <code>Promise&lt;<a href="#istorchenabledresult">IsTorchEnabledResult</a>&gt;</code>

--------------------


### isTorchAvailable()

```typescript
isTorchAvailable() => Promise<IsTorchAvailableResult>
```

**Returns:** <code>Promise&lt;<a href="#istorchavailableresult">IsTorchAvailableResult</a>&gt;</code>

--------------------


### setZoomRatio(...)

```typescript
setZoomRatio(options: SetZoomRatioOptions) => Promise<void>
```

| Param         | Type                                                                |
| ------------- | ------------------------------------------------------------------- |
| **`options`** | <code><a href="#setzoomratiooptions">SetZoomRatioOptions</a></code> |

--------------------


### getZoomRatio()

```typescript
getZoomRatio() => Promise<GetZoomRatioResult>
```

**Returns:** <code>Promise&lt;<a href="#getzoomratioresult">GetZoomRatioResult</a>&gt;</code>

--------------------


### getMinZoomRatio()

```typescript
getMinZoomRatio() => Promise<GetMinZoomRatioResult>
```

**Returns:** <code>Promise&lt;<a href="#getminzoomratioresult">GetMinZoomRatioResult</a>&gt;</code>

--------------------


### getMaxZoomRatio()

```typescript
getMaxZoomRatio() => Promise<GetMaxZoomRatioResult>
```

**Returns:** <code>Promise&lt;<a href="#getmaxzoomratioresult">GetMaxZoomRatioResult</a>&gt;</code>

--------------------


### openSettings()

```typescript
openSettings() => Promise<void>
```

--------------------


### isGoogleBarcodeScannerModuleAvailable()

```typescript
isGoogleBarcodeScannerModuleAvailable() => Promise<IsGoogleBarcodeScannerModuleAvailableResult>
```

**Returns:** <code>Promise&lt;<a href="#isgooglebarcodescannermoduleavailableresult">IsGoogleBarcodeScannerModuleAvailableResult</a>&gt;</code>

--------------------


### installGoogleBarcodeScannerModule()

```typescript
installGoogleBarcodeScannerModule() => Promise<void>
```

--------------------


### checkPermissions()

```typescript
checkPermissions() => Promise<PermissionStatus>
```

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### requestPermissions()

```typescript
requestPermissions() => Promise<PermissionStatus>
```

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### addListener('barcodesScanned', ...)

```typescript
addListener(eventName: 'barcodesScanned', listenerFunc: (event: BarcodesScannedEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                      |
| ------------------ | ----------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'barcodesScanned'</code>                                                            |
| **`listenerFunc`** | <code>(event: <a href="#barcodesscannedevent">BarcodesScannedEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('scanError', ...)

```typescript
addListener(eventName: 'scanError', listenerFunc: (event: ScanErrorEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                          |
| ------------------ | ----------------------------------------------------------------------------- |
| **`eventName`**    | <code>'scanError'</code>                                                      |
| **`listenerFunc`** | <code>(event: <a href="#scanerrorevent">ScanErrorEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('previewReady', ...)

```typescript
addListener(eventName: 'previewReady', listenerFunc: () => void) => Promise<PluginListenerHandle>
```

| Param              | Type                        |
| ------------------ | --------------------------- |
| **`eventName`**    | <code>'previewReady'</code> |
| **`listenerFunc`** | <code>() =&gt; void</code>  |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('googleBarcodeScannerModuleInstallProgress', ...)

```typescript
addListener(eventName: 'googleBarcodeScannerModuleInstallProgress', listenerFunc: (event: GoogleBarcodeScannerModuleInstallProgressEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                                                                          |
| ------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'googleBarcodeScannerModuleInstallProgress'</code>                                                                                      |
| **`listenerFunc`** | <code>(event: <a href="#googlebarcodescannermoduleinstallprogressevent">GoogleBarcodeScannerModuleInstallProgressEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------


### Interfaces


#### StartScanOptions

Options

| Prop                                 | Type                                              |
| ------------------------------------ | ------------------------------------------------- |
| **`formats`**                        | <code>BarcodeFormat[]</code>                      |
| **`lensFacing`**                     | <code><a href="#lensfacing">LensFacing</a></code> |
| **`resolution`**                     | <code><a href="#resolution">Resolution</a></code> |
| **`enableMultitaskingCameraAccess`** | <code>boolean</code>                              |
| **`videoElement`**                   | <code>HTMLVideoElement</code>                     |


#### ReadBarcodesFromImageResult

Results

| Prop           | Type                   |
| -------------- | ---------------------- |
| **`barcodes`** | <code>Barcode[]</code> |


#### Barcode

<a href="#barcode">Barcode</a> model

| Prop                | Type                                                                                  |
| ------------------- | ------------------------------------------------------------------------------------- |
| **`bytes`**         | <code>number[]</code>                                                                 |
| **`calendarEvent`** | <code><a href="#barcodecalendarevent">BarcodeCalendarEvent</a></code>                 |
| **`contactInfo`**   | <code><a href="#barcodecontactinfo">BarcodeContactInfo</a></code>                     |
| **`cornerPoints`**  | <code>[[number, number], [number, number], [number, number], [number, number]]</code> |
| **`displayValue`**  | <code>string</code>                                                                   |
| **`driverLicense`** | <code><a href="#barcodedriverlicense">BarcodeDriverLicense</a></code>                 |
| **`email`**         | <code><a href="#barcodeemail">BarcodeEmail</a></code>                                 |
| **`format`**        | <code><a href="#barcodeformat">BarcodeFormat</a></code>                               |
| **`geoPoint`**      | <code><a href="#barcodegeopoint">BarcodeGeoPoint</a></code>                           |
| **`phone`**         | <code><a href="#barcodephone">BarcodePhone</a></code>                                 |
| **`rawValue`**      | <code>string</code>                                                                   |
| **`sms`**           | <code><a href="#barcodesms">BarcodeSms</a></code>                                     |
| **`urlBookmark`**   | <code><a href="#barcodeurlbookmark">BarcodeUrlBookmark</a></code>                     |
| **`valueType`**     | <code><a href="#barcodevaluetype">BarcodeValueType</a></code>                         |
| **`wifi`**          | <code><a href="#barcodewifi">BarcodeWifi</a></code>                                   |


#### BarcodeCalendarEvent

| Prop              | Type                |
| ----------------- | ------------------- |
| **`description`** | <code>string</code> |
| **`end`**         | <code>string</code> |
| **`location`**    | <code>string</code> |
| **`organizer`**   | <code>string</code> |
| **`start`**       | <code>string</code> |
| **`status`**      | <code>string</code> |
| **`summary`**     | <code>string</code> |


#### BarcodeContactInfo

| Prop               | Type                                              |
| ------------------ | ------------------------------------------------- |
| **`addresses`**    | <code>Address[]</code>                            |
| **`emails`**       | <code>BarcodeEmail[]</code>                       |
| **`personName`**   | <code><a href="#personname">PersonName</a></code> |
| **`organization`** | <code>string</code>                               |
| **`phones`**       | <code>BarcodePhone[]</code>                       |
| **`title`**        | <code>string</code>                               |
| **`urls`**         | <code>string[]</code>                             |


#### Address

| Prop               | Type                                                |
| ------------------ | --------------------------------------------------- |
| **`addressLines`** | <code>string[]</code>                               |
| **`type`**         | <code><a href="#addresstype">AddressType</a></code> |


#### BarcodeEmail

| Prop          | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`address`** | <code>string</code>                                         |
| **`body`**    | <code>string</code>                                         |
| **`subject`** | <code>string</code>                                         |
| **`type`**    | <code><a href="#emailformattype">EmailFormatType</a></code> |


#### PersonName

| Prop                | Type                |
| ------------------- | ------------------- |
| **`first`**         | <code>string</code> |
| **`formattedName`** | <code>string</code> |
| **`last`**          | <code>string</code> |
| **`middle`**        | <code>string</code> |
| **`prefix`**        | <code>string</code> |
| **`pronunciation`** | <code>string</code> |
| **`suffix`**        | <code>string</code> |


#### BarcodePhone

| Prop         | Type                                                        |
| ------------ | ----------------------------------------------------------- |
| **`number`** | <code>string</code>                                         |
| **`type`**   | <code><a href="#phoneformattype">PhoneFormatType</a></code> |


#### BarcodeDriverLicense

| Prop                 | Type                |
| -------------------- | ------------------- |
| **`addressCity`**    | <code>string</code> |
| **`addressState`**   | <code>string</code> |
| **`addressStreet`**  | <code>string</code> |
| **`addressZip`**     | <code>string</code> |
| **`birthDate`**      | <code>string</code> |
| **`documentType`**   | <code>string</code> |
| **`expiryDate`**     | <code>string</code> |
| **`firstName`**      | <code>string</code> |
| **`gender`**         | <code>string</code> |
| **`issueDate`**      | <code>string</code> |
| **`issuingCountry`** | <code>string</code> |
| **`lastName`**       | <code>string</code> |
| **`licenseNumber`**  | <code>string</code> |
| **`middleName`**     | <code>string</code> |


#### BarcodeGeoPoint

| Prop            | Type                |
| --------------- | ------------------- |
| **`latitude`**  | <code>number</code> |
| **`longitude`** | <code>number</code> |


#### BarcodeSms

| Prop              | Type                |
| ----------------- | ------------------- |
| **`phoneNumber`** | <code>string</code> |
| **`message`**     | <code>string</code> |


#### BarcodeUrlBookmark

| Prop        | Type                |
| ----------- | ------------------- |
| **`url`**   | <code>string</code> |
| **`title`** | <code>string</code> |


#### BarcodeWifi

| Prop                 | Type                                                              |
| -------------------- | ----------------------------------------------------------------- |
| **`encryptionType`** | <code><a href="#wifiencryptiontype">WifiEncryptionType</a></code> |
| **`password`**       | <code>string</code>                                               |
| **`ssid`**           | <code>string</code>                                               |


#### ReadBarcodesFromImageOptions

| Prop          | Type                         |
| ------------- | ---------------------------- |
| **`formats`** | <code>BarcodeFormat[]</code> |
| **`path`**    | <code>string</code>          |


#### ScanResult

| Prop           | Type                   |
| -------------- | ---------------------- |
| **`barcodes`** | <code>Barcode[]</code> |


#### ScanOptions

| Prop           | Type                         |
| -------------- | ---------------------------- |
| **`formats`**  | <code>BarcodeFormat[]</code> |
| **`autoZoom`** | <code>boolean</code>         |


#### IsSupportedResult

| Prop            | Type                 |
| --------------- | -------------------- |
| **`supported`** | <code>boolean</code> |


#### IsTorchEnabledResult

| Prop          | Type                 |
| ------------- | -------------------- |
| **`enabled`** | <code>boolean</code> |


#### IsTorchAvailableResult

| Prop            | Type                 |
| --------------- | -------------------- |
| **`available`** | <code>boolean</code> |


#### SetZoomRatioOptions

| Prop            | Type                |
| --------------- | ------------------- |
| **`zoomRatio`** | <code>number</code> |


#### GetZoomRatioResult

| Prop            | Type                |
| --------------- | ------------------- |
| **`zoomRatio`** | <code>number</code> |


#### GetMinZoomRatioResult

| Prop            | Type                |
| --------------- | ------------------- |
| **`zoomRatio`** | <code>number</code> |


#### GetMaxZoomRatioResult

| Prop            | Type                |
| --------------- | ------------------- |
| **`zoomRatio`** | <code>number</code> |


#### IsGoogleBarcodeScannerModuleAvailableResult

| Prop            | Type                 |
| --------------- | -------------------- |
| **`available`** | <code>boolean</code> |


#### PermissionStatus

| Prop         | Type                                                                    |
| ------------ | ----------------------------------------------------------------------- |
| **`camera`** | <code><a href="#camerapermissionstate">CameraPermissionState</a></code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### BarcodesScannedEvent

Events

| Prop           | Type                   |
| -------------- | ---------------------- |
| **`barcodes`** | <code>Barcode[]</code> |


#### ScanErrorEvent

| Prop          | Type                |
| ------------- | ------------------- |
| **`message`** | <code>string</code> |


#### GoogleBarcodeScannerModuleInstallProgressEvent

| Prop           | Type                                                                                                      |
| -------------- | --------------------------------------------------------------------------------------------------------- |
| **`state`**    | <code><a href="#googlebarcodescannermoduleinstallstate">GoogleBarcodeScannerModuleInstallState</a></code> |
| **`progress`** | <code>number</code>                                                                                       |


### Type Aliases


#### CameraPermissionState

Permissions

<code><a href="#permissionstate">PermissionState</a> | 'limited'</code>


#### PermissionState

<code>'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'</code>


### Enums


#### BarcodeFormat

| Members          | Value                      |
| ---------------- | -------------------------- |
| **`Aztec`**      | <code>'AZTEC'</code>       |
| **`Codabar`**    | <code>'CODABAR'</code>     |
| **`Code39`**     | <code>'CODE_39'</code>     |
| **`Code93`**     | <code>'CODE_93'</code>     |
| **`Code128`**    | <code>'CODE_128'</code>    |
| **`DataMatrix`** | <code>'DATA_MATRIX'</code> |
| **`Ean8`**       | <code>'EAN_8'</code>       |
| **`Ean13`**      | <code>'EAN_13'</code>      |
| **`Itf`**        | <code>'ITF'</code>         |
| **`Pdf417`**     | <code>'PDF_417'</code>     |
| **`QrCode`**     | <code>'QR_CODE'</code>     |
| **`UpcA`**       | <code>'UPC_A'</code>       |
| **`UpcE`**       | <code>'UPC_E'</code>       |


#### LensFacing

| Members     | Value                |
| ----------- | -------------------- |
| **`Front`** | <code>'FRONT'</code> |
| **`Back`**  | <code>'BACK'</code>  |


#### Resolution

| Members           | Value          |
| ----------------- | -------------- |
| **`'640x480'`**   | <code>0</code> |
| **`'1280x720'`**  | <code>1</code> |
| **`'1920x1080'`** | <code>2</code> |
| **`'3840x2160'`** | <code>3</code> |


#### AddressType

| Members       | Value          |
| ------------- | -------------- |
| **`HOME`**    | <code>0</code> |
| **`UNKNOWN`** | <code>1</code> |
| **`WORK`**    | <code>2</code> |


#### EmailFormatType

| Members       | Value          |
| ------------- | -------------- |
| **`HOME`**    | <code>0</code> |
| **`UNKNOWN`** | <code>1</code> |
| **`WORK`**    | <code>2</code> |


#### PhoneFormatType

| Members       | Value          |
| ------------- | -------------- |
| **`FAX`**     | <code>0</code> |
| **`HOME`**    | <code>1</code> |
| **`MOBILE`**  | <code>2</code> |
| **`UNKNOWN`** | <code>3</code> |
| **`WORK`**    | <code>4</code> |


#### BarcodeValueType

| Members              | Value                          |
| -------------------- | ------------------------------ |
| **`CalendarEvent`**  | <code>'CALENDAR_EVENT'</code>  |
| **`ContactInfo`**    | <code>'CONTACT_INFO'</code>    |
| **`DriversLicense`** | <code>'DRIVERS_LICENSE'</code> |
| **`Email`**          | <code>'EMAIL'</code>           |
| **`Geo`**            | <code>'GEO'</code>             |
| **`Isbn`**           | <code>'ISBN'</code>            |
| **`Phone`**          | <code>'PHONE'</code>           |
| **`Product`**        | <code>'PRODUCT'</code>         |
| **`Sms`**            | <code>'SMS'</code>             |
| **`Text`**           | <code>'TEXT'</code>            |
| **`Url`**            | <code>'URL'</code>             |
| **`Wifi`**           | <code>'WIFI'</code>            |
| **`Unknown`**        | <code>'UNKNOWN'</code>         |


#### WifiEncryptionType

| Members    | Value          |
| ---------- | -------------- |
| **`OPEN`** | <code>1</code> |
| **`WEP`**  | <code>2</code> |
| **`WPA`**  | <code>3</code> |


#### GoogleBarcodeScannerModuleInstallState

| Members               | Value          |
| --------------------- | -------------- |
| **`UNKNOWN`**         | <code>0</code> |
| **`PENDING`**         | <code>1</code> |
| **`DOWNLOADING`**     | <code>2</code> |
| **`CANCELED`**        | <code>3</code> |
| **`COMPLETED`**       | <code>4</code> |
| **`FAILED`**          | <code>5</code> |
| **`INSTALLING`**      | <code>6</code> |
| **`DOWNLOAD_PAUSED`** | <code>7</code> |

</docgen-api>

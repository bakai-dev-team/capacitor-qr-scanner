import { WebPlugin } from '@capacitor/core';
import type {
  QrCodeScannerPlugin,
  PermissionStatus,
  ReadBarcodesFromImageOptions,
  ReadBarcodesFromImageResult,
  ScanOptions,
  ScanResult,
  IsSupportedResult,
  IsTorchAvailableResult,
  IsTorchEnabledResult,
  SetZoomRatioOptions,
  GetZoomRatioResult,
  GetMinZoomRatioResult,
  GetMaxZoomRatioResult,
  IsGoogleBarcodeScannerModuleAvailableResult,
  StartScanOptions,
} from './definitions';
export declare class QrCodeScannerWeb extends WebPlugin implements QrCodeScannerPlugin {
  startScan(_options?: StartScanOptions): Promise<void>;
  stopScan(): Promise<void>;
  pauseScan(): Promise<void>;
  resumeScan(): Promise<void>;
  readBarcodesFromImage(_options: ReadBarcodesFromImageOptions): Promise<ReadBarcodesFromImageResult>;
  scan(_options?: ScanOptions): Promise<ScanResult>;
  isSupported(): Promise<IsSupportedResult>;
  enableTorch(): Promise<void>;
  disableTorch(): Promise<void>;
  toggleTorch(): Promise<void>;
  isTorchEnabled(): Promise<IsTorchEnabledResult>;
  isTorchAvailable(): Promise<IsTorchAvailableResult>;
  setZoomRatio(_options: SetZoomRatioOptions): Promise<void>;
  getZoomRatio(): Promise<GetZoomRatioResult>;
  getMinZoomRatio(): Promise<GetMinZoomRatioResult>;
  getMaxZoomRatio(): Promise<GetMaxZoomRatioResult>;
  openSettings(): Promise<void>;
  isGoogleBarcodeScannerModuleAvailable(): Promise<IsGoogleBarcodeScannerModuleAvailableResult>;
  installGoogleBarcodeScannerModule(): Promise<void>;
  checkPermissions(): Promise<PermissionStatus>;
  requestPermissions(): Promise<PermissionStatus>;
}

import { WebPlugin } from '@capacitor/core';
export class QrCodeScannerWeb extends WebPlugin {
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
//# sourceMappingURL=web.js.map
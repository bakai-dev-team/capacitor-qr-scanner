import { registerPlugin } from '@capacitor/core';
const QrCodeScanner = registerPlugin('QrCodeScanner', {
  web: () => import('./web').then((m) => new m.QrCodeScannerWeb()),
});
export * from './definitions';
export { QrCodeScanner };
//# sourceMappingURL=index.js.map

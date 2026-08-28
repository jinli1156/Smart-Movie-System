declare module 'qrcode' {
  export interface QRCodeToCanvasOptions {
    width?: number
    margin?: number
    color?: { dark?: string; light?: string }
    errorCorrectionLevel?: 'L' | 'M' | 'Q' | 'H'
  }

  export function toCanvas(
    canvas: HTMLCanvasElement,
    text: string,
    options?: QRCodeToCanvasOptions
  ): Promise<void>

  const QRCode: { toCanvas: typeof toCanvas }
  export default QRCode
}

/**
 * 上传前按尺寸和质量压缩图片；压缩无收益或无法处理时保留原文件。
 */
interface CompressImageOptions {
  maxWidth?: number
  maxHeight?: number
  quality?: number
}

const loadImage = (file: File) =>
  new Promise<HTMLImageElement>((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      URL.revokeObjectURL(objectUrl)
      resolve(image)
    }
    image.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('图片解码失败'))
    }
    image.src = objectUrl
  })

/** 按最大尺寸压缩为 WebP；若压缩无收益则保留原文件。 */
export const compressImage = async (
  file: File,
  { maxWidth = 1200, maxHeight = 1600, quality = 0.82 }: CompressImageOptions = {}
) => {
  if (file.type === 'image/svg+xml' || file.type === 'image/gif') return file

  const image = await loadImage(file)
  const scale = Math.min(1, maxWidth / image.naturalWidth, maxHeight / image.naturalHeight)

  if (scale === 1 && file.size <= 700 * 1024 && file.type === 'image/webp') return file

  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
  canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))

  const context = canvas.getContext('2d')
  if (!context) return file
  context.drawImage(image, 0, 0, canvas.width, canvas.height)

  const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/webp', quality))
  if (!blob || blob.size >= file.size) return file

  const basename = file.name.replace(/\.[^.]+$/, '') || 'image'
  return new File([blob], `${basename}.webp`, {
    type: blob.type,
    lastModified: file.lastModified
  })
}

export const apiUrl = (url: string) => url.startsWith('/api/') ? (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '') + url.slice(4) : url
export async function encodeFormData(form: FormData) {
  const fields: Record<string, string> = {}, files: { name: string; filename: string; mime: string; base64: string }[] = []
  for (const [name, value] of form.entries()) {
    if (typeof value === 'string') fields[name] = value
    else {
      const encoded = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader(); reader.onerror = () => reject(new Error('文件读取失败'))
        reader.onload = () => resolve(String(reader.result).split(',')[1] || '')
        reader.readAsDataURL(value)
      })
      files.push({ name, filename: value.name || 'blob', mime: value.type, base64: encoded })
    }
  }
  return { fields, files }
}
export async function fetchCloudFile(url: string, options?: RequestInit): Promise<Response> {
  const target = apiUrl(url), response = await fetch(target, options)
  if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) return response
  const result = await response.json(), manifest = result.data
  if (result.code !== 200 || !Number.isInteger(manifest?.parts) || manifest.parts < 1 || manifest.parts > 42 || manifest.mime !== 'image/png') throw new Error(result.message || '图片暂不可用')
  const chunks: Blob[] = []
  for (let part = 0; part < manifest.parts; part++) {
    const r = await fetch(target + (target.includes('?') ? '&' : '?') + 'part=' + part, options)
    if (!r.ok || r.headers.get('content-type')?.includes('application/json')) throw new Error('图片分段加载失败，请重新加载')
    chunks.push(await r.blob())
  }
  const blob = new Blob(chunks, { type: manifest.mime })
  if (blob.size !== manifest.size) throw new Error('图片下载不完整，请重新加载')
  return new Response(blob, { headers: { 'Content-Type': manifest.mime } })
}

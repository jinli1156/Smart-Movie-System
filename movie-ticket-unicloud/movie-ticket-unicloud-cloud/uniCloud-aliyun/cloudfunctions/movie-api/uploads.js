'use strict'

const crypto = require('crypto')
const { Readable } = require('stream')
const Busboy = require('busboy')
const { ApiError, assert, success, requireUser } = require('./core')

const MAX_FILE = 1400 * 1024
const TYPES = new Map([['image/jpeg', 'jpg'], ['image/png', 'png'], ['image/gif', 'gif'], ['image/webp', 'webp'], ['image/bmp', 'bmp']])

async function readImage(request) {
  if ((request.headers['content-type'] || '').includes('application/json')) {
    const { files } = await require('./ai-common').multipart(request)
    assert(files.length === 1 && files[0].name === 'file' && TYPES.has(files[0].mime), 400, '请选择一张有效图片')
    const f = files[0], boundary = 'CloudImage' + crypto.randomUUID().replace(/-/g, '')
    const body = Buffer.concat([Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="image.${TYPES.get(f.mime)}"\r\nContent-Type: ${f.mime}\r\n\r\n`), f.bytes, Buffer.from(`\r\n--${boundary}--\r\n`)])
    return readImage({ ...request, headers: { 'content-type': `multipart/form-data; boundary=${boundary}` }, body })
  }
  const type = request.headers['content-type'] || ''
  assert(type.startsWith('multipart/form-data;'), 400, '请上传图片文件')
  const input = Buffer.isBuffer(request.body) ? request.body : Buffer.from(request.body || '', 'binary')
  assert(input.length <= 1900 * 1024, 413, '图片超过云函数请求大小限制')
  return new Promise((resolve, reject) => {
    let file = null
    let failed = false
    const parser = Busboy({ headers: { 'content-type': type }, limits: { files: 1, fileSize: MAX_FILE, parts: 2 } })
    parser.on('file', (name, stream, info) => {
      if (name !== 'file') { stream.resume(); return }
      const chunks = []
      stream.on('data', chunk => chunks.push(chunk))
      stream.on('limit', () => { failed = true })
      stream.on('end', () => { file = { filename: info.filename, mimeType: info.mimeType, data: Buffer.concat(chunks) } })
    })
    parser.on('error', reject)
    parser.on('finish', () => {
      try {
        assert(!failed, 413, '图片不能超过1.4MB')
        assert(file?.data?.length, 400, '请选择图片文件')
        assert(TYPES.has(file.mimeType), 400, '图片格式仅支持 JPEG、PNG、GIF、WebP、BMP')
        const bytes = file.data
        const mime = file.mimeType
        const valid = mime === 'image/jpeg' ? bytes[0] === 0xff && bytes[1] === 0xd8
          : mime === 'image/png' ? bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
          : mime === 'image/gif' ? bytes.subarray(0, 3).toString() === 'GIF'
          : mime === 'image/webp' ? bytes.subarray(0, 4).toString() === 'RIFF' && bytes.subarray(8, 12).toString() === 'WEBP'
          : bytes.subarray(0, 2).toString() === 'BM'
        assert(valid, 400, '文件内容与图片格式不符')
        resolve(file)
      } catch (error) { reject(error) }
    })
    Readable.from(input).pipe(parser)
  })
}

async function handle(request) {
  if (request.path !== '/upload/image' || request.method !== 'POST') throw new ApiError(404, '接口不存在')
  requireUser(request, ['ADMIN', 'CINEMA_ADMIN'])
  const file = await readImage(request)
  const cloudPath = `movie-ticket/${crypto.randomUUID()}.${TYPES.get(file.mimeType)}`
  const uploaded = await uniCloud.uploadFile({ cloudPath, cloudPathAsRealPath: true, fileContent: file.data })
  assert(uploaded.fileID, 502, '云存储未返回文件地址')
  return success({ url: uploaded.fileID, filename: file.filename, newFilename: cloudPath }, '上传成功')
}

module.exports = { handle, readImage }

import { encodeFormData } from '@/utils/api-url'
import request from '@/utils/request'

export interface UploadResult {
  url: string
  filename: string
  newFilename?: string
}

export const uploadApi = {
  async uploadImage(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<any, UploadResult>('/upload/image', await encodeFormData(formData))
  }
}

/**
 * 图片上传接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'

export interface UploadResult {
  url: string
  filename: string
  newFilename?: string
}

export const uploadApi = {
  /** 检查并保存图片，返回可供页面展示的资源地址。 */
  uploadImage(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<any, UploadResult>('/upload/image', formData)
  }
}

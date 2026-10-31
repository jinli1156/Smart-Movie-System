/**
 * 影院目录与资料接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Cinema } from '@/types'

export const cinemaApi = {
  /** 读取影院列表，供选择器或公开目录使用。 */
  getAll(silent = false) {
    return request.get<any, Cinema[]>('/cinema/all', { silent })
  },

  /** 根据 ID 查询影院详情。 */
  getById(id: number) {
    return request.get<any, Cinema>(`/cinema/${id}`)
  }
}

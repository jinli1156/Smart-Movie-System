import request from '@/utils/request'
import type { Hall, PageResult } from '@/types'

export const hallApi = {
  getPage(params: { page?: number; size?: number; cinemaId?: number } = {}) {
    return request.get<any, PageResult<Hall>>('/hall/page', {
      params: { page: 1, size: 200, ...params }
    })
  }
}

/**
 * 影厅与座位布局接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Hall, PageResult } from '@/types'

export const hallApi = {
  /** 按筛选条件分页查询影厅，供列表页展示。 */
  getPage(params: { page?: number; size?: number; cinemaId?: number } = {}) {
    return request.get<any, PageResult<Hall>>('/hall/page', {
      params: { page: 1, size: 200, ...params }
    })
  }
}

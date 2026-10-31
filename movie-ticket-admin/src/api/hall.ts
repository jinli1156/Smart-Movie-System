/**
 * 影厅与座位布局接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Hall, PageResult } from '@/types'

export const hallApi = {
  /** 按筛选条件分页查询影厅，供列表页展示。 */
  getPage(params: { page: number; size: number; cinemaId?: number }) {
    return request.get<any, PageResult<Hall>>('/hall/page', { params })
  },

  /** 读取指定影院下的影厅列表。 */
  getByCinemaId(cinemaId: number) {
    return request.get<any, Hall[]>(`/hall/cinema/${cinemaId}`)
  },

  /** 根据 ID 查询影厅详情。 */
  getById(id: number) {
    return request.get<any, Hall>(`/hall/${id}`)
  },

  /** 新增影厅，由业务层执行参数及关联数据校验。 */
  create(data: Hall) {
    return request.post('/hall', data)
  },

  /** 更新影厅，由业务层校验当前操作权限与业务条件。 */
  update(id: number, data: Hall) {
    return request.put(`/hall/${id}`, data)
  },

  /** 删除影厅，由业务层检查权限及关联数据限制。 */
  delete(id: number) {
    return request.delete(`/hall/${id}`)
  },

  /** 修改影厅状态，具体可用状态和操作限制由业务层校验。 */
  updateStatus(id: number, status: number) {
    return request.put(`/hall/${id}/status`, null, { params: { status } })
  }
}

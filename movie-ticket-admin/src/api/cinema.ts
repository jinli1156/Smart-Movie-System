/**
 * 影院目录与资料接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Cinema, PageResult } from '@/types'

export const cinemaApi = {
  /** 按筛选条件分页查询影院，供列表页展示。 */
  getPage(params: { page: number; size: number; keyword?: string }) {
    return request.get<any, PageResult<Cinema>>('/cinema/page', { params })
  },

  /** 读取影院列表，供选择器或公开目录使用。 */
  getAll() {
    return request.get<any, Cinema[]>('/cinema/all')
  },

  /** 根据 ID 查询影院详情。 */
  getById(id: number) {
    return request.get<any, Cinema>(`/cinema/${id}`)
  },

  /** 新增影院，由业务层执行参数及关联数据校验。 */
  create(data: Cinema) {
    return request.post('/cinema', data)
  },

  /** 更新影院，由业务层校验当前操作权限与业务条件。 */
  update(id: number, data: Cinema) {
    return request.put(`/cinema/${id}`, data)
  },

  /** 删除影院，由业务层检查权限及关联数据限制。 */
  delete(id: number) {
    return request.delete(`/cinema/${id}`)
  },

  /** 修改影院状态，具体可用状态和操作限制由业务层校验。 */
  updateStatus(id: number, status: number) {
    return request.put(`/cinema/${id}/status`, null, { params: { status } })
  }
}

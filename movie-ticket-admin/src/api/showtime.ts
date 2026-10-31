/**
 * 场次与座位状态接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Showtime, PageResult } from '@/types'

export const showtimeApi = {
  /** 按筛选条件分页查询场次，供列表页展示。 */
  getPage(params: { page: number; size: number; movieId?: number; cinemaId?: number; date?: string; status?: number }) {
    return request.get<any, PageResult<Showtime>>('/showtime/page', { params })
  },

  /** 根据影片、影院及日期查询可选场次。 */
  getList(params: { movieId: number; cinemaId: number; date?: string }) {
    return request.get<any, Showtime[]>('/showtime/list', { params })
  },

  /** 根据 ID 查询场次详情。 */
  getById(id: number) {
    return request.get<any, Showtime>(`/showtime/${id}`)
  },

  /** 新增场次，由业务层执行参数及关联数据校验。 */
  create(data: Showtime) {
    return request.post('/showtime', data)
  },

  /** 更新场次，由业务层校验当前操作权限与业务条件。 */
  update(id: number, data: Showtime) {
    return request.put(`/showtime/${id}`, data)
  },

  /** 删除场次，由业务层检查权限及关联数据限制。 */
  delete(id: number) {
    return request.delete(`/showtime/${id}`)
  },

  /** 修改场次状态，具体可用状态和操作限制由业务层校验。 */
  updateStatus(id: number, status: number) {
    return request.put(`/showtime/${id}/status`, null, { params: { status } })
  }
}

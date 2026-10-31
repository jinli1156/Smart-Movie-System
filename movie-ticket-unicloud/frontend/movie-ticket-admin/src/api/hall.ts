import request from '@/utils/request'
import type { Hall, PageResult } from '@/types'

export const hallApi = {
  getPage(params: { page: number; size: number; cinemaId?: number }) {
    return request.get<any, PageResult<Hall>>('/hall/page', { params })
  },

  getByCinemaId(cinemaId: number) {
    return request.get<any, Hall[]>(`/hall/cinema/${cinemaId}`)
  },

  getById(id: number) {
    return request.get<any, Hall>(`/hall/${id}`)
  },

  create(data: Hall) {
    return request.post('/hall', data)
  },

  update(id: number, data: Hall) {
    return request.put(`/hall/${id}`, data)
  },

  delete(id: number) {
    return request.delete(`/hall/${id}`)
  },

  updateStatus(id: number, status: number) {
    return request.put(`/hall/${id}/status`, null, { params: { status } })
  }
}

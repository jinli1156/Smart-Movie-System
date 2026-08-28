import request from '@/utils/request'
import type { Cinema, PageResult } from '@/types'

export const cinemaApi = {
  getPage(params: { page: number; size: number; keyword?: string }) {
    return request.get<any, PageResult<Cinema>>('/cinema/page', { params })
  },

  getAll() {
    return request.get<any, Cinema[]>('/cinema/all')
  },

  getById(id: number) {
    return request.get<any, Cinema>(`/cinema/${id}`)
  },

  create(data: Cinema) {
    return request.post('/cinema', data)
  },

  update(id: number, data: Cinema) {
    return request.put(`/cinema/${id}`, data)
  },

  delete(id: number) {
    return request.delete(`/cinema/${id}`)
  },

  updateStatus(id: number, status: number) {
    return request.put(`/cinema/${id}/status`, null, { params: { status } })
  }
}

import request from '@/utils/request'
import type { Showtime, PageResult } from '@/types'

export const showtimeApi = {
  getPage(params: { page: number; size: number; movieId?: number; cinemaId?: number; date?: string; status?: number }) {
    return request.get<any, PageResult<Showtime>>('/showtime/page', { params })
  },

  getList(params: { movieId: number; cinemaId: number; date?: string }) {
    return request.get<any, Showtime[]>('/showtime/list', { params })
  },

  getById(id: number) {
    return request.get<any, Showtime>(`/showtime/${id}`)
  },

  create(data: Showtime) {
    return request.post('/showtime', data)
  },

  update(id: number, data: Showtime) {
    return request.put(`/showtime/${id}`, data)
  },

  delete(id: number) {
    return request.delete(`/showtime/${id}`)
  },

  updateStatus(id: number, status: number) {
    return request.put(`/showtime/${id}/status`, null, { params: { status } })
  }
}

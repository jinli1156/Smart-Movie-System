import request from '@/utils/request'
import type { Showtime, SeatStatus, PageResult } from '@/types'

export const showtimeApi = {
  getList(params: { movieId: number; cinemaId: number; date?: string }) {
    return request.get<any, Showtime[]>('/showtime/list', { params })
  },

  getPage(params: {
    page: number
    size: number
    movieId?: number
    cinemaId?: number
    date?: string
    status?: number
  }) {
    return request.get<any, PageResult<Showtime>>('/showtime/page', { params })
  },

  getById(id: number) {
    return request.get<any, Showtime>(`/showtime/${id}`)
  },

  getSeatStatus(id: number) {
    return request.get<any, SeatStatus>(`/showtime/${id}/seats`)
  }
}

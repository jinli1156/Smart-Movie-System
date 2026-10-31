import request from '@/utils/request'
import type { Movie, PageResult } from '@/types'

export const movieApi = {
  getPage(params: { page: number; size: number; keyword?: string; status?: number }) {
    return request.get<any, PageResult<Movie>>('/movie/page', { params })
  },

  getNowShowing() {
    return request.get<any, Movie[]>('/movie/now-showing')
  },

  getComingSoon() {
    return request.get<any, Movie[]>('/movie/coming-soon')
  },

  getById(id: number) {
    return request.get<any, Movie>(`/movie/${id}`)
  },

  create(data: Movie) {
    return request.post('/movie', data)
  },

  update(id: number, data: Movie) {
    return request.put(`/movie/${id}`, data)
  },

  delete(id: number) {
    return request.delete(`/movie/${id}`)
  },

  updateStatus(id: number, status: number) {
    return request.put(`/movie/${id}/status`, null, { params: { status } })
  }
}

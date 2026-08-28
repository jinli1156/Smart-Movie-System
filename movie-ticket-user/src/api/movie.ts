import request from '@/utils/request'
import type { Movie, PageResult } from '@/types'

export const movieApi = {
  getPage(params: { page: number; size: number; keyword?: string; status?: number }) {
    return request.get<any, PageResult<Movie>>('/movie/page', { params })
  },

  getNowShowing(silent = false) {
    return request.get<any, Movie[]>('/movie/now-showing', { silent })
  },

  getComingSoon(silent = false) {
    return request.get<any, Movie[]>('/movie/coming-soon', { silent })
  },

  getById(id: number) {
    return request.get<any, Movie>(`/movie/${id}`)
  }
}

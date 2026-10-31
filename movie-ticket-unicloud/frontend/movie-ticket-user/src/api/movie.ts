import request from '@/utils/request'
import type { Movie, MovieBoxOfficeRank, MovieInsight, PageResult } from '@/types'

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

  getTodayBoxOfficeRanking(limit = 3, signal?: AbortSignal) {
    return request.get<any, MovieBoxOfficeRank[]>('/movie/box-office/today', {
      params: { limit, _t: Date.now() },
      signal,
      silent: true
    })
  },

  getHistoricalBoxOfficeRanking(signal?: AbortSignal) {
    return request.get<any, MovieBoxOfficeRank[]>('/movie/box-office/history', {
      params: { _t: Date.now() }, signal, silent: true
    })
  },

  getById(id: number) {
    return request.get<any, Movie>(`/movie/${id}`)
  },

  getInsight(id: number) {
    return request.get<any, MovieInsight>(`/movie/${id}/insight`, { silent: true })
  }
}

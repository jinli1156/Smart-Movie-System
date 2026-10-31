/**
 * 影片目录与票房信息接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Movie, MovieBoxOfficeRank, MovieInsight, PageResult } from '@/types'

export const movieApi = {
  /** 按筛选条件分页查询电影，供列表页展示。 */
  getPage(params: { page: number; size: number; keyword?: string; status?: number }) {
    return request.get<any, PageResult<Movie>>('/movie/page', { params })
  },

  /** 查询当前热映影片列表。 */
  getNowShowing(silent = false) {
    return request.get<any, Movie[]>('/movie/now-showing', { silent })
  },

  /** 查询即将上映影片列表。 */
  getComingSoon(silent = false) {
    return request.get<any, Movie[]>('/movie/coming-soon', { silent })
  },

  /** 按今日已支付订单汇总实时票房排行。 */
  getTodayBoxOfficeRanking(limit = 3, signal?: AbortSignal) {
    return request.get<any, MovieBoxOfficeRank[]>('/movie/box-office/today', {
      params: { limit, _t: Date.now() },
      signal,
      silent: true
    })
  },

  /** 按已支付订单汇总历史票房排行。 */
  getHistoricalBoxOfficeRanking(signal?: AbortSignal) {
    return request.get<any, MovieBoxOfficeRank[]>('/movie/box-office/history', {
      params: { _t: Date.now() }, signal, silent: true
    })
  },

  /** 根据 ID 查询电影详情。 */
  getById(id: number) {
    return request.get<any, Movie>(`/movie/${id}`)
  },

  /** 获取影片票房、观影人数和场次经营指标。 */
  getInsight(id: number) {
    return request.get<any, MovieInsight>(`/movie/${id}/insight`, { silent: true })
  }
}

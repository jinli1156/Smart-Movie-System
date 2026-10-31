/**
 * 场次与座位状态接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Showtime, SeatStatus, PageResult, SeatPreference, SeatRecommendation, SeatRecommendationResult } from '@/types'

export const showtimeApi = {
  /** 根据影片、影院及日期查询可选场次。 */
  getList(params: { movieId: number; cinemaId: number; date?: string }) {
    return request.get<any, Showtime[]>('/showtime/list', { params })
  },

  /** 按筛选条件分页查询场次，供列表页展示。 */
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

  /** 根据 ID 查询场次详情。 */
  getById(id: number) {
    return request.get<any, Showtime>(`/showtime/${id}`)
  },

  /** 合并座位布局、有效订单与临时占用，返回当前座位状态。 */
  getSeatStatus(id: number) {
    return request.get<any, SeatStatus>(`/showtime/${id}/seats`)
  },

  /** 按人数与结构化偏好生成可用座位组合。 */
  getSeatRecommendations(id: number, params: { people: number; preference: SeatPreference; limit?: number }) {
    return request.get<any, SeatRecommendation[]>(`/showtime/${id}/seat-recommendation`, { params })
  },

  /** 结合自然语言偏好推荐座位，并返回评分依据及分析来源。 */
  getSmartSeatRecommendations(id: number, data: {
    people: number
    preference: SeatPreference
    naturalLanguage?: string
    limit?: number
  }) {
    return request.post<any, SeatRecommendationResult>(`/showtime/${id}/seat-recommendation`, data, { timeout: 30000 })
  }
}

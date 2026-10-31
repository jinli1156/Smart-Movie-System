/**
 * 个性化推荐与偏好画像接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Movie, RecommendationEvaluation, RecommendationProfile, RecommendationRadar } from '@/types'

export const recommendApi = {
  /** 获取结合用户偏好与位置的个性化影片推荐。 */
  personal(params: { limit?: number; latitude?: number; longitude?: number }) {
    return request.get<any, Movie[]>('/recommend/personal', { params, silent: true, timeout: 30000 })
  },
  /** 读取推荐偏好画像及解释信息。 */
  profile() {
    return request.get<any, RecommendationProfile>('/recommend/profile', { silent: true, timeout: 30000 })
  },
  /** 读取偏好雷达图维度数据。 */
  radar() {
    return request.get<any, RecommendationRadar>('/recommend/profile/radar', { silent: true })
  },
  /** 提交初始喜欢的影片和类型，建立冷启动画像。 */
  initialize(data: { movieIds: number[]; favoriteGenres?: string[] }) {
    return request.post<any, void>('/recommend/cold-start', data)
  },
  /** 记录影片浏览行为，作为后续推荐的隐式反馈。 */
  recordView(movieId: number) {
    return request.post<any, void>(`/recommend/behavior/view/${movieId}`, undefined, { silent: true })
  },
  /** 切换当前用户对影片的想看状态，并返回最新状态。 */
  toggleFavorite(movieId: number) {
    return request.put<any, boolean>(`/recommend/behavior/favorite/${movieId}`, undefined)
  },
  /** 检查当前用户是否已将影片加入想看。 */
  isFavorite(movieId: number) {
    return request.get<any, boolean>(`/recommend/behavior/favorite/${movieId}`, { silent: true })
  },
  /** 读取本人想看影片列表。 */
  favorites() {
    return request.get<any, Movie[]>('/recommend/favorites')
  },
  /** 移除本人想看记录。 */
  removeFavorite(movieId: number) {
    return request.delete<any, void>(`/recommend/behavior/favorite/${movieId}`)
  },
  /** 运行推荐算法离线评估。 */
  evaluation(k = 6) {
    return request.get<any, RecommendationEvaluation>('/recommend/evaluation', { params: { k } })
  }
}

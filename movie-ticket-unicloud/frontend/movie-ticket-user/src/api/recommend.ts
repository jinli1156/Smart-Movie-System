import request from '@/utils/request'
import type { Movie, RecommendationEvaluation, RecommendationProfile, RecommendationRadar } from '@/types'

export const recommendApi = {
  personal(params: { limit?: number; latitude?: number; longitude?: number }) {
    return request.get<any, Movie[]>('/recommend/personal', { params, silent: true, timeout: 30000 })
  },
  profile() {
    return request.get<any, RecommendationProfile>('/recommend/profile', { silent: true, timeout: 30000 })
  },
  radar() {
    return request.get<any, RecommendationRadar>('/recommend/profile/radar', { silent: true })
  },
  initialize(data: { movieIds: number[]; favoriteGenres?: string[] }) {
    return request.post<any, void>('/recommend/cold-start', data)
  },
  recordView(movieId: number) {
    return request.post<any, void>(`/recommend/behavior/view/${movieId}`, undefined, { silent: true })
  },
  toggleFavorite(movieId: number) {
    return request.put<any, boolean>(`/recommend/behavior/favorite/${movieId}`, undefined)
  },
  isFavorite(movieId: number) {
    return request.get<any, boolean>(`/recommend/behavior/favorite/${movieId}`, { silent: true })
  },
  favorites() {
    return request.get<any, Movie[]>('/recommend/favorites')
  },
  removeFavorite(movieId: number) {
    return request.delete<any, void>(`/recommend/behavior/favorite/${movieId}`)
  },
  evaluation(k = 6) {
    return request.get<any, RecommendationEvaluation>('/recommend/evaluation', { params: { k } })
  }
}

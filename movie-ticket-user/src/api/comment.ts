import request from '@/utils/request'
import type { Comment, CommentEligibility } from '@/types'

export const commentApi = {
  create(data: { movieId: number; rating: number; content?: string }) {
    return request.post<any, Comment>('/comment', data)
  },

  update(id: number, data: { rating: number; content?: string }) {
    return request.put<any, Comment>(`/comment/${id}`, data)
  },

  listByMovie(movieId: number) {
    return request.get<any, Comment[]>(`/comment/movie/${movieId}`)
  },

  checkEligibility(movieId: number) {
    return request.get<any, CommentEligibility>('/comment/eligibility', {
      params: { movieId }
    })
  }
}

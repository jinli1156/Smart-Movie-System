/**
 * 观影评价接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Comment, CommentEligibility } from '@/types'

export const commentApi = {
  /** 新增观影评价，由业务层执行参数及关联数据校验。 */
  create(data: { movieId: number; rating: number; content?: string }) {
    return request.post<any, Comment>('/comment', data)
  },

  /** 更新观影评价，由业务层校验当前操作权限与业务条件。 */
  update(id: number, data: { rating: number; content?: string }) {
    return request.put<any, Comment>(`/comment/${id}`, data)
  },

  /** 读取指定影片的用户评价列表。 */
  listByMovie(movieId: number) {
    return request.get<any, Comment[]>(`/comment/movie/${movieId}`)
  },

  /** 核验用户是否已完成该影片的观影，以及是否已有可编辑评价。 */
  checkEligibility(movieId: number) {
    return request.get<any, CommentEligibility>('/comment/eligibility', {
      params: { movieId }
    })
  }
}

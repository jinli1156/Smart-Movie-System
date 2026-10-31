/**
 * 个性化推荐与偏好画像接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'

export interface Experiment {
  key: string
  name: string
  description: string
  evaluatedUsers: number
  precisionAtK: number
  recallAtK: number
  ndcgAtK: number
  catalogCoverage: number
  diversity: number
  recallChangeVsFull: number
}
export interface Evaluation {
  k: number
  evaluatedUsers: number
  skippedUsers: number
  precisionAtK: number
  recallAtK: number
  ndcgAtK: number
  catalogCoverage: number
  diversity: number
  baselines: Experiment[]
  ablations: Experiment[]
  protocol: string
  candidatePolicy: string
  generatedAt: string
}
export const evaluateRecommendations = (k: number) =>
  request.get<any, Evaluation>('/recommend/evaluation', { params: { k }, timeout: 120000 })

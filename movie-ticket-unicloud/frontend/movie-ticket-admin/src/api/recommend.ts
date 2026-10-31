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

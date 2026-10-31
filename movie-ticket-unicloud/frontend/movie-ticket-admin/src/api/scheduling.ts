import request from '@/utils/request'

export interface SchedulingAiAnalysis {
  source: 'model' | 'algorithm'
  status: 'success' | 'unconfigured' | 'unavailable'
  model?: string
  generatedAt: string
  summary: string
  dataBasis: { historicalSamples: number; historicalFrom?: string; historicalTo?: string; coldStart: boolean; occupancyBasis: string; predictionBasis: string }
  actions: { evidenceId: string; category: string; movieTitle?: string; hallName?: string; advice: string }[]
  risks: string[]
}

export interface PredictionRequest {
  movieId: number
  cinemaId: number
  hallId: number
  startTime: string
}

export interface SimulationRequest {
  originalShowtimeId: number
  targetMovieId: number
  targetHallId?: number
  targetStartTime?: string
}

export interface SchedulingOptimizationRequest {
  cinemaId: number
  targetDate: string
  movieIds?: number[]
  targetShowtimesPerHall: number
  maxShowtimesPerMovie: number
  minDistinctMovies: number
  cleaningMinutes: number
  minGoldenShowtimesPerHall: number
  revenueWeight: number
  occupancyWeight: number
  diversityWeight: number
  solverSeconds: number
}

export interface SchedulingPlanMetrics {
  method: string
  showtimeCount: number
  expectedRevenue: number
  expectedAudience: number
  occupancyRate: number
  distinctMovies: number
  calculationTimeMs: number
  constraintSatisfactionRate: number
}

export interface OptimizedShowtime {
  unitPrice?: number
  sourceShowtimeId?: number
  movieId: number
  movieTitle: string
  hallId: number
  hallName: string
  startTime: string
  endTime: string
  predictedRate: number
  expectedAudience: number
  expectedRevenue: number
  confidence: string
  locked: boolean
}

export interface SchedulingOptimizationResult {
  aiAnalysis?: SchedulingAiAnalysis
  status: 'OPTIMAL' | 'FEASIBLE' | 'INFEASIBLE' | 'UNKNOWN'
  statusText: string
  reason?: string
  solver: string
  targetDate: string
  objectiveValue?: number
  bestObjectiveBound?: number
  optimalityGap?: number
  solveTimeMs?: number
  candidateVariables?: number
  lockedShowtimes?: number
  selectedShowtimes: OptimizedShowtime[]
  comparisons: SchedulingPlanMetrics[]
  revenueLiftVsManual?: number | null
  revenueLiftVsHeuristic?: number | null
  occupancyLiftVsManual?: number
  occupancyLiftVsHeuristic?: number
  constraints?: string[]
  weights?: Record<string, number>
  notice: string
}

export interface DraftShowtime {
  sourceShowtimeId?: number
  movieId: number
  movieTitle: string
  hallId: number
  hallName: string
  startTime: string
  endTime: string
  basePrice: number
  locked: boolean
  lockReason?: string
  predictedRate?: number
  confidence?: string
}
export interface SchedulingWorkspace {
  version: string
  minimumCleaningMinutes: number
  items: DraftShowtime[]
  halls: import('@/types').Hall[]
}
export interface SchedulingDraftRequest {
  cinemaId: number
  targetDate: string
  version: string
  cleaningMinutes: number
  items: Pick<DraftShowtime, 'sourceShowtimeId' | 'movieId' | 'hallId' | 'startTime' | 'endTime' | 'basePrice'>[]
}
export interface DraftEvaluation {
  showtimeCount: number
  distinctMovies: number
  expectedRevenue: number
  expectedAudience: number
  occupancyRate: number | null
  historicalSamples: number
  items: OptimizedShowtime[]
}
export interface SchedulingDraftCheck {
  valid: boolean
  cleaningMinutes: number
  changes: { type: 'ADD' | 'UPDATE' | 'REMOVE'; before?: DraftShowtime; after?: DraftShowtime }[]
  original: DraftEvaluation
  draft: DraftEvaluation
}

export interface SavedSchedulingSummary {
  id: string
  cinemaId: number
  cinemaName: string
  targetDate: string
  savedAt: string
}
export interface SchedulingSnapshot {
  workspace: SchedulingWorkspace
  draft: DraftShowtime[]
  settings: Omit<SchedulingOptimizationRequest, 'cinemaId' | 'targetDate'>
  strategy: string
  generatedStrategy: string
  draftCleaning: number
}
export interface SavedSchedulingDraft { summary: SavedSchedulingSummary; content: SchedulingSnapshot }

export const schedulingApi = {
  savedDrafts() {
    return request.get<any, SavedSchedulingSummary[]>('/statistics/scheduling/saved-drafts')
  },
  savedDraft(id: string) {
    return request.get<any, SavedSchedulingDraft>(`/statistics/scheduling/saved-drafts/${id}`)
  },
  saveDraft(cinemaId: number, targetDate: string, content: SchedulingSnapshot) {
    return request.put<any, SavedSchedulingDraft>('/statistics/scheduling/saved-drafts', { cinemaId, targetDate, content })
  },
  deleteSavedDraft(id: string) {
    return request.delete(`/statistics/scheduling/saved-drafts/${id}`)
  },
  workspace(cinemaId: number, targetDate: string) {
    return request.get<any, SchedulingWorkspace>('/statistics/scheduling/workspace', { params: { cinemaId, targetDate } })
  },
  check(data: SchedulingDraftRequest) {
    return request.post<any, SchedulingDraftCheck>('/statistics/scheduling/check', data, { timeout: 60000 })
  },
  apply(data: SchedulingDraftRequest) {
    return request.post<any, { created: number; removed: number; message: string }>('/statistics/scheduling/apply', data, { timeout: 60000 })
  },
  getInsights(cinemaId?: number) {
    return request.get<any, any>('/statistics/scheduling-insights', { params: { cinemaId }, timeout: 60000 })
  },
  predict(data: PredictionRequest) {
    return request.post<any, any>('/statistics/scheduling/predict', data, { timeout: 60000 })
  },
  simulate(data: SimulationRequest) {
    return request.post<any, any>('/statistics/scheduling/simulate', data, { timeout: 60000 })
  },
  optimize(data: SchedulingOptimizationRequest) {
    return request.post<any, SchedulingOptimizationResult>('/statistics/scheduling/optimize', data, { timeout: 90000 })
  }
}

import request from '@/utils/request'

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

export const schedulingApi = {
  getInsights(cinemaId?: number) {
    return request.get<any, any>('/statistics/scheduling-insights', { params: { cinemaId } })
  },
  predict(data: PredictionRequest) {
    return request.post<any, any>('/statistics/scheduling/predict', data)
  },
  simulate(data: SimulationRequest) {
    return request.post<any, any>('/statistics/scheduling/simulate', data)
  }
}

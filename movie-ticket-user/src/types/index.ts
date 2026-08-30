export interface Movie {
  id: number
  title: string
  englishTitle?: string
  director?: string
  actors?: string
  genre?: string
  country?: string
  language?: string
  duration: number
  releaseDate?: string
  poster?: string
  trailer?: string
  description?: string
  rating?: number
  status?: number
  statusText?: string
  createTime?: string
  updateTime?: string
  matchScore?: number
  recommendationReason?: string
  recommendationScores?: RecommendationScores
  favoriteTime?: string
}

export interface RecommendationScores {
  content: number
  userPreference: number
  behavior: number
  scene: number
  trend: number
}

export interface RecommendationProfile {
  needsColdStart: boolean
  favoriteGenres: string[]
  viewedCount: number
  favoriteCount: number
  purchasedCount: number
  ratedCount: number
}

export interface RecommendationEvaluation {
  k: number
  evaluatedUsers: number
  precisionAtK: number
  recallAtK: number
  catalogCoverage: number
  diversity: number
  protocol: string
}

export interface MovieInsight {
  movieId: number
  todayBoxOffice: number
  todayViewerCount: number
  occupancyRate: number
  trendPercent: number
  trendPoints: number[]
  generatedAt: string
}

export interface Cinema {
  id: number
  name: string
  address: string
  lng?: number
  lat?: number
  phone?: string
  description?: string
  images?: string
  status?: number
}

export interface Showtime {
  id: number
  movieId: number
  movieTitle?: string
  moviePoster?: string
  movieDuration?: number
  cinemaId: number
  cinemaName?: string
  hallId: number
  hallName?: string
  startTime: string
  endTime: string
  basePrice: number
  totalSeats?: number
  availableSeats?: number
  status?: number
  statusText?: string
}

export interface Order {
  id: number
  orderNo: string
  userId: number
  userName?: string
  showtimeId: number
  movieId?: number
  movieTitle?: string
  moviePoster?: string
  cinemaName?: string
  hallName?: string
  showtime?: string
  seats: string
  seatCount: number
  totalPrice: number
  originalPrice?: number
  discountAmount?: number
  couponId?: number
  status: number
  statusText?: string
  ticketCode?: string
  isPicked?: number
  pickTime?: string
  payTime?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface Coupon {
  id: number
  name: string
  amount: number
  minSpend: number
  status: number
  statusText: string
  available: boolean
  validFrom: string
  expireTime: string
  useTime?: string
}

export interface SeatStatus {
  totalRows: number
  totalCols: number
  seatLayout: string
  occupiedSeats: string[]
  availableSeats: number
  totalSeats: number
}

export type SeatPreference = 'CENTER' | 'QUIET' | 'EASY_ACCESS' | 'COUPLE' | 'ACCESSIBLE'

export interface SeatRecommendationScores {
  visionCenter: number
  goldenDistance: number
  continuity: number
  accessConvenience: number
  preference: number
  realtimeAvailability: number
}

export interface SeatRecommendation {
  title: string
  icon: string
  match: number
  seatLabel: string
  seats: string[]
  count: number
  unitPrice: number
  reason: string
  modelReason?: string
  reasonSource?: 'model' | 'algorithm'
  tags: string[]
  scores: SeatRecommendationScores
  singleSeatPenalty: number
}

export interface SeatRecommendationResult {
  recommendations: SeatRecommendation[]
  interpretedPreference: SeatPreference
  preferenceSummary: string
  analysisSource: 'model' | 'algorithm'
  model?: string
  availabilityToken: string
  generatedAt: string
}

export interface Comment {
  id: number
  movieId: number
  userId: number
  userName?: string
  userAvatar?: string
  rating: number
  content?: string
  createTime?: string
}

export interface CommentEligibility {
  watched: boolean
  commented: boolean
  canComment: boolean
  ownComment?: Comment | null
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

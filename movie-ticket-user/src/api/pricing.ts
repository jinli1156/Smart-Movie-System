/**
 * 票价比较与优惠试算接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'

export interface PricePlan {
  couponId?: number
  couponName: string
  couponDiscount: number
  pointsUsed: number
  pointsDiscount: number
  discountAmount: number
  totalPrice: number
}
export interface PriceOffer {
  showtimeId: number
  cinemaId: number
  cinemaName: string
  hallName: string
  startTime: string
  availableSeats: number
  basePrice: number
  originalPrice: number
  bestPlan: PricePlan
  reason: string
}
export interface PriceComparison {
  movieId: number
  movieTitle: string
  moviePoster?: string
  date: string
  people: number
  personalized: boolean
  usePoints: boolean
  useCoupons: boolean
  availablePoints: number
  comparedCount: number
  matchedCount: number
  queriedAt: string
  offers: PriceOffer[]
  notice: string
}
export interface OrderQuote {
  orderNo: string
  originalPrice: number
  availablePoints: number
  bestPlan: PricePlan
  plans: PricePlan[]
  notice: string
}
export const pricingApi = {
  /** 比较符合条件的场次与优惠组合，返回实付价格方案。 */
  compare(params: { movieId?: number; showtimeId?: number; date?: string; people: number; usePoints: boolean; useCoupons: boolean; maxTotal?: number }, signal?: AbortSignal) {
    return request.get<any, PriceComparison>('/pricing/compare', { params, signal, silent: true })
  },
  /** 根据当前用户资产试算价格；试算不扣券、不扣积分。 */
  quote(orderNo: string, usePoints = true) {
    return request.get<any, OrderQuote>(`/pricing/orders/${encodeURIComponent(orderNo)}/quote`, { params: { usePoints }, silent: true })
  }
}

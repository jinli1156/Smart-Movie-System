import request from '@/utils/request'
import type { Coupon } from '@/types'

export const couponApi = {
  getMine() {
    return request.get<any, Coupon[]>('/coupon/my')
  },

  redeem(code: string) {
    return request.post<any, Coupon>('/coupon/redeem', { code })
  }
}

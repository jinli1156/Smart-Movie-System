import request from '@/utils/request'
import type { Coupon } from '@/types'

export const couponApi = {
  getMine() {
    return request.get<any, Coupon[]>('/coupon/my')
  }
}

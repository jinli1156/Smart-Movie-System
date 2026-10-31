/**
 * 优惠券与兑换码接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Coupon } from '@/types'

export const couponApi = {
  /** 读取本人优惠券及其有效状态。 */
  getMine() {
    return request.get<any, Coupon[]>('/coupon/my')
  },

  /** 校验兑换码的有效期、发放限额和重复领取限制，为本人发券。 */
  redeem(code: string) {
    return request.post<any, Coupon>('/coupon/redeem', { code })
  }
}

/**
 * 优惠券与兑换码接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { CouponCode, CouponRedemption, PageResult } from '@/types'

export const couponAdminApi = {
  /** 按筛选条件分页查询优惠券兑换码，供列表页展示。 */
  getPage(params: {
    page: number
    size: number
    keyword?: string
    status?: number
  }) {
    return request.get<any, PageResult<CouponCode>>('/admin/coupon-codes', { params })
  },

  /** 新增优惠券兑换码，由业务层执行参数及关联数据校验。 */
  create(data: CouponCode) {
    return request.post<any, CouponCode>('/admin/coupon-codes', data)
  },

  /** 更新优惠券兑换码，由业务层校验当前操作权限与业务条件。 */
  update(id: number, data: CouponCode) {
    return request.put<any, CouponCode>(`/admin/coupon-codes/${id}`, data)
  },

  /** 修改优惠券兑换码状态，具体可用状态和操作限制由业务层校验。 */
  updateStatus(id: number, status: number) {
    return request.put(`/admin/coupon-codes/${id}/status`, null, { params: { status } })
  },

  /** 删除优惠券兑换码，由业务层检查权限及关联数据限制。 */
  delete(id: number) {
    return request.delete(`/admin/coupon-codes/${id}`)
  },

  /** 分页查询兑换码领取记录，便于核对发放情况。 */
  getRedemptions(params: {
    page: number
    size: number
    couponCodeId?: number
    keyword?: string
  }) {
    return request.get<any, PageResult<CouponRedemption>>(
      '/admin/coupon-codes/redemptions',
      { params }
    )
  }
}

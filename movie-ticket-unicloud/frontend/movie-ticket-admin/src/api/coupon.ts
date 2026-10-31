import request from '@/utils/request'
import type { CouponCode, CouponRedemption, PageResult } from '@/types'

export const couponAdminApi = {
  getPage(params: {
    page: number
    size: number
    keyword?: string
    status?: number
  }) {
    return request.get<any, PageResult<CouponCode>>('/admin/coupon-codes', { params })
  },

  create(data: CouponCode) {
    return request.post<any, CouponCode>('/admin/coupon-codes', data)
  },

  update(id: number, data: CouponCode) {
    return request.put<any, CouponCode>(`/admin/coupon-codes/${id}`, data)
  },

  updateStatus(id: number, status: number) {
    return request.put(`/admin/coupon-codes/${id}/status`, null, { params: { status } })
  },

  delete(id: number) {
    return request.delete(`/admin/coupon-codes/${id}`)
  },

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

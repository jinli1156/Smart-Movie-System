import request from '@/utils/request'
import type { Order, PageResult } from '@/types'

export const orderApi = {
  getPage(params: { page: number; size: number; orderNo?: string; status?: number }) {
    return request.get<any, PageResult<Order>>('/order/page', { params })
  },

  getByOrderNo(orderNo: string) {
    return request.get<any, Order>(`/order/${orderNo}`)
  },

  cancel(orderNo: string) {
    return request.post(`/order/${orderNo}/cancel`)
  },

  refund(orderNo: string) {
    return request.post(`/order/${orderNo}/refund`)
  }
}

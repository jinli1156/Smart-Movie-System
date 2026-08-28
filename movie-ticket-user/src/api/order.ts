import request from '@/utils/request'
import type { Order, PageResult } from '@/types'

export const orderApi = {
  create(data: { showtimeId: number; seats: string[]; remark?: string }) {
    return request.post<any, string>('/order', data)
  },

  getMyOrders(params: { page: number; size: number; status?: number }) {
    return request.get<any, PageResult<Order>>('/order/my', { params })
  },

  getViewingHistory(params: { page: number; size: number }) {
    return request.get<any, PageResult<Order>>('/order/history', { params })
  },

  getByOrderNo(orderNo: string) {
    return request.get<any, Order>(`/order/${orderNo}`)
  },

  pay(orderNo: string, couponId?: number) {
    return request.post(`/order/${orderNo}/pay`, couponId ? { couponId } : {})
  },

  cancel(orderNo: string) {
    return request.post(`/order/${orderNo}/cancel`)
  },

  refund(orderNo: string) {
    return request.post(`/order/${orderNo}/refund`)
  },

  pickTicket(ticketCode: string) {
    return request.post<any, Order>('/order/pick', null, { params: { ticketCode } })
  }
}

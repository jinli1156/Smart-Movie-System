import request from '@/utils/request'
import type { Order, PageResult } from '@/types'

export const orderApi = {
  create(data: { showtimeId: number; seats: string[]; remark?: string }) {
    // 创建失败由确认页展示可操作的内联错误，避免全局提示与页面提示重复。
    return request.post<any, string>('/order', data, { silent: true })
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

  pay(orderNo: string, couponId?: number, points?: number) {
    const payload: { couponId?: number; points?: number } = {}
    if (couponId) payload.couponId = couponId
    if (points !== undefined) payload.points = points
    return request.post(`/order/${orderNo}/pay`, payload)
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

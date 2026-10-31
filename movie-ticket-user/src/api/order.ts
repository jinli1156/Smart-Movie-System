/**
 * 购票订单接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Order, PageResult } from '@/types'

export const orderApi = {
  /** 提交场次及座位创建待支付订单，成功后才开始订单锁座时限。 */
  create(data: { showtimeId: number; seats: string[]; remark?: string }) {
    // 创建失败由确认页展示可操作的内联错误，避免全局提示与页面提示重复。
    return request.post<any, string>('/order', data, { silent: true })
  },

  /** 仅分页查询当前登录用户的订单。 */
  getMyOrders(params: { page: number; size: number; status?: number }) {
    return request.get<any, PageResult<Order>>('/order/my', { params })
  },

  /** 查询本人已完成观影的购票记录。 */
  getViewingHistory(params: { page: number; size: number }) {
    return request.get<any, PageResult<Order>>('/order/history', { params })
  },

  /** 按订单号读取详情，并校验本人或管理员的数据访问范围。 */
  getByOrderNo(orderNo: string) {
    return request.get<any, Order>(`/order/${orderNo}`)
  },

  /** 校验订单状态、优惠券和积分，完成支付并记录取票凭证及积分变化。 */
  pay(orderNo: string, couponId?: number, points?: number) {
    const payload: { couponId?: number; points?: number } = {}
    if (couponId) payload.couponId = couponId
    if (points !== undefined) payload.points = points
    return request.post(`/order/${orderNo}/pay`, payload)
  },

  /** 提交本人待支付订单的取消请求，释放库存由后端处理。 */
  cancel(orderNo: string) {
    return request.post(`/order/${orderNo}/cancel`)
  },

  /** 按退票规则更新退款状态，返还优惠券和积分，并恢复座位库存。 */
  refund(orderNo: string) {
    return request.post(`/order/${orderNo}/refund`)
  },

  /** 通过取票码核验已支付订单，并记录取票状态。 */
  pickTicket(ticketCode: string) {
    return request.post<any, Order>('/order/pick', null, { params: { ticketCode } })
  }
}

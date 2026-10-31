/**
 * 购票订单接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Order, PageResult } from '@/types'

export const orderApi = {
  /** 通过取票码核验已支付订单，并记录取票状态。 */
  pick(ticketCode: string) {
    return request.post<any, Order>('/order/pick', null, { params: { ticketCode } })
  },
  /** 按筛选条件分页查询订单，供列表页展示。 */
  getPage(params: { page: number; size: number; orderNo?: string; status?: number }) {
    return request.get<any, PageResult<Order>>('/order/page', { params })
  },

  /** 按订单号读取详情，并校验本人或管理员的数据访问范围。 */
  getByOrderNo(orderNo: string) {
    return request.get<any, Order>(`/order/${orderNo}`)
  },

  /** 提交管理范围内订单的取消请求，权限与订单状态由后端校验。 */
  cancel(orderNo: string) {
    return request.post(`/order/${orderNo}/cancel`)
  },

  /** 按退票规则更新退款状态，返还优惠券和积分，并恢复座位库存。 */
  refund(orderNo: string) {
    return request.post(`/order/${orderNo}/refund`)
  }
}

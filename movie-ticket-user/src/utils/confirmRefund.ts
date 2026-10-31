/**
 * 集中展示退票确认与费用说明，供各订单入口复用。
 */
import { h } from 'vue'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import RefundSummary from '@/components/RefundSummary.vue'

export const confirmRefund = (amounts: {
  totalPrice: number
  refundAmount: number
  fee: number
  feeRate: number
}) => ElMessageBox.confirm(h(RefundSummary, amounts), '申请退票', {
  confirmButtonText: '确认退票',
  cancelButtonText: '暂不退票',
  customClass: 'refund-confirm-dialog',
  closeOnClickModal: false
})

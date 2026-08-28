<template>
  <div class="order-detail-page">
    <!-- TOP BAR -->
    <header class="top-bar">
      <button class="back-btn" @click="router.back()">
        <span class="msr">arrow_back</span>
        <span>返回</span>
      </button>
      <div class="page-title">
        <span class="msr">receipt_long</span>
        <span>订单详情</span>
      </div>
      <div class="spacer"></div>
    </header>

    <div class="container" v-loading="loading">
      <div v-if="order" class="detail-card">
        <!-- STATUS HEADER -->
        <div class="status-header">
          <div class="status-icon" :class="'status-' + order.status">
            <span class="msr">{{ getStatusIcon(order.status) }}</span>
          </div>
          <div class="status-info">
            <h2 class="status-title">{{ order.statusText }}</h2>
            <p class="status-desc">{{ getStatusDesc(order.status) }}</p>
          </div>
        </div>

        <!-- ORDER INFO -->
        <div class="info-section">
          <div class="section-header">
            <span class="msr">confirmation_number</span>
            <span>订单信息</span>
          </div>
          <div class="info-grid">
            <div class="info-item full">
              <span class="info-label">订单号</span>
              <span class="info-value mono">{{ order.orderNo }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">电影</span>
              <span class="info-value">{{ order.movieTitle }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">影院</span>
              <span class="info-value">{{ order.cinemaName }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">影厅</span>
              <span class="info-value">{{ order.hallName }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">场次</span>
              <span class="info-value">{{ formatDateTime(order.showtime!) }}</span>
            </div>
            <div class="info-item full">
              <span class="info-label">座位</span>
              <div class="seats-container">
                <span v-for="seat in order.seats.split(',')" :key="seat" class="seat-tag">
                  {{ seat }}
                </span>
              </div>
            </div>
            <div class="info-item">
              <span class="info-label">票数</span>
              <span class="info-value">{{ order.seatCount }} 张</span>
            </div>
            <div class="info-item">
              <span class="info-label">总价</span>
              <span class="info-value price">¥{{ order.totalPrice }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">下单时间</span>
              <span class="info-value">{{ formatDateTime(order.createTime!) }}</span>
            </div>
            <div class="info-item" v-if="order.payTime">
              <span class="info-label">支付时间</span>
              <span class="info-value">{{ formatDateTime(order.payTime) }}</span>
            </div>
            <div class="info-item full" v-if="order.remark">
              <span class="info-label">备注</span>
              <span class="info-value">{{ order.remark }}</span>
            </div>
          </div>
        </div>

        <!-- ACTIONS -->
        <div class="actions-section">
          <!-- 已支付订单显示取票码 -->
          <button
            v-if="order.status === 1"
            class="action-btn primary large"
            @click="showQRCode = true"
          >
            <span class="msr">qr_code</span>
            <span>查看取票码</span>
          </button>

          <button
            v-if="order.status === 0"
            class="action-btn primary large"
            @click="handlePay"
          >
            <span class="msr">payment</span>
            <span>立即支付</span>
          </button>
          <button
            v-if="order.status === 0"
            class="action-btn secondary large"
            @click="handleCancel"
          >
            <span class="msr">close</span>
            <span>取消订单</span>
          </button>

          <!-- 已支付订单的退票按钮 -->
          <template v-if="order.status === 1">
            <button
              v-if="canRefund"
              class="action-btn danger large"
              @click="handleRefund"
            >
              <span class="msr">receipt_long</span>
              <span>申请退票</span>
            </button>
            <button
              v-else
              class="action-btn disabled large"
              disabled
            >
              <span class="msr">block</span>
              <span>{{ refundDisabledReason }}</span>
            </button>
            <div v-if="!canRefund" class="refund-tip">
              <span class="msr">info</span>
              <span>{{ refundDisabledReason }}</span>
            </div>
          </template>
        </div>
      </div>

      <!-- 取票码二维码对话框 -->
      <TicketQRCode v-model="showQRCode" :order="order" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderApi } from '@/api/order'
import type { Order } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import TicketQRCode from '@/components/TicketQRCode.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const order = ref<Order | null>(null)
const showQRCode = ref(false)

const getStatusIcon = (status: number) => {
  const icons: Record<number, string> = {
    0: 'schedule',
    1: 'check_circle',
    2: 'cancel',
    3: 'receipt_long'
  }
  return icons[status] || 'info'
}

const getStatusDesc = (status: number) => {
  const descs: Record<number, string> = {
    0: '请在10分钟内完成支付，超时订单将自动取消',
    1: '订单已支付成功，请按时到影院取票观影',
    2: '订单已取消，如有疑问请联系客服',
    3: '退票已完成，退款将在1-7个工作日内到账'
  }
  return descs[status] || ''
}

// 计算是否可以退票
const canRefund = computed(() => {
  if (!order.value || order.value.status !== 1 || !order.value.showtime) return false

  const now = new Date().getTime()
  const showtimeDate = new Date(order.value.showtime).getTime()
  const minutesUntilShowtime = (showtimeDate - now) / (1000 * 60)

  return minutesUntilShowtime > 30
})

// 退票禁用原因
const refundDisabledReason = computed(() => {
  if (!order.value || !order.value.showtime) return '无法退票'

  const now = new Date().getTime()
  const showtimeDate = new Date(order.value.showtime).getTime()
  const minutesUntilShowtime = (showtimeDate - now) / (1000 * 60)

  if (minutesUntilShowtime <= 30) {
    return '距离开场不足30分钟，无法退票'
  }

  return '无法退票'
})

// 计算退款金额和手续费
const getRefundInfo = computed(() => {
  if (!order.value || !order.value.showtime) return null

  const now = new Date().getTime()
  const showtimeDate = new Date(order.value.showtime).getTime()
  const hoursUntilShowtime = (showtimeDate - now) / (1000 * 60 * 60)

  const totalPrice = Number(order.value.totalPrice)

  if (hoursUntilShowtime > 24) {
    // 开场前24小时外，全额退款
    return {
      refundAmount: totalPrice,
      fee: 0,
      feeRate: 0,
      message: '全额退款'
    }
  } else if (hoursUntilShowtime > 0.5) {
    // 开场前24小时内但超过30分钟，扣除10%手续费
    const fee = totalPrice * 0.1
    return {
      refundAmount: totalPrice - fee,
      fee: fee,
      feeRate: 10,
      message: '扣除10%手续费'
    }
  }

  return null
})

const loadOrder = async () => {
  loading.value = true
  try {
    const orderNo = route.params.orderNo as string
    order.value = await orderApi.getByOrderNo(orderNo)
  } catch (error) {
    console.error('加载订单失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const formatDateTime = (datetime: string) => {
  const date = new Date(datetime)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const handlePay = () => {
  if (!order.value) return
  router.push(`/order/pay/${order.value.orderNo}`)
}

const handleCancel = async () => {
  if (!order.value) return

  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await orderApi.cancel(order.value.orderNo)
    ElMessage.success('订单已取消')
    loadOrder()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('取消订单失败:', error)
    }
  }
}

const handleRefund = async () => {
  if (!order.value) return

  const refundInfo = getRefundInfo.value
  if (!refundInfo) {
    ElMessage.error('无法计算退款金额')
    return
  }

  try {
    let message = `
      <div style="padding: 16px 0;">
        <div style="display: flex; align-items: flex-start; gap: 12px; margin-bottom: 16px; padding: 16px; background: #FFF9E6; border-radius: 8px; border: 1px solid #FFB84D;">
          <span style="font-size: 24px;">⚠️</span>
          <div style="flex: 1;">
            <div style="font-weight: 700; font-size: 15px; color: #333; margin-bottom: 8px;">退票确认</div>
            <div style="font-size: 14px; color: #666; line-height: 1.6;">
              确认申请退票吗？退票成功后座位将立即释放。
            </div>
          </div>
        </div>

        <div style="background: #f3f4f6; border-radius: 8px; padding: 16px; margin-bottom: 16px;">
          <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
            <span style="color: #666;">订单金额</span>
            <span style="font-weight: 600;">¥${order.value.totalPrice}</span>
          </div>
    `

    if (refundInfo.fee > 0) {
      message += `
          <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
            <span style="color: #666;">手续费 (${refundInfo.feeRate}%)</span>
            <span style="color: #f59e0b; font-weight: 600;">-¥${refundInfo.fee.toFixed(2)}</span>
          </div>
      `
    }

    message += `
          <div style="height: 1px; background: #e5e7eb; margin: 12px 0;"></div>
          <div style="display: flex; justify-content: space-between;">
            <span style="font-weight: 700; font-size: 16px;">退款金额</span>
            <span style="font-weight: 800; font-size: 20px; color: #16a34a;">¥${refundInfo.refundAmount.toFixed(2)}</span>
          </div>
        </div>

        <div style="font-size: 13px; color: #666; line-height: 1.6;">
          <div style="margin-bottom: 4px;">• 退款将原路退回至支付账户</div>
          <div style="margin-bottom: 4px;">• 预计 1-7 个工作日到账</div>
          <div>• 退票成功后将收到通知</div>
        </div>
      </div>
    `

    await ElMessageBox.confirm(message, '申请退票', {
      confirmButtonText: '确认退票',
      cancelButtonText: '取消',
      type: 'warning',
      dangerouslyUseHTMLString: true,
      customClass: 'refund-confirm-dialog'
    })

    await orderApi.refund(order.value.orderNo)
    ElMessage.success('退票成功，退款将在1-7个工作日内到账')
    loadOrder()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('退票失败:', error)
    }
  }
}

onMounted(() => {
  loadOrder()
})
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Geist:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700;800&display=swap');
@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200&display=swap');

.msr {
  font-family: 'Material Symbols Rounded';
  font-weight: normal;
  font-style: normal;
  line-height: 1;
  letter-spacing: normal;
  text-transform: none;
  display: inline-block;
  white-space: nowrap;
  word-wrap: normal;
  direction: ltr;
  font-feature-settings: 'liga';
  -webkit-font-smoothing: antialiased;
  font-size: 20px;
  user-select: none;
}

.order-detail-page {
  --primary: #FF8400;
  --primary-foreground: #111111;
  --bg: #F2F3F0;
  --card: #FFFFFF;
  --fg: #111111;
  --muted: #666666;
  --border: #CBCCC9;
  --secondary: #E7E8E5;
  --radius-m: 16px;
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  --font-sans: 'Geist', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;

  min-height: 100vh;
  background: var(--bg);
  font-family: var(--font-sans);
  color: var(--fg);
}

/* TOP BAR */
.top-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 32px;
  background: var(--card);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 50;
  backdrop-filter: blur(8px);
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  background: var(--secondary);
  border: 1px solid var(--border);
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
  transition: all 0.15s;
  font-family: var(--font-sans);
}

.back-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.back-btn .msr {
  font-size: 18px;
}

.page-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 20px;
  font-weight: 700;
  color: var(--fg);
}

.page-title .msr {
  font-size: 24px;
  color: var(--primary);
}

.spacer {
  flex: 1;
}

/* CONTAINER */
.container {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px;
}

/* DETAIL CARD */
.detail-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  overflow: hidden;
}

/* STATUS HEADER */
.status-header {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 32px;
  background: linear-gradient(135deg, #FFF8F0 0%, #FFF 100%);
  border-bottom: 2px solid var(--secondary);
}

.status-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.status-icon .msr {
  font-size: 32px;
}

.status-icon.status-0 {
  background: linear-gradient(135deg, #FEF3C7 0%, #FDE68A 100%);
  color: #D97706;
}

.status-icon.status-1 {
  background: linear-gradient(135deg, #D1FAE5 0%, #A7F3D0 100%);
  color: #16A34A;
}

.status-icon.status-2 {
  background: linear-gradient(135deg, #E5E7EB 0%, #D1D5DB 100%);
  color: #6B7280;
}

.status-icon.status-3 {
  background: linear-gradient(135deg, #FEE2E2 0%, #FECACA 100%);
  color: #DC2626;
}

.status-info {
  flex: 1;
}

.status-title {
  font-size: 28px;
  font-weight: 800;
  margin: 0 0 8px 0;
  letter-spacing: -0.02em;
}

.status-desc {
  font-size: 14px;
  color: var(--muted);
  margin: 0;
  line-height: 1.5;
}

/* INFO SECTION */
.info-section {
  padding: 32px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 700;
  color: var(--fg);
  margin-bottom: 24px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--secondary);
}

.section-header .msr {
  font-size: 24px;
  color: var(--primary);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-item.full {
  grid-column: 1 / -1;
}

.info-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--muted);
  letter-spacing: 0.02em;
}

.info-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--fg);
}

.info-value.mono {
  font-family: var(--font-mono);
  font-size: 14px;
  letter-spacing: 0.5px;
}

.info-value.price {
  font-family: var(--font-mono);
  font-size: 24px;
  font-weight: 800;
  color: var(--primary);
}

.seats-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.seat-tag {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  background: linear-gradient(135deg, #FFF8F0 0%, #FFE8CC 100%);
  border: 1px solid #FFD6A5;
  border-radius: 8px;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  color: var(--primary);
}

/* ACTIONS SECTION */
.actions-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px 32px 32px;
  background: var(--bg);
  border-top: 1px solid var(--border);
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 28px;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  font-family: var(--font-sans);
}

.action-btn.large {
  padding: 16px 32px;
  font-size: 16px;
}

.action-btn .msr {
  font-size: 20px;
}

.action-btn.primary {
  background: var(--primary);
  color: var(--primary-foreground);
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.25);
}

.action-btn.primary:hover {
  background: #FF6B00;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(255, 132, 0, 0.35);
}

.action-btn.secondary {
  background: var(--secondary);
  color: var(--fg);
  border: 1px solid var(--border);
}

.action-btn.secondary:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.action-btn.danger {
  background: rgba(239, 68, 68, 0.1);
  color: #DC2626;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.action-btn.danger:hover {
  background: rgba(239, 68, 68, 0.15);
  border-color: #DC2626;
}

.action-btn.disabled {
  background: var(--secondary);
  color: var(--muted);
  cursor: not-allowed;
  opacity: 0.6;
}

.refund-tip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  background: #FEF3C7;
  border: 1px solid #FCD34D;
  border-radius: 10px;
  font-size: 14px;
  color: #92400E;
}

.refund-tip .msr {
  font-size: 20px;
  color: #F59E0B;
}

/* RESPONSIVE */
@media (max-width: 768px) {
  .container {
    padding: 20px 16px;
  }

  .top-bar {
    padding: 16px 20px;
  }

  .status-header {
    padding: 24px 20px;
  }

  .info-section {
    padding: 24px 20px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .actions-section {
    padding: 20px;
  }

  .action-btn.large {
    width: 100%;
  }
}
</style>

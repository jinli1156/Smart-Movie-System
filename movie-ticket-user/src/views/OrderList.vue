<template>
  <div class="order-list-page">
    <!-- ============ SIDEBAR ============ -->
    <AppSidebar :show-quick-access="false" />

    <!-- ============ MAIN AREA ============ -->
    <main class="main-area">
      <!-- TOP BAR -->
      <header class="top-bar">
        <div class="page-title">
          <MaterialIcon class="msr" name="receipt_long" />
          <span>我的订单</span>
        </div>
        <div class="spacer"></div>
      </header>

      <div class="container">
      <!-- FILTER TABS -->
      <div class="filter-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          class="tab-btn"
          :class="{ active: activeTab === tab.value }"
          @click="handleTabChange(tab.value)"
        >
          <MaterialIcon class="msr" v-if="tab.icon" :name="tab.icon" />
          <span>{{ tab.label }}</span>
          <span v-if="tab.badge" class="tab-badge">{{ tab.badge }}</span>
        </button>
      </div>

      <div v-loading="loading" class="content-area">
        <div v-if="orders.length > 0" class="order-list">
          <article
            v-for="order in orders"
            :key="order.id"
            class="order-card"
            @click="goToDetail(order.orderNo)"
          >
            <!-- CARD HEADER -->
            <div class="card-header">
              <div class="order-no">
                <MaterialIcon class="msr" name="tag" />
                <span>{{ order.orderNo }}</span>
              </div>
              <div class="header-right">
                <div v-if="order.status === 0 && getCountdown(order)" class="countdown-badge" :class="{ urgent: getCountdown(order)! < 120 }">
                  <MaterialIcon class="msr" name="schedule" />
                  <span class="time">{{ formatCountdown(getCountdown(order)!) }}</span>
                </div>
                <span class="status-badge" :class="'status-' + order.status">
                  {{ order.statusText }}
                </span>
              </div>
            </div>

            <!-- CARD CONTENT -->
            <div class="card-content">
              <div class="movie-poster">
                <img :src="order.moviePoster || '/placeholder.jpg'" :alt="order.movieTitle" />
                <div class="poster-overlay">
                  <MaterialIcon class="msr" name="movie" />
                </div>
              </div>

              <div class="order-info">
                <h3 class="movie-title">{{ order.movieTitle }}</h3>
                <div class="info-row">
                  <MaterialIcon class="msr" name="theaters" />
                  <span>{{ order.cinemaName }}</span>
                </div>
                <div class="info-row">
                  <MaterialIcon class="msr" name="meeting_room" />
                  <span>{{ order.hallName }}</span>
                </div>
                <div class="info-row">
                  <MaterialIcon class="msr" name="event" />
                  <span>{{ formatDateTime(order.showtime!) }}</span>
                </div>
                <div class="info-row seats">
                  <MaterialIcon class="msr" name="event_seat" />
                  <span>{{ order.seats }}</span>
                </div>
              </div>

              <div class="order-price">
                <div class="price-label">订单金额</div>
                <div class="price">
                  <span class="currency">¥</span>
                  <span class="amount">{{ order.totalPrice }}</span>
                </div>
                <div class="count">共 {{ order.seatCount }} 张</div>
              </div>
            </div>

            <!-- CARD ACTIONS -->
            <div class="card-actions">
              <button
                v-if="order.status === 1"
                class="action-btn primary"
                @click.stop="showQRCode(order)"
              >
                <MaterialIcon class="msr" name="qr_code" />
                <span>查看取票码</span>
              </button>
              <button
                v-if="order.status === 0"
                class="action-btn primary"
                @click.stop="handlePay(order.orderNo)"
              >
                <MaterialIcon class="msr" name="payment" />
                <span>立即支付</span>
              </button>
              <button
                v-if="order.status === 0"
                class="action-btn secondary"
                @click.stop="handleCancel(order.orderNo)"
              >
                <MaterialIcon class="msr" name="close" />
                <span>取消订单</span>
              </button>
              <button
                v-if="order.status === 1"
                class="action-btn danger"
                @click.stop="handleRefund(order.orderNo)"
              >
                <MaterialIcon class="msr" name="receipt_long" />
                <span>申请退票</span>
              </button>
              <button class="action-btn ghost" @click.stop="goToDetail(order.orderNo)">
                <MaterialIcon class="msr" name="info" />
                <span>查看详情</span>
              </button>
            </div>
          </article>
        </div>

        <div v-else class="empty-state">
          <MaterialIcon class="msr empty-icon" name="receipt_long" />
          <p class="empty-text">暂无订单</p>
          <button class="empty-btn" @click="router.push('/movies')">
            <span>去选片</span>
            <MaterialIcon class="msr" name="arrow_forward" />
          </button>
        </div>

        <div v-if="total > 0" class="pagination">
          <button
            class="page-btn"
            :disabled="page === 1"
            @click="changePage(page - 1)"
          >
            <MaterialIcon class="msr" name="arrow_back" />
            <span>上一页</span>
          </button>
          <div class="page-info">
            <span class="current">{{ page }}</span>
            <span class="sep">/</span>
            <span class="total-pages">{{ Math.ceil(total / size) }}</span>
          </div>
          <button
            class="page-btn"
            :disabled="page >= Math.ceil(total / size)"
            @click="changePage(page + 1)"
          >
            <span>下一页</span>
            <MaterialIcon class="msr" name="arrow_forward" />
          </button>
        </div>
      </div>
      </div>
    </main>

    <!-- 取票码对话框 -->
    <TicketQRCode v-model="showQRCodeDialog" :order="selectedOrder" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { orderApi } from '@/api/order'
import type { Order } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import TicketQRCode from '@/components/TicketQRCode.vue'
import AppSidebar from '@/components/AppSidebar.vue'

const router = useRouter()

const loading = ref(false)
const activeTab = ref('all')
const orders = ref<Order[]>([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const currentTime = ref(Date.now())
const showQRCodeDialog = ref(false)
const selectedOrder = ref<Order | null>(null)
let timer: number | null = null

const tabs: Array<{ value: string; label: string; icon: string; badge?: number | string }> = [
  { value: 'all', label: '全部', icon: 'list' },
  { value: '0', label: '待支付', icon: 'schedule' },
  { value: '1', label: '已支付', icon: 'check_circle' },
  { value: '2', label: '已取消', icon: 'cancel' },
  { value: '3', label: '已退款', icon: 'receipt_long' }
]

const loadOrders = async () => {
  loading.value = true
  try {
    const status = activeTab.value === 'all' ? undefined : Number(activeTab.value)
    const res = await orderApi.getMyOrders({ page: page.value, size: size.value, status })
    orders.value = res.records
    total.value = res.total
  } catch (error) {
    console.error('加载订单失败:', error)
  } finally {
    loading.value = false
  }
}

const handleTabChange = (tab: string) => {
  activeTab.value = tab
  page.value = 1
  loadOrders()
}

const changePage = (newPage: number) => {
  page.value = newPage
  loadOrders()
}

const showQRCode = (order: Order) => {
  selectedOrder.value = order
  showQRCodeDialog.value = true
}

const formatDateTime = (datetime: string) => {
  const date = new Date(datetime)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const goToDetail = (orderNo: string) => {
  router.push(`/order/${orderNo}`)
}

const handlePay = (orderNo: string) => {
  router.push(`/order/pay/${orderNo}`)
}

const handleCancel = async (orderNo: string) => {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await orderApi.cancel(orderNo)
    ElMessage.success('订单已取消')
    loadOrders()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('取消订单失败:', error)
    }
  }
}

const handleRefund = async (orderNo: string) => {
  try {
    // 先获取订单详情以计算退款信息
    const orderDetail = await orderApi.getByOrderNo(orderNo)

    if (!orderDetail.showtime) {
      ElMessage.error('无法获取场次信息')
      return
    }

    const now = new Date().getTime()
    const showtimeDate = new Date(orderDetail.showtime).getTime()
    const hoursUntilShowtime = (showtimeDate - now) / (1000 * 60 * 60)
    const totalPrice = Number(orderDetail.totalPrice)

    let refundAmount = totalPrice
    let fee = 0
    let feeRate = 0

    if (hoursUntilShowtime > 24) {
      // 全额退款
      refundAmount = totalPrice
      fee = 0
      feeRate = 0
    } else if (hoursUntilShowtime > 0.5) {
      // 扣除10%手续费
      fee = totalPrice * 0.1
      refundAmount = totalPrice - fee
      feeRate = 10
    } else {
      ElMessage.error('距离开场不足30分钟，无法退票')
      return
    }

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
            <span style="font-weight: 600;">¥${totalPrice.toFixed(2)}</span>
          </div>
    `

    if (fee > 0) {
      message += `
          <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
            <span style="color: #666;">手续费 (${feeRate}%)</span>
            <span style="color: #f59e0b; font-weight: 600;">-¥${fee.toFixed(2)}</span>
          </div>
      `
    }

    message += `
          <div style="height: 1px; background: #e5e7eb; margin: 12px 0;"></div>
          <div style="display: flex; justify-content: space-between;">
            <span style="font-weight: 700; font-size: 16px;">退款金额</span>
            <span style="font-weight: 800; font-size: 20px; color: #16a34a;">¥${refundAmount.toFixed(2)}</span>
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

    await orderApi.refund(orderNo)
    ElMessage.success('退票成功，退款将在1-7个工作日内到账')
    loadOrders()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('退票失败:', error)
    }
  }
}

// 计算订单剩余时间（秒）
const getCountdown = (order: Order) => {
  if (order.status !== 0 || !order.createTime) return null

  const createTime = new Date(order.createTime).getTime()
  const elapsed = Math.floor((currentTime.value - createTime) / 1000)
  const remaining = 10 * 60 - elapsed

  return remaining > 0 ? remaining : 0
}

// 格式化倒计时显示
const formatCountdown = (seconds: number) => {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

// 启动定时器
const startTimer = () => {
  timer = window.setInterval(() => {
    currentTime.value = Date.now()

    // 检查是否有订单超时，如果有则刷新列表
    const hasTimeout = orders.value.some(order => {
      if (order.status === 0 && order.createTime) {
        const countdown = getCountdown(order)
        return countdown === 0
      }
      return false
    })

    if (hasTimeout) {
      loadOrders()
    }
  }, 1000)
}

onMounted(() => {
  loadOrders()
  startTimer()
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
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

.order-list-page {
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

  display: flex;
  min-height: 100vh;
  background: var(--bg);
  font-family: var(--font-sans);
  color: var(--fg);
}

.main-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
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
  width: 100%;
  margin: 0 auto;
  padding: 28px 40px;
}

/* FILTER TABS */
.filter-tabs {
  display: flex;
  gap: 8px;
  padding: 6px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border: none;
  background: transparent;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.15s;
  font-family: var(--font-sans);
}

.tab-btn:hover {
  background: var(--secondary);
  color: var(--fg);
}

.tab-btn.active {
  background: var(--primary);
  color: var(--primary-foreground);
  box-shadow: 0 2px 8px rgba(255, 132, 0, 0.25);
}

.tab-btn .msr {
  font-size: 18px;
}

.tab-badge {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  background: rgba(255, 132, 0, 0.15);
  color: var(--primary);
  border-radius: 4px;
}

.tab-btn.active .tab-badge {
  background: rgba(17, 17, 17, 0.2);
  color: var(--primary-foreground);
}

/* CONTENT AREA */
.content-area {
  min-height: 400px;
}

/* ORDER LIST */
.order-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.order-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  padding: 24px;
  cursor: pointer;
  transition: all 0.2s;
}

.order-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: var(--primary);
}

/* CARD HEADER */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--secondary);
  margin-bottom: 20px;
}

.order-no {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--muted);
}

.order-no .msr {
  font-size: 16px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.countdown-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: linear-gradient(135deg, #FFF9E6 0%, #FFE8CC 100%);
  border: 1.5px solid #FFB84D;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: #FF8400;
  transition: all 0.3s;
}

.countdown-badge.urgent {
  background: linear-gradient(135deg, #FFE5E5 0%, #FFD6D6 100%);
  border-color: #FF4D4D;
  color: #DC2626;
  animation: pulse-badge 2s ease-in-out infinite;
}

@keyframes pulse-badge {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

.countdown-badge .msr {
  font-size: 16px;
}

.countdown-badge .time {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.status-badge {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.status-badge.status-0 {
  background: rgba(245, 158, 11, 0.15);
  color: #D97706;
}

.status-badge.status-1 {
  background: rgba(34, 197, 94, 0.15);
  color: #16A34A;
}

.status-badge.status-2 {
  background: rgba(107, 114, 128, 0.15);
  color: #6B7280;
}

.status-badge.status-3 {
  background: rgba(239, 68, 68, 0.15);
  color: #DC2626;
}

/* CARD CONTENT */
.card-content {
  display: grid;
  grid-template-columns: 120px 1fr auto;
  gap: 20px;
  margin-bottom: 20px;
}

.movie-poster {
  position: relative;
  width: 120px;
  height: 160px;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, #1a3a5c 0%, #0a1f3a 100%);
}

.movie-poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.poster-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);
  opacity: 0;
  transition: opacity 0.2s;
}

.order-card:hover .poster-overlay {
  opacity: 1;
}

.poster-overlay .msr {
  font-size: 48px;
  color: rgba(255, 255, 255, 0.9);
}

.order-info {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.movie-title {
  font-size: 20px;
  font-weight: 800;
  color: var(--fg);
  margin: 0 0 4px 0;
  letter-spacing: -0.01em;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--muted);
}

.info-row .msr {
  font-size: 16px;
  color: var(--primary);
}

.info-row.seats {
  font-family: var(--font-mono);
  font-weight: 600;
  color: var(--fg);
}

.order-price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 4px;
}

.price-label {
  font-size: 12px;
  color: var(--muted);
  font-weight: 500;
}

.price {
  display: flex;
  align-items: baseline;
  gap: 2px;
  color: var(--primary);
}

.currency {
  font-size: 16px;
  font-weight: 700;
}

.amount {
  font-family: var(--font-mono);
  font-size: 32px;
  font-weight: 800;
  line-height: 1;
}

.count {
  font-size: 13px;
  color: var(--muted);
  font-weight: 500;
}

/* CARD ACTIONS */
.card-actions {
  display: flex;
  gap: 10px;
  padding-top: 16px;
  border-top: 1px solid var(--secondary);
  flex-wrap: wrap;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border: none;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  font-family: var(--font-sans);
}

.action-btn .msr {
  font-size: 16px;
}

.action-btn.primary {
  background: var(--primary);
  color: var(--primary-foreground);
  box-shadow: 0 2px 8px rgba(255, 132, 0, 0.25);
}

.action-btn.primary:hover {
  background: #FF6B00;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.35);
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

.action-btn.ghost {
  background: transparent;
  color: var(--muted);
  border: 1px solid var(--border);
}

.action-btn.ghost:hover {
  background: var(--secondary);
  color: var(--fg);
}

/* EMPTY STATE */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
}

.empty-icon {
  font-size: 80px !important;
  color: var(--border);
  margin-bottom: 16px;
}

.empty-text {
  font-size: 16px;
  color: var(--muted);
  margin: 0 0 24px 0;
}

.empty-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: var(--primary);
  color: var(--primary-foreground);
  border: none;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  font-family: var(--font-sans);
}

.empty-btn:hover {
  background: #FF6B00;
  transform: translateY(-1px);
}

.empty-btn .msr {
  font-size: 18px;
}

/* PAGINATION */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  margin-top: 32px;
  padding: 20px;
}

.page-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
  transition: all 0.15s;
  font-family: var(--font-sans);
}

.page-btn:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-btn .msr {
  font-size: 16px;
}

.page-info {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  font-family: var(--font-mono);
}

.current {
  font-size: 24px;
  font-weight: 800;
  color: var(--primary);
}

.sep {
  font-size: 16px;
  color: var(--muted);
}

.total-pages {
  font-size: 16px;
  font-weight: 600;
  color: var(--muted);
}

/* RESPONSIVE */
@media (max-width: 768px) {
  .container {
    padding: 20px 16px;
  }

  .top-bar {
    padding: 16px 20px;
  }

  .card-content {
    grid-template-columns: 90px 1fr;
  }

  .order-price {
    grid-column: 1 / -1;
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    padding-top: 12px;
    border-top: 1px solid var(--secondary);
    margin-top: 12px;
  }

  .card-actions {
    flex-direction: column;
  }

  .action-btn {
    width: 100%;
    justify-content: center;
  }
}
</style>

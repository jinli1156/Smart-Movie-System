<template>
  <div class="pay-app">
    <!-- ============ TOP BAR ============ -->
    <header class="top-bar">
      <div class="tb-left">
        <button class="tb-back" @click="router.back()">
          <MaterialIcon class="msr" name="arrow_back" />
        </button>
        <div class="brand-block">
          <div class="brand-mark"><img class="brand-logo-image" src="/xiangzhi-ticket.png" alt="响指电影" /></div>
          <div class="brand-text">
            <span class="brand brand-wordmark" role="img" aria-label="响指电影"></span>
            <span class="brand-sub">premium booking</span>
          </div>
        </div>
        <div class="crumbs">
          <span class="crumb" @click="router.push('/home')">首页</span>
          <MaterialIcon class="msr crumb-sep" name="chevron_right" />
          <span class="crumb" @click="router.push('/orders')">订单</span>
          <MaterialIcon class="msr crumb-sep" name="chevron_right" />
          <span class="crumb active">收银台</span>
        </div>
      </div>
      <div class="tb-right">
        <div class="tb-step">
          <span class="step done"><MaterialIcon class="msr" name="check" /></span>
          <span class="step-label done">选座</span>
          <span class="step-line done"></span>
          <span class="step done"><MaterialIcon class="msr" name="check" /></span>
          <span class="step-label done">确认</span>
          <span class="step-line active"></span>
          <span class="step active">3</span>
          <span class="step-label active">支付</span>
          <span class="step-line"></span>
          <span class="step">4</span>
          <span class="step-label">取票</span>
        </div>
        <div class="tb-timer" :class="{ urgent: countdown < 60 }">
          <MaterialIcon class="msr" name="schedule" />
          <span class="timer-label">支付剩余</span>
          <span class="timer-time">{{ countdownText }}</span>
        </div>
      </div>
    </header>

    <div class="page-body" v-loading="loading">
      <!-- ============ PAGE HEADER ============ -->
      <section class="page-head">
        <div class="ph-left">
          <div class="ph-eyebrow">
            <span class="dot-primary"></span>
            <span>CHECKOUT · 2026</span>
          </div>
          <h1 class="ph-title">即将完成支付,享受沉浸观影</h1>
          <p class="ph-sub">请在倒计时结束前完成支付。可比较同片场次，并确认优惠券与积分方案。</p>
        </div>
        <div class="ph-right">
          <div class="ph-stat">
            <span class="ph-stat-num">¥{{ savedAmount }}</span>
            <span class="ph-stat-label">本单 AI 节省</span>
          </div>
          <div class="ph-divider"></div>
          <div class="ph-stat">
            <span class="ph-stat-num">{{ savedPercent }}%</span>
            <span class="ph-stat-label">综合折扣</span>
          </div>
        </div>
      </section>

      <!-- ============ 倒计时提示 ============ -->
      <section class="countdown-banner" :class="{ urgent: countdown < 120 }">
        <div class="countdown-icon">
          <MaterialIcon class="msr" name="hourglass_empty" />
        </div>
        <div class="countdown-content">
          <div class="countdown-main">
            <span class="countdown-label">请在</span>
            <span class="countdown-time">{{ Math.floor(countdown / 60) }}</span>
            <span class="countdown-unit">分钟</span>
            <span class="countdown-time">{{ countdown % 60 }}</span>
            <span class="countdown-unit">秒</span>
            <span class="countdown-label">内完成支付</span>
          </div>
          <p class="countdown-tip">超时订单会自动取消，如遇支付问题，请联系客服</p>
        </div>
      </section>

      <div class="layout">
        <!-- ============ LEFT MAIN ============ -->
        <main class="main-col">
          <!-- Order Card -->
          <article class="order-card" v-if="order">
            <div class="oc-head">
              <div class="oc-title-row">
                <MaterialIcon class="msr oc-ico" name="confirmation_number" />
                <h3 class="oc-title">订单信息</h3>
                <span class="oc-tag">待支付</span>
              </div>
              <span class="oc-no">订单号 · {{ order.orderNo || '提交后生成' }}</span>
            </div>

            <div class="oc-body">
              <div class="movie-poster">
                <img v-if="order.moviePoster" v-poster :src="order.moviePoster" :alt="order.movieTitle" />
                <MaterialIcon v-else class="msr poster-fallback" name="movie" />
                <div class="poster-rating">
                  <MaterialIcon class="msr" name="star" />
                  <span>9.2</span>
                </div>
              </div>

              <div class="movie-info">
                <div class="mi-tags">
                  <span class="mi-tag primary">IMAX</span>
                  <span class="mi-tag">3D</span>
                  <span class="mi-tag">杜比全景声</span>
                </div>
                <h2 class="movie-title">{{ order.movieTitle || '沙丘 第二部' }}</h2>
                <p class="movie-en">{{ (order.movieTitle || 'DUNE PART TWO').toUpperCase() }}</p>

                <div class="mi-grid">
                  <div class="mi-item">
                    <MaterialIcon class="msr mi-ico" name="store" />
                    <div>
                      <span class="mi-label">影院</span>
                      <span class="mi-val">{{ order.cinemaName || '响指电影 浦东陆家嘴店' }}</span>
                    </div>
                  </div>
                  <div class="mi-item">
                    <MaterialIcon class="msr mi-ico" name="meeting_room" />
                    <div>
                      <span class="mi-label">影厅</span>
                      <span class="mi-val">{{ order.hallName || 'IMAX 厅 · 1 号' }}</span>
                    </div>
                  </div>
                  <div class="mi-item">
                    <MaterialIcon class="msr mi-ico" name="schedule" />
                    <div>
                      <span class="mi-label">场次</span>
                      <span class="mi-val">{{ showtimeText }}</span>
                    </div>
                  </div>
                  <div class="mi-item">
                    <MaterialIcon class="msr mi-ico" name="chair" />
                    <div>
                      <span class="mi-label">座位 · {{ seatCount }} 张</span>
                      <span class="mi-val">{{ seatText }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </article>

          <PriceComparison v-if="order" :showtime-id="order.showtimeId" :initial-date="order.showtime?.slice(0, 10)" :initial-people="seatCount" fixed-people />

          <!-- ============ COUPONS ============ -->
          <article class="coupon-card">
            <div class="cc-head">
              <div class="cc-title-row">
                <MaterialIcon class="msr cc-ico" name="confirmation_number" />
                <h3 class="cc-title">优惠券与积分</h3>
                <span class="cc-tag">可自行调整</span>
              </div>
              <span class="cc-all" @click="router.push('/profile')">管理券包 →</span>
            </div>
            <div class="coupon-list">
              <label
                v-for="c in coupons"
                :key="c.id"
                class="coupon-item"
                :class="{ selected: c.selected, disabled: c.disabled }"
              >
                <input
                  type="checkbox"
                  :checked="c.selected"
                  :disabled="c.disabled"
                  @change="toggleCoupon(c)"
                />
                <div class="cp-left">
                  <span class="cp-amt-cur">¥</span>
                  <span class="cp-amt-num">{{ c.amount }}</span>
                </div>
                <div class="cp-mid">
                  <span class="cp-title">{{ c.title }}</span>
                  <span class="cp-sub">{{ c.sub }}</span>
                  <span class="cp-cond">{{ c.condition }}</span>
                </div>
                <div class="cp-right">
                  <span v-if="c.aiRecommended" class="cp-badge">
                    <MaterialIcon class="msr" name="auto_awesome" />AI 推荐
                  </span>
                  <span class="cp-tick"><MaterialIcon class="msr" name="check" /></span>
                </div>
              </label>
            </div>

            <div class="points-row">
              <div class="pt-left">
                <MaterialIcon class="msr pt-ico" name="stars" />
                <div>
                  <span class="pt-title">积分抵扣</span>
                <span class="pt-sub">可用 {{ pointsBalance.toLocaleString() }} 积分 · 100 积分抵 ¥1 · 每单最多抵 ¥1 · 消费 ¥1 得 0.5 积分</span>
                </div>
              </div>
              <div class="pt-right">
                <span class="pt-amt">-¥{{ pointsDeduct.toFixed(2) }}</span>
                <label class="switch">
                  <input type="checkbox" v-model="usePoints" :disabled="pointsBalance <= 0 || priceBeforePoints <= 0" />
                  <span class="slider"></span>
                </label>
              </div>
            </div>
          </article>
        </main>

        <!-- ============ RIGHT SIDE ============ -->
        <aside class="side-col">
          <div class="summary">
            <div class="sum-head">
              <MaterialIcon class="msr" name="request_quote" />
              <h3 class="sum-title">费用明细</h3>
            </div>
            <div class="sum-rows">
              <div class="sum-row">
                <span>票价小计</span>
                <span>¥{{ (basePrice * seatCount).toFixed(2) }}</span>
              </div>
              <div v-if="priceDiscount.ai" class="sum-row">
                <span>AI 智能优惠</span>
                <span class="text-pri">-¥{{ priceDiscount.ai }}.00</span>
              </div>
              <div v-if="priceDiscount.member" class="sum-row">
                <span>会员折扣</span>
                <span class="text-pri">-¥{{ priceDiscount.member }}.00</span>
              </div>
              <div v-if="priceDiscount.coupon" class="sum-row">
                <span>优惠券</span>
                <span class="text-pri">-¥{{ priceDiscount.coupon.toFixed(2) }}</span>
              </div>
              <div v-if="priceDiscount.time" class="sum-row">
                <span>时段优惠</span>
                <span class="text-pri">-¥{{ priceDiscount.time }}.00</span>
              </div>
              <div class="sum-row" v-if="usePoints">
                <span>积分抵扣</span>
                <span class="text-pri">-¥{{ pointsDeduct.toFixed(2) }}</span>
              </div>
              <div v-if="serviceFee" class="sum-row">
                <span>服务费</span>
                <span>¥{{ serviceFee }}.00</span>
              </div>
            </div>
            <div class="sum-divider"></div>
            <div class="sum-total">
              <div class="sum-total-left">
                <span class="st-label">应付总额</span>
                <span class="st-orig">原价 ¥{{ originalPrice.toFixed(2) }}</span>
              </div>
              <div class="sum-total-right">
                <span class="st-cur">¥</span>
                <span class="st-num">{{ Math.floor(payable) }}</span>
                <span class="st-dec">.{{ String((payable % 1).toFixed(2)).slice(2) }}</span>
              </div>
            </div>

            <button class="pay-btn" :disabled="paying" @click="showPaymentDialog">
              <span v-if="!paying" class="pay-btn-inner">
                <MaterialIcon class="msr" name="lock" />
                <span>立即支付 ¥{{ payable.toFixed(2) }}</span>
                <MaterialIcon class="msr" name="arrow_forward" />
              </span>
              <span v-else class="pay-btn-inner">
                <MaterialIcon class="msr spin" name="progress_activity" />
                <span>支付处理中</span>
              </span>
            </button>

            <div class="cancel-row">
              <span @click="handleCancelOrder">取消订单</span>
              <span class="dot"></span>

            </div>

            <div class="trust-row">
              <div class="tr-item">
                <MaterialIcon class="msr" name="shield" />
                <span>SSL 加密</span>
              </div>
              <div class="tr-item">
                <MaterialIcon class="msr" name="verified" />
                <span>银联认证</span>
              </div>
              <div class="tr-item">
                <MaterialIcon class="msr" name="support_agent" />
                <span>7×24 客服</span>
              </div>
            </div>
          </div>

          <div class="notice">
            <div class="notice-head">
              <MaterialIcon class="msr" name="info" />
              <span>温馨提示</span>
            </div>
            <ul class="notice-list">
              <li>订单生成后请在 <strong>10 分钟</strong>内完成支付,超时将自动取消。</li>
              <li>电子票将通过短信发送,请保持手机畅通。</li>
              <li>开场前 30 分钟以外可按退票规则申请退票，暂不支持改签。</li>
            </ul>
          </div>
        </aside>
      </div>

      <footer class="page-foot">
        <span>© 2026 响指电影 · 沪ICP 备 2024XXXXX 号</span>
        <div class="foot-links">



        </div>
      </footer>
    </div>

    <!-- ============ 支付方式选择对话框 ============ -->
    <el-dialog
      v-model="showPayMethodDialog"
      title="选择支付方式"
      class="consumer-dialog"
      align-center
      width="600px"
      :close-on-click-modal="false"
    >
      <div class="payment-dialog">
        <div class="payment-amount">
          <span class="label">支付金额</span>
          <div class="amount">
            <span class="currency">¥</span>
            <span class="value">{{ payable.toFixed(2) }}</span>
          </div>
        </div>

        <div class="payment-methods">
          <div
            v-for="m in payMethods"
            :key="m.key"
            class="method-item"
            :class="{ active: payMethod === m.key }"
            @click="payMethod = m.key"
          >
            <div class="method-icon" :style="{ '--mc': m.color }">
              <MaterialIcon class="msr" :name="m.icon" />
            </div>
            <div class="method-info">
              <span class="method-name">{{ m.name }}</span>
              <span class="method-desc">{{ m.desc }}</span>
            </div>
            <span v-if="m.discount" class="method-discount">{{ m.discount }}</span>
            <span class="method-radio">
              <MaterialIcon class="msr" :name="payMethod === m.key ? 'radio_button_checked' : 'radio_button_unchecked'" />
            </span>
          </div>
        </div>

        <div class="payment-security">
          <MaterialIcon class="msr" name="shield" />
          <span>支付信息经过 SSL 加密保护</span>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showPayMethodDialog = false">取消</el-button>
          <el-button type="primary" @click="handlePay" :loading="paying">
            确认支付
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderApi } from '@/api/order'
import { showtimeApi } from '@/api/showtime'
import { couponApi } from '@/api/coupon'
import { userApi } from '@/api/user'
import { pricingApi } from '@/api/pricing'
import PriceComparison from '@/components/PriceComparison.vue'
import type { Coupon, Order } from '@/types'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const paying = ref(false)
const order = ref<Order | null>(null)
const pendingShowtimeId = ref<number | null>(null)
const pendingSeats = ref<string[]>([])

const usePoints = ref(route.query.pricingPoints === 'true')
const pointsBalance = ref(0)
const payMethod = ref<'wechat' | 'alipay' | 'union' | 'card' | 'wallet'>('wechat')
const showPayMethodDialog = ref(false)

const countdown = ref(15 * 60)
let countdownTimer: number | null = null

const countdownText = computed(() => {
  const m = Math.floor(countdown.value / 60)
  const s = countdown.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

const seatCount = computed(() => {
  if (!order.value) return 2
  return order.value.seatCount || order.value.seats?.split(',').length || 2
})

const seatText = computed(() => {
  if (!order.value) return '4 排 7 座 · 4 排 8 座'
  return order.value.seats?.split(',').slice(0, 3).join(' · ') || '4 排 7 座 · 4 排 8 座'
})

const showtimeText = computed(() => {
  if (!order.value?.showtime) return '今日 19:30 · 周三'
  const d = new Date(order.value.showtime)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
})

const basePrice = computed(() => {
  if (!order.value) return 90
  return originalPrice.value / seatCount.value
})

const originalPrice = computed(() => {
  if (!order.value) return 180
  return Number(order.value.originalPrice ?? order.value.totalPrice)
})

const selectedCoupon = computed(() => coupons.value.find(coupon => coupon.selected && !coupon.disabled))

const priceDiscount = computed(() => ({
  ai: 0,
  member: 0,
  coupon: selectedCoupon.value ? Math.min(selectedCoupon.value.amount, originalPrice.value) : 0,
  time: 0
}))

const serviceFee = computed(() => 0)

const priceBeforePoints = computed(() => {
  const d = priceDiscount.value
  return Math.max(originalPrice.value - d.ai - d.member - d.coupon - d.time + serviceFee.value, 0)
})

// 100 积分抵 1 元，每单最多抵 1 元且不超过应付金额；余额支持 0.5 积分。
const pointsToUse = computed(() => {
  if (!usePoints.value || pointsBalance.value <= 0) return 0
  const points = Math.floor(Math.min(pointsBalance.value, priceBeforePoints.value * 100, 100) * 10 + 1e-8) / 10
  return Math.round(points) > 0 ? points : 0
})
const pointsDeduct = computed(() => Math.round(pointsToUse.value) / 100)

const finalPrice = computed(() => {
  const d = priceDiscount.value
  return Math.max(
    originalPrice.value - d.ai - d.member - d.coupon - d.time - pointsDeduct.value + serviceFee.value,
    0
  )
})

const payable = computed(() => finalPrice.value)

const savedAmount = computed(() => {
  const d = priceDiscount.value
  return d.ai + d.member + d.coupon + d.time + pointsDeduct.value
})

const savedPercent = computed(() => {
  if (!originalPrice.value) return 0
  return ((savedAmount.value / originalPrice.value) * 100).toFixed(1)
})

type PayCoupon = Coupon & {
  title: string
  sub: string
  condition: string
  selected: boolean
  disabled: boolean
  aiRecommended: boolean
}

const coupons = ref<PayCoupon[]>([])

const loadCoupons = async () => {
  try {
    const data = await couponApi.getMine()
    const now = Date.now()
    const available = data.filter(coupon => coupon.available && new Date(coupon.validFrom).getTime() <= now && new Date(coupon.expireTime).getTime() >= now)
    const bestId = available
      .filter(coupon => Number(coupon.minSpend) <= originalPrice.value)
      .sort((a, b) => Number(b.amount) - Number(a.amount))[0]?.id
    coupons.value = available.map(coupon => ({
      ...coupon,
      amount: Number(coupon.amount),
      minSpend: Number(coupon.minSpend),
      title: coupon.name,
      sub: '响指电影 新人专享',
      condition: Number(coupon.minSpend) > 0 ? `满 ¥${coupon.minSpend} 可用` : '无门槛使用',
      selected: route.query.pricingCoupons !== 'false' && coupon.id === (route.query.pricingCouponId ? Number(route.query.pricingCouponId) : bestId) && Number(coupon.minSpend) <= originalPrice.value,
      disabled: Number(coupon.minSpend) > originalPrice.value,
      aiRecommended: coupon.id === bestId
    }))
  } catch (error) {
    console.error('加载优惠券失败:', error)
    coupons.value = []
  }
}

const loadPoints = async () => {
  try {
    const points = await userApi.getPoints()
    pointsBalance.value = Math.max(Number(points || 0), 0)
  } catch (error) {
    console.error('加载积分余额失败:', error)
    pointsBalance.value = 0
  }
}

const toggleCoupon = (c: PayCoupon) => {
  coupons.value.forEach((x) => {
    if (x.id === c.id) x.selected = !x.selected
    else x.selected = false
  })
}

const quoteBusy = ref(false)
const applyBestOffer = async () => {
  if (!order.value?.orderNo || quoteBusy.value) return
  quoteBusy.value = true
  const requestedOrder = order.value.orderNo
  const requestedPoints = usePoints.value
  const previousCoupon = selectedCoupon.value?.id
  try {
    const quote = await pricingApi.quote(order.value.orderNo, usePoints.value)
    if (order.value?.orderNo !== requestedOrder || usePoints.value !== requestedPoints || selectedCoupon.value?.id !== previousCoupon) {
      throw new Error('优惠选择已变化，请重新试算')
    }
    const plan = quote.bestPlan
    if (plan.couponId && !coupons.value.some(c => c.id === plan.couponId)) {
      await loadCoupons()
    }
    if (plan.couponId && !coupons.value.some(c => c.id === plan.couponId && !c.disabled)) throw new Error('券包状态已变化，请刷新后重试')
    coupons.value.forEach(c => { c.selected = c.id === plan.couponId; c.aiRecommended = c.selected })
    pointsBalance.value = Number(quote.availablePoints)
    usePoints.value = Number(plan.pointsUsed) > 0
  } catch (error: any) { ElMessage.warning(error.response?.data?.message || error.message || '优惠试算失败，请重试') }
  finally { quoteBusy.value = false }
}

const payMethods = [
  { key: 'wechat' as const, name: '微信支付', desc: '使用微信钱包安全支付', icon: 'chat', color: '#1AAD19', discount: '随机立减' },
  { key: 'alipay' as const, name: '支付宝', desc: '推荐花呗分期 · 6 期免息', icon: 'account_balance_wallet', color: '#1677FF', discount: '6 期免息' },
  { key: 'union' as const, name: '云闪付', desc: '银联卡用户专享 95 折', icon: 'credit_card', color: '#E60012', discount: '95 折' },
  { key: 'card' as const, name: '银行卡', desc: '支持主流银行借记卡 / 信用卡', icon: 'payments', color: '#111111', discount: '' },
  { key: 'wallet' as const, name: '响指电影 钱包', desc: '余额 ¥328.50 · 实时到账', icon: 'savings', color: '#FF8400', discount: '5% 返现' }
]

const startCountdown = () => {
  countdownTimer = window.setInterval(() => {
    if (countdown.value > 0) {
      countdown.value--
    } else if (countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
      ElMessage.warning('订单已超时,自动取消')
      router.push('/orders')
    }
  }, 1000)
}

const loadOrder = async () => {
  loading.value = true
  try {
    const orderNo = route.params.orderNo as string
    if (orderNo) {
      order.value = await orderApi.getByOrderNo(orderNo)
      if (order.value.status !== 0) {
        showPayMethodDialog.value = false
        ElMessage.info(`订单${order.value.statusText || '状态已变化'}，请查看订单详情`)
        await router.replace(`/order/${orderNo}`)
        return
      }
      await Promise.all([loadCoupons(), loadPoints()])
      if (route.query.pricingCoupons === 'true') await applyBestOffer()

      // 根据订单创建时间计算真实的剩余时间
      if (order.value.createTime) {
        const createTime = new Date(order.value.createTime).getTime()
        const now = Date.now()
        const elapsed = Math.floor((now - createTime) / 1000) // 已过秒数
        const remaining = 10 * 60 - elapsed // 剩余秒数

        if (remaining <= 0) {
          ElMessage.warning('订单已超时')
          router.push('/orders')
          return
        }

        countdown.value = remaining
      }
      return
    }

    const showtimeId = Number(route.query.showtimeId)
    const seatsStr = route.query.seats as string
    if (!showtimeId || !seatsStr) {
      ElMessage.error('参数错误')
      router.back()
      return
    }

    const seatList = seatsStr.split(',')
    const showtime = await showtimeApi.getById(showtimeId)
    pendingShowtimeId.value = showtimeId
    pendingSeats.value = seatList

    order.value = {
      id: 0,
      orderNo: '',
      userId: 0,
      showtimeId,
      movieTitle: showtime.movieTitle,
      moviePoster: showtime.moviePoster,
      cinemaName: showtime.cinemaName,
      hallName: showtime.hallName,
      showtime: showtime.startTime,
      seats: seatList.join(','),
      seatCount: seatList.length,
      totalPrice: Number((showtime.basePrice * seatList.length).toFixed(2)),
      status: 0,
      statusText: '待支付'
    }
    await Promise.all([loadCoupons(), loadPoints()])
  } catch (error) {
    console.error('加载订单失败:', error)
  } finally {
    loading.value = false
  }
}

const showPaymentDialog = () => {
  if (paying.value || loading.value) return
  if (order.value && (order.value.status !== 0 || countdown.value <= 0)) {
    ElMessage.warning('订单状态已变化或已超时，请查看订单详情')
    if (order.value.orderNo) void router.replace(`/order/${order.value.orderNo}`)
    return
  }
  if (!order.value) {
    ElMessage.warning('订单未就绪')
    return
  }

  if (!order.value.orderNo) {
    ElMessage.error('订单号缺失')
    return
  }

  showPayMethodDialog.value = true
}

const handlePay = async () => {
  if (!order.value?.orderNo || paying.value || loading.value) return
  if (order.value.status !== 0 || countdown.value <= 0) {
    showPayMethodDialog.value = false
    ElMessage.warning('订单状态已变化或已超时，请查看订单详情')
    await router.replace(`/order/${order.value.orderNo}`)
    return
  }

  showPayMethodDialog.value = false

  try {
    paying.value = true
    await orderApi.pay(order.value.orderNo, selectedCoupon.value?.id, pointsToUse.value)
    ElMessage.success('支付成功')
    router.push(`/order/${order.value.orderNo}`)
  } catch (error: any) {
    if (error.response?.status === 409) {
      // 全局请求拦截器显示服务端原因；同步状态和优惠，避免重复提交旧订单。
      await loadOrder()
    } else {
      console.error('支付失败:', error)
    }
  } finally {
    paying.value = false
  }
}

const handleCancelOrder = async () => {
  if (!order.value?.orderNo) {
    ElMessage.info('订单不存在')
    router.back()
    return
  }

  try {
    await ElMessageBox.confirm('确认取消订单吗？取消后无法恢复。', '取消订单', {
      confirmButtonText: '确认取消',
      cancelButtonText: '继续支付',
      customClass: 'order-cancel-dialog',
      closeOnClickModal: false
    })

    await orderApi.cancel(order.value.orderNo)
    ElMessage.success('订单已取消')
    router.push('/orders')
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('取消订单失败:', e)
    }
  }
}


onMounted(() => {
  loadOrder()
  startCountdown()
})

onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style scoped>

.pay-app {
  --primary: #FF8400;
  --primary-foreground: #111111;
  --bg: #F2F3F0;
  --card: #FFFFFF;
  --fg: #111111;
  --muted: #666666;
  --border: #CBCCC9;
  --secondary: #E7E8E5;
  --ai-1: #A855F7;
  --ai-2: #EC4899;
  --ai-3: #6D28D9;
  --radius-m: 16px;
  --radius-l: 20px;
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  --font-sans: 'Geist', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;

  min-height: 100vh;
  background: var(--bg);
  font-family: var(--font-sans);
  color: var(--fg);
}

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
  font-size: 18px;
  user-select: none;
}

/* ============ TOP BAR ============ */
.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 14px 32px;
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 50;
  backdrop-filter: blur(8px);
}
.tb-left { display: flex; align-items: center; gap: 20px; }
.tb-back {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.15s, transform 0.15s;
}
.tb-back:hover { border-color: var(--primary); transform: translateX(-2px); }
.tb-back .msr { font-size: 20px; }

.brand-block { display: flex; align-items: center; gap: 10px; }
.brand-mark {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--primary);
  color: var(--primary-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand-mark .msr { font-size: 18px; }
.brand-text { display: flex; flex-direction: column; gap: 1px; }
.brand {
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 1.5px;
}
.brand-sub { font-size: 10px; color: var(--muted); }

.crumbs { display: flex; align-items: center; gap: 6px; }
.crumb {
  font-size: 13px;
  color: var(--muted);
  cursor: pointer;
  transition: color 0.15s;
}
.crumb:hover { color: var(--fg); }
.crumb.active {
  color: var(--fg);
  font-weight: 600;
  cursor: default;
}
.crumb-sep { font-size: 14px !important; color: var(--muted); }

.tb-right { display: flex; align-items: center; gap: 20px; }
.tb-step { display: flex; align-items: center; gap: 6px; }
.step {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: var(--secondary);
  border: 1px solid var(--border);
  color: var(--muted);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
}
.step .msr { font-size: 14px; }
.step.done {
  background: var(--fg);
  border-color: var(--fg);
  color: #fff;
}
.step.active {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
  box-shadow: 0 0 0 4px rgba(255, 132, 0, 0.15);
}
.step-line {
  width: 24px;
  height: 2px;
  background: var(--border);
  border-radius: 999px;
}
.step-line.done { background: var(--fg); }
.step-line.active { background: var(--primary); }
.step-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
  margin: 0 2px;
}
.step-label.done { color: var(--fg); }
.step-label.active { color: var(--fg); font-weight: 700; }

.tb-timer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 999px;
}
.tb-timer .msr { font-size: 16px; color: var(--primary); }
.timer-label { font-size: 12px; color: var(--muted); }
.timer-time {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 700;
  color: var(--fg);
  letter-spacing: 0.5px;
}
.tb-timer.urgent { border-color: #DC2626; background: rgba(220, 38, 38, 0.06); }
.tb-timer.urgent .msr,
.tb-timer.urgent .timer-time { color: #DC2626; }

/* ============ PAGE BODY ============ */
.page-body {
  max-width: 1320px;
  margin: 0 auto;
  padding: 32px 32px 48px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Page Head */
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 32px;
  padding: 8px 4px 4px;
}
.ph-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--muted);
  width: fit-content;
}
.dot-primary {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--primary);
  box-shadow: 0 0 8px var(--primary);
}
.ph-title {
  margin: 12px 0 8px;
  font-size: 36px;
  font-weight: 700;
  letter-spacing: -0.5px;
}
.ph-sub {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--muted);
  max-width: 640px;
}
.ph-right {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 18px 24px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
}
.ph-stat { display: flex; flex-direction: column; gap: 2px; align-items: flex-end; }
.ph-stat-num {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 700;
  color: var(--ai-3);
  letter-spacing: -0.5px;
}
.ph-stat-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
  letter-spacing: 1px;
}
.ph-divider {
  width: 1px;
  height: 36px;
  background: var(--border);
}

/* ============ 倒计时提示 ============ */
.countdown-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 24px 32px;
  background: linear-gradient(135deg, #FFF9E6 0%, #FFE8CC 100%);
  border: 2px solid #FFB84D;
  border-radius: 16px;
  margin-bottom: 24px;
  transition: all 0.3s;
}

.countdown-banner.urgent {
  background: linear-gradient(135deg, #FFE5E5 0%, #FFD6D6 100%);
  border-color: #FF4D4D;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.02); }
}

.countdown-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #FF8400 0%, #FF6B00 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.3);
}

.countdown-banner.urgent .countdown-icon {
  background: linear-gradient(135deg, #FF4D4D 0%, #DC2626 100%);
  animation: shake 0.5s ease-in-out infinite;
}

@keyframes shake {
  0%, 100% { transform: rotate(0deg); }
  25% { transform: rotate(-5deg); }
  75% { transform: rotate(5deg); }
}

.countdown-icon .msr {
  font-size: 32px;
  color: #fff;
}

.countdown-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.countdown-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.countdown-label {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.countdown-time {
  font-family: var(--font-mono);
  font-size: 48px;
  font-weight: 800;
  color: #FF8400;
  line-height: 1;
  letter-spacing: -1px;
}

.countdown-banner.urgent .countdown-time {
  color: #DC2626;
}

.countdown-unit {
  font-size: 20px;
  font-weight: 600;
  color: #666;
  margin-right: 4px;
}

.countdown-tip {
  margin: 0;
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.countdown-banner.urgent .countdown-tip {
  color: #DC2626;
  font-weight: 600;
}

/* ============ LAYOUT ============ */
.layout {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 24px;
}
.main-col { display: flex; flex-direction: column; gap: 24px; min-width: 0; }
.side-col { display: flex; flex-direction: column; gap: 16px; }

/* ============ ORDER CARD ============ */
.order-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-l);
  padding: 24px;
}
.oc-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--border);
}
.oc-title-row { display: flex; align-items: center; gap: 10px; }
.oc-ico { font-size: 20px !important; color: var(--primary); }
.oc-title { margin: 0; font-size: 17px; font-weight: 700; letter-spacing: -0.3px; }
.oc-tag {
  padding: 3px 10px;
  background: rgba(255, 132, 0, 0.1);
  border: 1px solid rgba(255, 132, 0, 0.3);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--primary);
}
.oc-no {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
  letter-spacing: 0.5px;
}

.oc-body {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 24px;
  padding-top: 24px;
}
.movie-poster {
  position: relative;
  aspect-ratio: 2 / 3;
  border-radius: 12px;
  overflow: hidden;
  background: linear-gradient(200deg, #C97B4A 0%, #7A3A20 50%, #1F1815 100%);
  border: 1px solid var(--border);
}
.movie-poster img { width: 100%; height: 100%; object-fit: cover; }
.poster-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56px !important;
  color: rgba(255, 255, 255, 0.3);
}
.poster-rating {
  position: absolute;
  top: 10px;
  left: 10px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
  border-radius: 999px;
  color: #fff;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
}
.poster-rating .msr { font-size: 14px; color: var(--primary); }

.movie-info { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.mi-tags { display: flex; gap: 6px; }
.mi-tag {
  padding: 3px 10px;
  background: var(--secondary);
  border: 1px solid var(--border);
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--muted);
}
.mi-tag.primary {
  background: rgba(255, 132, 0, 0.1);
  border-color: rgba(255, 132, 0, 0.3);
  color: var(--primary);
}
.movie-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.5px;
  line-height: 1.1;
}
.movie-en {
  margin: -4px 0 4px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 3px;
  color: var(--muted);
}

.mi-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 24px;
  padding-top: 8px;
  border-top: 1px dashed var(--border);
}
.mi-item { display: flex; align-items: flex-start; gap: 10px; min-width: 0; }
.mi-ico {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px !important;
  color: var(--muted);
  flex-shrink: 0;
}
.mi-item > div { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.mi-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
  letter-spacing: 0.5px;
}
.mi-val {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ============ AI PRICING ============ */
/* ============ COUPON CARD ============ */
.coupon-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-l);
  padding: 24px;
}
.cc-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--border);
}
.cc-title-row { display: flex; align-items: center; gap: 10px; }
.cc-ico { font-size: 20px !important; color: var(--primary); }
.cc-title { margin: 0; font-size: 17px; font-weight: 700; letter-spacing: -0.3px; }
.cc-tag {
  padding: 3px 10px;
  background: rgba(34, 197, 94, 0.1);
  border: 1px solid rgba(34, 197, 94, 0.25);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  color: #16a34a;
}
.cc-all {
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  cursor: pointer;
  transition: color 0.15s;
}
.cc-all:hover { color: var(--primary); }

.coupon-list { display: flex; flex-direction: column; gap: 10px; padding: 18px 0; }
.coupon-item {
  display: grid;
  grid-template-columns: auto 96px 1fr auto;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.coupon-item:hover:not(.disabled) { border-color: var(--primary); }
.coupon-item.selected {
  background: rgba(255, 132, 0, 0.06);
  border-color: var(--primary);
}
.coupon-item.disabled { opacity: 0.55; cursor: not-allowed; }
.coupon-item input[type='checkbox'] {
  appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 999px;
  border: 1.5px solid var(--border);
  background: var(--card);
  cursor: pointer;
  position: relative;
  flex-shrink: 0;
}
.coupon-item.selected input[type='checkbox'] {
  background: var(--primary);
  border-color: var(--primary);
}
.coupon-item.selected input[type='checkbox']::after {
  content: '';
  position: absolute;
  inset: 4px;
  border-radius: 999px;
  background: #fff;
}

.cp-left {
  display: flex;
  align-items: baseline;
  gap: 2px;
  justify-content: center;
  padding: 8px 0;
  background: linear-gradient(135deg, #FF8400 0%, #FF3F33 100%);
  color: #fff;
  border-radius: 8px;
}
.coupon-item.disabled .cp-left {
  background: linear-gradient(135deg, #999 0%, #777 100%);
}
.cp-amt-cur { font-family: var(--font-mono); font-size: 14px; font-weight: 700; }
.cp-amt-num { font-family: var(--font-mono); font-size: 32px; font-weight: 800; letter-spacing: -1px; }

.cp-mid { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.cp-title { font-size: 14px; font-weight: 700; color: var(--fg); }
.cp-sub { font-size: 12px; color: var(--muted); font-weight: 500; }
.cp-cond { font-size: 11px; color: var(--muted); font-family: var(--font-mono); }

.cp-right { display: flex; align-items: center; gap: 10px; }
.cp-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 4px 8px;
  border-radius: 6px;
  background: linear-gradient(135deg, var(--ai-1) 0%, var(--ai-2) 100%);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.cp-badge .msr { font-size: 11px; }
.cp-tick {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: var(--secondary);
  border: 1px solid var(--border);
  color: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.coupon-item.selected .cp-tick {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
}
.cp-tick .msr { font-size: 14px; }

.points-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 12px;
}
.pt-left { display: flex; align-items: center; gap: 12px; }
.pt-ico {
  font-size: 20px !important;
  color: var(--primary);
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: rgba(255, 132, 0, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
}
.pt-left > div { display: flex; flex-direction: column; gap: 2px; }
.pt-title { font-size: 13px; font-weight: 700; color: var(--fg); }
.pt-sub { font-size: 11px; color: var(--muted); font-family: var(--font-mono); }
.pt-right { display: flex; align-items: center; gap: 14px; }
.pt-amt {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 700;
  color: var(--primary);
}

/* Switch */
.switch {
  position: relative;
  display: inline-block;
  width: 40px;
  height: 22px;
}
.switch input { opacity: 0; width: 0; height: 0; }
.slider {
  position: absolute;
  inset: 0;
  background: var(--border);
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.2s;
}
.slider::before {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  background: #fff;
  border-radius: 999px;
  transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}
.switch input:checked + .slider { background: var(--primary); }
.switch input:checked + .slider::before { transform: translateX(18px); }

/* ============ PAY METHOD ============ */
.pay-method {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-l);
  padding: 24px;
}
.pm-head { padding-bottom: 18px; border-bottom: 1px solid var(--border); }
.pm-title-row { display: flex; align-items: center; gap: 10px; }
.pm-ico { font-size: 20px !important; color: var(--primary); }
.pm-title { margin: 0; font-size: 17px; font-weight: 700; letter-spacing: -0.3px; }
.pm-tag {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  background: var(--secondary);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  color: var(--muted);
}
.pm-tag .msr { font-size: 12px; color: #16a34a; }

.pm-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding-top: 18px;
}
.pm-item {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 12px;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: all 0.15s;
}
.pm-item:hover { border-color: var(--primary); }
.pm-item.active {
  background: rgba(255, 132, 0, 0.06);
  border-color: var(--primary);
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.12);
}
.pm-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--mc) 12%, white);
  color: var(--mc);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.pm-icon-wrap .msr { font-size: 20px; }
.pm-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.pm-name { font-size: 13px; font-weight: 700; color: var(--fg); }
.pm-desc {
  font-size: 11px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pm-discount {
  padding: 3px 8px;
  background: rgba(236, 72, 153, 0.1);
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  color: #be185d;
  white-space: nowrap;
}
.pm-radio {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  border: 1.5px solid var(--border);
  background: var(--card);
  color: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pm-item.active .pm-radio {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
}
.pm-radio .msr { font-size: 14px; font-weight: 700; }

/* ============ SUMMARY ============ */
.summary {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-l);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.sum-head { display: flex; align-items: center; gap: 8px; }
.sum-head .msr { font-size: 20px; color: var(--primary); }
.sum-title { margin: 0; font-size: 16px; font-weight: 700; }

.sum-rows { display: flex; flex-direction: column; gap: 10px; }
.sum-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--muted);
}
.sum-row > span:last-child {
  font-family: var(--font-mono);
  font-weight: 600;
  color: var(--fg);
}
.text-pri { color: var(--ai-2) !important; }

.sum-divider {
  height: 1px;
  background: var(--border);
}

.sum-total {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}
.sum-total-left { display: flex; flex-direction: column; gap: 4px; }
.st-label { font-size: 13px; font-weight: 600; color: var(--fg); }
.st-orig {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--muted);
  text-decoration: line-through;
}
.sum-total-right {
  display: flex;
  align-items: baseline;
}
.st-cur {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 700;
  color: var(--primary);
}
.st-num {
  font-family: var(--font-mono);
  font-size: 40px;
  font-weight: 800;
  letter-spacing: -1.5px;
  color: var(--primary);
  line-height: 1;
}
.st-dec {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 700;
  color: var(--primary);
}

.pay-btn {
  height: 56px;
  border: none;
  border-radius: 16px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-family: var(--font-sans);
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 12px 28px rgba(255, 132, 0, 0.4);
  transition: transform 0.15s, box-shadow 0.2s, opacity 0.15s;
}
.pay-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 16px 32px rgba(255, 132, 0, 0.5);
}
.pay-btn:active:not(:disabled) { transform: translateY(0); }
.pay-btn:disabled { opacity: 0.7; cursor: not-allowed; }
.pay-btn-inner { display: flex; align-items: center; justify-content: center; gap: 10px; }
.pay-btn-inner .msr { font-size: 18px; }
.msr.spin { animation: spin 1s linear infinite; }
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.cancel-row {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--muted);
}
.cancel-row span:not(.dot) {
  cursor: pointer;
  transition: color 0.15s;
}
.cancel-row span:not(.dot):hover { color: var(--primary); }
.cancel-row .dot {
  width: 3px;
  height: 3px;
  border-radius: 999px;
  background: var(--border);
}

.trust-row {
  display: flex;
  justify-content: space-around;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}
.tr-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--muted);
  font-weight: 500;
}
.tr-item .msr { font-size: 14px; color: #16a34a; }

/* Notice */
.notice {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.notice-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: var(--fg);
}
.notice-head .msr { font-size: 16px; color: var(--primary); }
.notice-list {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.notice-list li {
  font-size: 12px;
  line-height: 1.6;
  color: var(--muted);
}
.notice-list strong { color: var(--primary); font-weight: 700; }

/* Page Foot */
.page-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 4px 0;
  border-top: 1px solid var(--border);
  font-size: 11px;
  color: var(--muted);
}
.foot-links { display: flex; gap: 16px; }
.foot-links span {
  cursor: pointer;
  transition: color 0.15s;
}
.foot-links span:hover { color: var(--fg); }

/* ============ 支付方式对话框 ============ */
.payment-dialog {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.payment-amount {
  text-align: center;
  padding: 24px;
  background: linear-gradient(135deg, #FFF9E6 0%, #FFE8CC 100%);
  border-radius: 12px;
  border: 2px solid #FFB84D;
}

.payment-amount .label {
  display: block;
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.payment-amount .amount {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 4px;
}

.payment-amount .currency {
  font-family: var(--font-mono);
  font-size: 24px;
  font-weight: 700;
  color: #FF8400;
}

.payment-amount .value {
  font-family: var(--font-mono);
  font-size: 48px;
  font-weight: 800;
  color: #FF8400;
  line-height: 1;
}

.payment-methods {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.method-item {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: var(--bg);
  border: 2px solid var(--border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.method-item:hover {
  border-color: var(--primary);
  transform: translateX(4px);
}

.method-item.active {
  background: rgba(255, 132, 0, 0.08);
  border-color: var(--primary);
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.15);
}

.method-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--mc) 12%, white);
  color: var(--mc);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.method-icon .msr {
  font-size: 24px;
}

.method-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.method-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--fg);
}

.method-desc {
  font-size: 13px;
  color: var(--muted);
}

.method-discount {
  padding: 4px 10px;
  background: rgba(236, 72, 153, 0.1);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #be185d;
  white-space: nowrap;
}

.method-radio {
  color: var(--border);
  transition: color 0.2s;
}

.method-item.active .method-radio {
  color: var(--primary);
}

.method-radio .msr {
  font-size: 24px;
}

.payment-security {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  background: rgba(34, 197, 94, 0.08);
  border-radius: 8px;
  font-size: 13px;
  color: #16a34a;
  font-weight: 500;
}

.payment-security .msr {
  font-size: 18px;
  color: #16a34a;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* ============ RESPONSIVE ============ */
@media (max-width: 1180px) {
  .layout { grid-template-columns: 1fr; }
  .alt-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 900px) {
  .page-head { flex-direction: column; align-items: flex-start; }
  .ph-right { width: 100%; justify-content: space-around; }
  .alt-grid { grid-template-columns: 1fr; }
  .mi-grid { grid-template-columns: 1fr; }
  .pm-grid { grid-template-columns: 1fr; }
  .oc-body { grid-template-columns: 1fr; }
  .movie-poster { max-width: 200px; }
  .top-bar { flex-direction: column; align-items: flex-start; gap: 12px; padding: 12px 16px; }
  .tb-step { display: none; }
  .page-body { padding: 20px 16px 32px; }
}
@media (max-width: 520px) {
  .ph-title { font-size: 24px; }
  .order-card,
  .coupon-card,
  .pay-method,
  .summary,
  .notice { padding: 18px; border-radius: 14px; }
  .coupon-item { grid-template-columns: auto 80px 1fr; gap: 10px; }
  .coupon-item .cp-right { grid-column: 1 / -1; justify-content: flex-end; }
  .crumbs { display: none; }
}
</style>

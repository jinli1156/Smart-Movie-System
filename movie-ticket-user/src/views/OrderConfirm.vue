<template>
  <div class="confirm-app">
    <header class="top-bar">
      <div class="tb-left">
        <button class="tb-back" aria-label="返回选座" @click="goBack"><MaterialIcon class="msr" name="arrow_back" /></button>
        <div class="brand-block" @click="router.push('/home')">
          <div class="brand-mark"><img src="/xiangzhi-ticket.png" alt="响指电影" /></div>
          <div class="brand-text"><span class="brand brand-wordmark" role="img" aria-label="响指电影"></span><span class="brand-sub">premium booking</span></div>
        </div>
        <div class="crumbs">
          <span class="crumb" @click="router.push('/home')">首页</span><MaterialIcon class="msr crumb-sep" name="chevron_right" />
          <span class="crumb" @click="goBack">选座</span><MaterialIcon class="msr crumb-sep" name="chevron_right" />
          <span class="crumb active">确认订单</span>
        </div>
      </div>
      <div class="tb-step" aria-label="购票进度">
        <span class="step done"><MaterialIcon class="msr" name="check" /></span><span class="step-label done">选座</span><span class="step-line done"></span>
        <span class="step active">2</span><span class="step-label active">确认</span><span class="step-line"></span>
        <span class="step">3</span><span class="step-label">支付</span><span class="step-line"></span>
        <span class="step">4</span><span class="step-label">取票</span>
      </div>
    </header>

    <main class="page-body">
      <section class="page-head">
        <div>
          <div class="eyebrow"><span></span> CHECKOUT · STEP 02</div>
          <h1>确认订单信息</h1>
          <p>请核对场次和座位，提交后将为你锁座 10 分钟。</p>
        </div>
        <div v-if="showtime" class="head-badge"><MaterialIcon class="msr" name="verified" /><div><strong>信息已核验</strong><span>票价与座位实时同步</span></div></div>
      </section>

      <section v-if="loading" class="state-card loading-state"><span class="spinner"></span><strong>正在加载订单信息</strong><p>马上就好，请稍候…</p></section>
      <section v-else-if="loadError || !showtime" class="state-card error-state">
        <span class="state-icon"><MaterialIcon class="msr" name="error" /></span><h2>订单信息加载失败</h2>
        <p>{{ loadError || '没有找到对应场次，请返回重新选座。' }}</p>
        <div class="state-actions"><button class="secondary-btn" @click="goBack">返回选座</button><button class="primary-btn compact" @click="loadData">重新加载</button></div>
      </section>

      <template v-else>
        <div v-if="submitError" class="error-banner" role="alert">
          <MaterialIcon class="msr" name="error" /><div><strong>订单提交失败</strong><span>{{ submitError }}</span></div>
          <button aria-label="关闭错误提示" @click="submitError = ''"><MaterialIcon class="msr" name="close" /></button>
        </div>

        <div class="layout">
          <div class="main-col">
            <article class="card movie-card">
              <div class="card-head"><div><MaterialIcon class="msr" name="confirmation_number" /><h2>观影信息</h2></div><span class="live-tag"><i></i> 实时场次</span></div>
              <div class="movie-body">
                <div class="poster" :style="{ background: posterGradient }"><img v-if="showtime.moviePoster" v-poster :src="showtime.moviePoster" :alt="showtime.movieTitle" /><MaterialIcon v-else class="msr" name="movie" /></div>
                <div class="movie-info">
                  <div class="movie-tags"><span>2D</span><span>{{ hallBadge }}</span><span>中英字幕</span></div><h3>{{ showtime.movieTitle || '影片' }}</h3>
                  <div class="info-grid">
                    <div class="info-item full"><MaterialIcon class="msr" name="store" /><div><span>影院</span><strong>{{ showtime.cinemaName || '影院信息待确认' }}</strong></div></div>
                    <div class="info-item"><MaterialIcon class="msr" name="calendar_month" /><div><span>日期</span><strong>{{ formatDate(showtime.startTime) }}</strong></div></div>
                    <div class="info-item"><MaterialIcon class="msr" name="schedule" /><div><span>时间</span><strong>{{ formatTime(showtime.startTime) }}</strong></div></div>
                    <div class="info-item"><MaterialIcon class="msr" name="meeting_room" /><div><span>影厅</span><strong>{{ showtime.hallName || '影厅待确认' }}</strong></div></div>
                    <div class="info-item"><MaterialIcon class="msr" name="payments" /><div><span>单价</span><strong>¥{{ money(showtime.basePrice) }}</strong></div></div>
                  </div>
                </div>
              </div>
            </article>

            <article class="card seat-card">
              <div class="card-head"><div><MaterialIcon class="msr" name="event_seat" /><h2>已选座位</h2></div><span class="seat-count">{{ seats.length }} 张</span></div>
              <div class="seat-list"><span v-for="seat in seats" :key="seat"><MaterialIcon class="msr" name="chair" />{{ seat }}</span></div>
              <button class="change-seat" @click="goBack"><MaterialIcon class="msr" name="edit" />重新选座</button>
            </article>

            <PriceComparison class="confirm-price-comparison" :showtime-id="showtime.id" :initial-date="showtime.startTime.slice(0, 10)" :initial-people="seats.length" fixed-people />
          </div>

          <aside class="summary-col">
            <article class="summary-card">
              <div class="summary-head"><MaterialIcon class="msr" name="receipt_long" /><h2>订单明细</h2></div>
              <div class="summary-row"><span>电影票</span><strong>¥{{ money(showtime.basePrice) }} × {{ seats.length }}</strong></div>
              <div class="summary-row"><span>服务费</span><strong class="free">¥0.00</strong></div><div class="summary-divider"></div>
              <div class="summary-total"><span>应付金额</span><strong><small>¥</small>{{ totalPrice }}</strong></div>
              <div v-if="saleIssue" class="sale-warning"><MaterialIcon class="msr" name="schedule" /><span>{{ saleIssue }}</span></div>
              <button class="submit-btn" :disabled="submitting || !!saleIssue" @click="submitOrder">
                <span v-if="submitting" class="button-spinner"></span><MaterialIcon v-else class="msr" name="lock" />{{ submitting ? '正在创建订单…' : '提交订单' }}
              </button>
              <p class="submit-tip"><MaterialIcon class="msr" name="security" />提交即表示同意购票与退票规则</p>
            </article>
            <article class="card remark-card">
              <div class="card-head"><div><MaterialIcon class="msr" name="edit_note" /><h2>备注信息</h2></div><span class="optional">选填</span></div>
              <textarea v-model="remark" maxlength="200" rows="4" placeholder="如有特殊需求，请在此说明" @input="submitError = ''"></textarea>
              <span class="word-count">{{ remark.length }} / 200</span>
            </article>
            <article class="notice-card"><div><MaterialIcon class="msr" name="info" /><strong>温馨提示</strong></div><ul><li>请在订单创建后 10 分钟内完成支付</li><li>支付前座位可能因实时库存变化而失效</li><li>影片开场前 15 分钟停止售票</li></ul></article>
          </aside>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
/**
 * 订单确认：核对场次、座位和金额，创建待支付订单并展示可重试的失败原因。
 */
import axios from 'axios'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showtimeApi } from '@/api/showtime'
import { orderApi } from '@/api/order'
import PriceComparison from '@/components/PriceComparison.vue'
import type { Showtime } from '@/types'
import { ElMessage } from 'element-plus/es/components/message/index'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const submitting = ref(false)
const showtime = ref<Showtime | null>(null)
const seats = ref<string[]>([])
const remark = ref('')
const loadError = ref('')
const submitError = ref('')

const money = (value: number | string | undefined) => {
  const amount = Number(value)
  return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
}
const totalPrice = computed(() => showtime.value ? money(Number(showtime.value.basePrice) * seats.value.length) : '0.00')
const hallBadge = computed(() => {
  const name = showtime.value?.hallName?.toUpperCase() || ''
  if (name.includes('IMAX')) return 'IMAX'
  if (name.includes('VIP')) return 'VIP'
  if (name.includes('杜比')) return 'DOLBY'
  return '标准厅'
})
const posterGradient = computed(() => {
  const palettes = ['linear-gradient(145deg,#301607,#ff8400)', 'linear-gradient(145deg,#17182e,#5c6ac4)', 'linear-gradient(145deg,#102d26,#1f8a70)', 'linear-gradient(145deg,#36152f,#b9478c)']
  return palettes[(showtime.value?.movieId || 0) % palettes.length]
})
const saleIssue = computed(() => {
  if (!showtime.value) return ''
  if (showtime.value.status != null && showtime.value.status !== 0) return '该场次已停止售票，请返回重新选择场次。'
  const start = new Date(showtime.value.startTime).getTime()
  if (!Number.isFinite(start)) return '场次时间无效，请返回重新选择场次。'
  if (start - Date.now() <= 15 * 60 * 1000) return '距离开场不足 15 分钟，该场次已停止售票。'
  return ''
})

/** 读取并校验场次与座位路由参数，防止无效数据进入下单。 */
const parseRouteParams = () => {
  const rawId = Array.isArray(route.query.showtimeId) ? route.query.showtimeId[0] : route.query.showtimeId
  const rawSeats = Array.isArray(route.query.seats) ? route.query.seats[0] : route.query.seats
  const showtimeId = Number(rawId)
  const parsedSeats = typeof rawSeats === 'string' ? [...new Set(rawSeats.split(',').map(item => item.trim()).filter(Boolean))] : []
  if (!Number.isSafeInteger(showtimeId) || showtimeId <= 0 || parsedSeats.length === 0) throw new Error('订单参数已失效，请返回重新选座。')
  return { showtimeId, parsedSeats }
}
const requestErrorMessage = (error: unknown, fallback: string) => {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string } | undefined
    return data?.message || error.message || fallback
  }
  return error instanceof Error ? error.message : fallback
}
/** 加载当前页面所需数据，并同步加载与失败状态。 */
const loadData = async () => {
  loading.value = true
  loadError.value = ''
  submitError.value = ''
  showtime.value = null
  try {
    const { showtimeId, parsedSeats } = parseRouteParams()
    seats.value = parsedSeats
    showtime.value = await showtimeApi.getById(showtimeId)
    if (!showtime.value?.startTime) throw new Error('场次信息不完整，请返回重新选择。')
  } catch (error) {
    console.error('加载订单确认信息失败:', error)
    loadError.value = requestErrorMessage(error, '订单信息加载失败，请返回重新选座。')
  } finally {
    loading.value = false
  }
}
const formatDate = (datetime?: string) => {
  if (!datetime) return '--'
  const date = new Date(datetime)
  return Number.isNaN(date.getTime()) ? '--' : date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'short' })
}
const formatTime = (datetime?: string) => {
  if (!datetime) return '--:--'
  const date = new Date(datetime)
  return Number.isNaN(date.getTime()) ? '--:--' : date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}
/** 提交核对后的场次与座位，成功后跳转支付页；失败时保留可重试提示。 */
const submitOrder = async () => {
  if (!showtime.value || submitting.value || saleIssue.value) return
  submitError.value = ''
  submitting.value = true
  try {
    const latestSeatStatus = await showtimeApi.getSeatStatus(showtime.value.id)
    const occupied = new Set(latestSeatStatus.occupiedSeats || [])
    const unavailableSeats = seats.value.filter(seat => occupied.has(seat))
    if (unavailableSeats.length) throw new Error(`${unavailableSeats.join('、')} 已被占用，请返回重新选座。`)

    const orderNo = await orderApi.create({ showtimeId: showtime.value.id, seats: [...seats.value], remark: remark.value.trim() || undefined })
    if (!orderNo) throw new Error('服务器未返回订单号，请稍后重试。')
    ElMessage.success('订单创建成功，座位已为你锁定')
    await router.push({ name: 'OrderPay', params: { orderNo }, query: { pricingPoints: route.query.pricingPoints, pricingCoupons: route.query.pricingCoupons } })
  } catch (error) {
    console.error('创建订单失败:', error)
    submitError.value = requestErrorMessage(error, '创建订单失败，请稍后重试。')
    ElMessage.error(submitError.value)
  } finally {
    submitting.value = false
  }
}
const goBack = () => showtime.value?.id ? router.push({ name: 'SeatSelection', params: { id: showtime.value.id } }) : router.back()
onMounted(loadData)
</script>

<style scoped>
.confirm-price-comparison{margin:0}
.confirm-app{--primary:#ff8400;--bg:#f2f3f0;--card:#fff;--fg:#111;--muted:#666;--border:#cbccc9;--secondary:#e7e8e5;--danger:#dc2626;--mono:'JetBrains Mono',ui-monospace,SFMono-Regular,Menlo,monospace;--sans:'Geist',-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;min-height:100vh;color:var(--fg);background:var(--bg);font-family:var(--sans)}
button,textarea{font:inherit}button{color:inherit}.msr{font-size:19px}
.top-bar{position:sticky;top:0;z-index:40;display:flex;align-items:center;justify-content:space-between;gap:24px;min-height:69px;padding:14px 32px;background:rgba(242,243,240,.94);border-bottom:1px solid var(--border);backdrop-filter:blur(10px)}
.tb-left,.brand-block,.crumbs,.tb-step{display:flex;align-items:center}.tb-left{gap:20px}.tb-back{width:40px;height:40px;border-radius:50%;border:1px solid var(--border);background:var(--card);display:grid;place-items:center;cursor:pointer;transition:.15s ease}.tb-back:hover{border-color:var(--primary);transform:translateX(-2px)}
.brand-block{gap:10px;cursor:pointer}.brand-mark{width:34px;height:34px}.brand-mark img{width:100%;height:100%;object-fit:contain}.brand-text{display:flex;flex-direction:column}.brand{font:700 15px/1.2 var(--mono);letter-spacing:1.5px}.brand-sub{font-size:9px;color:var(--muted)}
.crumbs{gap:6px}.crumb{font-size:13px;color:var(--muted);cursor:pointer}.crumb:hover,.crumb.active{color:var(--fg)}.crumb.active{font-weight:700;cursor:default}.crumb-sep{font-size:14px;color:var(--muted)}
.tb-step{gap:6px}.step{width:24px;height:24px;display:grid;place-items:center;border-radius:50%;background:var(--secondary);border:1px solid var(--border);color:var(--muted);font:700 11px/1 var(--mono)}.step .msr{font-size:14px}.step.done{background:var(--fg);border-color:var(--fg);color:#fff}.step.active{background:var(--primary);border-color:var(--primary);color:#111;box-shadow:0 0 0 4px rgba(255,132,0,.15)}.step-label{margin:0 2px;color:var(--muted);font-size:11px}.step-label.done,.step-label.active{color:var(--fg);font-weight:700}.step-line{width:24px;height:2px;border-radius:2px;background:var(--border)}.step-line.done{background:var(--fg)}
.page-body{max-width:1180px;margin:0 auto;padding:36px 32px 56px}.page-head{display:flex;justify-content:space-between;align-items:flex-end;gap:24px;margin-bottom:28px}.eyebrow{display:flex;align-items:center;gap:8px;color:var(--muted);font:700 10px/1 var(--mono);letter-spacing:1.8px}.eyebrow span{width:7px;height:7px;border-radius:50%;background:var(--primary);box-shadow:0 0 8px var(--primary)}.page-head h1{margin:10px 0 6px;font-size:clamp(28px,3vw,42px);line-height:1.08;letter-spacing:-1.5px}.page-head p{color:var(--muted);font-size:14px}.head-badge{display:flex;align-items:center;gap:10px;min-width:220px;padding:12px 16px;border:1px solid var(--border);border-radius:14px;background:var(--card)}.head-badge>.msr{color:#16a34a;font-size:24px}.head-badge div{display:flex;flex-direction:column}.head-badge strong{font-size:13px}.head-badge span{margin-top:2px;color:var(--muted);font-size:11px}
.layout{display:grid;grid-template-columns:minmax(0,1fr) 340px;gap:22px;align-items:start}.main-col,.summary-col{display:flex;flex-direction:column;gap:18px}.card,.summary-card,.notice-card,.state-card{background:var(--card);border:1px solid var(--border);border-radius:18px;box-shadow:0 8px 30px rgba(17,17,17,.035)}.card{padding:22px}.card-head,.card-head>div,.summary-head{display:flex;align-items:center}.card-head{justify-content:space-between;gap:16px;margin-bottom:20px}.card-head>div,.summary-head{gap:9px}.card-head>div>.msr,.summary-head>.msr{color:var(--primary)}.card-head h2,.summary-head h2{font-size:16px}.live-tag,.seat-count,.optional{font:700 10px/1 var(--mono);color:var(--muted)}.live-tag{display:flex;align-items:center;gap:6px}.live-tag i{width:6px;height:6px;border-radius:50%;background:#16a34a}.seat-count{padding:6px 9px;border-radius:999px;background:var(--secondary);color:var(--fg)}
.movie-body{display:grid;grid-template-columns:132px 1fr;gap:24px}.poster{height:184px;border-radius:14px;overflow:hidden;display:grid;place-items:center;color:rgba(255,255,255,.8)}.poster img{width:100%;height:100%;object-fit:cover}.poster>.msr{font-size:46px}.movie-tags{display:flex;flex-wrap:wrap;gap:6px}.movie-tags span{padding:4px 7px;border:1px solid var(--border);border-radius:5px;background:var(--bg);color:var(--muted);font:700 9px/1 var(--mono)}.movie-tags span:first-child{border-color:var(--primary);background:rgba(255,132,0,.09);color:#b85f00}.movie-info h3{margin:10px 0 18px;font-size:25px;line-height:1.2}.info-grid{display:grid;grid-template-columns:1fr 1fr;gap:15px 20px}.info-item{display:flex;align-items:center;gap:10px;min-width:0}.info-item.full{grid-column:1/-1}.info-item>.msr{width:34px;height:34px;display:grid;place-items:center;border-radius:9px;background:var(--bg);color:var(--muted);font-size:17px}.info-item div{display:flex;min-width:0;flex-direction:column;gap:2px}.info-item span{color:var(--muted);font-size:10px}.info-item strong{overflow:hidden;font-size:13px;white-space:nowrap;text-overflow:ellipsis}
.seat-list{display:flex;flex-wrap:wrap;gap:10px}.seat-list span{display:flex;align-items:center;gap:7px;padding:10px 13px;border:1px solid rgba(255,132,0,.4);border-radius:10px;background:rgba(255,132,0,.08);font:700 12px/1 var(--mono)}.seat-list .msr{color:var(--primary);font-size:16px}.change-seat{display:flex;align-items:center;gap:6px;margin-top:18px;padding:0;border:0;background:none;color:var(--muted);font-size:12px;cursor:pointer}.change-seat:hover{color:var(--primary)}.change-seat .msr{font-size:15px}
.remark-card{position:relative}.remark-card textarea{width:100%;resize:vertical;min-height:96px;padding:13px 14px 28px;border:1px solid var(--border);border-radius:11px;outline:none;background:var(--bg);color:var(--fg);line-height:1.6}.remark-card textarea:focus{border-color:var(--primary);box-shadow:0 0 0 3px rgba(255,132,0,.1)}.word-count{position:absolute;right:36px;bottom:32px;color:var(--muted);font:500 10px/1 var(--mono)}
.summary-card{padding:22px}.summary-head{margin-bottom:23px}.summary-row{display:flex;justify-content:space-between;gap:16px;margin:13px 0;font-size:13px}.summary-row span{color:var(--muted)}.summary-row strong{font:700 12px/1.4 var(--mono)}.summary-row .free{color:#16a34a}.summary-divider{height:1px;margin:20px 0;background:var(--border)}.summary-total{display:flex;justify-content:space-between;align-items:flex-end}.summary-total span{font-size:14px;font-weight:700}.summary-total strong{color:var(--primary);font:800 34px/1 var(--mono);letter-spacing:-1.5px}.summary-total small{margin-right:2px;font-size:16px}
.submit-btn,.primary-btn{width:100%;display:flex;align-items:center;justify-content:center;gap:8px;margin-top:23px;padding:14px 18px;border:1px solid var(--primary);border-radius:11px;background:var(--primary);color:#111;font-weight:800;cursor:pointer;transition:.16s ease}.submit-btn:hover:not(:disabled),.primary-btn:hover{transform:translateY(-1px);box-shadow:0 9px 20px rgba(255,132,0,.23)}.submit-btn:disabled{cursor:not-allowed;opacity:.55}.submit-tip{display:flex;align-items:center;justify-content:center;gap:5px;margin-top:11px;color:var(--muted);font-size:10px}.submit-tip .msr{font-size:13px;color:#16a34a}.sale-warning{display:flex;align-items:flex-start;gap:8px;margin-top:18px;padding:11px;border:1px solid #fecaca;border-radius:9px;background:#fef2f2;color:#991b1b;font-size:12px;line-height:1.5}.sale-warning .msr{margin-top:1px;font-size:16px}.button-spinner,.spinner{border-radius:50%;border:2px solid rgba(17,17,17,.25);border-top-color:#111;animation:spin .7s linear infinite}.button-spinner{width:16px;height:16px}@keyframes spin{to{transform:rotate(360deg)}}
.notice-card{padding:17px 19px}.notice-card>div{display:flex;align-items:center;gap:7px;font-size:13px}.notice-card .msr{color:var(--primary);font-size:17px}.notice-card ul{margin:11px 0 0 17px;color:var(--muted);font-size:11px;line-height:1.8}
.error-banner{display:flex;align-items:center;gap:12px;margin-bottom:18px;padding:14px 16px;border:1px solid #fecaca;border-radius:12px;background:#fef2f2;color:#991b1b}.error-banner>.msr{font-size:22px}.error-banner div{display:flex;flex:1;flex-direction:column;gap:2px}.error-banner strong{font-size:13px}.error-banner span{font-size:12px}.error-banner button{padding:4px;border:0;background:none;color:inherit;cursor:pointer}
.state-card{display:flex;min-height:330px;align-items:center;justify-content:center;flex-direction:column;padding:40px;text-align:center}.loading-state .spinner{width:34px;height:34px;margin-bottom:18px;border-color:var(--secondary);border-top-color:var(--primary)}.state-card strong,.state-card h2{font-size:19px}.state-card p{max-width:520px;margin-top:8px;color:var(--muted);font-size:13px}.state-icon{width:52px;height:52px;display:grid;place-items:center;margin-bottom:16px;border-radius:50%;background:#fef2f2;color:var(--danger)}.state-icon .msr{font-size:28px}.state-actions{display:flex;gap:10px;margin-top:22px}.secondary-btn,.primary-btn.compact{width:auto;margin:0;padding:10px 16px;border-radius:9px;cursor:pointer}.secondary-btn{border:1px solid var(--border);background:var(--card)}
@media(max-width:900px){.layout{grid-template-columns:1fr}.tb-step{display:none}.head-badge{display:none}}
@media(max-width:640px){.top-bar{padding:12px 16px}.brand-text,.crumbs{display:none}.page-body{padding:25px 16px 40px}.page-head{margin-bottom:20px}.movie-body{grid-template-columns:84px 1fr;gap:15px}.poster{height:120px}.movie-info h3{font-size:20px}.info-grid{grid-template-columns:1fr}.info-item.full{grid-column:auto}.card{padding:18px}}
</style>

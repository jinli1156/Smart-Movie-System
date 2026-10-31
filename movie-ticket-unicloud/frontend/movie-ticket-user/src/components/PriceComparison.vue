<template>
  <section class="price-comparison" aria-label="AI 智能比价" :aria-busy="busy">
    <header>
      <div><span class="eyebrow">AI 购票助手 · 实时数据</span><h3>为这部电影，找到更省钱的场次</h3>
        <p>{{ result ? `已识别《${result.movieTitle}》 · ${result.date} · ${result.people}人` : '自动识别当前电影，比较站内同日场次和可用优惠' }}</p>
      </div>
      <button type="button" :disabled="busy" @click="refresh">{{ busy ? '正在比价…' : '刷新比价' }}</button>
    </header>
    <div class="filters">
      <label>观影日期<input v-model="date" aria-label="比价日期" type="date" :min="today" :max="lastDate" /></label>
      <label>购票人数<select v-model.number="people" aria-label="比价人数" :disabled="fixedPeople"><option v-for="n in 5" :key="n" :value="n">{{ n }} 人</option></select></label>
      <label class="check"><input v-model="useCoupons" type="checkbox" :disabled="!userStore.isLoggedIn" />使用优惠券</label>
      <label class="check"><input v-model="usePoints" type="checkbox" :disabled="!userStore.isLoggedIn" />使用积分</label>
    </div>
    <p v-if="!userStore.isLoggedIn" class="notice">当前比较原价，<router-link :to="{ path: '/login', query: { redirect: route.fullPath } }">登录后匹配我的优惠</router-link>。</p>
    <p v-if="busy" class="status" role="status">正在查询场次，计算优惠后总实付…</p>
    <p v-else-if="error" class="status error" role="alert">{{ error }} <button type="button" @click="refresh">重试</button></p>
    <template v-else-if="result">
      <p class="notice">已比较 {{ result.comparedCount }} 个可购场次 · 按 {{ result.people }} 张票总实付排序 · {{ result.queriedAt.replace('T', ' ') }} 查询</p>
      <p v-if="!result.offers.length" class="status">当日暂无满足人数要求的可购场次，请更换日期或人数。</p>
      <div v-else class="offers">
        <article v-for="(offer, index) in result.offers" :key="offer.showtimeId" class="offer" :class="{ best: index === 0 }">
          <div class="offer-title"><strong>{{ offer.cinemaName }}</strong><span>{{ index === 0 ? '最低实付推荐' : '备选场次' }}</span></div>
          <p>{{ offer.startTime.replace('T', ' ').slice(0, 16) }} · {{ offer.hallName }} · 余 {{ offer.availableSeats }} 座</p>
          <dl><div><dt>{{ result.people }} 张原价</dt><dd>¥{{ money(offer.originalPrice) }}</dd></div>
            <div><dt>{{ offer.bestPlan.couponName }}</dt><dd>−¥{{ money(offer.bestPlan.couponDiscount) }}</dd></div>
            <div><dt>{{ offer.bestPlan.pointsUsed }} 积分</dt><dd>−¥{{ money(offer.bestPlan.pointsDiscount) }}</dd></div></dl>
          <div class="offer-bottom"><div><small>预计总实付</small><strong>¥{{ money(offer.bestPlan.totalPrice) }}</strong></div>
            <router-link :to="{ path: `/showtime/${offer.showtimeId}/seats`, query: { people: result.people, pricingPoints: String(result.usePoints), pricingCoupons: String(result.useCoupons) } }">{{ offer.showtimeId === showtimeId ? '查看当前场次' : '前往选座' }}</router-link></div>
          <p class="reason">{{ offer.reason }}</p>
        </article>
      </div>
      <p class="notice">{{ result.notice }}</p>
      <p v-if="fixedPeople" class="notice">查看其他场次将进入选座页，当前待支付订单仍保留，可在订单页取消。</p>
      <router-link class="ask-ai" :to="{ path: '/assistant', query: { q: `帮我比价《${result.movieTitle}》，${result.date}，${result.people}人，${useCoupons ? '使用优惠券' : '保留优惠券'}，${usePoints ? '使用积分' : '保留积分'}，推荐最省钱的场次并解释原因。` } }">让 AI 解释比价方案 →</router-link>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { pricingApi, type PriceComparison } from '@/api/pricing'
const props = withDefaults(defineProps<{ movieId?: number; showtimeId?: number; initialDate?: string; initialPeople?: number; fixedPeople?: boolean }>(), { initialPeople: 1, fixedPeople: false })
const userStore = useUserStore()
const route = useRoute()
const beijingDate = (offset = 0) => new Date(Date.now() + 8 * 3600000 + offset * 86400000).toISOString().slice(0, 10)
const today = beijingDate()
const lastDate = beijingDate(90)
const date = ref(props.initialDate || today)
const people = ref(props.initialPeople)
const usePoints = ref(true)
const useCoupons = ref(true)
const busy = ref(false)
const error = ref('')
const result = ref<PriceComparison>()
const money = (value: number) => Number(value).toFixed(2)
let version = 0
let controller: AbortController | undefined
const refresh = async () => {
  const current = ++version
  controller?.abort()
  result.value = undefined
  error.value = ''
  if ((!props.movieId && !props.showtimeId) || !date.value) { busy.value = false; return }
  controller = new AbortController()
  busy.value = true
  try {
    const data = await pricingApi.compare({ ...(props.movieId ? { movieId: props.movieId } : { showtimeId: props.showtimeId }), date: date.value,
      people: people.value, usePoints: usePoints.value, useCoupons: useCoupons.value }, controller.signal)
    if (!data || !Array.isArray(data.offers) || typeof data.queriedAt !== 'string') throw new Error('未收到有效比价数据，请重试')
    if (current === version) result.value = data
  } catch (e: any) {
    if (current === version) error.value = e.response?.data?.message || e.message || '比价暂不可用，请稍后重试'
  } finally { if (current === version) busy.value = false }
}
watch(() => props.initialDate, value => { date.value = value || today })
watch(() => props.initialPeople, value => { people.value = value })
watch([() => props.movieId, () => props.showtimeId, date, people, usePoints, useCoupons, () => userStore.token], refresh, { immediate: true })
onBeforeUnmount(() => { version++; controller?.abort() })
</script>

<style scoped>
.price-comparison{margin:20px 0;padding:24px;border:1px solid #ecd6b8;border-radius:16px;background:#fffcf7;color:#252a23}.price-comparison header{display:flex;justify-content:space-between;gap:16px;align-items:center}.eyebrow{color:#a45d12;font-size:12px;font-weight:700}.price-comparison h3{font-size:20px;margin:8px 0}.price-comparison p{font-size:13px;line-height:1.65;margin:8px 0;color:#64695f}.price-comparison button,.offer-bottom a{border:1px solid #e1b779;background:#fff0d8;color:#733d0b;border-radius:8px;padding:9px 14px;font-size:13px;text-decoration:none;cursor:pointer}.price-comparison button:disabled{opacity:.6;cursor:wait}.filters{display:flex;flex-wrap:wrap;gap:14px;align-items:center;margin:20px 0 12px}.filters label{display:flex;align-items:center;gap:8px;font-size:13px}.filters input[type=date],.filters select{padding:8px;border:1px solid #dcdcd4;border-radius:7px;background:white;color:inherit;font:inherit}.check input{accent-color:#ce771b;width:16px;height:16px}.offers{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,260px),1fr));gap:12px;margin:16px 0}.offer{background:white;border:1px solid #e4e5dd;border-radius:12px;padding:16px}.offer.best{border-color:#dc9a49}.offer-title{display:flex;align-items:center;justify-content:space-between;gap:8px}.offer-title strong{font-size:15px}.offer-title span{font-size:11px;white-space:nowrap;background:#f7ecda;color:#96601b;padding:4px 6px;border-radius:4px}.offer dl{margin:14px 0}.offer dl div{display:flex;justify-content:space-between;gap:10px;font-size:12px;margin:8px 0}.offer dd{margin:0;white-space:nowrap}.offer-bottom{display:flex;justify-content:space-between;align-items:center;gap:10px;border-top:1px solid #eee9e1;padding-top:12px}.offer-bottom small{display:block;font-size:11px;color:#64695f}.offer-bottom strong{font-size:25px;color:#b25c12}.price-comparison .reason{font-size:11px;margin-top:12px}.status{padding:22px;background:#f5f2eb;border-radius:8px}.price-comparison .error{color:#a93721}.notice a,.ask-ai{color:#96590e;font-size:13px}.ask-ai{display:inline-block;margin-top:8px}.price-comparison :focus-visible{outline:2px solid #ad6815;outline-offset:3px}@media(max-width:600px){.price-comparison{padding:16px}.price-comparison header{align-items:flex-start;flex-direction:column}.price-comparison h3{font-size:18px}}
</style>

<template>
  <div class="assistant-page">
    <aside id="conversation-sidebar" class="conversation-sidebar" :class="{ 'is-open': sidebarOpen }" aria-label="对话导航" @keydown.esc="closeSidebar">
      <div class="sidebar-brand"><router-link to="/home"><img class="sidebar-logo" src="/xiangzhi-ticket.png" alt="" /><span class="sidebar-logo-text"><strong class="brand-wordmark" role="img" aria-label="响指电影"></strong><small>premium booking</small></span></router-link><button class="sidebar-close" type="button" aria-label="关闭历史会话" @click="closeSidebar">×</button></div>
      <button class="new-chat" type="button" @click="startNewChat"><MaterialIcon name="add" />新对话</button>
      <section v-if="userStore.isLoggedIn" class="conversation-history" aria-label="历史会话">
        <h2>历史会话</h2>
        <div class="history-actions"><button type="button" @click="refreshHistory" :disabled="historyBusy">刷新历史</button><span>对话自动保存到当前账号</span></div>
        <p v-if="historyError" role="alert">{{ historyError }}</p>
        <p v-if="historyBusy" role="status">正在读取历史…</p>
        <p v-else-if="!conversations.length">暂无历史会话，发送第一条消息后自动保存。</p>
        <div class="conversation-list">
          <div v-for="conversation in conversations" :key="conversation.id" class="conversation-row" :class="{ active: conversation.id === conversationId }">
            <button type="button" class="conversation-open" :aria-current="conversation.id === conversationId ? 'true' : undefined" :disabled="historyBusy" @click="selectConversation(conversation.id)"><strong :title="conversation.title">{{ conversation.title }}</strong><time :datetime="conversation.updatedAt">更新于 {{ conversation.updatedAt.replace('T', ' ').slice(0, 16) }}</time></button>
            <button v-if="deleteCandidate !== conversation.id" type="button" :aria-label="'删除会话：' + conversation.title" @click="deleteCandidate = conversation.id">删除</button>
            <template v-else><button type="button" :disabled="historyBusy" @click="deleteConversation(conversation.id)">确认删除</button><button type="button" @click="deleteCandidate = ''">取消</button></template>
          </div>
        </div>
        <button v-if="hasMoreConversations" type="button" :disabled="historyBusy" @click="loadConversations(false, true)">更多会话</button>
      </section>
      <div v-if="!userStore.isLoggedIn" class="guest-history"><h2>历史会话</h2><p>登录后，对话会自动保存在这里。</p><router-link to="/login">登录并保存对话 →</router-link></div>
      <nav class="sidebar-links" aria-label="电影服务"><router-link to="/home"><MaterialIcon name="home" />返回首页</router-link><router-link to="/movies"><MaterialIcon name="movie" />浏览电影</router-link><router-link to="/orders"><MaterialIcon name="receipt_long" />我的订单</router-link></nav>
      <div class="assistant-account"><router-link :to="userStore.isLoggedIn ? '/profile' : '/login'"><span class="account-avatar" :class="{ guest: !userStore.isLoggedIn }"><template v-if="userStore.isLoggedIn">{{ (userStore.userInfo?.nickname || userStore.userInfo?.username || '用户').slice(0, 1) }}</template><MaterialIcon v-else name="person" /></span><span class="account-info"><strong>{{ userStore.userInfo?.nickname || userStore.userInfo?.username || '访客' }}</strong><small>{{ userStore.isLoggedIn ? 'GOLD · ' + Number(userStore.userInfo?.points || 0).toLocaleString() + ' 积分' : '登录解锁会员权益' }}</small></span></router-link><button v-if="userStore.isLoggedIn" type="button" @click="userStore.logout()">退出登录</button><router-link v-else class="account-login" to="/login">登录</router-link></div>
    </aside>
    <main class="assistant-main">
      <header class="page-heading">
        <button ref="historyToggle" class="history-toggle" type="button" :aria-expanded="sidebarOpen" aria-controls="conversation-sidebar" @click="sidebarOpen = !sidebarOpen"><MaterialIcon name="menu" />历史会话</button>
        <div class="heading-title"><h1>影票观<span>AI</span></h1><p class="active-title">{{ activeTitle || '新对话' }}</p></div>
        <span class="connection-status"><i :class="{ ready: capabilities?.configured }"></i>{{ connectionLabel }}</span>
      </header>

      <p v-if="historyError" class="history-error" role="alert">{{ historyError }}</p>
      <div class="assistant-workspace">
        <section class="chat-panel" aria-label="影票观对话">
          <AssistantWatchPanel :key="userStore.token || 'guest'" :plan="watchPlan" :busy="loading || historyBusy" :logged-in="userStore.isLoggedIn" @change="(message, patch) => send(message, patch)" />

          <div ref="messageList" class="messages" role="log" aria-label="聊天记录" aria-live="polite" :aria-busy="loading">
            <button v-if="hasOlderTurns" class="older-messages" type="button" :disabled="historyBusy" @click="loadOlderTurns">加载更早消息</button>

            <div v-if="!messages.length" class="welcome">
              <span class="welcome-icon"><MaterialIcon name="movie" /></span><h2>今天，想看点什么？</h2><p>告诉我你的心情、喜欢的类型，<br />或者直接问一部电影的场次和余票。</p>
              <div class="starters"><button v-for="item in starters" :key="item.title" @click="send(item.question)"><MaterialIcon :name="item.icon" /><strong>{{ item.title }}</strong><span>{{ item.description }}</span></button></div>
            </div>
            <article v-for="(item, index) in messages" v-show="item.content" :key="index" class="message" :class="item.role">
              <div class="message-label">{{ item.role === 'user' ? '你' : '影票观' }}<span v-if="item.source === 'unavailable'"> · 回答未完成</span></div>
              <div v-if="item.content" class="bubble">{{ item.content }}</div>
              <p v-if="item.restored && item.results?.length" class="result-count">历史查询快照，当前价格、余票和优惠请重新查询。</p>
              <div v-if="item.source === 'model'" class="primary-results">
                <div v-if="primaryCards(item).length" class="recommendation-grid" :class="{ 'has-posters': primaryCards(item).some(card => card.movie), 'has-outings': primaryCards(item).some(card => card.outing || card.compact) }">
                  <article v-for="card in primaryCards(item)" :key="card.path + card.title" class="result-card" :class="{ 'movie-result': card.movie, 'outing-result': card.outing || card.compact }">
                    <router-link v-if="card.movie" :to="card.path" class="result-poster" :aria-label="card.title">
                      <img v-poster :src="card.poster || '/poster-placeholder.svg'" :alt="card.title + '海报'" />
                      <span v-if="card.rating" class="poster-rating"><MaterialIcon name="star" />{{ card.rating }}</span>
                      <span v-if="card.badge" class="poster-badge">{{ card.badge }}</span>
                    </router-link>
                    <div class="result-info"><strong>{{ card.title }}</strong><span v-if="card.outing" class="plan-total">{{ card.badge }}</span><p>{{ card.detail }}</p><p v-if="card.reason" class="movie-reason">{{ card.reason }}</p></div>
                    <router-link :to="card.path" class="result-action">{{ card.action }} <span aria-hidden="true">↗</span></router-link>
                    <button v-if="card.checkout" class="checkout-start" type="button" :disabled="loading || checkoutBusy || historyBusy" @click="prepareCheckout(card.checkout)">让影票观下单</button>
                  </article>
                </div>
                <p v-for="notice in bookingNotices(item)" :key="notice" class="result-count">{{ notice }}</p>
                <AssistantCheckoutCard v-if="checkoutDraft(item)" :draft="checkoutDraft(item)!" :busy="checkoutBusy" :disabled="loading || historyBusy" :error="checkoutErrors[checkoutDraft(item)!.draftId]" @confirm="confirmCheckout" @refresh="value => prepareCheckout({ criteria: value.criteria || { showtimeId: value.showtimeId, people: value.people, usePoints: value.usePoints, useCoupons: value.useCoupons } })" />
              </div>
              <p v-if="item.results?.some(result => result.code === 'LOGIN_REQUIRED')" class="login-hint"><router-link to="/login">去登录</router-link>后可查询自己的订单和偏好。</p>
              <button v-if="canSpeak && item.role === 'assistant' && item.content && !loading" class="read-aloud" @click="speak(item.content)">朗读回答</button>
            </article>
            <p v-if="loading" class="progress" role="status"><span></span>正在准备回答…</p>
          </div>
          <div v-if="suggestions.length && !loading" class="suggestions"><button v-for="suggestion in suggestions" :key="suggestion" @click="send(suggestion)">{{ suggestion }}</button></div>
          <form class="composer" @submit.prevent="send()">
            <label class="sr-only" for="assistant-question">向影票观提问</label>
            <textarea id="assistant-question" v-model="draft" :disabled="historyBusy || checkoutBusy" maxlength="500" rows="2" placeholder="给影票观发送消息" @keydown="onKeydown"></textarea>
            <div class="composer-bottom"><div class="input-actions"><button v-if="canRecognize" type="button" :disabled="loading" @click="toggleVoice">{{ listening ? '停止录音' : '语音输入' }}</button><button v-if="speaking" type="button" @click="stopSpeaking">停止朗读</button><span>{{ draft.length }}/500 · Enter 发送</span></div><button v-if="loading" type="button" class="send" @click="stop">停止回答</button><button v-else type="submit" class="send" :disabled="!draft.trim()">发送 <MaterialIcon name="send" /></button></div>
            <p v-if="voiceNotice" class="voice-notice" role="status">{{ voiceNotice }}</p>
          </form>
          <p class="snapshot-note">票价、余票与订单状态以查询时及提交时的系统校验为准。</p>
        </section>
        <aside class="assistant-guide" aria-label="使用指南与账号说明">
          <h2 class="guide-heading">使用指南与账号说明</h2>
          <button class="guide-toggle" type="button" :aria-expanded="guideExpanded" aria-controls="assistant-guide-content" @click="guideExpanded = !guideExpanded">使用指南与账号说明 <span aria-hidden="true">{{ guideExpanded ? '−' : '+' }}</span></button>
          <div id="assistant-guide-content" class="guide-body" :class="{ expanded: guideExpanded }">
            <section class="guide-section"><h3>你的观影，一站问清</h3><div class="ability" v-for="ability in abilities" :key="ability.title"><MaterialIcon :name="ability.icon" /><div><strong>{{ ability.title }}</strong><p>{{ ability.text }}</p></div></div></section>
            <section class="guide-souvenir" aria-labelledby="assistant-souvenir-title">
              <div class="souvenir-mark"><MaterialIcon name="auto_awesome" /><span>星空科幻 · 专属收藏</span></div>
              <h3 id="assistant-souvenir-title">AI 观影纪念票</h3>
              <p>把这一场光影，存入宇宙。基于已支付订单，收藏可保存、可分享的专属纪念卡。</p>
              <router-link to="/orders?status=1">{{ userStore.isLoggedIn ? '选择订单，制作纪念票' : '登录后制作纪念票' }}<MaterialIcon name="arrow_forward" /></router-link>
              <small>仅作观影纪念 · 非入场凭证</small>
            </section>
            <section class="guide-account"><h3>{{ userStore.isLoggedIn ? '对话已同步到账号' : '让推荐更懂你' }}</h3><p>{{ userStore.isLoggedIn ? '可在左侧继续或删除历史对话。' : '登录后保存对话，获取个性化推荐。' }}</p><router-link :to="userStore.isLoggedIn ? '/profile?edit=preferences' : { path: '/login', query: { redirect: '/profile?edit=preferences' } }">{{ userStore.isLoggedIn ? '管理观影偏好' : '登录账号' }} →</router-link></section>
            <p class="guide-note">点击推荐卡片查看详情或选座。问题与必要查询摘要会发送至 DeepSeek。</p>
          </div>
        </aside>

      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
/**
 * 电影助手：维护会话与观影条件，消费流式回答和工具卡片，通过确认卡创建待支付订单。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AssistantWatchPanel from '@/components/AssistantWatchPanel.vue'
import AssistantCheckoutCard from '@/components/AssistantCheckoutCard.vue'
import { assistantApi, type AssistantCapabilities, type AssistantHistoryMessage, type ToolResult, type Conversation, type SavedTurn, type WatchPlan, type CheckoutDraft } from '@/api/assistant'

interface ChatMessage extends AssistantHistoryMessage { results?: ToolResult[]; source?: string; complete?: boolean; restored?: boolean }
interface CheckoutSelection { criteria: Record<string, unknown>; seats?: string[] }
interface ResultCard { title: string; detail: string; path: string; action: string; movie?: boolean; outing?: boolean; compact?: boolean; poster?: string; rating?: string; badge?: string; reason?: string; checkout?: CheckoutSelection }
interface Recognition {
  lang: string; interimResults: boolean
  onresult: ((event: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null
  onerror: ((event: { error: string }) => void) | null
  onend: (() => void) | null
  start(): void; stop(): void; abort(): void
}
const speechWindow = window as Window & { SpeechRecognition?: new () => Recognition; webkitSpeechRecognition?: new () => Recognition }
const RecognitionClass = speechWindow.SpeechRecognition || speechWindow.webkitSpeechRecognition
const canRecognize = Boolean(RecognitionClass)
const canSpeak = 'speechSynthesis' in window
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const guideExpanded = ref(false)
const sidebarOpen = ref(false)
const historyToggle = ref<HTMLButtonElement>()
const closeSidebar = () => { sidebarOpen.value = false; historyToggle.value?.focus() }
const startNewChat = () => { resetChat(); sidebarOpen.value = false; nextTick(() => document.getElementById('assistant-question')?.focus()) }
const selectConversation = async (id: string) => { sidebarOpen.value = false; await openConversation(id); historyToggle.value?.focus() }
const draft = ref('')
const messages = ref<ChatMessage[]>([])
const loading = ref(false)
const checkoutBusy = ref(false)
const checkoutErrors = ref<Record<string, string>>({})
const suggestions = ref<string[]>([])
const capabilities = ref<AssistantCapabilities>()
const capabilitiesFailed = ref(false)
const messageList = ref<HTMLElement>()
const listening = ref(false)
const speaking = ref(false)
const voiceNotice = ref('')
const conversationId = ref<string>()
const watchPlan = ref<WatchPlan>({ criteria: {}, options: [] })
const conversations = ref<Conversation[]>([])
const activeTitle = computed(() => conversations.value.find(c => c.id === conversationId.value)?.title || '')
const historyBusy = ref(false)
const historyError = ref('')
const deleteCandidate = ref('')
const hasMoreConversations = ref(false)
const hasOlderTurns = ref(false)
const firstTurnId = ref<number>()
let conversationPage = 1
let viewVersion = 0
let recognition: Recognition | undefined
let controller: AbortController | undefined
const connectionLabel = computed(() => capabilitiesFailed.value ? '服务连接暂不可用' : capabilities.value ? capabilities.value.configured ? `DeepSeek · ${capabilities.value.model}` : 'DeepSeek 尚未配置' : '正在检查服务…')
const starters = [
  { title: '发现好电影', description: '按题材和口味推荐', icon: 'movie', question: '根据我的偏好推荐几部电影' },
  { title: '查场次与余票', description: '找到合适的观影时间', icon: 'calendar_month', question: '今天有哪些可购票场次？请给出价格和余票' },
  { title: '找一家影院', description: '查询影院和详细地址', icon: 'theaters', question: '南宁有哪些营业影院？' },
  { title: '我的订单', description: '查看待支付的电影票', icon: 'receipt_long', question: '帮我查看我的待支付订单' },
  { title: '多条件找场次', description: '时间、题材、评分与预算', icon: 'filter_alt', question: '找今天20点后、评分8分及以上、120分钟以内、单张不超过50元的科幻电影场次，两个人看' },
  { title: '智能比价', description: '跨影院比实付，匹配券与积分', icon: 'savings', question: '帮我比较一部电影的不同场次，算上我的优惠券和积分，推荐最省钱的方案' },
  { title: '选座与优惠', description: '推荐连座，试算预计实付', icon: 'event_seat', question: '我想买两张电影票，帮我找合适的场次、推荐中间靠后的连座，并试算优惠' }
]
const abilities = [
  { icon: 'movie', title: '发现好电影', text: '按喜好推荐，查询影院与场次' },
  { icon: 'event_seat', title: '安排一场电影', text: '查询余票、推荐连座与试算优惠' },
  { icon: 'receipt_long', title: '订单与购票帮助', text: '查看个人订单，了解购票规则' }
]
const movieRating = (value: unknown) => typeof value === 'number' && Number.isFinite(value) && value > 0 ? value.toFixed(1) : undefined
const money = (value: unknown) => Number.isFinite(Number(value)) ? Number(value).toFixed(2) : '—'
const cards = (result: ToolResult): ResultCard[] => {
  if (!result.ok || !result.data || Array.isArray(result.data)) return []
  const data = result.data
  if (result.source === 'plan_movie_outing' && Array.isArray(data.plans)) {
    return data.plans.filter((plan: any) => Number.isSafeInteger(plan.showtimeId) && plan.showtimeId > 0).slice(0, 3).map((plan: any, index: number) => {
      const seats = Array.isArray(plan.seats) && plan.seats.length === data.people && plan.seats.every((seat: unknown) => typeof seat === 'string' && /^\d+排\d+座$/.test(seat)) ? plan.seats : []
      const query = new URLSearchParams({ people: String(data.people), pricingPoints: String(data.usePoints !== false), pricingCoupons: String(data.useCoupons !== false) })
      if (seats.length) query.set('assistantSeats', seats.join(','))
      return { movie: true, outing: true, poster: plan.moviePoster, rating: movieRating(plan.movieRating), title: `方案 ${index + 1} · ${plan.movieTitle}`, badge: `${data.people}人实付 ¥${money(plan.bestPlan?.totalPrice)}`,
        checkout: seats.length ? { criteria: { ...(data.criteria || {}), showtimeId: plan.showtimeId, people: data.people, usePoints: data.usePoints !== false, useCoupons: data.useCoupons !== false }, seats } : undefined,
        detail: `${plan.cinemaName} · ${plan.hallName} · ${String(plan.startTime || '').replace('T', ' ').slice(0, 16)} 至 ${String(plan.endTime || '').slice(11, 16)}\n${seats.length ? seats.join(' · ') : '进入选座页自行选座'}\n原价 ¥${money(plan.originalPrice)} · 券抵 ¥${money(plan.bestPlan?.couponDiscount)} · 使用 ${plan.bestPlan?.pointsUsed ?? 0} 积分抵 ¥${money(plan.bestPlan?.pointsDiscount)}`,
        reason: `${plan.reason || ''}${plan.seatReason ? '；' + plan.seatReason : ''}`, path: `/showtime/${plan.showtimeId}/seats?${query}`, action: seats.length ? '使用此观影方案' : '前往选座' }
    })
  }
  if (result.source === 'compare_prices' && Array.isArray(data.offers)) {
    return data.offers.filter((offer: any) => Number.isSafeInteger(offer.showtimeId) && offer.showtimeId > 0).slice(0, 3).map((offer: any, index: number) => ({
      movie: true, compact: true, poster: data.moviePoster,
      title: `${index === 0 ? '最低实付推荐' : '备选场次'} · ${data.movieTitle} · 预计 ¥${money(offer.bestPlan.totalPrice)}`,
      detail: `${offer.cinemaName} · ${offer.hallName} · ${offer.startTime} · ${data.people}人 · 原价 ¥${money(offer.originalPrice)} · ${offer.bestPlan.couponName}抵 ¥${money(offer.bestPlan.couponDiscount)} · ${offer.bestPlan.pointsUsed} 积分抵 ¥${money(offer.bestPlan.pointsDiscount)}`,
      path: `/showtime/${offer.showtimeId}/seats?people=${Number(data.people)}&pricingPoints=${data.usePoints === true}&pricingCoupons=${data.useCoupons === true}`, action: '前往选座'
    }))
  }
  if (result.source === 'recommend_seats' && Number.isSafeInteger(data.showtimeId) && data.showtimeId > 0) {
    return (data.recommendations || []).filter((rec: any) => Array.isArray(rec.seats) && rec.seats.length > 0 && rec.seats.length <= 5 && rec.seats.every((seat: unknown) => typeof seat === 'string' && /^\d+排\d+座$/.test(seat))).slice(0, 3).map((rec: any) => ({
      movie: true, compact: true, poster: data.moviePoster,
      title: [data.movieTitle, rec.seats.join(' · ')].filter(Boolean).join(' · '), detail: `${rec.modelReason || rec.reason || data.preferenceSummary} · ${rec.seats.length}人 · 未锁座`,
      path: `/showtime/${data.showtimeId}/seats?assistantSeats=${encodeURIComponent(rec.seats.join(','))}`, action: '使用此座位方案'
    }))
  }
  if (result.source === 'quote_price') {
    const path = typeof data.orderNo === 'string' ? `/order/pay/${encodeURIComponent(data.orderNo)}`
      : Number.isSafeInteger(data.showtimeId) && data.showtimeId > 0 ? `/showtime/${data.showtimeId}/seats` : ''
    if (!path || !Array.isArray(data.plans)) return []
    return data.plans.slice(0, 3).map((plan: any, index: number) => ({
      movie: true, compact: true, poster: data.moviePoster,
      title: `${index === 0 ? '最低实付方案' : '备选方案 ' + (index + 1)} · 预计 ¥${money(plan.totalPrice)}`,
      detail: `${data.movieTitle ? data.movieTitle + ' · ' : ''}原价 ¥${money(data.originalPrice)} · ${plan.couponName}抵 ¥${money(plan.couponDiscount)} · 使用 ${plan.pointsUsed} 积分抵 ¥${money(plan.pointsDiscount)}`,
      path, action: data.orderNo ? '去支付页确认' : '前往选座'
    }))
  }
  if (result.source === 'get_seats' && data.bookable) return [{ movie: true, compact: true, poster: data.moviePoster, title: `${data.movieTitle || '场次 ' + data.showtimeId} · 余票 ${data.availableSeats}`, detail: '座位尚未锁定，请进入选座页面查看布局', path: `/showtime/${Number(data.showtimeId)}/seats`, action: '查看座位' }]
  const items: Record<string, any>[] = data.items || data.movies || []
  return items.flatMap(item => {
    if (result.source === 'get_my_orders' && typeof item.orderNo === 'string') return [{ movie: true, compact: true, poster: item.moviePoster, title: item.movieTitle || '电影订单', detail: `${item.statusText || '状态待查询'} · ${item.cinemaName || ''} · ¥${item.totalPrice ?? '—'}`, path: `/order/${encodeURIComponent(item.orderNo)}`, action: '查看订单' }]
    if (!Number.isSafeInteger(item.id) || item.id <= 0) return []
    if (result.source === 'search_cinemas') return [{ title: item.name, detail: item.address || '', path: `/cinema/${item.id}`, action: '查看影院' }]
    if (result.source === 'search_showtimes') return [{ movie: true, poster: item.moviePoster, rating: movieRating(item.movieRating), badge: `¥${money(item.basePrice)}`, title: item.movieTitle, detail: [item.cinemaName, item.hallName, item.startTime, item.movieGenre, item.movieDuration != null ? `${item.movieDuration}分钟` : '', `余${item.availableSeats}座`].filter(Boolean).join(' · '), path: `/showtime/${item.id}/seats`, action: '去选座' }]
    if (['search_movies', 'recommend_movies', 'discover_movies'].includes(result.source || '')) return [{ movie: true, poster: item.poster, rating: movieRating(item.rating), badge: item.status === 2 ? '即将上映' : undefined, title: item.title, detail: [item.genre, item.duration ? `${item.duration}分钟` : ''].filter(Boolean).join(' · '), reason: item.reason || item.recommendationReason, path: `/movie/${item.id}`, action: '了解影片' }]
    return []
  })
}
const primaryCards = (item: ChatMessage) => {
  if (checkoutDraft(item)) return []
  const outing = [...(item.results || [])].reverse().find(result => result.ok && result.source === 'plan_movie_outing')
  if (outing) return cards(outing)
  const bookings = ['compare_prices', 'recommend_seats', 'quote_price'].flatMap(source => {
    const result = [...(item.results || [])].reverse().find(r => r.ok && r.source === source)
    return result ? cards(result) : []
  })
  if (bookings.length) return bookings
  const result = [...(item.results || [])].reverse().find(r => r.ok && ['search_movies', 'search_cinemas', 'search_showtimes', 'get_seats', 'get_my_orders', 'recommend_movies', 'discover_movies'].includes(r.source || ''))
  return result ? cards(result).slice(0, 6) : []
}
const bookingNotices = (item: ChatMessage): string[] => [...new Set((item.results || [])
  .filter(r => r.ok && ['compare_prices', 'recommend_seats', 'quote_price', 'plan_movie_outing', 'discover_movies'].includes(r.source || '') && r.data && !Array.isArray(r.data))
  .map(r => r.data && !Array.isArray(r.data) ? r.data.notice : undefined).filter((notice): notice is string => typeof notice === 'string'))]
const scrollToBottom = async () => { await nextTick(); if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight }
const checkoutDraft = (item: ChatMessage): CheckoutDraft | undefined => {
  const result = [...(item.results || [])].reverse().find(r => r.ok && r.source === 'prepare_ticket_order')
  const value = result?.data
  if (!value || Array.isArray(value) || typeof value.draftId !== 'string' || !Array.isArray(value.seats) || !value.bestPlan) return undefined
  return value as CheckoutDraft
}
/** 根据当前观影条件准备确认卡，此时尚未锁座。 */
const prepareCheckout = async (selection: CheckoutSelection) => {
  if (checkoutBusy.value || loading.value || historyBusy.value) return
  if (!userStore.isLoggedIn) { await router.push({ path: '/login', query: { redirect: '/assistant' } }); return }
  checkoutBusy.value = true
  const version = viewVersion, token = userStore.token
  try {
    const value = await assistantApi.prepareCheckout(selection.criteria, selection.seats)
    if (version !== viewVersion || token !== userStore.token) return
    messages.value.push({ role: 'assistant', content: '请核对这张订单确认卡。确认后我会创建待支付订单，带你前往支付页。', source: 'model', complete: true, results: [{ ok: true, source: 'prepare_ticket_order', data: value }] })
    suggestions.value = ['确认下单']
    void scrollToBottom()
  } catch (error: any) {
    if (version === viewVersion && token === userStore.token) messages.value.push({ role: 'assistant', content: error?.response?.data?.message || error?.message || '准备订单失败，请重新查询场次和座位。', source: 'unavailable' })
    void scrollToBottom()
  } finally { checkoutBusy.value = false }
}
/** 提交本人确认卡，取得待支付订单后进入支付流程。 */
const confirmCheckout = async (value: CheckoutDraft) => {
  if (checkoutBusy.value || loading.value || historyBusy.value) return
  if (!userStore.isLoggedIn) { await router.push({ path: '/login', query: { redirect: '/assistant' } }); return }
  checkoutBusy.value = true; checkoutErrors.value[value.draftId] = ''
  const version = viewVersion, token = userStore.token
  try {
    const receipt = await assistantApi.confirmCheckout(value.draftId)
    if (version !== viewVersion || token !== userStore.token) return
    if (!receipt.orderNo || !Number.isInteger(receipt.status)) throw new Error('订单响应未确认，请重试同一确认卡或到“我的订单”查看。')
    const query: Record<string, string> = { pricingPoints: String(receipt.usePoints), pricingCoupons: String(receipt.useCoupons) }
    if (receipt.couponId) query.pricingCouponId = String(receipt.couponId)
    await router.push({ path: receipt.status === 0 ? `/order/pay/${encodeURIComponent(receipt.orderNo)}` : `/order/${encodeURIComponent(receipt.orderNo)}`, query })
  } catch (error: any) {
    if (version === viewVersion && token === userStore.token) checkoutErrors.value[value.draftId] = error?.response?.data?.message || error?.message || '下单结果尚未确认。可重试同一确认卡，或到“我的订单”查看。'
  } finally { checkoutBusy.value = false }
}
const messageId = () => crypto.randomUUID?.() || 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
  const r = crypto.getRandomValues(new Uint8Array(1))[0]! & 15
  return (c === 'x' ? r : (r & 3) | 8).toString(16)
})
/** 发送用户问题并处理流式回答、工具结果与观影条件更新。 */
const send = async (preset?: string, planPatch?: Record<string, unknown>) => {
  const question = (preset ?? draft.value).trim().slice(0, 500)
  if (!question || loading.value || historyBusy.value || checkoutBusy.value) return
  const lastReply = messages.value[messages.value.length - 1]
  const pending = lastReply && checkoutDraft(lastReply)
  if (!planPatch && pending && /^(?:好的[，,\s]*)?(确认下单|确认购买|就按这个下单|确认下单并去支付|就这个[，,]?下单吧|确认[，,]?就这个)[。！!\s]*$/.test(question)) {
    draft.value = ''; await confirmCheckout(pending); return
  }
  recognition?.abort(); stopSpeaking()
  const history = messages.value.filter(m => m.complete).map(m => ({ role: m.role, content: m.content }))
  messages.value.push({ role: 'user', content: question, complete: true })
  messages.value.push({ role: 'assistant', content: '', results: [] })
  const reply = messages.value[messages.value.length - 1]!
  draft.value = ''; suggestions.value = []; loading.value = true
  const activeController = new AbortController()
  controller = activeController
  const timeout = window.setTimeout(() => activeController.abort('timeout'), 185000)
  void scrollToBottom()
  try {
    await assistantApi.stream(question, history, event => {
      if (activeController.signal.aborted) return
      if (event.event === 'conversation') conversationId.value = event.data.id
      // Intermediate text may describe tool calls. Publish only the authoritative final response.
      if (event.event === 'done') { reply.content = event.data.answer; reply.results = event.data.toolResults; reply.source = event.data.source; reply.complete = event.data.source === 'model'; suggestions.value = event.data.suggestions; if (event.data.source === 'model' && event.data.watchPlan?.criteria) watchPlan.value = event.data.watchPlan }
      if (event.event === 'done') void scrollToBottom()
    }, activeController.signal, conversationId.value, messageId(), watchPlan.value, planPatch)
  } catch (error) {
    if (controller !== activeController) return
    const notice = activeController.signal.aborted ? activeController.signal.reason === 'timeout' ? '回答超时，请重试。' : '已停止回答。' : error instanceof Error ? error.message : '连接失败，请重试。'
    reply.content += `${reply.content ? '\n\n' : ''}${notice}`; reply.source = 'unavailable'
    if (!draft.value) draft.value = question
  } finally {
    window.clearTimeout(timeout)
    if (controller === activeController) { loading.value = false; controller = undefined; if (userStore.isLoggedIn) void loadConversations() }
    void scrollToBottom()
  }
}
/** 中止当前助手请求，结束本轮页面加载状态。 */
const stop = () => controller?.abort()
const stopSpeaking = () => { if (canSpeak) window.speechSynthesis.cancel(); speaking.value = false }
const resetChat = () => { viewVersion++; stop(); controller = undefined; loading.value = false; historyBusy.value = false; recognition?.abort(); stopSpeaking(); messages.value = []; suggestions.value = []; draft.value = ''; voiceNotice.value = ''; conversationId.value = undefined; watchPlan.value = { criteria: {}, options: [] }; hasOlderTurns.value = false; firstTurnId.value = undefined }
const savedMessages = (turns: SavedTurn[]): ChatMessage[] => turns.flatMap(turn => [
  { role: 'user', content: turn.question, complete: turn.status === 'complete' && turn.source === 'model', restored: true },
  { role: 'assistant', content: turn.answer || '此回答尚未完成，可稍后刷新历史查看。', results: turn.toolResults, source: turn.source, complete: turn.status === 'complete' && turn.source === 'model', restored: true }
])
/** 恢复选中会话的历史消息与观影条件。 */
const openConversation = async (id: string) => {
  resetChat()
  const version = viewVersion
  historyBusy.value = true; historyError.value = ''
  try {
    const detail = await assistantApi.conversation(id)
    if (version !== viewVersion) return
    conversationId.value = id; messages.value = savedMessages(detail.turns)
    watchPlan.value = detail.watchPlan?.criteria ? detail.watchPlan : { criteria: {}, options: [] }
    hasOlderTurns.value = detail.hasMore; firstTurnId.value = detail.turns[0]?.id
    suggestions.value = detail.turns[detail.turns.length - 1]?.suggestions || []
    void scrollToBottom()
  } catch { if (version === viewVersion) historyError.value = '会话读取失败，请刷新历史重试。' }
  finally { if (version === viewVersion) historyBusy.value = false }
}
/** 分页加载本人的助手会话摘要。 */
const loadConversations = async (restore = false, more = false) => {
  if (!userStore.isLoggedIn) return
  const version = viewVersion
  const page = more ? conversationPage + 1 : 1
  try {
    const result = await assistantApi.conversations(page)
    if (version !== viewVersion) return
    if (!Array.isArray(result.items)) throw new Error('Invalid history response')
    conversations.value = more ? [...conversations.value, ...result.items] : result.items
    hasMoreConversations.value = result.hasMore; conversationPage = page
    historyError.value = ''
    if (restore && result.items[0] && !draft.value && !messages.value.length) await openConversation(result.items[0].id)
  } catch { if (version === viewVersion) historyError.value = '历史列表加载失败，请点击刷新历史；发送成功后的对话仍会保存。' }
}
const refreshHistory = async () => {
  const version = viewVersion
  const id = conversationId.value
  await loadConversations()
  if (version === viewVersion && id && !loading.value) await openConversation(id)
}
/** 按游标加载更早消息，并保持历史阅读位置。 */
const loadOlderTurns = async () => {
  if (!conversationId.value || !firstTurnId.value || historyBusy.value) return
  const version = viewVersion
  historyBusy.value = true
  try {
    const detail = await assistantApi.conversation(conversationId.value, firstTurnId.value)
    if (version !== viewVersion) return
    const previousHeight = messageList.value?.scrollHeight || 0
    messages.value.unshift(...savedMessages(detail.turns))
    firstTurnId.value = detail.turns[0]?.id; hasOlderTurns.value = detail.hasMore
    await nextTick()
    if (messageList.value) messageList.value.scrollTop += messageList.value.scrollHeight - previousHeight
  } catch { if (version === viewVersion) historyError.value = '更早消息读取失败，请重试。' }
  finally { if (version === viewVersion) historyBusy.value = false }
}
/** 删除本人选中的会话并更新侧栏。 */
const deleteConversation = async (id: string) => {
  if (id === conversationId.value) resetChat()
  const version = viewVersion
  historyBusy.value = true
  try {
    await assistantApi.deleteConversation(id)
    if (version !== viewVersion) return
    conversations.value = conversations.value.filter(c => c.id !== id); deleteCandidate.value = ''
  } catch { if (version === viewVersion) historyError.value = '删除失败，请重试。' }
  finally { if (version === viewVersion) historyBusy.value = false }
}
const onKeydown = (event: KeyboardEvent) => { if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); void send() } }
/** 切换语音输入状态，将识别结果交给对话输入框。 */
const toggleVoice = () => {
  if (listening.value) { recognition?.stop(); return }
  if (!RecognitionClass) return
  recognition = new RecognitionClass(); recognition.lang = 'zh-CN'; recognition.interimResults = false
  recognition.onresult = event => { draft.value = (draft.value + (event.results[0]?.[0]?.transcript || '')).slice(0, 500); voiceNotice.value = '已转为文字，请检查后发送。' }
  recognition.onerror = () => { voiceNotice.value = '语音输入不可用，请检查麦克风权限或直接输入文字。'; listening.value = false }
  recognition.onend = () => { listening.value = false }
  try { recognition.start(); listening.value = true; voiceNotice.value = '正在听…语音识别由浏览器提供。' } catch { voiceNotice.value = '无法启动语音输入，请直接输入文字。' }
}
/** 使用浏览器语音朗读助手回答。 */
const speak = (text: string) => { stopSpeaking(); const utterance = new SpeechSynthesisUtterance(text); utterance.lang = 'zh-CN'; utterance.onend = utterance.onerror = () => { speaking.value = false }; speaking.value = true; window.speechSynthesis.speak(utterance) }
watch(() => userStore.token, () => { resetChat(); conversations.value = []; historyError.value = ''; deleteCandidate.value = ''; hasMoreConversations.value = false; void loadConversations(true) })
watch(() => route.query.q, q => { if (typeof q === 'string') { draft.value = q.slice(0, 500); void router.replace({ path: '/assistant' }) } }, { immediate: true })
onMounted(async () => { void loadConversations(true); try { capabilities.value = await assistantApi.capabilities() } catch { capabilitiesFailed.value = true } })
onBeforeUnmount(() => { viewVersion++; stop(); recognition?.abort(); stopSpeaking() })
</script>

<style scoped>
.checkout-start{font:inherit;font-size:13px;color:#80521a;background:#fff2df;border:1px solid #dca961;border-radius:8px;padding:9px 12px;cursor:pointer;white-space:nowrap}.checkout-start:disabled{opacity:.5;cursor:wait}.outing-result .checkout-start{grid-column:2}
.conversation-history{background:#fff;border:1px solid #dfe3d8;border-radius:12px;padding:12px 16px;margin-bottom:16px;font-size:16px}.conversation-history summary{cursor:pointer;font-weight:600;overflow-wrap:anywhere}.history-actions{display:flex;gap:14px;align-items:center;margin:12px 0;color:#596553}.conversation-history button,.older-messages{border:1px solid #dfe3d8;background:#f7f8f4;border-radius:6px;padding:7px 10px;cursor:pointer;font:inherit}.conversation-list{max-height:240px;overflow:auto}.conversation-row{display:flex;gap:8px;align-items:center;border-top:1px solid #edf0e7;padding:6px 0}.conversation-row.active{background:#fff3e3}.conversation-open{flex:1;min-width:0;text-align:left;display:flex;justify-content:space-between;gap:12px}.conversation-open strong{overflow:hidden;text-overflow:ellipsis;white-space:normal;overflow-wrap:anywhere}.conversation-open time{font-size:14px;color:#596553;white-space:nowrap}.history-error{font-size:16px;color:#ad6714;margin-bottom:10px}.older-messages{display:block;margin:0 auto 16px;font-size:16px}@media(max-width:600px){.conversation-open{display:block}.conversation-open time{display:block;margin-top:5px}.conversation-open strong{display:block}}
.query-results summary{cursor:pointer;color:#596553;font-size:14px;padding:10px 0}.primary-results{margin-top:10px}.login-hint{font-size:16px;margin:10px 0;color:#596553}.login-hint a{color:#ad6714}
.assistant-page{display:flex;min-height:100vh;background:#f3f3ef;color:#272c28}.assistant-main{flex:1;min-width:0;padding:32px 36px;max-width:1600px;margin:auto}.page-heading{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:28px}.eyebrow{font-size:14px;font-weight:700;letter-spacing:1.8px;color:#596553;margin-bottom:12px}h1{font-size:32px;letter-spacing:-1px;display:flex;align-items:center;gap:12px}h1 span{font-size:14px;letter-spacing:1px;background:#202621;color:#ff960f;padding:5px 7px;border-radius:5px}.intro{font-size:16px;color:#596553;margin-top:10px}.new-chat{display:flex;align-items:center;gap:8px;background:#fff;border:1px solid #dce0d7;border-radius:10px;padding:11px 14px;font-size:16px;cursor:pointer}.new-chat svg{width:17px}.assistant-workspace{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:22px}.chat-panel{min-width:0;display:flex;flex-direction:column;height:calc(100dvh - 170px);min-height:600px;max-height:1000px;border:1px solid #d9ded4;background:#fafbf8;border-radius:18px;box-shadow:0 8px 30px #303b2910;overflow:hidden}.chat-heading{display:flex;align-items:center;gap:11px;padding:18px 22px;border-bottom:1px solid #e3e6dd;background:#fff}.avatar{width:39px;height:39px;background:#242a25;color:#ff940e;border-radius:12px;display:grid;place-items:center}.avatar svg{width:23px}.chat-heading strong{font-size:16px}.chat-heading p{font-size:14px;color:#596553;margin-top:6px;display:flex;align-items:center;gap:5px}.chat-heading i{width:5px;height:5px;background:#b9bcb5;border-radius:50%}.chat-heading i.ready{background:#44a66a}.live-tag{margin-left:auto;color:#596553;border:1px solid #e1e7dc;border-radius:4px;font-size:14px;padding:4px 7px}.messages{flex:1;min-height:0;overflow:auto;padding:24px;overscroll-behavior:contain}.welcome{text-align:center;padding:18px 0}.welcome-icon{display:grid;place-items:center;background:#fff1df;color:#d87a0b;border-radius:20px;width:62px;height:62px;margin:0 auto 22px}.welcome-icon svg{width:30px;height:30px}.welcome .eyebrow{font-size:14px;color:#596553}.welcome h2{font-size:26px;margin-bottom:12px}.welcome>p:last-of-type{font-size:16px;line-height:1.8;color:#596553}.starters{display:grid;grid-template-columns:1fr 1fr;gap:11px;margin:28px auto 0;max-width:500px}.starters button{text-align:left;background:#fff;border:1px solid #e2e5db;border-radius:12px;padding:16px;cursor:pointer;display:grid;grid-template-columns:24px 1fr;gap:6px 10px;transition:.15s}.starters button:hover{border-color:#eea452;transform:translateY(-2px)}.starters svg{width:20px;color:#c97918;grid-row:span 2}.starters strong{font-size:16px}.starters span{font-size:14px;color:#596553}.message{margin-bottom:23px;max-width:96%;overflow-wrap:anywhere}.message.user{margin-left:auto;max-width:80%}.message-label{font-size:14px;color:#596553;margin-bottom:8px}.user .message-label{text-align:right}.bubble{white-space:pre-wrap;font-size:16px;line-height:1.85;background:#fff;border:1px solid #e8eae2;padding:14px 17px;border-radius:3px 14px 14px}.user .bubble{background:#fff0db;border-color:#f3dfc2;border-radius:14px 3px 14px 14px}.query-results{margin-top:12px}.query-result{margin:7px 0;font-size:16px}.source-line{font-size:14px;color:#648768;display:flex;justify-content:space-between;gap:8px;flex-wrap:wrap;margin:8px 0}.source-line time{color:#596553}.result-card{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:12px;background:#f1f4ec;border-bottom:1px solid #dfe6d7}.result-card strong{font-size:16px}.result-card p{font-size:14px;color:#596553;margin-top:4px;line-height:1.6}.result-card a,.query-result a{font-size:14px;white-space:nowrap;color:#ad6714;text-decoration:none}.result-count{font-size:14px;color:#596553;padding:7px 0}.knowledge-sources{display:flex;gap:6px;flex-wrap:wrap}.knowledge-sources span{font-size:14px;background:#edf0e7;color:#596553;padding:6px 8px;border-radius:4px}.read-aloud{border:0;background:none;font-size:14px;color:#596553;margin-top:8px;cursor:pointer}.progress{font-size:16px;color:#596553;display:flex;align-items:center;gap:8px}.progress span{width:6px;height:6px;border-radius:50%;background:#ee991e;animation:pulse 1s infinite alternate}.suggestions{display:flex;gap:6px;padding:0 20px 10px;overflow:auto}.suggestions button{white-space:nowrap;background:#fff;border:1px solid #e0e5d8;border-radius:20px;padding:7px 10px;font-size:14px;color:#596553;cursor:pointer}.composer{margin:0 18px;border:1px solid #d9dfd1;background:#fff;border-radius:12px;padding:12px}.composer:focus-within{border-color:#e0a050}.composer textarea{width:100%;resize:none;border:0;outline:0;font:inherit;font-size:16px;line-height:1.7;min-height:50px;background:transparent}.composer-bottom{display:flex;align-items:center;justify-content:space-between;gap:8px}.input-actions{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.input-actions button{font-size:14px;background:#f2f4ed;border:0;padding:6px;border-radius:4px;cursor:pointer}.input-actions span{font-size:14px;color:#596553}.send{display:flex;align-items:center;gap:8px;background:#ff920f;border:0;border-radius:8px;padding:10px 14px;white-space:nowrap;font-size:16px;font-weight:600;cursor:pointer}.send svg{width:15px}.send:disabled{opacity:.45;cursor:not-allowed}.voice-notice{font-size:14px;color:#596553;margin-top:8px}.snapshot-note{text-align:center;font-size:14px;color:#596553;padding:12px 8px}.info-card{background:#fff;border:1px solid #dfe3d8;border-radius:14px;padding:24px 21px;margin-bottom:16px}.info-card .eyebrow{font-size:14px}.info-card h2{font-size:17px;margin-bottom:26px}.ability{display:flex;gap:13px;margin-top:24px}.ability>svg{width:21px;color:#89977c}.ability strong{font-size:16px}.ability p{font-size:14px;color:#596553;margin-top:7px;line-height:1.6}.account-card{background:#272e27;color:#fff;border-color:#272e27}.account-card>svg{color:#ff9a21;width:24px;margin-bottom:13px}.account-card h3{font-size:15px}.account-card p{font-size:14px;line-height:1.85;color:#d3dccd;margin:12px 0 20px}.account-card a{font-size:16px;color:#ffad43;text-decoration:none}.help-note{padding:10px 16px;color:#596553}.help-note strong{font-size:14px;color:#596553}.help-note p{font-size:14px;line-height:1.85;margin-top:9px}.mobile-home{display:none}.sr-only{position:absolute;width:1px;height:1px;padding:0;overflow:hidden;clip:rect(0,0,0,0)}button:focus-visible,a:focus-visible{outline:2px solid #dc850f;outline-offset:3px}@keyframes pulse{to{opacity:.3}}@media(min-width:1025px){.assistant-page :deep(.sidebar){flex-shrink:0}}@media(max-width:1250px){.assistant-info{display:none}.assistant-workspace{grid-template-columns:1fr}.assistant-main{padding:26px}}@media(max-width:1024px){.mobile-home{display:block;color:#596553;text-decoration:none;font-size:14px;margin-bottom:14px}.chat-panel{height:calc(100dvh - 210px)}}@media(max-width:600px){.assistant-main{padding:18px 10px;width:100%}.page-heading{padding:0 6px;margin-bottom:18px}.eyebrow{font-size:14px}h1{font-size:25px}.intro{font-size:14px;max-width:240px;line-height:1.7}.new-chat{padding:8px;font-size:14px}.new-chat svg{display:none}.chat-panel{min-height:570px;height:calc(100dvh - 220px);border-radius:13px}.chat-heading{padding:14px}.live-tag{display:none}.messages{padding:15px}.welcome h2{font-size:23px}.starters{gap:8px}.starters button{padding:12px 10px;grid-template-columns:1fr}.starters svg{display:none}.starters span{font-size:14px}.composer{margin:0 10px}.result-card{gap:6px}.result-card a{font-size:14px}.snapshot-note{font-size:14px}}
/* A single conversation column keeps the composer in view at every size. */
.assistant-page{height:100dvh;min-height:0;overflow:hidden}
.assistant-main{height:100%;display:flex;flex-direction:column;gap:16px;padding:24px 32px;margin:0 auto;font-size:16px;line-height:1.6;max-width:1440px}
.page-heading{flex-shrink:0;margin:0;gap:16px}
.page-heading>div{min-width:0}
h1{font-size:28px;line-height:1.3}
.intro{margin-top:6px;line-height:1.6}
.new-chat{white-space:nowrap;flex-shrink:0}
.assistant-workspace{display:flex;flex:1;min-height:0}
.chat-panel{flex:1;height:auto;min-height:0;max-height:none;border-radius:18px;background:#fafbf8}
.chat-heading{padding:14px 24px;flex-shrink:0}
.chat-heading>div{min-width:0;overflow-wrap:anywhere}
.chat-heading p{flex-wrap:wrap;margin-top:2px}
.avatar,.live-tag,.send{flex-shrink:0}
.messages{padding:20px max(24px,calc((100% - 840px)/2));scrollbar-gutter:stable}
.welcome{max-width:760px;margin:0 auto;padding:8px 0 16px}
.welcome-icon{margin-bottom:10px}
.welcome h2{font-size:30px}
.starters{max-width:680px;margin-top:18px;gap:12px}
.starters button{padding:18px;min-width:0;align-content:start}
.starters span{line-height:1.6}
.message{max-width:100%;margin:20px 0 28px}
.message.user{width:fit-content;max-width:85%;margin-left:auto}
.bubble{padding:16px 20px}
.composer{flex-shrink:0;width:calc(100% - 48px);max-width:840px;margin:0 auto;padding:14px 16px;box-shadow:0 4px 18px #303b2908}
button,textarea{font-family:inherit}
.composer textarea{display:block;min-height:56px;color:#272c28}
.composer textarea::placeholder{color:#64705e;opacity:1}
.composer-bottom{margin-top:8px}
.input-actions{min-width:0}
.send{min-height:44px;padding:10px 20px}
.snapshot-note{flex-shrink:0;padding:10px 20px;line-height:1.5}
.suggestions{flex-shrink:0;flex-wrap:wrap;width:100%;max-width:888px;margin:0 auto;max-height:100px;overflow-y:auto;padding:0 24px 12px;gap:8px}
.suggestions button{white-space:normal;text-align:left;overflow-wrap:anywhere}
.result-card{border-radius:10px;margin-top:8px;flex-wrap:wrap;padding:16px}
.result-card>div{min-width:0;flex:1 1 200px;overflow-wrap:anywhere}
.result-card a{white-space:normal;padding:6px 0;font-weight:600}
.read-aloud{padding:6px 0}
.conversation-history{flex-shrink:0;max-height:30%;overflow:auto;margin:0;padding:10px 16px}
.history-actions,.conversation-row{flex-wrap:wrap}
.conversation-open{flex-basis:60%}
.history-error{margin:0}
.assistant-info{display:block;margin:0 0 16px;border-bottom:1px solid #e3e6dd;padding-bottom:12px;color:#596553;font-size:14px}
.assistant-info>summary{cursor:pointer;width:fit-content;padding:4px 0}
.guide-content{display:grid;grid-template-columns:1fr 1fr;gap:16px;padding-top:16px}
.info-card{margin:0;padding:18px}
.info-card .eyebrow{display:none}
.info-card h2{margin-bottom:12px}
.ability{margin-top:12px}
.ability>div{min-width:0}
.help-note{grid-column:1/-1;padding:0 4px 12px}
@media(max-width:1024px){
  .assistant-main{padding:18px 24px;gap:12px}
  .mobile-home{margin-bottom:6px}
}
@media(max-width:600px){
  .assistant-main{padding:12px 10px max(8px,env(safe-area-inset-bottom));gap:10px}
  .page-heading{padding:0 4px;align-items:center}
  h1{font-size:24px;gap:8px}
  .intro{display:none}
  .new-chat{padding:9px 12px}
  .chat-panel{border-radius:14px;min-height:0}
  .chat-heading{padding:12px 14px;gap:10px}
  .messages{padding:12px 14px;scrollbar-gutter:auto}
  .welcome{padding:4px 0 12px}
  .welcome-icon{display:none}
  .welcome h2{font-size:24px;margin-bottom:8px}
  .starters{gap:8px;margin-top:18px}
  .starters button{padding:10px;grid-template-columns:1fr;gap:4px}
  .starters svg{display:none}
  .message.user{max-width:92%}
  .bubble{padding:12px 14px}
  .composer{width:calc(100% - 20px);padding:10px 12px}
  .composer textarea{min-height:52px}
  .composer-bottom{align-items:flex-end;gap:6px}
  .input-actions{gap:4px 8px}
  .send{padding:10px 14px}
  .suggestions{padding:0 10px 8px;max-height:88px}
  .snapshot-note{padding:8px 12px;text-align:left}
  .guide-content{grid-template-columns:1fr}
  .conversation-open{display:block}
  .conversation-open time{display:block}
}
@media(max-height:600px){
  .assistant-main{gap:8px;padding-top:8px;padding-bottom:8px}
  .intro,.snapshot-note{display:none}
  .chat-heading{padding-top:8px;padding-bottom:8px}
}
/* Dedicated chat navigation and a quiet, centered conversation canvas. */
.assistant-page{background:#fff}
.conversation-sidebar{width:280px;flex-shrink:0;display:flex;flex-direction:column;gap:20px;height:100%;padding:24px 16px;background:#f4f5f2;border-right:1px solid #e4e7df;overflow:hidden}
.sidebar-brand{display:flex;align-items:center;justify-content:space-between;padding:0 8px}
.sidebar-brand a{display:flex;align-items:center;gap:10px;color:#272c28;font-size:20px;font-weight:750;text-decoration:none;letter-spacing:1px}
.sidebar-brand svg{width:26px;height:26px;color:#bd6900}
.conversation-sidebar .new-chat{width:100%;justify-content:flex-start;gap:10px;background:#fff;border:1px solid #dfe3d8;padding:12px 16px}
.conversation-history{flex:1;min-height:0;max-height:none;display:flex;flex-direction:column;border:0;border-radius:0;padding:0;background:transparent;overflow:hidden}
.conversation-history h2,.guest-history h2{font-size:14px;color:#626c5d;font-weight:600;padding:0 10px}
.history-actions{justify-content:space-between;gap:8px;margin:10px 0;padding:0 8px;font-size:14px}
.history-actions span{display:none}
.conversation-list{flex:1;min-height:0;max-height:none;overflow-y:auto;padding:2px}
.conversation-row{border:0;border-radius:8px;padding:5px 6px;margin-bottom:2px;gap:4px;align-items:center}
.conversation-row:hover{background:#e9ece5}
.conversation-row.active{background:#e4e8df}
.conversation-history .conversation-open{display:block;flex:1 1 120px;border:0;background:transparent;padding:3px 2px;text-align:left}
.conversation-open strong{display:block;font-size:13px;font-weight:500;line-height:1.5;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.conversation-open time{display:block;margin-top:3px;font-size:11px;line-height:1.4;color:#687261}
.conversation-row>button:not(.conversation-open){font-size:12px;padding:4px;background:transparent;border:0;color:#626c5d}
.guest-history{flex:1;color:#626c5d;font-size:14px;padding:0 8px;line-height:1.8}
.guest-history h2{padding:0}.guest-history p{margin:14px 0}
.guest-history a{color:#955500;text-decoration:none}
.sidebar-links{display:flex;flex-wrap:wrap;gap:10px 16px;border-top:1px solid #dfe3d8;padding:16px 8px 0}
.sidebar-links a{color:#596553;text-decoration:none;font-size:14px}
.assistant-account{display:flex;align-items:center;gap:8px;padding:0 8px;font-size:14px}
.assistant-account a{display:flex;flex:1;min-width:0;gap:8px;align-items:center;color:#272c28;text-decoration:none}
.assistant-account span{overflow-wrap:anywhere}
.assistant-account button{border:0;background:transparent;color:#596553;white-space:nowrap;cursor:pointer;padding:8px 0}
.assistant-main{max-width:none;padding:0;gap:0;background:#fff}
.page-heading{min-height:82px;padding:16px 32px;border-bottom:1px solid #eef0ea;gap:16px}
.heading-title{min-width:0}
h1{font-size:20px;letter-spacing:0;gap:8px}h1 span{font-size:14px;padding:2px 6px}
.active-title{font-size:14px;color:#687261;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;max-width:50vw;margin-top:3px}
.connection-status{margin-left:auto;font-size:14px;color:#596553;display:flex;align-items:center;gap:6px}
.connection-status i{width:6px;height:6px;border-radius:50%;background:#9ba195;flex-shrink:0}.connection-status i.ready{background:#348653}
.chat-panel{border:0;border-radius:0;box-shadow:none;background:#fff}
.messages{padding:24px max(24px,calc((100% - 800px)/2))}
.welcome{padding:clamp(12px,5vh,64px) 0 24px}
.welcome h2{font-size:32px;letter-spacing:-.5px}
.welcome-icon{background:#fff3e2}
.starters{max-width:720px;grid-template-columns:repeat(3,minmax(0,1fr));margin-top:28px}
.starters button{grid-template-columns:1fr;padding:16px;gap:8px;background:#fafbf8}
.starters svg{grid-row:auto;width:22px;height:22px}
.assistant .bubble{border:0;background:transparent;padding:4px 0}
.user .bubble{background:#f1f3ed;border:0;border-radius:18px;padding:12px 18px}
.composer{max-width:800px;border-radius:20px;background:#f8f9f6;box-shadow:none;padding:16px 20px}
.composer textarea{min-height:60px}
.snapshot-note{padding-bottom:16px}
.history-toggle,.sidebar-close{display:none}
.history-error{padding:8px 24px}
@media(max-width:900px){
  .conversation-sidebar{width:240px;padding:20px 12px}
  .page-heading{padding:14px 20px}
  .connection-status{max-width:170px;overflow-wrap:anywhere}
  .starters{grid-template-columns:repeat(2,minmax(0,1fr))}
}
@media(max-width:700px){
  .conversation-sidebar{display:none;width:100%;height:auto;max-height:55dvh;padding:16px;border-right:0;border-bottom:1px solid #dfe3d8;gap:12px}
  .conversation-sidebar.is-open{display:flex}
  .assistant-page{flex-direction:column}
  .assistant-main{min-height:0;flex:1;width:100%}
  .conversation-history{min-height:100px}
  .guest-history{flex:none}
  .sidebar-close{display:block;border:0;background:transparent;font-size:26px;padding:0 8px;cursor:pointer}
  .history-toggle{display:flex;align-items:center;gap:6px;background:transparent;border:1px solid #dfe3d8;border-radius:8px;padding:8px;font-size:14px;white-space:nowrap;color:#596553;cursor:pointer}
  .history-toggle svg{width:18px;height:18px}
  .page-heading{min-height:72px;padding:10px 14px;gap:12px;justify-content:flex-start}
  .connection-status{display:none}
  h1{font-size:18px}.active-title{max-width:45vw}
  .messages{padding:16px}
  .welcome{padding:16px 0}.welcome h2{font-size:26px}
  .starters{margin-top:20px;gap:8px}
  .starters button{padding:12px;gap:6px}.starters svg{display:none}
  .composer{padding:12px 14px;width:calc(100% - 24px)}
  .composer textarea{min-height:52px}
  .snapshot-note{padding:8px 14px max(10px,env(safe-area-inset-bottom))}
}
/* Match the home sidebar's brand, warm gray surfaces and orange accents. */
.conversation-sidebar{
  --sidebar-bg:#e7e8e5;
  --sidebar-border:#cbccc9;
  --sidebar-fg:#666;
  --sidebar-active:#18181b;
  --sidebar-primary:#ff8400;
  background:var(--sidebar-bg);
  border-color:var(--sidebar-border);
  padding:0;
  gap:16px;
  font-family:'Geist',-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif;
}
.sidebar-brand{height:90px;flex-shrink:0;padding:22px 24px;border-bottom:1px solid var(--sidebar-border)}
.sidebar-brand a{gap:14px;letter-spacing:0}
.sidebar-logo{width:42px;height:42px;object-fit:contain;flex-shrink:0}
.sidebar-logo-text{display:flex;flex-direction:column;gap:2px}
.sidebar-logo-text strong{font-family:'JetBrains Mono',ui-monospace,SFMono-Regular,Menlo,monospace;font-size:20px;letter-spacing:1.5px;color:var(--sidebar-active)}
.sidebar-logo-text small{font-size:14px;font-weight:400;color:var(--sidebar-fg)}
.conversation-sidebar .new-chat{width:calc(100% - 24px);margin:0 12px;padding:14px 16px;border:0;border-radius:12px;background:var(--sidebar-primary);color:#111;font-weight:600;min-height:52px}
.conversation-sidebar .new-chat:hover{background:#ed7b00}
.conversation-sidebar .new-chat svg{width:22px;height:22px}
.conversation-history{margin:0 12px;font-size:12px}
.conversation-history .history-actions{gap:6px;margin:6px 0 10px;font-size:11px}
.conversation-history .history-actions button{padding:4px 7px;font-size:12px}
.conversation-history h2{color:var(--sidebar-fg)}
.conversation-history button{border-color:var(--sidebar-border)}
.history-actions button{background:transparent}
.conversation-row:hover{background:#d9dbd6}
.conversation-row.active{background:#fff0db;box-shadow:inset 3px 0 0 var(--sidebar-primary)}
.conversation-open strong{color:var(--sidebar-active)}
.conversation-open time,.conversation-row>button:not(.conversation-open){color:#5d6359}
.guest-history{padding:0 24px;color:var(--sidebar-fg)}
.sidebar-links{display:grid;grid-template-columns:1fr;gap:4px;padding:12px;border-top:1px solid var(--sidebar-border)}
.sidebar-links a{display:flex;align-items:center;gap:14px;min-height:44px;padding:10px 16px;border-radius:12px;font-size:16px;color:var(--sidebar-active)}
.sidebar-links a svg{width:22px;height:22px;color:var(--sidebar-fg)}
.sidebar-links a:hover{background:var(--sidebar-border)}
.assistant-account{padding:18px 20px;border-top:1px solid var(--sidebar-border);gap:10px;flex-shrink:0}
.assistant-account a{gap:12px}
.assistant-account .account-avatar{display:grid;place-items:center;width:40px;height:40px;flex-shrink:0;border-radius:50%;background:linear-gradient(135deg,#ff8400,#ff3f33);color:#fff;font-size:16px;font-weight:700}
.assistant-account .account-avatar.guest{background:var(--sidebar-border);color:var(--sidebar-fg)}
.account-avatar svg{width:22px;height:22px}
.account-info{display:flex;flex-direction:column;min-width:0;gap:2px}
.account-info strong{font-size:14px;color:var(--sidebar-active);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.account-info small{font-size:14px;color:var(--sidebar-fg);line-height:1.5}
.assistant-account .account-login{flex:0;display:block;padding:7px 12px;background:var(--sidebar-primary);border-radius:999px;color:#111;font-weight:600;white-space:nowrap}
@media(max-width:700px){
  .conversation-sidebar{gap:10px}
  .sidebar-brand{height:72px;padding:12px 20px}
  .sidebar-links{display:flex;flex-wrap:wrap;gap:4px;padding:6px 12px}
  .sidebar-links a{font-size:14px;gap:6px;padding:8px;min-height:36px}
  .sidebar-links a svg{width:18px;height:18px}
  .assistant-account{padding:10px 20px}
  .conversation-sidebar .new-chat{min-height:44px;padding:10px 16px}
}
/* Keep concise guidance beside the conversation on wide screens. */
.assistant-workspace{gap:0}
.assistant-guide{flex:0 0 260px;min-width:0;overflow-y:auto;border-left:1px solid #e4e7df;background:#fafbf8;padding:24px 20px;color:#272c28}
.guide-heading{font-size:16px;line-height:1.6;margin-bottom:24px}
.guide-toggle{display:none}
.guide-section h3{font-size:15px;line-height:1.6;font-weight:600;color:#596553}
.assistant-guide .ability{margin-top:20px;gap:10px;align-items:flex-start}
.assistant-guide .ability>svg{width:20px;height:20px;flex-shrink:0;color:#a55f0c;margin-top:3px}
.assistant-guide .ability strong{font-size:15px;line-height:1.6}
.assistant-guide .ability p{font-size:14px;line-height:1.7;margin-top:4px}
.guide-account{margin-top:28px;padding:18px 16px;border:1px solid #eadac2;border-radius:12px;background:#fff4e5}
.guide-account h3{font-size:15px;line-height:1.6}
.guide-account p{margin:8px 0 14px;font-size:14px;line-height:1.7;color:#596553}
.guide-account a{font-size:14px;color:#935300;font-weight:600;text-decoration:none}
.guide-note{margin-top:22px;font-size:14px;line-height:1.8;color:#596553}
.guide-souvenir{position:relative;isolation:isolate;overflow:hidden;margin-top:24px;padding:20px 16px;border:1px solid #6572ae;border-radius:14px;background:radial-gradient(ellipse at 100% 0,#7460ae65,transparent 65%),linear-gradient(145deg,#111e3a,#24203e);color:#e8edff;box-shadow:0 6px 20px #393b6b18}
.guide-souvenir::before{content:'';position:absolute;z-index:-1;inset:0;background:radial-gradient(1px 1px at 12% 15%,#c6e5ff,transparent),radial-gradient(1px 1px at 85% 30%,#d5bdff,transparent),radial-gradient(1px 1px at 63% 8%,#c6e5ff,transparent);background-size:100px 90px;opacity:.7}
.souvenir-mark{display:flex;align-items:center;gap:8px;color:#b8c5ff;font-size:11px;letter-spacing:.5px}.souvenir-mark svg{width:22px;height:22px;color:#bdcaff;filter:drop-shadow(0 0 6px #a4b7ff88)}
.guide-souvenir h3{margin:14px 0 9px;font-size:18px;line-height:1.5;color:#eef1ff}.guide-souvenir p{margin:0;color:#aebcdd;font-size:13px;line-height:1.85}.guide-souvenir a{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:18px;padding:10px 11px;border:1px solid #7f8ac3;border-radius:8px;background:#6876bc35;color:#e7edff;text-decoration:none;font-size:12px;font-weight:600;line-height:1.7}.guide-souvenir a:hover{background:#6876bc60;border-color:#acbaff}.guide-souvenir a:focus-visible{outline:2px solid #b1c7ff;outline-offset:3px}.guide-souvenir a svg{width:17px;height:17px;flex-shrink:0}.guide-souvenir small{display:block;margin-top:12px;color:#9baecc;font-size:10px;line-height:1.7}
@media(min-width:1200px) and (max-width:1450px){.starters{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:1199px){
  .assistant-workspace{flex-direction:column}
  .assistant-guide{order:-1;flex:0 0 auto;max-height:35%;padding:0 20px;border-left:0;border-bottom:1px solid #eef0ea;background:#fafbf8}
  .guide-heading{display:none}
  .guide-toggle{display:flex;justify-content:space-between;align-items:center;gap:8px;width:100%;padding:12px 0;border:0;background:transparent;color:#596553;font-size:14px;text-align:left;cursor:pointer}
  .guide-body{display:none;padding-bottom:16px}
  .guide-body.expanded{display:block}
  .guide-account{margin-top:18px}
  .guide-note{margin-top:16px}
  .assistant-guide .ability{margin-top:12px}
}
/* Film results form a poster shelf; booking and account results remain compact. */
.recommendation-grid{display:grid;gap:14px;grid-template-columns:minmax(0,1fr);margin-top:16px}
.recommendation-grid.has-posters{grid-template-columns:repeat(3,minmax(0,1fr));align-items:stretch}
.recommendation-grid .result-card{margin:0;min-width:0}
.recommendation-grid .movie-result{display:flex;flex-direction:column;align-items:stretch;gap:0;padding:0;background:#fff;border:1px solid #e6e8e1;border-radius:14px;overflow:hidden;box-shadow:0 3px 12px #25301f06;transition:box-shadow .2s,transform .2s}
.movie-result:hover{transform:translateY(-3px);box-shadow:0 8px 22px #25301f12}
.movie-result a.result-poster{position:relative;display:block;padding:0;aspect-ratio:2/3;overflow:hidden;background:#edf0e8;border-radius:0;color:#fff}
.result-poster img{display:block;width:100%;height:100%;object-fit:cover;transition:transform .25s}
.result-poster:hover img{transform:scale(1.035)}
.poster-rating{position:absolute;top:10px;right:10px;display:flex;align-items:center;gap:4px;background:#18221be6;border:1px solid #ffffff24;padding:4px 7px;border-radius:7px;font-size:13px;font-weight:700;color:#fff1b4}
.poster-rating svg{width:14px;height:14px;color:#ffc65c}
.poster-badge{position:absolute;bottom:10px;left:10px;border-radius:6px;background:#fff6e6;padding:4px 8px;font-size:12px;font-weight:700;color:#805018}
.movie-result>div.result-info{flex:1 1 auto;min-width:0;padding:13px 13px 8px}
.movie-result .result-info strong{display:block;font-size:16px;line-height:1.5;color:#242e25}
.movie-result .result-info p{font-size:12px;line-height:1.7;color:#75806e;margin-top:6px}
.movie-result .result-info .movie-reason{color:#53644c;font-size:13px}
.movie-result a.result-action{display:flex;align-items:center;justify-content:space-between;margin:4px 13px 13px;padding:9px 10px;border-radius:8px;background:#f3f5ef;color:#526647;font-size:13px;text-decoration:none;transition:background .2s}
.movie-result a.result-action:hover{background:#e9eddf}
@media(max-width:700px){.recommendation-grid.has-posters{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.movie-result>div.result-info{padding:10px 10px 6px}.movie-result a.result-action{margin:4px 10px 10px}.movie-result .result-info strong{font-size:14px}}
@media(prefers-reduced-motion:reduce){.recommendation-grid .movie-result,.result-poster img{transition:none}.movie-result:hover,.result-poster:hover img{transform:none}}
.recommendation-grid.has-outings{grid-template-columns:minmax(0,1fr)}
.recommendation-grid .outing-result{display:grid;grid-template-columns:116px minmax(0,1fr);align-items:start}
.outing-result a.result-poster{grid-column:1;grid-row:1/3;margin:12px 0 12px 12px;border-radius:8px;align-self:start}
.outing-result .poster-badge{display:none}.outing-result .result-info{grid-column:2}.outing-result .result-info p{white-space:pre-line}
.outing-result a.result-action{grid-column:2;margin-top:2px}.plan-total{display:block;color:#a25a0b;font-size:16px;font-weight:700;margin-top:7px}
@media(max-width:600px){.recommendation-grid .outing-result{grid-template-columns:86px minmax(0,1fr)}.outing-result a.result-poster{margin:10px 0 10px 10px}.outing-result a.result-action{grid-column:1/3}.outing-result .movie-reason{font-size:12px}.outing-result .poster-rating{font-size:11px;padding:3px 5px}.plan-total{font-size:15px}}
</style>


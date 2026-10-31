<template>
  <div class="auth-page" @pointermove="moveLight" @pointerleave="resetLight">
    <div class="ambient-background" aria-hidden="true">
      <div class="ambient-glow ambient-glow-warm"></div>
      <div class="ambient-glow ambient-glow-sage"></div>
      <div ref="cinemaAtmosphere" class="cinema-atmosphere">
        <div class="projector-beam projector-beam-primary"></div>
        <div class="projector-beam projector-beam-secondary"></div>
        <div class="light-orbit light-orbit-outer"></div>
        <div class="light-orbit light-orbit-inner"></div>
        <span v-for="particle in 14" :key="particle" class="film-dust" :style="{
          left: `${(particle * 37) % 100}%`, top: `${(particle * 23) % 95}%`,
          '--drift-time': `${16 + particle % 5 * 3}s`, '--drift-delay': `${-particle * 2.7}s`,
          '--dust-size': `${particle % 3 + 2}px`
        }"></span>
      </div>
      <div ref="pointerLight" class="pointer-light"></div>
    </div>
    <header class="auth-header">
      <RouterLink to="/home" class="brand-lockup" aria-label="响指电影首页">
        <img src="/xiangzhi-ticket.png" alt="" />
        <span class="brand-wordmark" role="img" aria-label="响指电影"></span>
        <span class="brand-caption">让喜欢，即刻开场</span>
      </RouterLink>
      <RouterLink to="/home" class="header-link">先去逛逛 <el-icon><TopRight /></el-icon></RouterLink>
    </header>

    <main class="auth-main">
      <section class="story" aria-labelledby="story-title">
        <div class="overline"><span class="signal"></span> LIFE NEEDS A LITTLE MOVIE MAGIC</div>
        <h1 id="story-title">响指一打，<br /><em>好戏开场。</em></h1>
        <p class="story-copy">把日常按下暂停，把自己交给电影。<br />下一场心动，就从你的专属座位开始。</p>

        <div class="scene auditorium" aria-hidden="true">
          <div class="scene-label">THE XIANGZHI EXPERIENCE</div>
          <span class="scene-index">SCENE / 01</span>
          <div class="projection"></div>
          <div class="movie-screen"><span>好 故 事，值 得 入 座。</span><i></i></div>
          <div class="seat-floor">
            <div v-for="row in 4" :key="row" class="seat-row" :style="{ '--row': row }">
              <span v-for="seat in 9" :key="seat" class="cinema-seat" :class="{ chosen: row === 3 && (seat === 5 || seat === 6) }"></span>
            </div>
          </div>
          <div class="seat-tag"><span></span> 为热爱，留个好位置</div>
          <div class="scene-footer"><span>LIGHTS OFF. WORLD ON.</span><span>✦</span></div>
        </div>
        <div class="story-bottom">
          <p><strong>发现好电影</strong> / 选个好座位 / 收藏小期待</p>
          <span class="frame-counter">01 — YOUR NEXT SCENE</span>
        </div>
      </section>

      <section class="ticket-wrap" aria-labelledby="login-title">
        <div class="ticket-kicker"><span>YOUR TICKET TO THE MOVIES</span><span>NO. 001</span></div>
        <div class="ticket" :class="{ 'is-admitted': admitted }">
          <div class="ticket-top">
            <div class="admit-label"><el-icon><Ticket /></el-icon> ADMIT ONE · 观影通行证</div>
            <h2 id="login-title">{{ purchaseTitle ? '继续你的购票' : '欢迎入座' }}</h2>
            <p class="welcome-copy" :class="{ 'purchase-copy': purchaseTitle }" aria-live="polite">{{ purchaseTitle ? `登录后继续购买《${purchaseTitle}》` : '登录响指电影，继续你的光影旅程。' }}</p>
            <form class="auth-form" @submit.prevent="handleSubmit">
              <div class="field">
                <label for="username">用户名</label>
                <div class="input-row">
                  <el-icon><User /></el-icon>
                  <input id="username" v-model="form.username" placeholder="请输入用户名" autocomplete="username" required />
                </div>
              </div>
              <div class="field">
                <div class="field-head"><label for="password">密码</label><button type="button" class="quiet-button" @click="forgotPassword">忘记密码？</button></div>
                <div class="input-row">
                  <el-icon><Lock /></el-icon>
                  <input id="password" v-model="form.password" :type="showPwd ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" required />
                  <button class="password-toggle" type="button" :aria-label="showPwd ? '隐藏密码' : '显示密码'" :aria-pressed="showPwd" @click="showPwd = !showPwd">
                    <el-icon><View v-if="showPwd" /><Hide v-else /></el-icon>
                  </button>
                </div>
              </div>
              <div class="field">
                <div class="field-head"><label for="captcha">验证码</label><button type="button" class="quiet-button" :disabled="loading || captchaLoading" @click="refreshCaptcha">换一张</button></div>
                <div class="captcha-row">
                  <div class="input-row"><el-icon><CircleCheck /></el-icon><input id="captcha" v-model="captcha" placeholder="输入验证码" maxlength="4" autocomplete="off" autocapitalize="characters" :spellcheck="false" :disabled="loading" aria-describedby="captcha-note" required /></div>
                  <button type="button" class="captcha-image" :disabled="loading || captchaLoading" aria-label="刷新验证码" @click="refreshCaptcha">
                    <img v-if="captchaImage" :src="captchaImage" alt="图形验证码，点击可刷新" />
                    <span v-else>{{ captchaLoading ? '加载中…' : '点击重试' }}</span>
                  </button>
                </div>
                <p id="captcha-note" class="captcha-note" :class="{ 'captcha-error': captchaError }" role="status">{{ captchaError || '不区分大小写 · 点击图片换一张' }}</p>
              </div>
              <button class="submit-button" type="submit" :disabled="loading || captchaLoading || !captchaId" :aria-busy="loading">
                <span>{{ admitted ? '检票通过，欢迎入座' : loading ? '正在检票…' : '登录' }}</span><el-icon><Check v-if="admitted" /><Right v-else /></el-icon>
              </button>
            </form>
            <p class="register-line">第一次来？<RouterLink :to="{ path: '/register', query: route.query }">注册账号，开启第一场 <span aria-hidden="true">↗</span></RouterLink></p>
          </div>
          <div class="ticket-stub">
            <div><p class="stub-title">生活的另一面，在银幕里。</p><p class="stub-subtitle">XIANGZHI CINEMA · ENJOY THE SHOW</p></div>
            <div class="barcode" aria-hidden="true"></div>
            <div v-if="admitted" class="admission-stamp" aria-hidden="true">
              <el-icon><CircleCheck /></el-icon>
              <div><strong>检票通过</strong><span>ADMITTED · ENJOY THE SHOW</span></div>
            </div>
          </div>
        </div>
        <p class="ticket-after" role="status" aria-live="polite">{{ admitted ? '登录成功，正在带你入场…' : '一张电影票，一段属于你的时间。' }}</p>
      </section>
    </main>
    <footer class="auth-footer"><span>© {{ new Date().getFullYear() }} 响指电影 · 每一份热爱，都值得一场电影</span><span class="footer-motto">A SNAP. A TICKET. A NEW WORLD.</span></footer>
  </div>
</template>

<script setup lang="ts">
import { Check, CircleCheck, Hide, Lock, Right, Ticket, TopRight, User, View } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { useUserStore } from '@/stores/user'
import { useLoginCaptcha } from '@/composables/useLoginCaptcha'
import request from '@/utils/request'
import type { Showtime } from '@/types'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { captchaId, captchaImage, captcha, captchaLoading, captchaError, refreshCaptcha } = useLoginCaptcha()
const loading = ref(false)
const admitted = ref(false)
let disposed = false
const pointerLight = ref<HTMLElement | null>(null)
const cinemaAtmosphere = ref<HTMLElement | null>(null)
let pointerMedia: MediaQueryList | undefined
let pointerFrame = 0
let pointerX = 0
let pointerY = 0
const resetLight = () => {
  cancelAnimationFrame(pointerFrame)
  pointerFrame = 0
  pointerLight.value?.style.setProperty('opacity', '0')
  cinemaAtmosphere.value?.style.removeProperty('transform')
}
const moveLight = (event: PointerEvent) => {
  if (!pointerMedia?.matches || event.pointerType !== 'mouse') return
  pointerX = event.clientX
  pointerY = event.clientY
  if (pointerFrame) return
  pointerFrame = requestAnimationFrame(() => {
    pointerFrame = 0
    if (!pointerLight.value) return
    pointerLight.value.style.transform = `translate3d(${pointerX}px, ${pointerY}px, 0)`
    pointerLight.value.style.opacity = '1'
    const x = (pointerX / window.innerWidth - .5) * 20
    const y = (pointerY / window.innerHeight - .5) * 14
    cinemaAtmosphere.value?.style.setProperty('transform', `translate3d(${x}px, ${y}px, 0)`)
  })
}
onMounted(() => {
  pointerMedia = window.matchMedia('(min-width: 761px) and (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)')
  pointerMedia.addEventListener('change', resetLight)
})
const purchaseTitle = ref('')
const destination = computed(() => {
  const redirect = route.query.redirect
  if (typeof redirect !== 'string' || !redirect.startsWith('/') || redirect.startsWith('//') || redirect.includes('\\')) return '/home'
  try {
    const target = new URL(redirect, window.location.origin)
    if (target.origin !== window.location.origin || ['/login', '/register'].includes(target.pathname.replace(/\/$/, ''))) return '/home'
    return target.pathname + target.search + target.hash
  } catch { return '/home' }
})
watch(destination, async (target, _previous, onCleanup) => {
  purchaseTitle.value = ''
  const url = new URL(target, window.location.origin)
  const seatMatch = url.pathname.match(/^\/showtime\/(\d+)\/seats\/?$/)
  const rawId = seatMatch?.[1] || (url.pathname === '/order/confirm' ? url.searchParams.get('showtimeId') : null)
  const showtimeId = Number(rawId)
  if (!rawId || !/^\d+$/.test(rawId) || !Number.isSafeInteger(showtimeId) || showtimeId <= 0) return
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  try {
    // Only public showtime data is needed before authentication.
    const showtime = await request.get<any, Showtime>(`/showtime/${showtimeId}`, { silent: true, signal: controller.signal })
    if (!controller.signal.aborted && !disposed) purchaseTitle.value = showtime.movieTitle?.trim() || ''
  } catch { /* Optional context must never block login. */ }
}, { immediate: true })
let admissionTimer: ReturnType<typeof setTimeout> | undefined
let finishAdmission: (() => void) | undefined
onBeforeUnmount(() => {
  disposed = true
  resetLight()
  pointerMedia?.removeEventListener('change', resetLight)
  clearTimeout(admissionTimer)
  finishAdmission?.()
})
const showPwd = ref(false)
const form = reactive({ username: '', password: '' })
const forgotPassword = () => ElMessage.info('请联系客服重置密码')

const handleSubmit = async () => {
  if (loading.value) return
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (!captchaId.value || !captcha.value.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form.username.trim(), form.password, captchaId.value, captcha.value.trim())
    if (disposed) return
    admitted.value = true
    // Keep feedback brief, and cancel the pending redirect if the user leaves.
    await new Promise<void>((resolve) => {
      finishAdmission = resolve
      admissionTimer = setTimeout(resolve, window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 250 : 850)
    })
    if (disposed) return
    await router.push(destination.value)
  } catch (err) {
    console.error('登录失败:', err)
    if (!disposed) await refreshCaptcha()
  } finally {
    loading.value = false
    admitted.value = false
    finishAdmission = undefined
  }
}
</script>

<style scoped>

.auth-page {
  position: relative; isolation: isolate;
  --paper: #f6f3eb; --ink: #22241f; --orange: #ff6b2b; --muted: #797b72;
  min-height: 100vh; min-height: 100svh; padding: 0 5.2%;
  background: var(--paper); color: var(--ink);
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  display: flex; flex-direction: column; -webkit-font-smoothing: antialiased;
}
.ambient-background { position: fixed; inset: 0; z-index: -1; overflow: hidden; pointer-events: none; mask-image: linear-gradient(to bottom, #000 0%, #000 75%, transparent 100%); }
.ambient-glow { position: absolute; width: min(75vw, 1000px); aspect-ratio: 1; border-radius: 50%; }
.ambient-glow-warm { top: -36%; right: -15%; background: radial-gradient(circle, #efb27830, #efb2780c 45%, transparent 70%); animation: warm-light-drift 22s ease-in-out infinite alternate; }
.ambient-glow-sage { bottom: -46%; left: -18%; background: radial-gradient(circle, #98ae8730, #98ae870a 45%, transparent 70%); animation: sage-light-drift 26s ease-in-out infinite alternate; }
.pointer-light { position: absolute; top: -280px; left: -280px; width: 560px; height: 560px; opacity: 0; background: radial-gradient(circle, #f4b47726, #f4b4770b 40%, transparent 68%); transition: transform .65s cubic-bezier(.2,.65,.3,1), opacity .6s ease; }
.auth-page:focus-within .ambient-glow { animation-play-state: paused; }
.cinema-atmosphere { position: absolute; inset: -24px; transition: transform 1.2s cubic-bezier(.2,.65,.3,1); }
.projector-beam { position: absolute; width: 150vmax; height: 150vmax; top: -75vmax; left: calc(78% - 75vmax); border-radius: 50%; transform-origin: center; }
.projector-beam-primary { background: conic-gradient(from 151deg, transparent 0deg, #e8b57b00 4deg, #e8b57b24 13deg, #fffaf2a6 18deg, #e8b57b18 24deg, transparent 31deg); animation: projector-sweep 24s ease-in-out infinite alternate; }
.projector-beam-secondary { background: conic-gradient(from 183deg, transparent 0deg, #a9b2941c 10deg, #fffdf570 16deg, transparent 27deg); animation: projector-sweep 31s -12s ease-in-out infinite alternate-reverse; }
.light-orbit { position: absolute; right: -190px; top: -320px; width: 690px; height: 690px; border: 1px solid #aa896b1c; border-radius: 50%; animation: orbit-turn 65s linear infinite; }
.light-orbit::after { content: ''; position: absolute; left: 50%; top: -3px; width: 5px; height: 5px; border-radius: 50%; background: #d1935c80; box-shadow: 0 0 16px #e6a06555; }
.light-orbit-inner { width: 550px; height: 550px; top: -250px; right: -120px; border-style: dashed; border-color: #aa896b14; animation-direction: reverse; animation-duration: 90s; }
.film-dust { position: absolute; width: var(--dust-size); height: var(--dust-size); border-radius: 50%; background: #b78856; opacity: .2; animation: dust-drift var(--drift-time) var(--drift-delay) ease-in-out infinite; }
.auth-page:focus-within .cinema-atmosphere > * { animation-play-state: paused; }
@keyframes projector-sweep { from { transform: rotate(-9deg); opacity: .55; } to { transform: rotate(12deg); opacity: .95; } }
@keyframes orbit-turn { to { transform: rotate(360deg); } }
@keyframes dust-drift {
  0%, 100% { transform: translate3d(0, 14px, 0); opacity: 0; }
  25% { opacity: .28; }
  70% { opacity: .16; }
  90% { transform: translate3d(28px, -65px, 0); opacity: 0; }
}
@keyframes warm-light-drift { from { transform: translate3d(0, 0, 0) scale(1); } to { transform: translate3d(-12vw, 12vh, 0) scale(1.12); } }
@keyframes sage-light-drift { from { transform: translate3d(0, 0, 0); } to { transform: translate3d(14vw, -10vh, 0); } }
@media (max-width: 760px), (hover: none), (pointer: coarse), (prefers-reduced-motion: reduce) {
  .ambient-glow { animation: none; }
  .pointer-light { display: none; }
  .cinema-atmosphere { display: none; }
}
.auth-page button, .auth-page input { font: inherit; }
.auth-page button, .auth-page a { -webkit-tap-highlight-color: transparent; }
.auth-page button { cursor: pointer; }
.auth-page a { text-decoration: none; color: inherit; }
.auth-page button:focus-visible, .auth-page a:focus-visible, .auth-page input:focus-visible {
  outline: 3px solid var(--orange); outline-offset: 5px;
}
.auth-header { height: 108px; flex-shrink: 0; display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.brand-lockup { display: flex; align-items: center; gap: 16px; }
.brand-lockup img { width: 43px; height: 43px; object-fit: contain; transform: none; }
.brand-caption { border-left: 1px solid #cccac2; padding-left: 18px; font-size: 12px; color: #78796f; letter-spacing: 2px; }
.header-link { display: inline-flex; align-items: center; gap: 10px; font-size: 13px; }
.header-link:hover { color: #c94a15; }
.header-link .el-icon { font-size: 17px; }
.auth-main { width: 100%; max-width: 1360px; margin: auto; display: grid; grid-template-columns: minmax(0, 1.22fr) minmax(365px, .85fr); gap: clamp(44px, 6.4vw, 104px); align-items: center; padding: 28px 0 48px; }
.story { min-width: 0; }
.overline { display: flex; align-items: center; gap: 10px; font: 10px/1.5 ui-monospace, Consolas, monospace; letter-spacing: 2.4px; }
.signal { width: 6px; height: 6px; background: var(--orange); border-radius: 50%; box-shadow: 0 0 0 4px #ff6b2b16; }
.story h1 { margin: 24px 0 19px; font-size: clamp(44px, 4.35vw, 68px); line-height: 1.22; font-weight: 850; letter-spacing: -2px; }
.story h1 em { color: #e85a20; font-style: normal; position: relative; }
.story-copy { font-size: 14px; line-height: 1.9; color: #7a7b72; }
.scene { position: relative; margin-top: 35px; height: 290px; border-radius: 14px; overflow: hidden; background: #20251f; isolation: isolate; }
.scene-label { position: absolute; top: 22px; left: 23px; z-index: 3; font: 9px ui-monospace, Consolas, monospace; letter-spacing: 2px; color: #b6beb0; }
.scene-label::before { content: ''; display: inline-block; width: 5px; height: 5px; margin-right: 8px; border-radius: 50%; background: var(--orange); }
.scene-index { position: absolute; top: 22px; right: 23px; font: 10px ui-monospace, Consolas, monospace; color: #9ca396; }
.scene-footer { position: absolute; bottom: 18px; left: 23px; right: 23px; display: flex; justify-content: space-between; color: #a8b0a0; font-size: 10px; letter-spacing: 1.4px; z-index: 4; }
.story-bottom { display: flex; justify-content: space-between; align-items: center; margin-top: 20px; gap: 15px; }
.story-bottom p { font-size: 12px; color: #6d7065; }
.story-bottom strong { color: #33382e; font-weight: 600; }
.welcome-copy.purchase-copy { color: #a0522d; overflow-wrap: anywhere; }
.frame-counter { font: 10px ui-monospace, Consolas, monospace; color: #8a8c81; letter-spacing: 1px; }
.ticket-wrap { position: relative; width: 100%; max-width: 460px; justify-self: center; }
.ticket-kicker { display: flex; align-items: center; justify-content: space-between; margin: 0 2px 13px; color: #898a7e; font: 10px ui-monospace, Consolas, monospace; letter-spacing: 1.8px; }
.ticket { position: relative; isolation: isolate; background: linear-gradient(115deg, #fffefb, #faf7ef); border: 1px solid #e3ded1; border-radius: 16px; box-shadow: inset 0 1px 0 #fff, inset 0 -3px 0 #e8e1d34d, 0 22px 50px -18px #443b3029, 0 3px 6px #30322208; }
.ticket::before { content: ''; position: absolute; inset: 0; z-index: -1; border-radius: inherit; pointer-events: none; opacity: .3; background-image: repeating-linear-gradient(0deg, #99886c0a 0 1px, transparent 1px 3px), repeating-linear-gradient(93deg, #99886c09 0 1px, transparent 1px 5px); }
.ticket-top { padding: 32px 35px 26px; }
.admit-label { font: 10px ui-monospace, Consolas, monospace; letter-spacing: 2px; color: #a35531; display: flex; align-items: center; gap: 8px; text-shadow: 0 1px 0 #fff; }
.admit-label .el-icon { font-size: 17px; }
.ticket h2 { margin: 18px 0 10px; font-size: 29px; letter-spacing: -.6px; font-weight: 750; }
.welcome-copy { font-size: 12px; line-height: 1.8; color: #86867b; }
.captcha-row { display: grid; grid-template-columns: minmax(0, 1fr) 120px; gap: 10px; }
.captcha-image { padding: 0; height: 50px; overflow: hidden; border: 1px solid #dfdfd5; border-radius: 7px; background: #f6f3e7; color: #797b72; }
.captcha-image img { display: block; width: 100%; height: 100%; object-fit: contain; }
.captcha-note { margin: 7px 0 0; color: #797b72; font-size: 10px; line-height: 1.6; }
.captcha-error { color: #bd451e; }
.auth-form { display: flex; flex-direction: column; gap: 20px; margin-top: 27px; }
.field label, .field-head label { font-size: 12px; font-weight: 600; }
.field-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 9px; }
.field > label { display: block; margin-bottom: 9px; }
.quiet-button { border: 0; background: none; color: #818277; padding: 0; font-size: 11px !important; }
.quiet-button:hover { color: #bf4718; }
.input-row { display: flex; align-items: center; height: 50px; border: 1px solid #dfdfd5; border-radius: 7px; background: #faf9f4; padding: 0 14px; gap: 10px; transition: border-color .2s, box-shadow .2s; }
.input-row:focus-within { border-color: #e67b43; box-shadow: 0 0 0 3px #ff6b2b0d; background: #fff; }
.input-row > .el-icon { color: #939688; font-size: 17px; flex-shrink: 0; }
.input-row input { border: 0; outline: none !important; width: 100%; min-width: 0; height: 100%; background: transparent; font-size: 13px; color: var(--ink); }
.input-row input::placeholder { color: #a0a195; }
.password-toggle { background: none; border: 0; color: #858879; display: grid; place-items: center; padding: 7px 0 7px 7px; }
.password-toggle .el-icon { font-size: 17px; }
.form-note { color: #828377; font-size: 11px; display: flex; align-items: center; gap: 7px; line-height: 1.7; }
.submit-button { border: 0; width: 100%; height: 51px; border-radius: 7px; background: #ff7134; color: #291b13; padding: 0 18px; display: flex; align-items: center; justify-content: space-between; font-size: 14px !important; font-weight: 650 !important; transition: background .2s, transform .2s, box-shadow .2s; }
.submit-button:hover { background: #ff8148; box-shadow: 0 6px 18px #ed6a2826; transform: translateY(-1px); }
.submit-button:disabled { cursor: wait; opacity: .65; transform: none; }
.submit-button .el-icon { font-size: 20px; }
.register-line { text-align: center; margin-top: 23px; font-size: 12px; color: #8a8b80; }
.register-line a { color: #be481b; font-weight: 600; padding: 6px; }
.register-line a:hover { text-decoration: underline; }
.ticket-stub { position: relative; padding: 23px 35px; border-top: 1px dashed #cec6b6; box-shadow: inset 0 1px 0 #fff; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.is-admitted .ticket-stub > :not(.admission-stamp) { visibility: hidden; }
.admission-stamp { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%) rotate(-6deg); display: flex; align-items: center; gap: 10px; width: max-content; padding: 8px 17px; border: 2px solid #437254; outline: 1px solid #43725470; outline-offset: -5px; border-radius: 5px; color: #386447; animation: stamp-admission .32s ease-out both; }
.admission-stamp > .el-icon { font-size: 28px; }
.admission-stamp strong { display: block; font-size: 21px; line-height: 1.3; letter-spacing: 5px; }
.admission-stamp span { display: block; margin-top: 3px; font: 8px/1.4 ui-monospace, Consolas, monospace; letter-spacing: 1px; }
.is-admitted .submit-button { background: #e4ecdf; color: #31583e; opacity: 1; }
@keyframes stamp-admission {
  from { opacity: 0; transform: translate(-50%, -50%) rotate(-10deg) scale(1.22); }
  to { opacity: 1; transform: translate(-50%, -50%) rotate(-6deg) scale(1); }
}
.ticket-stub::before, .ticket-stub::after { content: ''; position: absolute; width: 22px; height: 22px; border: 1px solid #e3e0d6; border-radius: 50%; background: var(--paper); top: -12px; }
.ticket-stub::before { left: -12px; clip-path: inset(0 0 0 50%); }
.ticket-stub::after { right: -12px; clip-path: inset(0 50% 0 0); }
.stub-title { font-size: 12px; font-weight: 600; }
.stub-subtitle { font: 8px ui-monospace, Consolas, monospace; letter-spacing: 1.5px; color: #929486; margin-top: 7px; }
.barcode { width: 93px; height: 28px; opacity: .65; background: repeating-linear-gradient(90deg,#39402e 0 1px,transparent 1px 3px,#39402e 3px 6px,transparent 6px 8px,#39402e 8px 9px,transparent 9px 13px,#39402e 13px 15px,transparent 15px 17px); }
.ticket-after { margin-top: 19px; text-align: center; color: #8c8d81; font-size: 10px; letter-spacing: .5px; }
.auth-footer { min-height: 64px; display: flex; align-items: center; justify-content: space-between; gap: 20px; border-top: 1px solid #ddded2; color: #929386; font-size: 10px; letter-spacing: 1px; }
.footer-motto { padding-right: 100px; font: 9px ui-monospace, Consolas, monospace; letter-spacing: 2px; }
@media (min-width: 1600px) { .auth-header, .auth-footer { width: 100%; max-width: 1540px; margin-inline: auto; } .scene { height: 330px; } }
@media (max-width: 1050px) { .auth-page { padding-inline: 4%; } .auth-main { gap: 36px; grid-template-columns: minmax(0,1fr) minmax(340px,.95fr); } .story h1 { font-size: 45px; } .ticket-top { padding: 28px; } .ticket-stub { padding-inline: 28px; } .scene { height: 255px; } .frame-counter { display: none; } }
@media (max-width: 760px) {
  .auth-page { padding-inline: 24px; }
  .auth-header { height: 85px; } .brand-caption { display: none; } .brand-lockup { gap: 9px; } .brand-lockup img { width: 34px; height: 34px; }
  .auth-main { grid-template-columns: 1fr; gap: 30px; max-width: 460px; padding: 18px 0 40px; }
  .story h1 { font-size: 42px; margin-top: 16px; margin-bottom: 12px; } .story h1 br { display: none; }
  .story-copy { font-size: 12px; } .scene, .story-bottom { display: none; }
  .overline { font-size: 9px; letter-spacing: 1.8px; }
  .ticket-top { padding: 28px; } .ticket-kicker { font-size: 9px; }
  .auth-footer { padding: 17px 0 75px; align-items: flex-start; min-height: 0; } .footer-motto { display: none; }
}
@media (max-width: 390px) {
  .auth-page { padding-inline: 18px; } .brand-lockup .brand-wordmark { width: 106px; height: 25px; } .header-link { font-size: 11px; }
  .story h1 { font-size: 35px; } .ticket-top { padding: 24px; } .ticket-stub { padding-inline: 24px; } .ticket h2 { font-size: 26px; }
}
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation: none !important; transition: none !important; } }

.auditorium { background: radial-gradient(ellipse at 50% 0%, #4d58443d, transparent 70%), #1f241e; }
.projection { position: absolute; top: 65px; left: 4%; width: 92%; height: 180px; clip-path: polygon(18% 0, 82% 0, 100% 100%, 0 100%); background: linear-gradient(#dce6b717, transparent); }
.movie-screen { position: absolute; top: 59px; left: 19%; width: 62%; height: 60px; border-top: 3px solid #e6eccb; border-radius: 50% / 18%; background: linear-gradient(#dbe7b719, #dbe7b700); text-align: center; padding-top: 18px; color: #d8dec6; font-size: 11px; letter-spacing: 4px; box-shadow: 0 -8px 32px -13px #e9f7c9; }
.movie-screen i { display: block; width: 4px; height: 4px; border-radius: 50%; background: #b7c39b; margin: 10px auto; }
.seat-floor { position: absolute; top: 117px; left: 50%; transform: translateX(-50%); width: 83%; perspective: 450px; }
.seat-row { display: flex; justify-content: center; gap: 10px; margin-top: 9px; transform: scale(calc(.65 + var(--row) * .085)); }
.cinema-seat { position: relative; height: 23px; width: 29px; border-radius: 7px 7px 3px 3px; border: 1px solid #65705b; background: linear-gradient(145deg, #4b5744, #30392d); box-shadow: 0 5px 0 -1px #161b15, 0 7px 6px #0004; }
.cinema-seat::after { content: ''; position: absolute; left: -3px; right: -3px; bottom: -2px; height: 7px; border: 1px solid #56614c; background: #343d2f; border-radius: 3px; }
.cinema-seat:nth-child(3) { margin-right: 14px; }
.cinema-seat:nth-child(6) { margin-right: 14px; }
.cinema-seat.chosen { border-color: #ffb77d; background: linear-gradient(135deg, #ff9f57, #ef6425); box-shadow: 0 0 24px #ff772d4d; }
.cinema-seat.chosen::after { background: #e2672d; border-color: #ffad71; }
.seat-tag { position: absolute; right: 20px; bottom: 62px; border: 1px solid #76826750; border-radius: 4px; padding: 8px 10px; font-size: 9px; color: #e0e2d3; background: #353d2be8; transform: rotate(-5deg); }
.seat-tag span { display: inline-block; width: 5px; height: 5px; border-radius: 50%; background: #ff8a42; margin-right: 5px; }
.movie-screen { animation: screen-light-up .8s ease-out both; }
.projection { animation: projector-light-up .8s .08s ease-out both; }
.seat-row { animation: seats-emerge .65s ease-out both; animation-delay: calc(var(--row) * 40ms + 100ms); }
.cinema-seat.chosen { animation: reserved-seat-glow .8s .25s ease-out both; }
@keyframes screen-light-up {
  from { opacity: .12; filter: brightness(.35); }
  to { opacity: 1; filter: brightness(1); }
}
@keyframes projector-light-up { from { opacity: 0; } to { opacity: 1; } }
@keyframes seats-emerge { from { opacity: .15; } to { opacity: 1; } }
@keyframes reserved-seat-glow {
  from { box-shadow: 0 0 0 #ff772d00; filter: brightness(.65); }
  65% { box-shadow: 0 0 30px #ff772d66; filter: brightness(1.1); }
  to { box-shadow: 0 0 24px #ff772d4d; filter: brightness(1); }
}
@media (min-width: 1600px) { .seat-floor { top: 142px; } .movie-screen { top: 70px; } .seat-row { margin-top: 13px; } }
@media (max-width: 1050px) { .seat-floor { top: 99px; width: 90%; } .seat-row { gap: 6px; } .cinema-seat { width: 25px; height: 20px; } .movie-screen { top: 53px; font-size: 9px; letter-spacing: 1px; } .seat-tag { bottom: 42px; right: 10px; } }
</style>

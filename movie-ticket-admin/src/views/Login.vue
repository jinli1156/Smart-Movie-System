<template>
  <div class="auth-page admin-auth" @pointermove="moveLight" @pointerleave="resetLight">
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
      <div class="brand-lockup">
        <img src="/xiangzhi-ticket.png" alt="" />
        <span class="brand-wordmark" role="img" aria-label="响指电影"></span>
        <span class="brand-caption">管理工作台</span>
      </div>
      <span class="header-status"><span class="signal"></span> 幕后有序，台前精彩</span>
    </header>

    <main class="auth-main">
      <section class="story" aria-labelledby="story-title">
        <div class="overline"><span class="signal"></span> GREAT STORIES START BEHIND THE SCENES</div>
        <h1 id="story-title">好戏背后，<br /><em>由你掌镜。</em></h1>
        <p class="story-copy">从第一场排片，到最后一张检票。<br />让每一场热爱，都能准时开场。</p>
        <div class="scene backstage" aria-hidden="true">
          <div class="scene-label">XIANGZHI / CONTROL ROOM</div><span class="scene-index">SCENE / 02</span>
          <div class="schedule-board">
            <div class="board-heading"><span><i></i> 放映计划</span><span>PROGRAMMING</span></div>
            <div class="timeline"><span></span><span>14:00</span><span>16:00</span><span>18:00</span><span>20:00</span></div>
            <div v-for="(hall, index) in halls" :key="hall" class="schedule-row">
              <span class="hall-name">{{ hall }}</span>
              <div class="track"><span class="show-block" :class="'show-' + index"><i></i>{{ ['光影序章', '心动时刻', '好戏开场'][index] }}</span><span class="show-block next-block"></span></div>
            </div>
            <div class="playhead"><span></span></div>
          </div>
          <div class="backstage-pass"><el-icon><VideoCamera /></el-icon><span>每一幕精彩<br /><strong>都有你在场</strong></span><span class="pass-star">✦</span></div>
          <div class="scene-footer"><span>YOU MAKE THE MAGIC HAPPEN.</span><span>✦</span></div>
        </div>
        <div class="story-bottom"><p><strong>影院管理</strong> / 智能排片 / 经营洞察</p><span class="frame-counter">02 — BEHIND THE SCENES</span></div>
      </section>

      <section class="ticket-wrap" aria-labelledby="login-title">
        <div class="ticket-kicker"><span>THE PEOPLE BEHIND THE PICTURE</span><span>STAFF</span></div>
        <div class="ticket" :class="{ 'is-admitted': admitted }">
          <div class="ticket-top">
            <div class="admit-label"><el-icon><Key /></el-icon> BACKSTAGE PASS · 幕后通行证</div>
            <h2 id="login-title">欢迎回到幕后</h2>
            <p class="welcome-copy">使用管理员账号，进入响指电影管理工作台。</p>
            <form class="auth-form" @submit.prevent="handleLogin">
              <div class="field">
                <label for="username">管理员账号</label>
                <div class="input-row"><el-icon><User /></el-icon><input id="username" v-model="form.username" placeholder="请输入管理员账号" autocomplete="username" required /></div>
              </div>
              <div class="field">
                <div class="field-head"><label for="password">密码</label><button type="button" class="quiet-button" @click="showHelp">忘记密码？</button></div>
                <div class="input-row">
                  <el-icon><Lock /></el-icon>
                  <input id="password" v-model="form.password" :type="showPwd ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" required />
                  <button type="button" class="password-toggle" :aria-label="showPwd ? '隐藏密码' : '显示密码'" :aria-pressed="showPwd" @click="showPwd = !showPwd"><el-icon><View v-if="showPwd" /><Hide v-else /></el-icon></button>
                </div>
              </div>
              <div class="field">
                <div class="field-head"><label for="captcha">验证码</label><button type="button" class="quiet-button" :disabled="loading || captchaLoading" @click="rollCaptcha">换一张 <span aria-hidden="true">↻</span></button></div>
                <div class="captcha-row">
                  <div class="input-row"><el-icon><CircleCheck /></el-icon><input id="captcha" v-model="captcha" placeholder="请输入验证码" maxlength="4" autocomplete="off" spellcheck="false" required /></div>
                  <button type="button" class="captcha-img" :disabled="loading || captchaLoading" aria-label="刷新验证码" @click="rollCaptcha"><img v-if="captchaImage" :src="captchaImage" alt="图形验证码，点击可刷新" /><span v-else>{{ captchaLoading ? '加载中…' : '点击重试' }}</span></button>
                </div>
              </div>
              <p v-if="captchaError" class="captcha-error" role="status">{{ captchaError }}</p>
              <button class="submit-button" type="submit" :disabled="loading || captchaLoading || !captchaId" :aria-busy="loading"><span>{{ admitted ? '验证通过，欢迎进入' : loading ? '正在验证…' : '登录控制台' }}</span><el-icon><Check v-if="admitted" /><Right v-else /></el-icon></button>
              <p class="form-note"><el-icon><Lock /></el-icon> 仅限授权管理员与影院工作人员使用</p>
            </form>
          </div>
          <div class="ticket-stub">
            <div><p class="stub-title">你的工作台，每一场的起点。</p><p class="stub-subtitle">XIANGZHI CINEMA · STAFF ACCESS</p></div><div class="barcode" aria-hidden="true"></div>
            <div v-if="admitted" class="admission-stamp" aria-hidden="true">
              <el-icon><CircleCheck /></el-icon>
              <div><strong>验证通过</strong><span>VERIFIED · STAFF ACCESS</span></div>
            </div>
          </div>
        </div>
        <p v-if="admitted" class="ticket-after" role="status">登录成功，正在进入管理工作台…</p>
        <p v-else class="ticket-after">账号或权限遇到问题？<button type="button" class="quiet-button" @click="showHelp">联系平台管理员 <span aria-hidden="true">↗</span></button></p>
      </section>
    </main>
    <footer class="auth-footer"><span>© {{ new Date().getFullYear() }} 响指电影 · 管理工作台</span><span class="footer-motto">EVERY GREAT SHOW HAS A GREAT CREW.</span></footer>
  </div>
</template>

<script setup lang="ts">
/**
 * 后台登录：验证账号与验证码，并检查用户是否具有管理权限。
 */
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { Check, CircleCheck, Hide, Key, Lock, Right, User, VideoCamera, View } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useLoginCaptcha } from '@/composables/useLoginCaptcha'

const router = useRouter()
const userStore = useUserStore()
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
const halls = ['01 厅', '02 厅', '03 厅']
const { captchaId, captchaImage, captcha, captchaLoading, captchaError, refreshCaptcha: rollCaptcha } = useLoginCaptcha()
const showHelp = () => ElMessage.info('请联系为你分配账号的平台管理员，协助重置密码或开通权限。')

/** 提交账号、密码和验证码，登录成功后进入管理页面。 */
const handleLogin = async () => {
  if (loading.value) return
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入管理员账号和密码')
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
    if (userStore.userInfo?.role === 'USER') {
      ElMessage.error('权限不足，请使用管理员账号登录')
      userStore.logout()
      await rollCaptcha()
      return
    }
    if (disposed) return
    admitted.value = true
    await new Promise<void>((resolve) => {
      finishAdmission = resolve
      admissionTimer = setTimeout(resolve, window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 250 : 850)
    })
    if (disposed) return
    await router.push('/dashboard')
  } catch (error) {
    console.error('登录失败:', error)
    if (!disposed) await rollCaptcha()
  } finally {
    loading.value = false
    admitted.value = false
    finishAdmission = undefined
  }
}
</script>

<style scoped>
.auth-page .captcha-img { height: 50px; min-height: 0; padding: 0; overflow: hidden; }
.captcha-img img { display: block; width: 100%; height: 100%; object-fit: contain; }
.auth-page .captcha-img > span { font: 12px/1.5 "Microsoft YaHei", sans-serif; letter-spacing: 0; }
.captcha-error { font-size: 11px; color: #bd451e; margin: 0; }

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

.admin-auth .auth-main { padding-top: 16px; padding-bottom: 34px; }
.admin-auth .story h1 em { color: #58634a; }
.header-status { display: flex; align-items: center; gap: 10px; font-size: 11px; color: #7c8072; letter-spacing: 1px; }
.admin-auth .auth-form { gap: 18px; margin-top: 23px; }
.admin-auth .ticket-top { padding-top: 28px; padding-bottom: 23px; }
.admin-auth .admit-label { color: #6a765b; }
.admin-auth .submit-button { background: #303c2d; color: #fff8ec; }
.admin-auth .submit-button:hover { background: #44533c; box-shadow: 0 6px 18px #303c2d22; }
.admin-auth .ticket-after .quiet-button { color: #526147; font-size: 10px !important; }
.captcha-row { display: flex; gap: 10px; }
.captcha-row .input-row { flex: 1; min-width: 0; }
.captcha-img { width: 108px; flex-shrink: 0; border: 1px solid #d9dece; border-radius: 7px; display: flex; align-items: center; justify-content: center; gap: 6px; color: #536448; background: repeating-linear-gradient(155deg, transparent 0 8px, #a8b29338 8px 9px), #edf0e3; font: italic 22px Georgia, serif !important; letter-spacing: 1px; }
.backstage { background-color: #263024; background-image: linear-gradient(#cedec107 1px, transparent 1px), linear-gradient(90deg, #cedec107 1px, transparent 1px); background-size: 24px 24px; }
.schedule-board { position: absolute; top: 57px; left: 27px; right: 43px; border: 1px solid #65765c66; border-radius: 8px; padding: 17px 18px 14px; background: #303b2b; transform: perspective(900px) rotateY(-8deg) rotateZ(-3deg); box-shadow: 14px 15px 24px #0d160e44; }
.board-heading { display: flex; align-items: center; justify-content: space-between; color: #e6ebdd; font-size: 11px; padding-bottom: 13px; border-bottom: 1px solid #d5dfbe13; }
.board-heading span:last-child { font: 8px ui-monospace, Consolas, monospace; letter-spacing: 1px; color: #9cab8e; }
.board-heading i { display: inline-block; width: 6px; height: 6px; border-radius: 2px; background: #e8904c; margin-right: 6px; }
.timeline { display: grid; grid-template-columns: 48px repeat(4, 1fr); font: 8px ui-monospace, Consolas, monospace; color: #91a083; margin: 12px 0; }
.schedule-row { display: flex; align-items: center; gap: 10px; margin-top: 9px; }
.hall-name { width: 38px; flex-shrink: 0; font-size: 9px; color: #aebba0; }
.track { height: 24px; flex: 1; display: flex; gap: 12px; background: repeating-linear-gradient(90deg, #d4e4bf0a 0 1px, transparent 1px 25%); }
.show-block { display: flex; align-items: center; gap: 7px; width: 47%; background: #657655; border: 1px solid #94aa7966; color: #e5edda; border-radius: 4px; font-size: 9px; padding: 0 9px; white-space: nowrap; }
.show-block i { width: 3px; height: 10px; background: #c3d6a8; border-radius: 1px; }
.show-1 { margin-left: 13%; background: #c7753c; border-color: #eea771; color: #fff3df; }
.show-1 i { background: #ffdfaa; }
.show-2 { width: 38%; margin-left: 5%; }
.next-block { width: 24%; opacity: .4; }
.playhead { position: absolute; left: 62%; top: 53px; bottom: 12px; width: 1px; background: #f1bb7e; }
.playhead span { position: absolute; width: 7px; height: 7px; left: -3px; top: -2px; background: #f1bb7e; clip-path: polygon(0 0,100% 0,50% 100%); }
.backstage-pass { position: absolute; right: 18px; bottom: 43px; display: flex; align-items: center; gap: 12px; padding: 14px 17px; border: 1px solid #ead6ac; border-radius: 6px; color: #42492f; background: #f1e6ca; transform: rotate(7deg); box-shadow: 0 6px 15px #111a1133; }
.backstage-pass > .el-icon { font-size: 23px; }
.backstage-pass > span { font-size: 9px; line-height: 1.8; }
.backstage-pass strong { font-size: 12px; }
.backstage-pass .pass-star { margin-left: 7px; font-size: 28px; color: #c17842; }
@media (min-width: 1600px) { .schedule-board { top: 70px; } .schedule-row { margin-top: 14px; } }
@media (max-width: 1050px) { .schedule-board { left: 18px; right: 24px; top: 48px; padding: 14px; } .backstage-pass { bottom: 32px; transform: rotate(5deg) scale(.85); transform-origin: right bottom; } .show-block { font-size: 8px; padding: 0 5px; } }
@media (max-width: 760px) { .header-status { font-size: 10px; letter-spacing: 0; } .header-status .signal { display: none; } .admin-auth .auth-main { gap: 28px; padding-bottom: 40px; } }
@media (max-width: 390px) { .header-status { font-size: 9px; } .captcha-img { width: 93px; } .captcha-row { gap: 8px; } }
.schedule-board { animation: backstage-reveal .8s ease-out both; }
@keyframes backstage-reveal { from { opacity: .15; filter: brightness(.6); } to { opacity: 1; filter: brightness(1); } }
</style>

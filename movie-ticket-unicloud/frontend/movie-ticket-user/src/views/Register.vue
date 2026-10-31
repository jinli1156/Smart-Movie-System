<template>
  <div class="auth-page register-page">
    <header class="auth-header">
      <RouterLink to="/home" class="brand-lockup" aria-label="响指电影首页">
        <img src="/xiangzhi-ticket.png" alt="" />
        <span class="brand-wordmark" role="img" aria-label="响指电影"></span>
        <span class="brand-caption">让喜欢，即刻开场</span>
      </RouterLink>
      <RouterLink :to="{ path: '/login', query: route.query }" class="header-link">返回登录 <el-icon><TopRight /></el-icon></RouterLink>
    </header>

    <main class="auth-main">
      <section class="story" aria-labelledby="story-title">
        <div class="overline"><span class="signal"></span> EVERY MOVIE LOVER STARTS SOMEWHERE</div>
        <h1 id="story-title">初次见面，<br /><em>即刻入戏。</em></h1>
        <p class="story-copy">为喜欢的故事，留一个自己的位置。<br />从今天起，让每一次响指，都有好戏回应。</p>
        <div class="scene first-scene" aria-hidden="true">
          <div class="scene-label">THE BEGINNING OF YOUR STORY</div>
          <span class="scene-index">TAKE / 01</span>
          <div class="scene-orbit orbit-one"></div><div class="scene-orbit orbit-two"></div>
          <div class="souvenir-ticket ticket-back">
            <span class="souvenir-overline">XIANGZHI CINEMA</span>
            <strong>下一场，<br />有你。</strong><span class="back-star">✦</span>
          </div>
          <div class="souvenir-ticket ticket-front">
            <div class="souvenir-main">
              <span class="souvenir-overline">A NEW STORY AWAITS</span>
              <strong>人生如戏<br /><em>你是主角</em></strong>
              <span class="souvenir-caption">响指电影 · 影迷入场纪念</span>
            </div>
            <div class="souvenir-stub"><span>ADMIT ONE</span><div class="barcode"></div><b>01</b></div>
          </div>
          <div class="welcome-stamp">HELLO,<br /><strong>MOVIE LOVER</strong><span>✦</span></div>
          <div class="scene-footer"><span>YOUR FIRST FRAME STARTS HERE.</span><span>✦</span></div>
        </div>
        <div class="story-bottom">
          <p><strong>遇见好故事</strong> / 收藏心动 / 开启你的电影清单</p>
          <span class="frame-counter">00 — A NEW BEGINNING</span>
        </div>
      </section>

      <section class="ticket-wrap" aria-labelledby="register-title">
        <div class="ticket-kicker"><span>YOUR FIRST TICKET STARTS HERE</span><span>NEW</span></div>
        <div class="ticket">
          <div class="ticket-top">
            <div class="admit-label"><el-icon><Ticket /></el-icon> FIRST TAKE · 影迷初登场</div>
            <h2 id="register-title">领一张入场券</h2>
            <p class="welcome-copy">创建你的响指账号，把喜欢变成一场电影。</p>
            <form class="auth-form" @submit.prevent="handleRegister">
              <div class="field">
                <div class="field-head"><label for="register-username">用户名</label><span class="field-hint">4–20 位</span></div>
                <div class="input-row">
                  <el-icon><User /></el-icon>
                  <input id="register-username" v-model="form.username" placeholder="4-20 位字母、数字或下划线" autocomplete="username" minlength="4" maxlength="20" required />
                </div>
              </div>
              <div class="field">
                <div class="field-head"><label for="register-password">密码</label><span class="field-hint">6–20 位</span></div>
                <div class="input-row">
                  <el-icon><Lock /></el-icon>
                  <input id="register-password" v-model="form.password" :type="showPwd ? 'text' : 'password'" placeholder="请设置登录密码" autocomplete="new-password" minlength="6" maxlength="20" required />
                  <button type="button" class="password-toggle" :aria-label="showPwd ? '隐藏密码' : '显示密码'" :aria-pressed="showPwd" @click="showPwd = !showPwd"><el-icon><View v-if="showPwd" /><Hide v-else /></el-icon></button>
                </div>
              </div>
              <div class="field">
                <div class="field-head">
                  <label for="register-confirm">确认密码</label>
                  <span id="confirm-status" class="field-hint" :class="confirmStatus" aria-live="polite">{{ confirmStatus === 'mismatch' ? '两次密码不一致' : confirmStatus === 'match' ? '两次密码一致' : '再次确认' }}</span>
                </div>
                <div class="input-row" :class="{ 'input-error': confirmStatus === 'mismatch' }">
                  <el-icon><Lock /></el-icon>
                  <input id="register-confirm" v-model="form.confirmPassword" :type="showConfirmPwd ? 'text' : 'password'" placeholder="请再次输入密码" autocomplete="new-password" maxlength="20" :aria-invalid="confirmStatus === 'mismatch'" aria-describedby="confirm-status" required />
                  <button type="button" class="password-toggle" :aria-label="showConfirmPwd ? '隐藏确认密码' : '显示确认密码'" :aria-pressed="showConfirmPwd" @click="showConfirmPwd = !showConfirmPwd"><el-icon><View v-if="showConfirmPwd" /><Hide v-else /></el-icon></button>
                </div>
              </div>
              <div class="profile-fields">
                <div class="field">
                  <label for="register-nickname">昵称</label>
                  <div class="input-row"><el-icon><Avatar /></el-icon><input id="register-nickname" v-model="form.nickname" placeholder="你的影迷昵称" maxlength="20" autocomplete="nickname" required /></div>
                </div>
                <div class="field">
                  <div class="field-head"><label for="register-phone">手机号码</label><span class="field-hint">选填</span></div>
                  <div class="input-row"><el-icon><Iphone /></el-icon><input id="register-phone" v-model="form.phone" type="tel" inputmode="numeric" maxlength="11" autocomplete="tel-national" placeholder="11 位手机号" /></div>
                </div>
              </div>
              <label class="agreement">
                <input v-model="agreed" type="checkbox" />
                <span>我已阅读并同意<span class="agreement-titles">《用户协议》《隐私政策》《观影须知》</span></span>
              </label>
              <button class="submit-button" type="submit" :disabled="loading" :aria-busy="loading"><span>{{ loading ? '正在创建账号…' : '立即注册' }}</span><el-icon><Right /></el-icon></button>
            </form>
            <p class="register-line">已经有入场券？<RouterLink :to="{ path: '/login', query: route.query }">去登录 <span aria-hidden="true">↗</span></RouterLink></p>
          </div>
          <div class="ticket-stub">
            <div><p class="stub-title">第一场心动，从这里开始。</p><p class="stub-subtitle">XIANGZHI CINEMA · THE FIRST TAKE</p></div>
            <div class="barcode" aria-hidden="true"></div>
          </div>
        </div>
        <p class="ticket-after">欢迎加入，把日常过成值得回味的片段。</p>
      </section>
    </main>
    <footer class="auth-footer"><span>© {{ new Date().getFullYear() }} 响指电影 · 每一份热爱，都值得一场电影</span><span class="footer-motto">NEW ACCOUNT. ENDLESS STORIES.</span></footer>
  </div>
</template>

<script setup lang="ts">
import { Avatar, Hide, Iphone, Lock, Right, Ticket, TopRight, User, View } from '@element-plus/icons-vue'
import { reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { authApi } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const showPwd = ref(false)
const showConfirmPwd = ref(false)
const agreed = ref(true)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  phone: ''
})

const confirmStatus = computed<'idle' | 'match' | 'mismatch'>(() => {
  if (!form.confirmPassword) return 'idle'
  return form.password === form.confirmPassword ? 'match' : 'mismatch'
})

const handleRegister = async () => {
  if (loading.value) return
  if (!agreed.value) {
    ElMessage.warning('请先阅读并同意用户协议')
    return
  }

  if (!form.username) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!/^[a-zA-Z0-9_]{4,20}$/.test(form.username)) {
    ElMessage.warning('用户名必须是 4-20 位字母、数字或下划线')
    return
  }
  if (!form.password) {
    ElMessage.warning('请输入密码')
    return
  }
  if (form.password.length < 6 || form.password.length > 20) {
    ElMessage.warning('密码长度必须在 6-20 位之间')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (!form.nickname) {
    ElMessage.warning('请输入昵称')
    return
  }
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }

  loading.value = true
  try {
    await authApi.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      phone: form.phone || undefined
    })
    ElMessage.success('注册成功,请登录')
    await router.push({ path: '/login', query: route.query })
  } catch (err) {
    console.error('注册失败:', err)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  --paper: #f6f3eb; --ink: #22241f; --orange: #ff6b2b; --muted: #797b72;
  min-height: 100vh; min-height: 100svh; padding: 0 5.2%;
  background: var(--paper); color: var(--ink);
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  display: flex; flex-direction: column; -webkit-font-smoothing: antialiased;
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
.ticket { position: relative; background: #fffef9; border: 1px solid #e3e0d6; border-radius: 16px; box-shadow: 0 18px 50px #30322208, 0 3px 6px #30322203; }
.ticket-top { padding: 32px 35px 26px; }
.admit-label { font: 10px ui-monospace, Consolas, monospace; letter-spacing: 2px; color: #a35531; display: flex; align-items: center; gap: 8px; }
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
.ticket-stub { position: relative; padding: 23px 35px; border-top: 1px dashed #d9d9cb; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
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
.register-page .auth-main { padding-top: 12px; padding-bottom: 30px; }
.register-page .auth-form { margin-top: 22px; gap: 15px; }
.register-page .ticket-top { padding-top: 26px; padding-bottom: 22px; }
.register-page .ticket h2 { margin-top: 14px; }
.register-page .input-row { height: 45px; }
.register-page .register-line { margin-top: 18px; }
.register-page .ticket-stub { padding-top: 19px; padding-bottom: 19px; }
.register-page .ticket-after { margin-top: 15px; }
.field-hint { font-size: 10px; color: #8a8b7e; }
.field-hint.mismatch { color: #bc4829; }
.field-hint.match { color: #597044; }
.input-row.input-error { border-color: #ce765c; }
.profile-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 13px; }
.profile-fields .field { min-width: 0; }
.profile-fields .input-row { padding-inline: 11px; gap: 7px; }
.profile-fields .field-head { min-height: 16px; }
.agreement { display: flex; align-items: flex-start; gap: 8px; color: #87887b; font-size: 10px; line-height: 1.9; cursor: pointer; }
.agreement input { width: 14px; height: 14px; margin-top: 3px; flex-shrink: 0; accent-color: #e86c31; cursor: pointer; }
.agreement-titles { color: #726e5b; }
.first-scene { background: radial-gradient(ellipse at 45% 45%, #50604466, transparent 72%), #232b20; }
.scene-orbit { position: absolute; width: 420px; height: 420px; top: -65px; left: calc(50% - 210px); border: 1px solid #cad7ae12; border-radius: 50%; }
.orbit-two { width: 330px; height: 330px; left: calc(50% - 165px); top: -20px; }
.souvenir-ticket { position: absolute; width: 290px; height: 158px; border-radius: 9px; box-shadow: 0 16px 28px #0c160c40; }
.ticket-back { top: 52px; left: calc(50% - 80px); transform: rotate(12deg); background: #d97a43; border: 1px solid #f9b37e; padding: 18px 22px; color: #442b1a; }
.souvenir-overline { font: 8px ui-monospace, Consolas, monospace; letter-spacing: 1.3px; }
.ticket-back strong { display: block; margin-top: 12px; font-size: 25px; line-height: 1.4; letter-spacing: 2px; }
.back-star { position: absolute; right: 24px; top: 40px; color: #ffd2a1; font-size: 70px; }
.ticket-front { display: flex; top: 86px; left: calc(50% - 194px); background: #f2e9cf; border: 1px solid #f4eddc; transform: rotate(-9deg); color: #35412d; }
.souvenir-main { flex: 1; padding: 18px 21px; }
.souvenir-main strong { display: block; margin: 9px 0; font-size: 27px; line-height: 1.2; letter-spacing: 2px; }
.souvenir-main em { font-style: normal; color: #b95e30; }
.souvenir-caption { font-size: 8px; letter-spacing: 1px; color: #858368; }
.souvenir-stub { width: 62px; position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 15px; border-left: 1px dashed #a5ac8e; }
.souvenir-stub::before, .souvenir-stub::after { content: ''; position: absolute; left: -7px; width: 12px; height: 12px; border-radius: 50%; background: #303b28; }
.souvenir-stub::before { top: -7px; }
.souvenir-stub::after { bottom: -7px; }
.souvenir-stub > span { writing-mode: vertical-rl; font: 8px ui-monospace, Consolas, monospace; letter-spacing: 2px; }
.souvenir-stub .barcode { width: 33px; height: 18px; }
.souvenir-stub b { font: 18px Georgia, serif; }
.welcome-stamp { position: absolute; right: 26px; bottom: 45px; width: 100px; height: 100px; border: 1px solid #b7c29466; outline: 1px solid #b7c29433; outline-offset: -6px; border-radius: 50%; display: flex; flex-direction: column; align-items: center; justify-content: center; transform: rotate(10deg); color: #bdc9a8; font: 9px/1.8 ui-monospace, Consolas, monospace; letter-spacing: 1px; }
.welcome-stamp strong { font-size: 8px; }
.welcome-stamp > span { font-size: 24px; line-height: 1.2; color: #ecaa6c; }
@media (min-width: 1600px) { .ticket-front { top: 110px; } .ticket-back { top: 75px; } }
@media (max-width: 1050px) {
  .souvenir-ticket { width: 245px; height: 140px; }
  .ticket-front { left: calc(50% - 145px); top: 77px; }
  .ticket-back { left: calc(50% - 102px); top: 49px; }
  .souvenir-main { padding: 16px; } .souvenir-main strong { font-size: 24px; }
  .souvenir-overline { font-size: 7px; } .souvenir-caption { font-size: 7px; }
  .welcome-stamp { right: 12px; bottom: 24px; width: 80px; height: 80px; font-size: 8px; }
  .welcome-stamp strong { font-size: 6px; }
  .profile-fields { grid-template-columns: 1fr; gap: 15px; }
}
@media (max-width: 760px) {
  .register-page .auth-main { padding-top: 14px; padding-bottom: 36px; gap: 26px; }
  .register-page .story h1 { font-size: 38px; }
  .profile-fields { grid-template-columns: 1fr 1fr; gap: 12px; }
}
@media (max-width: 390px) { .register-page .story h1 { font-size: 34px; } }
@media (max-width: 360px) { .profile-fields { grid-template-columns: 1fr; } }
</style>

<template>
  <div class="auth-page">
    <!-- ============ LEFT HERO ============ -->
    <aside class="hero">
      <div class="hero-top">
        <div class="logo-row">
          <div class="logo-mark">
            <el-icon><VideoCamera /></el-icon>
          </div>
          <div class="logo-text">
            <span class="brand">JINLI · 影</span>
            <span class="brand-sub">premium booking</span>
          </div>
        </div>

        <div class="headline-block">
          <div class="eyebrow-hero">
            <span class="dot-primary"></span>
            <span>NOW PLAYING · 2026</span>
          </div>
          <h1 class="hero-title">沉浸式观影,<br />从这里开始。</h1>
          <p class="hero-desc">登录 JINLI 影,解锁 IMAX、CINITY、杜比影院专属选座、会员折扣与一键退改签。</p>
        </div>
      </div>

      <!-- Posters -->
      <div class="posters">
        <div class="poster-main">
          <div class="poster-top">
            <span class="poster-num">01</span>
            <span class="poster-tag">IMAX</span>
          </div>
          <div class="poster-bottom">
            <span class="poster-title">DUNE</span>
            <span class="poster-sub">PROPHET · 沙丘:救世主降临</span>
            <div class="poster-meta">
              <span>12.20 全国上映</span>
              <span><strong class="rating">9.2</strong>评分</span>
            </div>
          </div>
        </div>
        <div class="poster-accent">
          <span class="accent-num">02</span>
          <div>
            <span class="accent-title">TENET</span>
            <span class="accent-sub">信条 · 重映</span>
          </div>
        </div>
      </div>

      <!-- Hero bottom -->
      <div class="hero-bottom">
        <div class="stats">
          <div class="stat">
            <span class="stat-num">10K+</span>
            <span class="stat-label">合作影院</span>
          </div>
          <div class="stat">
            <span class="stat-num">9.6</span>
            <span class="stat-label">用户评分</span>
          </div>
          <div class="stat">
            <span class="stat-num">24H</span>
            <span class="stat-label">极速退改</span>
          </div>
        </div>
        <div class="hero-footer">
          <span class="copy">© 2026 JINLI Cinema · 沪ICP 备 2024XXXXX 号</span>
          <div class="hero-links">
            <span>帮助</span>
            <span>隐私</span>
            <span>条款</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- ============ RIGHT FORM ============ -->
    <main class="form-side">
      <div class="form-card">
        <header class="form-header">
          <div class="eyebrow">
            <span>WELCOME</span>
            <span class="dot"></span>
            <span>登录或注册</span>
          </div>
          <h2 class="form-title">进入你的电影世界</h2>
          <p class="form-sub">未注册的手机号将自动创建账号,新用户可领 8 元券和 5 元券各一张</p>
        </header>

        <div class="tabs">
          <button
            v-for="t in tabs"
            :key="t.key"
            class="tab"
            :class="{ active: activeTab === t.key }"
            @click="switchTab(t.key)"
          >
            {{ t.label }}
          </button>
        </div>

        <!-- Phone Login -->
        <div v-if="activeTab === 'phone'" class="fields">
          <div class="field">
            <div class="field-head">
              <span class="field-label">手机号码</span>
              <span class="field-hint">+86</span>
            </div>
            <div class="input-wrap">
              <div class="prefix">
                <el-icon><Iphone /></el-icon>
                <span>+86</span>
                <el-icon class="caret"><ArrowDown /></el-icon>
              </div>
              <div class="divider-v"></div>
              <input
                v-model="phone"
                class="input"
                type="tel"
                inputmode="numeric"
                maxlength="11"
                placeholder="188 8888 8888"
              />
              <button v-if="phone" class="clear" @click="phone = ''">
                <el-icon><Close /></el-icon>
              </button>
            </div>
          </div>

          <div class="field">
            <div class="field-head">
              <span class="field-label">短信验证码</span>
              <span class="field-link">未收到验证码?</span>
            </div>
            <div class="otp-row">
              <div class="otp-boxes">
                <input
                  v-for="(_, i) in 6"
                  :key="i"
                  :ref="(el) => (otpRefs[i] = el as HTMLInputElement)"
                  v-model="otpDigits[i]"
                  class="otp-box"
                  :class="{ filled: !!otpDigits[i] }"
                  maxlength="1"
                  inputmode="numeric"
                  @input="onOtpInput(i, $event)"
                  @keydown="onOtpKey(i, $event)"
                />
              </div>
              <button class="otp-send" :disabled="countdown > 0" @click="sendOtp">
                {{ countdown > 0 ? `${countdown}s` : '获取' }}
              </button>
            </div>
          </div>
        </div>

        <!-- QR Login -->
        <div v-else-if="activeTab === 'qr'" class="qr-block">
          <div class="qr-frame">
            <el-icon class="qr-icon"><Iphone /></el-icon>
            <p class="qr-title">请使用手机 App 扫码登录</p>
            <span class="qr-hint">该功能暂未开放,请使用密码登录</span>
          </div>
        </div>

        <!-- Password Login -->
        <div v-else class="fields">
          <div class="field">
            <div class="field-head">
              <span class="field-label">用户名</span>
              <span class="field-hint">USERNAME</span>
            </div>
            <div class="input-wrap simple">
              <div class="prefix-icon">
                <el-icon><User /></el-icon>
              </div>
              <input
                v-model="form.username"
                class="input"
                placeholder="请输入用户名"
                autocomplete="username"
                @keyup.enter="handleSubmit"
              />
              <button v-if="form.username" class="clear" @click="form.username = ''">
                <el-icon><Close /></el-icon>
              </button>
            </div>
          </div>

          <div class="field">
            <div class="field-head">
              <span class="field-label">密码</span>
              <span class="field-link" @click="forgotPassword">忘记密码?</span>
            </div>
            <div class="input-wrap simple">
              <div class="prefix-icon">
                <el-icon><Lock /></el-icon>
              </div>
              <input
                v-model="form.password"
                :type="showPwd ? 'text' : 'password'"
                class="input"
                placeholder="请输入密码"
                autocomplete="current-password"
                @keyup.enter="handleSubmit"
              />
              <button class="clear" @click="showPwd = !showPwd">
                <el-icon><View v-if="showPwd" /><Hide v-else /></el-icon>
              </button>
            </div>
          </div>
        </div>

        <!-- Agreement -->
        <div class="agreement">
          <div class="checkbox" :class="{ checked: agreed }" @click="agreed = !agreed">
            <el-icon v-if="agreed"><Check /></el-icon>
          </div>
          <span class="agree-text">我已阅读并同意</span>
          <span class="agree-link">《用户协议》</span>
          <span class="agree-link">《隐私政策》</span>
          <span class="agree-link">《观影须知》</span>
        </div>

        <!-- CTA -->
        <button class="cta" :disabled="loading" @click="handleSubmit">
          <span v-if="!loading">{{ activeTab === 'password' ? '登录' : '登录 / 注册' }}</span>
          <span v-else>登录中...</span>
          <el-icon v-if="!loading"><Right /></el-icon>
        </button>

        <!-- Divider -->
        <div class="divider">
          <span class="line"></span>
          <span class="divider-text">其他方式</span>
          <span class="line"></span>
        </div>

        <!-- Social -->
        <div class="social">
          <div class="social-item" @click="socialTip('微信')">
            <div class="social-icon" style="--c: #1AAD19">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <span>微信</span>
          </div>
          <div class="social-item" @click="socialTip('QQ')">
            <div class="social-icon" style="--c: #12B7F5">
              <el-icon><ChatLineRound /></el-icon>
            </div>
            <span>QQ</span>
          </div>
          <div class="social-item" @click="socialTip('微博')">
            <div class="social-icon" style="--c: #E6162D">
              <el-icon><TrendCharts /></el-icon>
            </div>
            <span>微博</span>
          </div>
          <div class="social-item" @click="socialTip('Apple')">
            <div class="social-icon" style="--c: #111111">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
                <path d="M17.5 12.5c0-2.8 2.3-4.1 2.4-4.2-1.3-1.9-3.4-2.2-4.1-2.2-1.7-.2-3.4 1-4.3 1-.9 0-2.3-1-3.7-1-1.9 0-3.7 1.1-4.7 2.8-2 3.5-.5 8.7 1.5 11.6.9 1.4 2.1 3 3.6 2.9 1.4-.1 2-.9 3.7-.9 1.7 0 2.2.9 3.7.9 1.5 0 2.5-1.4 3.5-2.8.9-1.2 1.6-2.5 1.6-2.6-.1 0-3.2-1.2-3.2-4.5zm-2.9-8.3c.8-1 1.3-2.3 1.2-3.7-1.1.1-2.5.7-3.3 1.7-.7.8-1.4 2.2-1.2 3.5 1.2.1 2.5-.6 3.3-1.5z" />
              </svg>
            </div>
            <span>Apple</span>
          </div>
        </div>

        <!-- Helper footer -->
        <div class="helper-footer">
          <div class="helper-left">
            <el-icon><Lock /></el-icon>
            <span>登录受 SSL 加密保护</span>
          </div>
          <div class="helper-right">
            <span>遇到问题?</span>
            <span class="helper-link" @click="router.push('/register')">立即注册</span>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

type TabKey = 'phone' | 'qr' | 'password'

const tabs: { key: TabKey; label: string }[] = [
  { key: 'phone', label: '手机登录' },
  { key: 'qr', label: '扫码登录' },
  { key: 'password', label: '密码登录' }
]

const activeTab = ref<TabKey>('password')
const loading = ref(false)
const showPwd = ref(false)
const agreed = ref(true)

const form = reactive({
  username: '',
  password: ''
})

const phone = ref('')
const otpDigits = ref<string[]>(['', '', '', '', '', ''])
const otpRefs: HTMLInputElement[] = []
const countdown = ref(0)
let countdownTimer: number | null = null

const switchTab = (tab: TabKey) => {
  activeTab.value = tab
  if (tab !== 'password') {
    ElMessage.info({
      message: '该方式暂未开放,请切换至「密码登录」',
      duration: 2000
    })
  }
}

const onOtpInput = (i: number, e: Event) => {
  const v = (e.target as HTMLInputElement).value.replace(/\D/g, '')
  otpDigits.value[i] = v.slice(-1)
  if (v && i < 5) {
    nextTick(() => otpRefs[i + 1]?.focus())
  }
}

const onOtpKey = (i: number, e: KeyboardEvent) => {
  if (e.key === 'Backspace' && !otpDigits.value[i] && i > 0) {
    nextTick(() => otpRefs[i - 1]?.focus())
  }
}

const sendOtp = () => {
  if (!/^1\d{10}$/.test(phone.value)) {
    ElMessage.warning('请输入正确的 11 位手机号')
    return
  }
  countdown.value = 58
  countdownTimer = window.setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
  ElMessage.success('验证码已发送(演示)')
}

const socialTip = (name: string) => {
  ElMessage.info(`${name} 登录暂未开放`)
}

const forgotPassword = () => {
  ElMessage.info('请联系客服重置密码')
}

const handleSubmit = async () => {
  if (!agreed.value) {
    ElMessage.warning('请先阅读并同意用户协议')
    return
  }
  if (activeTab.value !== 'password') {
    ElMessage.info('该方式暂未开放,请切换至「密码登录」')
    return
  }
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push('/home')
  } catch (err) {
    console.error('登录失败:', err)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Geist:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700;800&display=swap');

.auth-page {
  --primary: #FF8400;
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

/* =========== HERO =========== */
.hero {
  flex: 0 0 960px;
  background: linear-gradient(135deg, #1A1A1A 0%, #0F0F12 50%, #241814 100%);
  padding: 48px 56px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  color: #fff;
  overflow: hidden;
  position: relative;
}

.hero-top { display: flex; flex-direction: column; gap: 8px; }

.logo-row { display: flex; align-items: center; gap: 12px; }
.logo-mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #111;
}
.logo-mark :deep(.el-icon) { font-size: 20px; }
.logo-text { display: flex; flex-direction: column; gap: 2px; }
.brand {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1.5px;
}
.brand-sub { font-size: 11px; color: rgba(255, 255, 255, 0.5); }

.headline-block {
  padding-top: 56px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.eyebrow-hero {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.8);
  width: fit-content;
}
.dot-primary {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--primary);
}
.hero-title {
  font-size: 56px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -1px;
  margin: 0;
}
.hero-desc {
  font-size: 14px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.7);
  max-width: 520px;
  margin: 0;
}

/* Posters */
.posters {
  position: relative;
  height: 460px;
  margin: 24px 0;
}
.poster-main {
  width: 380px;
  height: 460px;
  border-radius: 16px;
  background: linear-gradient(180deg, #C97B4A 0%, #7A3A20 55%, #1F1815 100%);
  border: 2px solid rgba(255, 255, 255, 0.15);
  padding: 24px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transform: rotate(-4deg);
  box-shadow: -16px 24px 48px rgba(0, 0, 0, 0.6);
  position: relative;
  z-index: 1;
}
.poster-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.poster-num {
  font-family: var(--font-mono);
  font-size: 56px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -2px;
}
.poster-tag {
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: #fff;
}
.poster-bottom { display: flex; flex-direction: column; gap: 8px; }
.poster-title {
  font-family: var(--font-mono);
  font-size: 48px;
  font-weight: 800;
  letter-spacing: 4px;
  line-height: 1;
}
.poster-sub {
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.7);
}
.poster-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  font-size: 12px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.7);
}
.rating {
  color: var(--primary);
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 700;
  margin-right: 4px;
}

.poster-accent {
  position: absolute;
  top: 100px;
  left: 540px;
  width: 200px;
  height: 280px;
  border-radius: 14px;
  background: linear-gradient(180deg, #3D1B5C 0%, #150823 100%);
  border: 1.5px solid rgba(255, 255, 255, 0.15);
  padding: 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transform: rotate(8deg);
  box-shadow: 12px 16px 32px rgba(0, 0, 0, 0.5);
  z-index: 2;
}
.accent-num {
  font-family: var(--font-mono);
  font-size: 36px;
  font-weight: 800;
  letter-spacing: -1px;
  color: rgba(255, 255, 255, 0.8);
}
.accent-title {
  display: block;
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 2px;
  margin-bottom: 4px;
}
.accent-sub {
  font-size: 11px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.7);
}

/* Hero bottom */
.hero-bottom { display: flex; flex-direction: column; gap: 16px; }
.stats { display: flex; gap: 32px; }
.stat { display: flex; flex-direction: column; gap: 4px; }
.stat-num {
  font-family: var(--font-mono);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.5px;
}
.stat-label {
  font-size: 11px;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.5);
}
.hero-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  font-size: 11px;
}
.copy { color: rgba(255, 255, 255, 0.4); }
.hero-links { display: flex; gap: 16px; }
.hero-links span {
  color: rgba(255, 255, 255, 0.5);
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s;
}
.hero-links span:hover { color: #fff; }

/* =========== FORM SIDE =========== */
.form-side {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 48px 56px;
}
.form-card {
  width: 100%;
  max-width: 480px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  padding: 40px;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.form-header { display: flex; flex-direction: column; gap: 8px; }
.eyebrow {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--muted);
}
.dot {
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: var(--primary);
}
.form-title {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.5px;
  margin: 0;
  color: var(--fg);
}
.form-sub {
  font-size: 13px;
  line-height: 1.6;
  color: var(--muted);
  margin: 0;
}

/* Tabs */
.tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--secondary);
  border: 1px solid var(--border);
  border-radius: 999px;
}
.tab {
  flex: 1;
  padding: 10px 16px;
  border: none;
  background: transparent;
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 500;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.2s;
}
.tab.active {
  background: var(--card);
  color: var(--fg);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

/* Fields */
.fields { display: flex; flex-direction: column; gap: 16px; }
.field { display: flex; flex-direction: column; gap: 8px; }
.field-head { display: flex; justify-content: space-between; align-items: center; }
.field-label { font-size: 13px; font-weight: 600; color: var(--fg); }
.field-hint {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
}
.field-link {
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
  cursor: pointer;
  transition: color 0.15s;
}
.field-link:hover { color: var(--primary); }

.input-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 52px;
  padding: 0 8px 0 20px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 999px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.input-wrap.simple { padding: 0 8px 0 16px; gap: 10px; }
.input-wrap:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 4px rgba(255, 132, 0, 0.1);
}

.prefix {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  cursor: pointer;
}
.prefix > span {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
  color: var(--fg);
}
.prefix :deep(.el-icon) { font-size: 18px; }
.prefix .caret :deep(svg) { font-size: 16px; }
.prefix-icon { display: flex; color: var(--muted); }
.prefix-icon :deep(.el-icon) { font-size: 18px; }

.divider-v {
  width: 1px;
  height: 20px;
  background: var(--border);
}

.input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  outline: none;
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 500;
  color: var(--fg);
  letter-spacing: 0.5px;
}
.input::placeholder { color: var(--muted); opacity: 0.55; }

.clear {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 999px;
  background: var(--secondary);
  color: var(--muted);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s;
}
.clear:hover { background: var(--border); color: var(--fg); }
.clear :deep(.el-icon) { font-size: 14px; }

/* OTP */
.otp-row { display: flex; gap: 10px; align-items: center; }
.otp-boxes { display: flex; gap: 8px; flex: 1; }
.otp-box {
  flex: 1;
  width: 0;
  height: 52px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--card);
  text-align: center;
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 600;
  color: var(--fg);
  outline: none;
  transition: all 0.15s;
}
.otp-box.filled { background: var(--card); border-color: var(--border); }
.otp-box:focus {
  border-color: var(--primary);
  border-width: 1.5px;
  background: var(--bg);
  box-shadow: 0 0 0 4px rgba(255, 132, 0, 0.1);
}
.otp-send {
  height: 52px;
  padding: 0 18px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--secondary);
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--muted);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.otp-send:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--card);
}
.otp-send:disabled { opacity: 0.55; cursor: not-allowed; }

/* QR placeholder */
.qr-block { display: flex; justify-content: center; padding: 8px 0; }
.qr-frame {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 36px 24px;
  border: 1.5px dashed var(--border);
  border-radius: 16px;
  background: var(--bg);
  text-align: center;
}
.qr-icon { font-size: 48px; color: var(--muted); }
.qr-title { font-size: 14px; font-weight: 600; color: var(--fg); margin: 0; }
.qr-hint { font-size: 12px; color: var(--muted); }

/* Agreement */
.agreement {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.checkbox {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  border: 1px solid var(--border);
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s;
}
.checkbox.checked {
  background: var(--primary);
  border-color: var(--primary);
  color: #111;
}
.checkbox :deep(.el-icon) { font-size: 12px; font-weight: 700; }
.agree-text { font-size: 12px; color: var(--muted); }
.agree-link {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
  transition: color 0.15s;
}
.agree-link:hover { color: var(--primary); }

/* CTA */
.cta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 56px;
  border: none;
  border-radius: 999px;
  background: var(--primary);
  color: #111;
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
  cursor: pointer;
  box-shadow: 0 12px 24px rgba(255, 132, 0, 0.25);
  transition: transform 0.15s, box-shadow 0.2s, opacity 0.15s;
}
.cta:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 16px 32px rgba(255, 132, 0, 0.35);
}
.cta:active:not(:disabled) { transform: translateY(0); }
.cta:disabled { opacity: 0.7; cursor: not-allowed; }
.cta :deep(.el-icon) { font-size: 18px; }

/* Divider */
.divider {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0;
}
.divider .line {
  flex: 1;
  height: 1px;
  background: var(--border);
}
.divider-text {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 2px;
  color: var(--muted);
}

/* Social */
.social {
  display: flex;
  justify-content: center;
  gap: 24px;
}
.social-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.social-icon {
  width: 52px;
  height: 52px;
  border-radius: 999px;
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c, var(--fg));
  transition: all 0.2s;
}
.social-icon :deep(.el-icon) { font-size: 22px; }
.social-item:hover .social-icon {
  border-color: var(--c, var(--primary));
  transform: translateY(-2px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.08);
}
.social-item > span {
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
}

/* Helper footer */
.helper-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}
.helper-left,
.helper-right {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--muted);
}
.helper-left :deep(.el-icon) { font-size: 14px; }
.helper-link {
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
  transition: color 0.15s;
}
.helper-link:hover { color: var(--primary); }

/* Responsive */
@media (max-width: 1280px) {
  .hero { flex: 0 0 720px; padding: 40px 44px; }
  .hero-title { font-size: 44px; }
  .posters { transform: scale(0.85); transform-origin: left center; }
}

@media (max-width: 1024px) {
  .auth-page { flex-direction: column; }
  .hero {
    flex: 0 0 auto;
    width: 100%;
    min-height: auto;
    padding: 32px;
  }
  .posters { display: none; }
  .headline-block { padding-top: 24px; }
  .hero-title { font-size: 36px; }
  .form-side { padding: 32px 20px; }
}

@media (max-width: 520px) {
  .form-card { padding: 28px 20px; }
  .form-title { font-size: 22px; }
  .social { gap: 16px; }
  .social-icon { width: 44px; height: 44px; }
  .helper-footer { flex-direction: column; gap: 8px; align-items: flex-start; }
}
</style>

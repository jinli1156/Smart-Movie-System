<template>
  <div class="auth-page">
    <!-- ============ LEFT HERO ============ -->
    <aside class="hero">
      <div class="hero-top">
        <div class="logo-row">
          <div class="logo-mark">
            <img class="brand-logo-image" src="/cinema-logo.png" alt="JINLI 影" />
          </div>
          <div class="logo-text">
            <span class="brand">JINLI · 影</span>
            <span class="brand-sub">premium booking</span>
          </div>
        </div>

        <div class="headline-block">
          <div class="eyebrow-hero">
            <span class="dot-primary"></span>
            <span>JOIN US · 2026</span>
          </div>
          <h1 class="hero-title">你的电影之旅,<br />从此刻开始。</h1>
          <p class="hero-desc">注册 JINLI 影,立享 ¥13 新人观影券礼包 · 会员专属折扣 · IMAX/CINITY/杜比影院优先选座。</p>
        </div>
      </div>

      <!-- Benefits / Posters -->
      <div class="posters">
        <div class="poster-main">
          <div class="poster-top">
            <span class="poster-num">01</span>
            <span class="poster-tag">NEW USER</span>
          </div>
          <div class="poster-bottom">
            <span class="poster-title">¥13</span>
            <span class="poster-sub">8 元券 + 5 元券 · 注册即得</span>
            <div class="poster-meta">
              <span>立即激活</span>
              <span><strong class="rating">3张</strong>叠加</span>
            </div>
          </div>
        </div>
        <div class="poster-accent">
          <span class="accent-num">02</span>
          <div>
            <span class="accent-title">VIP</span>
            <span class="accent-sub">生日观影 · 双倍积分</span>
          </div>
        </div>
      </div>

      <!-- Hero bottom -->
      <div class="hero-bottom">
        <div class="stats">
          <div class="stat">
            <span class="stat-num">300万+</span>
            <span class="stat-label">注册影迷</span>
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
            <span>JOIN US</span>
            <span class="dot"></span>
            <span>创建新账号</span>
          </div>
          <h2 class="form-title">开启你的电影账号</h2>
          <p class="form-sub">完成注册即可领取 8 元券和 5 元券各一张,享会员专属折扣。</p>
        </header>

        <!-- Step indicator (decorative) -->
        <div class="steps">
          <div class="step active">
            <span class="step-num">01</span>
            <span class="step-label">填写信息</span>
          </div>
          <span class="step-line"></span>
          <div class="step">
            <span class="step-num">02</span>
            <span class="step-label">验证手机</span>
          </div>
          <span class="step-line"></span>
          <div class="step">
            <span class="step-num">03</span>
            <span class="step-label">完成注册</span>
          </div>
        </div>

        <!-- Fields -->
        <div class="fields">
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
                placeholder="4-20 位字母、数字或下划线"
                autocomplete="username"
              />
              <button v-if="form.username" class="clear" @click="form.username = ''">
                <el-icon><Close /></el-icon>
              </button>
            </div>
          </div>

          <div class="field">
            <div class="field-head">
              <span class="field-label">密码</span>
              <span class="field-hint">6-20 位</span>
            </div>
            <div class="input-wrap simple">
              <div class="prefix-icon">
                <el-icon><Lock /></el-icon>
              </div>
              <input
                v-model="form.password"
                :type="showPwd ? 'text' : 'password'"
                class="input"
                placeholder="请设置登录密码"
                autocomplete="new-password"
              />
              <button class="clear" @click="showPwd = !showPwd">
                <el-icon><View v-if="showPwd" /><Hide v-else /></el-icon>
              </button>
            </div>
          </div>

          <div class="field">
            <div class="field-head">
              <span class="field-label">确认密码</span>
              <span
                class="field-hint"
                :style="confirmStatus === 'mismatch' ? { color: '#E25C3D' } : confirmStatus === 'match' ? { color: '#1F9D55' } : {}"
              >
                {{ confirmStatus === 'mismatch' ? '两次密码不一致' : confirmStatus === 'match' ? '两次密码一致' : 'CONFIRM' }}
              </span>
            </div>
            <div
              class="input-wrap simple"
              :class="{ 'input-error': confirmStatus === 'mismatch' }"
            >
              <div class="prefix-icon">
                <el-icon><Lock /></el-icon>
              </div>
              <input
                v-model="form.confirmPassword"
                :type="showConfirmPwd ? 'text' : 'password'"
                class="input"
                placeholder="请再次输入密码"
                autocomplete="new-password"
              />
              <button class="clear" @click="showConfirmPwd = !showConfirmPwd">
                <el-icon><View v-if="showConfirmPwd" /><Hide v-else /></el-icon>
              </button>
            </div>
          </div>

          <div class="field">
            <div class="field-head">
              <span class="field-label">昵称</span>
              <span class="field-hint">NICKNAME</span>
            </div>
            <div class="input-wrap simple">
              <div class="prefix-icon">
                <el-icon><Avatar /></el-icon>
              </div>
              <input
                v-model="form.nickname"
                class="input"
                placeholder="给自己取个好听的名字"
                maxlength="20"
              />
              <button v-if="form.nickname" class="clear" @click="form.nickname = ''">
                <el-icon><Close /></el-icon>
              </button>
            </div>
          </div>

          <div class="field">
            <div class="field-head">
              <span class="field-label">手机号码</span>
              <span class="field-hint">可选 · OPTIONAL</span>
            </div>
            <div class="input-wrap">
              <div class="prefix">
                <el-icon><Iphone /></el-icon>
                <span>+86</span>
                <el-icon class="caret"><ArrowDown /></el-icon>
              </div>
              <div class="divider-v"></div>
              <input
                v-model="form.phone"
                class="input"
                type="tel"
                inputmode="numeric"
                maxlength="11"
                placeholder="188 8888 8888"
              />
              <button v-if="form.phone" class="clear" @click="form.phone = ''">
                <el-icon><Close /></el-icon>
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
        <button class="cta" :disabled="loading" @click="handleRegister">
          <span v-if="!loading">立即注册</span>
          <span v-else>注册中...</span>
          <el-icon v-if="!loading"><Right /></el-icon>
        </button>

        <!-- Divider -->
        <div class="divider">
          <span class="line"></span>
          <span class="divider-text">已有账号</span>
          <span class="line"></span>
        </div>

        <!-- Back to login -->
        <button class="ghost-btn" @click="router.push('/login')">
          <el-icon><Back /></el-icon>
          <span>返回登录</span>
        </button>

        <!-- Helper footer -->
        <div class="helper-footer">
          <div class="helper-left">
            <el-icon><Lock /></el-icon>
            <span>注册信息受 SSL 加密保护</span>
          </div>
          <div class="helper-right">
            <span>遇到问题?</span>
            <span class="helper-link" @click="contactSupport">联系客服</span>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'

const router = useRouter()
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

const contactSupport = () => {
  ElMessage.info('客服热线 400-888-8888 (9:00 - 22:00)')
}

const handleRegister = async () => {
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
    router.push('/login')
  } catch (err) {
    console.error('注册失败:', err)
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
  background: linear-gradient(180deg, #FFB74A 0%, #C8741F 50%, #2A1810 100%);
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
  font-size: 92px;
  font-weight: 800;
  letter-spacing: 2px;
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
  color: #fff;
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
  background: linear-gradient(180deg, #2D1F4F 0%, #150823 100%);
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
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 2px;
  margin-bottom: 4px;
  color: var(--primary);
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

/* Steps */
.steps {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 12px;
}
.step {
  display: flex;
  align-items: center;
  gap: 8px;
  opacity: 0.45;
}
.step.active { opacity: 1; }
.step-num {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--secondary);
  color: var(--muted);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}
.step.active .step-num {
  background: var(--primary);
  color: #111;
}
.step-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
  white-space: nowrap;
}
.step-line {
  flex: 1;
  height: 1px;
  background: var(--border);
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
  transition: color 0.15s;
}

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
.input-wrap.input-error {
  border-color: #E25C3D;
}
.input-wrap.input-error:focus-within {
  box-shadow: 0 0 0 4px rgba(226, 92, 61, 0.1);
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

/* Ghost button */
.ghost-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 52px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--card);
  color: var(--fg);
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.ghost-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(255, 132, 0, 0.05);
}
.ghost-btn :deep(.el-icon) { font-size: 16px; }

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
  .steps { padding: 10px 12px; gap: 6px; }
  .step-label { font-size: 11px; }
  .helper-footer { flex-direction: column; gap: 8px; align-items: flex-start; }
}
</style>

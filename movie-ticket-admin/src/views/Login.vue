<template>
  <div class="login-page">
    <header class="header-bar">
      <div class="header-logo">
        <img class="header-logo-image" src="/cinema-logo.png" alt="JINLI ADMIN" />
        <span class="logo-text">JINLI · ADMIN</span>
      </div>
      <div class="lang-switch">
        <el-icon><Connection /></el-icon>
        <span class="lang-label">简体中文</span>
        <el-icon class="small"><ArrowDown /></el-icon>
      </div>
    </header>

    <main class="center-wrap">
      <section class="brand-block">
        <div class="brand-row">
          <div class="brand-icon">
            <img class="brand-logo-image" src="/cinema-logo.png" alt="JINLI ADMIN" />
          </div>
          <div class="brand-text">
            <div class="brand-title">JINLI ADMIN</div>
            <div class="brand-sub">影管理 · 管理员后台</div>
          </div>
        </div>
        <div class="brand-note">请使用您的工号与密码登录管理控制台</div>
      </section>

      <section class="login-card">
        <header class="card-title-row">
          <div class="card-title">欢迎回来</div>
          <div class="card-subtitle">Sign in to continue · 安全登录</div>
        </header>

        <div class="tabs-row">
          <div
            class="tab"
            :class="{ active: activeTab === 'account' }"
            @click="activeTab = 'account'"
          >账号密码</div>
          <div
            class="tab"
            :class="{ active: activeTab === 'sms' }"
            @click="activeTab = 'sms'"
          >手机验证码</div>
        </div>

        <div class="form-column" v-if="activeTab === 'account'">
          <div class="field">
            <div class="field-label">管理员账号</div>
            <div class="input-row" :class="{ valid: form.username && form.username.length >= 3 }">
              <el-icon class="leading"><User /></el-icon>
              <input
                v-model="form.username"
                class="input-control"
                type="text"
                placeholder="请输入管理员账号"
                autocomplete="username"
              />
              <el-icon v-if="form.username && form.username.length >= 3" class="trailing ok"><CircleCheck /></el-icon>
            </div>
          </div>

          <div class="field">
            <div class="field-label-row">
              <span class="field-label">密码</span>
              <a class="link-forgot" @click.prevent>忘记密码</a>
            </div>
            <div class="input-row password">
              <el-icon class="leading"><Lock /></el-icon>
              <input
                v-model="form.password"
                class="input-control"
                :type="showPwd ? 'text' : 'password'"
                placeholder="请输入密码"
                autocomplete="current-password"
                @keyup.enter="handleLogin"
              />
              <button
                class="password-toggle"
                type="button"
                :aria-label="showPwd ? '隐藏密码' : '显示密码'"
                @click="showPwd = !showPwd"
              >
                <el-icon class="trailing toggle">
                  <View v-if="showPwd" />
                  <Hide v-else />
                </el-icon>
              </button>
            </div>
          </div>

          <div class="field">
            <div class="field-label">验证码</div>
            <div class="captcha-row">
              <div class="input-row captcha">
                <el-icon class="leading"><Lock /></el-icon>
                <input
                  v-model="form.captcha"
                  class="input-control"
                  type="text"
                  placeholder="请输入验证码"
                  maxlength="4"
                />
              </div>
              <div class="captcha-img" @click="rollCaptcha">
                <span class="cc cc-1">{{ captcha[0] }}</span>
                <span class="cc cc-2">{{ captcha[1] }}</span>
                <span class="cc cc-3">{{ captcha[2] }}</span>
                <span class="cc cc-4">{{ captcha[3] }}</span>
              </div>
            </div>
          </div>

          <div class="remember-row">
            <label class="remember-left">
              <span class="checkbox" :class="{ checked: remember }" @click="remember = !remember">
                <el-icon v-if="remember" class="tiny"><Check /></el-icon>
              </span>
              <span class="remember-label">7天内自动登录</span>
            </label>
            <div class="remember-right">
              <el-icon class="tiny shield"><Lock /></el-icon>
              <span class="secure-label">安全连接</span>
            </div>
          </div>
        </div>

        <div class="form-column sms" v-else>
          <div class="field">
            <div class="field-label">手机号</div>
            <div class="input-row">
              <el-icon class="leading"><Iphone /></el-icon>
              <input
                v-model="form.phone"
                class="input-control"
                type="tel"
                placeholder="请输入管理员手机号"
                maxlength="11"
              />
            </div>
          </div>
          <div class="field">
            <div class="field-label">短信验证码</div>
            <div class="captcha-row">
              <div class="input-row captcha">
                <el-icon class="leading"><Message /></el-icon>
                <input
                  v-model="form.smsCode"
                  class="input-control"
                  type="text"
                  placeholder="请输入短信验证码"
                  maxlength="6"
                />
              </div>
              <button class="sms-btn" :disabled="smsCountdown > 0" @click="sendSms">
                {{ smsCountdown > 0 ? `${smsCountdown}s` : '获取验证码' }}
              </button>
            </div>
          </div>
          <div class="remember-row">
            <div class="remember-right">
              <el-icon class="tiny shield"><Lock /></el-icon>
              <span class="secure-label">短信通道已加密</span>
            </div>
          </div>
        </div>

        <button class="login-button" :disabled="loading" @click="handleLogin">
          <span class="login-label">{{ loading ? '登录中…' : '登录控制台' }}</span>
          <el-icon class="arrow"><Right /></el-icon>
        </button>

        <div class="alt-row">
          <span class="line"></span>
          <span class="alt-text">其它登录方式</span>
          <span class="line"></span>
        </div>

        <div class="sso-row">
          <button class="sso" @click.prevent>
            <el-icon><Key /></el-icon>
            <span>工号 SSO</span>
          </button>
          <button class="sso" @click.prevent>
            <el-icon><Grid /></el-icon>
            <span>扫码登录</span>
          </button>
          <button class="sso" @click.prevent>
            <el-icon><Pointer /></el-icon>
            <span>指纹</span>
          </button>
        </div>
      </section>

      <div class="card-footnote">
        <el-icon class="tiny"><InfoFilled /></el-icon>
        <span>仅限授权员工使用 · 所有操作日志将被记录 · v2.6.0</span>
      </div>
    </main>

    <footer class="page-footer">
      <div class="footer-l">
        <span>© 2026 JINLI Film Group</span>
        <span class="dot">·</span>
        <span>粤ICP备 20260520 号</span>
        <span class="dot">·</span>
        <span>公网安备 44030302026005 号</span>
      </div>
      <div class="footer-r">
        <a @click.prevent>使用协议</a>
        <a @click.prevent>隐私政策</a>
        <a @click.prevent>帮助中心</a>
        <a @click.prevent>管理员手册</a>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Check,
  CircleCheck,
  Connection,
  Grid,
  Hide,
  InfoFilled,
  Iphone,
  Key,
  Lock,
  Message,
  Pointer,
  Right,
  User,
  View
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref<'account' | 'sms'>('account')
const loading = ref(false)
const showPwd = ref(false)
const remember = ref(true)
const smsCountdown = ref(0)
let smsTimer: number | null = null

const form = reactive({
  username: '',
  password: '',
  captcha: '',
  phone: '',
  smsCode: ''
})

const captcha = ref<string[]>([])
const captchaSeed = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ'

const rollCaptcha = () => {
  const next: string[] = []
  for (let i = 0; i < 4; i++) {
    next.push(captchaSeed[Math.floor(Math.random() * captchaSeed.length)])
  }
  captcha.value = next
}

const sendSms = () => {
  if (!form.phone || form.phone.length < 11) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  smsCountdown.value = 60
  smsTimer = window.setInterval(() => {
    smsCountdown.value -= 1
    if (smsCountdown.value <= 0 && smsTimer) {
      window.clearInterval(smsTimer)
      smsTimer = null
    }
  }, 1000)
  ElMessage.success('验证码已发送')
}

const handleLogin = async () => {
  if (activeTab.value === 'account') {
    if (!form.username) {
      ElMessage.warning('请输入管理员账号')
      return
    }
    if (!form.password) {
      ElMessage.warning('请输入密码')
      return
    }
    if (!form.captcha || form.captcha.toUpperCase() !== captcha.value.join('')) {
      ElMessage.warning('验证码错误')
      rollCaptcha()
      form.captcha = ''
      return
    }
  } else {
    if (!form.phone) {
      ElMessage.warning('请输入手机号')
      return
    }
    if (!form.smsCode) {
      ElMessage.warning('请输入短信验证码')
      return
    }
  }

  loading.value = true
  try {
    const account = activeTab.value === 'account' ? form.username : form.phone
    await userStore.login(account, form.password || form.smsCode)
    if (userStore.userInfo?.role === 'USER') {
      ElMessage.error('权限不足，请使用管理员账号登录')
      userStore.logout()
      return
    }
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error) {
    console.error('登录失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  rollCaptcha()
})

onBeforeUnmount(() => {
  if (smsTimer) {
    window.clearInterval(smsTimer)
    smsTimer = null
  }
})
</script>

<style scoped>
.login-page {
  --background: #f2f3f0;
  --card: #ffffff;
  --border: #cbccc9;
  --primary: #ff8400;
  --primary-shadow: rgba(255, 132, 0, 0.25);
  --text: #111111;
  --muted: #666666;
  --success: #22a06b;
  --font-primary: 'JetBrains Mono', ui-monospace, monospace;
  --font-secondary: 'Geist', system-ui, -apple-system, 'Segoe UI', sans-serif;
  min-height: 100vh;
  background: var(--background);
  display: flex;
  flex-direction: column;
  font-family: var(--font-secondary);
  color: var(--text);
}

.small { font-size: 14px; }
.tiny { font-size: 14px; }

.header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32px 60px 0 60px;
}
.header-logo { display: inline-flex; align-items: center; gap: 10px; }
.header-logo-image {
  width: 24px;
  height: 24px;
  display: block;
  object-fit: contain;
}
.logo-dot {
  width: 8px; height: 8px; border-radius: 999px; background: var(--primary);
  display: inline-block;
}
.logo-text {
  font-family: var(--font-primary);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.04em;
}
.lang-switch {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 8px 14px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 999px;
  cursor: pointer;
}
.lang-switch .el-icon { color: var(--muted); font-size: 17px; }
.lang-label { font-size: 13px; }

.center-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 28px;
  padding: 24px 60px;
}

.brand-block {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
}
.brand-row { display: inline-flex; align-items: center; gap: 14px; }
.brand-icon {
  width: 44px; height: 44px; border-radius: 12px;
  background: var(--primary);
  display: inline-flex; align-items: center; justify-content: center;
}
.brand-icon .el-icon { font-size: 26px; color: #111111; }
.brand-text { display: flex; flex-direction: column; gap: 4px; }
.brand-title {
  font-family: var(--font-primary);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.1;
}
.brand-sub { font-size: 13px; color: var(--muted); }
.brand-note { font-size: 13px; color: var(--muted); }

.login-card {
  width: 480px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 40px 40px 32px 40px;
  display: flex;
  flex-direction: column;
  gap: 22px;
  box-shadow: 0 8px 24px rgba(17, 17, 17, 0.08);
}

.card-title-row { display: flex; flex-direction: column; gap: 6px; }
.card-title { font-size: 22px; font-weight: 600; }
.card-subtitle {
  font-family: var(--font-primary);
  font-size: 12px;
  color: var(--muted);
}

.tabs-row {
  display: flex;
  gap: 4px;
  background: var(--background);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 4px;
}
.tab {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--muted);
  cursor: pointer;
  user-select: none;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.tab.active {
  background: var(--card);
  color: var(--text);
  border: 1px solid var(--border);
  font-weight: 500;
}

.form-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field { display: flex; flex-direction: column; gap: 8px; }
.field-label {
  font-family: var(--font-primary);
  font-size: 12px;
  font-weight: 500;
}
.field-label-row { display: flex; align-items: center; justify-content: space-between; }
.link-forgot {
  font-size: 12px;
  color: var(--primary);
  cursor: pointer;
  text-decoration: none;
}

.input-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 14px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 10px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.input-row:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(255, 132, 0, 0.12);
}
.input-row.valid { border-color: var(--primary); }
.input-row.captcha { flex: 1; }
.input-control {
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  font-family: var(--font-secondary);
  font-size: 14px;
  color: var(--text);
  min-width: 0;
}
.input-row.password .input-control { font-size: 16px; font-weight: 500; letter-spacing: 0.08em; }
.input-control::placeholder { color: #b0b0b0; }
.leading { color: var(--muted); font-size: 18px; }
.trailing { color: var(--muted); font-size: 18px; }
.trailing.ok { color: var(--success); font-size: 16px; }
.trailing.toggle { cursor: pointer; }
.password-toggle {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  padding: 0;
  border: 0;
  border-radius: 7px;
  color: var(--muted);
  background: transparent;
  cursor: pointer;
}
.password-toggle:hover { color: var(--text); background: var(--background); }
.password-toggle:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }

.captcha-row { display: flex; align-items: center; gap: 12px; }
.captcha-img {
  width: 140px;
  height: 50px;
  border-radius: 10px;
  background: #fff5e8;
  border: 1px solid var(--border);
  display: inline-flex;
  align-items: center;
  justify-content: space-around;
  cursor: pointer;
  user-select: none;
  overflow: hidden;
  position: relative;
}
.captcha-img::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 30%, rgba(255, 132, 0, 0.2) 0 1px, transparent 2px),
    radial-gradient(circle at 60% 70%, rgba(34, 160, 107, 0.2) 0 1px, transparent 2px),
    radial-gradient(circle at 80% 20%, rgba(17, 17, 17, 0.15) 0 1px, transparent 2px);
  background-size: 16px 16px, 22px 22px, 14px 14px;
}
.cc {
  position: relative;
  font-family: var(--font-primary);
  font-size: 22px;
  font-weight: 600;
  color: var(--text);
}
.cc-1 { transform: rotate(-8deg); color: #ff8400; }
.cc-2 { transform: rotate(6deg); color: #111111; }
.cc-3 { transform: rotate(-4deg); color: #22a06b; }
.cc-4 { transform: rotate(10deg); color: #c44a18; }

.sms-btn {
  height: 50px;
  padding: 0 18px;
  border-radius: 10px;
  background: var(--background);
  border: 1px solid var(--border);
  color: var(--text);
  font-family: var(--font-secondary);
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.sms-btn:disabled {
  color: var(--muted);
  cursor: not-allowed;
}

.remember-row { display: flex; align-items: center; justify-content: space-between; }
.remember-left { display: inline-flex; align-items: center; gap: 8px; cursor: pointer; }
.checkbox {
  width: 16px; height: 16px;
  border-radius: 4px;
  background: var(--card);
  border: 1px solid var(--border);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.checkbox.checked {
  background: var(--primary);
  border-color: var(--primary);
}
.checkbox .el-icon.tiny { color: #111111; font-size: 12px; font-weight: 700; }
.remember-label { font-size: 13px; }
.remember-right { display: inline-flex; align-items: center; gap: 6px; }
.shield { color: var(--success); }
.secure-label {
  font-family: var(--font-primary);
  font-size: 11px;
  color: var(--success);
}

.login-button {
  height: 52px;
  border-radius: 999px;
  background: var(--primary);
  color: #111111;
  border: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  font-family: var(--font-secondary);
  box-shadow: 0 6px 16px var(--primary-shadow);
  transition: transform 0.1s ease, box-shadow 0.15s ease;
}
.login-button:hover { box-shadow: 0 8px 20px var(--primary-shadow); }
.login-button:active { transform: translateY(1px); }
.login-button:disabled { opacity: 0.7; cursor: not-allowed; }
.login-label { font-size: 15px; font-weight: 600; }
.login-button .arrow { font-size: 18px; font-weight: 600; }

.alt-row { display: flex; align-items: center; gap: 12px; }
.line { flex: 1; height: 1px; background: var(--border); }
.alt-text { font-size: 11px; color: var(--muted); }

.sso-row { display: flex; gap: 12px; }
.sso {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--background);
  border: 1px solid var(--border);
  border-radius: 10px;
  font-size: 12px;
  cursor: pointer;
  color: var(--text);
  transition: background-color 0.15s ease, border-color 0.15s ease;
}
.sso:hover { border-color: var(--primary); background: #fff; }
.sso .el-icon { font-size: 16px; }

.card-footnote { display: inline-flex; align-items: center; gap: 12px; color: var(--muted); }
.card-footnote .el-icon { font-size: 14px; flex: 0 0 auto; }
.card-footnote span:last-child { font-family: var(--font-primary); font-size: 11px; }

.page-footer {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 60px 32px 60px;
}
.footer-l { display: inline-flex; align-items: center; gap: 16px; }
.footer-l span { font-family: var(--font-primary); font-size: 11px; color: var(--muted); }
.footer-l .dot { color: #cbccc9; }
.footer-r { display: inline-flex; align-items: center; gap: 18px; }
.footer-r a { font-size: 11px; color: var(--muted); cursor: pointer; }
.footer-r a:hover { color: var(--text); }

@media (max-width: 640px) {
  .login-card { width: 100%; padding: 28px 22px; }
  .header-bar, .page-footer { padding-left: 20px; padding-right: 20px; }
  .center-wrap { padding: 20px; }
  .footer-l, .footer-r { flex-wrap: wrap; gap: 8px 14px; justify-content: center; }
}
</style>

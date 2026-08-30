<template>
  <aside class="sidebar">
    <!-- Logo -->
    <div class="logo-block">
      <div class="logo-mark">
        <img class="brand-logo-image" src="/cinema-logo.png" alt="CINEMA+" />
      </div>
      <div class="logo-text">
        <span class="brand">CINEMA+</span>
        <span class="brand-sub">premium booking</span>
      </div>
    </div>

    <!-- Search -->
    <div class="sidebar-search" :class="{ focused: searchFocused }">
      <MaterialIcon class="msr search-ico" @click="onSearch" name="search" />
      <input
        v-model="sidebarKeyword"
        class="search-input"
        type="text"
        placeholder="搜索影片..."
        @keyup.enter="onSearch"
        @focus="searchFocused = true"
        @blur="searchFocused = false"
      />

    </div>

    <!-- Nav -->
    <nav class="nav">
      <a
        v-for="item in navItems"
        :key="item.key"
        class="nav-item"
        :class="{ active: item.key === activeNav }"
        @click="onNavClick(item)"
      >
        <MaterialIcon class="msr" :name="item.icon" />
        <span class="nav-label">{{ item.label }}</span>
        <span v-if="item.badge" class="nav-badge" :class="item.badgeClass">{{ item.badge }}</span>
      </a>
    </nav>

    <div class="sidebar-divider"></div>

    <!-- Quick Access -->
    <div v-if="showQuickAccess" class="quick-access">
      <div class="qa-head">
        <span>QUICK ACCESS</span>
        <MaterialIcon class="msr" name="expand_more" />
      </div>
      <a class="qa-item" :class="{ active: isHotRanking }" @click="goToHotRanking">
        <MaterialIcon class="msr qa-ico" name="trending_up" />
        <span class="qa-label">热映榜单</span>
        <span class="qa-tag">TOP 10</span>
      </a>
      <a class="qa-item" :class="{ active: isUpcomingListing }" @click="goToUpcoming">
        <MaterialIcon class="msr qa-ico" name="calendar_month" />
        <span class="qa-label">即将上映</span>
        <span class="qa-tag">NEW</span>
      </a>
      <a class="qa-item" @click="tip('周末特惠')">
        <MaterialIcon class="msr qa-ico" name="local_fire_department" />
        <span class="qa-label">周末特惠</span>
        <span class="qa-tag">-30%</span>
      </a>
      <a class="qa-item" @click="tip('兑换券码')">
        <MaterialIcon class="msr qa-ico" name="redeem" />
        <span class="qa-label">兑换券码</span>
        <span class="qa-tag">新</span>
      </a>
    </div>

    <div class="sidebar-spacer"></div>

    <!-- User Footer -->
    <div class="sidebar-footer" v-if="userStore.isLoggedIn">
      <div class="avatar" @click="router.push('/profile')">{{ avatarChar }}</div>
      <div class="user-info" @click="router.push('/profile')">
        <span class="user-name">{{ userStore.userInfo?.nickname || '用户' }}</span>
        <span class="user-tier">GOLD · 1280 积分</span>
      </div>
      <el-dropdown trigger="click">
        <MaterialIcon class="msr more-btn" name="more_horiz" />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push('/profile')">个人中心</el-dropdown-item>
            <el-dropdown-item @click="router.push('/orders')">我的订单</el-dropdown-item>
            <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <div v-else class="sidebar-footer guest">
      <div class="avatar guest-avatar">
        <MaterialIcon class="msr" name="person" />
      </div>
      <div class="user-info">
        <span class="user-name">访客</span>
        <span class="user-tier">登录解锁会员权益</span>
      </div>
      <button class="footer-login" @click="router.push('/login')">登录</button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const sidebarKeyword = ref('')
const searchFocused = ref(false)

withDefaults(defineProps<{ showQuickAccess?: boolean }>(), {
  showQuickAccess: true
})

const navItems = [
  { key: 'home', label: '首页', icon: 'home', route: '/home' },
  { key: 'movies', label: '影片', icon: 'movie', badge: 'NEW', badgeClass: 'badge-primary', route: '/movies' },
  { key: 'cinemas', label: '影院', icon: 'theaters', route: '/cinemas' },
  { key: 'profile', label: '个人中心', icon: 'person', route: '/profile' },
  { key: 'orders', label: '订单', icon: 'receipt_long', route: '/orders' },
  { key: 'favorites', label: '想看', icon: 'favorite', route: '/favorites' },
  { key: 'settings', label: '设置', icon: 'settings' }
]

const activeNav = computed(() => {
  const path = route.path
  if (path.startsWith('/home')) return 'home'
  if (path.startsWith('/movies') || path.startsWith('/movie/')) return 'movies'
  if (path.startsWith('/cinemas')) return 'cinemas'
  if (path.startsWith('/profile')) return 'profile'
  if (path.startsWith('/orders') || path.startsWith('/order')) return 'orders'
  if (path.startsWith('/favorites')) return 'favorites'
  return ''
})

const isHotRanking = computed(() =>
  route.path === '/movies' && route.query.ranking === 'hot'
)

const isUpcomingListing = computed(() =>
  route.path === '/movies' && route.query.ranking === 'upcoming'
)

const avatarChar = computed(() => {
  const n = userStore.userInfo?.nickname || '用'
  return n.charAt(0).toUpperCase()
})

const onNavClick = (item: { key: string; label: string; route?: string }) => {
  if (item.route) {
    if ((item.route === '/orders' || item.route === '/profile' || item.route === '/favorites') && !userStore.isLoggedIn) {
      ElMessage.warning('请先登录')
      router.push('/login')
      return
    }
    if (route.path !== item.route) {
      router.push(item.route)
    }
  } else {
    ElMessage.info(`${item.label} 模块敬请期待`)
  }
}

const onSearch = () => {
  const kw = sidebarKeyword.value.trim()
  if (!kw) return
  router.push({ path: '/movies', query: { q: kw } })
  sidebarKeyword.value = ''
}

const goToHotRanking = () => {
  router.push({ path: '/movies', query: { ranking: 'hot' } })
}

const goToUpcoming = () => {
  router.push({ path: '/movies', query: { ranking: 'upcoming' } })
}

const tip = (label: string) => {
  ElMessage.info(`${label} 功能敬请期待`)
}

const handleLogout = () => {
  userStore.logout()
  ElMessage.success('已退出登录')
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Geist:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700;800&display=swap');
@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,0,0&display=swap');

.sidebar {
  --primary: #FF8400;
  --primary-foreground: #111111;
  --bg: #F2F3F0;
  --card: #FFFFFF;
  --sidebar: #E7E8E5;
  --sidebar-accent: #CBCCC9;
  --sidebar-fg: #666666;
  --sidebar-accent-fg: #18181b;
  --sidebar-border: #CBCCC9;
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  --font-sans: 'Geist', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;

  width: 280px;
  flex-shrink: 0;
  background: var(--sidebar);
  border-right: 1px solid var(--sidebar-border);
  display: flex;
  flex-direction: column;
  height: 100vh;
  position: sticky;
  top: 0;
  font-family: var(--font-sans);
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
  font-size: 20px;
  user-select: none;
}

.logo-block {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 90px;
  padding: 22px 24px;
  border-bottom: 1px solid var(--sidebar-border);
  flex-shrink: 0;
}
.logo-mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--primary);
  color: var(--primary-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.logo-mark .msr { font-size: 24px; }
.logo-text { display: flex; flex-direction: column; gap: 2px; }
.brand {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 700;
  color: var(--sidebar-accent-fg);
  letter-spacing: 1.5px;
}
.brand-sub {
  font-size: 12px;
  color: var(--sidebar-fg);
}

.sidebar-search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  margin: 16px;
  background: var(--sidebar-accent);
  border: 1px solid var(--sidebar-border);
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.sidebar-search:hover { border-color: var(--primary); }
.sidebar-search.focused { border-color: var(--primary); background: var(--card); }
.sidebar-search .search-ico { font-size: 18px; color: var(--sidebar-fg); cursor: pointer; }
.sidebar-search .search-ico:hover { color: var(--primary); }
.search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 14px;
  color: var(--sidebar-accent-fg);
}
.search-input::placeholder { color: var(--sidebar-fg); }
.kbd {
  padding: 2px 6px;
  border: 1px solid var(--sidebar-border);
  border-radius: 4px;
  background: var(--sidebar);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--sidebar-fg);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 12px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s;
  text-decoration: none;
  color: inherit;
}
.nav-item:hover { background: rgba(0, 0, 0, 0.04); }
.nav-item .msr { font-size: 24px; color: var(--sidebar-fg); }
.nav-label {
  flex: 1;
  font-size: 15px;
  font-weight: 500;
  color: var(--sidebar-accent-fg);
}
.nav-item.active { background: var(--primary); }
.nav-item.active .msr,
.nav-item.active .nav-label { color: var(--primary-foreground); font-weight: 600; }
.nav-badge {
  padding: 3px 10px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  background: var(--sidebar-accent);
  color: var(--sidebar-fg);
}
.nav-item.active .nav-badge.badge-primary {
  background: rgba(17, 17, 17, 0.2);
  color: var(--primary-foreground);
}

.sidebar-divider {
  height: 1px;
  background: var(--sidebar-border);
  margin: 4px 0;
}

.quick-access {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px 20px 14px;
}
.qa-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1.5px;
  color: var(--sidebar-fg);
}
.qa-head .msr { font-size: 16px; }
.qa-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}
.qa-item:hover { background: rgba(0, 0, 0, 0.04); }
.qa-item.active {
  background: rgba(255, 132, 0, 0.12);
  box-shadow: inset 3px 0 0 var(--primary);
}
.qa-item.active .qa-label { color: var(--primary); font-weight: 700; }
.qa-ico { font-size: 18px; color: var(--primary); }
.qa-label {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--sidebar-accent-fg);
}
.qa-tag {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--primary);
}

.sidebar-spacer { flex: 1; }

.sidebar-footer {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-top: 1px solid var(--sidebar-border);
}
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: linear-gradient(135deg, #FF8400 0%, #FF3F33 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-sans);
  font-size: 15px;
  font-weight: 700;
  flex-shrink: 0;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.4);
}
.guest-avatar {
  background: var(--sidebar-accent);
  color: var(--sidebar-fg);
}
.guest-avatar .msr { font-size: 20px; }
.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
  cursor: pointer;
  transition: opacity 0.15s;
}
.user-info:hover {
  opacity: 0.8;
}
.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--sidebar-accent-fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-tier {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--sidebar-fg);
}
.more-btn {
  font-size: 18px;
  color: var(--sidebar-fg);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.15s;
}
.more-btn:hover { background: var(--sidebar-accent); }
.footer-login {
  padding: 6px 14px;
  border: none;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s;
}
.footer-login:hover { opacity: 0.88; }

@media (max-width: 1024px) {
  .sidebar { display: none; }
}
</style>

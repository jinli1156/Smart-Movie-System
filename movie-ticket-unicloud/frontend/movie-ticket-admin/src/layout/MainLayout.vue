<template>
  <el-container class="layout-container">
    <el-aside width="248px" class="sidebar">
      <div class="logo">
        <div class="logo-mark">
          <img class="brand-logo-image" src="/xiangzhi-ticket.png" alt="响指电影" />
        </div>
        <div class="logo-copy">
          <strong class="brand-wordmark" role="img" aria-label="响指电影"></strong>
          <span>ADMIN CONSOLE</span>
        </div>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据统计</span>
        </el-menu-item>

        <el-menu-item index="/cinema">
          <el-icon><OfficeBuilding /></el-icon>
          <span>影院管理</span>
        </el-menu-item>

        <el-menu-item index="/hall">
          <el-icon><Grid /></el-icon>
          <span>影厅管理</span>
        </el-menu-item>

        <el-menu-item index="/movie" v-if="userStore.userInfo?.role === 'ADMIN'">
          <el-icon><Film /></el-icon>
          <span>电影管理</span>
        </el-menu-item>

        <el-menu-item index="/listing">
          <el-icon><MagicStick /></el-icon><span>AI 辅助上架</span>
        </el-menu-item>

        <el-menu-item index="/showtime">
          <el-icon><Calendar /></el-icon>
          <span>排片管理</span>
        </el-menu-item>

        <el-menu-item index="/smart-scheduling">
          <el-icon><TrendCharts /></el-icon>
          <span>智能排片工作台</span>
        </el-menu-item>

        <el-menu-item index="/marketing/studio">
          <el-icon><Picture /></el-icon><span>营销工作台</span>
        </el-menu-item>
        <el-menu-item index="/order">
          <el-icon><Tickets /></el-icon>
          <span>订单管理</span>
        </el-menu-item>

        <el-menu-item index="/coupon-code" v-if="userStore.userInfo?.role === 'ADMIN'">
          <el-icon><Discount /></el-icon>
          <span>兑换码管理</span>
        </el-menu-item>

        <el-menu-item index="/user" v-if="userStore.userInfo?.role === 'ADMIN'">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/recommendation-evaluation" v-if="userStore.userInfo?.role === 'ADMIN'">
          <el-icon><DataAnalysis /></el-icon><span>推荐算法评估</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-content">
          <div class="page-identity">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/dashboard' }">管理中心</el-breadcrumb-item>
              <el-breadcrumb-item v-if="breadcrumb">{{ breadcrumb }}</el-breadcrumb-item>
            </el-breadcrumb>
            <strong>{{ breadcrumb || '管理中心' }}</strong>
          </div>

          <div class="user-info">
            <div class="admin-badge">{{ userStore.userInfo?.role === 'CINEMA_ADMIN' ? '所属影院管理' : '平台管理' }}</div>
            <el-dropdown trigger="click">
              <div class="user-menu">
                <span class="user-avatar">{{ avatarChar }}</span>
                <span class="user-name">{{ userStore.userInfo?.nickname || '管理员' }}</span>
                <el-icon class="el-icon--right"><arrow-down /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>

      <el-main class="main">
        <el-alert v-if="userStore.userInfo?.role === 'CINEMA_ADMIN' && !userStore.userInfo?.cinemaId"
          title="尚未分配影院，请联系平台管理员在用户管理中设置所属影院，设置后重新登录。" type="warning" :closable="false" show-icon />
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
/**
 * 后台布局：按角色展示功能菜单，提供页面导航和退出登录入口。
 */
import { computed, h } from 'vue'
import '@/styles/management-layout.css'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import 'element-plus/es/components/message-box/style/css'
import {
  ArrowDown,
  Calendar,
  DataAnalysis,
  Discount,
  Film,
  MagicStick,
  Grid,
  OfficeBuilding,
  SwitchButton,
  Tickets,
  TrendCharts,
  Picture,
  User
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path.startsWith('/marketing/') ? '/marketing/studio' : route.path)

const breadcrumbMap: Record<string, string> = {
  '/dashboard': '数据统计',
  '/cinema': '影院管理',
  '/hall': '影厅管理',
  '/movie': '电影管理',
  '/listing': 'AI 辅助上架',
  '/showtime': '排片管理',
  '/smart-scheduling': '智能排片工作台',
  '/marketing/studio': '营销创作工作台',
  '/marketing/tasks': '任务与素材中心',
  '/marketing/review': '审核与发布',
  '/order': '订单管理',
  '/coupon-code': '兑换码管理',
  '/user': '用户管理',
  '/recommendation-evaluation': '推荐算法评估'
}

const breadcrumb = computed(() => breadcrumbMap[route.path])
const avatarChar = computed(() => (userStore.userInfo?.nickname || '管').charAt(0).toUpperCase())

/** 清理当前会话并跳转登录页面。 */
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm(
      h('div', { class: 'logout-confirm-content' }, [
        h('div', { class: 'logout-confirm-icon', 'aria-hidden': 'true' }, [h(SwitchButton)]),
        h('p', { class: 'logout-confirm-heading' }, '准备离开管理后台？'),
        h('p', { class: 'logout-confirm-description' }, '退出后需重新登录才能继续管理，请确认当前编辑内容已保存。')
      ]),
      '退出登录',
      {
        customClass: 'admin-logout-confirm',
        confirmButtonText: '确认退出',
        cancelButtonText: '继续工作',
        closeOnClickModal: false,
        autofocus: false,
        distinguishCancelAndClose: true
      }
    )
  } catch (action) {
    if (action === 'cancel' || action === 'close') return
    throw action
  }
  userStore.logout()
  router.push('/login')
}
</script>

<style>
.el-message-box.admin-logout-confirm {
  width: 420px;
  max-width: calc(100vw - 32px);
  padding: 24px;
  border: 1px solid rgba(255, 255, 255, 0.65) !important;
  border-radius: 24px !important;
  box-shadow: 0 24px 80px rgba(35, 43, 36, 0.22) !important;
}

.admin-logout-confirm .el-message-box__header {
  padding: 0 28px 12px 0;
}

.admin-logout-confirm .el-message-box__title {
  color: var(--admin-muted);
  font-size: 13px;
  font-weight: 600;
}

.admin-logout-confirm .el-message-box__headerbtn {
  top: 20px;
  right: 20px;
}

.admin-logout-confirm .el-message-box__content {
  padding: 8px 0 24px;
}

.logout-confirm-content {
  text-align: center;
}

.logout-confirm-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  margin: 0 auto 20px;
  border-radius: 20px;
  color: #a95600;
  background: #f0d1a8;
  box-shadow: inset 2px 2px 5px #d8bc97, inset -2px -2px 5px #ffe6c7;
}

.logout-confirm-icon svg {
  width: 28px;
  height: 28px;
}

.logout-confirm-heading {
  margin: 0 0 10px;
  color: var(--admin-text);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
}

.logout-confirm-description {
  max-width: 310px;
  margin: 0 auto;
  color: var(--admin-muted);
  font-size: 14px;
  line-height: 1.8;
}

.admin-logout-confirm .el-message-box__btns {
  display: flex;
  gap: 12px;
  padding: 0;
}

.admin-logout-confirm .el-message-box__btns .el-button {
  flex: 1;
  height: 44px;
  margin: 0;
  border-radius: 12px;
  font-size: 14px;
}
</style>

<style scoped>
.layout-container {
  min-height: 100vh;
  background: var(--admin-bg);
}

.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  background: var(--admin-secondary);
  border-right: 0;
  box-shadow:
    8px 0 18px rgba(190, 194, 188, 0.72),
    -4px 0 12px rgba(250, 251, 248, 0.72);
  overflow-x: hidden;
  transition: width 0.2s ease;
}

.logo {
  height: 88px;
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 20px 22px;
  border-bottom: 0;
  box-shadow:
    inset 0 -4px 8px rgba(194, 197, 191, 0.48),
    inset 0 4px 8px rgba(250, 251, 248, 0.68);
}

.logo-mark {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 12px;
  color: #c56600;
  background: var(--admin-secondary);
  box-shadow:
    5px 5px 10px var(--admin-shadow-dark),
    -5px -5px 10px var(--admin-shadow-light);
}

.logo-mark .el-icon {
  font-size: 23px;
}

.logo-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.logo-copy strong {
  font-family: 'JetBrains Mono', monospace;
  font-size: 18px;
  letter-spacing: 0.08em;
}

.logo-copy span {
  margin-top: 3px;
  color: var(--admin-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

:deep(.el-menu) {
  padding: 14px 12px;
  background: transparent;
  border-right: none;
}

:deep(.el-menu-item) {
  height: 48px;
  margin-bottom: 6px;
  padding: 0 15px !important;
  border-radius: 12px;
  color: #4f504d;
  font-weight: 600;
  transition: box-shadow 0.3s ease-in-out, color 0.3s ease-in-out;
}

:deep(.el-menu-item .el-icon) {
  margin-right: 13px;
  color: var(--admin-muted);
  font-size: 20px;
}

:deep(.el-menu-item:hover) {
  color: #b65f00;
  background: var(--admin-secondary);
  box-shadow:
    3px 3px 7px var(--admin-shadow-dark),
    -3px -3px 7px var(--admin-shadow-light);
}

:deep(.el-menu-item.is-active) {
  color: #a95600;
  background: var(--admin-secondary);
  box-shadow:
    inset 4px 4px 8px var(--admin-shadow-dark),
    inset -4px -4px 8px var(--admin-shadow-light);
}

:deep(.el-menu-item.is-active .el-icon) {
  color: var(--admin-primary);
}

.header {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 76px;
  padding: 0 30px;
  background: var(--admin-bg);
  border-bottom: 0;
  box-shadow:
    0 7px 15px rgba(190, 194, 188, 0.6),
    0 -5px 12px rgba(250, 251, 248, 0.64);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-identity {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.page-identity strong {
  font-size: 18px;
  letter-spacing: -0.02em;
}

:deep(.el-breadcrumb__inner) {
  color: var(--admin-muted) !important;
  font-size: 11px;
  font-weight: 600 !important;
  letter-spacing: 0.06em;
}

.admin-badge {
  padding: 6px 11px;
  border: 0;
  border-radius: 999px;
  color: #a95400;
  background: var(--admin-bg);
  box-shadow:
    inset 3px 3px 6px var(--admin-shadow-dark),
    inset -3px -3px 6px var(--admin-shadow-light);
  font-size: 11px;
  font-weight: 700;
}

.user-menu {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px 6px 6px;
  border: 0;
  border-radius: 999px;
  background: var(--admin-card);
  box-shadow:
    5px 5px 10px var(--admin-shadow-dark),
    -5px -5px 10px var(--admin-shadow-light);
  transition: box-shadow 0.3s ease-in-out;
}

.user-menu:hover {
  box-shadow:
    3px 3px 6px var(--admin-shadow-dark),
    -3px -3px 6px var(--admin-shadow-light);
}

.user-menu:active {
  box-shadow:
    inset 3px 3px 6px var(--admin-shadow-dark),
    inset -3px -3px 6px var(--admin-shadow-light);
}

.user-avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #884900;
  background: #f0d1a8;
  box-shadow:
    inset 2px 2px 4px #c3b298,
    inset -2px -2px 4px #fff0d4;
  font-size: 13px;
  font-weight: 800;
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  color: var(--admin-text);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main {
  min-width: 0;
  padding: 28px 30px 40px;
  background: var(--admin-bg);
}

@media (prefers-reduced-motion: reduce) {
  .sidebar,
  :deep(.el-menu-item),
  .user-menu {
    transition-duration: 0.01ms !important;
  }
}

@media (max-width: 1100px) {
  .sidebar {
    width: 78px !important;
  }

  .logo {
    justify-content: center;
    padding-inline: 18px;
  }

  .logo-copy,
  :deep(.el-menu-item span) {
    display: none;
  }

  :deep(.el-menu-item) {
    justify-content: center;
    padding: 0 !important;
  }

  :deep(.el-menu-item .el-icon) {
    margin: 0;
  }
}

@media (max-width: 700px) {
  .sidebar {
    width: 68px !important;
  }

  .logo-mark {
    width: 38px;
    height: 38px;
  }

  .header {
    height: 68px;
    padding: 0 14px;
  }

  .main {
    padding: 16px 12px 28px;
  }

  .admin-badge,
  .user-name,
  .page-identity :deep(.el-breadcrumb) {
    display: none;
  }

  .page-identity strong {
    font-size: 16px;
  }
}
</style>

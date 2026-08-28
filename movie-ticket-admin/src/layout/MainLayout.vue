<template>
  <el-container class="layout-container">
    <el-aside width="248px" class="sidebar">
      <div class="logo">
        <div class="logo-mark">
          <el-icon><Film /></el-icon>
        </div>
        <div class="logo-copy">
          <strong>CINEMA+</strong>
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

        <el-menu-item index="/movie">
          <el-icon><Film /></el-icon>
          <span>电影管理</span>
        </el-menu-item>

        <el-menu-item index="/showtime">
          <el-icon><Calendar /></el-icon>
          <span>排片管理</span>
        </el-menu-item>

        <el-menu-item index="/order">
          <el-icon><Tickets /></el-icon>
          <span>订单管理</span>
        </el-menu-item>

        <el-menu-item index="/user" v-if="userStore.userInfo?.role === 'ADMIN'">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
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
            <div class="admin-badge">管理员视图</div>
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
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const breadcrumbMap: Record<string, string> = {
  '/dashboard': '数据统计',
  '/cinema': '影院管理',
  '/hall': '影厅管理',
  '/movie': '电影管理',
  '/showtime': '排片管理',
  '/order': '订单管理',
  '/user': '用户管理'
}

const breadcrumb = computed(() => breadcrumbMap[route.path])
const avatarChar = computed(() => (userStore.userInfo?.nickname || '管').charAt(0).toUpperCase())

const handleLogout = async () => {
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  userStore.logout()
  router.push('/login')
}
</script>

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
  border-right: 1px solid var(--admin-border);
  overflow-x: hidden;
  transition: width 0.2s ease;
}

.logo {
  height: 88px;
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 20px 22px;
  border-bottom: 1px solid var(--admin-border);
}

.logo-mark {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 12px;
  color: #111;
  background: var(--admin-primary);
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
}

:deep(.el-menu-item .el-icon) {
  margin-right: 13px;
  color: var(--admin-muted);
  font-size: 20px;
}

:deep(.el-menu-item:hover) {
  color: var(--admin-text);
  background: rgba(17, 17, 17, 0.05);
}

:deep(.el-menu-item.is-active) {
  color: #111;
  background: var(--admin-primary);
  box-shadow: 0 6px 16px rgba(255, 132, 0, 0.2);
}

:deep(.el-menu-item.is-active .el-icon) {
  color: #111;
}

.header {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 76px;
  padding: 0 30px;
  background: rgba(242, 243, 240, 0.9);
  border-bottom: 1px solid var(--admin-border);
  backdrop-filter: blur(14px);
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
  border: 1px solid #ffd39c;
  border-radius: 999px;
  color: #a95400;
  background: var(--admin-primary-soft);
  font-size: 11px;
  font-weight: 700;
}

.user-menu {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px 6px 6px;
  border: 1px solid var(--admin-border);
  border-radius: 999px;
  background: var(--admin-card);
  transition: border-color 0.15s ease;
}

.user-menu:hover {
  border-color: var(--admin-primary);
}

.user-avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, #ff8400, #ff3f33);
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

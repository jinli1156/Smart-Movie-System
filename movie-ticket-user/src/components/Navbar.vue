<template>
  <nav class="navbar">
    <div class="navbar-container">
      <div class="navbar-left">
        <router-link to="/home" class="logo">
          <img class="logo-icon" src="/xiangzhi-ticket.png" alt="响指电影" />
          <span class="logo-text brand-wordmark" role="img" aria-label="响指电影"></span>
        </router-link>

        <div class="nav-links">
          <router-link to="/posters" class="nav-link">
            <MaterialIcon class="msr" name="auto_awesome" />
            <span>AI 海报</span>
          </router-link>
          <router-link to="/assistant" class="nav-link">
            <MaterialIcon class="msr" name="smart_toy" />
            <span>影票观</span>
          </router-link>
          <router-link to="/home" class="nav-link">
            <MaterialIcon class="msr" name="home" />
            <span>首页</span>
          </router-link>
          <router-link to="/movies" class="nav-link">
            <MaterialIcon class="msr" name="movie" />
            <span>影片</span>
          </router-link>
          <router-link to="/cinemas" class="nav-link">
            <MaterialIcon class="msr" name="theaters" />
            <span>影院</span>
          </router-link>
        </div>
      </div>

      <div class="navbar-right">
        <template v-if="userStore.isLoggedIn">
          <router-link to="/orders" class="nav-icon-btn">
            <MaterialIcon class="msr" name="receipt_long" />
            <span class="tooltip">我的订单</span>
          </router-link>

          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-menu">
              <div class="user-avatar">
                {{ avatarChar }}
              </div>
              <span class="user-name">{{ userStore.userInfo?.nickname || '用户' }}</span>
              <MaterialIcon class="msr" name="expand_more" />
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <MaterialIcon class="msr" name="person" />
                  <span>个人中心</span>
                </el-dropdown-item>
                <el-dropdown-item command="orders">
                  <MaterialIcon class="msr" name="receipt_long" />
                  <span>我的订单</span>
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <MaterialIcon class="msr" name="logout" />
                  <span>退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>

        <template v-else>
          <router-link to="/login" class="btn-login">登录</router-link>
          <router-link to="/register" class="btn-register">注册</router-link>
        </template>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
/**
 * 用户导航栏，处理账号菜单与页面跳转。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus/es/components/message/index'

const router = useRouter()
const userStore = useUserStore()

const avatarChar = computed(() => {
  const name = userStore.userInfo?.nickname || '用'
  return name.charAt(0).toUpperCase()
})

const handleCommand = (command: string) => {
  switch (command) {
    case 'profile':
      router.push('/profile')
      break
    case 'orders':
      router.push('/orders')
      break
    case 'logout':
      userStore.logout()
      ElMessage.success('已退出登录')
      break
  }
}
</script>

<style scoped>

.navbar {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.navbar-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 32px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  font-size: 20px;
  font-weight: 700;
  color: #111;
  transition: opacity 0.2s;
}

.logo:hover {
  opacity: 0.8;
}

.logo-icon {
  width: 36px;
  height: 36px;
  display: block;
  object-fit: contain;
}

.logo-text {
  font-family: 'PingFang SC', -apple-system, BlinkMacSystemFont, sans-serif;
}

.nav-links {
  display: flex;
  gap: 8px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  color: #666;
  transition: all 0.2s;
}

.nav-link:hover {
  background: #f3f4f6;
  color: #111;
}

.nav-link.router-link-active {
  background: #FFF3E0;
  color: #FF8400;
  font-weight: 600;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nav-icon-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  color: #666;
  text-decoration: none;
  transition: all 0.2s;
}

.nav-icon-btn:hover {
  background: #f3f4f6;
  color: #111;
}

.nav-icon-btn .tooltip {
  position: absolute;
  bottom: -32px;
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 8px;
  background: #111;
  color: #fff;
  font-size: 12px;
  border-radius: 4px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s;
}

.nav-icon-btn:hover .tooltip {
  opacity: 1;
}

.user-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.user-menu:hover {
  background: #f3f4f6;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #FF8400 0%, #FF3F33 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #111;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-login,
.btn-register {
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.2s;
}

.btn-login {
  color: #111;
  background: #f3f4f6;
}

.btn-login:hover {
  background: #e5e7eb;
}

.btn-register {
  color: #111;
  background: #FF8400;
}

.btn-register:hover {
  background: #e67700;
}

.msr {
  font-family: 'Material Symbols Rounded';
  font-weight: normal;
  font-style: normal;
  font-size: 20px;
  line-height: 1;
  letter-spacing: normal;
  text-transform: none;
  display: inline-block;
  white-space: nowrap;
  word-wrap: normal;
  direction: ltr;
  font-feature-settings: 'liga';
  -webkit-font-smoothing: antialiased;
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
}

:deep(.el-dropdown-menu__item .msr) {
  font-size: 18px;
  color: #666;
}

:deep(.el-dropdown-menu__item:hover .msr) {
  color: #FF8400;
}

@media (max-width: 768px) {
  .navbar-container {
    padding: 0 16px;
  }

  .nav-links {
    display: none;
  }

  .logo-text {
    display: none;
  }

  .user-name {
    display: none;
  }
}
</style>

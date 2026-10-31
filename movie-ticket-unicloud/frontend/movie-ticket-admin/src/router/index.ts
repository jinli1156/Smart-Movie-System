/**
 * 路由配置与访问守卫：组织功能页面，处理登录状态及页面访问权限。
 */
import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue')
      },
      {
        path: 'cinema',
        name: 'Cinema',
        component: () => import('@/views/Cinema.vue')
      },
      {
        path: 'hall',
        name: 'Hall',
        component: () => import('@/views/Hall.vue')
      },
      {
        path: 'movie',
        name: 'Movie',
        component: () => import('@/views/Movie.vue'),
        meta: { role: 'ADMIN' }
      },
      {
        path: 'showtime',
        name: 'Showtime',
        component: () => import('@/views/Showtime.vue')
      },
      { path: 'listing', name: 'ListingWorkspace', component: () => import('@/views/ListingWorkspace.vue'), meta: { manager: true } },
      { path: 'marketing/studio', name: 'MarketingStudio', component: () => import('@/views/MarketingStudio.vue'), meta: { manager: true } },
      { path: 'marketing/tasks', name: 'MarketingTasks', component: () => import('@/views/MarketingTasks.vue'), meta: { manager: true } },
      { path: 'marketing/review', name: 'MarketingReview', component: () => import('@/views/MarketingReview.vue'), meta: { manager: true } },
      {
        path: 'smart-scheduling',
        name: 'SmartScheduling',
        component: () => import('@/views/SmartScheduling.vue')
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/Order.vue')
      },
      {
        path: 'coupon-code',
        name: 'CouponCode',
        component: () => import('@/views/CouponCode.vue'),
        meta: { role: 'ADMIN' }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/User.vue'),
        meta: { role: 'ADMIN' }
      },
      {
        path: 'recommendation-evaluation',
        name: 'RecommendationEvaluation',
        component: () => import('@/views/RecommendationEvaluation.vue'),
        meta: { role: 'ADMIN' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    next('/login')
  } else if (to.path === '/login' && userStore.isLoggedIn) {
    next('/dashboard')
  } else if (to.meta.manager && !['ADMIN', 'CINEMA_ADMIN'].includes(userStore.userInfo?.role || '')) {
    next('/dashboard')
  } else if (to.meta.role && userStore.userInfo?.role !== to.meta.role) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router

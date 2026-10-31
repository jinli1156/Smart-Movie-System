/**
 * 路由配置与访问守卫：组织功能页面，处理登录状态及页面访问权限。
 */
import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/posters',
    name: 'PosterStudio',
    component: () => import('@/views/PosterStudio.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/assistant',
    name: 'MovieAssistant',
    component: () => import('@/views/Assistant.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    redirect: '/home'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/movies',
    name: 'Movies',
    component: () => import('@/views/Movies.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/cinemas',
    name: 'Cinemas',
    component: () => import('@/views/Cinemas.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/cinema/:id',
    name: 'CinemaDetail',
    component: () => import('@/views/CinemaDetail.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/movie/:id',
    name: 'MovieDetail',
    component: () => import('@/views/MovieDetail.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/showtime/:id/seats',
    name: 'SeatSelection',
    component: () => import('@/views/SeatSelection.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/order/confirm',
    name: 'OrderConfirm',
    component: () => import('@/views/OrderConfirm.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/order/pay/:orderNo',
    name: 'OrderPay',
    component: () => import('@/views/OrderPay.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/orders',
    name: 'OrderList',
    component: () => import('@/views/OrderList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/order/:orderNo',
    name: 'OrderDetail',
    component: () => import('@/views/OrderDetail.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: () => import('@/views/Favorites.vue'),
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    // Updating filters/date in the query string is not a page change.
    if (to.path === from.path) return false
    return { top: 0, left: 0 }
  }
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if ((to.path === '/login' || to.path === '/register') && userStore.isLoggedIn) {
    next('/home')
  } else {
    next()
  }
})

export default router

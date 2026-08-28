import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    next('/login')
  } else if ((to.path === '/login' || to.path === '/register') && userStore.isLoggedIn) {
    next('/home')
  } else {
    next()
  }
})

export default router

import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import MainLayout from '@/layout/MainLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: MainLayout,
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
        component: () => import('@/views/Movie.vue')
      },
      {
        path: 'showtime',
        name: 'Showtime',
        component: () => import('@/views/Showtime.vue')
      },
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
        path: 'user',
        name: 'User',
        component: () => import('@/views/User.vue'),
        meta: { role: 'ADMIN' }
      }
    ]
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
  } else if (to.path === '/login' && userStore.isLoggedIn) {
    next('/dashboard')
  } else if (to.meta.role && userStore.userInfo?.role !== to.meta.role) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router

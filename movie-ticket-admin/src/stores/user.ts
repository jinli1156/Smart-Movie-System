/**
 * 用户会话状态：保存令牌和资料，同步本地缓存，并统一处理登录与退出。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { UserInfo } from '@/types/auth'
import { authApi } from '@/api/auth'
import router from '@/router'
import { clearPageStates } from '@/utils/pageState'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(
    localStorage.getItem('userInfo')
      ? JSON.parse(localStorage.getItem('userInfo')!)
      : null
  )

  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  const setUserInfo = (info: UserInfo) => {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  const login = async (username: string, password: string, captchaId: string, captcha: string) => {
    const res = await authApi.login({ username, password, captchaId, captcha })
    setToken(res.token)
    setUserInfo(res.userInfo)
    return res
  }

  const logout = () => {
    token.value = ''
    clearPageStates()
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    router.push('/login')
  }

  const isLoggedIn = computed(() => !!token.value)

  return {
    token,
    userInfo,
    setToken,
    setUserInfo,
    login,
    logout,
    isLoggedIn
  }
})

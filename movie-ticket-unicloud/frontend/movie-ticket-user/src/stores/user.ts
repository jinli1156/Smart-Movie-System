import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo, UserProfileUpdate } from '@/types/auth'
import { authApi } from '@/api/auth'
import request from '@/utils/request'
import router from '@/router'

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

  const isTokenUsable = (value: string) => {
    if (!value) return false
    try {
      const payloadPart = value.split('.')[1]
      if (!payloadPart) return false
      const normalized = payloadPart.replace(/-/g, '+').replace(/_/g, '/')
      const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
      const payload = JSON.parse(atob(padded)) as { exp?: number }
      return typeof payload.exp === 'number' && payload.exp * 1000 > Date.now()
    } catch {
      return false
    }
  }

  const clearSession = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  const login = async (username: string, password: string, captchaId: string, captcha: string) => {
    const res = await authApi.login({ username, password, captchaId, captcha })
    setToken(res.token)
    setUserInfo(res.userInfo)
    return res
  }

  const logout = () => {
    clearSession()
    router.push('/login')
  }

  const updateUserInfo = async (data: UserProfileUpdate) => {
    try {
      const res = await request.put<any, UserInfo>('/user/profile', data)
      setUserInfo(res)
      return res
    } catch (error) {
      throw error
    }
  }

  const isLoggedIn = computed(() => isTokenUsable(token.value))

  return {
    token,
    userInfo,
    setToken,
    setUserInfo,
    login,
    logout,
    clearSession,
    updateUserInfo,
    isLoggedIn
  }
})

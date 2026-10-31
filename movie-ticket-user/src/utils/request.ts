/**
 * 统一 HTTP 请求入口，附加登录令牌、解包业务响应并处理登录失效及错误提示。
 */
import axios, { AxiosInstance, AxiosResponse } from 'axios'
import { useUserStore } from '@/stores/user'

const showError = async (message: string) => {
  const { ElMessage } = await import('element-plus/es/components/message/index')
  ElMessage.error(message)
}

const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { code, message, data } = response.data
    if (code === 200) {
      return data
    } else {
      if (!response.config.silent) {
        void showError(message || '请求失败')
      }
      return Promise.reject(new Error(message))
    }
  },
  (error) => {
    const silent = Boolean(error.config?.silent)
    if (!silent && error.response) {
      const { status } = error.response
      if (status === 401) {
        void showError('未登录或登录已过期')
        const userStore = useUserStore()
        userStore.logout()
      } else if (status === 403) {
        void showError('权限不足')
      } else {
        void showError(error.response.data?.message || '请求失败')
      }
    } else if (!silent) {
      void showError('网络错误')
    }
    return Promise.reject(error)
  }
)

export default request

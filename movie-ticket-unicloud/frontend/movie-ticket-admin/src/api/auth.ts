import request from '@/utils/request'
import type { LoginRequest, RegisterRequest, LoginResponse } from '@/types/auth'

export const authApi = {
  getCaptcha() {
    return request.get<any, { captchaId: string; image: string; expiresIn: number }>('/auth/captcha')
  },
  login(data: LoginRequest) {
    return request.post<any, LoginResponse>('/auth/login', data)
  },

  register(data: RegisterRequest) {
    return request.post('/auth/register', data)
  }
}

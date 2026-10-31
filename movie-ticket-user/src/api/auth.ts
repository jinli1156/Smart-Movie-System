/**
 * 登录认证与验证码接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { LoginRequest, RegisterRequest, LoginResponse } from '@/types/auth'

export const authApi = {
  /** 获取一次性图形验证码，登录时连同挑战 ID 一起提交。 */
  getCaptcha() {
    return request.get<any, { captchaId: string; image: string; expiresIn: number }>('/auth/captcha', { silent: true })
  },
  /** 校验验证码及账号凭据，成功后返回 JWT 和用户资料。 */
  login(data: LoginRequest) {
    return request.post<any, LoginResponse>('/auth/login', data)
  },

  /** 注册普通用户账号，并初始化新用户权益。 */
  register(data: RegisterRequest) {
    return request.post('/auth/register', data)
  }
}

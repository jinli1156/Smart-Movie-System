export interface LoginRequest {
  captchaId: string
  captcha: string
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname: string
  phone?: string
}

export interface UserInfo {
  cinemaId?: number | null
  id: number
  username: string
  nickname: string
  phone?: string
  email?: string
  avatar?: string
  role: string
}

export interface LoginResponse {
  token: string
  userInfo: UserInfo
}

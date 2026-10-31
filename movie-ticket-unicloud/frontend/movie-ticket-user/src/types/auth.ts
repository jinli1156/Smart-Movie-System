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
  id: number
  username: string
  nickname: string
  phone?: string
  email?: string
  avatar?: string
  role: string
  gender?: number
  birthday?: string
  city?: string
  address?: string
  favoriteGenres?: string[]
  signature?: string
  points?: number
}

export interface LoginResponse {
  token: string
  userInfo: UserInfo
}

export interface UserProfileUpdate {
  nickname: string
  gender?: number
  birthday?: string
  city?: string
  address?: string
  favoriteGenres?: string[]
  signature?: string
}

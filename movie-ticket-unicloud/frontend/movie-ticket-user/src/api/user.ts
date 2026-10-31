import request from '@/utils/request'
import type { UserInfo, UserProfileUpdate } from '@/types/auth'

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

export const userApi = {
  getCurrent() {
    return request.get<any, any>('/user/me')
  },

  getPoints() {
    return request.get<any, number>('/user/points')
  },

  updateProfile(data: UserProfileUpdate) {
    return request.put<any, UserInfo>('/user/profile', data)
  },

  changePassword(data: ChangePasswordPayload) {
    return request.post<any, void>('/user/change-password', data)
  }
}

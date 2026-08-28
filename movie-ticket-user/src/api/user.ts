import request from '@/utils/request'

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

export const userApi = {
  getCurrent() {
    return request.get<any, any>('/user/me')
  },

  updateProfile(data: { nickname?: string; email?: string; avatar?: string }) {
    return request.put<any, void>('/user/profile', data)
  },

  changePassword(data: ChangePasswordPayload) {
    return request.post<any, void>('/user/change-password', data)
  }
}

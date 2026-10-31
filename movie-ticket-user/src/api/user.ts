/**
 * 账号与个人资料接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { UserInfo, UserProfileUpdate } from '@/types/auth'

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

export const userApi = {
  /** 读取当前登录用户资料。 */
  getCurrent() {
    return request.get<any, any>('/user/me')
  },

  /** 读取当前登录用户的积分余额。 */
  getPoints() {
    return request.get<any, number>('/user/points')
  },

  /** 更新本人允许编辑的资料字段。 */
  updateProfile(data: UserProfileUpdate) {
    return request.put<any, UserInfo>('/user/profile', data)
  },

  /** 验证旧密码后更新本人密码。 */
  changePassword(data: ChangePasswordPayload) {
    return request.post<any, void>('/user/change-password', data)
  }
}

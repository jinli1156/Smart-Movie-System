/**
 * 账号与个人资料接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { User, PageResult } from '@/types'

export const userApi = {
  /** 按筛选条件分页查询用户，供列表页展示。 */
  getPage(params: {
    page: number
    size: number
    keyword?: string
    role?: string
    status?: number
  }) {
    return request.get<any, PageResult<User>>('/user/page', { params })
  },

  /** 根据 ID 查询用户详情。 */
  getById(id: number) {
    return request.get<any, User>(`/user/${id}`)
  },

  /** 更新用户，由业务层校验当前操作权限与业务条件。 */
  update(id: number, data: Partial<User>) {
    return request.put(`/user/${id}`, data)
  },

  /** 修改用户状态，具体可用状态和操作限制由业务层校验。 */
  updateStatus(id: number, status: number) {
    return request.put(`/user/${id}/status`, null, { params: { status } })
  },

  /** 由平台管理员重置指定账号密码。 */
  resetPassword(id: number, password: string) {
    return request.put(`/user/${id}/reset-password`, null, { params: { password } })
  },

  /** 删除用户，由业务层检查权限及关联数据限制。 */
  delete(id: number) {
    return request.delete(`/user/${id}`)
  }
}

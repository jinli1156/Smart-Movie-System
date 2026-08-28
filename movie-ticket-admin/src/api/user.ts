import request from '@/utils/request'
import type { User, PageResult } from '@/types'

export const userApi = {
  getPage(params: {
    page: number
    size: number
    keyword?: string
    role?: string
    status?: number
  }) {
    return request.get<any, PageResult<User>>('/user/page', { params })
  },

  getById(id: number) {
    return request.get<any, User>(`/user/${id}`)
  },

  update(id: number, data: Partial<User>) {
    return request.put(`/user/${id}`, data)
  },

  updateStatus(id: number, status: number) {
    return request.put(`/user/${id}/status`, null, { params: { status } })
  },

  resetPassword(id: number, password: string) {
    return request.put(`/user/${id}/reset-password`, null, { params: { password } })
  },

  delete(id: number) {
    return request.delete(`/user/${id}`)
  }
}

import request from '@/utils/request'
import type { Cinema } from '@/types'

export const cinemaApi = {
  getAll(silent = false) {
    return request.get<any, Cinema[]>('/cinema/all', { silent })
  },

  getById(id: number) {
    return request.get<any, Cinema>(`/cinema/${id}`)
  }
}

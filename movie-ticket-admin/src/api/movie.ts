/**
 * 影片目录与票房信息接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import type { Movie, PageResult } from '@/types'

export const movieApi = {
  /** 按筛选条件分页查询电影，供列表页展示。 */
  getPage(params: { page: number; size: number; keyword?: string; status?: number }) {
    return request.get<any, PageResult<Movie>>('/movie/page', { params })
  },

  /** 查询当前热映影片列表。 */
  getNowShowing() {
    return request.get<any, Movie[]>('/movie/now-showing')
  },

  /** 查询即将上映影片列表。 */
  getComingSoon() {
    return request.get<any, Movie[]>('/movie/coming-soon')
  },

  /** 根据 ID 查询电影详情。 */
  getById(id: number) {
    return request.get<any, Movie>(`/movie/${id}`)
  },

  /** 新增电影，由业务层执行参数及关联数据校验。 */
  create(data: Movie) {
    return request.post('/movie', data)
  },

  /** 更新电影，由业务层校验当前操作权限与业务条件。 */
  update(id: number, data: Movie) {
    return request.put(`/movie/${id}`, data)
  },

  /** 删除电影，由业务层检查权限及关联数据限制。 */
  delete(id: number) {
    return request.delete(`/movie/${id}`)
  },

  /** 修改电影状态，具体可用状态和操作限制由业务层校验。 */
  updateStatus(id: number, status: number) {
    return request.put(`/movie/${id}/status`, null, { params: { status } })
  }
}

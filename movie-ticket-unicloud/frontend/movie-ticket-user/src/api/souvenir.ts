import { fetchCloudFile, apiUrl } from '@/utils/api-url'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

export interface SouvenirTicket {
  id: string
  style: string
  note: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  facts: { movieTitle: string; cinemaName: string; hallName: string; showtime: string }
  headline: string | null
  caption: string | null
  errorMessage: string | null
  retryable: boolean
  createdAt: string
}
export interface SouvenirCapabilities { available: boolean; theme: string; dailyLimit: number; message: string }

export const souvenirApi = {
  capabilities: () => request.get<any, SouvenirCapabilities>('/souvenirs/capabilities', { silent: true, timeout: 110_000 }),
  list: (orderNo: string) => request.get<any, SouvenirTicket[]>(`/souvenirs/orders/${encodeURIComponent(orderNo)}`, { silent: true, timeout: 110_000 }),
  create: (orderNo: string, note: string, retry = false) =>
    request.post<any, SouvenirTicket>(`/souvenirs/orders/${encodeURIComponent(orderNo)}`, { note, retry }, { silent: true, timeout: 110_000 }),
  async image(id: string) {
    const response = await fetchCloudFile(apiUrl(`/api/souvenirs/${encodeURIComponent(id)}/file`), {
      headers: { Authorization: `Bearer ${useUserStore().token}` }, cache: 'no-store'
    })
    if (!response.ok) throw new Error('纪念票图片暂时无法加载，请重新加载')
    const blob = await response.blob()
    if (!blob.type.startsWith('image/')) throw new Error('纪念票图片格式异常')
    return blob
  }
}

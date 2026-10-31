import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

export interface PosterStyle { id: string; name: string; description: string }
export interface PosterCapabilities {
  available: boolean; photoSupported: boolean; styles: PosterStyle[]
  dailyLimit: number; remaining: number; message: string
}
export interface PersonalPoster {
  id: string; movieTitle: string; style: string; prompt: string; ratio: string; hasPortrait: boolean
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  errorMessage: string | null; retryable: boolean; createdAt: string
}
export interface PosterInput { requestId: string; movieTitle: string; style: string; prompt: string; ratio: string }
export const posterApi = {
  capabilities: () => request.get<any, PosterCapabilities>('/posters/capabilities', { silent: true }),
  list: () => request.get<any, PersonalPoster[]>('/posters', { silent: true }),
  create(input: PosterInput, photo: File | null) {
    const form = new FormData()
    form.append('data', new Blob([JSON.stringify(input)], { type: 'application/json' }))
    if (photo) form.append('photo', photo)
    return request.post<any, PersonalPoster>('/posters', form, { silent: true, timeout: 30_000 })
  },
  retry: (id: string) => request.post<any, PersonalPoster>(`/posters/${encodeURIComponent(id)}/retry`, {}, { silent: true }),
  async image(id: string, signal?: AbortSignal) {
    const response = await fetch(`/api/posters/${encodeURIComponent(id)}/file`, {
      headers: { Authorization: `Bearer ${useUserStore().token}` }, cache: 'no-store', signal
    })
    if (!response.ok) throw new Error('海报暂时无法加载，请重新加载')
    const blob = await response.blob()
    if (blob.type !== 'image/png') throw new Error('海报图片格式异常')
    return blob
  }
}

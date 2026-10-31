/**
 * 助手对话、会话历史与确认下单接口封装，统一复用请求客户端及业务类型。
 */
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

export interface AssistantHistoryMessage { role: 'user' | 'assistant'; content: string }
export interface WatchPlan { criteria: Record<string, any>; options: { kind: 'movie' | 'showtime'; id: number; movieId?: number; title: string }[] }
export interface MovieExclusions { excludedGenres?: string[]; excludedMovieIds?: number[] }
export interface CheckoutDraft {
  criteria?: Record<string, unknown>;
  moviePoster?: string;
  draftId: string; expiresAt: string; showtimeId: number; movieTitle: string; cinemaName: string; hallName: string;
  startTime: string; endTime: string; people: number; seats: string[]; originalPrice: number;
  bestPlan: { totalPrice: number; couponId?: number; couponName: string; couponDiscount: number; pointsUsed: number; pointsDiscount: number };
  usePoints: boolean; useCoupons: boolean; notice: string;
}
export interface CheckoutReceipt { orderNo: string; status: number; usePoints: boolean; useCoupons: boolean; couponId?: number; message: string }
export interface ToolResult {
  ok: boolean
  source?: string
  queriedAt?: string
  code?: string
  message?: string
  data?: Record<string, any> | any[]
}
export interface AssistantChatResponse {
  watchPlan?: WatchPlan;
  conversationId?: string
  answer: string
  suggestions: string[]
  source: 'model' | 'unavailable'
  toolResults: ToolResult[]
}
export interface AssistantCapabilities { configured: boolean; model: string; vision: boolean; streaming: boolean }
export interface Conversation { id: string; title: string; updatedAt: string }
export interface SavedTurn { id: number; question: string; answer: string; source: 'model' | 'unavailable'; status: string; toolResults: ToolResult[]; suggestions: string[]; createdAt: string }
export interface ConversationDetail { conversation: Conversation; turns: SavedTurn[]; hasMore: boolean; watchPlan?: WatchPlan }
export type AssistantEvent =
  | { event: 'conversation'; data: { id: string } }
  | { event: 'answer_reset'; data: Record<string, never> }
  | { event: 'delta'; data: { text: string } }
  | { event: 'status'; data: { message: string } }
  | { event: 'tool_result'; data: ToolResult }
  | { event: 'done'; data: AssistantChatResponse }

const payload = (message: string, history: AssistantHistoryMessage[], conversationId?: string, clientMessageId?: string) => ({
  message,
  ...(conversationId ? { conversationId } : {}),
  ...(clientMessageId ? { clientMessageId } : {}),
  history: history.filter(m => m.content.trim()).slice(-10).map(m => ({ role: m.role, content: m.content.slice(0, 4000) }))
})

export const assistantApi = {
  /** 生成五分钟有效的订单确认卡；此时不锁座，确认时仍需重新核验。 */
  prepareCheckout(criteria: Record<string, unknown>, seats?: string[]) { return request.post<any, CheckoutDraft>('/assistant/checkout/prepare', { criteria, seats }, { silent: true }) },
  /** 校验本人确认卡及有效期，重新核验座位后幂等创建待支付订单。 */
  confirmCheckout(id: string) { return request.post<any, CheckoutReceipt>(`/assistant/checkout/${encodeURIComponent(id)}/confirm`, {}, { silent: true }) },
  /** 读取当前用户保存的长期排除偏好。 */
  preferences() { return request.get<any, MovieExclusions>('/assistant/preferences', { silent: true }) },
  /** 校验并保存当前用户的长期排除偏好。 */
  savePreferences(value: MovieExclusions) { return request.put<any, MovieExclusions>('/assistant/preferences', value, { silent: true }) },
  /** 分页查询本人助手会话摘要。 */
  conversations(page = 1) { return request.get<any, { items: Conversation[]; hasMore: boolean; page: number }>('/assistant/conversations', { params: { page }, silent: true }) },
  /** 读取指定会话，可通过游标加载更早轮次。 */
  conversation(id: string, before?: number) { return request.get<any, ConversationDetail>(`/assistant/conversations/${encodeURIComponent(id)}`, { params: { before }, silent: true }) },
  /** 删除本人指定的助手会话。 */
  deleteConversation(id: string) { return request.delete(`/assistant/conversations/${encodeURIComponent(id)}`, { silent: true }) },
  /** 返回助手模型配置及支持能力，供页面决定可用交互。 */
  capabilities() { return request.get<any, AssistantCapabilities>('/assistant/capabilities', { silent: true }) },
  /** 执行一轮助手对话，返回回答、工具结果和后续建议。 */
  chat(message: string, history: AssistantHistoryMessage[]) {
    return request.post<any, AssistantChatResponse>('/assistant/chat', payload(message, history), { silent: true, timeout: 180000 })
  },
  /** 携带登录令牌读取 SSE 流，按事件类型分发回答、工具结果和完成消息。 */
  async stream(message: string, history: AssistantHistoryMessage[], onEvent: (event: AssistantEvent) => void, signal: AbortSignal, conversationId?: string, clientMessageId?: string, watchPlan?: WatchPlan, planPatch?: Record<string, unknown>) {
    const token = useUserStore().token
    const response = await fetch('/api/assistant/chat/stream', {
      method: 'POST', signal,
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify({ ...payload(message, history, conversationId, clientMessageId),
        ...(!token && watchPlan ? { guestPlan: JSON.stringify(watchPlan) } : {}), ...(planPatch ? { planPatch } : {}) })
    })
    if (!response.ok) {
      const error = await response.json().catch(() => null)
      throw new Error(error?.message || (response.status === 429 ? '小助手繁忙，请稍后重试。' : response.status === 401 ? '登录已过期，请重新登录。' : '小助手连接失败，消息可能未保存，请刷新历史确认。'))
    }
    if (!response.body || !response.headers.get('content-type')?.includes('text/event-stream')) throw new Error('未收到有效的流式响应。')
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let completed = false
    const consume = (block: string) => {
      let event = ''
      const data: string[] = []
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
      }
      if (!data.length || !['conversation', 'delta', 'status', 'tool_result', 'done', 'answer_reset'].includes(event)) return
      onEvent({ event, data: JSON.parse(data.join('\n')) } as AssistantEvent)
      if (event === 'done') completed = true
    }
    try {
      while (!completed) {
        const { done, value } = await reader.read()
        buffer += decoder.decode(value, { stream: !done })
        // Normalize at frame boundaries without losing CRLF split across network chunks.
        let boundary: RegExpExecArray | null
        while ((boundary = /\r?\n\r?\n/.exec(buffer))) {
          consume(buffer.slice(0, boundary.index).replace(/\r\n/g, '\n'))
          buffer = buffer.slice(boundary.index + boundary[0].length)
        }
        if (buffer.length > 200000) throw new Error('响应过长，请重新提问。')
        if (done) break
      }
      if (!completed) throw new Error('连接中断，回答未完成，请重试。')
    } finally { await reader.cancel().catch(() => {}); reader.releaseLock() }
  }
}

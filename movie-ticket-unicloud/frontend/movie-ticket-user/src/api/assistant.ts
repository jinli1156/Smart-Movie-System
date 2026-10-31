import request from '@/utils/request'

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
  prepareCheckout(criteria: Record<string, unknown>, seats?: string[]) { return request.post<any, CheckoutDraft>('/assistant/checkout/prepare', { criteria, seats }, { silent: true }) },
  confirmCheckout(id: string) { return request.post<any, CheckoutReceipt>(`/assistant/checkout/${encodeURIComponent(id)}/confirm`, {}, { silent: true }) },
  preferences() { return request.get<any, MovieExclusions>('/assistant/preferences', { silent: true }) },
  savePreferences(value: MovieExclusions) { return request.put<any, MovieExclusions>('/assistant/preferences', value, { silent: true }) },
  conversations(page = 1) { return request.get<any, { items: Conversation[]; hasMore: boolean; page: number }>('/assistant/conversations', { params: { page }, silent: true }) },
  conversation(id: string, before?: number) { return request.get<any, ConversationDetail>(`/assistant/conversations/${encodeURIComponent(id)}`, { params: { before }, silent: true }) },
  deleteConversation(id: string) { return request.delete(`/assistant/conversations/${encodeURIComponent(id)}`, { silent: true }) },
  capabilities() { return request.get<any, AssistantCapabilities>('/assistant/capabilities', { silent: true }) },
  chat(message: string, history: AssistantHistoryMessage[]) {
    return request.post<any, AssistantChatResponse>('/assistant/chat', payload(message, history), { silent: true, timeout: 180000 })
  },
  async stream(message: string, history: AssistantHistoryMessage[], onEvent: (event: AssistantEvent) => void, signal: AbortSignal, conversationId?: string, clientMessageId?: string, watchPlan?: WatchPlan, planPatch?: Record<string, unknown>) {
    const response = await request.post<any, AssistantChatResponse>('/assistant/chat', {
      ...payload(message, history, conversationId, clientMessageId),
      ...(watchPlan ? { guestPlan: JSON.stringify(watchPlan) } : {}),
      ...(planPatch ? { planPatch } : {})
    }, { signal, silent: true, timeout: 180000 })
    if (response.conversationId) onEvent({ event: 'conversation', data: { id: response.conversationId } })
    onEvent({ event: 'done', data: response })
  }
}

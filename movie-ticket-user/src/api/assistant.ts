import request from '@/utils/request'

export interface AssistantHistoryMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AssistantChatResponse {
  answer: string
  suggestions: string[]
  source: 'model' | 'local'
}

export const assistantApi = {
  chat(message: string, history: AssistantHistoryMessage[]) {
    return request.post<any, AssistantChatResponse>('/assistant/chat', {
      message,
      history: history.slice(-10)
    }, { silent: true })
  }
}

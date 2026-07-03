import request from './request'

export interface Conversation {
  id: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface Message {
  id: number
  role: string
  content: string
  references?: Reference[]
  createdAt: string
}

export interface Reference {
  documentId: number
  documentTitle: string
  chunkId: number
  contentSnippet: string
  score: number
}

export function sendMessage(conversationId: number | null, question: string) {
  // 使用 fetch 处理 SSE 流式响应
  const token = localStorage.getItem('accessToken')
  return fetch('http://localhost:8080/api/chat/send', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ conversationId, question })
  })
}

export function getConversations() {
  return request.get<Conversation[]>('/chat/conversations')
}

export function getConversationMessages(conversationId: number) {
  return request.get<Message[]>(`/chat/conversations/${conversationId}`)
}

export function renameConversation(id: number, title: string) {
  return request.put(`/chat/conversations/${id}`, { title })
}

export function deleteConversation(id: number) {
  return request.delete(`/chat/conversations/${id}`)
}

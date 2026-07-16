import request from './request'

export interface UserMemory {
  id: number
  userId: number
  memoryType: string // preference | summary | fact
  content: string
  sourceConversationId?: number
  importance: number
  createdAt: string
  updatedAt: string
}

export function getMemories() {
  return request.get('/memories')
}

export function updateMemory(id: number, data: { content?: string; importance?: number }) {
  return request.put(`/memories/${id}`, data)
}

export function deleteMemory(id: number) {
  return request.delete(`/memories/${id}`)
}

import request from './request'

/**
 * API 基础路径：Docker 模式使用相对路径（Nginx 反向代理），本地开发使用绝对路径
 * 与 request.ts 保持一致，避免硬编码 localhost
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090/api'

export interface Conversation {
  id: number
  title: string
  /** 置顶标记 */
  pinned?: boolean
  createdAt: string
  updatedAt: string
}

export interface Message {
  id: number
  role: string
  content: string
  references?: Reference[]
  /** 拒答标志：检索未命中相关资料时由后端置为 true */
  refused?: boolean
  /** 用户反馈：like/dislike/null */
  feedback?: string
  createdAt: string
}

export interface Reference {
  documentId: number
  documentTitle: string
  chunkId: number
  contentSnippet: string
  score: number
}

/**
 * 发送问答消息（SSE 流式响应）
 * @param conversationId 会话 ID，null 表示新建会话
 * @param question 用户问题
 * @returns fetch Response，用于读取 SSE 流
 */
export function sendMessage(conversationId: number | null, question: string, knowledgeBaseId?: number) {
  // 使用 fetch 处理 SSE 流式响应（Axios 不支持 ReadableStream）
  const token = localStorage.getItem('accessToken')
  return fetch(`${API_BASE_URL}/chat/send`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ conversationId, question, knowledgeBaseId })
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

/**
 * 消息反馈（点赞/踩）
 * @param id 消息 ID
 * @param feedback 反馈值：like 或 dislike
 */
export function feedbackMessage(id: number, feedback: string) {
  return request.put(`/chat/messages/${id}/feedback`, { feedback })
}

/**
 * 搜索会话（按消息内容关键词）
 * @param keyword 搜索关键词，为空则返回全部会话
 */
export function searchConversations(keyword?: string) {
  return request.get<Conversation[]>('/chat/conversations/search', {
    params: keyword ? { keyword } : {}
  })
}

/**
 * 导出会话为 Markdown 文件
 * @param id 会话 ID
 * @returns Axios 响应，data 为 Markdown 文本
 */
export function exportConversation(id: number) {
  return request.get(`/chat/conversations/${id}/export`, {
    responseType: 'blob'
  })
}

/**
 * 切换会话置顶状态
 * @param id 会话 ID
 */
export function togglePinConversation(id: number) {
  return request.put(`/chat/conversations/${id}/pin`)
}

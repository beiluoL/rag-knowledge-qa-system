import request from './request'

export interface ConversationConfig {
  aiMode: string
  aiFramework: string
  ragVisualizationEnabled: boolean
  dimension: number
}

export interface UpdateConversationConfigPayload {
  aiMode?: string
  aiFramework?: string
  ragVisualizationEnabled?: boolean
}

export function getConversationConfig() {
  return request.get<ConversationConfig>('/admin/conversation-config')
}

export function updateConversationConfig(payload: UpdateConversationConfigPayload) {
  return request.put<ConversationConfig>('/admin/conversation-config', payload)
}

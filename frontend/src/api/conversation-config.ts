import request from './request'

export interface ConversationConfig {
  aiMode: string
  aiFramework: string
  ragVisualizationEnabled: boolean
  trueSseStreamingEnabled: boolean
  hybridEnabled: boolean
  rrfK: number
  dimension: number
}

export interface UpdateConversationConfigPayload {
  aiMode?: string
  aiFramework?: string
  ragVisualizationEnabled?: boolean
  trueSseStreamingEnabled?: boolean
  hybridEnabled?: boolean
  rrfK?: number
}

export function getConversationConfig() {
  return request.get<ConversationConfig>('/admin/conversation-config')
}

export function updateConversationConfig(payload: UpdateConversationConfigPayload) {
  return request.put<ConversationConfig>('/admin/conversation-config', payload)
}

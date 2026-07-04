import request from './request'

export interface AiModeInfo {
  mode: string
  dimension: string
}

export function getAiMode() {
  return request.get<AiModeInfo>('/ai-mode')
}

export function switchAiMode(mode: string) {
  return request.post<AiModeInfo & { message: string }>('/ai-mode/switch', { mode })
}

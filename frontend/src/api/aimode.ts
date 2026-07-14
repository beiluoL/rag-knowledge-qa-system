import request from './request'

export interface AiModeInfo {
  mode: string
  dimension: string
}

export interface FrameworkInfo {
  framework: string
  mode: string
  dimension: string
}

export function getAiMode() {
  return request.get<AiModeInfo>('/ai-mode')
}

export function switchAiMode(mode: string) {
  return request.post<AiModeInfo & { message: string }>('/ai-mode/switch', { mode })
}

export function getAiFramework() {
  return request.get<FrameworkInfo>('/ai-framework')
}

export function switchAiFramework(framework: string) {
  return request.post<FrameworkInfo & { message: string }>('/ai-framework/switch', { framework })
}

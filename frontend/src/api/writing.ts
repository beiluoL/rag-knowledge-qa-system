import request from './request'
import type { Reference } from './chat'

export interface WritingResult {
  article: string
  references: Reference[]
  topic: string
}

/** 基于知识库内容撰写文章 */
export function composeWriting(data: {
  topic: string
  knowledgeBaseId?: number
  outline?: string
  style?: string
  length?: number
}) {
  return request.post<WritingResult>('/writing/compose', data)
}

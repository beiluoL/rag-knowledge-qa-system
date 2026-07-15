import request from './request'

export interface KnowledgeCard {
  id: number
  userId: number
  title: string
  front: string | null
  back: string
  category: string | null
  tags: string | null
  source: 'MANUAL' | 'AI'
  knowledgeBaseId: number | null
  createdAt: string
  updatedAt: string
}

export interface CardForm {
  title: string
  front?: string
  back: string
  category?: string
  tags?: string
}

/** 列表（可选分类 / 关键词筛选） */
export function listCards(params?: { category?: string; keyword?: string }) {
  return request.get<KnowledgeCard[]>('/cards', { params })
}

/** 新建 */
export function createCard(data: CardForm) {
  return request.post<KnowledgeCard>('/cards', data)
}

/** 详情 */
export function getCard(id: number) {
  return request.get<KnowledgeCard>(`/cards/${id}`)
}

/** 更新 */
export function updateCard(id: number, data: CardForm) {
  return request.put<KnowledgeCard>(`/cards/${id}`, data)
}

/** 删除 */
export function deleteCard(id: number) {
  return request.delete(`/cards/${id}`)
}

/** AI 一键生成（按主题） */
export function generateCards(data: { topic: string; count?: number; category?: string }) {
  return request.post<KnowledgeCard[]>('/cards/generate', data)
}

/** AI 从知识库文档抽取卡片 */
export function extractCardsFromKb(knowledgeBaseId: number, count?: number, category?: string) {
  return request.post<KnowledgeCard[]>('/cards/extract-from-kb', null, { params: { knowledgeBaseId, count, category } })
}

/** 批量删除 */
export function batchDeleteCards(ids: number[]) {
  return request.post('/cards/batch-delete', { ids })
}

/** 批量移动到知识库 */
export function batchMoveCards(ids: number[], knowledgeBaseId: number | null) {
  return request.post('/cards/batch-move', { ids, knowledgeBaseId })
}

import request from './request'

export interface KnowledgeBase {
  id: number
  name: string
  description?: string
  tags?: string
  parentId?: number | null
  categoryId?: number | null
  categoryName?: string
  isSystem?: boolean
  sortOrder?: number
  ownerId?: number
  createdAt?: string
  updatedAt?: string
  children?: KbTreeNode[]
}

export interface KbTreeNode extends KnowledgeBase {
  children?: KbTreeNode[]
}

export interface KbCategory {
  id: number
  name: string
  description?: string
}

/** 扁平列表（后台兼容） */
export function listKnowledgeBases() {
  return request.get<KnowledgeBase[]>('/knowledge-bases')
}

/** 树形结构（含父子关系），用于树形选择器与管理页 */
export function getKbTree() {
  return request.get<KbTreeNode[]>('/knowledge-bases/tree')
}

/** 动态分类列表 */
export function getCategories() {
  return request.get<KbCategory[]>('/kb-categories')
}

export function createCategory(data: { name: string; description?: string }) {
  return request.post<KbCategory>('/kb-categories', data)
}

export function deleteCategory(id: number) {
  return request.delete(`/kb-categories/${id}`)
}

export function getKnowledgeBase(id: number) {
  return request.get<KnowledgeBase>(`/knowledge-bases/${id}`)
}

export function createKnowledgeBase(data: {
  name: string
  description?: string
  categoryId?: number | null
  parentId?: number | null
  tags?: string
}) {
  return request.post<KnowledgeBase>('/knowledge-bases', data)
}

export function updateKnowledgeBase(id: number, data: {
  name?: string
  description?: string
  categoryId?: number | null
  parentId?: number | null
  tags?: string
}) {
  return request.put<KnowledgeBase>(`/knowledge-bases/${id}`, data)
}

export function deleteKnowledgeBase(id: number) {
  return request.delete(`/knowledge-bases/${id}`)
}

export function getKbStats(id: number) {
  return request.get(`/knowledge-bases/${id}/stats`)
}

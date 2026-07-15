import request from './request'

export type NodeStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'

export interface LearningPathNode {
  id: number
  title: string
  description?: string
  orderIndex: number
  nodeType: string
  ref: Record<string, any>
  status: NodeStatus
  score?: number | null
  completedAt?: string | null
}

export interface LearningPathProgress {
  total: number
  completed: number
  inProgress: number
  percent: number
  status: string
}

export interface LearningPathDetail {
  id: number
  title: string
  description?: string
  knowledgeBaseId?: number | null
  status: string
  createdAt: string
  updatedAt: string
  nodes: LearningPathNode[]
  progress: LearningPathProgress
}

export interface LearningPathSummary {
  id: number
  title: string
  description?: string
  knowledgeBaseId?: number | null
  status: string
  createdAt: string
  updatedAt: string
}

export function listLearningPaths() {
  return request.get<LearningPathSummary[]>('/learning/paths')
}

export function createLearningPath(data: {
  title: string
  description?: string
  knowledgeBaseId?: number | null
}) {
  return request.post<LearningPathSummary>('/learning/paths', data)
}

export function generateLearningPath(data: { knowledgeBaseId: number; title?: string }) {
  return request.post<LearningPathSummary>('/learning/paths/generate', data)
}

export function getLearningPathDetail(id: number) {
  if (!Number.isInteger(id) || id <= 0) {
    return Promise.reject(new Error('invalid learning path id'))
  }
  return request.get<LearningPathDetail>(`/learning/paths/${id}`)
}

export function updateNodeProgress(id: number, nodeId: number, status: NodeStatus, score?: number) {
  return request.put<LearningPathDetail>(`/learning/paths/${id}/nodes/${nodeId}/progress`, { status, score })
}

export function deleteLearningPath(id: number) {
  return request.delete(`/learning/paths/${id}`)
}

/** 拖拽排序节点 */
export function reorderNodes(pathId: number, orders: { id: number; orderIndex: number }[]) {
  return request.put(`/learning/paths/${pathId}/nodes/reorder`, orders)
}

/** 节点详情（含文档内容） */
export interface NodeDetail {
  id: number; title: string; description?: string; orderIndex: number; nodeType: string
  ref?: { documentId?: number; title?: string }
  document?: { id: number; title: string; fileType: string; status: string }
  chunks?: { index: number; content: string }[]
}

export function getNodeDetail(pathId: number, nodeId: number) {
  return request.get<NodeDetail>(`/learning/paths/${pathId}/nodes/${nodeId}/detail`)
}

import request from './request'

export interface DocumentItem {
  id: number; title: string; fileName: string; fileType: string
  fileSize: number; status: string; chunkCount: number; tags?: string
  uploadedBy: number; createdAt: string
}

export interface DocumentChunk {
  id: number; documentId: number; chunkIndex: number
  content: string; tokenCount: number; hasEmbedding?: boolean
}

// ── 上传 ──
export function uploadDocument(file: File, chunkSize?: number, chunkOverlap?: number, knowledgeBaseId?: number) {
  const formData = new FormData()
  formData.append('file', file)
  if (chunkSize) formData.append('chunkSize', String(chunkSize))
  if (chunkOverlap) formData.append('chunkOverlap', String(chunkOverlap))
  if (knowledgeBaseId) formData.append('knowledgeBaseId', String(knowledgeBaseId))
  return request.post('/knowledge/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function uploadDocuments(files: File[], knowledgeBaseId?: number) {
  const formData = new FormData()
  files.forEach(f => formData.append('files', f))
  if (knowledgeBaseId) formData.append('knowledgeBaseId', String(knowledgeBaseId))
  return request.post('/knowledge/documents/upload-batch', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function importFromUrl(url: string, mode: string, knowledgeBaseId?: number) {
  return request.post('/knowledge/documents/import-url', { url, mode, knowledgeBaseId })
}

// ── 查询 ──
export function getDocuments(page = 0, size = 20, keyword?: string, status?: string) {
  return request.get('/knowledge/documents', { params: { page, size, keyword, status } })
}

export function getDocumentDetail(id: number) {
  return request.get(`/knowledge/documents/${id}`)
}

export function getStats() {
  return request.get('/knowledge/stats')
}

// ── 编辑 ──
export function updateDocument(id: number, data: { title?: string; tags?: string; description?: string }) {
  return request.put(`/knowledge/documents/${id}`, data)
}

export function deleteDocument(id: number) {
  return request.delete(`/knowledge/documents/${id}`)
}

export function reprocessDocument(id: number) {
  return request.post(`/knowledge/documents/${id}/reprocess`)
}

// ── 分块 ──
export function updateChunk(chunkId: number, content: string) {
  return request.put(`/knowledge/chunks/${chunkId}`, { content })
}

export function deleteChunk(chunkId: number) {
  return request.delete(`/knowledge/chunks/${chunkId}`)
}

// ── 批量 ──
export function batchDeleteDocuments(ids: number[]) {
  return request.post('/knowledge/documents/batch-delete', { ids })
}

export function clearAllDocuments() {
  return request.delete('/knowledge/documents/clear-all')
}

// ── 预览 ──
export function getDocumentContentUrl(id: number) {
  return `${request.defaults.baseURL}/knowledge/documents/${id}/content`
}

// ── 导出 ──
export function exportDocuments() {
  return request.get('/knowledge/documents/export')
}

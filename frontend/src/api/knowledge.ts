import request from './request'

export interface DocumentItem {
  id: number
  title: string
  fileName: string
  fileType: string
  fileSize: number
  status: string
  chunkCount: number
  uploadedBy: number
  createdAt: string
}

export interface DocumentChunk {
  id: number
  documentId: number
  chunkIndex: number
  content: string
  tokenCount: number
}

export function uploadDocument(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/knowledge/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getDocuments(page = 0, size = 20) {
  return request.get('/knowledge/documents', { params: { page, size } })
}

export function getDocumentDetail(id: number) {
  return request.get(`/knowledge/documents/${id}`)
}

export function deleteDocument(id: number) {
  return request.delete(`/knowledge/documents/${id}`)
}

export function reprocessDocument(id: number) {
  return request.post(`/knowledge/documents/${id}/reprocess`)
}

export function getStats() {
  return request.get('/knowledge/stats')
}

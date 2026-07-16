import request from './request'

export function getEvaluation(topN = 10, noHitN = 20) {
  return request.get('/admin/evaluation', { params: { topN, noHitN } })
}

export function getSatisfaction() {
  return request.get('/admin/evaluation/satisfaction')
}

export function getRetrieval() {
  return request.get('/admin/evaluation/retrieval')
}

export function getTopDocuments(topN = 10) {
  return request.get('/admin/evaluation/top-documents', { params: { topN } })
}

export function getNoHit(limit = 20) {
  return request.get('/admin/evaluation/no-hit', { params: { limit } })
}

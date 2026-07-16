import request from './request'

export type NotificationType = 'document_processed' | 'study_reminder' | 'system_announcement'

export interface AppNotification {
  id: number
  userId: number | null
  type: NotificationType | string
  title: string
  content: string
  refType?: string
  refId?: number
  isRead: boolean
  createdAt: string
}

export function getNotifications() {
  return request.get('/notifications')
}

export function getUnreadCount() {
  return request.get('/notifications/unread-count')
}

export function markRead(id: number) {
  return request.post('/notifications/mark-read', { id })
}

export function markAllRead() {
  return request.post('/notifications/mark-all-read')
}

export function adminAnnounce(data: { title: string; content: string; userId?: number }) {
  return request.post('/admin/notifications', data)
}

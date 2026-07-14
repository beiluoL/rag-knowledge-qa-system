import request from './request'

/** 管理员用户信息接口 */
export interface AdminUser {
  id: number
  username: string
  email: string
  nickname: string
  role: string
  enabled: boolean
  createdAt: string
}

/** 获取所有用户列表（管理员专用） */
export function getAllUsers() {
  return request.get<AdminUser[]>('/admin/users')
}

/** 切换用户启用/禁用状态（管理员专用） */
export function toggleUser(id: number) {
  return request.put(`/admin/users/${id}/toggle`)
}

/** 修改用户角色（管理员专用） */
export function updateUserRole(id: number, role: string) {
  return request.put(`/admin/users/${id}/role`, { role })
}

/** 系统统计数据接口 */
export interface SystemStats {
  totalUsers: number
  totalDocuments: number
  totalChunks: number
  totalConversations: number
  totalMessages: number
}

/** 获取系统全局统计数据 */
export function getSystemStats() {
  return request.get<SystemStats>('/admin/stats')
}

/** 操作日志接口 */
export interface OperationLog {
  id: number
  userId: number
  username: string
  action: string
  targetType: string
  targetId: number
  detail: string
  ipAddress: string
  createdAt: string
}

/** 获取操作日志（分页） */
export function getOperationLogs(page = 0, size = 20) {
  return request.get<{ content: OperationLog[]; totalElements: number }>('/admin/logs', {
    params: { page, size }
  })
}

import request from './request'

/** 用户资料接口 */
export interface UserProfile {
  id: number
  username: string
  email: string
  nickname: string
  avatar: string
  role: string
  createdAt: string
}

/** 获取当前登录用户信息 */
export function getUserInfo() {
  return request.get<UserProfile>('/user/me')
}

/** 修改密码 */
export function changePassword(oldPassword: string, newPassword: string) {
  return request.put('/user/password', { oldPassword, newPassword })
}

/**
 * 更新用户资料（昵称、邮箱、头像）
 * @param profile 仅传入需要更新的字段，null 字段保持原值
 */
export function updateProfile(profile: { nickname?: string; email?: string; avatar?: string }) {
  return request.put<UserProfile>('/user/profile', profile)
}

/**
 * 上传头像（multipart/form-data）
 */
export function uploadAvatar(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post<UserProfile>('/user/avatar', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 将后端存储的相对文件路径（如 /api/files/avatar/x.png）解析为可访问的完整 URL。
 * 因为 <img> 请求不携带 Bearer Token，必须用 API 服务器源拼接。
 */
export function resolveFileUrl(path?: string | null): string {
  if (!path) return ''
  if (/^https?:\/\//.test(path)) return path
  const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
  const origin = base.replace(/\/api\/?$/, '')
  return origin + path
}

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

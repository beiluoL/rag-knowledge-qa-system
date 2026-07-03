import request from './request'

export function getUserInfo() {
  return request.get('/user/me')
}

export function changePassword(oldPassword: string, newPassword: string) {
  return request.put('/user/password', { oldPassword, newPassword })
}

import request from './request'

export interface LoginParams {
  username: string
  password: string
}

export interface RegisterParams {
  username: string
  password: string
  email?: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: {
    id: number
    username: string
    role: string
  }
}

export function login(params: LoginParams) {
  return request.post<LoginResult>('/auth/login', params)
}

export function register(params: RegisterParams) {
  return request.post<LoginResult>('/auth/register', params)
}

export function refreshToken(token: string) {
  return request.post<LoginResult>('/auth/refresh', { refreshToken: token })
}

/**
 * 用户登出，将当前 access_token 加入后端黑名单
 */
export function logout() {
  return request.post('/auth/logout')
}

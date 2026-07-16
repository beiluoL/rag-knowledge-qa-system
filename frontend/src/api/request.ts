import axios from 'axios'
import type { AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// API 基础路径：Docker 模式使用相对路径（Nginx 反向代理），本地开发使用绝对路径
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090/api'

const service: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000
})

// 请求拦截器 —— 自动携带 Token
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器 —— Token 过期自动刷新
let isRefreshing = false
let refreshSubscribers: ((token: string) => void)[] = []

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb)
}

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach(cb => cb(token))
  refreshSubscribers = []
}

service.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    // Token 过期，尝试刷新
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise(resolve => {
          subscribeTokenRefresh((token: string) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(service(originalRequest))
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      const refreshToken = localStorage.getItem('refreshToken')
      if (refreshToken) {
        try {
          const { data } = await axios.post(`${API_BASE_URL}/auth/refresh`, { refreshToken })
          localStorage.setItem('accessToken', data.accessToken)
          localStorage.setItem('refreshToken', data.refreshToken)
          isRefreshing = false
          onTokenRefreshed(data.accessToken)
          originalRequest.headers.Authorization = `Bearer ${data.accessToken}`
          return service(originalRequest)
        } catch {
          isRefreshing = false
          localStorage.clear()
          router.push('/login')
          ElMessage.error('登录已过期，请重新登录')
          return Promise.reject(error)
        }
      } else {
        isRefreshing = false
        localStorage.clear()
        router.push('/login')
        ElMessage.error('请先登录')
      }
    }

    // 403 权限不足
    if (error.response?.status === 403) {
      ElMessage.error('权限不足')
    }

    return Promise.reject(error)
  }
)

export default service

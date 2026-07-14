import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi, logout as logoutApi, getMe, type LoginParams, type RegisterParams } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<any>(null)
  const token = ref<string>(localStorage.getItem('accessToken') || '')

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const username = computed(() => user.value?.username || '')
  const userId = computed(() => user.value?.id || 0)

  async function login(params: LoginParams) {
    const { data } = await loginApi(params)
    saveAuth(data)
    return data
  }

  async function register(params: RegisterParams) {
    const { data } = await registerApi(params)
    saveAuth(data)
    return data
  }

  function saveAuth(data: any) {
    token.value = data.accessToken
    user.value = data.user
    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
  }

  /**
   * 拉取当前用户信息（刷新后恢复会话 / 角色）
   */
  async function fetchProfile() {
    const { data } = await getMe()
    user.value = data
    return data
  }

  /**
   * 清除本地会话（token 失效时调用，不通知后端）
   */
  function clearSession() {
    token.value = ''
    user.value = null
    localStorage.clear()
  }

  /**
   * 用户登出：先通知后端将 token 加入黑名单，再清除本地存储
   */
  async function logout() {
    try {
      // 通知后端将当前 access_token 加入黑名单
      await logoutApi()
    } catch {
      // 即使后端调用失败也清除本地存储，保证用户能退出
    }
    token.value = ''
    user.value = null
    localStorage.clear()
  }

  return { user, token, isLoggedIn, isAdmin, username, userId, login, register, logout, fetchProfile, clearSession, saveAuth }
})

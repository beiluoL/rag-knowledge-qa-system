import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi, type LoginParams, type RegisterParams } from '@/api/auth'

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

  function logout() {
    token.value = ''
    user.value = null
    localStorage.clear()
  }

  return { user, token, isLoggedIn, isAdmin, username, userId, login, register, logout, saveAuth }
})

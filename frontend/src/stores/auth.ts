import { defineStore } from 'pinia'
import { apiFetch } from '../lib/api'

interface LoginResponse {
  token: string
  userId: number
  username: string
  displayName: string
  role: string
  admin: boolean
}

interface CurrentUserResponse {
  userId: number
  username: string
  displayName: string
  role: string
  admin: boolean
}

const TOKEN_KEY = 'agent-runtime-token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    user: null as CurrentUserResponse | null,
    errorMessage: ''
  }),
  actions: {
    async login(username: string, password: string) {
      const payload = await apiFetch<LoginResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password })
      })
      this.token = payload.token
      localStorage.setItem(TOKEN_KEY, payload.token)
      this.user = {
        userId: payload.userId,
        username: payload.username,
        displayName: payload.displayName,
        role: payload.role,
        admin: payload.admin
      }
    },
    async fetchMe() {
      this.user = await apiFetch<CurrentUserResponse>('/api/auth/me', {}, this.token)
    },
    async logout() {
      if (this.token) {
        await apiFetch<{ status: string }>('/api/auth/logout', { method: 'POST' }, this.token).catch(() => undefined)
      }
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
    }
  }
})

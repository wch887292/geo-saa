import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, getUserInfo, getMenus } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('geo-saa-token') || '',
    userInfo: null,
    permissions: [],
    menus: []
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.userInfo?.role === 'admin'
  },
  actions: {
    async login(loginData) {
      const res = await loginApi(loginData)
      const token = res.data.token
      this.token = token
      localStorage.setItem('geo-saa-token', token)
      return res
    },
    async logout() {
      try {
        await logoutApi()
      } catch {
        // ignore logout api error
      }
      this.token = ''
      this.userInfo = null
      this.permissions = []
      this.menus = []
      localStorage.removeItem('geo-saa-token')
    },
    async getInfo() {
      const res = await getUserInfo()
      this.userInfo = res.data
      this.permissions = res.data.permissions || []
      return res
    },
    async getMenus() {
      const res = await getMenus()
      this.menus = res.data || []
      return res
    }
  }
})
import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, getUserInfo, getMenus } from '@/api/auth'

const TOKEN_KEY = 'geo-saa-token'
const REFRESH_TOKEN_KEY = 'geo-saa-refresh-token'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY) || '',
    userInfo: null,
    permissions: [],
    menus: []
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    // 后端 role 存储为大写（ADMIN/USER），此处统一转小写比较，避免大小写不一致导致判断恒为 false
    isAdmin: (state) => String(state.userInfo?.role || '').toLowerCase() === 'admin',
    /** 判断是否拥有某项权限，admin 的 system:all 视为全量放行 */
    hasPermission: (state) => (code) => {
      if (!code) return true
      return state.permissions.includes('system:all') || state.permissions.includes(code)
    }
  },
  actions: {
    async login(loginData) {
      const res = await loginApi(loginData)
      const payload = res?.data || {}
      this.token = payload.token || ''
      this.refreshToken = payload.refreshToken || ''
      this.permissions = payload.permissions || []
      localStorage.setItem(TOKEN_KEY, this.token)
      if (this.refreshToken) {
        localStorage.setItem(REFRESH_TOKEN_KEY, this.refreshToken)
      }
      return res
    },

    async logout() {
      try {
        await logoutApi()
      } catch {
        // 登出接口失败不应阻塞本地状态清理
      }
      this.clearAuth()
    },

    clearAuth() {
      this.token = ''
      this.refreshToken = ''
      this.userInfo = null
      this.permissions = []
      this.menus = []
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    },

    /**
     * 后端 /auth/user-info 返回结构为 { user, permissions, menus }，
     * 需要解包后再写入 store，否则 userInfo 会被存成外层包装对象，
     * 导致 userInfo.role 等字段全部读取不到。
     */
    async getInfo() {
      const res = await getUserInfo()
      const payload = res?.data || {}
      this.userInfo = payload.user || null
      this.permissions = payload.permissions || []
      if (Array.isArray(payload.menus)) {
        this.menus = payload.menus
      }
      return res
    },

    async getMenus() {
      const res = await getMenus()
      this.menus = res?.data || []
      return res
    }
  }
})

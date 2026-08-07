import axios from 'axios'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import router from '@/router'

const TOKEN_KEY = 'geo-saa-token'
const REFRESH_TOKEN_KEY = 'geo-saa-refresh-token'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

// 避免 401 集中爆发时重复弹提示、重复跳转
let redirecting = false

function redirectToLogin() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  if (redirecting) return
  redirecting = true
  ElMessage.error('登录已过期，请重新登录')
  const current = router.currentRoute.value
  router
    .push({ path: '/login', query: current.path === '/login' ? {} : { redirect: current.fullPath } })
    .finally(() => {
      redirecting = false
    })
}

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const body = response.data

    // 二进制流（导出等场景）直接透传
    if (response.config?.responseType === 'blob') {
      return body
    }

    // 后端统一包装为 { code, message, data }，
    // 业务失败时 HTTP 状态可能仍是 200，需要在这里识别并转为 reject
    if (body && typeof body.code === 'number' && body.code !== 200) {
      const message = body.message || '请求失败'
      ElMessage.error(message)
      return Promise.reject(Object.assign(new Error(message), { code: body.code, payload: body }))
    }

    return body
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      const message = data?.message
      // 登录接口本身返回 401 表示账号密码错误，不应触发"登录过期"跳转
      const isLoginRequest = (error.config?.url || '').includes('/auth/login')
      switch (status) {
        case 401:
          if (isLoginRequest) {
            ElMessage.error(message || '用户名或密码错误')
          } else {
            redirectToLogin()
          }
          break
        case 403:
          ElMessage.error(message || '没有权限执行此操作')
          break
        case 404:
          ElMessage.error(message || '请求的资源不存在')
          break
        case 500:
          ElMessage.error(message || '服务器错误，请稍后重试')
          break
        default:
          ElMessage.error(message || '请求失败')
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error('网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default request

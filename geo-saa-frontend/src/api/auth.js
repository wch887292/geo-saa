import request from './request'

/**
 * 认证相关接口
 * 对应后端 com.geosaa.modules.auth.controller.AuthController
 * 基础路径：/api/v1/auth
 */

export function login(data) {
  return request.post('/auth/login', data)
}

export function logout() {
  return request.post('/auth/logout')
}

/** 获取动态菜单 */
export function getMenus() {
  return request.get('/auth/menus')
}

/** 获取当前用户信息（含权限列表与菜单） */
export function getUserInfo() {
  return request.get('/auth/user-info')
}

/** 仅获取当前登录用户基础信息 */
export function getCurrentUser() {
  return request.get('/auth/me')
}

/** 刷新访问令牌 */
export function refreshToken(token) {
  return request.post('/auth/refresh', null, { params: { refreshToken: token } })
}

import request from './request'

export function login(data) {
  return request.post('/auth/login', data)
}

export function logout() {
  return request.post('/auth/logout')
}

export function getMenus() {
  return request.get('/auth/menus')
}

export function getUserInfo() {
  return request.get('/auth/userinfo')
}
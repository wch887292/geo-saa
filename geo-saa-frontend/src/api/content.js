import request from './request'

export function batchGenerate(data) {
  return request.post('/content/batch', data)
}

export function getContentList(params) {
  return request.get('/content', { params })
}

export function exportContent(params) {
  return request.get('/content/export', { params, responseType: 'blob' })
}
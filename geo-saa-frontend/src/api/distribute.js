import request from './request'

export function createTask(data) {
  return request.post('/distribute', data)
}

export function getChannels() {
  return request.get('/distribute/channels')
}

export function getTaskProgress(id) {
  return request.get('/distribute/progress/' + id)
}

export function getTaskList(params) {
  return request.get('/distribute', { params })
}
import request from './request'

export function getKnowledgeList(params) {
  return request.get('/knowledge', { params })
}

export function createKnowledge(data) {
  return request.post('/knowledge', data)
}

export function updateKnowledge(id, data) {
  return request.put('/knowledge/' + id, data)
}

export function getJsonLd(id) {
  return request.get('/knowledge/' + id + '/jsonld')
}

export function getVersionHistory(id) {
  return request.get('/knowledge/' + id + '/versions')
}
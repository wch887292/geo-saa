import request from './request'

export function createDiagnose(data) {
  return request.post('/diagnose', data)
}

export function getDiagnoseProgress(id) {
  return request.get('/diagnose/progress/' + id)
}

export function getDiagnoseReport(id) {
  return request.get('/diagnose/report/' + id)
}

export function getCompetitorData(data) {
  return request.post('/diagnose/competitor', data)
}
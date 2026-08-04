import request from './request'

export function getStats(params) {
  return request.get('/monitor/stats', { params })
}

export function getTrends(params) {
  return request.get('/monitor/trends', { params })
}

export function getCompetitorComparison(params) {
  return request.get('/monitor/competitor', { params })
}
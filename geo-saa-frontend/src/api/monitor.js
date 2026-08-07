import request from './request'

/**
 * 数据监测相关接口
 * 对应后端 com.geosaa.modules.monitor.controller.MonitorController
 * 基础路径：/api/v1/monitor
 */

/** 分页查询统计数据 params: { pageNum, pageSize, statType, startDate, endDate } */
export function getStatList(params) {
  return request.get('/monitor/list', { params })
}

/** 按日期查询统计数据，date 格式 YYYY-MM-DD */
export function getStatsByDate(date, statType) {
  return request.get(`/monitor/date/${date}`, { params: { statType } })
}

/** 新增统计数据 */
export function addStat(data) {
  return request.post('/monitor/add', data)
}

/** 核心指标：AI 提及率、首推占比、收录量 */
export function getCoreMetrics(brandName) {
  return request.get('/monitor/core-metrics', { params: { brandName } })
}

/** 趋势数据 params: { statType, period: day|week|month, days } */
export function getTrend(params) {
  return request.get('/monitor/trend', { params })
}

/** 竞品对比 */
export function getCompetitorComparison(brandName) {
  return request.get('/monitor/competitor', { params: { brandName } })
}

import request from './request'

/**
 * 工作台首页统计接口
 * 对应后端 com.geosaa.modules.statistics.controller.StatisticsController
 * 基础路径：/api/v1/statistics
 *
 * 返回结构 { code, message, data }，其中 data 为聚合 Map：
 *   visibilityScore / contentTotal / distributeSuccess / distributeRate
 *   trendData { categories, series } / runningTasks[] / recentReports[]
 *   无数据的指标为 null 或空集合，前端需做占位/空态处理。
 */
export function getDashboardStatistics() {
  return request.get('/statistics/dashboard')
}

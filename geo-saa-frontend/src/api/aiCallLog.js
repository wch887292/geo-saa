import request from './request'

/**
 * AI 调用日志相关接口
 * 对应后端 com.geosaa.modules.ai.controller.AiCallLogController
 * 基础路径：/api/v1/ai/call-log
 */

/** 分页查询调用日志 */
export function getCallLogList(params) {
  return request.get('/ai/call-log/page', { params })
}

/** 调用统计 */
export function getCallStatistics(params) {
  return request.get('/ai/call-log/statistics', { params })
}

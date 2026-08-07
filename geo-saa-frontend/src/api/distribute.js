import request from './request'

/**
 * 多渠道分发相关接口
 * 对应后端 com.geosaa.modules.distribute.controller.DistributeController
 * 基础路径：/api/v1/distribute
 */

/** 分页查询分发任务 params: { pageNum, pageSize, targetPlatform, status } */
export function getTaskList(params) {
  return request.get('/distribute/list', { params })
}

export function getTaskDetail(id) {
  return request.get(`/distribute/${id}`)
}

/** 创建分发任务 */
export function createTask(data) {
  return request.post('/distribute/create', data)
}

export function cancelTask(id) {
  return request.post(`/distribute/${id}/cancel`)
}

export function deleteTask(id) {
  return request.delete(`/distribute/${id}`)
}

/** 获取可用渠道列表 */
export function getChannels() {
  return request.get('/distribute/channels')
}

/** 轮询分发进度 */
export function getTaskProgress(id) {
  return request.get(`/distribute/${id}/progress`)
}

/** 分发统计概览 */
export function getDistributeStats() {
  return request.get('/distribute/stats')
}

import request from './request'

/**
 * AI 大模型设置相关接口
 * 对应后端 com.geosaa.modules.ai.controller.AiModelController
 * 基础路径：/api/v1/ai/model
 */

/** 分页查询模型列表 */
export function getModelList(params) {
  return request.get('/ai/model/page', { params })
}

/** 获取所有可用模型 */
export function getAvailableModels() {
  return request.get('/ai/model/available')
}

/** 获取模型详情 */
export function getModelDetail(id) {
  return request.get(`/ai/model/${id}`)
}

/** 获取默认模型 */
export function getDefaultModel() {
  return request.get('/ai/model/default')
}

/** 新增模型 */
export function createModel(data) {
  return request.post('/ai/model', data)
}

/** 更新模型 */
export function updateModel(data) {
  return request.put('/ai/model', data)
}

/** 删除模型 */
export function deleteModel(id) {
  return request.delete(`/ai/model/${id}`)
}

/** 设置默认模型 */
export function setDefaultModel(id) {
  return request.put(`/ai/model/${id}/default`)
}

/** 测试模型连接 */
export function testModelConnection(id) {
  return request.post(`/ai/model/${id}/test`)
}

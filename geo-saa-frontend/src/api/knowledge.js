import request from './request'

/**
 * 知识库相关接口
 * 对应后端 com.geosaa.modules.knowledge.controller.KnowledgeController
 * 基础路径：/api/v1/knowledge
 *
 * 注意：该模块分为「品牌信息(brands)」与「品牌知识(knowledge)」两类资源，
 * 后端更新接口均为整体对象提交，id 放在 body 中而非路径上。
 */

// ========== 品牌信息 ==========

/** 分页查询品牌 params: { pageNum, pageSize, brandName, industry } */
export function getBrandList(params) {
  return request.get('/knowledge/brands', { params })
}

export function getBrandDetail(id) {
  return request.get(`/knowledge/brands/${id}`)
}

export function createBrand(data) {
  return request.post('/knowledge/brands', data)
}

export function updateBrand(data) {
  return request.put('/knowledge/brands', data)
}

export function deleteBrand(id) {
  return request.delete(`/knowledge/brands/${id}`)
}

// ========== 品牌知识 ==========

/** 获取指定品牌下的全部知识条目 */
export function getKnowledgeList(brandId) {
  return request.get(`/knowledge/brands/${brandId}/knowledge`)
}

export function createKnowledge(data) {
  return request.post('/knowledge/knowledge', data)
}

/** 更新知识条目，后端会自动递增版本号 */
export function updateKnowledge(data) {
  return request.put('/knowledge/knowledge', data)
}

export function deleteKnowledge(id) {
  return request.delete(`/knowledge/knowledge/${id}`)
}

/** 获取知识条目的版本历史 */
export function getVersionHistory(id) {
  return request.get(`/knowledge/knowledge/${id}/versions`)
}

// ========== GEO 结构化 ==========

/** 将非结构化文本 AI 转换为结构化数据 */
export function autoStructure(brandId, text) {
  return request.post(`/knowledge/auto-structure/${brandId}`, { text })
}

/** 生成 Schema.org JSON-LD */
export function getJsonLd(brandId) {
  return request.get(`/knowledge/json-ld/${brandId}`)
}

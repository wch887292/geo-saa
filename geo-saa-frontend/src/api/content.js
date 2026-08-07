import request from './request'

/**
 * AI 内容创作相关接口
 * 对应后端 com.geosaa.modules.content.controller.ContentController
 * 基础路径：/api/v1/content
 */

/** 分页查询内容列表 params: { pageNum, pageSize, contentType, status } */
export function getContentList(params) {
  return request.get('/content/list', { params })
}

/** 获取内容详情 */
export function getContentDetail(id) {
  return request.get(`/content/${id}`)
}

/** 创建内容（服务端自动做合规检测） */
export function createContent(data) {
  return request.post('/content/create', data)
}

/** 更新内容（整体对象提交，id 包含在 body 中） */
export function updateContent(data) {
  return request.put('/content/update', data)
}

export function deleteContent(id) {
  return request.delete(`/content/${id}`)
}

/** 对已有内容记录触发 AI 生成 */
export function generateContent(id) {
  return request.post(`/content/generate/${id}`)
}

/** 批量生成文章，入参为请求数组 */
export function batchGenerate(list) {
  return request.post('/content/batch-generate', list)
}

/** 批量生成短视频脚本，入参为请求数组 */
export function batchGenerateScripts(list) {
  return request.post('/content/batch-scripts', list)
}

/** 获取全部行业模板 */
export function getTemplates() {
  return request.get('/content/templates')
}

/** 获取指定行业的推荐模板 */
export function getTemplateByIndustry(industry) {
  return request.get(`/content/templates/${encodeURIComponent(industry)}`)
}

/** 内容合规检测 */
export function checkCompliance(content) {
  return request.post('/content/check-compliance', { content })
}

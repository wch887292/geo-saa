import request from './request'

/**
 * GEO / AAO 能力接口
 * 对应后端 com.geosaa.modules.geo.controller.GeoController 与 content 的 geo-validate
 */

/**
 * GEO 内容健康度校验（九战术 + 2026 v2 维度，16 维度评分）
 * @param {Object} data { content, keywords, publishDate? }
 * @returns { totalScore, blocked, redFlags, suggestions, tactics }
 */
export function geoValidate(data) {
  return request.post('/content/geo-validate', data)
}

/**
 * AAO 就绪度评估（AX Score 六维度）
 * @param {Object} profile AaoProfile（hasLlmsTxt/hasMcpCard/hasAgentJson/allowAiCrawlers/...）
 * @returns { axScore, grade, dimensions, suggestions }
 */
export function aaoValidate(profile) {
  return request.post('/geo/aao-validate', profile)
}

/** 生成 llms.txt（llmstxt.org 格式） */
export function getLlmsTxt(params) {
  return request.get('/geo/llms-txt', { params })
}

/** 生成 /.well-known/agent.json（A2A AgentCard） */
export function getAgentJson(params) {
  return request.get('/geo/agent-json', { params })
}

import request from './request'

/**
 * AI 品牌诊断相关接口
 * 对应后端 com.geosaa.modules.diagnose.controller.DiagnoseController
 * 基础路径：/api/v1/diagnose
 */

/** 分页查询诊断任务 params: { pageNum, pageSize, taskType, status } */
export function getDiagnoseList(params) {
  return request.get('/diagnose/list', { params })
}

/** 获取诊断任务详情 */
export function getDiagnoseDetail(id) {
  return request.get(`/diagnose/${id}`)
}

/** 创建诊断任务（后端异步执行，立即返回任务对象） */
export function createDiagnose(data) {
  return request.post('/diagnose/create', data)
}

export function deleteDiagnose(id) {
  return request.delete(`/diagnose/${id}`)
}

/** 轮询诊断进度 */
export function getDiagnoseProgress(id) {
  return request.get(`/diagnose/${id}/progress`)
}

/** 获取诊断报告（含竞品对比数据） */
export function getDiagnoseReport(id) {
  return request.get(`/diagnose/${id}/report`)
}

import request from './request'

/**
 * 资产存证接口
 * 对应后端 com.geosaa.modules.asset.controller.AssetController
 * 基础路径：/api/v1/asset
 *
 * 返回结构：
 *  - /asset/overview -> { code, message, data: { contentTotal, knowledgeTotal,
 *      distributeTotal, distributeSuccess, diagnoseTotal, totalAssets, published,
 *      screenshots, byType } }
 *  - /asset/list     -> PageResult { code, message, data: [...], total, pageSize, pageNum, pages }
 *     每条资产：{ id, assetType, title, description, date, status, statusText,
 *      typeLabel, brandName?, brandId?, extra }
 */
export function getAssetOverview() {
  return request.get('/asset/overview')
}

export function getAssetList(params) {
  return request.get('/asset/list', { params })
}

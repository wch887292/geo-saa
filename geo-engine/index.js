/**
 * @feihong/geo-engine — GEO/AAO 引擎
 *
 * - GEO 校验：九战术（Princeton KDD 2024）+ 2026 GEO v2 维度（答案前置/事实密度/
 *   结构化数据/E-E-A-T/关键引语/新鲜度/一手来源），关键词堆砌自动拦截
 * - AAO 评估：Agent Experience Score（AX Score）六维度 + llms.txt / agent.json 生成
 */
import geoValidator, { validateGeo } from './lib/geo-validator.js';
import aaoEngine, { evaluateAao, generateLlmsTxt, generateAgentJson } from './lib/aao-engine.js';

export {
  validateGeo,
  evaluateAao,
  generateLlmsTxt,
  generateAgentJson,
  geoValidator,
  aaoEngine
};

export default {
  validateGeo,
  evaluateAao,
  generateLlmsTxt,
  generateAgentJson
};

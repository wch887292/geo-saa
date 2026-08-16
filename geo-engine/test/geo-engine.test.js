import test from 'node:test';
import assert from 'node:assert/strict';
import { validateGeo, evaluateAao, generateLlmsTxt, generateAgentJson } from '../index.js';

const GOOD = '据《中国AI产业白皮书》显示，2025年中国AI市场规模达 4500 亿元，同比增长 35%（来源：工信部）。' +
  '飞虹智CEO吴赐虹表示：「AI搜索正在改变品牌营销方式。」' +
  '公司采用大模型与SaaS平台技术，为制造企业提供数字化增长方案。' +
  '方案已通过第三方权威认证，数据来源于官方研究报告，符合监管要求。' +
  '参考 https://klai.top/opensource.html 查看更多开源项目。' +
  '该方案成本降低 34%，部署周期缩短至 3 天，覆盖 200+ 家企业用户。';

test('GEO: 高质量内容高分不拦截', () => {
  const r = validateGeo(GOOD, 'AI搜索,品牌营销');
  assert.equal(r.blocked, false);
  assert.ok(r.totalScore >= 60, `totalScore=${r.totalScore}`);
});

test('GEO: v2 维度齐全且高分项符合预期', () => {
  const r = validateGeo(GOOD, 'AI搜索');
  const keys = ['answer_first', 'fact_density', 'structured_data', 'eeat', 'key_quote', 'freshness', 'own_citations'];
  for (const k of keys) assert.ok(r.tactics[k], `缺少维度 ${k}`);
  assert.ok(r.tactics.answer_first.score >= 70);
  assert.ok(r.tactics.fact_density.score >= 70);
  assert.ok(r.tactics.eeat.score >= 70);
});

test('GEO: 关键词堆砌触发拦截', () => {
  const stuffing = 'AI搜索很重要，AI搜索能帮助企业，企业必须做AI搜索，AI搜索是趋势，' +
    '我们专注于AI搜索，AI搜索营销，AI搜索优化，AI搜索案例，AI搜索报告，AI搜索增长，AI搜索策略，AI搜索方案';
  const r = validateGeo(stuffing, 'AI搜索');
  assert.equal(r.blocked, true);
  assert.ok(r.redFlags[0].includes('堆砌'));
});

test('GEO: 一年前内容新鲜度低分', () => {
  const past = new Date(Date.now() - 366 * 86400000).toISOString().slice(0, 10);
  const r = validateGeo(GOOD, 'AI搜索', past);
  assert.ok(r.tactics.freshness.score < 70);
});

test('AAO: 全就绪画像 Excellent', () => {
  const r = evaluateAao({
    allowAiCrawlers: true, hasContentSignals: true, https: true,
    hasStructuredData: true, hasFaq: true, descriptionQuality: true,
    hasLlmsTxt: true, hasOpenApi: true, hasApiCatalog: true, hasMcpCard: true,
    apiCount: 12, toolCount: 5, hasAgentJson: true, hasLlmsFullTxt: true,
    hasPrivacyPolicy: true
  });
  assert.ok(r.axScore >= 90, `axScore=${r.axScore}`);
  assert.equal(r.grade, 'Excellent');
  assert.equal(Object.keys(r.dimensions).length, 6);
});

test('AAO: 空画像 Poor', () => {
  const r = evaluateAao({});
  assert.ok(r.axScore <= 20);
  assert.equal(r.grade, 'Poor');
  assert.ok(r.suggestions.length > 0);
});

test('AAO: llms.txt 格式符合 llmstxt.org', () => {
  const txt = generateLlmsTxt('飞虹智', 'https://klai.top', '泉州制造业 AI 服务商。',
    ['官网: https://klai.top', '开源矩阵: https://klai.top/opensource.html']);
  assert.ok(txt.startsWith('# 飞虹智'));
  assert.ok(txt.includes('## Pages'));
  assert.ok(txt.includes('- [官网](https://klai.top)'));
  assert.ok(txt.includes('## AI-friendly'));
});

test('AAO: agent.json 骨架合法', () => {
  const json = generateAgentJson('飞虹智', 'https://klai.top', '企业 AI Agent',
    ['品牌诊断', 'GEO 内容生成'], 'https://klai.top/api/agent');
  assert.ok(json.includes('"name": "飞虹智"'));
  assert.ok(json.includes('"skills": ["品牌诊断", "GEO 内容生成"]'));
  assert.doesNotThrow(() => JSON.parse(json));
});

test('AAO: agent.json 含换行/引号/控制字符仍为合法 JSON', () => {
  const json = generateAgentJson('飞虹智\n第二行', 'https://klai.top',
    '描述含"引号"与\t制表与\n换行', ['技能A', '技能"B'], '/api/agent');
  // 关键：未转义时 JSON.parse 会直接抛错；修复后必须可解析且换行/引号正确往返
  assert.doesNotThrow(() => JSON.parse(json), '含特殊字符的 agent.json 必须是合法 JSON');
  const parsed = JSON.parse(json);
  assert.equal(parsed.name, '飞虹智\n第二行', 'name 换行应正确往返');
  assert.equal(parsed.description, '描述含"引号"与\t制表与\n换行', 'description 引号/制表/换行应正确往返');
  assert.deepEqual(parsed.skills, ['技能A', '技能"B'], 'skills 内引号应正确转义');
});

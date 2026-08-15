/**
 * GEO 内容健康度校验器（JS 版，对齐后端 GeoContentValidator）。
 *
 * 九战术（Princeton KDD 2024）+ 2026 GEO v2 维度：
 * 答案前置 / 事实密度 / 结构化数据 / E-E-A-T / 关键引语 / 新鲜度 / 一手来源。
 * 关键词堆砌密度 >3% 且提及 >=3 次时 blocked=true 且总分折半。
 */

const T = {
  AUTHORITATIVE: 'authoritative',
  STATISTICS: 'statistics',
  KEYWORD_STUFFING: 'keyword_stuffing',
  CITE_SOURCES: 'cite_sources',
  QUOTATIONS: 'quotations',
  EASY_TO_UNDERSTAND: 'easy_to_understand',
  FLUENCY: 'fluency',
  UNIQUE_WORDS: 'unique_words',
  TECHNICAL_TERMS: 'technical_terms',
  ANSWER_FIRST: 'answer_first',
  FACT_DENSITY: 'fact_density',
  STRUCTURED_DATA: 'structured_data',
  EEAT: 'eeat',
  KEY_QUOTE: 'key_quote',
  FRESHNESS: 'freshness',
  OWN_CITATIONS: 'own_citations'
};

const NAMES = {
  [T.QUOTATIONS]: '专家引语', [T.STATISTICS]: '量化数据', [T.FLUENCY]: '流畅度',
  [T.CITE_SOURCES]: '引用来源', [T.TECHNICAL_TERMS]: '技术术语', [T.EASY_TO_UNDERSTAND]: '易于理解',
  [T.AUTHORITATIVE]: '权威语气', [T.UNIQUE_WORDS]: '独特词汇', [T.KEYWORD_STUFFING]: '关键词堆砌',
  [T.ANSWER_FIRST]: '答案前置', [T.FACT_DENSITY]: '事实密度', [T.STRUCTURED_DATA]: '结构化数据',
  [T.EEAT]: 'E-E-A-T', [T.KEY_QUOTE]: '关键引语', [T.FRESHNESS]: '新鲜度', [T.OWN_CITATIONS]: '一手来源'
};

const WEIGHTS = {
  [T.QUOTATIONS]: 0.12, [T.STATISTICS]: 0.10, [T.CITE_SOURCES]: 0.08, [T.FLUENCY]: 0.07,
  [T.TECHNICAL_TERMS]: 0.05, [T.EASY_TO_UNDERSTAND]: 0.05, [T.AUTHORITATIVE]: 0.04,
  [T.UNIQUE_WORDS]: 0.02,
  [T.ANSWER_FIRST]: 0.10, [T.FACT_DENSITY]: 0.10, [T.STRUCTURED_DATA]: 0.09, [T.EEAT]: 0.07,
  [T.KEY_QUOTE]: 0.04, [T.FRESHNESS]: 0.04, [T.OWN_CITATIONS]: 0.03
};

const AUTHORITY_WORDS = ['权威', '官方', '认证', '白皮书', '研究报告', '行业协会', '监管', '标准', '依据', '数据显示', '实测', '第三方', '国家级', '工信部', '卫健委'];
const TECHNICAL_TERMS = ['AI', '大模型', '算法', '机器学习', '深度学习', 'SaaS', 'API', '云计算', '数字化', '智能制造', '供应链', '私域', '转化率', '用户画像', 'BI', 'ERP', 'CRM', '开源', '微服务', '分布式', 'KPI', 'SOP', '闭环', '生态', '算力', 'Agent', 'Prompt', 'AIGC', 'GEO', 'SEO', 'AAO', '多模态', '向量数据库'];
const STOP_WORDS = new Set(['的', '了', '是', '在', '和', '与', '及', '或', '很', '都', '也', '就', '而', '但', '并', '一个', '我们', '你们', '他们', '这个', '那个', '这些', '那些', '可以', '能够', '需要', '进行', '以及']);

const RE = {
  stat: /\d+(?:\.\d+)?\s*(?:%|％|万|亿|倍|家|个|款|种|天|元|人|次|项|年|月|台|件|篇|GB|TB|MB|KB|M|B)/g,
  url: /https?:\/\/[\w.\-/:?=&%]+|www\.\w+[\w.\-]*(?::\d+)?(?:\/[\w.\-/?=&%]*)?/g,
  citeHint: /来源[:：]|参考[:：]|据[^。；]{0,20}(报告|数据|研究)|数据来源/g,
  quote: /[“”"''「」『』]/g,
  expertQuote: /[^。；\n]{0,24}(专家|CEO|创始人|教授|分析师|学者|负责人|院长|主任|研究员)[^。；\n]{0,12}(表示|指出|认为|强调|称|说|建议|警告|预计)/g,
  year: /\d{4}年|\d{4}-\d{2}/g,
  eeatAuthor: /作者[:：]|撰文|文[:：]|By |byline|作者署名/,
  eeatDate: /\d{4}年\d{1,2}月|\d{4}-\d{2}-\d{2}|发布时间|更新于/,
  eeatOrg: /公司|研究院|大学|学院|官方|机构|实验室|平台/,
  eeatCred: /认证|博士|教授|专家|资质|证书|高级工程师/,
  conclusionHead: /是|为|达到|增长|建议|推荐|提供|实现|覆盖|支持/
};

/** 校验内容 GEO 健康度 */
export function validateGeo(content, keywords = '', publishDate = null) {
  const result = { totalScore: 0, blocked: false, redFlags: [], suggestions: [], tactics: {} };
  if (!content || !content.trim()) {
    result.redFlags.push('内容为空，无法评分');
    result.suggestions.push('先撰写正文，再进行 GEO 健康度校验');
    return result;
  }
  const text = content.trim();
  const kwList = splitKeywords(keywords);
  const scores = {};
  scores[T.STATISTICS] = scoreStatistics(text);
  scores[T.CITE_SOURCES] = scoreCiteSources(text);
  scores[T.QUOTATIONS] = scoreQuotations(text);
  scores[T.AUTHORITATIVE] = scoreAuthoritative(text);
  scores[T.EASY_TO_UNDERSTAND] = scoreEasyToUnderstand(text);
  scores[T.FLUENCY] = scoreFluency(text);
  scores[T.UNIQUE_WORDS] = scoreUniqueWords(text);
  scores[T.TECHNICAL_TERMS] = scoreTechnicalTerms(text);
  scores[T.ANSWER_FIRST] = scoreAnswerFirst(text);
  scores[T.FACT_DENSITY] = scoreFactDensity(text);
  scores[T.STRUCTURED_DATA] = scoreStructuredData(text);
  scores[T.EEAT] = scoreEeat(text);
  scores[T.KEY_QUOTE] = scoreKeyQuote(text);
  scores[T.FRESHNESS] = scoreFreshness(publishDate);
  scores[T.OWN_CITATIONS] = scoreOwnCitations(text);

  let weighted = 0;
  for (const [code, score] of Object.entries(scores)) {
    weighted += WEIGHTS[code] * score;
    result.tactics[code] = { code, name: NAMES[code], score, detail: detailText(score) };
  }

  const stuffing = scoreKeywordStuffing(text, kwList);
  result.tactics[T.KEYWORD_STUFFING] = { code: T.KEYWORD_STUFFING, name: NAMES[T.KEYWORD_STUFFING], score: stuffing.score, detail: stuffing.detail };
  if (stuffing.blocked) {
    result.blocked = true;
    weighted *= 0.5;
    result.redFlags.push(stuffing.detail);
  } else if (stuffing.score < 100) {
    result.redFlags.push(stuffing.detail);
  }

  result.totalScore = Math.round(weighted);
  result.suggestions = buildSuggestions(result);
  return result;
}

// ---------- 九战术 ----------

function scoreStatistics(t) { const n = count(RE.stat, t); return n >= 3 ? 100 : n === 2 ? 70 : n === 1 ? 40 : 0; }
function scoreCiteSources(t) {
  const total = count(RE.url, t) + count(RE.citeHint, t);
  return total >= 2 ? 100 : total === 1 ? 60 : 0;
}
function scoreQuotations(t) {
  const total = Math.floor(count(RE.quote, t) / 2) + count(RE.expertQuote, t);
  return total >= 2 ? 100 : total === 1 ? 70 : 0;
}
function scoreAuthoritative(t) {
  const hits = AUTHORITY_WORDS.filter(w => t.includes(w)).length;
  return hits >= 3 ? 100 : hits === 2 ? 70 : hits === 1 ? 40 : 0;
}
function scoreEasyToUnderstand(t) {
  const sentences = splitSentences(t);
  if (!sentences.length) return 0;
  const avg = t.length / sentences.length;
  return avg <= 30 ? 100 : avg <= 50 ? 70 : avg <= 80 ? 40 : 10;
}
function scoreFluency(t) {
  const sentences = splitSentences(t);
  if (sentences.length < 3) return 60;
  const lens = sentences.map(s => s.length);
  const avg = lens.reduce((a, b) => a + b, 0) / lens.length;
  if (!avg) return 0;
  const variance = lens.reduce((a, l) => a + (l - avg) ** 2, 0) / lens.length;
  const cv = Math.sqrt(variance) / avg;
  return cv <= 0.4 ? 100 : cv <= 0.7 ? 70 : cv <= 1.0 ? 40 : 10;
}
function scoreUniqueWords(t) {
  const unique = new Set(t.split(/[\s,，。；;！？!?（）()、:："“”'‘’\[\]【】]/).filter(w => w.length >= 2 && !STOP_WORDS.has(w)));
  const n = unique.size;
  return n >= 8 ? 100 : n >= 4 ? 60 : n >= 2 ? 30 : 0;
}
function scoreTechnicalTerms(t) {
  const hits = TECHNICAL_TERMS.filter(term => t.includes(term)).length;
  return hits >= 3 ? 100 : hits === 2 ? 70 : hits === 1 ? 40 : 0;
}

// ---------- GEO v2 新维度 ----------

function scoreAnswerFirst(t) {
  const head = t.slice(0, 200);
  if (head.length < 40) return 40;
  const conclusion = RE.conclusionHead.test(head);
  const hasNumber = /\d/.test(head);
  return conclusion && hasNumber ? 100 : conclusion || hasNumber ? 70 : 30;
}
function scoreFactDensity(t) {
  const facts = count(RE.stat, t) + count(RE.year, t);
  return facts >= 5 ? 100 : facts >= 3 ? 70 : facts >= 1 ? 40 : 0;
}
function scoreStructuredData(t) {
  if (t.includes('application/ld+json') || t.includes('@context')) return 100;
  if (t.includes('schema.org') || t.includes('@type') || t.includes('JSON-LD') || t.includes('jsonld')) return 70;
  return 0;
}
function scoreEeat(t) {
  let hits = 0;
  if (RE.eeatAuthor.test(t)) hits++;
  if (RE.eeatDate.test(t)) hits++;
  if (RE.eeatOrg.test(t)) hits++;
  if (RE.eeatCred.test(t)) hits++;
  return hits >= 3 ? 100 : hits === 2 ? 70 : hits === 1 ? 40 : 0;
}
function scoreKeyQuote(t) {
  if (t.includes('>') || t.includes('**') || t.includes('【核心】') || t.includes('一句话总结')) return 100;
  if (t.includes('核心观点') || t.includes('要点') || t.includes('结论')) return 60;
  return 0;
}
function scoreFreshness(publishDate) {
  if (!publishDate) return 50;
  const days = Math.floor((Date.now() - new Date(publishDate).getTime()) / 86400000);
  if (days <= 30) return 100;
  if (days <= 90) return 70;
  if (days <= 365) return 40;
  return 10;
}
function scoreOwnCitations(t) {
  const links = count(RE.url, t);
  const primary = count(/(官方|官网|文档|白皮书|研究报告|论文|arXiv|github\.com|gov\.|org\.)/g, t);
  return links >= 2 && primary >= 1 ? 100 : links >= 1 && primary >= 1 ? 70 : links >= 1 ? 40 : 0;
}

// ---------- 关键词堆砌 ----------

function scoreKeywordStuffing(t, keywords) {
  if (!keywords.length) return { score: 100, detail: '未提供目标关键词，跳过堆砌检测', blocked: false };
  if (!t.length) return { score: 100, detail: '空内容', blocked: false };
  let kwChars = 0;
  const detail = [];
  for (const k of keywords) {
    const occ = countOccurrences(t, k);
    if (occ > 0) { kwChars += occ * k.length; detail.push(`${k}x${occ}`); }
  }
  const density = kwChars / t.length;
  const desc = `关键词密度 ${(density * 100).toFixed(1)}%（${detail.join(' ')}）`;
  if (density > 0.03 && kwChars >= 12) {
    return { score: 0, detail: `关键词堆砌：密度 ${(density * 100).toFixed(1)}% 超出阈值 3%，论文实测可见度 −8%~−10%，已拦截`, blocked: true };
  }
  if (density > 0.03) return { score: 50, detail: desc + '，密度略高于 3%（单次提及，暂不拦截），建议自然表述', blocked: false };
  if (density > 0.015) return { score: 50, detail: desc + '，密度偏高（1.5%~3%），建议自然稀释', blocked: false };
  return { score: 100, detail: desc + '，密度正常', blocked: false };
}

// ---------- 工具 ----------

function splitKeywords(kw) {
  if (!kw) return [];
  return kw.split(/[,，、;；]/).map(s => s.trim()).filter(Boolean);
}
function splitSentences(t) {
  return t.split(/[。！？!?；;\n]+/).map(s => s.trim()).filter(Boolean);
}
function count(re, t) { return (t.match(re) || []).length; }
function countOccurrences(t, kw) {
  if (!kw) return 0;
  let idx = 0, n = 0;
  while ((idx = t.indexOf(kw, idx)) >= 0) { n++; idx += kw.length; }
  return n;
}
function detailText(score) {
  return score >= 100 ? '充分' : score >= 60 ? '基本具备' : score >= 30 ? '不足' : '缺失';
}
function buildSuggestions(result) {
  const out = [];
  const s = result.tactics;
  if (s[T.QUOTATIONS]?.score < 70) out.push('加入 1-2 条带署名的专家/客户引语（论文实测最高效 +41%）');
  if (s[T.STATISTICS]?.score < 70) out.push('用具体数字替换模糊表述，如「成本降低 34%（Forrester, 2025）」');
  if (s[T.CITE_SOURCES]?.score < 70) out.push('在行内补充可信来源引用（论文实测 +28%）');
  if (s[T.KEYWORD_STUFFING]?.score < 100) out.push('降低关键词重复密度，AI 引擎按语义而非词频理解内容');
  if (s[T.ANSWER_FIRST]?.score < 70) out.push('前 200 字直接给出核心答案（结论前置 + 数字佐证）');
  if (s[T.FACT_DENSITY]?.score < 70) out.push('提升事实密度：每节至少 1 个可引用事实（数字/名称/日期）');
  if (s[T.STRUCTURED_DATA]?.score < 70) out.push('部署 JSON-LD 结构化数据（Article/FAQPage/Organization，2026 引用概率 +2.5x）');
  if (s[T.EEAT]?.score < 70) out.push('补齐 E-E-A-T 信号：作者署名 + 发布日期 + 机构资质');
  if (s[T.FRESHNESS]?.score < 70) out.push('更新内容发布时间（AI 偏好 30 天内的新鲜来源）');
  if (!out.length) out.push('内容 GEO 健康度良好，可保持当前结构');
  return out;
}

export default {
  validate: validateGeo,
  dimensions: T,
  weights: WEIGHTS,
  names: NAMES
};

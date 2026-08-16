/**
 * AAO 引擎（JS 版，对齐后端 AaoEngine）。
 *
 * Agent Experience Score（AX Score，0-100）六维度模型：
 * Crawlability 25 / Structured Data 25 / Content Quality 15 /
 * Agent Interaction 20 / Discoverability 10 / Security & Trust 5。
 * 另提供 llms.txt（llmstxt.org）与 A2A agent.json 生成。
 */

const DIMENSIONS = {
  CRAWLABILITY: 'crawlability',
  STRUCTURED: 'structured_data',
  CONTENT: 'content_quality',
  INTERACTION: 'agent_interaction',
  DISCOVERABILITY: 'discoverability',
  TRUST: 'security_trust'
};

const DIM_NAMES = {
  [DIMENSIONS.CRAWLABILITY]: '可爬取性',
  [DIMENSIONS.STRUCTURED]: '结构化数据',
  [DIMENSIONS.CONTENT]: '内容质量',
  [DIMENSIONS.INTERACTION]: 'Agent 可调用性',
  [DIMENSIONS.DISCOVERABILITY]: '可发现性',
  [DIMENSIONS.TRUST]: '安全与信任'
};

const DIM_WEIGHTS = {
  [DIMENSIONS.CRAWLABILITY]: 25,
  [DIMENSIONS.STRUCTURED]: 25,
  [DIMENSIONS.CONTENT]: 15,
  [DIMENSIONS.INTERACTION]: 20,
  [DIMENSIONS.DISCOVERABILITY]: 10,
  [DIMENSIONS.TRUST]: 5
};

/**
 * 评估 Agent 就绪度。
 * @param {object} p AaoProfile（字段见后端 AaoProfile：hasLlmsTxt/hasApiCatalog/hasMcpCard/hasAgentJson/...）
 * @returns {{axScore:number, grade:string, dimensions:object, suggestions:string[]}}
 */
export function evaluateAao(p = {}) {
  const dims = {};
  const miss = [];

  // 1. Crawlability 25%
  const crawlMiss = [];
  let crawl = 0;
  if (p.allowAiCrawlers) crawl += 50; else crawlMiss.push('robots.txt 未放行 AI 爬虫');
  if (p.hasContentSignals) crawl += 20; else crawlMiss.push('未声明 Content-Signal');
  if (p.https) crawl += 30; else crawlMiss.push('未启用 HTTPS');
  dims[DIMENSIONS.CRAWLABILITY] = { code: DIMENSIONS.CRAWLABILITY, name: DIM_NAMES[DIMENSIONS.CRAWLABILITY], weight: 25, score: crawl, detail: crawlMiss.join('；') };

  // 2. Structured Data 25%
  const sMiss = [];
  let structured = 0;
  if (p.hasStructuredData) structured += 60; else sMiss.push('未部署 JSON-LD schema');
  if (p.hasFaq) structured += 40; else sMiss.push('缺少 FAQ 内容');
  dims[DIMENSIONS.STRUCTURED] = { code: DIMENSIONS.STRUCTURED, name: DIM_NAMES[DIMENSIONS.STRUCTURED], weight: 25, score: structured, detail: sMiss.join('；') };

  // 3. Content Quality 15%
  const cMiss = [];
  let content = 0;
  if (p.descriptionQuality) content += 60; else cMiss.push('能力描述不完整');
  if (p.hasFaq) content += 40; else cMiss.push('缺少自然语言问答内容');
  dims[DIMENSIONS.CONTENT] = { code: DIMENSIONS.CONTENT, name: DIM_NAMES[DIMENSIONS.CONTENT], weight: 15, score: content, detail: cMiss.join('；') };

  // 4. Agent Interaction 20%
  const iMiss = [];
  let interact = 0;
  if (p.hasLlmsTxt) interact += 40; else iMiss.push('未发布 /llms.txt');
  if (p.hasOpenApi || p.hasApiCatalog) interact += 20; else iMiss.push('未暴露 API 描述');
  if (p.hasMcpCard) interact += 20; else iMiss.push('未发布 MCP Server Card');
  if ((p.apiCount || 0) > 0 || (p.toolCount || 0) > 0) interact += 20; else iMiss.push('无可调用 API/工具');
  dims[DIMENSIONS.INTERACTION] = { code: DIMENSIONS.INTERACTION, name: DIM_NAMES[DIMENSIONS.INTERACTION], weight: 20, score: interact, detail: iMiss.join('；') };

  // 5. Discoverability 10%
  const dMiss = [];
  let discover = 0;
  if (p.hasAgentJson) discover += 60; else dMiss.push('未发布 A2A AgentCard');
  if (p.hasLlmsFullTxt) discover += 40; else dMiss.push('未发布 llms-full.txt');
  dims[DIMENSIONS.DISCOVERABILITY] = { code: DIMENSIONS.DISCOVERABILITY, name: DIM_NAMES[DIMENSIONS.DISCOVERABILITY], weight: 10, score: discover, detail: dMiss.join('；') };

  // 6. Security & Trust 5%
  const tMiss = [];
  let trust = 0;
  if (p.https) trust += 50; else tMiss.push('无 HTTPS');
  if (p.hasPrivacyPolicy) trust += 50; else tMiss.push('缺少隐私政策/条款');
  dims[DIMENSIONS.TRUST] = { code: DIMENSIONS.TRUST, name: DIM_NAMES[DIMENSIONS.TRUST], weight: 5, score: trust, detail: tMiss.join('；') };

  let ax = 0;
  for (const d of Object.values(dims)) ax += d.weight / 100 * d.score;
  const axScore = Math.round(ax);
  const grade = axScore >= 90 ? 'Excellent' : axScore >= 70 ? 'Good' : axScore >= 50 ? 'NeedsWork' : 'Poor';

  const suggestions = Object.values(dims)
    .filter(d => d.score < 70 && d.detail)
    .map(d => `[${d.name}] ${d.detail}`);
  if (!suggestions.length) suggestions.push('Agent 就绪度优秀：站点已可被 AI Agent 发现、理解与调用');

  return { axScore, grade, dimensions: dims, suggestions };
}

/** 生成 llms.txt（llmstxt.org 格式） */
export function generateLlmsTxt(brandName, siteUrl, description = '', pages = []) {
  const url = siteUrl || '';
  let out = `# ${brandName || url}\n\n`;
  if (description) out += `> ${description.trim()}\n\n`;
  out += '## Pages\n\n';
  for (const page of pages) {
    const s = page.trim();
    if (!s) continue;
    if (s.includes(': ')) {
      const [title, link] = s.split(': ', 2);
      out += `- [${title}](${link})\n`;
    } else {
      out += `- ${s}\n`;
    }
  }
  out += '\n## AI-friendly\n\n';
  out += `- llms.txt: ${url}/llms.txt\n`;
  return out;
}

/** 生成 /.well-known/agent.json（A2A AgentCard 骨架） */
export function generateAgentJson(brandName, siteUrl = '', description = '', skills = [], dispatchUrl = '/api/agent') {
  const esc = s => {
    const str = String(s ?? '');
    return str
      .replace(/\\/g, '\\\\')
      .replace(/"/g, '\\"')
      .replace(/\n/g, '\\n')
      .replace(/\r/g, '\\r')
      .replace(/\t/g, '\\t')
      .replace(/\f/g, '\\f')
      .replace(/[\b]/g, '\\b')
      .replace(/[\u0000-\u001F]/g, ch => '\\u' + ch.charCodeAt(0).toString(16).padStart(4, '0'));
  };
  let out = '{\n';
  out += `  "name": "${esc(brandName || 'agent')}",\n`;
  out += `  "description": "${esc(description || 'AI Agent 能力描述')}",\n`;
  out += `  "url": "${esc(siteUrl)}",\n`;
  out += '  "version": "1.0.0",\n';
  out += `  "skills": [${skills.map(s => `"${esc(s.trim())}"`).join(', ')}],\n`;
  out += `  "dispatch": "${esc(dispatchUrl)}"\n`;
  out += '}\n';
  return out;
}

export default {
  evaluate: evaluateAao,
  generateLlmsTxt,
  generateAgentJson,
  dimensions: DIMENSIONS
};

<template>
  <div class="geo-page">
    <el-card shadow="never">
      <template #header>
        <div class="geo-header">
          <span class="geo-title">GEO / AAO 体检工作台</span>
          <el-tag size="small" type="success">2026 规则引擎</el-tag>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- ========== Tab 1: GEO 内容校验 ========== -->
        <el-tab-pane label="GEO 内容校验" name="geo">
          <el-form label-width="80px">
            <el-form-item label="内容正文">
              <el-input
                v-model="geoForm.content"
                type="textarea"
                :rows="8"
                placeholder="粘贴待校验内容…（将按九战术 + 答案前置 / 事实密度 / 结构化数据 / E-E-A-T 等 16 维度评分）"
              />
            </el-form-item>
            <el-form-item label="目标关键词">
              <el-input v-model="geoForm.keywords" placeholder="逗号分隔，如：AI搜索, 品牌营销（用于堆砌检测）" />
            </el-form-item>
            <el-form-item label="发布日期">
              <el-date-picker v-model="geoForm.publishDate" type="date" value-format="YYYY-MM-DD" placeholder="影响新鲜度评分，默认今天" style="width: 220px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="geoLoading" @click="handleGeoValidate">开始校验</el-button>
            </el-form-item>
          </el-form>

          <template v-if="geoResult">
            <el-alert
              v-if="geoResult.blocked"
              type="error"
              title="内容被拦截：关键词堆砌"
              :description="geoResult.redFlags && geoResult.redFlags[0]"
              show-icon
              :closable="false"
              style="margin-bottom: 16px"
            />
            <div class="score-row">
              <div class="score-big">
                <span class="score-num">{{ geoResult.totalScore }}</span>
                <span class="score-label">GEO 总分</span>
              </div>
              <el-progress
                type="dashboard"
                :percentage="geoResult.totalScore"
                :width="120"
                :color="scoreColor(geoResult.totalScore)"
              />
            </div>

            <el-descriptions :column="2" border size="small" style="margin: 16px 0">
              <el-descriptions-item v-for="(t, code) in geoResult.tactics" :key="code" :label="t.name">
                <el-progress
                  :percentage="t.score"
                  :stroke-width="8"
                  :color="scoreColor(t.score)"
                  style="width: 100%"
                />
                <div class="tactic-detail">{{ t.detail }}</div>
              </el-descriptions-item>
            </el-descriptions>

            <div v-if="geoResult.suggestions && geoResult.suggestions.length" class="suggest-box">
              <div class="suggest-title">优化建议</div>
              <ul>
                <li v-for="(s, i) in geoResult.suggestions" :key="i">{{ s }}</li>
              </ul>
            </div>
          </template>
        </el-tab-pane>

        <!-- ========== Tab 2: AAO 就绪度 ========== -->
        <el-tab-pane label="AAO 就绪度" name="aao">
          <el-form label-width="150px" style="max-width: 720px">
            <el-form-item label="站点域名">
              <el-input v-model="aaoForm.domain" placeholder="如 klai.top" />
            </el-form-item>
            <el-form-item label="AI 爬虫放行 (robots.txt)">
              <el-switch v-model="aaoForm.allowAiCrawlers" />
              <span class="switch-hint">允许 GPTBot / ClaudeBot / PerplexityBot / Google-Extended</span>
            </el-form-item>
            <el-form-item label="Content-Signal">
              <el-switch v-model="aaoForm.hasContentSignals" />
              <span class="switch-hint">robots.txt 声明 search / ai-train / ai-input</span>
            </el-form-item>
            <el-form-item label="发布 /llms.txt">
              <el-switch v-model="aaoForm.hasLlmsTxt" />
            </el-form-item>
            <el-form-item label="发布 llms-full.txt">
              <el-switch v-model="aaoForm.hasLlmsFullTxt" />
            </el-form-item>
            <el-form-item label="API 目录 (api-catalog)">
              <el-switch v-model="aaoForm.hasApiCatalog" />
              <span class="switch-hint">/.well-known/api-catalog (RFC 9727)</span>
            </el-form-item>
            <el-form-item label="MCP Server Card">
              <el-switch v-model="aaoForm.hasMcpCard" />
              <span class="switch-hint">/.well-known/mcp/server-card.json</span>
            </el-form-item>
            <el-form-item label="A2A agent.json">
              <el-switch v-model="aaoForm.hasAgentJson" />
              <span class="switch-hint">/.well-known/agent.json</span>
            </el-form-item>
            <el-form-item label="OpenAPI 描述">
              <el-switch v-model="aaoForm.hasOpenApi" />
              <span class="switch-hint">/api/openapi.json</span>
            </el-form-item>
            <el-form-item label="JSON-LD 结构化数据">
              <el-switch v-model="aaoForm.hasStructuredData" />
            </el-form-item>
            <el-form-item label="FAQ 内容">
              <el-switch v-model="aaoForm.hasFaq" />
            </el-form-item>
            <el-form-item label="HTTPS">
              <el-switch v-model="aaoForm.https" />
            </el-form-item>
            <el-form-item label="隐私政策 / 条款">
              <el-switch v-model="aaoForm.hasPrivacyPolicy" />
            </el-form-item>
            <el-form-item label="能力描述完整">
              <el-switch v-model="aaoForm.descriptionQuality" />
              <span class="switch-hint">一句话能说清"能做什么、为谁服务"</span>
            </el-form-item>
            <el-form-item label="API 数量">
              <el-input-number v-model="aaoForm.apiCount" :min="0" style="width: 160px" />
            </el-form-item>
            <el-form-item label="工具 / 技能数">
              <el-input-number v-model="aaoForm.toolCount" :min="0" style="width: 160px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="aaoLoading" @click="handleAaoValidate">评估 AX Score</el-button>
            </el-form-item>
          </el-form>

          <template v-if="aaoResult">
            <div class="score-row">
              <div class="score-big">
                <span class="score-num">{{ aaoResult.axScore }}</span>
                <span class="score-label">AX Score（Agent Experience）</span>
              </div>
              <div class="ax-grade">
                <el-tag :type="gradeType(aaoResult.grade)" size="large">{{ aaoResult.grade }}</el-tag>
              </div>
            </div>
            <el-descriptions :column="2" border size="small" style="margin: 16px 0">
              <el-descriptions-item v-for="(d, code) in aaoResult.dimensions" :key="code" :label="`${d.name}（权重 ${d.weight}%）`">
                <el-progress :percentage="d.score" :stroke-width="8" :color="scoreColor(d.score)" style="width: 100%" />
                <div class="tactic-detail">{{ d.detail }}</div>
              </el-descriptions-item>
            </el-descriptions>
            <div v-if="aaoResult.suggestions && aaoResult.suggestions.length" class="suggest-box">
              <div class="suggest-title">就绪度提升建议</div>
              <ul>
                <li v-for="(s, i) in aaoResult.suggestions" :key="i">{{ s }}</li>
              </ul>
            </div>
          </template>
        </el-tab-pane>

        <!-- ========== Tab 3: llms.txt / agent.json 生成 ========== -->
        <el-tab-pane label="llms.txt / agent.json" name="gen">
          <el-form label-width="110px" style="max-width: 640px">
            <el-form-item label="品牌名称">
              <el-input v-model="genForm.brandName" placeholder="如 飞虹智" />
            </el-form-item>
            <el-form-item label="站点 URL">
              <el-input v-model="genForm.siteUrl" placeholder="如 https://klai.top" />
            </el-form-item>
            <el-form-item label="一句话描述">
              <el-input v-model="genForm.description" placeholder="站点/产品一句话描述（供 AI Agent 理解）" />
            </el-form-item>
            <el-form-item label="页面清单">
              <el-input
                v-model="genForm.pages"
                type="textarea"
                :rows="3"
                placeholder="格式：标题: URL，逗号分隔。如：官网: https://klai.top, 开源矩阵: https://klai.top/opensource.html"
              />
            </el-form-item>
            <el-form-item label="技能清单">
              <el-input v-model="genForm.skills" placeholder="逗号分隔，如：品牌诊断, GEO 内容生成" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="genLoading" @click="handleGenerate('llms')">生成 llms.txt</el-button>
              <el-button :loading="genLoading" @click="handleGenerate('agent')">生成 agent.json</el-button>
            </el-form-item>
          </el-form>

          <template v-if="genOutput">
            <div class="gen-header">
              <span>{{ genOutputType === 'llms' ? 'llms.txt（站点根目录）' : 'agent.json（/.well-known/）' }}</span>
              <el-button size="small" type="success" plain @click="handleCopy">复制</el-button>
            </div>
            <pre class="gen-pre">{{ genOutput }}</pre>
          </template>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { geoValidate, aaoValidate, getLlmsTxt, getAgentJson } from '@/api/geo'

const activeTab = ref('geo')

// ---------- GEO 校验 ----------
const geoForm = ref({ content: '', keywords: '', publishDate: '' })
const geoLoading = ref(false)
const geoResult = ref(null)

async function handleGeoValidate() {
  if (!geoForm.value.content || !geoForm.value.content.trim()) {
    ElMessage.warning('请先输入内容正文')
    return
  }
  geoLoading.value = true
  try {
    const res = await geoValidate({
      content: geoForm.value.content,
      keywords: geoForm.value.keywords,
      publishDate: geoForm.value.publishDate || undefined
    })
    geoResult.value = res.data || res
    if (geoResult.value.blocked) {
      ElMessage.error('校验未通过：关键词堆砌')
    } else {
      ElMessage.success(`GEO 总分 ${geoResult.value.totalScore}`)
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '校验失败，请重试')
  } finally {
    geoLoading.value = false
  }
}

// ---------- AAO 评估 ----------
const aaoForm = ref({
  domain: '',
  allowAiCrawlers: false,
  hasContentSignals: false,
  hasLlmsTxt: false,
  hasLlmsFullTxt: false,
  hasApiCatalog: false,
  hasMcpCard: false,
  hasAgentJson: false,
  hasOpenApi: false,
  hasStructuredData: false,
  hasFaq: false,
  https: false,
  hasPrivacyPolicy: false,
  descriptionQuality: false,
  apiCount: 0,
  toolCount: 0
})
const aaoLoading = ref(false)
const aaoResult = ref(null)

async function handleAaoValidate() {
  aaoLoading.value = true
  try {
    const res = await aaoValidate({ ...aaoForm.value })
    aaoResult.value = res.data || res
    ElMessage.success(`AX Score ${aaoResult.value.axScore}（${aaoResult.value.grade}）`)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '评估失败，请重试')
  } finally {
    aaoLoading.value = false
  }
}

// ---------- 生成器 ----------
const genForm = ref({ brandName: '', siteUrl: '', description: '', pages: '', skills: '' })
const genLoading = ref(false)
const genOutput = ref('')
const genOutputType = ref('llms')

async function handleGenerate(type) {
  if (!genForm.value.brandName) {
    ElMessage.warning('请填写品牌名称')
    return
  }
  genLoading.value = true
  try {
    const params = {
      brandName: genForm.value.brandName,
      siteUrl: genForm.value.siteUrl,
      description: genForm.value.description
    }
    let res
    if (type === 'llms') {
      params.pages = genForm.value.pages
      res = await getLlmsTxt(params)
      genOutputType.value = 'llms'
    } else {
      params.skills = genForm.value.skills
      res = await getAgentJson(params)
      genOutputType.value = 'agent'
    }
    genOutput.value = res.data || res
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '生成失败，请重试')
  } finally {
    genLoading.value = false
  }
}

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(genOutput.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

// ---------- 工具 ----------
function scoreColor(score) {
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

function gradeType(grade) {
  const map = { Excellent: 'success', Good: 'warning', NeedsWork: 'danger', Poor: 'danger' }
  return map[grade] || 'info'
}
</script>

<style scoped>
.geo-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.geo-title {
  font-size: 16px;
  font-weight: 600;
}
.score-row {
  display: flex;
  align-items: center;
  gap: 24px;
  margin: 8px 0;
}
.score-big {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.score-num {
  font-size: 40px;
  font-weight: 700;
  color: #409eff;
}
.score-label {
  font-size: 12px;
  color: #909399;
}
.tactic-detail {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.suggest-box {
  background: #f8fafc;
  border-radius: 6px;
  padding: 12px 16px;
}
.suggest-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.suggest-box ul {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
  color: #606266;
}
.switch-hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.ax-grade {
  margin-left: 8px;
}
.gen-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin-bottom: 8px;
}
.gen-pre {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 6px;
  padding: 14px 16px;
  max-height: 360px;
  overflow: auto;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>

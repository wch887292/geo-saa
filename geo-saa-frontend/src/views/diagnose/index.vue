<template>
  <div class="diagnose" v-loading="pageLoading">
    <el-card shadow="hover" class="form-card">
      <template #header>
        <span>AI 诊断</span>
      </template>
      <el-form :model="form" label-width="110px" :rules="rules" ref="formRef">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="品牌名称" prop="brand">
              <el-input v-model="form.brand" placeholder="请输入品牌名称（必填）" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="竞品关键词" prop="competitor">
              <el-input v-model="form.competitor" placeholder="多个用逗号分隔，可选" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="24" :md="8">
            <el-form-item label="诊断平台">
              <el-checkbox-group v-model="form.platforms">
                <el-checkbox label="doubao" value="doubao">豆包</el-checkbox>
                <el-checkbox label="qianwen" value="qianwen">千问</el-checkbox>
                <el-checkbox label="kimi" value="kimi">Kimi</el-checkbox>
                <el-checkbox label="general" value="general">通用AI</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <el-button type="primary" size="large" @click="handleDiagnose" :loading="diagnosing">
            {{ diagnosing ? '诊断中...' : '一键诊断' }}
          </el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="showProgress" shadow="hover" class="progress-card">
      <template #header>
        <span>诊断进度</span>
      </template>
      <TaskProgress
        name="AI 生态数据采集与分析"
        :progress="progress"
        :status="progressStatus"
        :detail="progressDetail"
      />
    </el-card>

    <el-card v-if="showResult" shadow="hover" class="result-card">
      <template #header>
        <div class="card-header">
          <span>诊断结果</span>
          <el-button size="small" @click="showCompetitorDialog = true">竞品对比</el-button>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :xs="24" :md="8">
          <BrandScore :score="diagnosisResult.score" size="large" label="综合评分" :subtitle="diagnosisResult.scoreLevel" />
        </el-col>
        <el-col :xs="24" :md="16">
          <TrendChart :chart-data="diagnosisResult.platformData" type="bar" height="250px" />
        </el-col>
      </el-row>
      <el-divider />
      <el-row :gutter="20">
        <el-col :span="24">
          <el-card shadow="never" class="gap-card">
            <template #header><span>流量缺口分析</span></template>
            <p>{{ diagnosisResult.gapAnalysis || '暂无缺口分析数据' }}</p>
          </el-card>
        </el-col>
      </el-row>
      <el-row :gutter="20" class="content-row">
        <el-col :span="24">
          <el-card shadow="never">
            <template #header><span>优化建议</span></template>
            <el-timeline v-if="diagnosisResult.suggestions.length">
              <el-timeline-item
                v-for="(item, idx) in diagnosisResult.suggestions"
                :key="idx"
                :type="item.type"
                :timestamp="item.priority"
              >
                {{ item.content }}
              </el-timeline-item>
            </el-timeline>
            <div v-else class="empty-text">暂无优化建议</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="hover" class="history-card">
      <template #header>
        <span>历史诊断记录</span>
      </template>
      <el-table :data="historyList" stripe style="width: 100%" v-loading="historyLoading">
        <el-table-column prop="taskName" label="任务名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="brandName" label="品牌" width="140" show-overflow-tooltip />
        <el-table-column prop="taskType" label="诊断类型" width="160" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="taskStatusTag(row.status)">
              {{ taskStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="诊断时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="viewReport(row)">查看报告</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="historyPage"
          :page-size="10"
          :total="historyTotal"
          layout="prev, pager, next"
          small
          @current-change="loadHistory"
        />
      </div>
    </el-card>

    <el-dialog v-model="showCompetitorDialog" title="竞品对比分析" width="700px">
      <el-table :data="competitorData" stripe style="width: 100%" v-loading="competitorLoading">
        <el-table-column prop="name" label="品牌" width="120" />
        <el-table-column prop="score" label="综合评分" width="100">
          <template #default="{ row }">
            <el-tag :type="row.score >= 80 ? 'success' : row.score >= 60 ? 'warning' : 'danger'">
              {{ row.score }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="coverage" label="关键词覆盖" width="120" />
        <el-table-column prop="aiAdoption" label="AI采信率" width="120" />
        <el-table-column prop="gap" label="差距分析" min-width="180" />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="showCompetitorDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import BrandScore from '@/components/BrandScore.vue'
import TrendChart from '@/components/TrendChart.vue'
import TaskProgress from '@/components/TaskProgress.vue'
import {
  createDiagnose,
  getDiagnoseList,
  getDiagnoseProgress,
  getDiagnoseReport
} from '@/api/diagnose'

const pageLoading = ref(false)
const formRef = ref(null)
const diagnosing = ref(false)
const showProgress = ref(false)
const showResult = ref(false)
const progress = ref(0)
const historyLoading = ref(false)
const historyPage = ref(1)
const historyTotal = ref(0)
const showCompetitorDialog = ref(false)
const competitorLoading = ref(false)
const diagnoseTimer = ref(null)

const form = reactive({
  brand: '',
  competitor: '',
  platforms: ['doubao', 'qianwen', 'kimi', 'general']
})

const rules = {
  brand: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }]
}

const progressStatus = computed(() => {
  if (progress.value >= 100) return 'completed'
  return 'running'
})

const progressDetail = computed(() => {
  if (progress.value < 30) return '正在采集 AI 平台数据...'
  if (progress.value < 60) return '正在分析品牌提及情况...'
  if (progress.value < 90) return '正在生成诊断报告...'
  return '诊断即将完成'
})

const diagnosisResult = reactive({
  score: 0,
  scoreLevel: '',
  gapAnalysis: '',
  suggestions: [],
  platformData: { categories: [], series: [] }
})

const historyList = ref([])
const competitorData = ref([])

const TASK_STATUS = { 0: '待处理', 1: '进行中', 2: '已完成', 3: '失败' }
function taskStatusText(status) {
  return TASK_STATUS[status] || '未知'
}
function taskStatusTag(status) {
  return status === 2 ? 'success' : status === 3 ? 'danger' : 'warning'
}

function buildDiagnosisResult(report) {
  const inner = report.report || {}
  const score = typeof inner.score === 'number' ? inner.score : 0
  diagnosisResult.score = score
  diagnosisResult.scoreLevel = score >= 80 ? '优秀' : score >= 60 ? '良好' : '待提升'

  const gaps = Array.isArray(inner.gaps) ? inner.gaps : []
  diagnosisResult.gapAnalysis = gaps.length
    ? gaps.map((g) => `[${g.title || '缺口'}] ${g.description || ''}`).join('；')
    : (report.visibility ? `品牌「${report.visibility.brandName || ''}」在${report.visibility.platform || ''}平台的综合评分 ${score}。` : '暂无缺口分析数据')

  const suggestions = Array.isArray(inner.suggestions) ? inner.suggestions : []
  diagnosisResult.suggestions = suggestions.map((text, i) => ({
    content: text,
    type: i % 2 === 0 ? 'primary' : 'warning',
    priority: i < 2 ? '高优先级' : '中优先级'
  }))

  const competitor = Array.isArray(report.competitorComparison) ? report.competitorComparison : []
  diagnosisResult.platformData = {
    categories: competitor.map((c) => c.name),
    series: [{
      name: '综合评分',
      data: competitor.map((c) => c.score || 0)
    }]
  }
  competitorData.value = competitor.map((c) => ({
    name: c.name,
    score: c.score || 0,
    coverage: c.mentionRate != null ? c.mentionRate + '%' : '-',
    aiAdoption: c.firstRecommendRate != null ? c.firstRecommendRate + '%' : '-',
    gap: c.advantage || c.disadvantage || '-'
  }))
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await getDiagnoseList({ pageNum: historyPage.value, pageSize: 10 })
    historyList.value = res.data || []
    historyTotal.value = res.total || 0
  } catch {
    historyList.value = []
    historyTotal.value = 0
  } finally {
    historyLoading.value = false
  }
}

async function fetchReport(taskId) {
  const res = await getDiagnoseReport(taskId)
  buildDiagnosisResult(res.data || {})
  showResult.value = true
}

function pollProgress(taskId) {
  progress.value = 0
  diagnoseTimer.value = setInterval(async () => {
    try {
      const res = await getDiagnoseProgress(taskId)
      const tp = res.data
      if (tp) {
        progress.value = tp.percentage || 0
        if (tp.status === 'completed') {
          clearInterval(diagnoseTimer.value)
          diagnoseTimer.value = null
          diagnosing.value = false
          ElMessage.success('诊断完成')
          await fetchReport(taskId)
        } else if (tp.status === 'failed') {
          clearInterval(diagnoseTimer.value)
          diagnoseTimer.value = null
          diagnosing.value = false
          ElMessage.error(tp.message || '诊断失败')
        }
      }
    } catch {
      // 轮询异常（如任务尚未初始化进度），继续下一次
    }
  }, 1500)
}

function handleDiagnose() {
  formRef.value?.validate((valid) => {
    if (!valid) return
    diagnosing.value = true
    showProgress.value = true
    showResult.value = false
    progress.value = 0
    const payload = {
      taskName: form.brand + ' AI 诊断',
      taskType: form.platforms.join(','),
      brandName: form.brand,
      inputParams: form.competitor
    }
    createDiagnose(payload)
      .then((res) => {
        const task = res.data
        if (!task || !task.id) {
          diagnosing.value = false
          showProgress.value = false
          ElMessage.error('创建诊断任务失败')
          return
        }
        pollProgress(task.id)
      })
      .catch(() => {
        diagnosing.value = false
        showProgress.value = false
      })
  })
}

function resetForm() {
  formRef.value?.resetFields()
  showProgress.value = false
  showResult.value = false
  progress.value = 0
}

function viewReport(row) {
  competitorLoading.value = true
  fetchReport(row.id)
    .catch(() => ElMessage.error('获取报告失败'))
    .finally(() => {
      competitorLoading.value = false
    })
}

onBeforeUnmount(() => {
  if (diagnoseTimer.value) {
    clearInterval(diagnoseTimer.value)
    diagnoseTimer.value = null
  }
})

onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.diagnose { padding: 0; }
.form-card { margin-bottom: 20px; }
.progress-card { margin-bottom: 20px; }
.result-card { margin-bottom: 20px; }
.history-card { margin-bottom: 20px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.content-row { margin-top: 16px; }
.gap-card { margin-bottom: 16px; }
.gap-card p {
  font-size: 14px;
  line-height: 1.8;
  color: #606266;
  margin: 0;
}
.empty-text {
  text-align: center;
  color: #c0c4cc;
  padding: 20px 0;
  font-size: 14px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

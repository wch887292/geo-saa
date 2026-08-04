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
            <p>{{ diagnosisResult.gapAnalysis }}</p>
          </el-card>
        </el-col>
      </el-row>
      <el-row :gutter="20" class="content-row">
        <el-col :span="24">
          <el-card shadow="never">
            <template #header><span>优化建议</span></template>
            <el-timeline>
              <el-timeline-item
                v-for="(item, idx) in diagnosisResult.suggestions"
                :key="idx"
                :type="item.type"
                :timestamp="item.priority"
              >
                {{ item.content }}
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="hover" class="history-card">
      <template #header>
        <span>历史诊断记录</span>
      </template>
      <el-table :data="historyList" stripe style="width: 100%" v-loading="historyLoading">
        <el-table-column prop="brand" label="品牌" width="140" />
        <el-table-column prop="score" label="评分" width="80">
          <template #default="{ row }">
            <el-tag :type="row.score >= 80 ? 'success' : row.score >= 60 ? 'warning' : 'danger'">
              {{ row.score }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="platforms" label="诊断平台" width="200">
          <template #default="{ row }">
            <el-tag v-for="p in row.platforms" :key="p" size="small" style="margin-right:4px">{{ p }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : 'warning'">
              {{ row.status === 'completed' ? '已完成' : '进行中' }}
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
        />
      </div>
    </el-card>

    <el-dialog v-model="showCompetitorDialog" title="竞品对比分析" width="700px">
      <el-table :data="competitorData" stripe style="width: 100%">
        <el-table-column prop="name" label="品牌" width="120" />
        <el-table-column prop="score" label="综合评分" width="100">
          <template #default="{ row }">
            <el-tag :type="row.score >= 80 ? 'success' : 'warning'">{{ row.score }}</el-tag>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import BrandScore from '@/components/BrandScore.vue'
import TrendChart from '@/components/TrendChart.vue'
import TaskProgress from '@/components/TaskProgress.vue'

const pageLoading = ref(false)
const formRef = ref(null)
const diagnosing = ref(false)
const showProgress = ref(false)
const showResult = ref(false)
const progress = ref(0)
const historyLoading = ref(false)
const historyPage = ref(1)
const historyTotal = ref(12)
const showCompetitorDialog = ref(false)

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
  score: 72,
  scoreLevel: '良好',
  gapAnalysis: '当前品牌在 AI 平台上的关键词覆盖率为 65%，主要缺口集中在豆包和通用AI平台。建议优先补充产品参数类结构化数据，提升在 AI 搜索中的可见度。',
  suggestions: [
    { content: '补充产品核心参数的结构化数据标记（JSON-LD）', type: 'primary', priority: '高优先级' },
    { content: '在豆包平台增加品牌相关信息发布频率', type: 'warning', priority: '中优先级' },
    { content: '优化品牌关键词策略，覆盖更多长尾词', type: 'primary', priority: '高优先级' },
    { content: '建立品牌知识图谱，提升 AI 采信率', type: 'info', priority: '中优先级' }
  ],
  platformData: {
    categories: ['豆包', '千问', 'Kimi', '通用AI'],
    series: [{
      name: '采信率',
      data: [45, 72, 68, 55]
    }]
  }
})

const historyList = ref([
  { brand: '品牌A', score: 72, platforms: ['豆包', '千问', 'Kimi', '通用AI'], status: 'completed', createdAt: '2024-07-28 14:30' },
  { brand: '品牌A', score: 65, platforms: ['豆包', '千问'], status: 'completed', createdAt: '2024-07-21 10:00' },
  { brand: '品牌A', score: 58, platforms: ['豆包', '千问', 'Kimi'], status: 'completed', createdAt: '2024-07-14 16:20' }
])

const competitorData = ref([
  { name: '本品牌', score: 72, coverage: '65%', aiAdoption: '60%', gap: '-' },
  { name: '竞品A', score: 85, coverage: '78%', aiAdoption: '82%', gap: '领先 13 分' },
  { name: '竞品B', score: 68, coverage: '58%', aiAdoption: '55%', gap: '落后 4 分' }
])

function handleDiagnose() {
  formRef.value?.validate((valid) => {
    if (!valid) return
    diagnosing.value = true
    showProgress.value = true
    showResult.value = false
    progress.value = 0
    const timer = setInterval(() => {
      progress.value += Math.floor(Math.random() * 12) + 3
      if (progress.value >= 100) {
        progress.value = 100
        clearInterval(timer)
        setTimeout(() => {
          diagnosing.value = false
          showResult.value = true
          ElMessage.success('诊断完成')
        }, 500)
      }
    }, 400)
  })
}

function resetForm() {
  formRef.value?.resetFields()
  showProgress.value = false
  showResult.value = false
  progress.value = 0
}

function viewReport(row) {
  ElMessage.info('查看报告详情: ' + row.brand)
}

onMounted(() => {})
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
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
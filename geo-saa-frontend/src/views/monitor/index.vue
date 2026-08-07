<template>
  <div class="monitor" v-loading="pageLoading">
    <el-card shadow="hover" class="filter-card">
      <el-row :gutter="20" align="middle">
        <el-col :xs="12" :sm="8" :md="6">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width:100%"
            @change="handleQuery"
          />
        </el-col>
        <el-col :xs="12" :sm="6" :md="4">
          <el-button type="primary" @click="loadAll" style="width:100%">查询</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-row :gutter="20" class="stats-row">
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ stats.mentionRate }}%</div>
            <div class="stat-label">品牌 AI 提及率</div>
            <div class="stat-trend trend-up">环比 ↑ {{ stats.mentionRateChange }}%</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ stats.topRate }}%</div>
            <div class="stat-label">AI 首推占比</div>
            <div class="stat-trend" :class="stats.topRateChange >= 0 ? 'trend-up' : 'trend-down'">
              环比 {{ stats.topRateChange >= 0 ? '↑' : '↓' }} {{ Math.abs(stats.topRateChange) }}%
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ stats.brandCount }}</div>
            <div class="stat-label">监测品牌数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ stats.competitorCount }}</div>
            <div class="stat-label">竞品数据覆盖数</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="content-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <span>AI 提及率趋势（近 {{ trendDays }} 天）</span>
          </template>
          <TrendChart :chart-data="mentionTrendData" type="line" height="300px" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <span>AI 首推占比趋势</span>
          </template>
          <TrendChart :chart-data="topTrendData" type="line" height="300px" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="content-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <span>各平台收录量对比</span>
          </template>
          <TrendChart :chart-data="platformData" type="bar" height="300px" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <span>竞品多维对比</span>
          </template>
          <TrendChart :chart-data="competitorRadarData" type="radar" height="300px" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover">
      <template #header>
        <span>月度分析</span>
      </template>
      <el-table :data="analysisTable" stripe style="width:100%" v-loading="tableLoading">
        <el-table-column prop="month" label="月份" width="100" />
        <el-table-column prop="mentionRate" label="AI提及率" width="100">
          <template #default="{ row }">{{ row.mentionRate }}%</template>
        </el-table-column>
        <el-table-column prop="topRate" label="首推占比" width="100">
          <template #default="{ row }">{{ row.topRate }}%</template>
        </el-table-column>
        <el-table-column prop="coverage" label="关键词覆盖" width="100">
          <template #default="{ row }">{{ row.coverage }}%</template>
        </el-table-column>
        <el-table-column prop="competitorAvg" label="竞品均值" width="100">
          <template #default="{ row }">{{ row.competitorAvg }}%</template>
        </el-table-column>
        <el-table-column prop="trend" label="环比变化" width="100">
          <template #default="{ row }">
            <span :class="row.trend >= 0 ? 'trend-up' : 'trend-down'">
              {{ row.trend >= 0 ? '+' : '' }}{{ row.trend }}%
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import TrendChart from '@/components/TrendChart.vue'
import { getCoreMetrics, getTrend, getCompetitorComparison } from '@/api/monitor'

const pageLoading = ref(false)
const tableLoading = ref(false)
const dateRange = ref([])
const trendDays = ref(7)

const stats = reactive({
  mentionRate: 0,
  mentionRateChange: 0,
  topRate: 0,
  topRateChange: 0,
  brandCount: 0,
  competitorCount: 0
})

const mentionTrendData = reactive({ categories: [], series: [] })
const topTrendData = reactive({ categories: [], series: [] })
const platformData = reactive({ categories: [], series: [] })
const competitorRadarData = reactive({ indicator: [], series: [] })

const analysisTable = ref([])

function toLineData(trendRes, name) {
  const data = (trendRes && trendRes.data && trendRes.data.data) || []
  return {
    categories: data.map((d) => d.label),
    series: [{ name, data: data.map((d) => d.value) }]
  }
}

async function loadCoreMetricsAndCompetitors() {
  try {
    const metricsRes = await getCoreMetrics()
    const metrics = metricsRes.data || {}
    stats.mentionRate = metrics.mentionRate || 0
    stats.topRate = metrics.firstRecommendRate || 0

    const compRes = await getCompetitorComparison()
    const competitors = compRes.data || []
    stats.brandCount = 1
    stats.competitorCount = Math.max(0, competitors.length - 1)

    competitorRadarData.indicator = [
      { name: 'AI提及率', max: 100 },
      { name: '首推占比', max: 100 },
      { name: '收录量', max: 1000 }
    ]
    competitorRadarData.series = competitors.map((c) => ({
      value: [c.mentionRate || 0, c.firstRecommendRate || 0, c.collectionCount || 0],
      name: c.name
    }))
  } catch {
    // 指标/竞品接口失败时保留默认 0 态
  }
}

async function loadTrends() {
  try {
    const mention = await getTrend({ statType: 'mention_rate', period: 'day', days: trendDays.value })
    const md = toLineData(mention, 'AI提及率')
    mentionTrendData.categories = md.categories
    mentionTrendData.series = md.series

    const top = await getTrend({ statType: 'first_recommend_rate', period: 'day', days: trendDays.value })
    const td = toLineData(top, '首推占比')
    topTrendData.categories = td.categories
    topTrendData.series = td.series

    const platform = await getTrend({ statType: 'collection_count', period: 'day', days: trendDays.value })
    const pd = toLineData(platform, '收录量')
    platformData.categories = pd.categories
    platformData.series = pd.series
  } catch {
    // 趋势接口失败保留空态
  }
}

async function loadAnalysis() {
  tableLoading.value = true
  try {
    const mention = await getTrend({ statType: 'mention_rate', period: 'month', days: 365 })
    const top = await getTrend({ statType: 'first_recommend_rate', period: 'month', days: 365 })
    const mData = (mention.data && mention.data.data) || []
    const tData = (top.data && top.data.data) || []
    analysisTable.value = mData.map((m, i) => ({
      month: m.label,
      mentionRate: m.value,
      topRate: tData[i] ? tData[i].value : 0,
      coverage: 0,
      competitorAvg: 0,
      trend: 0
    }))
  } catch {
    analysisTable.value = []
  } finally {
    tableLoading.value = false
  }
}

function handleQuery() {
  if (dateRange.value && dateRange.value.length === 2) {
    const start = new Date(dateRange.value[0])
    const end = new Date(dateRange.value[1])
    const diffDays = Math.round((end - start) / 86400000) + 1
    trendDays.value = Math.min(Math.max(diffDays, 1), 90)
  } else {
    trendDays.value = 7
  }
  loadTrends()
}

function loadAll() {
  loadCoreMetricsAndCompetitors()
  loadTrends()
  loadAnalysis()
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.monitor { padding: 0; }
.filter-card { margin-bottom: 20px; }
.stats-row { margin-bottom: 20px; }
.stat-card { margin-bottom: 12px; }
.stat-content { text-align: center; padding: 8px 0; }
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 4px;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}
.stat-trend {
  font-size: 12px;
}
.trend-up { color: #67c23a; }
.trend-down { color: #f56c6c; }
.content-row { margin-bottom: 20px; }
</style>

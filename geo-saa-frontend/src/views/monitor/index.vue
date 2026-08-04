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
          />
        </el-col>
        <el-col :xs="12" :sm="6" :md="4">
          <el-button type="primary" @click="handleQuery" style="width:100%">查询</el-button>
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
            <span>AI 提及率趋势（多平台对比）</span>
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
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          :page-size="10"
          :total="total"
          layout="prev, pager, next"
          small
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import TrendChart from '@/components/TrendChart.vue'

const pageLoading = ref(false)
const tableLoading = ref(false)
const page = ref(1)
const total = ref(6)
const dateRange = ref([])

const stats = reactive({
  mentionRate: 72.5,
  mentionRateChange: 5.2,
  topRate: 38.2,
  topRateChange: 3.1,
  brandCount: 6,
  competitorCount: 18
})

const mentionTrendData = {
  categories: ['7/28', '7/29', '7/30', '7/31', '8/1', '8/2', '8/3'],
  series: [
    { name: '豆包', data: [45, 48, 52, 50, 55, 58, 62] },
    { name: '千问', data: [55, 58, 62, 60, 65, 68, 72] },
    { name: 'Kimi', data: [40, 42, 45, 48, 50, 52, 55] },
    { name: '通用AI', data: [60, 62, 65, 68, 70, 72, 75] }
  ]
}

const topTrendData = {
  categories: ['7/28', '7/29', '7/30', '7/31', '8/1', '8/2', '8/3'],
  series: [
    { name: '首推占比', data: [28, 30, 32, 35, 36, 37, 38] }
  ]
}

const platformData = {
  categories: ['豆包', '千问', 'Kimi', '通用AI', '文心一言'],
  series: [{
    name: '收录量',
    data: [128, 256, 96, 320, 192]
  }]
}

const competitorRadarData = {
  indicator: [
    { name: 'AI可见度', max: 100 },
    { name: '关键词覆盖', max: 100 },
    { name: '首推占比', max: 100 },
    { name: '情感倾向', max: 100 },
    { name: '内容质量', max: 100 }
  ],
  series: [
    { value: [86, 78, 38, 72, 80], name: '本品牌', areaStyle: { color: 'rgba(64,158,255,0.2)' } },
    { value: [72, 65, 32, 60, 70], name: '竞品A', areaStyle: { color: 'rgba(103,194,58,0.2)' } },
    { value: [68, 71, 28, 55, 65], name: '竞品B', areaStyle: { color: 'rgba(230,162,60,0.2)' } }
  ]
}

const analysisTable = ref([
  { month: '2024-07', mentionRate: 72.5, topRate: 38.2, coverage: 65, competitorAvg: 58.3, trend: 5.2 },
  { month: '2024-06', mentionRate: 68.3, topRate: 35.1, coverage: 62, competitorAvg: 56.8, trend: 3.8 },
  { month: '2024-05', mentionRate: 64.5, topRate: 32.0, coverage: 58, competitorAvg: 55.2, trend: 4.2 },
  { month: '2024-04', mentionRate: 60.3, topRate: 28.5, coverage: 55, competitorAvg: 53.5, trend: -1.5 },
  { month: '2024-03', mentionRate: 61.8, topRate: 30.2, coverage: 56, competitorAvg: 54.0, trend: 2.1 },
  { month: '2024-02', mentionRate: 59.7, topRate: 28.8, coverage: 53, competitorAvg: 52.6, trend: 0.5 }
])

function handleQuery() {
  ElMessage.success('数据已更新')
}

onMounted(() => {})
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
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
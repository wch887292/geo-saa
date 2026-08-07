<template>
  <div class="dashboard" v-loading="pageLoading">
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <BrandScore :score="stats.visibilityScore" size="small" label="" />
            <div class="stat-info">
              <div class="stat-label">AI 可见度评分</div>
              <div class="stat-trend trend-up">↑ {{ stats.visibilityChange }}%</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon-box" style="background:#e1f3d8">
              <el-icon color="#67c23a" :size="32"><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">内容产出总量</div>
              <div class="stat-value">{{ stats.contentTotal }}</div>
              <div class="stat-trend trend-up">↑ {{ stats.contentGrowth }}%</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon-box" style="background:#fdf6ec">
              <el-icon color="#e6a23c" :size="32"><Promotion /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">分发成功量</div>
              <div class="stat-value">{{ stats.distributeSuccess }}</div>
              <el-progress :percentage="stats.distributeRate" :stroke-width="6" />
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon-box" style="background:#fde2e2">
              <el-icon color="#f56c6c" :size="32"><Medal /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">竞品排名</div>
              <div class="stat-value">#{{ stats.rank }}</div>
              <div class="stat-trend" :class="stats.rankChange > 0 ? 'trend-up' : 'trend-down'">
                {{ stats.rankChange > 0 ? '↑' : '↓' }} {{ Math.abs(stats.rankChange) }} 位
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="content-row">
      <el-col :xs="24" :lg="16">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>品牌 AI 可见度趋势</span>
              <el-tag size="small">近7天</el-tag>
            </div>
          </template>
          <TrendChart :chart-data="trendData" type="line" height="300px" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="todo-card">
          <template #header>
            <span>今日待办</span>
          </template>
          <el-empty v-if="todos.length === 0" description="暂无待办" :image-size="50" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="item in todos"
              :key="item.id"
              :timestamp="item.time"
              :type="item.done ? 'success' : 'primary'"
              :hollow="item.done"
              size="small"
            >
              <span :class="{ 'todo-done': item.done }">{{ item.text }}</span>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="content-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <span>运行中任务</span>
          </template>
          <div v-if="runningTasks.length === 0" class="empty-text">暂无运行中的任务</div>
          <div v-else class="task-list">
            <TaskProgress
              v-for="task in runningTasks"
              :key="task.id"
              :name="task.name"
              :progress="task.progress"
              :status="task.status"
              :detail="task.detail"
            />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <span>最近报告</span>
          </template>
          <div v-if="recentReports.length === 0" class="empty-text">暂无报告</div>
          <div class="report-list" v-else>
            <div class="report-item" v-for="report in recentReports" :key="report.id">
              <el-icon color="#409eff"><Document /></el-icon>
              <el-link :underline="false" class="report-name">{{ report.name }}</el-link>
              <span class="report-date">{{ report.date }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import BrandScore from '@/components/BrandScore.vue'
import TrendChart from '@/components/TrendChart.vue'
import TaskProgress from '@/components/TaskProgress.vue'
import { getDashboardStatistics } from '@/api/statistics'

const pageLoading = ref(false)

const stats = reactive({
  visibilityScore: 0,
  visibilityChange: 0,
  contentTotal: 0,
  contentGrowth: 0,
  distributeSuccess: 0,
  distributeRate: 0,
  rank: '-',
  rankChange: 0
})

const trendData = reactive({ categories: [], series: [] })
const todos = ref([])
const runningTasks = ref([])
const recentReports = ref([])

function num(v, fallback = 0) {
  return typeof v === 'number' ? v : fallback
}

async function loadDashboard() {
  pageLoading.value = true
  try {
    const res = await getDashboardStatistics()
    const data = res.data || {}
    stats.visibilityScore = num(data.visibilityScore)
    stats.visibilityChange = num(data.visibilityChange)
    stats.contentTotal = num(data.contentTotal)
    stats.contentGrowth = num(data.contentGrowth)
    stats.distributeSuccess = num(data.distributeSuccess)
    stats.distributeRate = num(data.distributeRate)
    stats.rank = data.rank == null ? '-' : data.rank
    stats.rankChange = num(data.rankChange)

    const td = data.trendData || {}
    trendData.categories = td.categories || []
    trendData.series = td.series || []

    todos.value = data.todos || []
    runningTasks.value = (data.runningTasks || []).map((t) => ({
      id: t.id,
      name: t.name,
      progress: num(t.progress),
      status: t.status,
      detail: t.detail
    }))
    recentReports.value = (data.recentReports || []).map((r) => ({
      id: r.id,
      name: r.name,
      date: r.date
    }))
  } catch {
    // 接口失败时保留默认空态，不阻断页面渲染
  } finally {
    pageLoading.value = false
  }
}

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped>
.dashboard { padding: 0; }
.stats-row { margin-bottom: 20px; }
.stat-card { margin-bottom: 12px; }
.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon-box {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-info { flex: 1; }
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 4px;
}
.stat-trend {
  font-size: 12px;
}
.trend-up { color: #67c23a; }
.trend-down { color: #f56c6c; }
.content-row { margin-bottom: 20px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.todo-card { margin-bottom: 12px; }
.todo-done {
  text-decoration: line-through;
  color: #c0c4cc;
}
.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.report-list {
  display: flex;
  flex-direction: column;
}
.report-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}
.report-item:last-child { border-bottom: none; }
.report-name { flex: 1; font-size: 14px; }
.report-date {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
.empty-text {
  text-align: center;
  color: #c0c4cc;
  padding: 20px 0;
  font-size: 14px;
}
.chart-card { margin-bottom: 12px; }
</style>

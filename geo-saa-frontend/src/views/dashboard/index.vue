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
          <el-timeline>
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
import { ElMessage } from 'element-plus'

const pageLoading = ref(false)

const stats = reactive({
  visibilityScore: 86,
  visibilityChange: 5.2,
  contentTotal: '1,284',
  contentGrowth: 12.3,
  distributeSuccess: '967',
  distributeRate: 75,
  rank: 3,
  rankChange: 2
})

const trendData = {
  categories: ['7/28', '7/29', '7/30', '7/31', '8/1', '8/2', '8/3'],
  series: [
    { name: '本品牌', data: [65, 72, 68, 78, 82, 79, 86] },
    { name: '竞品A', data: [55, 58, 62, 60, 65, 63, 60] },
    { name: '竞品B', data: [45, 48, 52, 50, 55, 58, 54] }
  ]
}

const todos = ref([
  { id: 1, text: '完成品牌 AI 诊断报告', time: '09:00', done: false },
  { id: 2, text: '审核本周创作内容', time: '10:30', done: false },
  { id: 3, text: '检查各渠道分发状态', time: '14:00', done: true },
  { id: 4, text: '更新知识库条目', time: '16:00', done: false }
])

const runningTasks = ref([
  { id: 1, name: '全域内容分发 - 品牌A', progress: 65, status: 'running', detail: '3/5 渠道完成' },
  { id: 2, name: '竞品数据采集', progress: 30, status: 'running', detail: '采集分析中' }
])

const recentReports = ref([
  { id: 1, name: '2024年7月AI可见度报告', date: '2024-07-31' },
  { id: 2, name: '竞品分析报告 - 季度', date: '2024-07-25' },
  { id: 3, name: '内容质量评估报告', date: '2024-07-20' }
])

onMounted(() => {
  // API calls would go here
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
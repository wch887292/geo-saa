<template>
  <div class="ai-call-log">
    <el-card shadow="never" class="stats-card">
      <template #header>
        <span class="title">调用统计</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="small"
          style="margin-left: 16px"
          @change="fetchStatistics"
        />
      </template>
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ statistics.totalCalls || 0 }}</div>
            <div class="stat-label">总调用次数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item success">
            <div class="stat-value">{{ statistics.successRate ? statistics.successRate.toFixed(1) : '0' }}%</div>
            <div class="stat-label">成功率</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item warning">
            <div class="stat-value">{{ statistics.totalTokens || 0 }}</div>
            <div class="stat-label">总Token消耗</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item info">
            <div class="stat-value">${{ statistics.totalCost ? Number(statistics.totalCost).toFixed(4) : '0.0000' }}</div>
            <div class="stat-label">估算费用(美元)</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">调用日志</span>
          <div class="filters">
            <el-input v-model="filterModelName" placeholder="模型名称" clearable size="small" style="width:160px" @clear="fetchList" />
            <el-select v-model="filterModule" placeholder="业务模块" clearable size="small" style="width:140px">
              <el-option label="内容生成" value="content" />
              <el-option label="知识库" value="knowledge" />
              <el-option label="AI诊断" value="diagnose" />
              <el-option label="RAG检索" value="rag" />
              <el-option label="GEO优化" value="geo" />
              <el-option label="系统" value="system" />
            </el-select>
            <el-select v-model="filterStatus" placeholder="状态" clearable size="small" style="width:100px">
              <el-option label="成功" value="success" />
              <el-option label="失败" value="failed" />
              <el-option label="超时" value="timeout" />
            </el-select>
            <el-button type="primary" size="small" @click="fetchList">查询</el-button>
          </div>
        </div>
      </template>

      <el-table :data="logList" v-loading="loading" stripe max-height="500">
        <el-table-column prop="createdAt" label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型" min-width="120" show-overflow-tooltip />
        <el-table-column prop="provider" label="提供商" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ row.provider }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="businessModule" label="模块" width="90" align="center">
          <template #default="{ row }">{{ moduleLabel(row.businessModule) }}</template>
        </el-table-column>
        <el-table-column prop="businessType" label="类型" width="90" align="center" />
        <el-table-column prop="totalTokens" label="Token" width="90" align="center" />
        <el-table-column prop="latencyMs" label="耗时" width="80" align="center">
          <template #default="{ row }">{{ row.latencyMs }}ms</template>
        </el-table-column>
        <el-table-column prop="costAmount" label="费用" width="90" align="center">
          <template #default="{ row }">${{ Number(row.costAmount || 0).toFixed(4) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">
              {{ row.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="调用详情" width="700px">
      <el-descriptions :column="2" border v-if="currentLog">
        <el-descriptions-item label="调用时间">{{ formatTime(currentLog.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="请求ID">{{ currentLog.requestId }}</el-descriptions-item>
        <el-descriptions-item label="模型名称">{{ currentLog.modelName }}</el-descriptions-item>
        <el-descriptions-item label="模型ID">{{ currentLog.modelId }}</el-descriptions-item>
        <el-descriptions-item label="提供商">{{ currentLog.provider }}</el-descriptions-item>
        <el-descriptions-item label="业务模块">{{ moduleLabel(currentLog.businessModule) }}</el-descriptions-item>
        <el-descriptions-item label="输入Token">{{ currentLog.promptTokens }}</el-descriptions-item>
        <el-descriptions-item label="输出Token">{{ currentLog.completionTokens }}</el-descriptions-item>
        <el-descriptions-item label="总Token">{{ currentLog.totalTokens }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentLog.latencyMs }}ms</el-descriptions-item>
        <el-descriptions-item label="费用">${{ Number(currentLog.costAmount || 0).toFixed(6) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentLog.status === 'success' ? 'success' : 'danger'" size="small">
            {{ currentLog.status }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">请求 Prompt</el-divider>
      <div class="log-content">{{ currentLog?.promptText }}</div>
      <el-divider content-position="left">响应内容</el-divider>
      <div class="log-content">{{ currentLog?.responseText }}</div>
      <el-divider v-if="currentLog?.errorMessage" content-position="left">错误信息</el-divider>
      <div v-if="currentLog?.errorMessage" class="log-content error">{{ currentLog.errorMessage }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCallLogList, getCallStatistics } from '@/api/aiCallLog'

const loading = ref(false)
const logList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filterModelName = ref('')
const filterModule = ref('')
const filterStatus = ref('')
const dateRange = ref([])

const statistics = reactive({
  totalCalls: 0,
  successRate: 0,
  totalTokens: 0,
  totalCost: 0
})

const detailVisible = ref(false)
const currentLog = ref(null)

function moduleLabel(module) {
  const map = { content: '内容生成', knowledge: '知识库', diagnose: 'AI诊断', rag: 'RAG检索', geo: 'GEO优化', system: '系统', other: '其他' }
  return map[module] || module || '-'
}

function formatTime(time) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

async function fetchList() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      modelName: filterModelName.value || undefined,
      businessModule: filterModule.value || undefined,
      status: filterStatus.value || undefined
    }
    const res = await getCallLogList(params)
    logList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function fetchStatistics() {
  try {
    const params = {}
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0] + ' 00:00:00'
      params.endTime = dateRange.value[1] + ' 23:59:59'
    }
    const res = await getCallStatistics(params)
    Object.assign(statistics, res.data || {})
  } catch (e) {
    // 统计接口失败不影响日志展示
  }
}

function showDetail(row) {
  currentLog.value = row
  detailVisible.value = true
}

onMounted(() => {
  fetchList()
  fetchStatistics()
})
</script>

<style scoped>
.ai-call-log { padding: 0; }
.stats-card { margin-bottom: 16px; }
.title { font-size: 16px; font-weight: 600; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.filters { display: flex; gap: 8px; align-items: center; }
.stat-item { text-align: center; padding: 12px; }
.stat-value { font-size: 28px; font-weight: 700; color: #303133; }
.stat-item.success .stat-value { color: #67c23a; }
.stat-item.warning .stat-value { color: #e6a23c; }
.stat-item.info .stat-value { color: #909399; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.log-content {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.log-content.error { color: #f56c6c; background: #fef0f0; }
</style>

<template>
  <div class="asset" v-loading="pageLoading">
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="8" :sm="8" :md="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalAssets }}</div>
            <div class="stat-label">内容资产数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="8" :sm="8" :md="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ stats.published }}</div>
            <div class="stat-label">已分发发布数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="8" :sm="8" :md="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ stats.screenshots }}</div>
            <div class="stat-label">截图存证数</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="filter-card">
      <el-row :gutter="20" align="middle">
        <el-col :span="6">
          <el-date-picker v-model="filterYear" type="year" placeholder="选择年份" style="width:100%" value-format="yyyy" />
        </el-col>
        <el-col :span="6">
          <el-date-picker v-model="filterMonth" type="month" placeholder="选择月份" style="width:100%" value-format="yyyy-MM" />
        </el-col>
        <el-col :span="2">
          <el-button type="primary" @click="loadAll">过滤</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="hover" class="timeline-card">
      <template #header>
        <span>品牌资产时间线（内容 / 知识 / 分发 / 诊断）</span>
      </template>
      <el-empty v-if="timeline.length === 0" description="暂无资产" :image-size="50" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="item in timeline"
          :key="item.assetType + '-' + item.id"
          :timestamp="item.date"
          :type="item.type"
          size="large"
          @click="toggleTimelineDetail(item)"
        >
          <div class="timeline-content">
            <h4>{{ item.title }}</h4>
            <p>{{ item.description }}</p>
            <el-tag size="small" style="margin-top:4px">{{ item.typeLabel }} · {{ item.status }}</el-tag>
            <div v-if="item.showDetail" class="timeline-detail">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="资产类型">{{ assetTypeLabel(item.assetType) }}</el-descriptions-item>
                <el-descriptions-item label="状态">{{ item.status }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ item.date }}</el-descriptions-item>
                <el-descriptions-item label="归属">{{ item.brandName || item.brandId || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <el-row :gutter="20" class="content-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <span>内容存档（内容资产）</span>
          </template>
          <el-empty v-if="archiveList.length === 0" description="暂无存档" :image-size="50" />
          <el-table v-else :data="archiveList" stripe style="width:100%">
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="date" label="日期" width="120" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="downloadAsset(row)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>媒体发布记录（分发任务 · 已完成）</span>
            </div>
          </template>
          <el-empty v-if="publishRecords.length === 0" description="暂无发布记录" :image-size="50" />
          <el-table v-else :data="publishRecords" stripe style="width:100%">
            <el-table-column prop="title" label="内容标题" min-width="140" show-overflow-tooltip />
            <el-table-column prop="channel" label="发布渠道" width="100" />
            <el-table-column prop="date" label="发布时间" width="120" />
            <el-table-column label="截图" width="70">
              <template #default>
                <el-button size="small" type="primary" link @click="ElMessage.info('截图存证功能待接入')">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { getAssetOverview, getAssetList } from '@/api/asset'

const pageLoading = ref(false)
const filterYear = ref(null)
const filterMonth = ref(null)

const stats = reactive({
  totalAssets: 0,
  published: 0,
  screenshots: 0
})

const timeline = ref([])
const archiveList = ref([])
const publishRecords = ref([])

const ASSET_TYPE_LABEL = {
  content: '内容',
  knowledge: '知识',
  distribute: '分发',
  diagnose: '诊断'
}

function assetTypeLabel(type) {
  return ASSET_TYPE_LABEL[type] || type || '未知'
}

function resolveYearMonth() {
  let year = null
  let month = null
  if (filterYear.value) {
    const y = parseInt(String(filterYear.value).slice(0, 4), 10)
    if (!Number.isNaN(y)) year = y
  }
  if (filterMonth.value) {
    const parts = String(filterMonth.value).split('-')
    if (parts.length >= 2) {
      const m = parseInt(parts[1], 10)
      if (!Number.isNaN(m)) month = m
    }
  }
  return { year, month }
}

async function loadStatistics() {
  try {
    const res = await getAssetOverview()
    const data = res.data || {}
    stats.totalAssets = data.totalAssets || 0
    stats.published = data.published || 0
    stats.screenshots = data.screenshots || 0
  } catch {
    stats.totalAssets = 0
    stats.published = 0
  }
}

function toTimelineItem(it) {
  const status = it.status
  return {
    id: it.id,
    assetType: it.assetType,
    title: it.title,
    description: it.description || '',
    date: it.date || '-',
    type: status === 2 ? 'success' : status === 3 ? 'danger' : 'primary',
    typeLabel: assetTypeLabel(it.assetType),
    status: it.statusText || '未知',
    brandName: it.brandName,
    brandId: it.brandId,
    showDetail: false
  }
}

async function loadTimeline() {
  try {
    const { year, month } = resolveYearMonth()
    const res = await getAssetList({ pageNum: 1, pageSize: 50, year, month })
    const list = res.data || []
    timeline.value = list.map(toTimelineItem)
  } catch {
    timeline.value = []
  }
}

async function loadArchive() {
  try {
    const { year, month } = resolveYearMonth()
    const res = await getAssetList({ assetType: 'content', pageNum: 1, pageSize: 50, year, month })
    const list = res.data || []
    archiveList.value = list.map((it) => ({
      id: it.id,
      title: it.title,
      type: (it.extra && it.extra.contentType) || assetTypeLabel(it.assetType),
      date: it.date || ''
    }))
  } catch {
    archiveList.value = []
  }
}

async function loadPublishRecords() {
  try {
    const { year, month } = resolveYearMonth()
    const res = await getAssetList({ assetType: 'distribute', status: 2, pageNum: 1, pageSize: 50, year, month })
    const list = res.data || []
    publishRecords.value = list.map((it) => ({
      id: it.id,
      title: it.title,
      channel: (it.extra && it.extra.targetPlatform) || '-',
      date: (it.extra && it.extra.publishTime) || it.date || ''
    }))
  } catch {
    publishRecords.value = []
  }
}

function downloadAsset(row) {
  ElMessage.info(`导出接口待接入：${row.title}`)
}

function toggleTimelineDetail(item) {
  item.showDetail = !item.showDetail
}

function loadAll() {
  pageLoading.value = true
  Promise.all([loadStatistics(), loadTimeline(), loadArchive(), loadPublishRecords()])
    .finally(() => { pageLoading.value = false })
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.asset { padding: 0; }
.stats-row { margin-bottom: 20px; }
.stat-card { margin-bottom: 12px; }
.stat-content {
  text-align: center;
  padding: 8px 0;
}
.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #409eff;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.filter-card { margin-bottom: 20px; }
.timeline-card { margin-bottom: 20px; }
.timeline-content { cursor: pointer; }
.timeline-content h4 {
  margin: 0 0 4px;
  font-size: 15px;
  color: #303133;
}
.timeline-content p {
  margin: 0;
  font-size: 13px;
  color: #909399;
}
.timeline-detail {
  margin-top: 8px;
}
.content-row { margin-bottom: 20px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>

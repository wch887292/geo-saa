<template>
  <div class="asset" v-loading="pageLoading">
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="8" :sm="8" :md="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalAssets }}</div>
            <div class="stat-label">总资产数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="8" :sm="8" :md="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ stats.published }}</div>
            <div class="stat-label">已发布数</div>
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
          <el-date-picker v-model="filterYear" type="year" placeholder="选择年份" style="width:100%" />
        </el-col>
        <el-col :span="6">
          <el-date-picker v-model="filterMonth" type="month" placeholder="选择月份" style="width:100%" />
        </el-col>
        <el-col :span="2">
          <el-button type="primary" @click="handleFilter">过滤</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="hover" class="timeline-card">
      <template #header>
        <span>内容资产时间线</span>
      </template>
      <el-timeline>
        <el-timeline-item
          v-for="item in timeline"
          :key="item.id"
          :timestamp="item.date"
          :type="item.type"
          size="large"
          @click="toggleTimelineDetail(item)"
        >
          <div class="timeline-content">
            <h4>{{ item.title }}</h4>
            <p>{{ item.description }}</p>
            <el-tag size="small" style="margin-top:4px">{{ item.typeLabel }}</el-tag>
            <div v-if="item.showDetail" class="timeline-detail">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="状态">{{ item.status }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ item.createdAt }}</el-descriptions-item>
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
            <span>内容存档</span>
          </template>
          <el-table :data="archiveList" stripe style="width:100%">
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="80" />
            <el-table-column prop="date" label="日期" width="120" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default>
                <el-button size="small" type="primary" link>下载</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>媒体发布记录</span>
              <el-button size="small" @click="showUpload = true">上传截图</el-button>
            </div>
          </template>
          <el-table :data="publishRecords" stripe style="width:100%">
            <el-table-column prop="title" label="内容标题" min-width="140" show-overflow-tooltip />
            <el-table-column prop="channel" label="发布渠道" width="100" />
            <el-table-column prop="date" label="发布时间" width="120" />
            <el-table-column label="截图" width="70">
              <template #default>
                <el-button size="small" type="primary" link @click="viewScreenshot">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="showUpload" title="上传截图" width="500px">
      <el-upload
        drag
        action="#"
        :auto-upload="false"
        list-type="picture-card"
        multiple
        :on-change="handleUploadChange"
      >
        <el-icon class="upload-icon" :size="32"><Plus /></el-icon>
        <div class="upload-text">点击或拖拽上传</div>
      </el-upload>
      <div style="margin-top:12px">
        <el-radio-group v-model="uploadType">
          <el-radio label="publish">发布截图</el-radio>
          <el-radio label="ai">AI平台引用截图</el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="showUpload = false">取消</el-button>
        <el-button type="primary" @click="handleUpload">确认上传</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showScreenshot" title="截图预览" width="500px">
      <div class="screenshot-placeholder">
        <el-icon :size="48" color="#c0c4cc"><Picture /></el-icon>
        <p>截图预览区域</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="showScreenshot = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const pageLoading = ref(false)
const showUpload = ref(false)
const showScreenshot = ref(false)
const uploadType = ref('publish')
const filterYear = ref(null)
const filterMonth = ref(null)

const stats = reactive({
  totalAssets: 128,
  published: 96,
  screenshots: 45
})

const timeline = ref([
  { id: 1, title: '品牌内容策略文档', description: '完成品牌核心内容策略制定', date: '2024-07-28', type: 'primary', typeLabel: '策略文档', status: '已完成', createdAt: '2024-07-28 10:00', showDetail: false },
  { id: 2, title: '产品白皮书发布', description: '发布至官网及行业媒体', date: '2024-07-25', type: 'success', typeLabel: '发布', status: '已发布', createdAt: '2024-07-25 14:30', showDetail: false },
  { id: 3, title: '行业洞察报告', description: '生成并分发至各渠道', date: '2024-07-20', type: 'warning', typeLabel: '报告', status: '已完成', createdAt: '2024-07-20 09:00', showDetail: false },
  { id: 4, title: '品牌故事创作', description: '完成品牌故事初稿', date: '2024-07-15', type: 'info', typeLabel: '创作', status: '草稿', createdAt: '2024-07-15 16:00', showDetail: false }
])

const archiveList = ref([
  { title: 'Q3品牌内容资产包', type: '压缩包', date: '2024-07-30' },
  { title: '产品白皮书V2.1', type: 'PDF', date: '2024-07-25' },
  { title: '行业洞察报告-7月', type: 'PDF', date: '2024-07-20' }
])

const publishRecords = ref([
  { title: '产品白皮书V2.1', channel: '官网', date: '2024-07-25' },
  { title: 'AI驱动品牌增长', channel: '知乎', date: '2024-07-22' },
  { title: '行业洞察报告', channel: '微信公众号', date: '2024-07-20' },
  { title: '品牌故事', channel: '微博', date: '2024-07-15' }
])

function toggleTimelineDetail(item) {
  item.showDetail = !item.showDetail
}

function handleFilter() {
  ElMessage.success('过滤完成')
}

function handleUploadChange(file) {
  // preview handled by upload component
}

function handleUpload() {
  showUpload.value = false
  stats.screenshots++
  ElMessage.success('截图上传成功')
}

function viewScreenshot() {
  showScreenshot.value = true
}

onMounted(() => {})
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
.upload-icon { margin-bottom: 8px; }
.upload-text {
  font-size: 14px;
  color: #606266;
}
.screenshot-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px;
  background: #f5f7fa;
  border-radius: 8px;
  color: #c0c4cc;
}
</style>
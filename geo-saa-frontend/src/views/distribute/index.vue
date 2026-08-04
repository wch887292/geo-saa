<template>
  <div class="distribute" v-loading="pageLoading">
    <el-row :gutter="20">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="create-card">
          <template #header>
            <span>创建分发任务</span>
          </template>
          <el-form :model="taskForm" label-width="100px" :rules="taskRules" ref="taskFormRef">
            <el-form-item label="任务名称" prop="name">
              <el-input v-model="taskForm.name" placeholder="请输入任务名称" />
            </el-form-item>
            <el-form-item label="选择内容" prop="contentId">
              <el-select v-model="taskForm.contentId" placeholder="选择要分发的内容" style="width:100%">
                <el-option v-for="item in contentOptions" :key="item.id" :label="item.title" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="分发渠道" prop="channels">
              <div class="channel-selector">
                <el-collapse v-model="activeChannelCategories">
                  <el-collapse-item title="央媒 ({{ categorizedChannels['央媒']?.length || 0 }})" name="央媒">
                    <el-checkbox-group v-model="taskForm.channels">
                      <el-checkbox v-for="ch in categorizedChannels['央媒']" :key="ch.id" :label="ch.id" :value="ch.id">
                        {{ ch.name }}
                      </el-checkbox>
                    </el-checkbox-group>
                  </el-collapse-item>
                  <el-collapse-item title="地方媒体 ({{ categorizedChannels['地方媒体']?.length || 0 }})" name="地方媒体">
                    <el-checkbox-group v-model="taskForm.channels">
                      <el-checkbox v-for="ch in categorizedChannels['地方媒体']" :key="ch.id" :label="ch.id" :value="ch.id">
                        {{ ch.name }}
                      </el-checkbox>
                    </el-checkbox-group>
                  </el-collapse-item>
                  <el-collapse-item title="行业媒体 ({{ categorizedChannels['行业媒体']?.length || 0 }})" name="行业媒体">
                    <el-checkbox-group v-model="taskForm.channels">
                      <el-checkbox v-for="ch in categorizedChannels['行业媒体']" :key="ch.id" :label="ch.id" :value="ch.id">
                        {{ ch.name }}
                      </el-checkbox>
                    </el-checkbox-group>
                  </el-collapse-item>
                  <el-collapse-item title="自媒体 ({{ categorizedChannels['自媒体']?.length || 0 }})" name="自媒体">
                    <el-checkbox-group v-model="taskForm.channels">
                      <el-checkbox v-for="ch in categorizedChannels['自媒体']" :key="ch.id" :label="ch.id" :value="ch.id">
                        {{ ch.name }}
                      </el-checkbox>
                    </el-checkbox-group>
                  </el-collapse-item>
                  <el-collapse-item title="AI 平台 ({{ categorizedChannels['AI平台']?.length || 0 }})" name="AI平台">
                    <el-checkbox-group v-model="taskForm.channels">
                      <el-checkbox v-for="ch in categorizedChannels['AI平台']" :key="ch.id" :label="ch.id" :value="ch.id">
                        {{ ch.name }}
                      </el-checkbox>
                    </el-checkbox-group>
                  </el-collapse-item>
                </el-collapse>
                <div class="selected-count">已选 {{ taskForm.channels.length }} 个渠道</div>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="large" @click="createTask" :loading="creating">
                {{ creating ? '创建中...' : '创建任务' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="progress-card">
          <template #header>
            <span>进行中任务</span>
          </template>
          <div v-if="tasks.length === 0" class="empty-text">暂无进行中的任务</div>
          <div v-for="task in tasks" :key="task.id" class="task-item">
            <div class="task-header">
              <span class="task-name">{{ task.name }}</span>
              <div>
                <el-tag :type="task.status === 'completed' ? 'success' : task.status === 'partial' ? 'warning' : 'primary'" size="small">
                  {{ task.status === 'completed' ? '已完成' : task.status === 'partial' ? '部分失败' : '进行中' }}
                </el-tag>
                <el-button size="small" type="primary" link style="margin-left:8px" @click="showTaskDetail(task)">详情</el-button>
              </div>
            </div>
            <div class="channel-progress" v-for="ch in task.channelProgress" :key="ch.name">
              <span class="channel-name">{{ ch.name }}</span>
              <el-progress
                :percentage="ch.progress"
                :status="ch.progress === 100 ? 'success' : ch.status === 'failed' ? 'exception' : ''"
                :stroke-width="8"
              />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="history-card">
      <template #header>
        <span>任务列表</span>
      </template>
      <el-table :data="taskList" stripe style="width:100%" v-loading="listLoading">
        <el-table-column prop="name" label="任务名称" min-width="160" />
        <el-table-column prop="contentTitle" label="内容标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="channelCount" label="渠道数" width="80" />
        <el-table-column prop="successCount" label="成功数" width="80" />
        <el-table-column prop="failCount" label="失败数" width="80" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : row.status === 'partial' ? 'warning' : 'primary'">
              {{ row.status === 'completed' ? '已完成' : row.status === 'partial' ? '部分失败' : '进行中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="showTaskDetail(row)">详情</el-button>
            <el-button v-if="row.failCount > 0" size="small" type="warning" link @click="retryFailed(row)">重试失败项</el-button>
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

    <el-dialog v-model="showDetailDialog" :title="'任务详情 - ' + (currentTask?.name || '')" width="600px">
      <div v-if="currentTask">
        <div class="detail-summary">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="内容标题">{{ currentTask.contentTitle || '-' }}</el-descriptions-item>
            <el-descriptions-item label="渠道总数">{{ currentTask.channelCount || currentTask.channelProgress?.length }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <el-divider />
        <div v-for="ch in (currentTask.channelProgress || [])" :key="ch.name" class="channel-progress">
          <span class="channel-name">{{ ch.name }}</span>
          <el-progress
            :percentage="ch.progress"
            :status="ch.progress === 100 ? 'success' : ch.status === 'failed' ? 'exception' : ''"
            :stroke-width="10"
          />
          <el-tag v-if="ch.status === 'failed'" size="small" type="danger" style="margin-top:4px">失败</el-tag>
          <el-tag v-else-if="ch.progress === 100" size="small" type="success" style="margin-top:4px">成功</el-tag>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const pageLoading = ref(false)
const listLoading = ref(false)
const creating = ref(false)
const page = ref(1)
const total = ref(6)
const showDetailDialog = ref(false)
const currentTask = ref(null)
const activeChannelCategories = ref(['央媒', '地方媒体', '行业媒体', '自媒体', 'AI平台'])

const taskFormRef = ref(null)

const taskForm = reactive({
  name: '',
  contentId: '',
  channels: []
})

const taskRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  contentId: [{ required: true, message: '请选择内容', trigger: 'change' }],
  channels: [{ required: true, message: '请选择至少一个渠道', trigger: 'change' }]
}

const allChannels = ref([
  { id: 'people', name: '人民网', category: '央媒' },
  { id: 'xinhua', name: '新华网', category: '央媒' },
  { id: 'cctv', name: '央视网', category: '央媒' },
  { id: 'bjnews', name: '北京日报', category: '地方媒体' },
  { id: 'shnews', name: '上海日报', category: '地方媒体' },
  { id: 'gznews', name: '广州日报', category: '地方媒体' },
  { id: '36kr', name: '36氪', category: '行业媒体' },
  { id: 'huxiu', name: '虎嗅', category: '行业媒体' },
  { id: 'tmtpost', name: '钛媒体', category: '行业媒体' },
  { id: 'wechat', name: '微信公众号', category: '自媒体' },
  { id: 'weibo', name: '微博', category: '自媒体' },
  { id: 'zhihu', name: '知乎', category: '自媒体' },
  { id: 'toutiao', name: '今日头条', category: '自媒体' },
  { id: 'bilibili', name: 'B站', category: '自媒体' },
  { id: 'doubao', name: '豆包', category: 'AI平台' },
  { id: 'kimi', name: 'Kimi', category: 'AI平台' }
])

const categorizedChannels = computed(() => {
  const map = {}
  allChannels.value.forEach(ch => {
    if (!map[ch.category]) map[ch.category] = []
    map[ch.category].push(ch)
  })
  return map
})

const contentOptions = ref([
  { id: 1, title: 'AI驱动品牌增长新范式' },
  { id: 2, title: '智能内容创作白皮书' },
  { id: 3, title: 'XX品牌数字化升级案例' },
  { id: 4, title: '医疗AI应用场景分析' }
])

const tasks = ref([
  {
    id: 1,
    name: '产品白皮书分发',
    status: 'active',
    channelProgress: [
      { name: '官网', progress: 100, status: 'success' },
      { name: '知乎', progress: 65, status: 'active' },
      { name: '微信公众号', progress: 30, status: 'active' }
    ]
  }
])

const taskList = ref([
  { id: 1, name: 'Q3品牌内容分发', contentTitle: 'AI驱动品牌增长新范式', channelCount: 5, successCount: 4, failCount: 1, status: 'partial', createdAt: '2024-07-28 14:30' },
  { id: 2, name: '行业洞察报告分发', contentTitle: '行业洞察报告', channelCount: 3, successCount: 3, failCount: 0, status: 'completed', createdAt: '2024-07-20 10:00' },
  { id: 3, name: '品牌故事投放', contentTitle: '品牌故事', channelCount: 2, successCount: 1, failCount: 1, status: 'partial', createdAt: '2024-07-15 16:20' },
  { id: 4, name: '产品白皮书分发', contentTitle: '智能内容创作白皮书', channelCount: 4, successCount: 0, failCount: 0, status: 'active', createdAt: '2024-07-30 09:00' }
])

function createTask() {
  taskFormRef.value?.validate((valid) => {
    if (!valid) return
    creating.value = true
    setTimeout(() => {
      creating.value = false
      const content = contentOptions.value.find(c => c.id === taskForm.contentId)
      const selectedChannels = allChannels.value.filter(c => taskForm.channels.includes(c.id))
      tasks.value.push({
        id: tasks.value.length + 1,
        name: taskForm.name,
        status: 'active',
        channelProgress: selectedChannels.map(ch => ({
          name: ch.name,
          progress: 0,
          status: 'active'
        }))
      })
      taskList.value.unshift({
        id: Date.now(),
        name: taskForm.name,
        contentTitle: content?.title || '',
        channelCount: selectedChannels.length,
        successCount: 0,
        failCount: 0,
        status: 'active',
        createdAt: new Date().toLocaleString()
      })
      total.value = taskList.value.length
      ElMessage.success('分发任务已创建')
      taskForm.name = ''
      taskForm.contentId = ''
      taskForm.channels = []
    }, 1500)
  })
}

function showTaskDetail(task) {
  currentTask.value = task
  showDetailDialog.value = true
}

function retryFailed(row) {
  ElMessage.success(`正在重试 ${row.name} 的失败项`)
}

onMounted(() => {})
</script>

<style scoped>
.distribute { padding: 0; }
.create-card { margin-bottom: 20px; }
.progress-card { margin-bottom: 20px; }
.history-card { margin-bottom: 20px; }
.channel-selector {
  max-height: 300px;
  overflow-y: auto;
}
.selected-count {
  margin-top: 8px;
  font-size: 13px;
  color: #909399;
  text-align: right;
}
.task-item {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}
.task-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}
.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.task-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.channel-progress {
  margin-bottom: 10px;
}
.channel-name {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
  display: block;
}
.empty-text {
  text-align: center;
  color: #c0c4cc;
  padding: 40px 0;
  font-size: 14px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.detail-summary {
  margin-bottom: 16px;
}
</style>
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
              <el-select
                v-model="taskForm.channels"
                multiple
                placeholder="选择目标平台"
                style="width:100%"
              >
                <el-option v-for="ch in channelOptions" :key="ch.id" :label="ch.name" :value="ch.id" />
              </el-select>
              <div class="selected-count">已选 {{ taskForm.channels.length }} 个渠道（每个渠道创建独立分发任务）</div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="large" @click="handleCreateTask" :loading="creating">
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
          <div v-if="activeTasks.length === 0" class="empty-text">暂无进行中的任务</div>
          <div v-for="task in activeTasks" :key="task.id" class="task-item">
            <div class="task-header">
              <span class="task-name">{{ task.taskName || task.name }}</span>
              <el-tag :type="task.status === 2 ? 'success' : task.status === 3 ? 'danger' : 'primary'" size="small">
                {{ taskStatusText(task.status) }}
              </el-tag>
            </div>
            <el-progress
              :percentage="taskProgress(task.status)"
              :status="task.status === 2 ? 'success' : task.status === 3 ? 'exception' : ''"
              :stroke-width="8"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="history-card">
      <template #header>
        <span>任务列表</span>
      </template>
      <el-table :data="taskList" stripe style="width:100%" v-loading="listLoading">
        <el-table-column prop="taskName" label="任务名称" min-width="160" />
        <el-table-column label="内容标题" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ contentMap[row.contentId] || ('内容#' + row.contentId) }}</template>
        </el-table-column>
        <el-table-column prop="targetPlatform" label="目标平台" width="140" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 2 ? 'success' : row.status === 3 ? 'danger' : 'warning'">
              {{ taskStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="showTaskDetail(row)">详情</el-button>
            <el-popconfirm title="确认删除？" @confirm="deleteTaskRow(row)">
              <template #reference>
                <el-button size="small" type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
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
          @current-change="loadList"
        />
      </div>
    </el-card>

    <el-dialog v-model="showDetailDialog" :title="'任务详情 - ' + (currentTask?.taskName || '')" width="600px">
      <div v-if="currentTask">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="任务名称">{{ currentTask.taskName }}</el-descriptions-item>
          <el-descriptions-item label="目标平台">{{ currentTask.targetPlatform }}</el-descriptions-item>
          <el-descriptions-item label="内容">{{ contentMap[currentTask.contentId] || ('内容#' + currentTask.contentId) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ taskStatusText(currentTask.status) }}</el-descriptions-item>
          <el-descriptions-item label="计划时间">{{ currentTask.scheduledTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ currentTask.publishTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结果信息" :span="2">{{ currentTask.resultInfo || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button type="primary" @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { getTaskList, getChannels, createTask, deleteTask, getTaskDetail } from '@/api/distribute'
import { getContentList } from '@/api/content'

const pageLoading = ref(false)
const listLoading = ref(false)
const creating = ref(false)
const page = ref(1)
const total = ref(0)
const showDetailDialog = ref(false)
const currentTask = ref(null)
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

const contentOptions = ref([])
const contentMap = reactive({})
const channelOptions = ref([])
const taskList = ref([])

const TASK_STATUS = { 0: '待处理', 1: '进行中', 2: '已完成', 3: '失败' }
function taskStatusText(status) {
  return TASK_STATUS[status] || '未知'
}
function taskProgress(status) {
  if (status === 2) return 100
  if (status === 3) return 100
  if (status === 1) return 60
  return 10
}

const activeTasks = computed(() => taskList.value.filter((t) => t.status === 0 || t.status === 1))

async function loadContentOptions() {
  try {
    const res = await getContentList({ pageNum: 1, pageSize: 100 })
    const list = res.data || []
    contentOptions.value = list
    list.forEach((c) => { contentMap[c.id] = c.title })
  } catch {
    contentOptions.value = []
  }
}

async function loadChannels() {
  try {
    const res = await getChannels()
    const map = res.data || {}
    channelOptions.value = Object.keys(map).map((key) => ({ id: key, name: map[key] || key }))
  } catch {
    channelOptions.value = []
  }
}

async function loadList() {
  listLoading.value = true
  try {
    const res = await getTaskList({ pageNum: page.value, pageSize: 10 })
    taskList.value = res.data || []
    total.value = res.total || 0
  } catch {
    taskList.value = []
    total.value = 0
  } finally {
    listLoading.value = false
  }
}

function handleCreateTask() {
  taskFormRef.value?.validate((valid) => {
    if (!valid) return
    creating.value = true
    const requests = taskForm.channels.map((platform) => ({
      taskName: taskForm.name,
      contentId: taskForm.contentId,
      targetPlatform: platform
    }))
    let pending = requests.length
    let failed = false
    requests.forEach((req) => {
      createTask(req)
        .then(() => {
          pending -= 1
          if (pending === 0 && !failed) {
            creating.value = false
            ElMessage.success(`已创建 ${requests.length} 个分发任务`)
            taskForm.name = ''
            taskForm.contentId = ''
            taskForm.channels = []
            loadList()
          }
        })
        .catch(() => {
          failed = true
          pending -= 1
          if (pending === 0) creating.value = false
        })
    })
  })
}

function showTaskDetail(row) {
  getTaskDetail(row.id)
    .then((res) => {
      currentTask.value = res.data || row
      showDetailDialog.value = true
    })
    .catch(() => {
      currentTask.value = row
      showDetailDialog.value = true
    })
}

function deleteTaskRow(row) {
  deleteTask(row.id)
    .then(() => {
      ElMessage.success('已删除')
      loadList()
    })
    .catch(() => {})
}

onMounted(() => {
  pageLoading.value = true
  Promise.all([loadContentOptions(), loadChannels(), loadList()])
    .finally(() => { pageLoading.value = false })
})
</script>

<style scoped>
.distribute { padding: 0; }
.create-card { margin-bottom: 20px; }
.progress-card { margin-bottom: 20px; }
.history-card { margin-bottom: 20px; }
.selected-count {
  margin-top: 8px;
  font-size: 13px;
  color: #909399;
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
</style>

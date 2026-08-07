<template>
  <div class="content" v-loading="pageLoading">
    <el-card shadow="hover" class="generate-card">
      <template #header>
        <span>批量生成</span>
      </template>
      <el-form :model="generateForm" label-width="110px" ref="generateFormRef">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="6">
            <el-form-item label="行业模板">
              <el-select v-model="generateForm.template" placeholder="选择行业模板" style="width:100%">
                <el-option label="科技" value="科技" />
                <el-option label="金融" value="金融" />
                <el-option label="医疗" value="医疗" />
                <el-option label="教育" value="教育" />
                <el-option label="制造" value="制造" />
                <el-option label="餐饮" value="餐饮" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <el-form-item label="创作方向">
              <el-input v-model="generateForm.direction" placeholder="输入创作方向" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <el-form-item label="关键词配比">
              <el-input v-model="generateForm.keywords" placeholder="逗号分隔" />
            </el-form-item>
          </el-col>
          <el-col :xs="12" :sm="6" :md="3">
            <el-form-item label="内容类型">
              <el-select v-model="generateForm.contentType" style="width:100%">
                <el-option label="文章" value="article" />
                <el-option label="短视频脚本" value="script" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="12" :sm="6" :md="3">
            <el-form-item label="数量">
              <el-input-number v-model="generateForm.count" :min="1" :max="50" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <el-button type="primary" size="large" @click="handleGenerate" :loading="generating">
            {{ generating ? '生成中...' : '批量生成' }}
          </el-button>
          <el-dropdown v-if="contentList.length > 0" style="margin-left:12px" @command="handleExport">
            <el-button size="large">批量导出<el-icon><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="txt">导出 TXT</el-dropdown-item>
                <el-dropdown-item command="csv">导出 CSV</el-dropdown-item>
                <el-dropdown-item command="zip">导出 ZIP</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-form-item>
      </el-form>
      <el-progress
        v-if="generating"
        :percentage="generateProgress"
        striped
        striped-flow
        :stroke-width="12"
        style="margin-top:8px"
      />
    </el-card>

    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>内容列表</span>
          <div>
            <el-checkbox v-model="selectAll" @change="handleSelectAll" style="margin-right:12px">全选</el-checkbox>
            <span style="font-size:13px;color:#909399">已选 {{ selectedIds.length }} 项</span>
          </div>
        </div>
      </template>
      <el-table
        :data="contentList"
        stripe
        style="width:100%"
        @selection-change="handleSelectionChange"
        v-loading="listLoading"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="contentType" label="类型" width="100">
          <template #default="{ row }">{{ row.contentType === 'article' ? '文章' : '短视频脚本' }}</template>
        </el-table-column>
        <el-table-column prop="brandName" label="品牌/行业" min-width="100" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 2 ? 'success' : row.status === 3 ? 'danger' : 'warning'">
              {{ contentStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="viewContent(row)">查看</el-button>
            <el-button size="small" type="primary" link @click="editContent(row)">编辑</el-button>
            <el-button size="small" type="primary" link @click="exportSingle(row)">导出</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDeleteContent(row)">
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

    <el-dialog v-model="showDetailDialog" title="内容详情" width="700px">
      <template v-if="currentContent">
        <h3 style="margin:0 0 12px">{{ currentContent.title }}</h3>
        <el-tag size="small" style="margin-bottom:12px">{{ currentContent.brandName }}</el-tag>
        <el-divider />
        <div class="content-body">{{ currentContent.content || '暂无正文内容' }}</div>
        <div class="content-keywords" v-if="currentContent.keywords">
          <el-divider>关键词</el-divider>
          <el-tag v-for="kw in String(currentContent.keywords).split(',')" :key="kw" size="small" style="margin-right:6px">{{ kw }}</el-tag>
        </div>
      </template>
      <template #footer>
        <el-button type="primary" @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEditDialog" title="编辑内容" width="700px">
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="内容类型">
          <el-select v-model="editForm.contentType" style="width:100%">
            <el-option label="文章" value="article" />
            <el-option label="短视频脚本" value="script" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="editForm.brandName" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="editForm.keywords" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="editForm.summary" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="editForm.content" type="textarea" :rows="6" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import {
  getContentList,
  getContentDetail,
  deleteContent,
  updateContent,
  batchGenerate,
  batchGenerateScripts
} from '@/api/content'

const pageLoading = ref(false)
const listLoading = ref(false)
const generating = ref(false)
const generateProgress = ref(0)
const page = ref(1)
const total = ref(0)
const showDetailDialog = ref(false)
const showEditDialog = ref(false)
const saving = ref(false)
const selectAll = ref(false)
const selectedIds = ref([])
const generateFormRef = ref(null)

const generateForm = reactive({
  template: '',
  direction: '',
  keywords: '',
  contentType: 'article',
  count: 5
})

const currentContent = ref(null)
const editForm = reactive({
  id: null,
  title: '',
  contentType: 'article',
  brandName: '',
  keywords: '',
  summary: '',
  content: ''
})

const contentList = ref([])

const CONTENT_STATUS = { 0: '待生成', 1: '生成中', 2: '已完成', 3: '失败' }
function contentStatusText(status) {
  return CONTENT_STATUS[status] || '未知'
}

async function loadList() {
  listLoading.value = true
  try {
    const res = await getContentList({ pageNum: page.value, pageSize: 10 })
    contentList.value = res.data || []
    total.value = res.total || 0
  } catch {
    contentList.value = []
    total.value = 0
  } finally {
    listLoading.value = false
  }
}

function handleGenerate() {
  if (!generateForm.template || !generateForm.direction) {
    ElMessage.warning('请选择行业模板并填写创作方向')
    return
  }
  generating.value = true
  generateProgress.value = 10
  const requests = []
  for (let i = 0; i < generateForm.count; i++) {
    requests.push({
      title: `${generateForm.direction}-${i + 1}`,
      contentType: generateForm.contentType,
      brandName: generateForm.template,
      keywords: generateForm.keywords
    })
  }
  const apiCall = generateForm.contentType === 'script'
    ? batchGenerateScripts(requests)
    : batchGenerate(requests)
  apiCall
    .then((res) => {
      generateProgress.value = 100
      const ids = res.data || []
      ElMessage.success(`已提交 ${ids.length || requests.length} 篇内容生成任务`)
      loadList()
    })
    .catch(() => {})
    .finally(() => {
      generating.value = false
    })
}

function handleSelectionChange(selection) {
  selectedIds.value = selection.map((item) => item.id)
}

function handleSelectAll(val) {
  // 由表格 selection 自动处理
}

function handleExport(format) {
  ElMessage.info(`导出 ${selectedIds.value.length} 项内容为 ${format.toUpperCase()}（后端导出接口待接入）`)
}

function exportSingle(row) {
  ElMessage.info(`导出: ${row.title}（后端导出接口待接入）`)
}

async function viewContent(row) {
  try {
    const res = await getContentDetail(row.id)
    currentContent.value = res.data || row
    showDetailDialog.value = true
  } catch {
    currentContent.value = row
    showDetailDialog.value = true
  }
}

function editContent(row) {
  editForm.id = row.id
  editForm.title = row.title || ''
  editForm.contentType = row.contentType || 'article'
  editForm.brandName = row.brandName || ''
  editForm.keywords = row.keywords || ''
  editForm.summary = row.summary || ''
  editForm.content = row.content || ''
  showEditDialog.value = true
}

function saveEdit() {
  if (!editForm.title) {
    ElMessage.warning('标题不能为空')
    return
  }
  saving.value = true
  updateContent({
    id: editForm.id,
    title: editForm.title,
    contentType: editForm.contentType,
    brandName: editForm.brandName,
    keywords: editForm.keywords,
    summary: editForm.summary,
    content: editForm.content
  })
    .then(() => {
      ElMessage.success('内容已更新')
      showEditDialog.value = false
      loadList()
    })
    .catch(() => {})
    .finally(() => {
      saving.value = false
    })
}

function handleDeleteContent(row) {
  deleteContent(row.id)
    .then(() => {
      ElMessage.success('已删除')
      loadList()
    })
    .catch(() => {})
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.content { padding: 0; }
.generate-card { margin-bottom: 20px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.content-body {
  font-size: 14px;
  line-height: 1.8;
  color: #606266;
  white-space: pre-wrap;
}
.content-keywords {
  margin-top: 16px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

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
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">{{ row.type === 'article' ? '文章' : '短视频脚本' }}</template>
        </el-table-column>
        <el-table-column prop="industry" label="行业标签" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : row.status === 'failed' ? 'danger' : 'warning'">
              {{ row.status === 'completed' ? '已完成' : row.status === 'failed' ? '失败' : '生成中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="viewContent(row)">查看</el-button>
            <el-button size="small" type="primary" link @click="editContent(row)">编辑</el-button>
            <el-button size="small" type="primary" link @click="exportSingle(row)">导出</el-button>
            <el-popconfirm title="确认删除？" @confirm="deleteContent(row)">
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
        />
      </div>
    </el-card>

    <el-dialog v-model="showDetailDialog" title="内容详情" width="700px">
      <h3 style="margin:0 0 12px">{{ currentContent.title }}</h3>
      <el-tag size="small" style="margin-bottom:12px">{{ currentContent.industry }}</el-tag>
      <el-divider />
      <div class="content-body">{{ currentContent.body || '暂无正文内容' }}</div>
      <div class="content-keywords" v-if="currentContent.keywords">
        <el-divider>关键词</el-divider>
        <el-tag v-for="kw in currentContent.keywords.split(',')" :key="kw" size="small" style="margin-right:6px">{{ kw }}</el-tag>
      </div>
      <template #footer>
        <el-button type="primary" @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const pageLoading = ref(false)
const listLoading = ref(false)
const generating = ref(false)
const generateProgress = ref(0)
const page = ref(1)
const total = ref(5)
const showDetailDialog = ref(false)
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

const currentContent = reactive({
  title: '',
  industry: '',
  body: '',
  keywords: ''
})

const contentList = ref([
  { id: 1, title: 'AI驱动品牌增长新范式', type: 'article', industry: '科技', status: 'completed', createdAt: '2024-07-28 10:30', body: '本文探讨了AI技术如何驱动品牌在数字化时代实现增长...', keywords: 'AI,品牌增长,数字化' },
  { id: 2, title: '智能内容创作白皮书', type: 'article', industry: '科技', status: 'completed', createdAt: '2024-07-27 14:20', body: '白皮书详细介绍了智能内容创作的方法论...', keywords: '内容创作,AI,白皮书' },
  { id: 3, title: 'XX品牌数字化升级案例', type: 'script', industry: '金融', status: 'generating', createdAt: '2024-07-26 16:00', body: '', keywords: '数字化,案例,金融' },
  { id: 4, title: '医疗AI应用场景分析', type: 'article', industry: '医疗', status: 'failed', createdAt: '2024-07-25 09:00', body: '医疗AI的应用场景分析...', keywords: '医疗,AI,应用场景' },
  { id: 5, title: '教育行业AI解决方案', type: 'article', industry: '教育', status: 'completed', createdAt: '2024-07-24 11:00', body: 'AI技术在教育行业的解决方案...', keywords: '教育,AI,解决方案' }
])

function handleGenerate() {
  if (!generateForm.template || !generateForm.direction) {
    ElMessage.warning('请选择行业模板并填写创作方向')
    return
  }
  generating.value = true
  generateProgress.value = 0
  const timer = setInterval(() => {
    generateProgress.value += Math.floor(Math.random() * 15) + 5
    if (generateProgress.value >= 100) {
      generateProgress.value = 100
      clearInterval(timer)
      setTimeout(() => {
        generating.value = false
        for (let i = 0; i < generateForm.count; i++) {
          contentList.value.unshift({
            id: Date.now() + i,
            title: `${generateForm.direction}内容${i + 1}`,
            type: generateForm.contentType,
            industry: generateForm.template,
            status: 'completed',
            createdAt: new Date().toLocaleString(),
            body: `这是${generateForm.template}行业关于${generateForm.direction}的自动生成内容...`,
            keywords: generateForm.keywords
          })
        }
        total.value = contentList.value.length
        ElMessage.success(`已生成 ${generateForm.count} 篇内容`)
      }, 500)
    }
  }, 300)
}

function handleSelectionChange(selection) {
  selectedIds.value = selection.map(item => item.id)
}

function handleSelectAll(val) {
  // handled by table selection
}

function handleExport(format) {
  ElMessage.success(`正在导出 ${selectedIds.value.length} 项内容为 ${format.toUpperCase()} 格式`)
}

function exportSingle(row) {
  ElMessage.success(`正在导出: ${row.title}`)
}

function viewContent(row) {
  currentContent.title = row.title
  currentContent.industry = row.industry
  currentContent.body = row.body || '暂无正文内容'
  currentContent.keywords = row.keywords || ''
  showDetailDialog.value = true
}

function editContent(row) {
  ElMessage.info('编辑功能开发中: ' + row.title)
}

function deleteContent(row) {
  const idx = contentList.value.findIndex(item => item.id === row.id)
  if (idx > -1) {
    contentList.value.splice(idx, 1)
    total.value = contentList.value.length
    ElMessage.success('已删除')
  }
}

onMounted(() => {})
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
<template>
  <div class="knowledge" v-loading="pageLoading">
    <el-card shadow="hover" class="profile-card">
      <template #header>
        <div class="card-header">
          <span>企业品牌信息</span>
          <el-button size="small" @click="showEditProfile = true">编辑</el-button>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="品牌名称">{{ profile.brandName }}</el-descriptions-item>
        <el-descriptions-item label="所属行业">{{ profile.industry }}</el-descriptions-item>
        <el-descriptions-item label="企业简介">{{ profile.description }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="hover" class="list-card">
      <template #header>
        <div class="card-header">
          <span>知识条目</span>
          <el-button type="primary" size="small" @click="openAddDialog">新增知识</el-button>
        </div>
      </template>
      <el-table :data="knowledgeList" stripe style="width: 100%" v-loading="listLoading">
        <el-table-column prop="type" label="知识类型" width="120" />
        <el-table-column prop="summary" label="原始内容摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="structured" label="结构化状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.structured === '已结构化' ? 'success' : 'info'">{{ row.structured }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本号" width="80" />
        <el-table-column prop="reviewStatus" label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.reviewStatus === '已通过' ? 'success' : row.reviewStatus === '待审核' ? 'warning' : 'danger'">
              {{ row.reviewStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="editKnowledge(row)">编辑</el-button>
            <el-button size="small" type="primary" link @click="viewJsonLd(row)">JSON-LD</el-button>
            <el-button size="small" type="primary" link @click="showVersionHistory(row)">版本历史</el-button>
            <el-button size="small" type="danger" link @click="deleteKnowledge(row)">删除</el-button>
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

    <el-dialog v-model="showDialog" :title="isEdit ? '编辑知识条目' : '新增知识条目'" width="700px">
      <el-form :model="knowledgeForm" label-width="110px" :rules="knowledgeRules" ref="knowledgeFormRef">
        <el-form-item label="知识类型" prop="type">
          <el-select v-model="knowledgeForm.type" style="width:100%">
            <el-option label="产品参数" value="产品参数" />
            <el-option label="案例" value="案例" />
            <el-option label="资质" value="资质" />
            <el-option label="荣誉" value="荣誉" />
            <el-option label="服务体系" value="服务体系" />
          </el-select>
        </el-form-item>
        <el-form-item label="原始内容" prop="content">
          <el-input v-model="knowledgeForm.content" type="textarea" :rows="4" placeholder="请输入原始内容" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="structuring" @click="autoStructure">
            {{ structuring ? '转换中...' : '自动结构化转换' }}
          </el-button>
        </el-form-item>
        <el-form-item label="结构化结果" v-if="knowledgeForm.structuredResult">
          <el-input v-model="knowledgeForm.structuredResult" type="textarea" :rows="3" />
        </el-form-item>
        <el-collapse v-if="knowledgeForm.jsonLd" style="margin-bottom:16px">
          <el-collapse-item title="JSON-LD 预览" name="jsonld">
            <pre class="json-ld">{{ knowledgeForm.jsonLd }}</pre>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="saveKnowledge">{{ isEdit ? '更新' : '保存' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showJsonLdDialog" title="JSON-LD 预览" width="700px">
      <pre class="json-ld">{{ currentJsonLd }}</pre>
      <template #footer>
        <el-button type="primary" @click="showJsonLdDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showVersionDialog" title="版本历史" width="700px">
      <el-table :data="versionList" stripe style="width:100%">
        <el-table-column prop="version" label="版本号" width="80" />
        <el-table-column prop="updatedBy" label="修改人" width="120" />
        <el-table-column prop="updatedAt" label="修改时间" width="180" />
        <el-table-column prop="changeLog" label="变更说明" min-width="200" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="rollback(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showVersionDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEditProfile" title="编辑企业品牌信息" width="600px">
      <el-form :model="profile" label-width="100px">
        <el-form-item label="品牌名称">
          <el-input v-model="profile.brandName" />
        </el-form-item>
        <el-form-item label="所属行业">
          <el-select v-model="profile.industry" style="width:100%">
            <el-option label="科技" value="科技" />
            <el-option label="金融" value="金融" />
            <el-option label="医疗" value="医疗" />
            <el-option label="教育" value="教育" />
            <el-option label="制造" value="制造" />
            <el-option label="餐饮" value="餐饮" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="企业简介">
          <el-input v-model="profile.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditProfile = false">取消</el-button>
        <el-button type="primary" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const pageLoading = ref(false)
const listLoading = ref(false)
const page = ref(1)
const total = ref(6)
const showDialog = ref(false)
const showJsonLdDialog = ref(false)
const showVersionDialog = ref(false)
const showEditProfile = ref(false)
const isEdit = ref(false)
const structuring = ref(false)

const knowledgeFormRef = ref(null)

const profile = reactive({
  brandName: '示例品牌',
  industry: '科技',
  description: '一家专注于AI技术创新的科技企业'
})

const knowledgeForm = reactive({
  type: '',
  content: '',
  structuredResult: '',
  jsonLd: ''
})

const knowledgeRules = {
  type: [{ required: true, message: '请选择知识类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入原始内容', trigger: 'blur' }]
}

const knowledgeList = ref([
  { id: 1, type: '产品参数', summary: '核心产品技术规格参数说明文档', structured: '已结构化', version: 'v2.1', reviewStatus: '已通过', createdAt: '2024-07-28 14:30' },
  { id: 2, type: '案例', summary: '某大型企业数字化转型成功案例', structured: '已结构化', version: 'v1.3', reviewStatus: '已通过', createdAt: '2024-07-25 10:15' },
  { id: 3, type: '资质', summary: 'ISO27001 信息安全管理体系认证', structured: '未结构化', version: 'v1.0', reviewStatus: '待审核', createdAt: '2024-07-20 16:45' },
  { id: 4, type: '荣誉', summary: '2024年度AI创新企业奖', structured: '已结构化', version: 'v1.0', reviewStatus: '已通过', createdAt: '2024-07-18 09:00' },
  { id: 5, type: '服务体系', summary: '售前咨询-实施部署-售后运维全流程', structured: '未结构化', version: 'v1.0', reviewStatus: '未通过', createdAt: '2024-07-15 11:30' }
])

const currentJsonLd = ref('')
const versionList = ref([])

function openAddDialog() {
  isEdit.value = false
  knowledgeForm.type = ''
  knowledgeForm.content = ''
  knowledgeForm.structuredResult = ''
  knowledgeForm.jsonLd = ''
  showDialog.value = true
}

function editKnowledge(row) {
  isEdit.value = true
  knowledgeForm.type = row.type
  knowledgeForm.content = row.summary
  knowledgeForm.structuredResult = ''
  knowledgeForm.jsonLd = ''
  showDialog.value = true
}

function autoStructure() {
  if (!knowledgeForm.content) {
    ElMessage.warning('请先输入原始内容')
    return
  }
  structuring.value = true
  setTimeout(() => {
    structuring.value = false
    knowledgeForm.structuredResult = '结构化完成：提取了关键实体和关系'
    knowledgeForm.jsonLd = JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'Thing',
      name: knowledgeForm.content.substring(0, 20),
      description: knowledgeForm.content
    }, null, 2)
    ElMessage.success('结构化转换完成')
  }, 1500)
}

function saveKnowledge() {
  if (!knowledgeForm.type || !knowledgeForm.content) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (isEdit.value) {
    ElMessage.success('知识条目已更新')
  } else {
    knowledgeList.value.unshift({
      id: Date.now(),
      type: knowledgeForm.type,
      summary: knowledgeForm.content.substring(0, 30) + '...',
      structured: knowledgeForm.structuredResult ? '已结构化' : '未结构化',
      version: 'v1.0',
      reviewStatus: '待审核',
      createdAt: new Date().toLocaleString()
    })
    total.value = knowledgeList.value.length
    ElMessage.success('知识条目已创建')
  }
  showDialog.value = false
}

function viewJsonLd(row) {
  currentJsonLd.value = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'CreativeWork',
    name: row.summary,
    version: row.version,
    dateCreated: row.createdAt
  }, null, 2)
  showJsonLdDialog.value = true
}

function showVersionHistory(row) {
  versionList.value = [
    { version: 'v2.1', updatedBy: '管理员', updatedAt: '2024-07-28 14:30', changeLog: '更新产品参数' },
    { version: 'v2.0', updatedBy: '编辑员', updatedAt: '2024-07-20 10:00', changeLog: '新增技术规格' },
    { version: 'v1.0', updatedBy: '管理员', updatedAt: '2024-07-01 09:00', changeLog: '初始创建' }
  ]
  showVersionDialog.value = true
}

function rollback(row) {
  ElMessageBox.confirm(`确认回滚到 ${row.version} 版本？`, '确认', {
    type: 'warning'
  }).then(() => {
    ElMessage.success(`已回滚到 ${row.version}`)
  }).catch(() => {})
}

function deleteKnowledge(row) {
  ElMessageBox.confirm('确认删除该知识条目？', '确认', {
    type: 'warning'
  }).then(() => {
    const idx = knowledgeList.value.findIndex(item => item.id === row.id)
    if (idx > -1) {
      knowledgeList.value.splice(idx, 1)
      total.value = knowledgeList.value.length
      ElMessage.success('已删除')
    }
  }).catch(() => {})
}

function saveProfile() {
  showEditProfile.value = false
  ElMessage.success('企业信息已更新')
}

onMounted(() => {})
</script>

<style scoped>
.knowledge { padding: 0; }
.profile-card { margin-bottom: 20px; }
.list-card { margin-bottom: 20px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.json-ld {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow-y: auto;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
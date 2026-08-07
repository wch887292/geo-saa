<template>
  <div class="knowledge" v-loading="pageLoading">
    <el-card shadow="hover" class="brand-card">
      <div class="brand-header">
        <span class="brand-title">企业品牌信息</span>
        <div class="brand-actions">
          <el-select
            v-model="currentBrandId"
            placeholder="选择品牌"
            style="width:220px"
            :loading="brandLoading"
            @change="selectBrand"
          >
            <el-option v-for="b in brands" :key="b.id" :label="b.brandName" :value="b.id" />
          </el-select>
          <el-button size="small" @click="openAddBrand">新增品牌</el-button>
          <el-button size="small" @click="showEditProfile = true" :disabled="!currentBrandId">编辑</el-button>
        </div>
      </div>
      <el-descriptions :column="3" border v-if="profile">
        <el-descriptions-item label="品牌名称">{{ profile.brandName }}</el-descriptions-item>
        <el-descriptions-item label="所属行业">{{ profile.industry }}</el-descriptions-item>
        <el-descriptions-item label="企业简介">{{ profile.brandDescription }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="请先创建或选择品牌" :image-size="60" />
    </el-card>

    <el-card shadow="hover" class="list-card">
      <template #header>
        <div class="card-header">
          <span>知识条目</span>
          <el-button type="primary" size="small" @click="openAddDialog" :disabled="!currentBrandId">新增知识</el-button>
        </div>
      </template>
      <el-table :data="knowledgeList" stripe style="width: 100%" v-loading="listLoading">
        <el-table-column prop="knowledgeType" label="知识类型" width="120" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="structured" label="结构化状态" width="110">
          <template #default="{ row }">
            <el-tag :type="isStructured(row) ? 'success' : 'info'">{{ isStructured(row) ? '已结构化' : '未结构化' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'">
              {{ row.status === 1 ? '已通过' : '待审核' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="editKnowledge(row)">编辑</el-button>
            <el-button size="small" type="primary" link @click="viewJsonLd(row)">JSON-LD</el-button>
            <el-button size="small" type="primary" link @click="showVersionHistory(row)">版本历史</el-button>
            <el-button size="small" type="danger" link @click="handleDeleteKnowledge(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap" v-if="total > 10">
        <el-pagination
          v-model:current-page="page"
          :page-size="10"
          :total="total"
          layout="prev, pager, next"
          small
          @current-change="loadKnowledge"
        />
      </div>
    </el-card>

    <el-dialog v-model="showDialog" :title="isEdit ? '编辑知识条目' : '新增知识条目'" width="700px">
      <el-form :model="knowledgeForm" label-width="110px" :rules="knowledgeRules" ref="knowledgeFormRef">
        <el-form-item label="知识类型" prop="knowledgeType">
          <el-select v-model="knowledgeForm.knowledgeType" style="width:100%">
            <el-option label="产品参数" value="产品参数" />
            <el-option label="案例" value="案例" />
            <el-option label="资质" value="资质" />
            <el-option label="荣誉" value="荣誉" />
            <el-option label="服务体系" value="服务体系" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="knowledgeForm.title" placeholder="知识标题" />
        </el-form-item>
        <el-form-item label="原始内容" prop="content">
          <el-input v-model="knowledgeForm.content" type="textarea" :rows="4" placeholder="请输入原始内容" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="structuring" @click="handleAutoStructure">
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
        <el-button type="primary" :loading="saving" @click="saveKnowledge">{{ isEdit ? '更新' : '保存' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showJsonLdDialog" title="JSON-LD 预览" width="700px">
      <pre class="json-ld">{{ currentJsonLd }}</pre>
      <template #footer>
        <el-button type="primary" @click="showJsonLdDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showVersionDialog" title="版本历史" width="700px">
      <el-table :data="versionList" stripe style="width:100%" v-loading="versionLoading">
        <el-table-column prop="version" label="版本号" width="80" />
        <el-table-column prop="updatedBy" label="修改人" width="120" />
        <el-table-column prop="updatedAt" label="修改时间" width="180" />
        <el-table-column prop="changeLog" label="变更说明" min-width="200" />
      </el-table>
      <template #footer>
        <el-button @click="showVersionDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEditProfile" title="编辑企业品牌信息" width="600px">
      <el-form :model="profileForm" label-width="100px">
        <el-form-item label="品牌名称">
          <el-input v-model="profileForm.brandName" />
        </el-form-item>
        <el-form-item label="所属行业">
          <el-select v-model="profileForm.industry" style="width:100%">
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
          <el-input v-model="profileForm.brandDescription" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditProfile = false">取消</el-button>
        <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showBrandDialog" :title="brandIsEdit ? '编辑品牌' : '新增品牌'" width="600px">
      <el-form :model="brandForm" label-width="100px">
        <el-form-item label="品牌名称">
          <el-input v-model="brandForm.brandName" />
        </el-form-item>
        <el-form-item label="所属行业">
          <el-select v-model="brandForm.industry" style="width:100%">
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
          <el-input v-model="brandForm.brandDescription" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBrandDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingBrand" @click="saveBrand">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import {
  getBrandList,
  getBrandDetail,
  createBrand,
  updateBrand,
  getKnowledgeList,
  createKnowledge,
  updateKnowledge,
  deleteKnowledge,
  autoStructure,
  getJsonLd,
  getVersionHistory
} from '@/api/knowledge'

const pageLoading = ref(false)
const brandLoading = ref(false)
const listLoading = ref(false)
const structuring = ref(false)
const saving = ref(false)
const savingProfile = ref(false)
const savingBrand = ref(false)
const versionLoading = ref(false)
const page = ref(1)
const total = ref(0)

const brands = ref([])
const currentBrandId = ref(null)
const profile = ref(null)

const knowledgeFormRef = ref(null)
const showDialog = ref(false)
const showJsonLdDialog = ref(false)
const showVersionDialog = ref(false)
const showEditProfile = ref(false)
const showBrandDialog = ref(false)
const isEdit = ref(false)
const brandIsEdit = ref(false)

const profileForm = reactive({ id: null, brandName: '', industry: '', brandDescription: '' })
const brandForm = reactive({ id: null, brandName: '', industry: '', brandDescription: '' })

const knowledgeForm = reactive({
  id: null,
  brandId: null,
  knowledgeType: '',
  title: '',
  content: '',
  structuredResult: '',
  jsonLd: ''
})

const knowledgeRules = {
  knowledgeType: [{ required: true, message: '请选择知识类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入原始内容', trigger: 'blur' }]
}

const knowledgeList = ref([])
const currentJsonLd = ref('')
const versionList = ref([])

function isStructured(row) {
  return row.content && String(row.content).includes('{')
}

async function loadBrands() {
  brandLoading.value = true
  try {
    const res = await getBrandList({ pageNum: 1, pageSize: 100 })
    brands.value = res.data || []
    if (brands.value.length && !currentBrandId.value) {
      currentBrandId.value = brands.value[0].id
      await selectBrand(currentBrandId.value)
    }
  } catch {
    brands.value = []
  } finally {
    brandLoading.value = false
  }
}

async function selectBrand(id) {
  if (!id) return
  await Promise.all([loadProfile(id), loadKnowledge()])
}

async function loadProfile(id) {
  try {
    const res = await getBrandDetail(id)
    profile.value = res.data
  } catch {
    profile.value = null
  }
}

async function loadKnowledge() {
  if (!currentBrandId.value) return
  listLoading.value = true
  try {
    const res = await getKnowledgeList(currentBrandId.value)
    knowledgeList.value = res.data || []
    total.value = knowledgeList.value.length
  } catch {
    knowledgeList.value = []
    total.value = 0
  } finally {
    listLoading.value = false
  }
}

function openAddDialog() {
  isEdit.value = false
  knowledgeForm.id = null
  knowledgeForm.brandId = currentBrandId.value
  knowledgeForm.knowledgeType = ''
  knowledgeForm.title = ''
  knowledgeForm.content = ''
  knowledgeForm.structuredResult = ''
  knowledgeForm.jsonLd = ''
  showDialog.value = true
}

function editKnowledge(row) {
  isEdit.value = true
  knowledgeForm.id = row.id
  knowledgeForm.brandId = row.brandId
  knowledgeForm.knowledgeType = row.knowledgeType
  knowledgeForm.title = row.title
  knowledgeForm.content = row.content
  knowledgeForm.structuredResult = ''
  knowledgeForm.jsonLd = ''
  showDialog.value = true
}

function handleAutoStructure() {
  if (!knowledgeForm.content) {
    ElMessage.warning('请先输入原始内容')
    return
  }
  structuring.value = true
  autoStructure(currentBrandId.value, knowledgeForm.content)
    .then((res) => {
      knowledgeForm.structuredResult = typeof res.data === 'string' ? res.data : JSON.stringify(res.data)
      ElMessage.success('结构化转换完成')
    })
    .catch(() => {})
    .finally(() => {
      structuring.value = false
    })
}

function saveKnowledge() {
  if (!knowledgeForm.knowledgeType || !knowledgeForm.title || !knowledgeForm.content) {
    ElMessage.warning('请填写完整信息')
    return
  }
  saving.value = true
  const payload = {
    brandId: currentBrandId.value,
    knowledgeType: knowledgeForm.knowledgeType,
    title: knowledgeForm.title,
    content: knowledgeForm.content
  }
  const call = isEdit.value
    ? updateKnowledge({ id: knowledgeForm.id, ...payload })
    : createKnowledge(payload)
  call
    .then(() => {
      ElMessage.success(isEdit.value ? '知识条目已更新' : '知识条目已创建')
      showDialog.value = false
      loadKnowledge()
    })
    .catch(() => {})
    .finally(() => {
      saving.value = false
    })
}

function viewJsonLd() {
  getJsonLd(currentBrandId.value)
    .then((res) => {
      currentJsonLd.value = typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2)
      showJsonLdDialog.value = true
    })
    .catch(() => ElMessage.error('获取 JSON-LD 失败'))
}

function showVersionHistory(row) {
  versionLoading.value = true
  getVersionHistory(row.id)
    .then((res) => {
      versionList.value = (res.data || []).map((v) => ({
        version: 'v' + v.version,
        updatedBy: '系统',
        updatedAt: v.createdAt || '-',
        changeLog: v.changeLog || '-'
      }))
      showVersionDialog.value = true
    })
    .catch(() => ElMessage.error('获取版本历史失败'))
    .finally(() => {
      versionLoading.value = false
    })
}

function handleDeleteKnowledge(row) {
  ElMessageBox.confirm('确认删除该知识条目？', '确认', {
    type: 'warning'
  }).then(() => {
    deleteKnowledge(row.id)
      .then(() => {
        ElMessage.success('已删除')
        loadKnowledge()
      })
      .catch(() => {})
  }).catch(() => {})
}

function saveProfile() {
  if (!profileForm.brandName) {
    ElMessage.warning('请输入品牌名称')
    return
  }
  savingProfile.value = true
  updateBrand({
    id: profileForm.id,
    brandName: profileForm.brandName,
    industry: profileForm.industry,
    brandDescription: profileForm.brandDescription
  })
    .then(() => {
      ElMessage.success('企业信息已更新')
      showEditProfile.value = false
      loadProfile(currentBrandId.value)
    })
    .catch(() => {})
    .finally(() => {
      savingProfile.value = false
    })
}

function openAddBrand() {
  brandIsEdit.value = false
  brandForm.id = null
  brandForm.brandName = ''
  brandForm.industry = ''
  brandForm.brandDescription = ''
  showBrandDialog.value = true
}

function saveBrand() {
  if (!brandForm.brandName) {
    ElMessage.warning('请输入品牌名称')
    return
  }
  savingBrand.value = true
  createBrand({
    brandName: brandForm.brandName,
    industry: brandForm.industry,
    brandDescription: brandForm.brandDescription
  })
    .then(() => {
      ElMessage.success('品牌已创建')
      showBrandDialog.value = false
      loadBrands()
    })
    .catch(() => {})
    .finally(() => {
      savingBrand.value = false
    })
}

onMounted(() => {
  loadBrands()
})
</script>

<style scoped>
.knowledge { padding: 0; }
.brand-card { margin-bottom: 20px; }
.brand-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}
.brand-title { font-size: 16px; font-weight: 600; color: #303133; }
.brand-actions { display: flex; align-items: center; gap: 8px; }
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

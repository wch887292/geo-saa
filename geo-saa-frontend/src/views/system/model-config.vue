<template>
  <div class="ai-model-config">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">大模型设置</span>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增模型</el-button>
        </div>
      </template>

      <el-table :data="modelList" v-loading="loading" stripe>
        <el-table-column prop="modelName" label="模型名称" min-width="140">
          <template #default="{ row }">
            <span class="model-name">{{ row.modelName }}</span>
            <el-tag v-if="row.isDefault === 1" type="success" size="small" effect="plain" class="ml-2">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="provider" label="提供商" width="110">
          <template #default="{ row }">
            <el-tag :type="providerTagType(row.provider)" size="small">{{ providerLabel(row.provider) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modelId" label="模型ID" min-width="140" show-overflow-tooltip />
        <el-table-column prop="apiUrl" label="API地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="temperature" label="温度" width="80" align="center" />
        <el-table-column prop="maxTokens" label="最大Token" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="testConnection(row)">测试</el-button>
            <el-button v-if="row.isDefault !== 1" link type="warning" size="small" @click="setDefault(row)">设默认</el-button>
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑模型' : '新增模型'" width="640px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="如：我的GPT-4o" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="提供商" prop="provider">
          <el-select v-model="form.provider" style="width:100%" @change="onProviderChange">
            <el-option label="OpenAI" value="openai" />
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="通义千问" value="tongyi" />
            <el-option label="豆包" value="doubao" />
            <el-option label="Claude" value="claude" />
            <el-option label="Ollama(本地)" value="ollama" />
            <el-option label="自定义(OpenAI兼容)" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="sk-..." />
          <div class="form-tip" v-if="isEdit && form.apiKey && form.apiKey.includes('****')">保留原密钥，不修改请留空</div>
        </el-form-item>
        <el-form-item label="API URL" prop="apiUrl">
          <el-input v-model="form.apiUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="模型ID" prop="modelId">
          <el-input v-model="form.modelId" placeholder="如：gpt-4o、deepseek-chat、qwen-max" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="温度">
              <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大Token">
              <el-input-number v-model="form.maxTokens" :min="1" :max="128000" :step="512" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="超时(秒)">
              <el-input-number v-model="form.timeoutSeconds" :min="5" :max="300" :step="5" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="form.priority" :min="0" :max="100" :step="1" style="width:100%" />
              <div class="form-tip">数字越大越优先，用于故障降级切换</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="inactive">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getModelList, createModel, updateModel, deleteModel,
  setDefaultModel, testModelConnection
} from '@/api/aiModel'

const loading = ref(false)
const saving = ref(false)
const modelList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const defaultForm = () => ({
  id: null,
  modelName: '',
  provider: 'openai',
  apiKey: '',
  apiUrl: 'https://api.openai.com/v1',
  modelId: 'gpt-4o',
  temperature: 0.7,
  maxTokens: 4096,
  timeoutSeconds: 60,
  priority: 0,
  status: 'active',
  isDefault: 0,
  remark: ''
})

const form = reactive(defaultForm())

const rules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请选择提供商', trigger: 'change' }],
  apiKey: [{ required: true, message: '请输入API Key', trigger: 'blur' }],
  apiUrl: [{ required: true, message: '请输入API URL', trigger: 'blur' }],
  modelId: [{ required: true, message: '请输入模型ID', trigger: 'blur' }]
}

const providerDefaults = {
  openai: { apiUrl: 'https://api.openai.com/v1', modelId: 'gpt-4o' },
  deepseek: { apiUrl: 'https://api.deepseek.com/v1', modelId: 'deepseek-chat' },
  tongyi: { apiUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelId: 'qwen-max' },
  doubao: { apiUrl: 'https://ark.cn-beijing.volces.com/api/v3', modelId: 'doubao-pro-32k' },
  claude: { apiUrl: 'https://api.anthropic.com/v1', modelId: 'claude-3-5-sonnet-20241022' },
  ollama: { apiUrl: 'http://localhost:11434/v1', modelId: 'llama3.1' },
  custom: { apiUrl: '', modelId: '' }
}

function providerLabel(provider) {
  const map = { openai: 'OpenAI', deepseek: 'DeepSeek', tongyi: '通义千问', doubao: '豆包', claude: 'Claude', ollama: 'Ollama', custom: '自定义' }
  return map[provider] || provider
}

function providerTagType(provider) {
  const map = { openai: '', deepseek: 'success', tongyi: 'warning', doubao: 'danger', claude: 'info', ollama: 'info', custom: '' }
  return map[provider] || ''
}

function onProviderChange(provider) {
  const defaults = providerDefaults[provider]
  if (defaults) {
    if (!form.apiUrl || form.apiUrl === providerDefaults[form.provider]?.apiUrl) {
      form.apiUrl = defaults.apiUrl
    }
    if (!form.modelId || form.modelId === providerDefaults[form.provider]?.modelId) {
      form.modelId = defaults.modelId
    }
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getModelList({ pageNum: pageNum.value, pageSize: pageSize.value })
    modelList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  Object.assign(form, defaultForm())
  if (row) {
    isEdit.value = true
    Object.assign(form, row)
  } else {
    isEdit.value = false
  }
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateModel(form)
      ElMessage.success('模型更新成功')
    } else {
      await createModel(form)
      ElMessage.success('模型创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除模型「${row.modelName}」吗？`, '确认删除', { type: 'warning' })
  await deleteModel(row.id)
  ElMessage.success('删除成功')
  fetchList()
}

async function setDefault(row) {
  await setDefaultModel(row.id)
  ElMessage.success('已设为默认模型')
  fetchList()
}

async function testConnection(row) {
  const loading = ElMessage.loading({ message: '正在测试连接...', duration: 0 })
  try {
    const res = await testModelConnection(row.id)
    loading.close()
    if (res.data?.success) {
      ElMessage.success(`连接成功！响应：${res.data.content?.substring(0, 50) || 'OK'}`)
    } else {
      ElMessage.error(`连接失败：${res.data?.errorMessage || '未知错误'}`)
    }
  } catch (e) {
    loading.close()
    ElMessage.error('连接测试失败')
  }
}

onMounted(() => fetchList())
</script>

<style scoped>
.ai-model-config { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.title { font-size: 16px; font-weight: 600; }
.model-name { font-weight: 500; }
.ml-2 { margin-left: 8px; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.form-tip { font-size: 12px; color: #909399; margin-top: 4px; }
</style>

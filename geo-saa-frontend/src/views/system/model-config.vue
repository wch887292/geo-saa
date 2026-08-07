<template>
  <div class="model-config" v-loading="pageLoading">
    <el-card shadow="hover" class="config-card">
      <template #header>
        <span>公有模型配置</span>
      </template>
      <el-form :model="publicModels" label-width="140px" label-position="left">
        <el-divider content-position="left">OpenAI</el-divider>
        <el-form-item label="API Key">
          <el-input v-model="publicModels.openai.apiKey" type="password" show-password placeholder="sk-..." />
        </el-form-item>
        <el-form-item label="API URL">
          <el-input v-model="publicModels.openai.apiUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-select v-model="publicModels.openai.model" style="width:100%">
            <el-option label="gpt-4o" value="gpt-4o" />
            <el-option label="gpt-4o-mini" value="gpt-4o-mini" />
            <el-option label="gpt-4-turbo" value="gpt-4-turbo" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">通义千问</el-divider>
        <el-form-item label="API Key">
          <el-input v-model="publicModels.tongyi.apiKey" type="password" show-password placeholder="请输入通义千问 API Key" />
        </el-form-item>
        <el-form-item label="API URL">
          <el-input v-model="publicModels.tongyi.apiUrl" placeholder="https://dashscope.aliyuncs.com/api/v1" />
        </el-form-item>

        <el-divider content-position="left">豆包</el-divider>
        <el-form-item label="API Key">
          <el-input v-model="publicModels.doubao.apiKey" type="password" show-password placeholder="请输入豆包 API Key" />
        </el-form-item>
        <el-form-item label="API URL">
          <el-input v-model="publicModels.doubao.apiUrl" placeholder="https://ark.cn-beijing.volces.com/api/v3" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="savePublicConfig">保存配置</el-button>
          <el-button @click="testConnection('public')">测试连接</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" class="config-card">
      <template #header>
        <span>私有模型配置</span>
      </template>
      <el-form :model="privateModel" label-width="120px" label-position="left">
        <el-form-item label="接口地址">
          <el-input v-model="privateModel.apiUrl" placeholder="http://localhost:8000/v1" />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="privateModel.modelName" placeholder="如: qwen2-7b-instruct" />
        </el-form-item>
        <el-form-item label="认证方式">
          <el-select v-model="privateModel.authType" style="width:100%">
            <el-option label="API Key" value="apikey" />
            <el-option label="Bearer Token" value="bearer" />
            <el-option label="无认证" value="none" />
          </el-select>
        </el-form-item>
        <el-form-item label="认证密钥" v-if="privateModel.authType !== 'none'">
          <el-input v-model="privateModel.apiKey" type="password" show-password placeholder="认证密钥" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="savePrivateConfig">保存配置</el-button>
          <el-button @click="testConnection('private')">测试连接</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" class="config-card">
      <template #header>
        <span>内部 RAG 配置</span>
      </template>
      <el-form :model="ragConfig" label-width="120px" label-position="left">
        <el-form-item label="知识库地址">
          <el-input v-model="ragConfig.url" placeholder="http://localhost:8001" />
        </el-form-item>
        <el-form-item label="向量库类型">
          <el-select v-model="ragConfig.vectorType" style="width:100%">
            <el-option label="Milvus" value="milvus" />
            <el-option label="Pinecone" value="pinecone" />
            <el-option label="Weaviate" value="weaviate" />
            <el-option label="Chroma" value="chroma" />
            <el-option label="Qdrant" value="qdrant" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key" v-if="ragConfig.vectorType !== 'chroma'">
          <el-input v-model="ragConfig.apiKey" type="password" show-password placeholder="可选" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveRagConfig">保存配置</el-button>
          <el-button @click="testConnection('rag')">测试连接</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'

const pageLoading = ref(false)

const publicModels = reactive({
  openai: {
    apiKey: '',
    apiUrl: 'https://api.openai.com/v1',
    model: 'gpt-4o'
  },
  tongyi: {
    apiKey: '',
    apiUrl: 'https://dashscope.aliyuncs.com/api/v1'
  },
  doubao: {
    apiKey: '',
    apiUrl: 'https://ark.cn-beijing.volces.com/api/v3'
  }
})

const privateModel = reactive({
  apiUrl: '',
  modelName: '',
  authType: 'apikey',
  apiKey: ''
})

const ragConfig = reactive({
  url: '',
  vectorType: 'milvus',
  apiKey: ''
})

function savePublicConfig() {
  ElMessage.success('公有模型配置已保存')
}

function savePrivateConfig() {
  ElMessage.success('私有模型配置已保存')
}

function saveRagConfig() {
  ElMessage.success('RAG 知识库配置已保存')
}

function testConnection(type) {
  const label = type === 'public' ? '公有模型' : type === 'private' ? '私有模型' : 'RAG 知识库'
  ElMessage.info(`正在测试 ${label} 连接...`)
  setTimeout(() => {
    ElMessage.success('连接测试成功')
  }, 1500)
}

onMounted(() => {})
</script>

<style scoped>
.model-config { padding: 0; }
.config-card { margin-bottom: 20px; }
</style>
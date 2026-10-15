<template>
  <div class="login-container">
    <!-- 登录页语言切换（右上角，未登录也可切换） -->
    <div class="login-lang-switch">
      <el-radio-group v-model="localLang" size="small" @change="handleLangChange">
        <el-radio-button value="zh-CN">中文</el-radio-button>
        <el-radio-button value="en">EN</el-radio-button>
      </el-radio-group>
    </div>
    <div class="login-card">
      <div class="login-header">
        <img src="@/assets/logo.svg" alt="logo" class="login-logo" />
        <h2 class="login-title">GEO-SaaS {{ $t('auth.platformDesc') }}</h2>
        <p class="login-desc">{{ $t('auth.welcomeBack') }}</p>
      </div>
      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            :placeholder="$t('auth.username')"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            :placeholder="$t('auth.password')"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? $t('common.loading') : $t('auth.login') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { setLanguage } from '@/i18n'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const { locale, t } = useI18n()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const localLang = computed({
  get: () => locale.value,
  set: (v) => { locale.value = v }
})

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [
    { required: true, message: t('auth.usernameRequired'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: t('auth.passwordRequired'), trigger: 'blur' }
  ]
}

function handleLangChange(lang) {
  setLanguage(lang)
}

async function handleLogin() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await userStore.login({
      username: loginForm.username,
      password: loginForm.password
    })
    ElMessage.success(t('auth.loginSuccess'))
    const redirect = route.query.redirect || '/dashboard'
    router.push(redirect)
  } catch {
    // 失败原因已由 axios 响应拦截器统一提示，避免重复弹窗
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
}

.login-lang-switch {
  position: absolute;
  top: 24px;
  right: 32px;
  z-index: 10;
}

.login-card {
  width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.login-logo {
  width: 56px;
  height: 56px;
  margin-bottom: 12px;
}

.login-title {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
}

.login-desc {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.login-form {
  margin-top: 24px;
}

.login-btn {
  width: 100%;
}
</style>
<template>
  <el-container class="layout-container">
    <el-aside :width="sidebarCollapsed ? '64px' : '220px'" class="layout-aside" :class="{ collapsed: sidebarCollapsed }">
      <div class="logo-container" @click="router.push('/dashboard')">
        <img src="@/assets/logo.svg" alt="logo" class="logo-img" />
        <span v-show="!sidebarCollapsed" class="logo-text">GEO-SaaS</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        :router="true"
        background-color="#001529"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#fff"
        class="side-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>Dashboard</template>
        </el-menu-item>
        <el-menu-item index="/diagnose">
          <el-icon><Search /></el-icon>
          <template #title>诊断中心</template>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Notebook /></el-icon>
          <template #title>知识库</template>
        </el-menu-item>
        <el-menu-item index="/content">
          <el-icon><Edit /></el-icon>
          <template #title>创作中心</template>
        </el-menu-item>
        <el-menu-item index="/asset">
          <el-icon><FolderChecked /></el-icon>
          <template #title>资产存证</template>
        </el-menu-item>
        <el-menu-item index="/distribute">
          <el-icon><Share /></el-icon>
          <template #title>分发中心</template>
        </el-menu-item>
        <el-menu-item index="/monitor">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>数据看板</template>
        </el-menu-item>
        <el-sub-menu index="system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </template>
          <el-menu-item index="/system/permission">
            <el-icon><Lock /></el-icon>
            <template #title>权限管理</template>
          </el-menu-item>
          <el-menu-item index="/system/model-config">
            <el-icon><Cpu /></el-icon>
            <template #title>模型配置</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleSidebar" :size="20">
            <Fold v-if="!sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-dropdown">
              <el-avatar :size="32"><UserFilled /></el-avatar>
              <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const currentTitle = computed(() => route.meta?.title)

const activeMenu = computed(() => {
  const { path } = route
  if (path.startsWith('/system/')) return path
  return path
})

function toggleSidebar() {
  appStore.toggleSidebar()
}

async function handleCommand(command) {
  if (command === 'logout') {
    // 需等待登出接口返回后再跳转，否则 token 被提前清除会导致登出请求失败
    await userStore.logout()
    router.push('/login')
  }
}

onMounted(() => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    userStore.getInfo().catch(() => {})
  }
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
  overflow: hidden;
}
.layout-aside {
  background-color: #001529;
  transition: width 0.3s ease;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.logo-container {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  cursor: pointer;
  flex-shrink: 0;
}
.logo-img {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
}
.logo-text {
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  white-space: nowrap;
}
.side-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
.side-menu:not(.el-menu--collapse) {
  width: 220px;
}
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  height: 60px;
  flex-shrink: 0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.collapse-btn {
  cursor: pointer;
  color: #606266;
}
.header-right {
  display: flex;
  align-items: center;
}
.user-dropdown {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #606266;
}
.username {
  font-size: 14px;
}
.layout-main {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
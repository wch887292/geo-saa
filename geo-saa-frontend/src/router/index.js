import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/views/layout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台首页', icon: 'Odometer' }
      },
      {
        path: 'diagnose',
        name: 'Diagnose',
        component: () => import('@/views/diagnose/index.vue'),
        meta: { title: '诊断中心', icon: 'Search' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/index.vue'),
        meta: { title: '知识库', icon: 'Notebook' }
      },
      {
        path: 'content',
        name: 'Content',
        component: () => import('@/views/content/index.vue'),
        meta: { title: '创作中心', icon: 'Edit' }
      },
      {
        path: 'asset',
        name: 'Asset',
        component: () => import('@/views/asset/index.vue'),
        meta: { title: '资产存证', icon: 'FolderChecked' }
      },
      {
        path: 'distribute',
        name: 'Distribute',
        component: () => import('@/views/distribute/index.vue'),
        meta: { title: '分发中心', icon: 'Share' }
      },
      {
        path: 'monitor',
        name: 'Monitor',
        component: () => import('@/views/monitor/index.vue'),
        meta: { title: '数据看板', icon: 'DataAnalysis' }
      },
      {
        path: 'system/permission',
        name: 'SystemPermission',
        component: () => import('@/views/system/permission.vue'),
        meta: { title: '权限管理', icon: 'Setting' }
      },
      {
        path: 'system/model-config',
        name: 'SystemModelConfig',
        component: () => import('@/views/system/model-config.vue'),
        meta: { title: '模型配置', icon: 'Cpu' }
      }
    ]
  },
  // 兜底路由：避免访问未知路径时出现白屏
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const BASE_TITLE = 'GEO-SaaS 全域AI搜索优化平台'

router.beforeEach((to, from, next) => {
  document.title = to.meta?.title ? `${to.meta.title} - ${BASE_TITLE}` : BASE_TITLE

  const token = localStorage.getItem('geo-saa-token')
  if (to.meta.requiresAuth && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && token) {
    next({ path: '/dashboard' })
  } else {
    next()
  }
})

export default router
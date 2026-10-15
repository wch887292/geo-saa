import { createRouter, createWebHistory } from 'vue-router'
import i18n from '@/i18n'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login.vue'),
    meta: { title: 'auth.login', public: true }
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
        meta: { title: 'menu.dashboard', icon: 'Odometer' }
      },
      {
        path: 'diagnose',
        name: 'Diagnose',
        component: () => import('@/views/diagnose/index.vue'),
        meta: { title: 'menu.diagnose', icon: 'Search' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/index.vue'),
        meta: { title: 'menu.knowledge', icon: 'Notebook' }
      },
      {
        path: 'content',
        name: 'Content',
        component: () => import('@/views/content/index.vue'),
        meta: { title: 'menu.content', icon: 'Edit' }
      },
      {
        path: 'asset',
        name: 'Asset',
        component: () => import('@/views/asset/index.vue'),
        meta: { title: 'menu.asset', icon: 'FolderChecked' }
      },
      {
        path: 'distribute',
        name: 'Distribute',
        component: () => import('@/views/distribute/index.vue'),
        meta: { title: 'menu.distribute', icon: 'Share' }
      },
      {
        path: 'monitor',
        name: 'Monitor',
        component: () => import('@/views/monitor/index.vue'),
        meta: { title: 'menu.monitor', icon: 'DataAnalysis' }
      },
      {
        path: 'geo',
        name: 'Geo',
        component: () => import('@/views/geo/index.vue'),
        meta: { title: 'menu.geo', icon: 'MagicStick' }
      },
      {
        path: 'system/permission',
        name: 'SystemPermission',
        component: () => import('@/views/system/permission.vue'),
        meta: { title: 'menu.permission', icon: 'Setting' }
      },
      {
        path: 'system/model-config',
        name: 'SystemModelConfig',
        component: () => import('@/views/system/model-config.vue'),
        meta: { title: 'menu.modelConfig', icon: 'Cpu' }
      },
      {
        path: 'system/ai-call-log',
        name: 'SystemAiCallLog',
        component: () => import('@/views/system/ai-call-log.vue'),
        meta: { title: 'menu.aiCallLog', icon: 'List' }
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

const BASE_TITLE = 'GEO-SaaS'

router.beforeEach((to, from, next) => {
  // i18n：文档标题根据当前语言动态翻译
  const pageTitle = to.meta?.title ? i18n.global.t(to.meta.title) : ''
  document.title = pageTitle ? `${pageTitle} - ${BASE_TITLE}` : BASE_TITLE

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
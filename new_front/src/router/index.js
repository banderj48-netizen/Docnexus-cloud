/**
 * @description DocAI-main 原始页面路由配置。
 */
import { createRouter, createWebHistory } from 'vue-router'
import { ensureOriginalDemoSession, isOriginalDemoEnabled } from '../mock/originalDocAiDemo'
import { STORAGE_KEYS } from '../constants'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: () => import('../views/auth/LoginView.vue') },
  { path: '/register', component: () => import('../views/auth/RegisterView.vue') },
  { path: '/dashboard', component: () => import('../views/dashboard/IndexView.vue') },
  { path: '/cloud-docs', redirect: '/dashboard' },
  { path: '/ai-skills', redirect: '/editor/chat-mode' },
  { path: '/aiops', component: () => import('../views/aiops/AIOpsView.vue') },
  { path: '/editor/:id', component: () => import('../views/EditorView.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 静态演示模式只绕过登录校验，不改变原始页面路由和页面结构。
router.beforeEach((to, from, next) => {
  if (isOriginalDemoEnabled()) {
    ensureOriginalDemoSession()
    if (to.path === '/' || to.path === '/login' || to.path === '/register') {
      next('/dashboard')
      return
    }
    next()
    return
  }

  const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
  if (to.path !== '/login' && to.path !== '/register' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router

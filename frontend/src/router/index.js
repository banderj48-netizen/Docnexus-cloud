/**
 * 页面路由配置。
 */
import { createRouter, createWebHistory } from 'vue-router'
import { STORAGE_KEYS } from '../constants'
import { isTokenValid } from '../utils/jwt'
import { clearAuthState, ensureAuthenticatedSession } from '../utils/session'

const routes = [
  { path: '/', component: () => import('../views/HomeView.vue') },
  { path: '/workspace', component: () => import('../views/WorkspaceView.vue') },
  { path: '/profile', component: () => import('../views/UserProfileView.vue') },
  { path: '/knowledge', component: () => import('../views/KnowledgeLibraryView.vue') },
  { path: '/study', component: () => import('../views/StudyRoomView.vue') },
  { path: '/ai-logs', component: () => import('../views/AiLogSystemView.vue') },
  { path: '/knowledge-map', redirect: '/knowledge' },
  { path: '/factory', redirect: '/knowledge' },
  { path: '/insights', redirect: '/ai-logs' },
  { path: '/deliverables', redirect: '/ai-logs' },
  { path: '/ai-todos', redirect: '/ai-logs' },
  { path: '/settings', redirect: '/workspace' },
  { path: '/login', component: () => import('../views/auth/LoginView.vue') },
  { path: '/register', component: () => import('../views/auth/RegisterView.vue') },
  { path: '/dashboard', component: () => import('../views/dashboard/IndexView.vue') },
  { path: '/aiops', component: () => import('../views/aiops/AIOpsView.vue') },
  { path: '/editor/:id', component: () => import('../views/EditorView.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const publicPaths = new Set(['/', '/login', '/register'])

router.beforeEach(async (to) => {
  const hasLocalSession = Boolean(
    localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
    || (localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN) && localStorage.getItem(STORAGE_KEYS.SESSION_ID))
  )

  if (
    (to.path === '/' || to.path === '/login' || to.path === '/register')
    && hasLocalSession
    && await ensureAuthenticatedSession({ verifyServer: true, rememberMissing: false })
  ) {
    return to.query.redirect || '/workspace'
  }

  if (publicPaths.has(to.path)) {
    return true
  }

  const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
  if (!isTokenValid(token) && !await ensureAuthenticatedSession({ verifyServer: true })) {
    clearAuthState()
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  if (isTokenValid(token) && !await ensureAuthenticatedSession({ verifyServer: true })) {
    clearAuthState()
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  return true
})

export default router

<template>
  <div class="studio-shell" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <aside class="studio-sidebar">
      <div class="brand-block">
        <div class="brand-logo">X</div>
        <div>
          <strong>文枢智能</strong>
          <span>DocNexus</span>
        </div>
      </div>

      <nav class="side-nav" aria-label="工作台导航">
        <button
          v-for="item in navItems"
          :key="item.path"
          class="side-nav-item"
          :class="{ active: route.path === item.path }"
          type="button"
          @click="router.push(item.path)"
        >
          <component :is="item.icon" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="project-card">
        <span class="project-label">当前项目</span>
        <strong>毕业设计资料库</strong>
        <p>{{ projectSummary }}</p>
        <div class="project-progress">
          <span :style="{ width: projectProgress + '%' }" />
        </div>
      </div>

      <button class="collapse-button" type="button" :title="sidebarCollapsed ? '展开菜单' : '收起菜单'" @click="toggleSidebar">
        <el-icon><component :is="sidebarCollapsed ? Expand : Fold" /></el-icon>
        <span>{{ sidebarCollapsed ? '展开菜单' : '收起菜单' }}</span>
      </button>

      <button class="logout-button" type="button" :disabled="loggingOut" @click="handleLogout">
        <el-icon><SwitchButton /></el-icon>
        <span>{{ loggingOut ? '正在退出' : '退出登录' }}</span>
      </button>
    </aside>

    <main class="studio-main">
      <header class="global-topbar">
        <div class="global-brand">
          <strong class="global-logo">X</strong>
          <div>
            <h1>文枢智能 DocNexus</h1>
            <p>智能文档处理与知识管理平台</p>
          </div>
        </div>
        <div class="global-actions">
          <button class="notice-button" type="button" title="系统通知">
            <el-icon><Bell /></el-icon>
            <span></span>
          </button>
          <button
            class="layout-user-entry"
            type="button"
            title="进入账户中心"
            @click="goUserProfile"
          >
            <div class="layout-user-avatar">{{ userInitial }}</div>
            <strong>{{ username }}</strong>
            <el-icon><ArrowDown /></el-icon>
          </button>
        </div>
      </header>
      <slot />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Bell,
  DocumentChecked,
  Expand,
  Files,
  Fold,
  House,
  Reading,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import { STORAGE_KEYS } from '../constants'
import { logout } from '../utils/auth'
import { userApi } from '../api/user'

const route = useRoute()
const router = useRouter()
const loggingOut = ref(false)
const sidebarCollapsed = ref(localStorage.getItem('docnexusSidebarCollapsed') === '1')
let heartbeatTimer = null

const userInfo = computed(() => {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEYS.USER_INFO) || '{}')
  } catch {
    return {}
  }
})

const username = computed(() => (
  userInfo.value.displayName
  || userInfo.value.username
  || localStorage.getItem('userName')
  || '未登录用户'
))

const userInitial = computed(() => username.value.trim().charAt(0).toUpperCase() || '用')

const goUserProfile = () => {
  router.push('/profile')
}

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem('docnexusSidebarCollapsed', sidebarCollapsed.value ? '1' : '0')
}

const navItems = computed(() => [
  { label: '首页', icon: House, path: '/workspace' },
  { label: '账户中心', icon: User, path: '/profile' },
  { label: '文档库', icon: Files, path: '/knowledge' },
  { label: 'AI 阅读室', icon: Reading, path: '/study' },
  { label: '用户日志', icon: DocumentChecked, path: '/ai-logs' },
])

const projectSummary = computed(() => (
  '进入具体页面后自动从后端读取最新业务数据。'
))

const projectProgress = computed(() => 0)

const sendSessionHeartbeat = () => {
  const sessionId = localStorage.getItem(STORAGE_KEYS.SESSION_ID)
  if (!sessionId) return
  userApi.heartbeatCurrentSession(sessionId).catch(() => {})
}

const handleLogout = async () => {
  loggingOut.value = true
  try {
    await logout()
    ElMessage.success(`${userInfo.value.displayName || userInfo.value.username || '用户'}已退出登录`)
    router.replace('/')
  } finally {
    loggingOut.value = false
  }
}

onMounted(() => {
  sendSessionHeartbeat()
  heartbeatTimer = window.setInterval(sendSessionHeartbeat, 10000)
})

onUnmounted(() => {
  if (heartbeatTimer) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
})
</script>

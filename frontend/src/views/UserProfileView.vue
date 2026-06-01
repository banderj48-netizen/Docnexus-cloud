<template>
  <StudioLayout>
    <section class="account-page">
      <section class="account-hero">
        <div class="hero-identity">
          <div class="profile-avatar">{{ userInitial }}</div>
          <div class="identity-main">
            <span class="identity-label">当前账号</span>
            <strong>{{ profile.username || '当前用户' }}</strong>
            <div class="identity-badges">
              <span class="status-pill" :class="{ disabled: !isAccountEnabled }">
                <img :src="sessionSectionIcon" alt="账号状态图标" />
                账号状态 {{ accountStatusText }}
              </span>
              <span class="role-pill">{{ profile.role || 'USER' }}</span>
              <span class="level-pill">
                <img :src="sessionSectionIcon" alt="安全等级图标" />
                安全等级 高
              </span>
            </div>
            <div class="identity-times">
              <span><Calendar /> 注册时间&nbsp;&nbsp;{{ formatDate(profile.createTimeMillis) }}</span>
              <span><Clock /> 最近登录&nbsp;&nbsp;{{ currentLoginText }}</span>
            </div>
          </div>
        </div>

        <div class="hero-side">
          <div class="contact-panel">
            <h3>联系方式</h3>
            <div class="contact-row">
              <span><Iphone /> 手机号</span>
              <strong>{{ profile.phone || '-' }}</strong>
            </div>
            <div class="contact-row">
              <span><Message /> 邮箱</span>
              <strong>{{ profile.email || '-' }}</strong>
            </div>
            <div class="contact-row">
              <span><Location /> 位置</span>
              <strong>{{ currentLocationText }}</strong>
            </div>
          </div>
        </div>
      </section>

      <main class="account-grid">
        <section class="account-card profile-card">
          <div class="card-title">
            <div class="title-left">
              <img class="section-title-icon" :src="profileSectionIcon" alt="个人信息图标" />
              <div>
                <h2>个人信息</h2>
                <p>查看并维护您填写的用户名、手机号和邮箱。</p>
              </div>
            </div>
            <button class="outline-button" type="button" :disabled="loadingProfile" @click="loadProfile">
              <Refresh />
              <span>刷新</span>
            </button>
          </div>

          <div v-loading="loadingProfile" class="info-list">
            <div class="info-row">
              <span data-label="用户名"><User /></span>
              <strong>{{ profile.username || '-' }}</strong>
            </div>
            <div class="info-row">
              <span data-label="手机号"><Iphone /></span>
              <strong>{{ profile.phone || '-' }}</strong>
            </div>
            <div class="info-row">
              <span data-label="邮箱"><Message /></span>
              <strong>{{ profile.email || '-' }}</strong>
            </div>
            <div class="info-row">
              <span data-label="用户 ID"><Key /></span>
              <strong>{{ profile.userId || currentUserId || '-' }}</strong>
            </div>
          </div>

          <div class="action-row">
            <button class="profile-action edit-action" type="button" @click="openEditDialog">
              <Edit />
              <span>编辑个人信息</span>
            </button>
            <button class="profile-action password-action" type="button" @click="openPasswordDialog">
              <Lock />
              <span>修改密码</span>
            </button>
          </div>
        </section>

        <section class="account-card session-card">
          <div class="card-title">
            <div class="title-left">
              <img class="section-title-icon" :src="sessionSectionIcon" alt="用户会话图标" />
              <div>
                <h2>用户会话管理</h2>
                <p>管理用户登录中的会话。</p>
              </div>
            </div>
            <button class="outline-button" type="button" :disabled="loadingSessions" @click="loadSessions">
              <Refresh />
              <span>刷新</span>
            </button>
          </div>

          <div v-loading="loadingSessions" class="session-table">
            <div class="session-head">
              <span>设备与浏览器</span>
              <span>位置</span>
              <span>登录时间</span>
              <span>最后活跃</span>
              <span>有效期至</span>
              <span>状态</span>
              <span>当前会话</span>
              <span>操作</span>
            </div>
            <div v-if="!sessions.length" class="session-empty">暂无登录会话</div>
            <article v-for="session in sessions" :key="session.sessionId" class="session-row">
              <div class="session-device">
                <span class="browser-icon-wrap">
                  <img
                    class="browser-icon"
                    :class="`browser-${browserType(session)}`"
                    :src="browserIcon(session)"
                    draggable="false"
                    :alt="`${browserName(session)}图标`"
                  />
                </span>
                <div>
                  <strong>{{ deviceName(session) }} · {{ browserName(session) }}</strong>
                  <span>{{ formatUserAgent(session.userAgent) }}</span>
                </div>
              </div>
              <span class="session-cell">{{ resolveIpLocation(session) }}</span>
              <span class="session-cell">{{ formatSessionTime(session.loginAtMillis) }}</span>
              <span class="session-cell">{{ formatSessionTime(session.lastActiveAtMillis) }}</span>
              <span class="session-cell">{{ formatSessionTime(session.refreshExpiresAtMillis) }}</span>
              <span class="session-cell">
                <em class="online-badge" :class="session.online ? 'session-online' : 'session-offline'">
                  {{ session.online ? '在线' : '离线' }}
                </em>
              </span>
              <span class="session-cell">
                <em v-if="session.current" class="current-badge">当前设备</em>
                <em v-else class="empty-badge">-</em>
              </span>
              <span class="session-cell session-actions">
                <button
                  v-if="!session.current"
                  class="session-logout"
                  type="button"
                  :disabled="loggingOutSessionId === session.sessionId"
                  @click="logoutSession(session)"
                >
                  退出会话
                </button>
                <em v-else class="current-action-text">当前会话</em>
              </span>
            </article>
          </div>

          <div class="session-pagination">
            <span class="session-total">共 {{ sessionTotal }} 条会话</span>
            <div class="session-page-controls">
              <button class="page-nav" type="button" :disabled="sessionPageNum <= 1" @click="changeSessionPage(sessionPageNum - 1)">上一页</button>
              <strong class="page-indicator">{{ sessionPageNum }} / {{ sessionPages || 1 }}</strong>
              <button class="page-nav" type="button" :disabled="sessionPageNum >= (sessionPages || 1)" @click="changeSessionPage(sessionPageNum + 1)">下一页</button>
              <div class="page-size-switch" aria-label="每页条数">
                <button
                  v-for="size in sessionPageSizeOptions"
                  :key="size"
                  type="button"
                  :class="{ active: sessionPageSize === size }"
                  @click="changeSessionPageSize(size)"
                >
                  {{ size }} 条/页
                </button>
              </div>
            </div>
          </div>
        </section>
      </main>

      <el-dialog
        v-model="editDialogVisible"
        title="编辑个人信息"
        width="520px"
        destroy-on-close
        append-to-body
        class="account-dialog"
      >
        <div class="dialog-profile-preview">
          <div class="mini-avatar">{{ userInitial }}</div>
          <div>
            <strong>{{ editForm.username || profile.username || '当前用户' }}</strong>
            <span>可修改手机号和邮箱，用户名暂不支持在此处变更。</span>
          </div>
        </div>

        <el-form
          ref="editFormRef"
          :model="editForm"
          :rules="profileRules"
          label-position="top"
          class="dialog-form"
        >
          <el-form-item label="用户名">
            <el-input v-model="editForm.username" disabled>
              <template #prefix><User /></template>
            </el-input>
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model.trim="editForm.phone" placeholder="请输入手机号" maxlength="11">
              <template #prefix><Iphone /></template>
            </el-input>
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model.trim="editForm.email" placeholder="请输入邮箱">
              <template #prefix><Message /></template>
            </el-input>
          </el-form-item>
        </el-form>

        <template #footer>
          <button class="plain-action" type="button" @click="editDialogVisible = false">取消</button>
          <button class="primary-action dialog-submit" type="button" :disabled="savingProfile" @click="submitProfile">
            提交修改
          </button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="passwordDialogVisible"
        title="修改密码"
        width="520px"
        destroy-on-close
        append-to-body
        class="account-dialog"
      >
        <el-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-position="top"
          class="dialog-form"
        >
          <el-form-item label="原始密码" prop="oldPassword">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password">
              <template #prefix><Lock /></template>
            </el-input>
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              show-password
              autocomplete="new-password"
              @input="handleNewPasswordInput"
            >
              <template #prefix><Lock /></template>
            </el-input>
            <div class="password-checklist">
              <div :class="['check-item', passwordStrength.length ? 'met' : '']">
                <el-icon><CircleCheckFilled /></el-icon>
                至少 8 个字符
              </div>
              <div :class="['check-item', passwordStrength.mixed ? 'met' : '']">
                <el-icon><CircleCheckFilled /></el-icon>
                四类字符（大写字母、小写字母、数字、特殊符号）中至少满足两类
              </div>
            </div>
          </el-form-item>
          <el-form-item label="再次输入新密码" prop="confirmPassword">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password">
              <template #prefix><Lock /></template>
            </el-input>
          </el-form-item>
        </el-form>

        <template #footer>
          <button class="plain-action" type="button" @click="passwordDialogVisible = false">取消</button>
          <button class="danger-action dialog-submit" type="button" :disabled="changingPassword" @click="submitPassword">
            确认修改
          </button>
        </template>
      </el-dialog>
    </section>
  </StudioLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Calendar,
  CircleCheckFilled,
  Clock,
  Edit,
  Iphone,
  Key,
  Location,
  Lock,
  Message,
  Refresh,
  User,
} from '@element-plus/icons-vue'
import StudioLayout from '../components/StudioLayout.vue'
import { REGEX, STORAGE_KEYS } from '../constants'
import { userApi } from '../api/user'
import { clearAuthState } from '../utils/auth'
import { getUserIdFromToken } from '../utils/jwt'
import profileSectionIcon from '../assets/account-icons/profile-section.svg'
import sessionSectionIcon from '../assets/account-icons/session-section.svg'
import edgeIcon from '../assets/browser-icons/compact/edge.png'
import browser360Icon from '../assets/browser-icons/compact/browser-360.png'
import chromeIcon from '../assets/browser-icons/compact/chrome.png'
import firefoxIcon from '../assets/browser-icons/compact/firefox.png'
import sogouIcon from '../assets/browser-icons/compact/sogou.png'
import qqIcon from '../assets/browser-icons/compact/qq.png'
import otherBrowserIcon from '../assets/browser-icons/compact/other.png'

const router = useRouter()
const editFormRef = ref(null)
const passwordFormRef = ref(null)
const loadingProfile = ref(false)
const loadingSessions = ref(false)
const savingProfile = ref(false)
const changingPassword = ref(false)
const loggingOutSessionId = ref('')
const editDialogVisible = ref(false)
const passwordDialogVisible = ref(false)
const sessions = ref([])
const sessionPageNum = ref(1)
const sessionPageSize = ref(5)
const sessionPageSizeOptions = [5, 10, 20]
const sessionTotal = ref(0)
const sessionPages = ref(0)

const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
const currentUserId = getUserIdFromToken(token) || localStorage.getItem('userId')
const currentSessionId = localStorage.getItem(STORAGE_KEYS.SESSION_ID) || ''

const profile = reactive({
  userId: '',
  username: '',
  email: '',
  phone: '',
  role: '',
  accountStatus: '',
  accountStatusText: '',
  createTimeMillis: '',
  lastLoginAtMillis: '',
})

const editForm = reactive({
  username: '',
  email: '',
  phone: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const userInitial = computed(() => profile.username.trim().charAt(0).toUpperCase() || '用')
const isAccountEnabled = computed(() => String(profile.accountStatus || '').toUpperCase() !== 'DISABLE')
const accountStatusText = computed(() => profile.accountStatusText || (isAccountEnabled.value ? '正常' : '禁用'))

const currentSession = computed(() => sessions.value.find((session) => session.current) || sessions.value[0] || {})
const currentLoginText = computed(() => formatDateTime(profile.lastLoginAtMillis || currentSession.value.loginAtMillis))
const currentLocationText = computed(() => resolveIpLocation(currentSession.value))
const deviceTimezoneText = computed(() => {
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || '本地时区'
  const offsetMinutes = -new Date().getTimezoneOffset()
  const sign = offsetMinutes >= 0 ? '+' : '-'
  const absoluteMinutes = Math.abs(offsetMinutes)
  const hours = String(Math.floor(absoluteMinutes / 60)).padStart(2, '0')
  const minutes = String(absoluteMinutes % 60).padStart(2, '0')
  return `(UTC${sign}${hours}:${minutes}) ${timezone}`
})

const passwordCategoryCount = computed(() => {
  const password = passwordForm.newPassword || ''
  return [
    REGEX.HAS_UPPER.test(password),
    REGEX.HAS_LOWER.test(password),
    REGEX.HAS_NUMBER.test(password),
    REGEX.HAS_SPECIAL.test(password),
  ].filter(Boolean).length
})

const passwordStrength = computed(() => {
  const password = passwordForm.newPassword || ''
  return {
    length: password.length >= 8,
    mixed: passwordCategoryCount.value >= 2,
  }
})

const profileRules = {
  phone: [
    { required: true, message: '手机号不能为空', trigger: 'blur' },
    { pattern: REGEX.PHONE, message: '手机号格式不正确', trigger: ['blur', 'change'] },
  ],
  email: [
    { required: true, message: '邮箱不能为空', trigger: 'blur' },
    { pattern: REGEX.EMAIL, message: '邮箱格式不正确', trigger: ['blur', 'change'] },
  ],
}

const validateConfirmPassword = (_rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
    return
  }
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

/**
 * 校验修改密码时的新密码强度，规则与页面提示保持一致。
 */
const validateNewPassword = (_rule, value, callback) => {
  if (!value) {
    callback(new Error('新密码不能为空'))
    return
  }
  if (value.length < 8) {
    callback(new Error('新密码至少需要 8 个字符'))
    return
  }
  if (passwordCategoryCount.value < 2) {
    callback(new Error('新密码需在大写字母、小写字母、数字、特殊符号中至少满足两类'))
    return
  }
  callback()
}

/**
 * 新密码输入时实时刷新强度校验，并同步触发确认密码一致性校验。
 */
const handleNewPasswordInput = () => {
  passwordFormRef.value?.validateField('newPassword')
  if (passwordForm.confirmPassword) {
    passwordFormRef.value?.validateField('confirmPassword')
  }
}

const passwordRules = {
  oldPassword: [{ required: true, message: '原始密码不能为空', trigger: 'blur' }],
  newPassword: [{ validator: validateNewPassword, trigger: ['blur', 'change'] }],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: ['blur', 'change'] }],
}

const readLocalUser = () => {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEYS.USER_INFO) || '{}')
  } catch {
    return {}
  }
}

const fillProfile = (data = {}) => {
  const localUser = readLocalUser()
  profile.userId = data.userId || data.id || localUser.userId || localUser.id || currentUserId || ''
  profile.username = data.username || localUser.username || ''
  profile.email = data.email || localUser.email || ''
  profile.phone = data.phone || localUser.phone || ''
  profile.role = data.role || localUser.role || ''
  profile.accountStatus = data.accountStatus || localUser.accountStatus || 'ENABLE'
  profile.accountStatusText = data.accountStatusText || localUser.accountStatusText || ''
  profile.createTimeMillis = data.createTimeMillis || data.createTime || localUser.createTimeMillis || ''
  profile.lastLoginAtMillis = data.lastLoginAtMillis || localUser.lastLoginAtMillis || ''
}

const syncLocalUser = () => {
  const localUser = readLocalUser()
  localStorage.setItem(STORAGE_KEYS.USER_INFO, JSON.stringify({
    ...localUser,
    id: profile.userId,
    userId: profile.userId,
    username: profile.username,
    email: profile.email,
    phone: profile.phone,
    role: profile.role,
    accountStatus: profile.accountStatus,
    accountStatusText: profile.accountStatusText,
    createTimeMillis: profile.createTimeMillis,
    lastLoginAtMillis: profile.lastLoginAtMillis,
  }))
}

const loadProfile = async () => {
  loadingProfile.value = true
  try {
    const response = await userApi.getCurrentProfile()
    fillProfile(response.data || {})
    syncLocalUser()
  } catch {
    fillProfile()
  } finally {
    loadingProfile.value = false
  }
}

const loadSessions = async () => {
  loadingSessions.value = true
  try {
    const response = await userApi.listCurrentSessions({
      currentSessionId,
      pageNum: sessionPageNum.value,
      pageSize: sessionPageSize.value,
    })
    const pageData = response.data || {}
    if (Array.isArray(pageData)) {
      sessions.value = pageData
      sessionTotal.value = pageData.length
      sessionPages.value = pageData.length ? 1 : 0
      return
    }
    sessions.value = Array.isArray(pageData.records) ? pageData.records : []
    sessionTotal.value = Number(pageData.total || 0)
    sessionPageNum.value = Number(pageData.pageNum || sessionPageNum.value || 1)
    sessionPageSize.value = Number(pageData.pageSize || sessionPageSize.value || 5)
    sessionPages.value = Number(pageData.pages || 0)
  } finally {
    loadingSessions.value = false
  }
}

const changeSessionPage = async (pageNum) => {
  const maxPage = sessionPages.value || 1
  sessionPageNum.value = Math.min(Math.max(Number(pageNum) || 1, 1), maxPage)
  await loadSessions()
}

const changeSessionPageSize = async (pageSize) => {
  if (sessionPageSize.value === pageSize) return
  sessionPageSize.value = pageSize
  sessionPageNum.value = 1
  await loadSessions()
}

const formatSessionTime = (millis) => {
  if (!millis) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(millis))
}

const formatDate = (millis) => {
  if (!millis) return '-'
  const date = typeof millis === 'number' ? new Date(millis) : new Date(millis)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date).replaceAll('/', '-')
}

const formatDateTime = (millis) => {
  if (!millis) return '-'
  const date = typeof millis === 'number' ? new Date(millis) : new Date(millis)
  if (Number.isNaN(date.getTime())) return '-'
  const dateText = new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date).replaceAll('/', '-')
  const timeText = new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
  return `${dateText} ${timeText}`
}

const getSessionFingerprint = (session = {}) => `${session.deviceName || ''} ${session.userAgent || ''}`.toLowerCase()

const osName = (session = {}) => {
  const text = getSessionFingerprint(session)
  if (text.includes('windows')) return 'Windows'
  if (text.includes('mac os') || text.includes('macintosh') || text.includes('macbook')) return 'macOS'
  if (text.includes('iphone') || text.includes('ipad') || text.includes('ios')) return 'iOS'
  if (text.includes('android')) return 'Android'
  return '其他'
}

const deviceName = (session = {}) => {
  const device = String(session.deviceName || '').split('·')[0]?.trim()
  if (device && device !== '未知设备') return device
  const os = osName(session)
  if (os === 'Windows') return 'Windows 11'
  if (os === 'macOS') return 'MacBook Pro'
  if (os === 'iOS') return 'iPhone 15 Pro'
  if (os === 'Android') return 'Android Phone'
  return '未知设备'
}

const browserVersion = (session = {}) => {
  const userAgent = String(session.userAgent || '')
  const browser = browserName(session)
  const versionMatch = userAgent.match(/(Edg|Edge|Chrome|Firefox|Safari|Version|QQBrowser|MQQBrowser|MetaSr|360SE|360EE)\/?([\d.]+)?/i)
  const version = versionMatch?.[2]?.split('.').slice(0, 4).join('.') || ''
  return version ? `${browser} ${version}` : browser
}

const maskIp = (ip) => {
  if (!ip) return '未知'
  const parts = String(ip).split('.')
  if (parts.length !== 4) return ip
  return `${parts[0]}.${parts[1]}.***.***`
}

const isPrivateIp = (ip) => {
  const value = String(ip || '').trim()
  return (
    value === '127.0.0.1'
    || value === '::1'
    || value.startsWith('10.')
    || value.startsWith('192.168.')
    || /^172\.(1[6-9]|2\d|3[0-1])\./.test(value)
  )
}

const resolveIpLocation = (session = {}) => {
  return '中国'
}

const browserOptions = [
  {
    type: 'qq',
    name: 'QQ浏览器',
    icon: qqIcon,
    pattern: /(qqbrowser|mqqbrowser|qbcore|tencenttraveler)/i,
  },
  {
    type: 'sogou',
    name: '搜狗浏览器',
    icon: sogouIcon,
    pattern: /(metasr|sogoumobilebrowser|sogouexplorer|sogou| se[ /]\d)/i,
  },
  {
    type: 'browser360',
    name: '360浏览器',
    icon: browser360Icon,
    pattern: /(360se|360ee|360browser|qhbrowser|qihoobrowser|qihoo|360 aphone browser)/i,
  },
  {
    type: 'edge',
    name: 'Microsoft Edge',
    icon: edgeIcon,
    pattern: /(edg|edge|edga|edgios)\//i,
  },
  {
    type: 'firefox',
    name: 'Firefox',
    icon: firefoxIcon,
    pattern: /(firefox|fxios)\//i,
  },
  {
    type: 'chrome',
    name: 'Chrome',
    icon: chromeIcon,
    pattern: /(chrome|crios|chromium)\//i,
  },
]

const resolveBrowser = (session = {}) => {
  const text = `${session.deviceName || ''} ${session.userAgent || ''}`
  return browserOptions.find((option) => option.pattern.test(text)) || {
    type: 'other',
    name: '其他浏览器',
    icon: otherBrowserIcon,
  }
}

const browserIcon = (session) => resolveBrowser(session).icon

const browserName = (session) => resolveBrowser(session).name

const browserType = (session) => resolveBrowser(session).type

const formatUserAgent = (userAgent) => {
  if (!userAgent) return '未知客户端'
  const value = String(userAgent)
  const tokens = value.match(/[A-Za-z][A-Za-z0-9_.-]*\/[\w.\-]+/g) || []
  const ignoreTokens = new Set(['Mozilla'])
  const compactTokens = tokens
    .filter((token) => !ignoreTokens.has(token.split('/')[0]))
    .map((token) => {
      const [name, version = ''] = token.split('/')
      const shortVersion = version.split('.').slice(0, 2).join('.')
      return shortVersion ? `${name}/${shortVersion}` : name
    })
    .filter((token, index, array) => array.indexOf(token) === index)

  if (compactTokens.length) {
    return compactTokens.slice(0, 4).join(' · ')
  }

  return value
    .replace(/^Mozilla\/5\.0\s*/i, '')
    .replace(/\([^)]*\)/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 80) || '未知客户端'
}

const logoutSession = async (session) => {
  loggingOutSessionId.value = session.sessionId
  try {
    await userApi.logoutUserSession(session.sessionId)
    ElMessage.success(session.current ? '当前设备已退出' : '会话已退出')
    if (session.current) {
      clearAuthState()
      router.replace('/login')
      return
    }
    await loadSessions()
  } finally {
    loggingOutSessionId.value = ''
  }
}

const openEditDialog = async () => {
  await loadProfile()
  editForm.username = profile.username
  editForm.phone = profile.phone
  editForm.email = profile.email
  editDialogVisible.value = true
}

const submitProfile = async () => {
  await editFormRef.value?.validate()
  savingProfile.value = true
  try {
    const response = await userApi.updateCurrentProfile(editForm)
    fillProfile(response.data || {
      ...profile,
      phone: editForm.phone,
      email: editForm.email,
    })
    syncLocalUser()
    editDialogVisible.value = false
    ElMessage.success('个人信息已更新')
  } finally {
    savingProfile.value = false
  }
}

const openPasswordDialog = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

const submitPassword = async () => {
  await passwordFormRef.value?.validate()
  changingPassword.value = true
  try {
    const response = await userApi.changeCurrentPassword(passwordForm)
    const data = response.data || {}
    if (data.token) {
      localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, data.token)
      if (data.refreshToken) {
        localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, data.refreshToken)
      }
      if (data.sessionId) {
        localStorage.setItem(STORAGE_KEYS.SESSION_ID, data.sessionId)
      }
      profile.userId = data.userId || profile.userId
      profile.username = data.username || profile.username
      profile.role = data.role || profile.role
      profile.accountStatus = data.accountStatus || profile.accountStatus
      profile.accountStatusText = data.accountStatusText || profile.accountStatusText
      syncLocalUser()
    }
    passwordDialogVisible.value = false
    ElMessage.success('密码已修改')
  } finally {
    changingPassword.value = false
  }
}

onMounted(() => {
  loadProfile()
  loadSessions()
})
</script>

<style scoped>
.account-page {
  width: 100%;
  min-height: 100%;
  min-width: 0;
  overflow-x: hidden;
}

.account-header {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 18px;
}

.header-copy h1 {
  margin: 0;
  color: #172033;
  font-size: 30px;
  line-height: 1.2;
  letter-spacing: 0;
}

.header-copy p {
  margin: 8px 0 0;
  color: #5f6f85;
  font-size: 14px;
}

.back-button,
.icon-button,
.primary-action,
.danger-action,
.plain-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  gap: 8px;
  border: 1px solid #d7dee9;
  border-radius: 8px;
  background: #ffffff;
  color: #172033;
  cursor: pointer;
  font-weight: 800;
}

.back-button {
  flex: 0 0 auto;
  width: auto;
  min-height: 56px;
  padding: 0 14px;
  line-height: 1.2;
  white-space: nowrap;
}

.account-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
  padding: 20px 22px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.06);
}

.hero-identity {
  display: flex;
  align-items: center;
  gap: 18px;
}

.profile-avatar,
.mini-avatar {
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #dff7ec;
  color: #047857;
  font-weight: 900;
}

.profile-avatar {
  width: 72px;
  height: 72px;
  font-size: 30px;
}

.identity-label {
  color: #64748b;
  font-size: 13px;
}

.hero-identity strong {
  display: block;
  margin-top: 4px;
  color: #172033;
  font-size: 26px;
}

.hero-identity p {
  margin: 4px 0 0;
  color: #047857;
  font-weight: 900;
}

.hero-summary {
  display: grid;
  gap: 4px;
  min-width: 260px;
  max-width: 360px;
  padding: 14px 16px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #fbfdff;
}

.hero-summary span {
  color: #64748b;
  font-size: 13px;
}

.hero-summary strong,
.hero-summary small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-summary strong {
  color: #172033;
  font-size: 18px;
}

.hero-summary small {
  color: #64748b;
  font-size: 13px;
}

.account-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.account-card {
  min-width: 0;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.05);
}

.profile-card,
.session-card {
  padding: 24px;
}

.profile-card {
  padding: 20px;
}

.card-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.card-title h2 {
  margin: 0;
  color: #172033;
  font-size: 20px;
  letter-spacing: 0;
}

.card-title p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.icon-button {
  width: 42px;
}

.info-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  min-height: 0;
}

.info-row {
  min-width: 0;
  min-height: 86px;
  padding: 14px 16px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #ffffff;
}

.info-row span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1;
  white-space: nowrap;
}

.info-row strong {
  display: block;
  min-width: 0;
  margin-top: 12px;
  overflow: hidden;
  color: #172033;
  font-size: 17px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
  word-break: break-all;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
}

.profile-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 132px;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #ffffff;
  cursor: pointer;
  font-size: 14px;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.profile-action svg {
  width: 17px;
  height: 17px;
  flex: 0 0 auto;
}

.profile-action:hover {
  transform: translateY(-1px);
}

.edit-action {
  border-color: #a7f3d0;
  background: #ecfdf5;
  color: #047857;
}

.edit-action:hover {
  border-color: #047857;
  background: #dff7ec;
}

.password-action {
  border-color: #fecaca;
  background: #fff1f2;
  color: #b91c1c;
}

.password-action:hover {
  border-color: #b91c1c;
  background: #ffe4e6;
}

.primary-action,
.danger-action,
.plain-action {
  min-width: 136px;
  min-height: 42px;
  padding: 0 16px;
  white-space: nowrap;
}

.primary-action svg,
.danger-action svg,
.plain-action svg {
  width: 20px;
  height: 20px;
  flex: 0 0 auto;
}

.primary-action {
  border-color: #047857;
  background: #047857;
  color: #ffffff;
}

.danger-action {
  border-color: #b91c1c;
  background: #b91c1c;
  color: #ffffff;
}

.plain-action {
  background: #ffffff;
}

.session-list {
  display: grid;
  gap: 10px;
  min-height: 84px;
}

.session-empty {
  display: grid;
  min-height: 76px;
  place-items: center;
  border: 1px dashed #dbe3ee;
  border-radius: 8px;
  color: #64748b;
  font-size: 14px;
}

.session-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.6fr) repeat(4, minmax(112px, 1fr)) minmax(176px, auto);
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #fbfdff;
}

.session-device,
.session-meta {
  min-width: 0;
}

.session-device strong,
.session-device span,
.session-meta span,
.session-meta strong {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-device strong {
  color: #172033;
  font-size: 15px;
}

.session-device span,
.session-meta span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.session-meta strong {
  margin-top: 5px;
  color: #172033;
  font-size: 14px;
}

.online-badge,
.current-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.session-online {
  border: 1px solid #a7f3d0;
  background: #dcfce7;
  color: #065f46;
}

.session-offline {
  border: 1px solid #d7dee9;
  background: #ffffff;
  color: #172033;
}

.current-badge {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.session-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 0;
}

.session-logout {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fff7f7;
  color: #b91c1c;
  font-size: 13px;
  font-weight: 900;
  white-space: nowrap;
}

.session-logout:hover {
  border-color: #b91c1c;
  background: #ffe4e6;
}

.session-logout:disabled {
  cursor: wait;
  opacity: 0.68;
}

.dialog-profile-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  padding: 14px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #fbfdff;
}

.mini-avatar {
  width: 44px;
  height: 44px;
}

.dialog-profile-preview strong,
.dialog-profile-preview span {
  display: block;
}

.dialog-profile-preview span {
  margin-top: 3px;
  color: #64748b;
  font-size: 13px;
}

.dialog-form {
  padding-top: 2px;
}

.password-checklist {
  display: grid;
  gap: 10px;
  margin-top: 12px;
  padding: 14px 16px;
  border-radius: 8px;
  background: #f8fafc;
  color: #8a9ab3;
  font-size: 13px;
  font-weight: 700;
}

.check-item {
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 1.45;
}

.check-item .el-icon {
  flex: 0 0 auto;
  color: #94a3b8;
  font-size: 15px;
}

.check-item.met {
  color: #059669;
}

.check-item.met .el-icon {
  color: #10b981;
}

.dialog-submit {
  min-width: 112px;
}

button:disabled {
  cursor: wait;
  opacity: 0.72;
}

@media (max-width: 1120px) {
  .account-grid {
    grid-template-columns: 1fr;
  }

  .account-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-summary {
    width: 100%;
    max-width: none;
    min-width: 0;
  }
}

@media (max-width: 1180px) {
  .session-row {
    grid-template-columns: minmax(220px, 1fr) repeat(2, minmax(112px, 0.7fr)) minmax(176px, auto);
  }

  .session-meta:nth-of-type(4),
  .session-meta:nth-of-type(5) {
    display: none;
  }
}

@media (max-width: 980px) {
  .info-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .session-row {
    grid-template-columns: 1fr;
    align-items: flex-start;
  }

  .current-badge,
  .session-logout,
  .session-actions {
    width: fit-content;
  }
}

@media (max-width: 760px) {
  .account-header {
    flex-direction: column;
  }

  .info-list {
    grid-template-columns: 1fr;
  }

  .action-row {
    flex-direction: column;
  }

  .primary-action,
  .danger-action {
    width: 100%;
  }
}

/* 账户中心原型化布局：覆盖旧版个人信息页样式。 */
.account-page {
  padding: 0;
  color: #101828;
  max-width: 1680px;
  margin: 0 auto;
}

.account-header {
  display: none;
}

.menu-button {
  display: inline-flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 8px;
  background: transparent;
}

.menu-button span {
  display: block;
  width: 20px;
  height: 2px;
  border-radius: 999px;
  background: #172033;
}

.header-copy h1 {
  font-size: 24px;
  font-weight: 900;
}

.header-copy p {
  margin-top: 8px;
  color: #5b6b82;
  font-size: 14px;
}

.account-hero,
.account-card {
  border: 1px solid #e2eaf3;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
}

.account-hero {
  align-items: stretch;
  gap: clamp(22px, 3vw, 46px);
  margin-bottom: 16px;
  padding: clamp(18px, 2vw, 24px) clamp(22px, 2.6vw, 34px);
}

.hero-identity {
  flex: 1 1 520px;
  min-width: 0;
  gap: clamp(20px, 2vw, 28px);
}

.identity-main {
  min-width: 0;
}

.profile-avatar {
  width: clamp(104px, 8vw, 128px);
  height: clamp(104px, 8vw, 128px);
  background: #dff8ef;
  color: #00856f;
  font-size: clamp(54px, 5vw, 70px);
}

.identity-label {
  display: block;
  margin-top: 6px;
  font-size: 15px;
  color: #475569;
}

.hero-identity strong {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 900;
}

.identity-badges,
.identity-times {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
}

.identity-badges {
  margin-top: 14px;
}

.identity-times {
  margin-top: 18px;
  color: #172033;
  font-size: 14px;
  font-weight: 800;
}

.identity-times span,
.status-pill,
.role-pill,
.level-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.identity-times svg {
  width: 17px;
  height: 17px;
}

.status-pill img,
.level-pill img {
  width: 22px;
  height: 22px;
}

.status-pill,
.level-pill {
  min-height: 38px;
  padding: 0 14px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 900;
}

.status-pill {
  border: 1px solid #a7f3d0;
  background: #ecfdf5;
  color: #047857;
}

.status-pill.disabled {
  border-color: #fecaca;
  background: #fff1f2;
  color: #b91c1c;
}

.level-pill {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.hero-side {
  display: grid;
  grid-template-columns: minmax(300px, 430px) 150px;
  align-items: stretch;
  gap: 16px;
}

.contact-panel {
  min-width: 0;
  padding: 16px 20px;
  border: 1px solid #e2eaf3;
  border-radius: 10px;
}

.contact-panel h3 {
  margin: 0 0 14px;
  color: #172033;
  font-size: 16px;
}

.contact-row {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  align-items: center;
  min-height: 30px;
  gap: 14px;
  color: #475569;
  font-size: 14px;
}

.contact-row span {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.contact-row svg {
  width: 17px;
  height: 17px;
}

.contact-row strong {
  min-width: 0;
  overflow: hidden;
  color: #101828;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-summary {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.session-summary div {
  display: grid;
  align-content: center;
  min-height: 72px;
  padding: 12px 14px;
  border: 1px solid #e2eaf3;
  border-radius: 10px;
  background: #fbfdff;
}

.session-summary span {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.session-summary strong {
  margin-top: 6px;
  color: #00856f;
  font-size: 26px;
  line-height: 1;
}

.account-grid {
  gap: 18px;
}

.profile-card,
.session-card {
  padding: 22px 24px;
}

.card-title {
  align-items: center;
  margin-bottom: 18px;
}

.card-title > div {
  min-width: 0;
  margin-right: auto;
}

.section-title-icon {
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
}

.card-title h2 {
  font-size: 22px;
}

.outline-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  gap: 7px;
  padding: 0 14px;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #ffffff;
  color: #172033;
  font-size: 13px;
  font-weight: 900;
}

.outline-button svg {
  width: 16px;
  height: 16px;
}

.info-list {
  gap: 18px;
}

.info-row {
  min-height: 76px;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: center;
  column-gap: 12px;
  padding: 14px 18px;
  border-color: #e5edf5;
}

.info-row span {
  grid-row: 1 / span 2;
  font-size: 0;
}

.info-row span svg {
  width: 22px;
  height: 22px;
  color: #344256;
}

.info-row span::after {
  content: attr(data-label);
}

.info-row strong {
  margin-top: 3px;
  font-size: 16px;
}

.info-row span {
  position: relative;
}

.info-row span {
  color: transparent;
}

.info-row:nth-child(1)::before,
.info-row:nth-child(2)::before,
.info-row:nth-child(3)::before,
.info-row:nth-child(4)::before {
  grid-column: 2;
  color: #64748b;
  font-size: 13px;
}

.info-row:nth-child(1)::before {
  content: "用户名";
}

.info-row:nth-child(2)::before {
  content: "手机号";
}

.info-row:nth-child(3)::before {
  content: "邮箱";
}

.info-row:nth-child(4)::before {
  content: "用户 ID";
}

.action-row {
  margin-top: 18px;
}

.profile-action {
  min-width: 154px;
}

.session-table {
  overflow: hidden;
  border: 1px solid #e2eaf3;
  border-radius: 8px;
}

.session-head,
.session-row {
  display: grid;
  grid-template-columns: 300px 130px 124px 124px 124px 92px 104px 104px;
  align-items: center;
  min-width: 1102px;
  padding: 0;
}

.session-head {
  min-height: 42px;
  background: #fbfdff;
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.session-head > span {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  height: 100%;
  padding: 0 10px;
  text-align: center;
  white-space: nowrap;
}

.session-head > span:first-child {
  justify-content: flex-start;
  padding-left: 26px;
  text-align: left;
}

.session-row {
  min-height: 62px;
  border-top: 1px solid #e2eaf3;
  background: #ffffff;
}

.session-device {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
  padding: 12px 18px 12px 26px;
}

.session-device > div {
  min-width: 0;
}

.browser-icon-wrap {
  display: grid;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  min-width: 36px;
  margin-top: 0;
  overflow: hidden;
  place-items: center;
  border-radius: 50%;
  border: 0;
  outline: 0;
  background: transparent;
  box-shadow: none;
  clip-path: circle(50% at 50% 50%);
  white-space: normal;
  user-select: none;
  -webkit-user-select: none;
}

.browser-icon {
  display: block;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  outline: 0;
  background: transparent;
  box-shadow: none;
  clip-path: circle(50% at 50% 50%);
  object-fit: contain;
  user-select: none;
  -webkit-user-select: none;
  -webkit-user-drag: none;
}

.session-device strong,
.session-device > div > span {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-device strong {
  color: #172033;
  font-size: 14px;
}

.session-device > div > span {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.session-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  height: 100%;
  padding: 0 10px;
  overflow: hidden;
  color: #172033;
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
}

.online-badge,
.current-badge,
.empty-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 900;
}

.session-online {
  border: 1px solid #bbf7d0;
  background: #dcfce7;
  color: #047857;
}

.session-offline {
  border: 1px solid #dbe4ee;
  background: #ffffff;
  color: #172033;
}

.current-badge {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.empty-badge {
  color: #94a3b8;
}

.session-actions {
  justify-content: center;
}

.session-logout {
  min-height: 34px;
  padding: 0 13px;
  border-color: #fecaca;
  background: #fff7f7;
  color: #dc2626;
}

.account-page-title {
  margin: 0 0 14px;
}

.account-page-title h1 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 900;
}

.account-page-title p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.account-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;
}

.account-main,
.account-insights {
  display: grid;
  min-width: 0;
  gap: 14px;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(420px, 1.4fr) minmax(260px, 1fr) minmax(250px, 0.85fr);
  gap: 12px;
}

.account-overview-card,
.contact-card,
.security-card,
.session-count-card,
.device-stat-card {
  padding: 16px 18px;
}

.account-overview-card h2,
.contact-card h2,
.security-card h2,
.session-count-card h2,
.device-stat-card h2 {
  margin: 0;
  color: #172033;
  font-size: 15px;
  font-weight: 900;
}

.overview-user {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) 138px;
  align-items: center;
  gap: 14px;
  margin-top: 18px;
}

.overview-user .profile-avatar {
  width: 76px;
  height: 76px;
  font-size: 40px;
}

.overview-user-copy {
  min-width: 0;
}

.overview-user-copy strong {
  display: inline-block;
  color: #0f172a;
  font-size: 24px;
  line-height: 1.15;
  font-weight: 900;
}

.overview-user-copy span {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  margin-left: 10px;
  padding: 0 10px;
  border-radius: 999px;
  background: #dcfce7;
  color: #047857;
  font-size: 12px;
  font-weight: 900;
  vertical-align: 4px;
}

.overview-user-copy p {
  margin: 8px 0 0;
  color: #475569;
  font-size: 13px;
}

.overview-status {
  display: grid;
  gap: 7px;
}

.overview-status span {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 34px;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  background: #fbfdff;
  color: #475569;
  font-size: 12px;
  font-weight: 900;
}

.overview-status i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
}

.overview-status img {
  width: 18px;
  height: 18px;
}

.overview-times {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid #e5edf5;
}

.overview-times div {
  display: grid;
  grid-template-columns: 22px minmax(0, max-content) minmax(0, 1fr);
  align-items: center;
  column-gap: 8px;
  row-gap: 3px;
}

.overview-times svg {
  grid-row: 1 / span 2;
  width: 19px;
  height: 19px;
  color: #344256;
}

.overview-times span {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.overview-times strong,
.overview-times small {
  min-width: 0;
  overflow: hidden;
  color: #172033;
  font-size: 13px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overview-times small {
  grid-column: 3;
  color: #64748b;
  font-size: 12px;
}

.contact-line {
  display: grid;
  grid-template-columns: 18px 76px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-height: 30px;
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
}

.contact-line svg {
  width: 16px;
  height: 16px;
}

.contact-line strong {
  min-width: 0;
  overflow: hidden;
  color: #172033;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.soft-action {
  width: 100%;
  min-height: 30px;
  margin-top: 12px;
  border: 1px solid #a7f3d0;
  border-radius: 6px;
  background: #f0fdfa;
  color: #047857;
  font-size: 12px;
  font-weight: 900;
}

.security-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 72px;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}

.security-content > img {
  width: 70px;
  height: 70px;
}

.security-list {
  display: grid;
  gap: 10px;
}

.security-list span {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: #475569;
  font-size: 13px;
  font-weight: 800;
}

.security-list i {
  width: 8px;
  height: 8px;
  margin-right: 4px;
  border-radius: 50%;
  background: #10b981;
}

.security-list strong {
  color: #047857;
  font-size: 12px;
}

.profile-card,
.session-card {
  padding: 16px 18px;
}

.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.title-left {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.section-title-icon {
  width: 32px;
  height: 32px;
}

.card-title h2 {
  font-size: 18px;
  line-height: 1.2;
}

.card-actions,
.session-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.outline-button.green {
  border-color: #a7f3d0;
  background: #ecfdf5;
  color: #047857;
}

.profile-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.profile-tabs button,
.filter-button {
  min-height: 32px;
  padding: 0 16px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #ffffff;
  color: #475569;
  font-size: 12px;
  font-weight: 900;
}

.profile-tabs button.active,
.filter-button.active {
  border-color: #059669;
  background: linear-gradient(180deg, #10b981, #059669);
  color: #ffffff;
}

.profile-matrix {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid #e2eaf3;
  border-radius: 8px;
}

.matrix-item {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  align-content: center;
  min-height: 58px;
  gap: 4px 10px;
  padding: 10px 14px;
  border-right: 1px solid #e2eaf3;
  border-bottom: 1px solid #e2eaf3;
}

.matrix-item:nth-child(4n) {
  border-right: 0;
}

.matrix-item:nth-last-child(-n + 4) {
  border-bottom: 0;
}

.matrix-item svg,
.matrix-item img {
  grid-row: 1 / span 2;
  width: 17px;
  height: 17px;
  color: #344256;
}

.matrix-item span {
  color: #64748b;
  font-size: 12px;
}

.matrix-item strong {
  min-width: 0;
  overflow: hidden;
  color: #172033;
  font-size: 13px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.matrix-item .state-text {
  color: #047857;
}

.session-search {
  display: flex;
  align-items: center;
  width: 270px;
  min-height: 34px;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid #dbe4ee;
  border-radius: 7px;
  background: #ffffff;
  color: #94a3b8;
}

.session-search input {
  min-width: 0;
  width: 100%;
  border: 0;
  outline: 0;
  color: #172033;
  font-size: 12px;
}

.managed-session-table {
  overflow-x: auto;
  border: 1px solid #e2eaf3;
  border-radius: 8px;
}

.managed-session-head,
.managed-session-row {
  display: grid;
  grid-template-columns: 128px 168px 126px 128px 128px 128px 86px 92px 82px;
  align-items: center;
  min-width: 1066px;
}

.managed-session-head {
  min-height: 36px;
  background: #fbfdff;
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
}

.managed-session-head span,
.managed-session-row > span,
.managed-device,
.managed-browser {
  min-width: 0;
  padding: 0 10px;
}

.managed-session-row {
  min-height: 46px;
  border-top: 1px solid #e2eaf3;
  color: #172033;
  font-size: 12px;
  font-weight: 800;
}

.managed-device,
.managed-browser {
  display: flex;
  align-items: center;
  gap: 8px;
}

.managed-device span {
  display: grid;
  width: 18px;
  height: 18px;
  place-items: center;
  border-radius: 5px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 11px;
  font-weight: 900;
}

.managed-browser img {
  width: 17px;
  height: 17px;
  border-radius: 50%;
}

.managed-session-row small {
  color: #64748b;
  font-size: 11px;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 5px;
  border-radius: 50%;
  background: #10b981;
}

.dot.offline {
  background: #94a3b8;
}

.managed-logout {
  border: 0;
  background: #ffffff;
  color: #ef4444;
  font-size: 12px;
  font-weight: 900;
}

.current-session-text {
  color: #94a3b8;
  font-style: normal;
}

.session-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  color: #475569;
  font-size: 13px;
  font-weight: 800;
}

.session-footer div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-footer button,
.session-footer select {
  min-width: 34px;
  min-height: 32px;
  border: 1px solid #dbe4ee;
  border-radius: 7px;
  background: #ffffff;
  color: #475569;
  font-weight: 900;
}

.session-footer button.active {
  border-color: #10b981;
  color: #047857;
}

.session-count-card,
.device-stat-card {
  min-width: 0;
}

.session-count-value {
  margin-top: 18px;
  color: #0f172a;
  font-size: 42px;
  line-height: 1;
  font-weight: 900;
}

.session-count-card p {
  margin: 6px 0 18px;
  color: #64748b;
  font-size: 13px;
}

.session-count-card button {
  padding: 0;
  border: 0;
  background: transparent;
  color: #047857;
  font-size: 13px;
  font-weight: 900;
}

.device-chart-row {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  margin-top: 18px;
}

.donut-chart {
  position: relative;
  display: grid;
  width: 108px;
  height: 108px;
  place-items: center;
  border-radius: 50%;
}

.donut-chart::after {
  position: absolute;
  width: 62px;
  height: 62px;
  border-radius: 50%;
  background: #ffffff;
  content: "";
}

.donut-chart strong,
.donut-chart span {
  position: relative;
  z-index: 1;
}

.donut-chart strong {
  margin-top: 18px;
  color: #0f172a;
  font-size: 24px;
  line-height: 1;
}

.donut-chart span {
  margin-top: -26px;
  color: #64748b;
  font-size: 11px;
  font-weight: 900;
}

.device-legend {
  display: grid;
  gap: 9px;
}

.device-legend span {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.device-legend i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.device-legend strong {
  margin-left: auto;
  color: #475569;
}

@media (max-width: 1280px) {
  .account-layout {
    grid-template-columns: 1fr;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .account-insights {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .account-hero {
    flex-direction: column;
  }

  .hero-side {
    grid-template-columns: minmax(0, 1fr) 160px;
  }

  .session-table {
    overflow-x: auto;
  }

  .session-head,
  .session-row {
    min-width: 1102px;
  }
}

@media (max-width: 900px) {
  .account-insights,
  .profile-matrix {
    grid-template-columns: 1fr;
  }

  .matrix-item,
  .matrix-item:nth-child(4n),
  .matrix-item:nth-last-child(-n + 4) {
    border-right: 0;
    border-bottom: 1px solid #e2eaf3;
  }

  .matrix-item:last-child {
    border-bottom: 0;
  }

  .overview-user,
  .overview-times {
    grid-template-columns: 1fr;
  }

  .overview-status {
    grid-column: 1;
  }

  .hero-side,
  .info-list {
    grid-template-columns: 1fr;
  }

  .session-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .profile-avatar {
    width: 88px;
    height: 88px;
    font-size: 46px;
  }
}

/* 账户中心最终响应式修正：控制大屏宽度和卡片密度，避免宽屏松散、小屏拥挤。 */
.account-page {
  width: min(100%, 1480px);
  max-width: 1480px;
  position: relative;
  padding-inline: clamp(2px, 0.45vw, 8px);
  isolation: isolate;
}

.account-page::before {
  position: fixed;
  top: 62px;
  right: 0;
  bottom: 0;
  left: 184px;
  z-index: -1;
  pointer-events: none;
  background:
    radial-gradient(circle at 83% 3%, rgba(16, 185, 129, 0.18) 0 1px, transparent 1.4px) 0 0 / 16px 16px,
    repeating-linear-gradient(15deg, rgba(20, 184, 166, 0.13) 0 1px, transparent 1px 22px),
    radial-gradient(ellipse at 80% 9%, rgba(16, 185, 129, 0.16), transparent 34%),
    linear-gradient(135deg, rgba(240, 253, 250, 0.98) 0%, rgba(245, 248, 251, 0.94) 42%, rgba(248, 250, 252, 0.98) 100%);
  content: "";
  opacity: 0.9;
}

.account-page-title {
  margin-bottom: 12px;
}

.account-page-title h1 {
  font-size: clamp(22px, 1.7vw, 26px);
}

.account-layout {
  grid-template-columns: minmax(0, 1fr) clamp(250px, 17vw, 280px);
  gap: 10px;
}

.account-main,
.account-insights {
  gap: 12px;
}

.overview-grid {
  grid-template-columns: minmax(390px, 1.36fr) minmax(250px, 0.92fr) minmax(246px, 0.82fr);
  gap: 10px;
}

.account-card {
  border-color: #dfe8f2;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(8px);
}

.account-overview-card,
.contact-card,
.security-card,
.session-count-card,
.device-stat-card {
  min-height: 0;
  padding: 14px 16px;
}

.account-overview-card h2,
.contact-card h2,
.security-card h2,
.session-count-card h2,
.device-stat-card h2 {
  font-size: 14px;
}

.overview-user {
  grid-template-columns: 62px minmax(0, 1fr) 128px;
  gap: 12px;
  margin-top: 12px;
}

.overview-user .profile-avatar {
  width: 62px;
  height: 62px;
  font-size: 34px;
}

.overview-user-copy strong {
  font-size: 22px;
}

.overview-user-copy span {
  min-height: 22px;
  padding-inline: 9px;
  font-size: 11px;
}

.overview-status {
  gap: 6px;
}

.overview-status span {
  min-height: 30px;
  padding-inline: 10px;
  font-size: 11px;
}

.overview-times {
  gap: 12px;
  margin-top: 14px;
  padding-top: 14px;
}

.overview-times div {
  grid-template-columns: 20px 66px minmax(0, 1fr);
  column-gap: 7px;
}

.overview-times span,
.overview-times strong {
  font-size: 12px;
}

.overview-times small {
  max-width: 150px;
  font-size: 11px;
}

.contact-line {
  grid-template-columns: 17px 66px minmax(0, 1fr);
  min-height: 26px;
  margin-top: 7px;
  gap: 8px;
  font-size: 12px;
}

.soft-action {
  min-height: 28px;
  margin-top: 10px;
}

.security-content {
  grid-template-columns: minmax(0, 1fr) 58px;
  margin-top: 12px;
}

.security-content > img {
  width: 58px;
  height: 58px;
}

.security-list {
  gap: 9px;
}

.security-list span,
.security-list strong {
  font-size: 12px;
}

.profile-card,
.session-card {
  padding: 14px 16px;
}

.card-title {
  margin-bottom: 10px;
}

.section-title-icon {
  width: 30px;
  height: 30px;
}

.card-title h2 {
  font-size: 18px;
}

.profile-tabs {
  gap: 6px;
  margin-bottom: 9px;
}

.profile-tabs button,
.filter-button {
  min-height: 30px;
  padding-inline: 14px;
}

.profile-matrix {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.matrix-item {
  min-height: 52px;
  padding: 8px 12px;
  gap: 3px 8px;
}

.matrix-item span {
  font-size: 11px;
}

.matrix-item strong {
  font-size: 12.5px;
}

.session-toolbar {
  gap: 7px;
}

.session-search {
  width: min(270px, 28vw);
  min-height: 32px;
}

.managed-session-head,
.managed-session-row {
  grid-template-columns: 118px 150px 118px 132px 112px 112px 74px 86px 70px;
  min-width: 972px;
}

.managed-session-head {
  min-height: 34px;
}

.managed-session-row {
  min-height: 44px;
}

.managed-session-head span,
.managed-session-row > span,
.managed-device,
.managed-browser {
  padding-inline: 8px;
}

.session-footer {
  margin-top: 8px;
}

.session-count-value {
  margin-top: 12px;
  font-size: 36px;
}

.session-count-card p {
  margin-bottom: 12px;
}

.session-count-card {
  min-height: 148px;
}

.device-stat-card {
  min-height: 190px;
}

.device-chart-row {
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 10px;
  margin-top: 12px;
}

.donut-chart {
  width: 92px;
  height: 92px;
}

.donut-chart::after {
  width: 54px;
  height: 54px;
}

.donut-chart strong {
  font-size: 22px;
}

.device-legend {
  gap: 7px;
}

.device-legend span {
  font-size: 11px;
}

@media (min-width: 1680px) {
  .account-page {
    max-width: 1520px;
  }
}

@media (max-width: 1500px) {
  .account-page {
    max-width: 1320px;
  }

  .account-layout {
    grid-template-columns: minmax(0, 1fr) 260px;
  }

  .overview-grid {
    grid-template-columns: minmax(360px, 1.26fr) minmax(226px, 0.84fr) minmax(226px, 0.8fr);
  }
}

@media (max-width: 1320px) {
  .account-page {
    max-width: 1120px;
  }

  .account-layout {
    grid-template-columns: 1fr;
  }

  .account-insights {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-grid {
    grid-template-columns: minmax(0, 1.15fr) minmax(250px, 0.85fr);
  }

  .security-card {
    grid-column: 1 / -1;
  }

  .security-content {
    grid-template-columns: minmax(0, 1fr) 70px;
  }

  .security-content > img {
    width: 70px;
    height: 70px;
  }

  .session-search {
    width: 260px;
  }
}

@media (max-width: 980px) {
  .account-page {
    max-width: 760px;
  }

  .overview-grid,
  .account-insights {
    grid-template-columns: 1fr;
  }

  .security-card {
    grid-column: auto;
  }

  .overview-user {
    grid-template-columns: 58px minmax(0, 1fr);
  }

  .overview-status {
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-times {
    grid-template-columns: 1fr;
  }

  .profile-matrix {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .matrix-item:nth-child(4n) {
    border-right: 1px solid #e2eaf3;
  }

  .matrix-item:nth-child(2n) {
    border-right: 0;
  }

  .matrix-item:nth-last-child(-n + 4) {
    border-bottom: 1px solid #e2eaf3;
  }

  .matrix-item:nth-last-child(-n + 2) {
    border-bottom: 0;
  }

  .card-title {
    align-items: flex-start;
    flex-direction: column;
  }

  .card-actions,
  .session-toolbar {
    justify-content: flex-start;
    width: 100%;
  }

  .session-search {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .account-page {
    max-width: none;
    padding-inline: 0;
  }

  .profile-matrix {
    grid-template-columns: 1fr;
  }

  .matrix-item,
  .matrix-item:nth-child(2n),
  .matrix-item:nth-child(4n),
  .matrix-item:nth-last-child(-n + 2),
  .matrix-item:nth-last-child(-n + 4) {
    border-right: 0;
    border-bottom: 1px solid #e2eaf3;
  }

  .matrix-item:last-child {
    border-bottom: 0;
  }
}

@media (max-width: 1080px) {
  .account-page::before {
    left: 86px;
  }
}

/* 会话表回退版修正：列宽、图标和当前会话操作统一对齐。 */
.session-card {
  overflow: hidden;
}

.session-table {
  width: 100%;
  max-height: clamp(286px, 32vh, 360px);
  overflow: auto;
  border: 1px solid #e2eaf3;
  border-radius: 8px;
  background: #ffffff;
  scrollbar-color: #b8c7d9 #f5f8fb;
  scrollbar-width: thin;
}

.session-table::-webkit-scrollbar {
  width: 9px;
  height: 9px;
}

.session-table::-webkit-scrollbar-track {
  border-radius: 999px;
  background: #f5f8fb;
}

.session-table::-webkit-scrollbar-thumb {
  border: 2px solid #f5f8fb;
  border-radius: 999px;
  background: #b8c7d9;
}

.session-table::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.session-head,
.session-row {
  display: grid;
  grid-template-columns: minmax(290px, 1.55fr) minmax(118px, 0.72fr) minmax(126px, 0.72fr) minmax(126px, 0.72fr) minmax(126px, 0.72fr) minmax(92px, 0.54fr) minmax(112px, 0.62fr) minmax(124px, 0.72fr);
  align-items: center;
  min-width: 1214px;
}

.session-head {
  position: sticky;
  top: 0;
  z-index: 2;
  min-height: 44px;
  background: #fbfdff;
  color: #64748b;
  font-size: 13px;
  font-weight: 900;
}

.session-head > span,
.session-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  height: 100%;
  padding: 0 12px;
  text-align: center;
  white-space: nowrap;
}

.session-head > span:first-child {
  justify-content: flex-start;
  padding-left: 34px;
  text-align: left;
}

.session-row {
  min-height: 74px;
  border-top: 1px solid #e2eaf3;
  background: #ffffff;
}

.session-device {
  display: flex;
  align-items: center;
  min-width: 0;
  height: 100%;
  gap: 14px;
  padding: 12px 18px 12px 34px;
}

.session-device > div {
  min-width: 0;
}

.browser-icon-wrap {
  position: relative;
  display: grid !important;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  min-width: 36px;
  margin: 0 !important;
  padding: 0;
  overflow: hidden;
  place-items: center;
  border: 0;
  border-radius: 50%;
  outline: 0;
  background: transparent;
  box-sizing: border-box;
  box-shadow: none;
  clip-path: circle(50% at 50% 50%);
  color: inherit;
  font-size: 0;
  line-height: 0;
  text-align: center;
  white-space: normal;
}

.browser-icon-wrap::before {
  display: none;
  content: none;
}

.browser-icon-wrap::selection,
.browser-icon::selection {
  background: transparent;
}

.browser-icon {
  display: block;
  width: 34px;
  height: 34px;
  max-width: none;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 50%;
  outline: 0;
  background: transparent;
  box-shadow: none;
  clip-path: circle(50% at 50% 50%);
  object-fit: contain;
  object-position: center;
  transform: none;
  vertical-align: middle;
  user-select: none;
  -webkit-user-select: none;
  -webkit-user-drag: none;
}

.session-device strong,
.session-device > div > span {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-device strong {
  color: #172033;
  font-size: 15px;
  font-weight: 900;
}

.session-device > div > span {
  max-width: 210px;
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.session-cell {
  overflow: hidden;
  color: #172033;
  font-size: 14px;
  font-weight: 800;
  text-overflow: ellipsis;
}

.online-badge,
.current-badge,
.empty-badge,
.current-action-text {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  min-width: 58px;
  padding: 0 12px;
  border-radius: 7px;
  font-size: 13px;
  font-style: normal;
  font-weight: 900;
  line-height: 1;
}

.session-online {
  border: 1px solid #bbf7d0;
  background: #dcfce7;
  color: #047857;
}

.session-offline {
  border: 1px solid #dbe4ee;
  background: #ffffff;
  color: #172033;
}

.current-badge {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.empty-badge {
  color: #94a3b8;
}

.session-actions {
  gap: 0;
}

.session-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid #e5edf5;
  border-radius: 8px;
  background: #fbfdff;
  color: #64748b;
  font-size: 14px;
  font-weight: 800;
}

.session-total {
  white-space: nowrap;
}

.session-page-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.page-nav,
.page-size-switch button {
  min-height: 34px;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #ffffff;
  color: #172033;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease;
}

.page-nav {
  padding: 0 12px;
}

.page-nav:not(:disabled):hover,
.page-size-switch button:hover {
  border-color: #99f6e4;
  background: #ecfdf5;
  color: #047857;
}

.page-nav:disabled {
  cursor: not-allowed;
  color: #94a3b8;
  opacity: 0.7;
}

.page-indicator {
  min-width: 56px;
  color: #172033;
  text-align: center;
}

.page-size-switch {
  display: inline-flex;
  overflow: hidden;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #ffffff;
}

.page-size-switch button {
  min-width: 76px;
  border: 0;
  border-right: 1px solid #e5edf5;
  border-radius: 0;
}

.page-size-switch button:last-child {
  border-right: 0;
}

.page-size-switch button.active {
  background: #00856f;
  color: #ffffff;
}

.session-logout {
  min-width: 92px;
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fff7f7;
  color: #dc2626;
  font-size: 14px;
  font-weight: 900;
  white-space: nowrap;
}

.current-action-text {
  min-width: 92px;
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #94a3b8;
  font-size: 14px;
  white-space: nowrap;
}

:global(.el-overlay) {
  position: fixed !important;
  inset: 0 !important;
  z-index: 3000 !important;
}

:global(.el-overlay-dialog) {
  z-index: 3001 !important;
}

@media (max-width: 1280px) {
  .session-head,
  .session-row {
    grid-template-columns: minmax(260px, 1.4fr) 112px 116px 116px 116px 88px 104px 112px;
    min-width: 1024px;
  }

  .session-device {
    padding-left: 24px;
  }

  .session-head > span:first-child {
    padding-left: 24px;
  }
}
</style>

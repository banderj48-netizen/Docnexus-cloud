<template>
  <main class="login-page">
    <router-link class="login-brand" to="/">
      <span>DN</span>
      <strong>文枢智能 DocNexus</strong>
    </router-link>

    <section class="login-shell">
      <aside class="login-intro">
        <span class="login-kicker">AI 知识库工作台</span>
        <h1>欢迎回到你的文档库与知识库中心</h1>
        <p>
          登录后可继续管理资料库、发起 RAG 问答、查看 AI 日志，并跟踪每份资料的解析与索引状态。
        </p>
        <div class="intro-checks">
          <div v-for="item in introChecks" :key="item">
            <el-icon><CircleCheckFilled /></el-icon>
            <span>{{ item }}</span>
          </div>
        </div>
      </aside>

      <section class="login-card" aria-label="登录表单">
        <div class="card-heading">
          <span>账户登录</span>
          <h2>进入工作台</h2>
          <p>使用系统管理员或企业账号登录。</p>
        </div>

        <el-form :model="form" label-position="top" @submit.prevent>
          <el-form-item label="用户名">
            <el-input
              v-model.trim="form.username"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>

          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入密码"
              size="large"
              :prefix-icon="Lock"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <div class="form-row">
            <el-checkbox v-model="remember">记住登录状态</el-checkbox>
            <a href="javascript:void(0)" @click="openRecoveryDialog">忘记密码</a>
          </div>

          <el-button
            class="login-submit"
            type="primary"
            size="large"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>

          <el-button class="register-entry" size="large" @click="router.push('/register')">
            注册新账号
          </el-button>

          <div class="login-tip">
            <span>默认演示账号：admin / 123456</span>
            <router-link to="/">返回项目介绍</router-link>
          </div>
        </el-form>
      </section>
    </section>

    <el-dialog
      v-model="recoveryDialogVisible"
      title="找回密码"
      width="420px"
      :close-on-click-modal="false"
    >
      <div v-if="recoveryStep === 'verify'" class="recovery-panel">
        <el-form :model="recoveryForm" label-position="top">
          <el-form-item label="用户名">
            <el-input v-model.trim="recoveryForm.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model.trim="recoveryForm.email" placeholder="请输入注册邮箱" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model.trim="recoveryForm.phone" placeholder="请输入注册手机号" />
          </el-form-item>
        </el-form>
        <el-button type="primary" class="recovery-submit" :loading="recoveryLoading" @click="handleVerifyRecovery">
          验证身份
        </el-button>
      </div>

      <div v-else class="recovery-panel">
        <el-form :model="recoveryForm" label-position="top">
          <el-form-item label="新密码">
            <el-input v-model="recoveryForm.password" type="password" show-password placeholder="请输入新密码" />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input
              v-model="recoveryForm.confirmPassword"
              type="password"
              show-password
              placeholder="请再次输入新密码"
              @keyup.enter="handleResetPassword"
            />
          </el-form-item>
        </el-form>
        <el-button type="primary" class="recovery-submit" :loading="recoveryLoading" @click="handleResetPassword">
          重置密码
        </el-button>
      </div>
    </el-dialog>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled, Lock, User } from '@element-plus/icons-vue'
import { userApi } from '../../api/user'
import { STORAGE_KEYS } from '../../constants'
import { getUserIdFromToken, getUsernameFromToken } from '../../utils/jwt'
import { consumeAuthMessage } from '../../utils/session'

const router = useRouter()
const loading = ref(false)
const remember = ref(true)

const form = reactive({
  username: '',
  password: '',
})

const recoveryDialogVisible = ref(false)
const recoveryLoading = ref(false)
const recoveryStep = ref('verify')
const recoveryForm = reactive({
  username: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
  resetToken: '',
})

const introChecks = ['资料解析与知识库沉淀', '基于引用片段的 AI 阅读对话', '检索日志与知识库评估']

onMounted(() => {
  const message = consumeAuthMessage()
  if (message) {
    ElMessage.warning(message)
  }
})

const openRecoveryDialog = () => {
  recoveryDialogVisible.value = true
  recoveryStep.value = 'verify'
  recoveryForm.username = form.username || ''
  recoveryForm.email = ''
  recoveryForm.phone = ''
  recoveryForm.password = ''
  recoveryForm.confirmPassword = ''
  recoveryForm.resetToken = ''
}

const handleVerifyRecovery = async () => {
  if (!recoveryForm.username || !recoveryForm.email || !recoveryForm.phone) {
    ElMessage.warning('请填写用户名、邮箱和手机号')
    return
  }

  recoveryLoading.value = true
  try {
    const res = await userApi.verifyPasswordRecovery({
      username: recoveryForm.username,
      email: recoveryForm.email,
      phone: recoveryForm.phone,
    })
    const data = res.data || {}
    if (!data.allowed || !data.resetToken) {
      ElMessage.error('身份验证失败')
      return
    }
    recoveryForm.resetToken = data.resetToken
    recoveryStep.value = 'reset'
    ElMessage.success('身份验证通过，请设置新密码')
  } finally {
    recoveryLoading.value = false
  }
}

const handleResetPassword = async () => {
  if (!recoveryForm.password || !recoveryForm.confirmPassword) {
    ElMessage.warning('请填写新密码和确认密码')
    return
  }
  if (recoveryForm.password !== recoveryForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  recoveryLoading.value = true
  try {
    await userApi.resetPassword({
      username: recoveryForm.username,
      resetToken: recoveryForm.resetToken,
      password: recoveryForm.password,
      confirmPassword: recoveryForm.confirmPassword,
    })
    ElMessage.success('密码重置成功，请使用新密码登录')
    recoveryDialogVisible.value = false
    form.username = recoveryForm.username
    form.password = ''
  } finally {
    recoveryLoading.value = false
  }
}

const handleLogin = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }

  loading.value = true
  try {
    const res = await userApi.login({
      username: form.username,
      password: form.password,
    })

    const data = res.data || {}
    if (data.token) {
      localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, data.token)
      if (data.refreshToken) {
        localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, data.refreshToken)
      }
      if (data.sessionId) {
        localStorage.setItem(STORAGE_KEYS.SESSION_ID, data.sessionId)
      }
      const userId = data.userId || data.id || getUserIdFromToken(data.token)
      const username = data.username || getUsernameFromToken(data.token) || form.username
      const role = data.role || 'USER'
      if (userId) {
        localStorage.setItem('userId', userId)
        localStorage.setItem(STORAGE_KEYS.USER_INFO, JSON.stringify({
          id: userId,
          userId,
          username,
          role,
        }))
      }
    } else {
      localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
      localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN)
      localStorage.removeItem(STORAGE_KEYS.SESSION_ID)
      localStorage.removeItem('userId')
      localStorage.removeItem(STORAGE_KEYS.USER_INFO)
    }

    if (data.sessionTakeover) {
      ElMessage.warning(data.takeoverMessage || '在该设备上已存在会话，已接管原会话')
    } else {
      ElMessage.success('登录成功，正在进入工作台')
    }
    router.push(router.currentRoute.value.query.redirect || '/workspace')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  height: 100%;
  min-height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  background:
    linear-gradient(135deg, rgba(4, 120, 87, 0.13), transparent 32%),
    linear-gradient(315deg, rgba(14, 165, 233, 0.15), transparent 34%),
    #edf1f5;
  color: #172033;
}

.login-brand {
  position: fixed;
  top: 28px;
  left: 36px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: #172033;
}

.login-brand span {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 8px;
  background: linear-gradient(135deg, #047857, #0ea5e9);
  color: #ffffff;
  font-weight: 900;
}

.login-shell {
  display: grid;
  grid-template-columns: minmax(360px, 0.95fr) minmax(380px, 460px);
  align-items: center;
  gap: 48px;
  width: min(1180px, calc(100% - 64px));
  min-height: 100vh;
  margin: 0 auto;
  padding: 100px 0 56px;
}

.login-intro {
  max-width: 640px;
}

.login-kicker,
.card-heading span {
  display: inline-flex;
  width: fit-content;
  border-radius: 999px;
  background: #dff7ec;
  color: #047857;
  padding: 6px 11px;
  font-size: 12px;
  font-weight: 900;
}

.login-intro h1 {
  margin-top: 18px;
  font-size: 46px;
  line-height: 1.14;
  letter-spacing: 0;
}

.login-intro p {
  margin-top: 18px;
  color: #53657d;
  font-size: 16px;
  line-height: 1.82;
}

.intro-checks {
  display: grid;
  gap: 12px;
  margin-top: 30px;
}

.intro-checks div {
  display: flex;
  align-items: center;
  gap: 10px;
  width: fit-content;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
  padding: 12px 14px;
  color: #334155;
}

.intro-checks .el-icon {
  color: #047857;
}

.login-card {
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.95);
  padding: 34px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.14);
}

.card-heading {
  margin-bottom: 26px;
}

.card-heading h2 {
  margin-top: 12px;
  font-size: 30px;
}

.card-heading p {
  margin-top: 8px;
  color: #64748b;
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -4px 0 18px;
  font-size: 13px;
}

.form-row a {
  color: #047857;
  font-weight: 800;
}

.login-submit {
  width: 100%;
  min-height: 44px;
  border: 0;
  border-radius: 8px;
  background: #047857;
  font-weight: 900;
}

.register-entry {
  width: 100%;
  min-height: 44px;
  margin-left: 0;
  margin-top: 12px;
  border-radius: 8px;
  border-color: #a7f3d0;
  color: #047857;
  font-weight: 900;
}

.login-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
  color: #64748b;
  font-size: 13px;
}

.login-tip a {
  color: #047857;
  font-weight: 800;
  white-space: nowrap;
}

.recovery-panel {
  display: grid;
  gap: 12px;
}

.recovery-submit {
  width: 100%;
  min-height: 42px;
  border-radius: 8px;
  font-weight: 900;
}

:deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #d7dee9 inset;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #047857 inset;
}

@media (max-width: 920px) {
  .login-brand {
    position: static;
    margin: 22px 24px 0;
  }

  .login-shell {
    grid-template-columns: 1fr;
    width: min(640px, calc(100% - 32px));
    min-height: auto;
    padding-top: 38px;
  }

  .login-intro h1 {
    font-size: 34px;
  }
}

@media (max-width: 560px) {
  .login-shell {
    width: calc(100% - 24px);
    gap: 24px;
    padding-bottom: 28px;
  }

  .login-card {
    padding: 24px;
  }

  .login-tip,
  .form-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

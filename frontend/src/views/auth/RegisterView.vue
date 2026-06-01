<template>
  <main class="register-page">
    <router-link class="auth-brand" to="/">
      <span>DN</span>
      <strong>文枢智能 DocNexus</strong>
    </router-link>

    <section class="register-shell">
      <aside class="register-intro">
        <span class="auth-kicker">账户注册</span>
        <h1>创建你的知识库工作台账号</h1>
        <p>注册后可以上传资料、管理文档库，并为后续 RAG 学习、知识检索和引用溯源建立个人资料空间。</p>
        <div class="intro-checks">
          <div v-for="item in introChecks" :key="item">
            <el-icon><CircleCheckFilled /></el-icon>
            <span>{{ item }}</span>
          </div>
        </div>
      </aside>

      <section class="register-card" aria-label="注册表单">
        <div class="card-heading">
          <span>创建账号</span>
          <h2>注册 DocNexus</h2>
          <p>密码会先在浏览器中加密，后端只保存哈希结果。</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model.trim="form.username"
              class="register-field-username"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
              clearable
              @input="handleFieldInput('username')"
              @keydown.down.prevent="focusRegisterField('next')"
              @keydown.up.prevent="focusRegisterField('prev')"
            />
          </el-form-item>

          <el-form-item label="邮箱" prop="email">
            <div class="email-field">
              <el-input
                v-model.trim="form.email"
                class="register-field-email"
                placeholder="例如：name@example.com"
                size="large"
                :prefix-icon="Message"
                clearable
                @focus="emailFocused = true"
                @input="handleEmailInput"
                @keydown.down.prevent="handleEmailArrow(1)"
                @keydown.up.prevent="handleEmailArrow(-1)"
                @keydown.enter.prevent="confirmEmailSuggestion"
                @keydown.esc="emailFocused = false"
                @blur="handleEmailBlur"
              />
              <div v-if="showEmailSuggestions" class="email-suggestions">
                <button
                  v-for="(item, index) in emailSuggestions"
                  :key="item"
                  :class="{ active: index === emailSuggestionIndex }"
                  type="button"
                  @mouseenter="emailSuggestionIndex = index"
                  @mousedown.prevent="selectEmailSuggestion(item)"
                >
                  {{ item }}
                </button>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="手机号" prop="phone">
            <el-input
              v-model.trim="form.phone"
              class="register-field-phone"
              placeholder="请输入 11 位手机号"
              size="large"
              :prefix-icon="Iphone"
              clearable
              @input="handleFieldInput('phone')"
              @keydown.down.prevent="focusRegisterField('next')"
              @keydown.up.prevent="focusRegisterField('prev')"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              class="register-field-password"
              type="password"
              show-password
              placeholder="至少 8 位，四类字符中至少满足两类"
              size="large"
              :prefix-icon="Lock"
              @input="handlePasswordInput"
              @keydown.down.prevent="focusRegisterField('next')"
              @keydown.up.prevent="focusRegisterField('prev')"
            />
            <div v-if="showPasswordTips" class="password-checklist">
              <div :class="['check-item', strength.length ? 'met' : '']">
                <el-icon><CircleCheckFilled /></el-icon>
                至少 8 个字符
              </div>
              <div :class="['check-item', strength.mixed ? 'met' : '']">
                <el-icon><CircleCheckFilled /></el-icon>
                四类字符（大写字母、小写字母、数字、特殊符号）中至少满足两类
              </div>
            </div>
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              class="register-field-confirm"
              type="password"
              show-password
              placeholder="请再次输入密码"
              size="large"
              :prefix-icon="Lock"
              @input="handleFieldInput('confirmPassword')"
              @keydown.down.prevent="focusRegisterField('next')"
              @keydown.up.prevent="focusRegisterField('prev')"
              @keyup.enter="handleRegister"
            />
          </el-form-item>

          <el-button
            class="register-submit"
            type="primary"
            size="large"
            :loading="loading"
            :disabled="!canSubmit"
            @click="handleRegister"
          >
            立即注册
          </el-button>

          <div class="register-tip">
            <router-link to="/login">已有账号？返回登录</router-link>
            <router-link to="/">返回项目介绍</router-link>
          </div>
        </el-form>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled, Iphone, Lock, Message, User } from '@element-plus/icons-vue'
import { REGEX } from '../../constants'
import { userApi } from '../../api/user'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const emailFocused = ref(false)
const emailSuggestionIndex = ref(0)
const submitted = ref(false)

const form = reactive({
  username: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
})

const touched = reactive({
  username: false,
  email: false,
  phone: false,
  password: false,
  confirmPassword: false,
})

const strength = reactive({
  length: false,
  upper: false,
  lower: false,
  number: false,
  special: false,
  mixed: false,
})

const introChecks = ['密码加密传输', '账号信息写入 MySQL', '注册后可立即登录']
const emailSuffixes = ['qq.com', 'gmail.com', '163.com', '126.com', 'outlook.com', 'hotmail.com', 'foxmail.com']
const registerFieldClasses = [
  'register-field-username',
  'register-field-email',
  'register-field-phone',
  'register-field-password',
  'register-field-confirm',
]

const checkPasswordStrength = () => {
  const password = form.password || ''
  strength.length = password.length >= 8
  strength.upper = REGEX.HAS_UPPER.test(password)
  strength.lower = REGEX.HAS_LOWER.test(password)
  strength.number = REGEX.HAS_NUMBER.test(password)
  strength.special = REGEX.HAS_SPECIAL.test(password)
  strength.mixed = passwordCategoryCount.value >= 2
}

const passwordCategoryCount = computed(() => {
  const password = form.password || ''
  return [
    REGEX.HAS_UPPER.test(password),
    REGEX.HAS_LOWER.test(password),
    REGEX.HAS_NUMBER.test(password),
    REGEX.HAS_SPECIAL.test(password),
  ].filter(Boolean).length
})

const isPasswordValid = computed(() => Boolean(form.password))
const canSubmit = computed(() =>
  Boolean(form.username)
  && Boolean(form.email)
  && Boolean(form.phone)
  && Boolean(form.password)
  && Boolean(form.confirmPassword)
  && form.confirmPassword === form.password
)
const showPasswordTips = computed(() => touched.password || Boolean(form.password))

/**
 * 输入时立即触发表单项校验，让提示随用户输入实时更新。
 */
const validateFieldLive = (field) => {
  window.setTimeout(() => {
    formRef.value?.validateField(field).catch(() => {})
  }, 0)
}

const shouldShowFieldError = (field, value) => submitted.value || touched[field] || Boolean(value)

const handleFieldInput = (field) => {
  touched[field] = true
  validateFieldLive(field)
}

const handleEmailInput = () => {
  emailFocused.value = true
  emailSuggestionIndex.value = 0
  handleFieldInput('email')
}

const handlePasswordInput = () => {
  touched.password = true
  checkPasswordStrength()
  validateFieldLive('password')
  if (form.confirmPassword) {
    validateFieldLive('confirmPassword')
  }
}

const emailSuggestions = computed(() => {
  const value = (form.email || '').trim()
  if (!value || value.startsWith('@')) {
    return []
  }

  const [namePart, suffixPart = ''] = value.split('@')
  if (!namePart) {
    return []
  }

  const normalizedSuffix = suffixPart.toLowerCase()
  return emailSuffixes
    .filter((suffix) => !normalizedSuffix || suffix.startsWith(normalizedSuffix))
    .map((suffix) => `${namePart}@${suffix}`)
    .filter((email) => email !== value)
})

const showEmailSuggestions = computed(() => emailFocused.value && emailSuggestions.value.length > 0)

const selectEmailSuggestion = (email) => {
  form.email = email
  emailFocused.value = false
  emailSuggestionIndex.value = 0
  formRef.value?.validateField('email').catch(() => {})
}

const moveEmailSuggestion = (step) => {
  if (!showEmailSuggestions.value) {
    emailFocused.value = true
    emailSuggestionIndex.value = 0
    return
  }

  const count = emailSuggestions.value.length
  emailSuggestionIndex.value = (emailSuggestionIndex.value + step + count) % count
}

const handleEmailArrow = (step) => {
  if (showEmailSuggestions.value) {
    moveEmailSuggestion(step)
    return
  }

  focusRegisterField(step > 0 ? 'next' : 'prev')
}

const confirmEmailSuggestion = () => {
  if (!showEmailSuggestions.value) return

  selectEmailSuggestion(emailSuggestions.value[emailSuggestionIndex.value])
}

/**
 * 表单内上下键切换输入焦点；邮箱候选展开时由候选列表优先处理。
 */
const focusRegisterField = (direction) => {
  const fields = registerFieldClasses
    .map((className) => document.querySelector(`.${className} input`))
    .filter(Boolean)

  if (!fields.length) return

  const activeIndex = fields.findIndex((field) => field === document.activeElement)
  const fallbackIndex = direction === 'next' ? 0 : fields.length - 1
  const currentIndex = activeIndex === -1 ? fallbackIndex : activeIndex
  const offset = direction === 'next' ? 1 : -1
  const nextIndex = (currentIndex + offset + fields.length) % fields.length

  fields[nextIndex].focus()
}

const handleEmailBlur = () => {
  window.setTimeout(() => {
    emailFocused.value = false
    emailSuggestionIndex.value = 0
  }, 120)
}

const validateEmail = (_rule, value, callback) => {
  if (!value) {
    if (!shouldShowFieldError('email', value)) {
      callback()
      return
    }
    callback(new Error('邮箱不能为空'))
    return
  }
  if (!REGEX.EMAIL.test(value)) {
    callback(new Error('邮箱格式不正确'))
    return
  }
  callback()
}

const validatePhone = (_rule, value, callback) => {
  if (!value) {
    if (!shouldShowFieldError('phone', value)) {
      callback()
      return
    }
    callback(new Error('手机号不能为空'))
    return
  }
  if (!REGEX.PHONE.test(value)) {
    callback(new Error('手机号格式不正确'))
    return
  }
  callback()
}

const validatePassword = (_rule, _value, callback) => {
  checkPasswordStrength()
  if (!form.password && !shouldShowFieldError('password', form.password)) {
    callback()
    return
  }
  if (!form.password) {
    callback(new Error('密码不能为空'))
    return
  }
  callback()
}

const validateConfirmPassword = (_rule, value, callback) => {
  if (!value) {
    if (!submitted.value) {
      callback()
      return
    }
    callback(new Error('请再次输入密码'))
    return
  }
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const rules = {
  username: [
    {
      validator: (_rule, value, callback) => {
        if (!value && !shouldShowFieldError('username', value)) {
          callback()
          return
        }
        if (!value) {
          callback(new Error('用户名不能为空'))
          return
        }
        callback()
      },
      trigger: ['blur', 'change'],
    },
    { min: 3, max: 32, message: '用户名长度应为 3-32 个字符', trigger: ['blur', 'change'] },
  ],
  email: [{ validator: validateEmail, trigger: ['blur', 'change'] }],
  phone: [{ validator: validatePhone, trigger: ['blur', 'change'] }],
  password: [{ validator: validatePassword, trigger: ['blur', 'change'] }],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: ['blur', 'change'] }],
}

const handleRegister = async () => {
  submitted.value = true
  Object.keys(touched).forEach((field) => {
    touched[field] = true
  })

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    focusFirstInvalidField()
    return
  }

  loading.value = true
  try {
    await userApi.register({
      username: form.username,
      email: form.email,
      phone: form.phone,
      password: form.password,
      confirmPassword: form.confirmPassword,
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } finally {
    loading.value = false
  }
}

const focusFirstInvalidField = () => {
  const invalidField = registerFieldClasses.find((className) => {
    const wrapper = document.querySelector(`.${className}`)?.closest('.el-form-item')
    return wrapper?.classList.contains('is-error')
  })

  const target = invalidField
    ? document.querySelector(`.${invalidField} input`)
    : document.querySelector('.register-field-username input')

  target?.focus()
}
</script>

<style scoped>
.register-page {
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

.auth-brand {
  position: fixed;
  top: 18px;
  left: 36px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: #172033;
}

.auth-brand span {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 8px;
  background: linear-gradient(135deg, #047857, #0ea5e9);
  color: #ffffff;
  font-weight: 900;
}

.register-shell {
  display: grid;
  grid-template-columns: minmax(360px, 0.95fr) minmax(420px, 500px);
  align-items: center;
  gap: 40px;
  width: min(1180px, calc(100% - 64px));
  min-height: 100vh;
  margin: 0 auto;
  padding: 66px 0 30px;
}

.register-intro {
  max-width: 640px;
}

.auth-kicker,
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

.register-intro h1 {
  margin-top: 16px;
  font-size: 40px;
  line-height: 1.14;
  letter-spacing: 0;
}

.register-intro p {
  margin-top: 14px;
  color: #53657d;
  font-size: 16px;
  line-height: 1.82;
}

.intro-checks {
  display: grid;
  gap: 10px;
  margin-top: 22px;
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

.register-card {
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.95);
  padding: 22px 26px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.14);
}

.card-heading {
  margin-bottom: 14px;
}

.card-heading h2 {
  margin-top: 10px;
  font-size: 28px;
}

.card-heading p {
  margin-top: 6px;
  color: #64748b;
}

.password-checklist {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  width: 100%;
  margin-top: 10px;
  border-radius: 8px;
  background: #f8fafc;
  padding: 10px;
}

.check-item {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #94a3b8;
  font-size: 12px;
}

.check-item.met {
  color: #047857;
  font-weight: 800;
}

.register-submit {
  width: 100%;
  min-height: 42px;
  border: 0;
  border-radius: 8px;
  background: #047857;
  font-weight: 900;
}

.register-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  font-size: 13px;
}

.register-tip a {
  color: #047857;
  font-weight: 800;
}

:deep(.el-input__wrapper) {
  min-height: 40px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #d7dee9 inset;
}

.email-field {
  position: relative;
  width: 100%;
}

.email-suggestions {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 20;
  max-height: 180px;
  overflow: auto;
  border: 1px solid #d7dee9;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.14);
  padding: 6px;
}

.email-suggestions button {
  display: block;
  width: 100%;
  min-height: 34px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #334155;
  padding: 0 10px;
  text-align: left;
}

.email-suggestions button:hover {
  background: #ecfdf5;
  color: #047857;
}

.email-suggestions button.active {
  background: #dff7ec;
  color: #047857;
  font-weight: 800;
}

:deep(.el-form-item) {
  margin-bottom: 14px;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #047857 inset;
}

@media (max-width: 920px) {
  .auth-brand {
    position: static;
    margin: 22px 24px 0;
  }

  .register-shell {
    grid-template-columns: 1fr;
    width: min(640px, calc(100% - 32px));
    min-height: auto;
    padding-top: 38px;
  }

  .register-intro h1 {
    font-size: 34px;
  }
}

@media (max-width: 560px) {
  .register-shell {
    width: calc(100% - 24px);
    gap: 24px;
    padding-bottom: 28px;
  }

  .register-card {
    padding: 24px;
  }

  .password-checklist {
    grid-template-columns: 1fr;
  }

  .register-tip {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

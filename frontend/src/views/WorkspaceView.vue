<template>
  <StudioLayout>
    <section class="nexus-workbench">
      <div class="workbench-center">
        <div class="assistant-mark">文</div>
        <h1>Hi {{ username }}，今天需要我帮你处理什么？</h1>

        <div class="command-box">
          <button class="command-icon-button" type="button" title="添加资料" @click="showReservedTip('添加资料')">
            <Plus />
          </button>
          <input
            v-model="commandText"
            class="command-input"
            placeholder="总结我的资料、生成汇报大纲，或者帮我梳理一份交付文档..."
            @keyup.enter="submitReservedCommand"
          />
          <button class="command-icon-button voice-button" type="button" title="语音输入" @click="showReservedTip('语音输入')">
            <Microphone />
          </button>
          <button class="send-button" type="button" title="发送" @click="submitReservedCommand">
            <Top />
          </button>
        </div>

        <div class="quick-actions" aria-label="首页预留操作">
          <button v-for="action in quickActions" :key="action.label" type="button" @click="showReservedTip(action.label)">
            <component :is="action.icon" />
            <span>{{ action.label }}</span>
          </button>
        </div>
      </div>

      <section class="reserved-panel" aria-label="后续功能预留">
        <article v-for="item in reservedModules" :key="item.title" class="reserved-card">
          <div class="reserved-icon">
            <component :is="item.icon" />
          </div>
          <strong>{{ item.title }}</strong>
          <p>{{ item.description }}</p>
          <button type="button" @click="showReservedTip(item.title)">预留入口</button>
        </article>
      </section>
    </section>
  </StudioLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  Collection,
  DataAnalysis,
  DataBoard,
  Document,
  FolderOpened,
  Microphone,
  Plus,
  Tickets,
  Top,
  Upload,
} from '@element-plus/icons-vue'
import StudioLayout from '../components/StudioLayout.vue'
import { STORAGE_KEYS } from '../constants'

const commandText = ref('')

const quickActions = [
  { label: '上传文档', icon: Upload },
  { label: '搭建知识库', icon: Collection },
  { label: '开启对话', icon: ChatDotRound },
  { label: '生成大纲', icon: Document },
]

const reservedModules = [
  {
    title: '文档库',
    description: '集中管理上传文档、解析状态、在线查看和下载删除流程。',
    icon: FolderOpened,
  },
  {
    title: 'AI 阅读室',
    description: '后续接入基于资料的问答、摘要、追问和引用溯源。',
    icon: ChatDotRound,
  },
  {
    title: '文档工厂',
    description: '后续接入 Word、PPT、汇报材料和交付文档生成能力。',
    icon: Document,
  },
  {
    title: '任务与日志',
    description: '后续展示 AI 调用、解析任务、队列状态和异常排查。',
    icon: Tickets,
  },
]

const username = computed(() => {
  try {
    const user = JSON.parse(localStorage.getItem(STORAGE_KEYS.USER_INFO) || '{}')
    return user.displayName || user.username || localStorage.getItem('userName') || 'DocNexus'
  } catch {
    return localStorage.getItem('userName') || 'DocNexus'
  }
})

// 首页当前只做展示占位，不触发真实业务流程。
const showReservedTip = (name) => {
  ElMessage.info(`${name} 功能入口已预留，后续再接入真实业务。`)
}

// 输入框发送暂不调用后端，只给出预留提示。
const submitReservedCommand = () => {
  if (!commandText.value.trim()) {
    showReservedTip('智能指令')
    return
  }
  ElMessage.info('智能指令入口已预留，后续将接入 AI 工作流。')
  commandText.value = ''
}
</script>

<style scoped>
.nexus-workbench {
  min-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 34px;
  padding: 48px 24px;
  background:
    radial-gradient(circle at 50% 10%, rgba(34, 197, 94, 0.14), transparent 28%),
    linear-gradient(180deg, rgba(240, 253, 244, 0.78), rgba(245, 248, 251, 0.96));
}

.workbench-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: min(100%, 1060px);
  margin: 0 auto;
  text-align: center;
}

.assistant-mark {
  display: grid;
  width: 96px;
  height: 96px;
  place-items: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #00856f, #22c55e);
  color: #ffffff;
  font-size: 42px;
  font-weight: 900;
  box-shadow: 0 18px 44px rgba(0, 133, 111, 0.28);
}

.workbench-center h1 {
  margin-top: 34px;
  color: #172033;
  font-size: 42px;
  line-height: 1.2;
  font-weight: 900;
  letter-spacing: 0;
}

.command-box {
  display: flex;
  align-items: center;
  width: min(100%, 1050px);
  min-height: 78px;
  gap: 14px;
  margin-top: 54px;
  padding: 10px 14px 10px 28px;
  border: 1px solid rgba(0, 133, 111, 0.12);
  border-radius: 999px;
  background: #ffffff;
  box-shadow: 0 22px 54px rgba(15, 23, 42, 0.10);
}

.command-icon-button,
.send-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border: 0;
  background: transparent;
}

.command-icon-button {
  width: 34px;
  height: 34px;
  color: #64748b;
}

.command-icon-button svg {
  width: 24px;
  height: 24px;
}

.voice-button svg {
  width: 21px;
  height: 21px;
}

.command-input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #172033;
  font-size: 20px;
}

.command-input::placeholder {
  color: #7b8797;
}

.send-button {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: #00856f;
  color: #ffffff;
  box-shadow: 0 10px 24px rgba(0, 133, 111, 0.28);
}

.send-button:hover {
  background: #00715f;
}

.send-button svg {
  width: 26px;
  height: 26px;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px;
  margin-top: 36px;
}

.quick-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 148px;
  height: 52px;
  gap: 9px;
  border: 1px solid #dbe5e1;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: #172033;
  font-weight: 800;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.05);
}

.quick-actions button:hover {
  border-color: #00856f;
  color: #00856f;
}

.quick-actions svg {
  width: 20px;
  height: 20px;
}

.reserved-panel {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  width: min(100%, 1060px);
  margin: 0 auto;
}

.reserved-card {
  min-height: 184px;
  padding: 18px;
  border: 1px solid rgba(0, 133, 111, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.05);
}

.reserved-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 8px;
  background: #e6f7ef;
  color: #00856f;
}

.reserved-icon svg {
  width: 22px;
  height: 22px;
}

.reserved-card strong {
  display: block;
  margin-top: 16px;
  color: #172033;
  font-size: 16px;
}

.reserved-card p {
  min-height: 48px;
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.reserved-card button {
  height: 34px;
  margin-top: 14px;
  padding: 0 12px;
  border: 1px solid #cde9dd;
  border-radius: 8px;
  background: #f0fdf4;
  color: #00856f;
  font-weight: 800;
}

@media (max-width: 1180px) {
  .reserved-panel {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .nexus-workbench {
    justify-content: flex-start;
    padding: 34px 14px;
  }

  .assistant-mark {
    width: 76px;
    height: 76px;
    font-size: 34px;
  }

  .workbench-center h1 {
    margin-top: 24px;
    font-size: 30px;
  }

  .command-box {
    min-height: 66px;
    margin-top: 32px;
    padding-left: 18px;
  }

  .command-input {
    font-size: 16px;
  }

  .send-button {
    width: 48px;
    height: 48px;
  }

  .reserved-panel {
    grid-template-columns: 1fr;
  }
}
</style>

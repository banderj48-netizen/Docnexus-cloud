<template>
  <div class="feature-page">
    <header class="feature-hero">
      <div>
        <span class="eyebrow">Settings</span>
        <h1>系统设置</h1>
        <p>配置模型、知识库索引、检索评估、Python Agent 服务和企业部署参数。</p>
      </div>
      <button class="primary-button">
        <Check />
        保存配置
      </button>
    </header>

    <section class="settings-grid">
      <article v-for="group in groups" :key="group.title" class="surface setting-card">
        <component :is="group.icon" />
        <h2>{{ group.title }}</h2>
        <p>{{ group.desc }}</p>
        <div v-for="item in group.items" :key="item" class="setting-line">
          <span>{{ item }}</span>
          <strong>已启用</strong>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { Check, Connection, Files, MagicStick, Setting } from '@element-plus/icons-vue'

const groups = [
  { title: '模型配置', desc: '聊天模型、Embedding 模型和重排序策略。', icon: MagicStick, items: ['DeepSeek Chat', 'Qwen Embedding', '流式响应'] },
  { title: '基础设施', desc: 'MySQL、Redis、MinIO 与文件访问策略。', icon: Files, items: ['MinIO 存储', 'Redis 缓存', 'MySQL 元数据'] },
  { title: 'Agent 服务', desc: 'Java 后端流式调用 Python Agent。', icon: Connection, items: ['SSE 流式调用', '任务回调', '工具执行'] },
  { title: '系统偏好', desc: '审阅规则、引用要求和知识库质量阈值。', icon: Setting, items: ['引用强制校验', '风险提示', '召回质量监控'] },
]
</script>

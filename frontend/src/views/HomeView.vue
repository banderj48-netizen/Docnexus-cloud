<template>
  <main class="home-page">
    <header class="home-header">
      <router-link class="home-brand" to="/">
        <span class="home-brand-mark">DN</span>
        <span>
          <strong>文枢智能 DocNexus</strong>
          <small>面向文档库与知识库的 AI 阅读工作台</small>
        </span>
      </router-link>

      <nav class="home-nav" aria-label="产品导航">
        <a href="#capability">能力</a>
        <a href="#workflow">流程</a>
        <a href="#scenario">场景</a>
      </nav>

      <router-link class="home-login" to="/login">
        <el-icon><User /></el-icon>
        登录
      </router-link>
    </header>

    <section class="home-hero">
      <div class="hero-copy">
        <span class="hero-kicker">企业资料到知识库的一体化链路</span>
        <h1>让 AI 读懂资料，沉淀可检索、可引用的知识库</h1>
        <p>
          文枢智能 DocNexus 面向真实企业资料管理场景，围绕上传、解析、摘要、知识检索、AI 对话、
          引用溯源和检索评估，构建一个完整可追踪的文档库与知识库工作台。
        </p>

        <div class="hero-actions">
          <router-link class="hero-primary" to="/login">
            <el-icon><Right /></el-icon>
            进入登录
          </router-link>
          <router-link class="hero-secondary" to="/workspace">查看工作台</router-link>
        </div>

        <div class="hero-metrics">
          <div>
            <strong>7</strong>
            <span>核心业务环节</span>
          </div>
          <div>
            <strong>SSE</strong>
            <span>流式任务进度</span>
          </div>
          <div>
            <strong>RAG</strong>
            <span>资料引用问答</span>
          </div>
        </div>
      </div>

      <section class="carousel-panel" aria-label="项目介绍轮播">
        <el-carousel height="430px" indicator-position="outside" trigger="click" arrow="always">
          <el-carousel-item v-for="slide in slides" :key="slide.title">
            <article class="intro-slide" :class="slide.theme">
              <div class="slide-top">
                <span>{{ slide.tag }}</span>
                <el-icon><component :is="slide.icon" /></el-icon>
              </div>
              <h2>{{ slide.title }}</h2>
              <p>{{ slide.description }}</p>
              <div class="slide-board">
                <div v-for="item in slide.items" :key="item.label">
                  <strong>{{ item.value }}</strong>
                  <span>{{ item.label }}</span>
                </div>
              </div>
            </article>
          </el-carousel-item>
        </el-carousel>
      </section>
    </section>

    <section id="capability" class="home-section">
      <div class="section-title">
        <span>平台能力</span>
        <h2>不是普通文件列表，而是可追溯的知识库工作台</h2>
      </div>
      <div class="capability-grid">
        <article v-for="capability in capabilities" :key="capability.title">
          <el-icon><component :is="capability.icon" /></el-icon>
          <strong>{{ capability.title }}</strong>
          <p>{{ capability.description }}</p>
        </article>
      </div>
    </section>

    <section id="workflow" class="workflow-section">
      <div class="section-title">
        <span>业务闭环</span>
        <h2>从资料进入，到知识可用，每一步都有记录</h2>
      </div>
      <div class="workflow-line">
        <div v-for="(step, index) in workflow" :key="step" class="workflow-step">
          <em>{{ String(index + 1).padStart(2, '0') }}</em>
          <strong>{{ step }}</strong>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup>
const slides = [
  {
    tag: '知识理解',
    title: '上传资料后，AI 自动解析、切片并沉淀为可引用知识',
    description: '系统围绕企业资料建立结构化知识资产，为后续问答、摘要和引用审查提供可信来源。',
    theme: 'slide-green',
    icon: 'Reading',
    items: [
      { value: 'PDF / Word / PPT', label: '多格式资料接入' },
      { value: '片段引用', label: '追踪回答来源' },
      { value: '任务状态', label: '解析进度可见' },
    ],
  },
  {
    tag: 'AI 学习室',
    title: '围绕资料追问、总结和对比，让学习过程可复用',
    description: '用户可以基于已上传资料与 AI 对话，快速定位知识点、生成摘要并保留学习上下文。',
    theme: 'slide-blue',
    icon: 'ChatLineRound',
    items: [
      { value: 'RAG', label: '基于资料回答' },
      { value: '上下文', label: '连续对话理解' },
      { value: '引用审阅', label: '降低幻觉风险' },
    ],
  },
  {
    tag: 'AI 日志',
    title: '用评估和日志持续优化 RAG 检索效果',
    description: '从召回片段、上下文窗口到 token 消耗，每次知识检索都可以追踪和复盘。',
    theme: 'slide-dark',
    icon: 'DocumentChecked',
    items: [
      { value: 'Recall', label: '召回率评估' },
      { value: 'Precision', label: '精确率评估' },
      { value: 'Trace', label: '检索日志' },
    ],
  },
]

const capabilities = [
  {
    title: '资料知识库',
    description: '集中管理上传资料，记录解析状态、摘要、标签和可引用片段。',
    icon: 'FolderOpened',
  },
  {
    title: 'RAG 问答',
    description: '回答基于企业资料生成，并保留来源片段便于审阅。',
    icon: 'DataAnalysis',
  },
  {
    title: 'AI 日志',
    description: '记录摘要、问答、关键词和引用检查过程，方便复盘召回资料与工具步骤。',
    icon: 'EditPen',
  },
  {
    title: '检索评估',
    description: '统一查看索引覆盖、引用风险、检索日志和评估指标。',
    icon: 'Finished',
  },
]

const workflow = ['上传资料', 'AI 解析理解', '构建知识库', '交流学习', 'AI 日志', '引用检查', '评估优化']
</script>

<style scoped>
.home-page {
  min-height: 100%;
  overflow: auto;
  background: #eef3f7;
  color: #172033;
}

.home-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: grid;
  grid-template-columns: minmax(260px, 1fr) auto auto;
  align-items: center;
  gap: 24px;
  min-height: 72px;
  padding: 0 48px;
  border-bottom: 1px solid rgba(203, 213, 225, 0.72);
  background: rgba(248, 250, 252, 0.92);
  backdrop-filter: blur(14px);
}

.home-brand,
.home-login,
.hero-primary,
.hero-secondary {
  display: inline-flex;
  align-items: center;
}

.home-brand {
  gap: 12px;
  color: inherit;
}

.home-brand-mark {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 8px;
  background: linear-gradient(135deg, #047857, #0ea5e9);
  color: #ffffff;
  font-weight: 900;
}

.home-brand strong,
.home-brand small {
  display: block;
}

.home-brand small {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.home-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.home-nav a {
  border-radius: 8px;
  color: #475569;
  padding: 9px 12px;
  font-size: 14px;
}

.home-nav a:hover {
  background: #e2e8f0;
  color: #172033;
}

.home-login {
  justify-content: center;
  min-height: 40px;
  gap: 8px;
  border-radius: 8px;
  background: #047857;
  color: #ffffff;
  padding: 0 16px;
  font-weight: 800;
}

.home-hero {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(420px, 1.08fr);
  gap: 36px;
  align-items: center;
  min-height: calc(100vh - 72px);
  padding: 48px;
}

.hero-copy {
  max-width: 680px;
}

.hero-kicker,
.section-title span {
  display: inline-flex;
  width: fit-content;
  border-radius: 999px;
  background: #dff7ec;
  color: #047857;
  padding: 6px 11px;
  font-size: 12px;
  font-weight: 900;
}

.hero-copy h1 {
  margin-top: 18px;
  font-size: 48px;
  line-height: 1.12;
  letter-spacing: 0;
}

.hero-copy p {
  margin-top: 18px;
  color: #53657d;
  font-size: 16px;
  line-height: 1.85;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}

.hero-primary,
.hero-secondary {
  justify-content: center;
  min-height: 44px;
  gap: 8px;
  border-radius: 8px;
  padding: 0 18px;
  font-weight: 900;
}

.hero-primary {
  background: #047857;
  color: #ffffff;
}

.hero-secondary {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #172033;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 34px;
}

.hero-metrics div {
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  padding: 16px;
}

.hero-metrics strong,
.hero-metrics span {
  display: block;
}

.hero-metrics strong {
  font-size: 25px;
}

.hero-metrics span {
  margin-top: 5px;
  color: #64748b;
  font-size: 13px;
}

.carousel-panel {
  min-width: 0;
}

.intro-slide {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 100%;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 8px;
  padding: 34px;
  color: #ffffff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.18);
}

.slide-green {
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.16), transparent 42%),
    linear-gradient(160deg, #065f46, #0891b2);
}

.slide-blue {
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.15), transparent 38%),
    linear-gradient(160deg, #0f4c81, #2563eb);
}

.slide-dark {
  background:
    linear-gradient(135deg, rgba(56, 189, 248, 0.22), transparent 44%),
    linear-gradient(160deg, #111827, #334155);
}

.slide-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.slide-top span {
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.17);
  padding: 7px 12px;
  font-size: 13px;
  font-weight: 900;
}

.slide-top .el-icon {
  font-size: 34px;
}

.intro-slide h2 {
  max-width: 720px;
  margin-top: auto;
  font-size: 34px;
  line-height: 1.22;
  letter-spacing: 0;
}

.intro-slide p {
  max-width: 690px;
  margin-top: 14px;
  color: rgba(255, 255, 255, 0.82);
  line-height: 1.75;
}

.slide-board {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 28px;
}

.slide-board div {
  min-height: 78px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.12);
  padding: 14px;
}

.slide-board strong,
.slide-board span {
  display: block;
}

.slide-board strong {
  font-size: 18px;
}

.slide-board span {
  margin-top: 6px;
  color: rgba(255, 255, 255, 0.74);
  font-size: 12px;
}

.home-section,
.workflow-section {
  padding: 28px 48px 56px;
}

.section-title h2 {
  margin-top: 12px;
  font-size: 28px;
  line-height: 1.28;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 20px;
}

.capability-grid article {
  min-height: 190px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  padding: 20px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.05);
}

.capability-grid .el-icon {
  color: #047857;
  font-size: 30px;
}

.capability-grid strong,
.capability-grid p {
  display: block;
}

.capability-grid strong {
  margin-top: 18px;
  font-size: 18px;
}

.capability-grid p {
  margin-top: 10px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.65;
}

.workflow-line {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 10px;
  margin-top: 20px;
}

.workflow-step {
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #ffffff;
  padding: 16px;
}

.workflow-step em,
.workflow-step strong {
  display: block;
}

.workflow-step em {
  color: #0ea5e9;
  font-style: normal;
  font-weight: 900;
}

.workflow-step strong {
  margin-top: 12px;
  font-size: 15px;
}

:deep(.el-carousel__container) {
  border-radius: 8px;
}

:deep(.el-carousel__arrow) {
  border-radius: 8px;
}

:deep(.el-carousel__button) {
  background: #047857;
}

@media (max-width: 1180px) {
  .home-header {
    padding: 0 24px;
  }

  .home-hero {
    grid-template-columns: 1fr;
    min-height: auto;
    padding: 36px 24px;
  }

  .capability-grid,
  .workflow-line {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .home-section,
  .workflow-section {
    padding-inline: 24px;
  }
}

@media (max-width: 760px) {
  .home-header {
    grid-template-columns: 1fr auto;
    gap: 12px;
    padding: 0 16px;
  }

  .home-nav {
    display: none;
  }

  .home-brand small {
    display: none;
  }

  .home-hero {
    padding: 28px 16px;
  }

  .hero-copy h1 {
    font-size: 34px;
  }

  .hero-metrics,
  .slide-board,
  .capability-grid,
  .workflow-line {
    grid-template-columns: 1fr;
  }

  .intro-slide {
    padding: 24px;
  }

  .intro-slide h2 {
    font-size: 25px;
  }

  .home-section,
  .workflow-section {
    padding: 18px 16px 40px;
  }
}
</style>

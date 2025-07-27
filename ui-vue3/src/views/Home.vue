<template>
  <a-layout class="home-layout">
    <!-- Animated Background -->
    <div class="animated-bg">
      <div class="bg-gradient"></div>
      <div class="floating-shapes">
        <div class="shape shape-1"></div>
        <div class="shape shape-2"></div>
        <div class="shape shape-3"></div>
      </div>
    </div>

    <!-- Main Content -->
    <a-layout-content class="main-content">
      <div class="content-container">
        <!-- Header Section -->
        <div class="header-section">
          <div class="header-content">
            <div class="brand-content">
              <div class="brand-icon">
                <RocketOutlined class="rocket-icon" />
              </div>
              <a-typography-title :level="1" class="brand-title">
                Spring-AI-Alibaba-Copilot
              </a-typography-title>
              <a-typography-text class="brand-subtitle">
                智能代码生成助手，让创意变为现实
              </a-typography-text>
            </div>

          </div>
        </div>

        <!-- Project Templates -->
        <div class="templates-section">
          <a-typography-title :level="4" class="section-title">
            <ThunderboltOutlined class="section-icon" />
            项目模板
          </a-typography-title>

          <a-row :gutter="[32, 32]" class="templates-grid" justify="space-around">
            <a-col
              v-for="card in projectCards"
              :key="card.label"
              :xs="12" :sm="8" :md="6" :lg="4" :xl="4"
            >
              <a-card
                hoverable
                class="template-card"
                @click="selectCard(card)"
                :class="{ 'card-loading': isLoading }"
              >
                <template #cover>
                  <div class="card-cover">
                    <div class="card-icon" :style="{ backgroundColor: card.color }">
                      <component :is="card.iconComponent" class="icon" />
                    </div>
                  </div>
                </template>

                <a-card-meta>
                  <template #title>
                    <span class="card-title">{{ card.label }}</span>
                  </template>
                  <template #description>
                    <span class="card-description">{{ card.description }}</span>
                  </template>
                </a-card-meta>
              </a-card>
            </a-col>
          </a-row>
        </div>

        <!-- Input Section -->
        <div class="input-section">
          <a-typography-title :level="4" class="section-title">
            <EditOutlined class="section-icon" />
            自定义需求
          </a-typography-title>

          <div class="input-container">
            <div class="textarea-wrapper">
              <a-textarea
                v-model:value="inputMessage"
                @keydown="handleKeydown"
                :disabled="isLoading"
                placeholder="详细描述您想要创建的项目，比如：创建一个待办事项管理应用，支持添加、删除、标记完成功能..."
                :rows="3"
                class="custom-textarea"
                :maxlength="5000"
                show-count
                :bordered="false"
              />
              <a-button
                type="primary"
                shape="circle"
                @click="sendAndGoToEditor"
                :disabled="!inputMessage.trim() || isLoading"
                :loading="isLoading"
                class="send-button-inline"
              >
                <SendOutlined />
              </a-button>
            </div>
          </div>
        </div>


      </div>
    </a-layout-content>
  </a-layout>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import * as Icons from '@ant-design/icons-vue'
const {
  RocketOutlined,
  ThunderboltOutlined,
  EditOutlined,
  SendOutlined,
  TrophyOutlined,
  BarChartOutlined,
  CodeOutlined,
  ReadOutlined,
  GlobalOutlined
} = Icons
import { message } from 'ant-design-vue'

const router = useRouter()

// Reactive data
const inputMessage = ref('')
const isLoading = ref(false)
const messageApi = message

// Project cards with Ant Design icons and colors (只显示5个)
const projectCards = [
  {
    iconComponent: ThunderboltOutlined,
    label: '空白模板',
    text: 'blank-template',
    description: '快速开始空白项目',
    color: '#1677ff',
    isBlankTemplate: true
  },
  {
    iconComponent: BarChartOutlined,
    label: '数据展示',
    text: '创建一个数据可视化页面，使用图表展示数据',
    description: '图表与数据可视化',
    color: '#36cfc9'
  },
  {
    iconComponent: CodeOutlined,
    label: 'Vue项目',
    text: '创建一个Vue3项目，包含组件化开发和状态管理',
    description: '现代前端框架应用',
    color: '#52c41a'
  },
  {
    iconComponent: ReadOutlined,
    label: '博客项目',
    text: '创建一个简单的博客系统，支持文章列表和详情页',
    description: '内容管理系统',
    color: '#597ef7'
  },
  {
    iconComponent: GlobalOutlined,
    label: '翻译助手',
    text: '创建一个多语言翻译助手应用',
    description: '语言处理工具',
    color: '#9254de'
  }
]

// Methods
const selectCard = (card: any) => {
  // 如果是空白模板，直接跳转到编辑器
  if (card.isBlankTemplate) {
    goToEditorWithTemplate()
    return
  }

  inputMessage.value = card.text

  // 添加选择卡片的动画效果
  messageApi.success({
    content: '已选择模板，正在准备创建...',
    duration: 1
  })

  // 设置短暂延迟以显示动画效果
  isLoading.value = true
  setTimeout(() => {
    sendAndGoToEditor()
    isLoading.value = false
  }, 800)
}

const sendAndGoToEditor = () => {
  if (!inputMessage.value.trim()) return

  // 显示加载状态
  isLoading.value = true

  // 跳转到编辑页面，传递用户输入并指示打开AI助手
  setTimeout(() => {
    router.push({
      path: '/editor',
      query: {
        prompt: inputMessage.value.trim(),
        openAI: 'true',
        autoSend: 'true'
      }
    })
  }, 300)
}

// 直接进入编辑器
const goToEditorDirectly = () => {
  isLoading.value = true

  setTimeout(() => {
    router.push({
      path: '/editor',
      query: {
        openAI: 'true'
      }
    })
    isLoading.value = false
  }, 300)
}

// 使用空白模板进入编辑器
const goToEditorWithTemplate = () => {
  isLoading.value = true

  setTimeout(() => {
    router.push({
      path: '/editor',
      query: {
        prompt: '创建一个基础的HTML页面结构',
        openAI: 'true',
        autoSend: 'true'
      }
    })
    isLoading.value = false
  }, 300)
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault()
    sendAndGoToEditor()
  }
}

</script>

<style scoped>
/* 整体布局样式 */
.home-layout {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
}

/* 动画背景 */
.animated-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  overflow: hidden;
}

.bg-gradient {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(240, 245, 255, 0.8) 0%, rgba(230, 240, 255, 0.8) 100%);
  opacity: 0.8;
}

:global(.dark) .bg-gradient {
  background: linear-gradient(135deg, rgba(22, 28, 36, 0.95) 0%, rgba(30, 38, 50, 0.95) 100%);
}

/* 浮动形状动画 */
.floating-shapes .shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.1;
  filter: blur(60px);
  animation: float 15s infinite ease-in-out;
}

.shape-1 {
  width: 300px;
  height: 300px;
  background: linear-gradient(45deg, #1677ff, #52c41a);
  top: -100px;
  right: 10%;
  animation-delay: 0s;
}

.shape-2 {
  width: 250px;
  height: 250px;
  background: linear-gradient(45deg, #722ed1, #eb2f96);
  bottom: -50px;
  left: 5%;
  animation-delay: -5s;
}

.shape-3 {
  width: 200px;
  height: 200px;
  background: linear-gradient(45deg, #fa8c16, #fa541c);
  top: 40%;
  left: 30%;
  animation-delay: -10s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-20px) scale(1.05);
  }
}

/* 头部区域样式 */
.header-section {
  padding: 40px 20px;
  margin-bottom: 40px;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1200px;
  margin: 0 auto;
  gap: 40px;
}

.brand-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  flex: 1;
}

.header-action {
  flex-shrink: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    gap: 20px;
  }

  .header-action {
    align-self: center;
  }
}

.brand-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: #1677ff;
  animation: pulse 2s infinite ease-in-out;
}

.rocket-icon {
  font-size: 48px;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
}

.brand-title {
  margin: 0 !important;
  font-size: 42px !important;
  background: linear-gradient(90deg, #1677ff, #52c41a);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: titleGradient 8s infinite linear;
}

@keyframes titleGradient {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

.brand-subtitle {
  font-size: 18px;
  color: rgba(0, 0, 0, 0.65);
  margin-top: 8px;
}

:global(.dark) .brand-subtitle {
  color: rgba(255, 255, 255, 0.65);
}

/* 主内容区域 */
.main-content {
  position: relative;
  z-index: 1;
  padding: 0 20px 40px;
}

.content-container {
  max-width: 1200px;
  margin: 0 auto;
}



/* 模板区域 */
.templates-section {
  margin-bottom: 40px;
}

.section-title {
  margin-bottom: 24px !important;
  display: flex;
  align-items: center;
}

.section-icon {
  margin-right: 8px;
  color: #1677ff;
}

.templates-grid {
  margin-bottom: 20px;
}

/* 卡片样式 - 增加20px宽度 */
.template-card {
  transition: all 0.3s ease;
  overflow: hidden;
  height: 100%;
  width: calc(100% + 20px);
  margin-left: -10px;
}

.template-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);
}

.card-cover {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24px 0;
  background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%);
}

:global(.dark) .card-cover {
  background: linear-gradient(135deg, #1f1f1f 0%, #2a2a2a 100%);
}

.card-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  transition: all 0.3s ease;
}

.template-card:hover .card-icon {
  transform: scale(1.1);
}

.icon {
  font-size: 28px;
  color: white;
}

.card-title {
  font-size: 16px;
  font-weight: 500;
}

.card-description {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

:global(.dark) .card-description {
  color: rgba(255, 255, 255, 0.45);
}



.card-loading {
  pointer-events: none;
  opacity: 0.7;
}

/* 输入区域 */
.input-section {
  margin-bottom: 40px;
}

/* 快速开始区域 */
.quick-start-section {
  margin-bottom: 40px;
}

.quick-start-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.start-coding-button-header {
  height: 48px;
  padding: 0 32px;
  font-size: 16px;
  font-weight: 500;
  border-radius: 24px;
  background: linear-gradient(135deg, #1677ff 0%, #52c41a 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(22, 119, 255, 0.3);
  transition: all 0.3s ease;
}

.start-coding-button-header:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(22, 119, 255, 0.4);
  background: linear-gradient(135deg, #0958d9 0%, #389e0d 100%);
}

.template-button-blank {
  height: 48px;
  padding: 0 32px;
  font-size: 16px;
  font-weight: 500;
  border-radius: 24px;
  border: none;
  color: #1677ff;
  background: transparent;
  transition: all 0.3s ease;
}

.template-button-blank:hover {
  transform: translateY(-2px);
  background: rgba(22, 119, 255, 0.1);
  color: #1677ff;
}

.quick-start-description {
  text-align: center;
  margin-top: 8px;
}

:global(.dark) .template-button-blank {
  color: #1677ff;
}

:global(.dark) .template-button-blank:hover {
  background: rgba(22, 119, 255, 0.2);
  color: #1677ff;
}

.input-container {
  display: flex;
  flex-direction: column;
}

.textarea-wrapper {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.textarea-wrapper:hover {
  border-color: #d1d5db;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.textarea-wrapper:focus-within {
  border-color: #93c5fd;
  box-shadow: 0 0 0 2px rgba(147, 197, 253, 0.2);
}

:global(.dark) .textarea-wrapper {
  background: #1f2937;
  border-color: #374151;
}

:global(.dark) .textarea-wrapper:hover {
  border-color: #4b5563;
}

:global(.dark) .textarea-wrapper:focus-within {
  border-color: #1677ff;
}

.custom-textarea {
  flex: 1;
  border: none !important;
  outline: none !important;
  resize: none;
  font-size: 16px;
  line-height: 1.5;
  background: transparent !important;
  padding: 0 !important;
  min-height: 60px;
  font-family: inherit;
  box-shadow: none !important;
}

.custom-textarea::placeholder {
  color: #9ca3af;
}

/* 移除 Ant Design textarea 的默认样式 */
:global(.ant-input) {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  padding: 0 !important;
}

:global(.ant-input:focus) {
  border: none !important;
  box-shadow: none !important;
}



:global(.dark) .custom-textarea {
  color: #f9fafb;
}

:global(.dark) .custom-textarea::placeholder {
  color: #6b7280;
}



.send-button-inline {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #1677ff;
  color: white;
  border: none;
  transition: all 0.2s ease;
  flex-shrink: 0;
  margin-bottom: 8px;
}

.send-button-inline:not(:disabled):hover {
  background: #0958d9;
  transform: scale(1.05);
}

.send-button-inline:disabled {
  background: #d1d5db;
  color: #9ca3af;
  cursor: not-allowed;
}

.send-button-inline .anticon {
  font-size: 18px;
}

:global(.dark) .send-button-inline {
  background: #1677ff;
}

:global(.dark) .send-button-inline:not(:disabled):hover {
  background: #0958d9;
}

:global(.dark) .send-button-inline:disabled {
  background: #4b5563;
  color: #6b7280;
}
</style>

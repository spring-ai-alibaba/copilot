<template>
  <div class="task-input">
    <div class="input-header">
      <h3>
        <RobotOutlined />
        描述您的编码需求
      </h3>
      <p class="description">
        AI助手将分析您的需求，制定执行计划，并逐步完成任务
      </p>
    </div>

    <div class="input-form">
      <a-form @submit="handleSubmit">
        <a-form-item>
          <div class="textarea-wrapper">
            <a-textarea
              v-model:value="query"
              placeholder="例如：帮我创建一个Spring Boot + Vue3的博客系统，包含用户管理、文章发布、评论功能..."
              :rows="4"
              :maxlength="1000"
              show-count
              @keydown.ctrl.enter="handleSubmit"
              :disabled="loading"
            />
            <!-- 添加AI生成中的等待样式 -->
            <div v-if="loading" class="ai-loading-indicator">
              <LoadingOutlined spin />
              <span>AI助手正在生成中...</span>
            </div>
          </div>
        </a-form-item>

        <a-form-item>
          <div class="form-actions">
            <div class="quick-actions">
              <a-tag
                v-for="template in quickTemplates"
                :key="template.key"
                @click="selectTemplate(template)"
                style="cursor: pointer; margin-bottom: 8px;"
              >
                {{ template.label }}
              </a-tag>
            </div>

            <div class="submit-actions">
              <a-button
                type="primary"
                size="large"
                :loading="loading"
                :disabled="!query.trim() || loading"
                @click="handleSubmit"
              >
                <SendOutlined />
                {{ loading ? '创建中' : '开始执行' }}
              </a-button>
            </div>
          </div>
        </a-form-item>
      </a-form>
    </div>

    <div class="examples">
      <a-collapse ghost>
        <a-collapse-panel key="examples" header="查看示例需求">
          <div class="example-list">
            <div
              v-for="example in examples"
              :key="example.title"
              class="example-item"
              @click="selectExample(example)"
            >
              <h4>{{ example.title }}</h4>
              <p>{{ example.description }}</p>
            </div>
          </div>
        </a-collapse-panel>
      </a-collapse>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref} from 'vue'
import {LoadingOutlined, RobotOutlined, SendOutlined} from '@ant-design/icons-vue'
import {useTaskStore} from '@/stores/task'

interface QuickTemplate {
  key: string
  label: string
  content: string
}

interface Example {
  title: string
  description: string
  content: string
}

const props = defineProps<{
  loading?: boolean
}>()

const emit = defineEmits<{
  submit: [query: string]
  taskCreated: [taskId: string]
}>()

const query = ref('')
const taskStore = useTaskStore()

// 快速模板
const quickTemplates: QuickTemplate[] = [
  {
    key: 'spring-vue',
    label: 'AI翻译应用',
    content: '创建一个Spring AI后端 + Vue3前端的AI应用,加上翻译功能'
  },
  {
    key: 'microservice',
    label: '微服务架构',
    content: '设计并实现一个微服务架构的系统，包含网关、服务发现、配置中心等组件'
  },
  {
    key: 'ai-chat',
    label: 'AI聊天应用',
    content: '开发一个AI聊天应用，支持多轮对话、流式响应、对话历史管理'
  },
  {
    key: 'data-analysis',
    label: '数据分析平台',
    content: '构建一个数据分析平台，支持数据导入、可视化图表、报表生成'
  }
]

// 示例需求
const examples: Example[] = [
  {
    title: '电商系统开发',
    description: '完整的电商平台，包含商品管理、订单处理、支付集成',
    content: '帮我开发一个完整的电商系统，需要包含：\n1. 商品管理（增删改查、分类、库存）\n2. 用户管理（注册登录、个人信息、收货地址）\n3. 购物车功能\n4. 订单管理（下单、支付、发货、退款）\n5. 后台管理系统\n技术栈：Spring Boot + Vue3 + MySQL + Redis'
  },
  {
    title: '博客系统',
    description: '个人博客平台，支持文章发布、评论、标签分类',
    content: '创建一个个人博客系统，功能包括：\n1. 文章管理（发布、编辑、删除、草稿）\n2. 分类和标签管理\n3. 评论系统\n4. 用户管理\n5. 搜索功能\n6. 响应式设计\n使用Spring Boot + Vue3 + Ant Design Vue'
  },
  {
    title: '任务管理工具',
    description: '团队协作的任务管理平台，支持项目管理、进度跟踪',
    content: '开发一个任务管理工具，类似Jira，包含：\n1. 项目管理\n2. 任务创建和分配\n3. 进度跟踪\n4. 团队协作\n5. 甘特图\n6. 报表统计\n技术要求：前后端分离，支持实时通知'
  },
  {
    title: 'API文档生成器',
    description: '自动生成和管理API文档的工具',
    content: '构建一个API文档生成和管理工具：\n1. 自动扫描Spring Boot项目生成API文档\n2. 在线测试API接口\n3. 版本管理\n4. 团队协作\n5. 导出多种格式文档\n6. 集成Swagger/OpenAPI'
  }
]

// 处理提交
const handleSubmit = async () => {
  if (query.value.trim() && !props.loading) {
    emit('submit', query.value.trim())

    try {
      // 只负责创建任务，不处理SSE连接
      const response = await taskStore.createTask(query.value.trim())

      if (response.taskId) {
        // 通知父组件任务已创建
        emit('taskCreated', response.taskId)
        // 清空输入框
        query.value = ''
      }
    } catch (error) {
      console.error('创建任务失败:', error)
    }
  }
}

// 选择模板
const selectTemplate = (template: QuickTemplate) => {
  query.value = template.content
}

// 选择示例
const selectExample = (example: Example) => {
  query.value = example.content
}
</script>

<style scoped>
.task-input {
  width: 100%;
}

.input-header {
  margin-bottom: 24px;
}

.input-header h3 {
  margin: 0 0 8px 0;
  color: #262626;
  font-size: 18px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.description {
  margin: 0;
  color: #8c8c8c;
  font-size: 14px;
}

.input-form {
  margin-bottom: 24px;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
}

.quick-actions {
  flex: 1;
}

.submit-actions {
  flex-shrink: 0;
}

.examples {
  border-top: 1px solid #f0f0f0;
  padding-top: 16px;
}

.example-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 16px;
}

.example-item {
  padding: 16px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
}

.example-item:hover {
  border-color: #1890ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.1);
}

.example-item h4 {
  margin: 0 0 8px 0;
  color: #262626;
  font-size: 14px;
  font-weight: 600;
}

.example-item p {
  margin: 0;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
}

:deep(.ant-tag) {
  margin-right: 8px;
  margin-bottom: 4px;
}

:deep(.ant-collapse-ghost > .ant-collapse-item > .ant-collapse-header) {
  padding: 12px 0;
}

/* 添加textarea包装器样式 */
.textarea-wrapper {
  position: relative;
}

/* AI加载指示器样式 */
.ai-loading-indicator {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-color: rgba(255, 255, 255, 0.8);
  border-radius: 2px;
  z-index: 1;
}

.ai-loading-indicator span {
  margin-top: 8px;
  color: #1890ff;
}
</style>
import TaskExecutionDetail from './TaskExecutionDetail.vue'

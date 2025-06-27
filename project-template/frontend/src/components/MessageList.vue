<template>
  <div class="message-list" ref="messageListRef">
    <div class="messages-container">
      <!-- 空状态 -->
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">
          <RobotOutlined />
        </div>
        <h3>开始与AI助手对话</h3>
        <p>您可以问我任何问题，我会尽力为您提供帮助。</p>
      </div>

      <!-- 消息列表 -->
      <div v-else class="messages">
        <div
          v-for="message in messages"
          :key="message.id"
          :class="['message-item', `message-${message.type.toLowerCase()}`]"
        >
          <!-- 用户消息 -->
          <div v-if="message.type === MessageType.USER" class="user-message">
            <div class="message-content">
              <div class="message-text">{{ message.content }}</div>
              <div class="message-time">{{ formatTime(message.timestamp) }}</div>
            </div>
            <div class="message-avatar">
              <UserOutlined />
            </div>
          </div>

          <!-- AI助手消息 -->
          <div v-else-if="message.type === MessageType.ASSISTANT" class="assistant-message">
            <div class="message-avatar">
              <RobotOutlined />
            </div>
            <div class="message-content">
              <div class="message-text">
                <!-- 流式输入效果 -->
                <div v-if="message.isStreaming" class="streaming-text">
                  {{ message.content }}
                  <span class="cursor">|</span>
                </div>
                <!-- 普通文本 -->
                <div v-else class="normal-text">
                  {{ message.content }}
                </div>
                <!-- 错误信息 -->
                <div v-if="message.error" class="error-text">
                  <ExclamationCircleOutlined />
                  {{ message.error }}
                </div>
              </div>
              <div class="message-time">{{ formatTime(message.timestamp) }}</div>
            </div>
          </div>

          <!-- 系统消息 -->
          <div v-else-if="message.type === MessageType.SYSTEM" class="system-message">
            <div class="system-content">
              <InfoCircleOutlined />
              {{ message.content }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {nextTick, ref, watch} from 'vue'
import {ExclamationCircleOutlined, InfoCircleOutlined, RobotOutlined, UserOutlined} from '@ant-design/icons-vue'
import type {DisplayMessage} from '@/types/chat'
import {MessageType} from '@/types/chat'

interface Props {
  messages: DisplayMessage[]
  autoScroll?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  autoScroll: true
})

// 响应式数据
const messageListRef = ref<HTMLElement>()

// 格式化时间
const formatTime = (timestamp: Date): string => {
  const now = new Date()
  const diff = now.getTime() - timestamp.getTime()

  if (diff < 60000) { // 1分钟内
    return '刚刚'
  } else if (diff < 3600000) { // 1小时内
    return `${Math.floor(diff / 60000)}分钟前`
  } else if (diff < 86400000) { // 24小时内
    return timestamp.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit'
    })
  } else {
    return timestamp.toLocaleDateString('zh-CN', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (props.autoScroll && messageListRef.value) {
    nextTick(() => {
      messageListRef.value!.scrollTop = messageListRef.value!.scrollHeight
    })
  }
}

// 监听消息变化，自动滚动
watch(
  () => props.messages,
  () => {
    scrollToBottom()
  },
  { deep: true }
)

// 暴露方法给父组件
defineExpose({
  scrollToBottom
})
</script>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafafa;
}

.messages-container {
  max-width: 800px;
  margin: 0 auto;
}

/* 空状态样式 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  text-align: center;
  color: #8c8c8c;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: #d9d9d9;
}

.empty-state h3 {
  margin: 0 0 8px 0;
  color: #595959;
  font-weight: 500;
}

.empty-state p {
  margin: 0;
  font-size: 14px;
}

/* 消息样式 */
.messages {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-item {
  display: flex;
  width: 100%;
}

/* 用户消息样式 */
.user-message {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.user-message .message-content {
  max-width: 70%;
  background: #1890ff;
  color: white;
  padding: 12px 16px;
  border-radius: 18px 18px 4px 18px;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.15);
}

.user-message .message-avatar {
  width: 32px;
  height: 32px;
  background: #1890ff;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* AI助手消息样式 */
.assistant-message {
  display: flex;
  justify-content: flex-start;
  gap: 12px;
}

.assistant-message .message-content {
  max-width: 70%;
  background: white;
  padding: 12px 16px;
  border-radius: 18px 18px 18px 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: 1px solid #f0f0f0;
}

.assistant-message .message-avatar {
  width: 32px;
  height: 32px;
  background: #52c41a;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* 系统消息样式 */
.system-message {
  justify-content: center;
}

.system-content {
  background: #f6f6f6;
  color: #8c8c8c;
  padding: 8px 16px;
  border-radius: 16px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 消息文本样式 */
.message-text {
  line-height: 1.5;
  word-wrap: break-word;
  white-space: pre-wrap;
}

.message-time {
  font-size: 11px;
  opacity: 0.7;
  margin-top: 4px;
}

/* 流式输入效果 */
.streaming-text {
  position: relative;
}

.cursor {
  animation: blink 1s infinite;
  color: #1890ff;
  font-weight: bold;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* 错误文本样式 */
.error-text {
  color: #ff4d4f;
  margin-top: 8px;
  padding: 8px;
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 6px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .message-list {
    padding: 12px;
  }

  .user-message .message-content,
  .assistant-message .message-content {
    max-width: 85%;
  }

  .message-content {
    padding: 10px 14px;
  }

  .message-avatar {
    width: 28px !important;
    height: 28px !important;
    font-size: 12px;
  }
}
</style>

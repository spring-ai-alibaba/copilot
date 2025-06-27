<template>
  <div class="message-input">
    <div class="input-container">
      <a-textarea
        v-model:value="inputMessage"
        :placeholder="placeholder"
        :rows="rows"
        :maxlength="maxLength"
        :disabled="disabled"
        show-count
        @keydown="handleKeyDown"
        @focus="handleFocus"
        @blur="handleBlur"
        class="message-textarea"
      />
      <div class="input-actions">
        <a-button
          type="primary"
          :loading="loading"
          :disabled="!canSend"
          @click="handleSend"
          class="send-button"
        >
          <template #icon>
            <SendOutlined />
          </template>
          {{ loading ? '发送中' : '发送' }}
        </a-button>
      </div>
    </div>
    <div v-if="showTips" class="input-tips">
      <span class="tip-text">按 Ctrl + Enter 快速发送</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue'
import {SendOutlined} from '@ant-design/icons-vue'

interface Props {
  loading?: boolean
  disabled?: boolean
  placeholder?: string
  maxLength?: number
  rows?: number
  showTips?: boolean
}

interface Emits {
  (e: 'send', message: string): void
  (e: 'focus'): void
  (e: 'blur'): void
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  disabled: false,
  placeholder: '请输入您的消息...',
  maxLength: 2000,
  rows: 3,
  showTips: true
})

const emit = defineEmits<Emits>()

// 响应式数据
const inputMessage = ref('')
const isFocused = ref(false)

// 计算属性
const canSend = computed(() => {
  return !props.loading && !props.disabled && inputMessage.value.trim().length > 0
})

// 事件处理
const handleKeyDown = (event: KeyboardEvent) => {
  if (event.ctrlKey && event.key === 'Enter') {
    event.preventDefault()
    handleSend()
  }
}

const handleSend = () => {
  if (canSend.value) {
    const message = inputMessage.value.trim()
    emit('send', message)
    inputMessage.value = ''
  }
}

const handleFocus = () => {
  isFocused.value = true
  emit('focus')
}

const handleBlur = () => {
  isFocused.value = false
  emit('blur')
}

// 暴露方法给父组件
const focus = () => {
  const textarea = document.querySelector('.message-textarea textarea') as HTMLTextAreaElement
  textarea?.focus()
}

const clear = () => {
  inputMessage.value = ''
}

const setValue = (value: string) => {
  inputMessage.value = value
}

defineExpose({
  focus,
  clear,
  setValue
})
</script>

<style scoped>
.message-input {
  padding: 16px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
}

.input-container {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.message-textarea {
  flex: 1;
}

.message-textarea :deep(.ant-input) {
  resize: none;
  border-radius: 8px;
  border: 1px solid #d9d9d9;
  transition: all 0.3s;
}

.message-textarea :deep(.ant-input:focus) {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.input-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.send-button {
  height: 40px;
  border-radius: 8px;
  font-weight: 500;
}

.input-tips {
  margin-top: 8px;
  text-align: center;
}

.tip-text {
  font-size: 12px;
  color: #8c8c8c;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .message-input {
    padding: 12px;
  }

  .input-container {
    flex-direction: column;
    align-items: stretch;
  }

  .input-actions {
    flex-direction: row;
    justify-content: flex-end;
  }

  .send-button {
    width: auto;
    min-width: 80px;
  }
}
</style>

<template>
  <div class="tool-config-modal" v-if="isVisible" @click.self="closeModal">
    <div class="modal-content bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-4xl w-full max-h-[90vh] overflow-hidden">
      <!-- Header -->
      <div class="modal-header bg-gray-50 dark:bg-gray-700 px-6 py-4 border-b border-gray-200 dark:border-gray-600">
        <div class="flex items-center justify-between">
          <h2 class="text-xl font-semibold text-gray-900 dark:text-white flex items-center">
            <span class="mr-2">🔧</span>
            动态工具配置
          </h2>
          <button
            @click="closeModal"
            class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
          >
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>
      </div>

      <!-- Tab Navigation -->
      <div class="tab-nav bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-600">
        <div class="flex">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            @click="activeTab = tab.id"
            :class="getTabClass(tab.id)"
            class="px-6 py-3 text-sm font-medium transition-colors"
          >
            <span class="mr-2">{{ tab.icon }}</span>
            {{ tab.label }}
          </button>
        </div>
      </div>

      <!-- Tab Content -->
      <div class="modal-body p-6 overflow-y-auto" style="max-height: calc(90vh - 140px);">
        <!-- System Tools Tab -->
        <div v-if="activeTab === 'system-tools'" class="tab-content">
          <div class="tool-section">
            <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-4">系统内置工具</h3>
            <div class="tools-list space-y-3">
              <div v-if="isLoadingSystemTools" class="loading-tools text-center py-8">
                <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500 mx-auto mb-2"></div>
                <div class="text-gray-500 dark:text-gray-400">正在加载系统工具...</div>
              </div>
              <div v-else-if="systemTools.length === 0" class="no-tools text-center py-8 text-gray-500 dark:text-gray-400">
                暂无系统工具
              </div>
              <ToolItem
                v-else
                v-for="tool in systemTools"
                :key="tool.name"
                :tool="tool"
                type="system"
                @test="testTool"
                @info="showToolInfo"
                @toggle="toggleTool"
              />
            </div>
          </div>
        </div>

        <!-- MCP Tools Tab -->
        <div v-if="activeTab === 'mcp-tools'" class="tab-content">
          <div class="tool-section">
            <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-4">MCP服务器工具</h3>
            <div class="tools-list space-y-3">
              <div v-if="isLoadingMcpTools" class="loading-tools text-center py-8">
                <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500 mx-auto mb-2"></div>
                <div class="text-gray-500 dark:text-gray-400">正在加载MCP工具...</div>
              </div>
              <div v-else-if="mcpTools.length === 0" class="no-tools text-center py-8 text-gray-500 dark:text-gray-400">
                暂无MCP工具，请先添加MCP服务器
              </div>
              <ToolItem
                v-else
                v-for="tool in mcpTools"
                :key="tool.name"
                :tool="tool"
                type="mcp"
                @test="testTool"
                @info="showToolInfo"
                @remove="removeTool"
              />
            </div>
          </div>
        </div>

        <!-- Add MCP Server Tab -->
        <div v-if="activeTab === 'add-mcp'" class="tab-content">
          <McpServerConfig @server-added="handleServerAdded" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import ToolItem from './ToolItem.vue'
import McpServerConfig from './McpServerConfig.vue'
import { useToolConfigStore } from '../stores/toolConfigStore'

interface Tool {
  name: string
  displayName?: string
  description: string
  type: 'SYSTEM' | 'MCP'
  source: string
  enabled: boolean
  parameters?: any[]
  icon?: string
}

interface Tab {
  id: string
  label: string
  icon: string
}

// Props & Emits
interface Props {
  visible: boolean
}

interface Emits {
  (e: 'update:visible', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// Store
const toolConfigStore = useToolConfigStore()

// Reactive data
const activeTab = ref('system-tools')
const isLoadingSystemTools = ref(false)
const isLoadingMcpTools = ref(false)

// Computed
const isVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

const systemTools = computed(() => toolConfigStore.systemTools)
const mcpTools = computed(() => toolConfigStore.mcpTools)

// Tabs configuration
const tabs: Tab[] = [
  { id: 'system-tools', label: '系统工具', icon: '⚙️' },
  { id: 'mcp-tools', label: 'MCP工具', icon: '🌐' },
  { id: 'add-mcp', label: '添加MCP服务器', icon: '➕' }
]

// Methods
const closeModal = () => {
  isVisible.value = false
}

const getTabClass = (tabId: string) => {
  const baseClass = 'border-b-2 transition-colors'
  if (activeTab.value === tabId) {
    return `${baseClass} border-blue-500 text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20`
  } else {
    return `${baseClass} border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600`
  }
}

const loadSystemTools = async () => {
  isLoadingSystemTools.value = true
  try {
    await toolConfigStore.loadSystemTools()
  } catch (error) {
    console.error('加载系统工具失败:', error)
  } finally {
    isLoadingSystemTools.value = false
  }
}

const loadMcpTools = async () => {
  isLoadingMcpTools.value = true
  try {
    await toolConfigStore.loadMcpTools()
  } catch (error) {
    console.error('加载MCP工具失败:', error)
  } finally {
    isLoadingMcpTools.value = false
  }
}

const testTool = async (toolName: string, toolType: string) => {
  try {
    await toolConfigStore.testTool(toolName, toolType)
  } catch (error) {
    console.error('测试工具失败:', error)
  }
}

const showToolInfo = (toolName: string, toolType: string) => {
  const tool = toolType === 'system' 
    ? systemTools.value.find(t => t.name === toolName)
    : mcpTools.value.find(t => t.name === toolName)
  
  if (tool) {
    const paramInfo = tool.parameters && tool.parameters.length > 0
      ? tool.parameters.map(p => `  - ${p.name} (${p.type}): ${p.description}`).join('\n')
      : '  无参数'
      
    alert(`工具详情:\n\n名称: ${tool.name}\n显示名: ${tool.displayName || tool.name}\n描述: ${tool.description}\n类型: ${tool.type}\n来源: ${tool.source}\n状态: ${tool.enabled ? '启用' : '禁用'}\n\n参数:\n${paramInfo}`)
  }
}

const toggleTool = async (toolName: string, enabled: boolean) => {
  try {
    await toolConfigStore.toggleTool(toolName, enabled)
  } catch (error) {
    console.error('切换工具状态失败:', error)
  }
}

const removeTool = async (toolName: string) => {
  if (confirm(`确定要删除工具 "${toolName}" 吗？`)) {
    try {
      await toolConfigStore.removeTool(toolName)
    } catch (error) {
      console.error('删除工具失败:', error)
    }
  }
}

const handleServerAdded = () => {
  // 服务器添加成功后，重新加载MCP工具
  loadMcpTools()
  // 切换到MCP工具标签页
  activeTab.value = 'mcp-tools'
}

// Lifecycle
onMounted(() => {
  loadSystemTools()
  loadMcpTools()
})
</script>

<style scoped>
.tool-config-modal {
  @apply fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4;
}

.modal-content {
  animation: modalSlideIn 0.3s ease-out;
}

@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: translateY(-20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.tab-content {
  animation: fadeIn 0.2s ease-in-out;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>

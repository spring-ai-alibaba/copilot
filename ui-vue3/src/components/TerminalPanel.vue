<template>
  <div class="terminal-panel h-full flex flex-col bg-black">
    <!-- 终端工具栏 -->
    <div class="terminal-toolbar flex items-center justify-between px-4 py-2 bg-gray-800 border-b border-gray-700">
      <div class="flex items-center space-x-2">
        <span class="text-sm font-medium text-gray-300">终端</span>
        <div class="flex items-center space-x-1">
          <div class="w-2 h-2 bg-green-500 rounded-full"></div>
          <span class="text-xs text-gray-400">就绪</span>
        </div>
      </div>
      
      <div class="flex items-center space-x-2">
        <!-- 终端标签 -->
        <div class="flex items-center space-x-1">
          <div
            v-for="(terminal, index) in terminals"
            :key="terminal.id"
            class="terminal-tab px-3 py-1 text-xs rounded cursor-pointer transition-colors"
            :class="{
              'bg-gray-700 text-white': terminal.id === activeTerminalId,
              'bg-gray-600 text-gray-300 hover:bg-gray-700': terminal.id !== activeTerminalId
            }"
            @click="switchTerminal(terminal.id)"
          >
            终端 {{ index + 1 }}
            <button
              v-if="terminals.length > 1"
              class="ml-2 hover:text-red-400"
              @click.stop="closeTerminal(terminal.id)"
            >
              ×
            </button>
          </div>
        </div>
        
        <!-- 控制按钮 -->
        <a-button size="small" @click="addTerminal" title="新建终端">
          <template #icon>
            <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd"/>
            </svg>
          </template>
        </a-button>
        
        <a-button size="small" @click="clearTerminal" title="清空">
          <template #icon>
            <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
            </svg>
          </template>
        </a-button>
      </div>
    </div>

    <!-- 终端内容 -->
    <div class="terminal-content flex-1 relative">
      <div
        v-for="terminal in terminals"
        :key="terminal.id"
        :ref="el => setTerminalRef(terminal.id, el)"
        class="terminal-instance absolute inset-0"
        :style="{ display: terminal.id === activeTerminalId ? 'block' : 'none' }"
      />
      
      <!-- 空状态 -->
      <div
        v-if="terminals.length === 0"
        class="absolute inset-0 flex flex-col items-center justify-center text-gray-400"
      >
        <svg class="w-16 h-16 mb-4" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M2 5a2 2 0 012-2h12a2 2 0 012 2v10a2 2 0 01-2 2H4a2 2 0 01-2-2V5zm3.293 1.293a1 1 0 011.414 0l3 3a1 1 0 010 1.414l-3 3a1 1 0 01-1.414-1.414L7.586 10 5.293 7.707a1 1 0 010-1.414zM11 12a1 1 0 100 2h3a1 1 0 100-2h-3z" clip-rule="evenodd"/>
        </svg>
        <p>点击"+"创建新终端</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { WebLinksAddon } from '@xterm/addon-web-links'
import '@xterm/xterm/css/xterm.css'

interface TerminalInstance {
  id: string
  terminal: Terminal
  fitAddon: FitAddon
  process?: any
}

interface Props {
  containerInstance?: any
}

interface Emits {
  (e: 'command-output', output: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 状态
const terminals = ref<TerminalInstance[]>([])
const activeTerminalId = ref<string>('')
const terminalRefs = ref<Map<string, HTMLElement>>(new Map())

// 设置终端引用
const setTerminalRef = (id: string, el: any) => {
  if (el) {
    terminalRefs.value.set(id, el)
  }
}

// 创建新终端
const addTerminal = async () => {
  const id = `terminal-${Date.now()}`
  
  const terminal = new Terminal({
    theme: {
      background: '#000000',
      foreground: '#ffffff',
      cursor: '#ffffff',
      selection: 'rgba(255, 255, 255, 0.3)',
      black: '#000000',
      red: '#cd3131',
      green: '#0dbc79',
      yellow: '#e5e510',
      blue: '#2472c8',
      magenta: '#bc3fbc',
      cyan: '#11a8cd',
      white: '#e5e5e5',
      brightBlack: '#666666',
      brightRed: '#f14c4c',
      brightGreen: '#23d18b',
      brightYellow: '#f5f543',
      brightBlue: '#3b8eea',
      brightMagenta: '#d670d6',
      brightCyan: '#29b8db',
      brightWhite: '#e5e5e5'
    },
    fontSize: 14,
    fontFamily: '"Fira Code", "JetBrains Mono", "Monaco", "Consolas", monospace',
    cursorBlink: true,
    cursorStyle: 'block',
    scrollback: 1000,
    tabStopWidth: 4
  })

  const fitAddon = new FitAddon()
  const webLinksAddon = new WebLinksAddon()
  
  terminal.loadAddon(fitAddon)
  terminal.loadAddon(webLinksAddon)

  const terminalInstance: TerminalInstance = {
    id,
    terminal,
    fitAddon
  }

  terminals.value.push(terminalInstance)
  activeTerminalId.value = id

  await nextTick()
  
  const element = terminalRefs.value.get(id)
  if (element) {
    terminal.open(element)
    fitAddon.fit()
    
    // 初始化终端
    await initTerminal(terminalInstance)
  }
}

// 初始化终端
const initTerminal = async (terminalInstance: TerminalInstance) => {
  const { terminal } = terminalInstance
  
  try {
    if (props.containerInstance) {
      // 如果有容器实例，启动 shell
      const shellProcess = await props.containerInstance.spawn('sh', [], {
        terminal: {
          cols: terminal.cols,
          rows: terminal.rows
        }
      })
      
      terminalInstance.process = shellProcess
      
      // 处理输入
      terminal.onData((data) => {
        shellProcess.input.getWriter().write(new TextEncoder().encode(data))
      })
      
      // 处理输出
      const reader = shellProcess.output.getReader()
      const readOutput = async () => {
        try {
          while (true) {
            const { done, value } = await reader.read()
            if (done) break
            
            const output = new TextDecoder().decode(value)
            terminal.write(output)
            emit('command-output', output)
          }
        } catch (error) {
          console.error('Terminal output error:', error)
        }
      }
      
      readOutput()
      
      // 处理调整大小
      terminal.onResize(({ cols, rows }) => {
        // WebContainer 可能不支持调整终端大小
        console.log('Terminal resized:', { cols, rows })
      })
      
    } else {
      // 模拟终端
      terminal.writeln('Welcome to Spring-AI-Alibaba-Copilot Terminal')
      terminal.writeln('WebContainer not initialized. Please initialize container first.')
      terminal.write('$ ')
      
      let currentLine = ''
      
      terminal.onData((data) => {
        const char = data
        
        if (char === '\r') {
          // 回车键
          terminal.writeln('')
          if (currentLine.trim()) {
            handleCommand(terminal, currentLine.trim())
          }
          currentLine = ''
          terminal.write('$ ')
        } else if (char === '\u007f') {
          // 退格键
          if (currentLine.length > 0) {
            currentLine = currentLine.slice(0, -1)
            terminal.write('\b \b')
          }
        } else if (char >= ' ') {
          // 可打印字符
          currentLine += char
          terminal.write(char)
        }
      })
    }
  } catch (error) {
    console.error('Failed to initialize terminal:', error)
    terminal.writeln('Failed to initialize terminal')
    terminal.write('$ ')
  }
}

// 处理命令（模拟模式）
const handleCommand = (terminal: Terminal, command: string) => {
  switch (command) {
    case 'clear':
      terminal.clear()
      break
    case 'help':
      terminal.writeln('Available commands:')
      terminal.writeln('  clear  - Clear terminal')
      terminal.writeln('  help   - Show this help')
      terminal.writeln('  ls     - List files')
      break
    case 'ls':
      terminal.writeln('package.json  index.html  main.js')
      break
    default:
      terminal.writeln(`Command not found: ${command}`)
      break
  }
}

// 切换终端
const switchTerminal = (id: string) => {
  activeTerminalId.value = id
  
  // 重新调整大小
  nextTick(() => {
    const terminalInstance = terminals.value.find(t => t.id === id)
    if (terminalInstance) {
      terminalInstance.fitAddon.fit()
    }
  })
}

// 关闭终端
const closeTerminal = (id: string) => {
  const index = terminals.value.findIndex(t => t.id === id)
  if (index > -1) {
    const terminalInstance = terminals.value[index]
    
    // 清理资源
    if (terminalInstance.process) {
      try {
        terminalInstance.process.kill()
      } catch (error) {
        console.error('Failed to kill process:', error)
      }
    }
    
    terminalInstance.terminal.dispose()
    terminals.value.splice(index, 1)
    terminalRefs.value.delete(id)
    
    // 如果关闭的是当前活动终端，切换到其他终端
    if (activeTerminalId.value === id && terminals.value.length > 0) {
      activeTerminalId.value = terminals.value[0].id
    } else if (terminals.value.length === 0) {
      activeTerminalId.value = ''
    }
  }
}

// 清空当前终端
const clearTerminal = () => {
  const activeTerminal = terminals.value.find(t => t.id === activeTerminalId.value)
  if (activeTerminal) {
    activeTerminal.terminal.clear()
  }
}

// 处理窗口大小变化
const handleResize = () => {
  terminals.value.forEach(terminalInstance => {
    if (terminalInstance.id === activeTerminalId.value) {
      terminalInstance.fitAddon.fit()
    }
  })
}

// 组件挂载
onMounted(() => {
  // 创建第一个终端
  addTerminal()
  
  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)
})

// 组件卸载
onUnmounted(() => {
  // 清理所有终端
  terminals.value.forEach(terminalInstance => {
    if (terminalInstance.process) {
      try {
        terminalInstance.process.kill()
      } catch (error) {
        console.error('Failed to kill process:', error)
      }
    }
    terminalInstance.terminal.dispose()
  })
  
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.terminal-panel {
  font-family: 'Fira Code', 'JetBrains Mono', 'Monaco', 'Consolas', monospace;
}

.terminal-tab {
  font-size: 12px;
  white-space: nowrap;
}

.terminal-instance {
  padding: 8px;
}

/* xterm.js 样式覆盖 */
:deep(.xterm) {
  height: 100% !important;
}

:deep(.xterm-viewport) {
  overflow-y: auto;
}

:deep(.xterm-screen) {
  height: 100% !important;
}
</style>

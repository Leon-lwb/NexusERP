<template>
  <el-drawer v-model="visible" title="NexusERP 智能助手" direction="rtl" size="450px" :before-close="handleClose">
    <div class="ai-chat-container">
      <!-- 消息列表 -->
      <div ref="messageListRef" class="message-list">
        <div v-if="messages.length === 0" class="welcome-hint">
          <el-icon :size="40" color="#409eff"><ChatDotRound /></el-icon>
          <p>基于 AI 的 ERP 数据查询与智能对话</p>
        </div>
        <div v-for="(msg, index) in messages" :key="index" :class="['message-item', msg.role === 'user' ? 'user-message' : 'ai-message']">
          <div class="message-content">
            <!-- 推理过程（DeepSeek-R1 等，折叠展示） -->
            <details v-if="msg.reasoning && msg.role === 'assistant'" class="reasoning-details">
              <summary>思考过程</summary>
              <div class="reasoning-content">{{ msg.reasoning }}</div>
            </details>
            <div v-if="msg.role === 'assistant'" class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
            <div v-else>{{ msg.content }}</div>
          </div>
        </div>
        <div v-if="thinking" class="message-item ai-message">
          <div class="message-content thinking">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>AI 正在思考...</span>
          </div>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="input-area">
        <div class="mode-selector">
          <el-radio-group v-model="chatMode" size="small">
            <el-radio-button label="auto">自动</el-radio-button>
            <el-radio-button label="chat">对话</el-radio-button>
            <el-radio-button label="agent">数据查询</el-radio-button>
            <el-radio-button label="rag">知识库</el-radio-button>
          </el-radio-group>
        </div>
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          :placeholder="modePlaceholder"
          @keydown.enter.exact.prevent="sendMessage"
          :disabled="loading"
        />
        <div class="input-actions">
          <DeepThinking v-model="enableThinking" />
          <el-button type="primary" :icon="Promotion" @click="sendMessage" :loading="loading" style="flex: 1">
            发送
          </el-button>
          <el-button @click="newChat" :icon="RefreshRight" />
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { Promotion, Loading, ChatDotRound, RefreshRight } from '@element-plus/icons-vue'
import { sendAiMessage } from '@/api/ai/chat'
import { marked } from 'marked'
import { createAiRenderer } from '@/utils/aiRenderers'
import DeepThinking from '@/components/ai/DeepThinking.vue'
import * as echarts from 'echarts'
import 'github-markdown-css'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  /** 推理过程内容（DeepSeek-R1 等推理模型的思考链） */
  reasoning?: string
}

const visible = ref(false)
const inputText = ref('')
const messages = ref<ChatMessage[]>([])
const thinking = ref(false)
const loading = ref(false)
const messageListRef = ref<HTMLElement>()
const sessionId = ref<number | null>(null)
const chatMode = ref('auto')
const enableThinking = ref(false)

/** 根据当前模式显示不同的输入提示 */
const modePlaceholder = computed(() => {
  switch (chatMode.value) {
    case 'agent': return '输入数据查询需求，如：查询本月采购订单总金额'
    case 'rag': return '输入知识检索问题，如：如何创建采购订单'
    case 'chat': return '输入任意问题，与 AI 自由对话'
    default: return '输入问题，AI 将自动选择最佳模式'
  }
})

function onAiSseMessage(event: Event) {
  const detail = (event as CustomEvent).detail
  if (!detail) return

  switch (detail.type) {
    case 'AI_THINKING':
      thinking.value = true
      break
    case 'AI_CONTENT_INCREMENT':
      // 流式 Token 追加模式
      thinking.value = false
      const incMsg = messages.value[messages.value.length - 1]
      if (incMsg && incMsg.role === 'assistant') {
        incMsg.content += detail.content
      } else {
        messages.value.push({ role: 'assistant', content: detail.content })
      }
      nextTick(() => scrollToBottom())
      break
    case 'AI_CONTENT':
      thinking.value = false
      loading.value = false
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        lastMsg.content = detail.content
      } else {
        messages.value.push({ role: 'assistant', content: detail.content })
      }
      nextTick(() => scrollToBottom())
      break
    case 'AI_DONE':
      thinking.value = false
      loading.value = false
      // 扫描并挂载 ECharts 容器
      nextTick(() => mountEchartsContainers())
      break
    case 'AI_ERROR':
      thinking.value = false
      loading.value = false
      messages.value.push({ role: 'assistant', content: `[错误] ${detail.content}` })
      break
    case 'AI_CHART':
      break
    case 'AI_THINKING_TOKEN': {
      // 推理 token 流（DeepSeek-R1 等），追加到当前 assistant 消息的 reasoning 字段
      const reasonMsg = messages.value[messages.value.length - 1]
      if (reasonMsg && reasonMsg.role === 'assistant') {
        reasonMsg.reasoning = (reasonMsg.reasoning || '') + detail.content
      } else {
        messages.value.push({ role: 'assistant', content: '', reasoning: detail.content })
      }
      nextTick(() => scrollToBottom())
      break
    }
  }
}

function renderMarkdown(content: string) {
  const renderer = createAiRenderer()
  return marked.parse(content || '', { renderer })
}

/** 扫描 DOM 中的 echarts-container 并挂载 ECharts 实例 */
function mountEchartsContainers() {
  if (!messageListRef.value) return
  const containers = messageListRef.value.querySelectorAll('.echarts-container:not([data-mounted])')
  containers.forEach((el) => {
    const optionStr = (el as HTMLElement).dataset.option
    if (!optionStr) return
    try {
      const option = JSON.parse(decodeURIComponent(optionStr))
      el.setAttribute('data-mounted', 'true')
      const chart = echarts.init(el as HTMLElement)
      chart.setOption(option, true)
      const ro = new ResizeObserver(() => chart.resize())
      ro.observe(el as HTMLElement)
    } catch (e) {
      console.error('ECharts 容器挂载失败:', e)
    }
  })
}

function scrollToBottom() {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true

  await nextTick()
  scrollToBottom()

  try {
    const res = await sendAiMessage({
      content: text,
      sessionId: sessionId.value ?? undefined,
      mode: chatMode.value === 'auto' ? undefined : chatMode.value,
      enableThinking: enableThinking.value || undefined
    })
    const sid = res?.data ?? res
    if (sid && typeof sid === 'number') {
      sessionId.value = sid
    }
  } catch (e: any) {
    loading.value = false
    thinking.value = false
    messages.value.push({ role: 'assistant', content: `[请求失败] ${e.message}` })
  }
}

function newChat() {
  sessionId.value = null
  messages.value = []
  thinking.value = false
  loading.value = false
}

function handleClose() {
  visible.value = false
}

function open() {
  visible.value = true
}

defineExpose({ open })

onMounted(() => {
  window.addEventListener('ai-sse-message', onAiSseMessage)
})

onUnmounted(() => {
  window.removeEventListener('ai-sse-message', onAiSseMessage)
})
</script>

<style scoped lang="scss">
.ai-chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0 16px;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
}

.welcome-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;

  p {
    margin-top: 12px;
    font-size: 13px;
  }
}

.message-item {
  margin-bottom: 16px;
  display: flex;

  &.user-message {
    justify-content: flex-end;

    .message-content {
      background-color: #409eff;
      color: white;
      border-radius: 12px 12px 0 12px;
      max-width: 80%;
    }
  }

  &.ai-message {
    justify-content: flex-start;

    .message-content {
      background-color: #f4f4f5;
      color: #303133;
      border-radius: 12px 12px 12px 0;
      max-width: 85%;
    }
  }
}

.message-content {
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;

  &.thinking {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #909399;
  }

  :deep(.markdown-body) {
    background: transparent;
    font-size: 14px;

    table {
      border-collapse: collapse;
      width: 100%;

      th, td {
        border: 1px solid #dcdfe6;
        padding: 6px 10px;
        text-align: left;
      }

      th {
        background-color: #f5f7fa;
      }
    }
  }
}

/* 推理过程（DeepSeek-R1 等） */
.reasoning-details {
  margin-bottom: 6px;
  border: 1px solid #e0e6ed;
  border-radius: 6px;
  overflow: hidden;

  summary {
    padding: 6px 10px;
    font-size: 12px;
    color: #909399;
    cursor: pointer;
    background: #fafafa;
    user-select: none;

    &:hover {
      background: #f0f0f0;
    }
  }

  .reasoning-content {
    padding: 8px 10px;
    font-size: 12px;
    color: #606266;
    line-height: 1.6;
    max-height: 200px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.input-area {
  padding: 12px 0;
  border-top: 1px solid #e4e7ed;

  .mode-selector {
    margin-bottom: 8px;

    :deep(.el-radio-group) {
      display: flex;
      width: 100%;
    }

    :deep(.el-radio-button) {
      flex: 1;

      .el-radio-button__inner {
        width: 100%;
        font-size: 12px;
        padding: 5px 0;
      }
    }
  }

  .input-actions {
    display: flex;
    gap: 8px;
    margin-top: 8px;
  }
}
</style>

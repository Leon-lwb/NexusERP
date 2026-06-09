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
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="输入问题，如：查询本月采购订单总金额"
          @keydown.enter.exact.prevent="sendMessage"
          :disabled="loading"
        />
        <div class="input-actions">
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
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { Promotion, Loading, ChatDotRound, RefreshRight } from '@element-plus/icons-vue'
import { sendAiMessage } from '@/api/ai/chat'
import { marked } from 'marked'
import 'github-markdown-css'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

const visible = ref(false)
const inputText = ref('')
const messages = ref<ChatMessage[]>([])
const thinking = ref(false)
const loading = ref(false)
const messageListRef = ref<HTMLElement>()
const sessionId = ref<number | null>(null)

function onAiSseMessage(event: Event) {
  const detail = (event as CustomEvent).detail
  if (!detail) return

  switch (detail.type) {
    case 'AI_THINKING':
      thinking.value = true
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
      break
    case 'AI_ERROR':
      thinking.value = false
      loading.value = false
      messages.value.push({ role: 'assistant', content: `[错误] ${detail.content}` })
      break
    case 'AI_CHART':
      break
  }
}

function renderMarkdown(content: string) {
  return marked.parse(content || '')
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
    const res = await sendAiMessage({ content: text, sessionId: sessionId.value ?? undefined })
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

.input-area {
  padding: 12px 0;
  border-top: 1px solid #e4e7ed;

  .input-actions {
    display: flex;
    gap: 8px;
    margin-top: 8px;
  }
}
</style>

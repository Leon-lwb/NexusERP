<template>
  <div class="app-container ai-chat-page">
    <div class="chat-layout">
      <!-- 左侧会话列表 -->
      <div class="session-sidebar">
        <div class="sidebar-header">
          <span class="sidebar-title">对话记录</span>
          <el-button type="primary" :icon="Plus" size="small" @click="newSession">新对话</el-button>
        </div>
        <div class="session-list">
          <div v-if="sessionLoading" class="session-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>
          <template v-else>
            <div
              v-for="session in sessions"
              :key="session.id"
              :class="['session-item', { active: session.id === currentSessionId }]"
              @click="switchSession(session.id)"
              @contextmenu.prevent="showContextMenu($event, session)"
            >
              <el-icon class="session-icon"><ChatDotRound /></el-icon>
              <span class="session-title">{{ session.title }}</span>
              <span class="session-time">{{ formatTime(session.createTime) }}</span>
            </div>
            <el-empty v-if="sessions.length === 0" description="暂无对话" :image-size="60" />
          </template>
        </div>
      </div>

      <!-- 右侧聊天区域 -->
      <el-card class="chat-card" shadow="never">
        <!-- 消息列表 -->
        <div ref="messageListRef" class="message-list">
          <div v-if="messages.length === 0" class="welcome-area">
            <el-icon :size="64" color="#409eff"><ChatDotRound /></el-icon>
            <h2>NexusERP 智能助手</h2>
            <p>基于 AI 的 ERP 数据查询与智能对话</p>
            <div class="quick-actions">
              <el-button v-for="(tip, idx) in quickTips" :key="idx" type="info" plain @click="sendQuick(tip)">
                {{ tip }}
              </el-button>
            </div>
          </div>

          <div v-for="(msg, index) in messages" :key="index" :class="['message-item', msg.role === 'user' ? 'user-message' : 'ai-message']">
            <div class="avatar">
              <el-avatar :size="36" :icon="msg.role === 'user' ? UserFilled : ChatDotRound" :style="{ background: msg.role === 'user' ? '#409eff' : '#67c23a' }" />
            </div>
            <div class="message-body">
              <div class="message-role">{{ msg.role === 'user' ? '我' : 'NexusERP AI' }}</div>
              <div class="message-content">
                <div v-if="msg.role === 'assistant'" class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
                <div v-else>{{ msg.content }}</div>
              </div>
            </div>
          </div>

          <div v-if="thinking" class="message-item ai-message">
            <div class="avatar">
              <el-avatar :size="36" :icon="ChatDotRound" :style="{ background: '#67c23a' }" />
            </div>
            <div class="message-body">
              <div class="message-role">NexusERP AI</div>
              <div class="message-content thinking">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>AI 正在思考...</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="输入问题，如：查询本月采购订单总金额、展示各仓库库存分布..."
            @keydown.enter.exact.prevent="sendMessage"
            :disabled="loading"
          />
          <div class="input-actions">
            <el-button type="primary" :icon="Promotion" @click="sendMessage" :loading="loading">
              发送
            </el-button>
            <el-button @click="clearMessages">
              清空对话
            </el-button>
          </div>
        </div>
      </el-card></div>

      <!-- 右键菜单 -->
      <div v-if="contextMenu.visible" class="context-menu" :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }">
        <div class="context-menu-item" @click="handleRename">
          <el-icon><Edit /></el-icon>
          <span>重命名</span>
        </div>
        <div class="context-menu-item danger" @click="handleDelete">
          <el-icon><Delete /></el-icon>
          <span>删除</span>
        </div>
      </div>

      <!-- 重命名弹窗 -->
      <el-dialog v-model="renameDialogVisible" title="重命名会话" width="400px" append-to-body>
        <el-input v-model="renameTitle" placeholder="请输入新标题" maxlength="50" show-word-limit />
        <template #footer>
          <el-button @click="renameDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmRename">确定</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { Promotion, Loading, ChatDotRound, UserFilled, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { sendAiMessage, getChatHistory, getChatSessions, deleteSession, renameSession, type AiMessage as AiMessageDto, type AiSession } from '@/api/ai/chat'
import { marked } from 'marked'
import { createAiRenderer } from '@/utils/aiRenderers'
import 'github-markdown-css'

/** 前端聊天消息类型 */
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

const inputText = ref('')
const messages = ref<ChatMessage[]>([])
const thinking = ref(false)
const loading = ref(false)
const sessionLoading = ref(false)
const messageListRef = ref<HTMLElement>()
const sessions = ref<AiSession[]>([])
const currentSessionId = ref<number | null>(null)

/** 右键菜单状态 */
const contextMenu = ref({ visible: false, x: 0, y: 0, session: null as AiSession | null })
const renameDialogVisible = ref(false)
const renameTitle = ref('')

const quickTips = [
  '查询所有业务表',
  '本月采购订单总金额',
  '各仓库库存总量',
  '生产工单完成情况'
]

/**
 * AI SSE 消息监听器
 */
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
      // Agent 模式完整替换
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
      // 刷新会话列表（新会话可能已创建）
      loadSessions()
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
  }
}

/** 渲染 Markdown（含 ECharts 代码块识别） */
function renderMarkdown(content: string) {
  const renderer = createAiRenderer()
  return marked.parse(content || '', { renderer: renderer as any })
}

/** 滚动到底部 */
function scrollToBottom() {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

/** 加载会话列表 */
async function loadSessions() {
  sessionLoading.value = true
  try {
    const res = await getChatSessions()
    console.log('[AI] 会话列表响应:', res)
    // 兼容两种响应格式：R.ok 直接返回 data 字段，或 { rows: [...] }
    const list = res?.data ?? res?.rows ?? (Array.isArray(res) ? res : [])
    sessions.value = list
  } catch (e) {
    console.error('[AI] 加载会话列表失败', e)
  } finally {
    sessionLoading.value = false
  }
}

/** 切换会话 */
async function switchSession(sessionId: number) {
  if (currentSessionId.value === sessionId) return
  currentSessionId.value = sessionId
  messages.value = []
  loading.value = true
  try {
    const res = await getChatHistory(sessionId)
    console.log('[AI] 聊天历史响应:', res)
    const history: AiMessageDto[] = res?.data ?? res?.rows ?? (Array.isArray(res) ? res : [])
    messages.value = history.map(m => ({
      role: m.role === 'USER' ? 'user' as const : 'assistant' as const,
      content: m.content
    }))
    nextTick(() => scrollToBottom())
  } catch (e) {
    console.error('[AI] 加载聊天历史失败', e)
  } finally {
    loading.value = false
  }
}

/** 新建会话 */
function newSession() {
  currentSessionId.value = null
  messages.value = []
  thinking.value = false
  loading.value = false
}

/** 发送消息 */
async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true

  await nextTick()
  scrollToBottom()

  try {
    const res = await sendAiMessage({ content: text, sessionId: currentSessionId.value ?? undefined })
    console.log('[AI] 发送消息响应:', res)
    // 后端返回 sessionId（新会话时为新 ID）
    const sid = res?.data ?? res
    if (sid && typeof sid === 'number') {
      currentSessionId.value = sid
    }
  } catch (e: any) {
    loading.value = false
    thinking.value = false
    messages.value.push({ role: 'assistant', content: `[请求失败] ${e.message}` })
  }
}

/** 快捷提示 */
function sendQuick(tip: string) {
  inputText.value = tip
  sendMessage()
}

/** 清空当前对话 */
function clearMessages() {
  messages.value = []
  thinking.value = false
  loading.value = false
}

/** 格式化时间 */
function formatTime(time: string) {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  if (isToday) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

/** 显示右键菜单 */
function showContextMenu(event: MouseEvent, session: AiSession) {
  contextMenu.value = {
    visible: true,
    x: event.clientX,
    y: event.clientY,
    session
  }
}

/** 隐藏右键菜单 */
function hideContextMenu() {
  contextMenu.value.visible = false
}

/** 重命名会话 */
function handleRename() {
  if (!contextMenu.value.session) return
  renameTitle.value = contextMenu.value.session.title || ''
  renameDialogVisible.value = true
  hideContextMenu()
}

/** 确认重命名 */
async function confirmRename() {
  if (!contextMenu.value.session || !renameTitle.value.trim()) return
  try {
    await renameSession({ id: contextMenu.value.session.id, title: renameTitle.value.trim() })
    // 更新本地列表
    const s = sessions.value.find(item => item.id === contextMenu.value.session!.id)
    if (s) s.title = renameTitle.value.trim()
    renameDialogVisible.value = false
  } catch (e: any) {
    console.error('[AI] 重命名会话失败', e)
  }
}

/** 删除会话 */
async function handleDelete() {
  if (!contextMenu.value.session) return
  const session = contextMenu.value.session
  hideContextMenu()
  try {
    await ElMessageBox.confirm('确定删除该会话及其所有消息？', '提示', { type: 'warning' })
    await deleteSession([session.id])
    sessions.value = sessions.value.filter(s => s.id !== session.id)
    if (currentSessionId.value === session.id) {
      newSession()
    }
  } catch {
    // 用户取消
  }
}

import { ElMessageBox } from 'element-plus'

/** 点击空白处关闭右键菜单 */
function onGlobalClick() {
  hideContextMenu()
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
      // 标记已挂载，避免重复渲染
      el.setAttribute('data-mounted', 'true')
      const chart = echarts.init(el as HTMLElement)
      chart.setOption(option, true)
      // 监听容器大小变化
      const ro = new ResizeObserver(() => chart.resize())
      ro.observe(el as HTMLElement)
    } catch (e) {
      console.error('ECharts 容器挂载失败:', e)
    }
  })
}

import * as echarts from 'echarts'

onMounted(() => {
  window.addEventListener('ai-sse-message', onAiSseMessage)
  window.addEventListener('click', onGlobalClick)
  loadSessions()
})

onUnmounted(() => {
  window.removeEventListener('ai-sse-message', onAiSseMessage)
  window.removeEventListener('click', onGlobalClick)
})
</script>

<style scoped lang="scss">
.ai-chat-page {
  height: calc(100vh - 84px);
  padding: 10px;
}

.chat-layout {
  display: flex;
  height: 100%;
  gap: 10px;
}

/* 左侧会话列表 */
.session-sidebar {
  width: 240px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .sidebar-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    border-bottom: 1px solid #e4e7ed;

    .sidebar-title {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
    }
  }

  .session-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
  }

  .session-loading {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 20px;
    color: #909399;
    font-size: 13px;
  }

  .session-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 6px;
    cursor: pointer;
    transition: background-color 0.2s;
    margin-bottom: 2px;

    &:hover {
      background-color: #f5f7fa;
    }

    &.active {
      background-color: #ecf5ff;
      color: #409eff;
    }

    .session-icon {
      flex-shrink: 0;
      font-size: 16px;
      color: #909399;
    }

    .session-title {
      flex: 1;
      font-size: 13px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .session-time {
      flex-shrink: 0;
      font-size: 11px;
      color: #c0c4cc;
    }
  }
}

/* 右侧聊天区域 */
.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    height: 100%;
    display: flex;
    flex-direction: column;
    padding: 0;
  }
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.welcome-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #606266;

  h2 {
    margin: 16px 0 8px;
    font-size: 24px;
    color: #303133;
  }

  p {
    margin-bottom: 24px;
    font-size: 14px;
  }

  .quick-actions {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
    justify-content: center;

    .el-button {
      border-radius: 20px;
    }
  }
}

.message-item {
  display: flex;
  margin-bottom: 20px;
  gap: 12px;

  &.user-message {
    flex-direction: row-reverse;

    .message-body {
      align-items: flex-end;

      .message-content {
        background-color: #ecf5ff;
        color: #303133;
      }
    }
  }

  &.ai-message {
    .message-content {
      background-color: #f5f7fa;
      color: #303133;
    }
  }
}

.avatar {
  flex-shrink: 0;
}

.message-body {
  display: flex;
  flex-direction: column;
  max-width: 70%;
}

.message-role {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.message-content {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;

  &.thinking {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #909399;
  }
}

.input-area {
  border-top: 1px solid #e4e7ed;
  padding: 16px 20px;

  .el-textarea {
    margin-bottom: 12px;
  }
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.markdown-body {
  background: transparent;
  font-size: 14px;

  :deep(table) {
    border-collapse: collapse;
    width: 100%;
    margin: 8px 0;

    th, td {
      border: 1px solid #dcdfe6;
      padding: 6px 10px;
      text-align: left;
    }

    th {
      background-color: #f5f7fa;
    }
  }

  :deep(pre) {
    background-color: #f5f7fa;
    padding: 12px;
    border-radius: 4px;
    overflow-x: auto;
  }

  :deep(code) {
    background-color: #f5f7fa;
    padding: 2px 6px;
    border-radius: 3px;
    font-family: monospace;
  }
}

/* 右键菜单 */
.context-menu {
  position: fixed;
  z-index: 3000;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  padding: 4px 0;
  min-width: 120px;

  .context-menu-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    cursor: pointer;
    font-size: 13px;
    color: #606266;
    transition: background-color 0.2s;

    &:hover {
      background-color: #f5f7fa;
    }

    &.danger {
      color: #f56c6c;

      &:hover {
        background-color: #fef0f0;
      }
    }
  }
}
</style>

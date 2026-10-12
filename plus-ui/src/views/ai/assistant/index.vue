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
              <el-avatar
                :size="36"
                :icon="msg.role === 'user' ? UserFilled : ChatDotRound"
                :style="{ background: msg.role === 'user' ? '#409eff' : '#67c23a' }"
              />
            </div>
            <div class="message-body">
              <div class="message-role">{{ msg.role === 'user' ? '我' : 'NexusERP AI' }}</div>
              <!-- 推理过程（DeepSeek-R1 等推理模型，折叠展示） -->
              <details v-if="msg.reasoning && msg.role === 'assistant'" class="reasoning-details">
                <summary>思考过程</summary>
                <div class="reasoning-content">{{ msg.reasoning }}</div>
              </details>
              <!-- 联网搜索来源卡片 -->
              <div v-if="msg.webSources && msg.webSources.length > 0 && msg.role === 'assistant'" class="web-sources">
                <div class="web-sources-title">信息来源</div>
                <div v-for="(src, srcIdx) in msg.webSources" :key="srcIdx" class="web-source-item">
                  <el-icon><Link /></el-icon>
                  <a :href="src.url" target="_blank" rel="noopener noreferrer">{{ src.title || src.url }}</a>
                </div>
              </div>
              <!-- Agent 思考过程 -->
              <div v-if="msg.thinking && msg.role === 'assistant'" class="agent-thinking">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>{{ msg.thinking }}</span>
              </div>
              <!-- 工具调用过程 -->
              <div v-if="msg.toolCalls && msg.toolCalls.length > 0 && msg.role === 'assistant'" class="tool-calls">
                <div v-for="(tc, tcIdx) in msg.toolCalls" :key="tcIdx" :class="['tool-call-item', tc.status]">
                  <div class="tool-call-header">
                    <el-icon v-if="tc.status === 'running'" class="is-loading"><Loading /></el-icon>
                    <el-icon v-else><CircleCheck /></el-icon>
                    <span class="tool-name">{{ tc.toolName }}</span>
                    <span v-if="tc.status === 'completed'" class="tool-duration">{{ formatToolDuration(tc.duration) }}</span>
                  </div>
                  <div v-if="tc.resultSummary && tc.status === 'completed'" class="tool-result">
                    {{ truncateText(tc.resultSummary, 200) }}
                  </div>
                </div>
              </div>
              <!-- 消息内容 -->
              <div class="message-content">
                <div v-if="msg.role === 'assistant' && msg.content" class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
                <div v-else-if="msg.role === 'user'">{{ msg.content }}</div>
              </div>
              <!-- 反馈按钮（仅 AI 消息且有内容时显示） -->
              <div v-if="msg.role === 'assistant' && msg.content && !msg.content.startsWith('[错误]')" class="message-feedback">
                <span
                  v-for="d in ai_message_feedback"
                  :key="d.value"
                  :class="['feedback-btn', { active: msg.feedback === d.value }]"
                  @click="handleFeedback(msg, d.value)"
                  :title="d.label"
                  >{{ feedbackIcon(d.value) }}</span
                >
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
          <div class="input-toolbar">
            <div class="mode-tabs">
              <span v-for="m in modeOptions" :key="m.value" :class="['mode-tab', { active: chatMode === m.value }]" @click="chatMode = m.value">
                <el-icon v-if="m.icon" :size="14"><component :is="m.icon" /></el-icon>
                {{ m.label }}
              </span>
            </div>
            <div class="toolbar-right">
              <ModelSelect v-model="selectedModel" model-type="TEXT,VISION" placeholder="默认模型" style="width: 170px" />
              <span v-if="tokenUsage > 0" class="token-usage">{{ tokenUsage.toLocaleString() }} tokens</span>
              <DeepThinking v-model="enableThinking" />
              <WebSearchToggle v-model="enableWebSearch" />
              <el-tooltip content="MCP 工具选择" placement="top">
                <el-button size="small" :icon="SetUp" @click="openToolDrawer" plain round>
                  {{ selectedToolIds.length > 0 ? selectedToolIds.length + ' 个工具' : '工具' }}
                </el-button>
              </el-tooltip>
            </div>
          </div>
          <div v-if="chatMode === 'rag'" class="mb-2 flex flex-wrap items-center gap-2">
            <el-select
              v-model="selectedKnowledgeId"
              filterable
              remote
              clearable
              :remote-method="loadChatKnowledge"
              :debounce="0"
              :loading="knowledgeLoading"
              :disabled="loading || !canListKnowledge"
              placeholder="选择索引就绪的知识库，可按名称搜索"
              style="width: 320px"
            >
              <el-option v-for="knowledge in knowledgeOptions" :key="knowledge.id" :label="knowledge.label" :value="knowledge.id" />
            </el-select>
            <el-button :loading="knowledgeLoading" :disabled="loading || !canListKnowledge" @click="loadChatKnowledge()">刷新列表</el-button>
            <span v-if="knowledgeLoadError" class="text-red-500">{{ knowledgeLoadError }}</span>
            <span v-else class="text-gray-500">仅显示匹配的前100项；选库不等于已命中资料</span>
          </div>
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            :placeholder="modePlaceholder"
            @keydown.enter.exact.prevent="sendMessage"
            :disabled="loading"
          />
          <div class="input-actions">
            <el-button type="primary" :icon="Promotion" @click="sendMessage" :loading="loading"> 发送 </el-button>
            <el-button @click="clearMessages"> 清空对话 </el-button>
          </div>
        </div>
      </el-card>

      <!-- MCP 工具选择抽屉 -->
      <el-drawer v-model="toolDrawerVisible" title="MCP 工具选择" size="350px" :append-to-body="true">
        <p class="tool-drawer-tip">选择本次对话可使用的 MCP 外部工具，未选择时使用全部已启用工具。</p>
        <el-checkbox-group v-model="selectedToolIds">
          <div v-for="tool in availableTools" :key="tool.id" class="tool-item">
            <el-checkbox :value="tool.id">
              <span class="tool-name">{{ tool.displayName || tool.toolName }}</span>
              <span class="tool-desc">{{ tool.description }}</span>
            </el-checkbox>
          </div>
        </el-checkbox-group>
        <el-empty v-if="availableTools.length === 0" description="暂无可用的 MCP 工具" />
        <template #footer>
          <el-button
            @click="
              selectedToolIds = [];
              toolDrawerVisible = false;
            "
            >使用全部</el-button
          >
          <el-button type="primary" @click="toolDrawerVisible = false">确认</el-button>
        </template>
      </el-drawer>

      <!-- 右键菜单 -->
      <div v-if="contextMenu.visible" class="context-menu" :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }">
        <div class="context-menu-item" @click="handleExport">
          <el-icon><Download /></el-icon>
          <span>导出对话</span>
        </div>
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
      <el-dialog v-model="renameDialogVisible" title="重命名会话" width="500px" append-to-body>
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
import { ref, computed, watch, onMounted, onUnmounted, nextTick, getCurrentInstance, type Component } from 'vue';
import {
  Promotion,
  Loading,
  ChatDotRound,
  UserFilled,
  Plus,
  Edit,
  Delete,
  CircleCheck,
  Download,
  SetUp,
  DataAnalysis,
  Collection,
  MagicStick,
  Link
} from '@element-plus/icons-vue';
import {
  sendAiMessage,
  getChatHistory,
  getChatSessions,
  deleteSession,
  renameSession,
  messageFeedback,
  exportSession,
  getTokenUsage,
  type AiMessage as AiMessageDto,
  type AiSession
} from '@/api/ai/chat';
import { getAvailableMcpTools } from '@/api/ai/mcpTool';
import { listKnowledge } from '@/api/ai/knowledge';
import { checkPermi } from '@/utils/permission';
import { chatKnowledgeChoices, prepareChatKnowledgeRequest, type ChatKnowledgeChoice } from './knowledgeSelection';
import { formatToolDuration } from './toolDuration';
import { marked } from 'marked';
import * as echarts from 'echarts';
import { createAiRenderer, sanitizeOption, sanitizeHtml } from '@/utils/aiRenderers';
import { AI_CHAT_MODE_OPTIONS } from '../constants';
import DeepThinking from '@/components/ai/DeepThinking.vue';
import WebSearchToggle from '@/components/ai/WebSearchToggle.vue';
import ModelSelect from '@/components/ai/ModelSelect.vue';
import 'github-markdown-css';

const { proxy } = getCurrentInstance() as any;
const { ai_message_feedback } = toRefs<any>(proxy?.useDict('ai_message_feedback'));

/** 工具调用状态 */
interface ToolCallInfo {
  toolName: string;
  arguments?: string;
  duration?: number;
  resultSummary?: string;
  status: 'running' | 'completed';
}

/** 前端聊天消息类型 */
interface ChatMessage {
  id?: number | string;
  role: 'user' | 'assistant';
  content: string;
  toolCalls?: ToolCallInfo[];
  thinking?: string;
  /** 推理过程内容（DeepSeek-R1 等推理模型的思考链） */
  reasoning?: string;
  /** 联网搜索来源卡片 */
  webSources?: Array<{ title: string; url: string }>;
  feedback?: string; // LIKE / DISLIKE
}

const inputText = ref('');
const messages = ref<ChatMessage[]>([]);
const thinking = ref(false);
const loading = ref(false);
const sessionLoading = ref(false);
const messageListRef = ref<HTMLElement>();
const sessions = ref<AiSession[]>([]);
const currentSessionId = ref<number | string | null>(null);
const chatMode = ref('auto');
/** 只使用已授权列表中的明确知识库，不自动选首项或回退跨库检索。 */
const selectedKnowledgeId = ref('');
const knowledgeOptions = ref<ChatKnowledgeChoice[]>([]);
const knowledgeLoading = ref(false);
const knowledgeLoadError = ref('');
const canListKnowledge = computed(() => checkPermi(['ai:knowledge:list']));
let knowledgeRequestVersion = 0;

async function loadChatKnowledge(query = '') {
  const version = ++knowledgeRequestVersion;
  knowledgeLoading.value = true;
  knowledgeLoadError.value = '';
  if (!canListKnowledge.value) {
    knowledgeOptions.value = [];
    selectedKnowledgeId.value = '';
    knowledgeLoading.value = false;
    knowledgeLoadError.value = '当前账号没有知识库列表权限';
    return;
  }
  try {
    // 保留现有列表权限；仅显示匹配的前100项，可通过名称搜索，不假定全库完整。
    const response: any = await listKnowledge({ pageNum: 1, pageSize: 100, status: '0', knowledgeName: query || undefined });
    if (version !== knowledgeRequestVersion) return;
    knowledgeOptions.value = chatKnowledgeChoices(response?.rows);
    if (!knowledgeOptions.value.some((choice) => choice.id === selectedKnowledgeId.value)) selectedKnowledgeId.value = '';
  } catch {
    if (version !== knowledgeRequestVersion) return;
    knowledgeOptions.value = [];
    selectedKnowledgeId.value = '';
    knowledgeLoadError.value = '知识库加载失败，请点击重试';
  } finally {
    if (version === knowledgeRequestVersion) knowledgeLoading.value = false;
  }
}

watch(chatMode, (mode) => {
  if (mode === 'rag') void loadChatKnowledge();
});

/** 选中模型名称（为空使用默认模型），ModelSelect 组件会预选默认模型 */
const selectedModel = ref('');
const enableThinking = ref(false);
const enableWebSearch = ref(false);

/** 对话模式图标映射（模式枚举值/文案统一维护在 ai/constants.ts） */
const modeIcons: Record<string, Component> = {
  auto: MagicStick,
  chat: ChatDotRound,
  agent: DataAnalysis,
  rag: Collection
};
/** 对话模式选项 */
const modeOptions = AI_CHAT_MODE_OPTIONS.map((m) => ({ ...m, icon: modeIcons[m.value] }));

/** MCP 工具选择器状态 */
const toolDrawerVisible = ref(false);
const availableTools = ref<any[]>([]);
const selectedToolIds = ref<number[]>([]);

/** Token 用量 */
const tokenUsage = ref<number>(0);

/** 根据当前模式显示不同的输入提示 */
const modePlaceholder = computed(() => {
  switch (chatMode.value) {
    case 'agent':
      return '输入数据查询需求，如：查询本月采购订单总金额';
    case 'rag':
      return '输入知识检索问题，如：如何创建采购订单';
    case 'chat':
      return '输入任意问题，与 AI 自由对话';
    default:
      return '输入问题，AI 将自动选择最佳模式';
  }
});

/** 右键菜单状态 */
const contextMenu = ref({ visible: false, x: 0, y: 0, session: null as AiSession | null });
const renameDialogVisible = ref(false);
const renameTitle = ref('');

const quickTips = ['查询所有业务表', '本月采购订单总金额', '各仓库库存总量', '生产工单完成情况'];

/**
 * AI SSE 消息监听器
 */
function clearAssistantThinking() {
  const message = messages.value[messages.value.length - 1];
  if (message?.role === 'assistant') message.thinking = undefined;
}

function onAiSseMessage(event: Event) {
  const detail = (event as CustomEvent).detail;
  if (!detail) return;

  switch (detail.type) {
    case 'AI_THINKING':
      thinking.value = true;
      break;
    case 'AI_CONTENT_INCREMENT':
      clearAssistantThinking();
      // 流式 Token 追加模式
      thinking.value = false;
      const incMsg = messages.value[messages.value.length - 1];
      if (incMsg && incMsg.role === 'assistant') {
        incMsg.content += detail.content;
      } else {
        messages.value.push({ role: 'assistant', content: detail.content });
      }
      nextTick(() => scrollToBottom());
      break;
    case 'AI_CONTENT':
      clearAssistantThinking();
      // Agent 模式完整替换
      thinking.value = false;
      loading.value = false;
      const lastMsg = messages.value[messages.value.length - 1];
      if (lastMsg && lastMsg.role === 'assistant') {
        lastMsg.content = detail.content;
      } else {
        messages.value.push({ role: 'assistant', content: detail.content });
      }
      nextTick(() => scrollToBottom());
      break;
    case 'AI_DONE':
      clearAssistantThinking();
      thinking.value = false;
      loading.value = false;
      // 刷新会话列表（新会话可能已创建）
      loadSessions();
      // 扫描并挂载 ECharts 容器
      nextTick(() => mountEchartsContainers());
      break;
    case 'AI_ERROR':
      clearAssistantThinking();
      thinking.value = false;
      loading.value = false;
      messages.value.push({ role: 'assistant', content: `[错误] ${detail.content}` });
      break;
    case 'AI_CHART':
      break;
    case 'AI_IMAGE': {
      // 文生图结果：解析 {url, prompt}，追加 markdown 图片到当前 assistant 消息
      thinking.value = false;
      let imgInfo: { url?: string; prompt?: string } = {};
      try {
        imgInfo = JSON.parse(detail.content || '{}');
      } catch {
        /* ignore */
      }
      const imgMd = `\n\n![${imgInfo.prompt || 'image'}](${imgInfo.url})\n`;
      const imgMsg = messages.value[messages.value.length - 1];
      if (imgMsg && imgMsg.role === 'assistant') {
        imgMsg.content += imgMd;
      } else {
        messages.value.push({ role: 'assistant', content: imgMd.trim() });
      }
      loading.value = false;
      nextTick(() => scrollToBottom());
      break;
    }
    case 'AI_WEB_SEARCH': {
      // 联网搜索来源卡片：解析 {sources:[{title,url}]}，挂到当前 assistant 消息
      let srcInfo: { sources?: Array<{ title?: string; url?: string }> } = {};
      try {
        srcInfo = JSON.parse(detail.content || '{}');
      } catch {
        /* ignore */
      }
      const sources = (srcInfo.sources || []).map((s) => ({ title: s.title || s.url || '', url: s.url || '' }));
      const srcMsg = messages.value[messages.value.length - 1];
      if (srcMsg && srcMsg.role === 'assistant') {
        srcMsg.webSources = sources;
      } else {
        messages.value.push({ role: 'assistant', content: '', webSources: sources });
      }
      nextTick(() => scrollToBottom());
      break;
    }
    case 'AI_THINKING_TOKEN': {
      // 推理 token 流（DeepSeek-R1 等），追加到当前 assistant 消息的 reasoning 字段
      const reasonMsg = messages.value[messages.value.length - 1];
      if (reasonMsg && reasonMsg.role === 'assistant') {
        reasonMsg.reasoning = (reasonMsg.reasoning || '') + detail.content;
      } else {
        messages.value.push({ role: 'assistant', content: '', reasoning: detail.content });
      }
      nextTick(() => scrollToBottom());
      break;
    }
    case 'AI_TOOL_START': {
      // 工具调用开始
      thinking.value = false;
      let toolInfo: { toolName?: string; arguments?: string } = {};
      try {
        toolInfo = JSON.parse(detail.content || '{}');
      } catch {
        /* ignore */
      }
      const toolStartMsg = messages.value[messages.value.length - 1];
      if (toolStartMsg && toolStartMsg.role === 'assistant') {
        if (!toolStartMsg.toolCalls) toolStartMsg.toolCalls = [];
        toolStartMsg.toolCalls.push({
          toolName: toolInfo.toolName || 'unknown',
          arguments: toolInfo.arguments,
          status: 'running'
        });
      } else {
        messages.value.push({
          role: 'assistant',
          content: '',
          toolCalls: [
            {
              toolName: toolInfo.toolName || 'unknown',
              arguments: toolInfo.arguments,
              status: 'running'
            }
          ]
        });
      }
      nextTick(() => scrollToBottom());
      break;
    }
    case 'AI_TOOL_END': {
      // 工具调用完成
      let toolEndInfo: { toolName?: string; duration?: number; resultSummary?: string } = {};
      try {
        toolEndInfo = JSON.parse(detail.content || '{}');
      } catch {
        /* ignore */
      }
      // 查找最近一条包含该工具的 assistant 消息，更新状态
      const toolEndMsg = messages.value[messages.value.length - 1];
      if (toolEndMsg && toolEndMsg.role === 'assistant' && toolEndMsg.toolCalls) {
        const runningTool = [...toolEndMsg.toolCalls].reverse().find((tc) => tc.toolName === toolEndInfo.toolName && tc.status === 'running');
        if (runningTool) {
          runningTool.status = 'completed';
          runningTool.duration = toolEndInfo.duration;
          runningTool.resultSummary = toolEndInfo.resultSummary;
        }
      }
      nextTick(() => scrollToBottom());
      break;
    }
    case 'AI_AGENT_THINKING': {
      // 已结束的请求不再接收迟到的进度状态。
      if (!loading.value) break;
      // Agent 思考过程
      thinking.value = false;
      let thinkInfo: { agentName?: string; message?: string } = {};
      try {
        thinkInfo = JSON.parse(detail.content || '{}');
      } catch {
        /* ignore */
      }
      const thinkMsg = messages.value[messages.value.length - 1];
      if (thinkMsg && thinkMsg.role === 'assistant') {
        thinkMsg.thinking = thinkInfo.message || '';
      } else {
        messages.value.push({
          role: 'assistant',
          content: '',
          thinking: thinkInfo.message || ''
        });
      }
      nextTick(() => scrollToBottom());
      break;
    }
  }
}

/** 渲染 Markdown（含 ECharts 代码块识别 + XSS 净化） */
function renderMarkdown(content: string) {
  const renderer = createAiRenderer();
  const html = marked.parse(content || '', { renderer });
  return sanitizeHtml(typeof html === 'string' ? html : '');
}

/** 滚动到底部 */
function scrollToBottom() {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
  }
}

/** 加载会话列表 */
async function loadSessions() {
  sessionLoading.value = true;
  try {
    const res = await getChatSessions();
    // 兼容两种响应格式：R.ok 直接返回 data 字段，或 { rows: [...] }
    const list = res?.data ?? res?.rows ?? (Array.isArray(res) ? res : []);
    sessions.value = list;
  } catch (e) {
    console.error('[AI] 加载会话列表失败', e);
  } finally {
    sessionLoading.value = false;
  }
}

/** 切换会话 */
async function switchSession(sessionId: number | string) {
  if (currentSessionId.value === sessionId) return;
  currentSessionId.value = sessionId as any;
  messages.value = [];
  loading.value = true;
  try {
    const res = await getChatHistory(sessionId as any);
    // 兼容多种响应格式：R.ok(data)、{ rows: [...] }、直接数组
    let history: AiMessageDto[] = [];
    if (Array.isArray(res)) {
      history = res;
    } else if (Array.isArray(res?.data)) {
      history = res.data;
    } else if (Array.isArray(res?.rows)) {
      history = res.rows;
    } else if (res?.data?.rows && Array.isArray(res.data.rows)) {
      history = res.data.rows;
    }
    messages.value = history.map((m) => ({
      id: m.id,
      role: m.role === 'USER' ? ('user' as const) : ('assistant' as const),
      content: m.content,
      reasoning: (m as any).thinkingContent,
      feedback: (m as any).feedback
    }));
    nextTick(() => scrollToBottom());
  } catch (e: any) {
    console.error('[AI] 加载聊天历史失败', e);
    proxy?.$modal.msgError('加载聊天历史失败: ' + (e.message || e));
  } finally {
    loading.value = false;
  }
}

/** 新建会话 */
function newSession() {
  currentSessionId.value = null;
  messages.value = [];
  thinking.value = false;
  loading.value = false;
}

/** 发送消息 */
async function sendMessage() {
  const text = inputText.value.trim();
  if (!text || loading.value) return;

  const knowledgeRequest = prepareChatKnowledgeRequest(chatMode.value, selectedKnowledgeId.value, knowledgeOptions.value, {
    loading: knowledgeLoading.value,
    error: knowledgeLoadError.value,
    canList: canListKnowledge.value
  });
  if (knowledgeRequest.error) {
    proxy?.$modal.msgWarning(knowledgeRequest.error);
    return;
  }

  messages.value.push({ role: 'user', content: text });
  inputText.value = '';
  loading.value = true;

  await nextTick();
  scrollToBottom();

  try {
    const res = await sendAiMessage({
      content: text,
      sessionId: currentSessionId.value ?? undefined,
      mode: chatMode.value === 'auto' ? undefined : chatMode.value,
      knowledgeId: knowledgeRequest.knowledgeId,
      model: selectedModel.value || undefined,
      enableThinking: enableThinking.value || undefined,
      enableWebSearch: enableWebSearch.value || undefined,
      enabledToolIds: selectedToolIds.value.length > 0 ? selectedToolIds.value : undefined
    });
    // 后端返回 sessionId（新会话时为新 ID）
    const sid = res?.data ?? res;
    if (sid && (typeof sid === 'number' || typeof sid === 'string')) {
      currentSessionId.value = sid as any;
    }
  } catch (e: any) {
    loading.value = false;
    thinking.value = false;
    messages.value.push({ role: 'assistant', content: `[请求失败] ${e.message}` });
  }
}

/** 打开 MCP 工具选择器 */
async function openToolDrawer() {
  toolDrawerVisible.value = true;
  if (availableTools.value.length === 0) {
    try {
      const res = await getAvailableMcpTools();
      availableTools.value = res?.data ?? res ?? [];
    } catch (e) {
      console.error('[AI] 获取 MCP 工具列表失败', e);
    }
  }
}

/** 刷新 Token 用量 */
async function refreshTokenUsage() {
  if (!currentSessionId.value) return;
  try {
    const res = await getTokenUsage(currentSessionId.value as number);
    tokenUsage.value = res?.data?.totalTokens ?? 0;
  } catch {
    // 忽略
  }
}

/** 快捷提示 */
function sendQuick(tip: string) {
  inputText.value = tip;
  sendMessage();
}

/** 清空当前对话 */
function clearMessages() {
  messages.value = [];
  thinking.value = false;
  loading.value = false;
}

/** 格式化时间 */
function formatTime(time: string) {
  if (!time) return '';
  const d = new Date(time);
  const now = new Date();
  const isToday = d.toDateString() === now.toDateString();
  if (isToday) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
}

/** 截断文本 */
function truncateText(text: string, maxLen: number) {
  if (!text) return '';
  return text.length > maxLen ? text.substring(0, maxLen) + '...' : text;
}

/** 反馈按钮图标（选项集来自字典 ai_message_feedback，此处仅按值映射展示图标） */
function feedbackIcon(value: string) {
  return value === 'DISLIKE' ? '\u{1F44E}' : '\u{1F44D}';
}

/** 消息反馈 */
async function handleFeedback(msg: ChatMessage, feedback: string) {
  if (!msg.id) {
    // 新消息尚未保存到后端，提示用户刷新后操作
    proxy?.$modal.msgInfo('请等待消息保存后再反馈');
    return;
  }
  // 如果点击已选中的反馈，则取消
  const newFeedback = msg.feedback === feedback ? 'NONE' : feedback;
  try {
    await messageFeedback(msg.id, newFeedback);
    msg.feedback = newFeedback === 'NONE' ? undefined : newFeedback;
  } catch (e: any) {
    console.error('[AI] 反馈失败', e);
    proxy?.$modal.msgError('反馈提交失败');
  }
}

/** 显示右键菜单 */
function showContextMenu(event: MouseEvent, session: AiSession) {
  contextMenu.value = {
    visible: true,
    x: event.clientX,
    y: event.clientY,
    session
  };
}

/** 隐藏右键菜单 */
function hideContextMenu() {
  contextMenu.value.visible = false;
}

/** 重命名会话 */
function handleRename() {
  if (!contextMenu.value.session) return;
  renameTitle.value = contextMenu.value.session.title || '';
  renameDialogVisible.value = true;
  hideContextMenu();
}

/** 确认重命名 */
async function confirmRename() {
  if (!contextMenu.value.session || !renameTitle.value.trim()) return;
  try {
    await renameSession({ id: contextMenu.value.session.id, title: renameTitle.value.trim() });
    // 更新本地列表
    const s = sessions.value.find((item) => item.id === contextMenu.value.session!.id);
    if (s) s.title = renameTitle.value.trim();
    renameDialogVisible.value = false;
  } catch (e: any) {
    console.error('[AI] 重命名会话失败', e);
  }
}

/** 导出会话为 Markdown 文件 */
async function handleExport() {
  if (!contextMenu.value.session) return;
  const session = contextMenu.value.session;
  hideContextMenu();
  try {
    const res = await exportSession(session.id);
    const blob = new Blob([res], { type: 'text/markdown;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${session.title || 'AI对话'}.md`;
    link.click();
    window.URL.revokeObjectURL(url);
    proxy?.$modal.msgSuccess('导出成功');
  } catch (e: any) {
    console.error('[AI] 导出会话失败', e);
    proxy?.$modal.msgError('导出失败');
  }
}

/** 删除会话 */
async function handleDelete() {
  if (!contextMenu.value.session) return;
  const session = contextMenu.value.session;
  hideContextMenu();
  try {
    await proxy?.$modal.confirm('确定删除该会话及其所有消息？');
    await deleteSession([session.id]);
    sessions.value = sessions.value.filter((s) => s.id !== session.id);
    if (currentSessionId.value === session.id) {
      newSession();
    }
  } catch {
    // 用户取消
  }
}

/** 点击空白处关闭右键菜单 */
function onGlobalClick() {
  hideContextMenu();
}

/** 跟踪 ECharts 实例和 ResizeObserver，用于组件卸载时清理 */
const echartsInstances: echarts.ECharts[] = [];
const echartsResizeObservers: ResizeObserver[] = [];

/** 扫描 DOM 中的 echarts-container 并挂载 ECharts 实例 */
function mountEchartsContainers() {
  if (!messageListRef.value) return;
  const containers = messageListRef.value.querySelectorAll('.echarts-container:not([data-mounted])');
  containers.forEach((el) => {
    const optionStr = (el as HTMLElement).dataset.option;
    if (!optionStr) return;
    try {
      const option = JSON.parse(decodeURIComponent(optionStr));
      // 安全过滤：移除可能包含 JavaScript 函数的字段
      sanitizeOption(option);
      // 标记已挂载，避免重复渲染
      el.setAttribute('data-mounted', 'true');
      const chart = echarts.init(el as HTMLElement);
      chart.setOption(option, true);
      echartsInstances.push(chart);
      // 监听容器大小变化
      const ro = new ResizeObserver(() => chart.resize());
      ro.observe(el as HTMLElement);
      echartsResizeObservers.push(ro);
    } catch (e) {
      console.error('ECharts 容器挂载失败:', e);
    }
  });
}

onMounted(() => {
  window.addEventListener('ai-sse-message', onAiSseMessage);
  window.addEventListener('click', onGlobalClick);
  loadSessions();
});

onUnmounted(() => {
  knowledgeRequestVersion++;
  window.removeEventListener('ai-sse-message', onAiSseMessage);
  window.removeEventListener('click', onGlobalClick);
  // 清理 ECharts 实例和 ResizeObserver，防止内存泄漏
  echartsResizeObservers.forEach((ro) => ro.disconnect());
  echartsResizeObservers.length = 0;
  echartsInstances.forEach((chart) => chart.dispose());
  echartsInstances.length = 0;
});
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

/* 联网搜索来源卡片 */
.web-sources {
  margin-bottom: 8px;
  padding: 8px 12px;
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  border-radius: 8px;

  .web-sources-title {
    font-size: 12px;
    color: #67c23a;
    font-weight: 600;
    margin-bottom: 4px;
  }

  .web-source-item {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    padding: 2px 0;

    a {
      color: #409eff;
      text-decoration: none;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      max-width: 100%;

      &:hover {
        text-decoration: underline;
      }
    }
  }
}

/* 推理过程（DeepSeek-R1 等） */
.reasoning-details {
  margin-bottom: 8px;
  border: 1px solid #e0e6ed;
  border-radius: 8px;
  overflow: hidden;

  summary {
    padding: 8px 12px;
    font-size: 13px;
    color: #909399;
    cursor: pointer;
    background: #fafafa;
    user-select: none;

    &:hover {
      background: #f0f0f0;
    }
  }

  .reasoning-content {
    padding: 10px 12px;
    font-size: 13px;
    color: #606266;
    line-height: 1.6;
    max-height: 400px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

/* Agent 思考过程 */
.agent-thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: #fdf6ec;
  border-radius: 8px;
  font-size: 13px;
  color: #e6a23c;

  .el-icon {
    font-size: 14px;
  }
}

/* 消息反馈按钮 */
.message-feedback {
  display: flex;
  gap: 4px;
  margin-top: 4px;
  opacity: 0;
  transition: opacity 0.2s;

  .feedback-btn {
    cursor: pointer;
    font-size: 16px;
    padding: 2px 4px;
    border-radius: 4px;
    user-select: none;
    filter: grayscale(100%);
    transition:
      filter 0.2s,
      background-color 0.2s;

    &:hover {
      filter: grayscale(0%);
      background-color: #f0f0f0;
    }

    &.active {
      filter: grayscale(0%);
    }
  }
}

.ai-message:hover .message-feedback {
  opacity: 1;
}

/* 工具调用过程 */
.tool-calls {
  margin-bottom: 8px;
}

.tool-call-item {
  padding: 8px 12px;
  margin-bottom: 4px;
  border-radius: 8px;
  font-size: 13px;
  border: 1px solid #e4e7ed;
  background: #fafafa;

  &.running {
    border-color: #409eff;
    background: #ecf5ff;
  }

  &.completed {
    border-color: #67c23a;
    background: #f0f9eb;
  }

  .tool-call-header {
    display: flex;
    align-items: center;
    gap: 6px;

    .el-icon {
      font-size: 14px;
    }

    .tool-name {
      font-weight: 600;
      color: #303133;
    }

    .tool-duration {
      margin-left: auto;
      font-size: 11px;
      color: #909399;
    }
  }

  .tool-result {
    margin-top: 6px;
    padding-top: 6px;
    border-top: 1px dashed #dcdfe6;
    font-size: 12px;
    color: #606266;
    max-height: 80px;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.input-area {
  border-top: 1px solid #e4e7ed;
  padding: 12px 20px 16px;

  .input-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
  }

  .mode-tabs {
    display: flex;
    gap: 4px;
    background: #f5f7fa;
    border-radius: 8px;
    padding: 3px;
  }

  .mode-tab {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 5px 14px;
    border-radius: 6px;
    font-size: 13px;
    color: #606266;
    cursor: pointer;
    transition: all 0.2s;
    user-select: none;
    white-space: nowrap;

    &:hover {
      color: #409eff;
      background: #ecf5ff;
    }

    &.active {
      color: #fff;
      background: #409eff;
      box-shadow: 0 1px 3px rgba(64, 158, 255, 0.3);
    }
  }

  .toolbar-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .token-usage {
    font-size: 12px;
    color: #909399;
    padding: 2px 8px;
    background: #f5f7fa;
    border-radius: 4px;
  }

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

    th,
    td {
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

.tool-drawer-tip {
  font-size: 13px;
  color: #909399;
  margin-bottom: 15px;
}

.tool-item {
  margin-bottom: 8px;

  .tool-name {
    font-weight: 500;
    margin-right: 6px;
  }

  .tool-desc {
    font-size: 12px;
    color: #909399;
  }
}
</style>

<template>
  <div class="im-page">
    <el-row :gutter="16" class="h-full">
      <el-col :span="7" class="h-full">
        <el-card class="im-card h-full" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>即时通讯</span>
              <el-tag size="small" type="info">Beta</el-tag>
            </div>
          </template>

          <el-tabs v-model="leftTab" class="left-tabs">
            <el-tab-pane label="聊天" name="chat">
              <div class="toolbar between">
                <el-input v-model="keyword" size="small" placeholder="搜索会话" clearable class="w-60" />
                <el-button size="small" @click="loadConversations">刷新</el-button>
              </div>

              <el-scrollbar max-height="560px">
                <el-empty v-if="!filteredConversations.length" description="暂无会话" />
                <div
                  v-for="c in filteredConversations"
                  :key="c.chatType + '-' + c.targetId"
                  class="conv-item"
                  :class="{ active: activeMenuIndex === c.chatType + '|' + c.targetId }"
                  @click="onSelectConv(c.chatType + '|' + c.targetId)"
                >
                  <el-avatar :size="36">{{ getAvatarText(c.title) }}</el-avatar>
                  <div class="conv-main">
                    <div class="conv-main-top">
                      <span class="conv-title">{{ c.title || '未命名会话' }}</span>
                      <span class="conv-time">{{ formatTime(c.lastTime || c.lastMessageTime || c.updateTime) }}</span>
                    </div>
                    <div class="conv-main-bottom">
                      <span class="conv-preview">{{ c.lastContent || c.lastMessage || '暂无消息，快发第一条吧' }}</span>
                      <el-badge v-if="c.unreadCount > 0" :value="c.unreadCount" :max="99" />
                    </div>
                  </div>
                </div>
              </el-scrollbar>
            </el-tab-pane>

            <el-tab-pane label="联系人" name="contacts">
              <div class="toolbar contact-add">
                <el-input-number v-model="manualPeerId" :min="1" controls-position="right" />
                <el-button type="primary" @click="openPrivateChat">发起私聊</el-button>
              </div>

              <el-divider content-position="left">常用联系人</el-divider>
              <div class="quick-list">
                <el-tag v-for="uid in recentPeers" :key="uid" class="quick-item" @click="openPeer(uid)"> 用户 {{ uid }} </el-tag>
              </div>

              <el-alert title="联系人接口待接入，当前以最近私聊对象与手动输入为主。" type="info" :closable="false" />
            </el-tab-pane>

            <el-tab-pane label="群聊" name="groups">
              <el-form label-position="top">
                <el-form-item label="群名称">
                  <el-input v-model="newGroupName" placeholder="请输入群名称" />
                </el-form-item>
                <el-form-item label="成员用户ID（逗号分隔）">
                  <el-input v-model="memberIdsText" type="textarea" :rows="3" placeholder="例如：100,101,102" />
                </el-form-item>
                <el-button type="primary" @click="onCreateGroup">创建群聊</el-button>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="设置" name="settings">
              <el-form label-width="100px">
                <el-form-item label="快捷发送">
                  <el-radio-group v-model="sendMode">
                    <el-radio value="enter">Enter</el-radio>
                    <el-radio value="ctrlEnter">Ctrl + Enter</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="会话自动刷新">
                  <el-switch v-model="autoRefresh" />
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>

      <el-col :span="17" class="h-full">
        <el-card class="im-card h-full" shadow="never">
          <template #header>
            <div class="chat-header">
              <div class="chat-title-wrap">
                <el-avatar :size="34">{{ getAvatarText(active?.title) }}</el-avatar>
                <div>
                  <div class="chat-title">{{ chatTitle }}</div>
                  <div class="chat-sub">{{ active?.chatType === 'GROUP' ? '群会话' : '私聊会话' }}</div>
                </div>
              </div>
              <div class="chat-actions">
                <el-button v-if="active?.chatType === 'PRIVATE'" size="small" @click="onMarkRead">标已读</el-button>
                <el-button v-if="active?.chatType === 'GROUP'" size="small" @click="memberDrawer = true">群成员</el-button>
                <el-button size="small" @click="loadMessages" :disabled="!active">刷新</el-button>
              </div>
            </div>
          </template>

          <template v-if="active">
            <el-scrollbar max-height="460px" class="msg-scroll">
              <div v-if="!messages.length" class="msg-empty">暂无消息</div>

              <div v-for="m in messages" :key="m.messageId" class="msg-row" :class="{ mine: isMine(m) }">
                <div class="msg-bubble">
                  <div class="msg-meta">
                    <span class="msg-user">{{ isMine(m) ? '我' : m.fromUserId }}</span>
                    <span class="msg-time">{{ m.createTime || '' }}</span>
                  </div>
                  <div class="msg-content">{{ m.content }}</div>
                </div>
              </div>
            </el-scrollbar>

            <div class="editor">
              <el-input v-model="draft" type="textarea" :rows="4" placeholder="输入消息..." @keydown="onEditorKeydown" />
              <div class="editor-actions">
                <el-text type="info" size="small">{{ sendTip }}</el-text>
                <el-button type="primary" @click="onSend">发送</el-button>
              </div>
            </div>
          </template>

          <el-empty v-else description="请选择会话或先发起私聊" />
        </el-card>
      </el-col>
    </el-row>

    <el-drawer v-model="memberDrawer" title="群成员" size="360px" :with-header="true">
      <el-empty description="群成员接口待接入" />
      <el-divider />
      <el-form label-position="top">
        <el-form-item label="新增成员ID（逗号分隔）">
          <el-input v-model="addMemberIdsText" type="textarea" :rows="3" placeholder="例如：201,202" />
        </el-form-item>
        <el-button type="primary" :disabled="active?.chatType !== 'GROUP'" @click="onAddMembers">添加成员</el-button>
      </el-form>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useUserStore } from '@/store/modules/user';
import {
  addGroupMembers,
  createGroup,
  groupHistory,
  listConversations,
  markPrivateRead,
  privateHistory,
  sendGroupMessage,
  sendPrivateMessage
} from '@/api/im/chat';

const userStore = useUserStore();

const leftTab = ref<'chat' | 'contacts' | 'groups' | 'settings'>('chat');
const sendMode = ref<'enter' | 'ctrlEnter'>('enter');
const autoRefresh = ref(true);

const keyword = ref('');
const memberDrawer = ref(false);
const addMemberIdsText = ref('');

const conversations = ref<any[]>([]);
const messages = ref<any[]>([]);
const active = ref<{ chatType: string; targetId: string; title: string } | null>(null);
const draft = ref('');
const newGroupName = ref('');
const memberIdsText = ref('');
const manualPeerId = ref<number>(1);
const myUserId = computed(() => Number(userStore.userId) || null);

const chatTitle = computed(() => (active.value ? active.value.title || '未命名会话' : '聊天窗口'));
const activeMenuIndex = computed(() => (active.value ? `${active.value.chatType}|${active.value.targetId}` : ''));
const sendTip = computed(() => (sendMode.value === 'enter' ? 'Enter 发送，Shift+Enter 换行' : 'Ctrl+Enter 发送'));

const filteredConversations = computed(() => {
  const list = conversations.value || [];
  if (!keyword.value.trim()) return list;
  return list.filter((c) =>
    String(c.title || '')
      .toLowerCase()
      .includes(keyword.value.toLowerCase())
  );
});

const recentPeers = computed(() => {
  return (conversations.value || [])
    .filter((c) => c.chatType === 'PRIVATE')
    .map((c) => Number(c.targetId))
    .filter((n, i, arr) => !Number.isNaN(n) && n > 0 && arr.indexOf(n) === i)
    .slice(0, 12);
});

const getAvatarText = (name?: string) => {
  const n = (name || '?').trim();
  return n.substring(0, 1).toUpperCase();
};

const formatTime = (v?: string) => {
  if (!v) return '';
  if (v.length <= 16) return v;
  return v.substring(0, 16);
};

const loadConversations = async () => {
  const res: any = await listConversations();
  if (res?.code === 200) {
    conversations.value = res.data || [];
  }
};

const loadMessages = async () => {
  if (!active.value) return;
  const page = { pageNum: 1, pageSize: 50 };
  let res: any;
  if (active.value.chatType === 'PRIVATE') {
    res = await privateHistory({ peerUserId: Number(active.value.targetId), ...page });
  } else {
    res = await groupHistory({ groupId: active.value.targetId, ...page });
  }
  if (res && (res.code === 200 || res.rows)) {
    messages.value = (res.rows || []).slice().reverse();
  }
};

const onSelectConv = async (index: string) => {
  const [chatType, targetId] = index.split('|');
  const c = conversations.value.find((x) => x.chatType === chatType && x.targetId === targetId);
  active.value = c ? { chatType: c.chatType, targetId: c.targetId, title: c.title } : { chatType, targetId, title: '' };
  await loadMessages();
};

const openPrivateChat = async () => {
  if (!manualPeerId.value || manualPeerId.value < 1) {
    ElMessage.warning('请输入有效的用户ID');
    return;
  }
  active.value = { chatType: 'PRIVATE', targetId: String(manualPeerId.value), title: `用户 ${manualPeerId.value}` };
  leftTab.value = 'chat';
  await loadMessages();
};

const openPeer = async (uid: number) => {
  manualPeerId.value = uid;
  await openPrivateChat();
};

const onSend = async () => {
  if (!active.value || !draft.value.trim()) return;
  const content = draft.value.trim();
  if (content.length > 2000) {
    ElMessage.warning('消息内容不能超过 2000 个字符');
    return;
  }
  draft.value = '';
  try {
    let res: any;
    if (active.value.chatType === 'PRIVATE') {
      res = await sendPrivateMessage({ toUserId: Number(active.value.targetId), content });
    } else {
      res = await sendGroupMessage({ groupId: active.value.targetId, content });
    }
    if (res?.code === 200) {
      await loadMessages();
      if (autoRefresh.value) await loadConversations();
    } else {
      ElMessage.error(res?.msg || '发送失败');
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '发送失败');
  }
};

const onEditorKeydown = (e: KeyboardEvent) => {
  if (sendMode.value === 'enter' && e.key === 'Enter' && !e.shiftKey && !e.ctrlKey) {
    e.preventDefault();
    onSend();
    return;
  }
  if (sendMode.value === 'ctrlEnter' && e.key === 'Enter' && e.ctrlKey) {
    e.preventDefault();
    onSend();
  }
};

const onMarkRead = async () => {
  if (!active.value || active.value.chatType !== 'PRIVATE') return;
  const res: any = await markPrivateRead(Number(active.value.targetId));
  if (res?.code === 200) {
    ElMessage.success('已标记已读');
    await loadConversations();
  }
};

const onCreateGroup = async () => {
  const ids = parseUserIds(memberIdsText.value);

  if (!newGroupName.value.trim() || ids.length < 1) {
    ElMessage.warning('请输入群名和至少一个成员ID');
    return;
  }

  const res: any = await createGroup({ groupName: newGroupName.value.trim(), memberUserIds: ids });
  if (res?.code === 200) {
    ElMessage.success('建群成功');
    newGroupName.value = '';
    memberIdsText.value = '';
    leftTab.value = 'chat';
    await loadConversations();
  } else {
    ElMessage.error(res?.msg || '建群失败');
  }
};

const parseUserIds = (text: string) => {
  return Array.from(
    new Set(
      text
        .split(/[,，\s]+/)
        .map((s) => Number(s.trim()))
        .filter((n) => Number.isSafeInteger(n) && n > 0)
    )
  );
};

const onAddMembers = async () => {
  if (!active.value || active.value.chatType !== 'GROUP') return;
  const ids = parseUserIds(addMemberIdsText.value);
  if (!ids.length) {
    ElMessage.warning('请输入至少一个成员ID');
    return;
  }
  const res: any = await addGroupMembers(active.value.targetId, { memberUserIds: ids });
  if (res?.code === 200) {
    ElMessage.success('添加成员成功');
    addMemberIdsText.value = '';
  } else {
    ElMessage.error(res?.msg || '添加成员失败');
  }
};

const isMine = (m: any) => {
  if (myUserId.value == null) return false;
  return Number(m.fromUserId) === myUserId.value;
};

const handleRealtime = (ev: Event) => {
  const ce = ev as CustomEvent;
  const p = ce.detail;
  if (!p || !active.value) return;

  if (p.chatType === 'PRIVATE' && active.value.chatType === 'PRIVATE') {
    const peer = Number(active.value.targetId);
    if (p.fromUserId === peer || p.toUserId === peer) {
      loadMessages();
    }
  }

  if (p.chatType === 'GROUP' && active.value.chatType === 'GROUP' && String(p.groupId) === active.value.targetId) {
    loadMessages();
  }

  if (autoRefresh.value) {
    loadConversations();
  }
};

onMounted(() => {
  loadConversations();
  window.addEventListener('im-realtime', handleRealtime as EventListener);
});

onUnmounted(() => {
  window.removeEventListener('im-realtime', handleRealtime as EventListener);
});
</script>

<style scoped>
.im-page {
  padding: 16px;
  height: calc(100vh - 110px);
}

.h-full {
  height: 100%;
}

.im-card {
  border-radius: 12px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
}

.toolbar {
  margin-bottom: 10px;
}

.between {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.w-60 {
  width: 60%;
}

.conv-item {
  display: flex;
  gap: 10px;
  padding: 10px;
  border-radius: 10px;
  cursor: pointer;
  margin-bottom: 6px;
  transition: all 0.2s ease;
}

.conv-item:hover {
  background: var(--el-fill-color-light);
}

.conv-item.active {
  background: var(--el-color-primary-light-9);
}

.conv-main {
  flex: 1;
  min-width: 0;
}

.conv-main-top,
.conv-main-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.conv-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time,
.conv-preview {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.conv-preview {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}

.contact-add {
  display: flex;
  gap: 8px;
}

.quick-list {
  margin-bottom: 12px;
}

.quick-item {
  margin-right: 8px;
  margin-bottom: 8px;
  cursor: pointer;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-title {
  font-weight: 700;
}

.chat-sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.chat-actions {
  display: flex;
  gap: 8px;
}

.msg-empty {
  color: var(--el-text-color-secondary);
  text-align: center;
  margin: 24px 0;
}

.msg-row {
  display: flex;
  margin-bottom: 12px;
}

.msg-row.mine {
  justify-content: flex-end;
}

.msg-bubble {
  max-width: 72%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 8px 10px;
  background: #fff;
}

.msg-row.mine .msg-bubble {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.msg-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
}

.msg-user {
  font-size: 12px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.msg-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.msg-content {
  line-height: 1.6;
  word-break: break-word;
}

.editor {
  margin-top: 12px;
}

.editor-actions {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>

<template>
  <div class="im-page">
    <el-row :gutter="16" class="h-full">
      <el-col :span="7" class="h-full">
        <el-card class="im-card h-full" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>即时通讯</span>
              <el-badge v-if="imStore.unreadTotal > 0" :value="imStore.unreadTotal" :max="99" class="unread-badge">
                <el-tag size="small" type="info">Beta</el-tag>
              </el-badge>
              <el-tag v-else size="small" type="info">Beta</el-tag>
            </div>
          </template>

          <el-tabs v-model="leftTab" class="left-tabs">
            <el-tab-pane label="聊天" name="chat">
              <div class="toolbar between">
                <el-input v-model="keyword" size="small" placeholder="搜索会话/内容" clearable class="w-60" />
                <el-button size="small" @click="imStore.loadConversations">刷新</el-button>
              </div>

              <el-scrollbar class="conv-scroll">
                <el-empty v-if="!filteredConversations.length" description="暂无会话" />
                <div
                  v-for="c in filteredConversations"
                  :key="c.chatType + '-' + c.targetId"
                  class="conv-item"
                  :class="{ active: imStore.activeKey === c.chatType + '|' + c.targetId }"
                  @click="onSelectConv(c.chatType + '|' + c.targetId)"
                >
                  <el-avatar :size="36" :src="convAvatar(c) || undefined">{{ getAvatarText(c.title) }}</el-avatar>
                  <div class="conv-main">
                    <div class="conv-main-top">
                      <span class="conv-title">{{ c.title || '未命名会话' }}</span>
                      <span class="conv-time">{{ formatTime(c.lastTime) }}</span>
                    </div>
                    <div class="conv-main-bottom">
                      <span class="conv-preview">{{ previewText(c) }}</span>
                      <el-badge v-if="(c.unreadCount || 0) > 0" :value="c.unreadCount" :max="99" />
                    </div>
                  </div>
                </div>
              </el-scrollbar>
            </el-tab-pane>

            <el-tab-pane label="联系人" name="contacts">
              <div class="toolbar">
                <el-button type="primary" size="small" @click="contactSelectRef?.open()">选择联系人发起私聊</el-button>
              </div>
              <el-divider content-position="left">最近私聊</el-divider>
              <div class="quick-list">
                <el-tag v-for="c in recentPrivate" :key="c.targetId" class="quick-item" @click="onSelectConv('PRIVATE|' + c.targetId)">
                  {{ c.title || c.targetId }}
                </el-tag>
                <el-text v-if="!recentPrivate.length" type="info" size="small">暂无</el-text>
              </div>
            </el-tab-pane>

            <el-tab-pane label="群聊" name="groups">
              <el-form label-position="top">
                <el-form-item label="群名称">
                  <el-input v-model="newGroupName" placeholder="请输入群名称" />
                </el-form-item>
                <el-form-item label="群成员">
                  <el-button size="small" @click="openMemberSelect('create')">选择成员</el-button>
                  <div class="member-tags">
                    <el-tag v-for="u in selectedMembers" :key="u.userId" class="mt-1 mr-1" closable @close="removeSelectedMember(u)">
                      {{ u.nickName || u.userName }}
                    </el-tag>
                  </div>
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
                <el-avatar :size="34" :src="active?.avatar || undefined">{{ getAvatarText(active?.title) }}</el-avatar>
                <div>
                  <div class="chat-title">{{ active?.title || '聊天窗口' }}</div>
                  <div class="chat-sub">{{ active?.chatType === 'GROUP' ? '群会话' : '私聊会话' }}</div>
                </div>
              </div>
              <div class="chat-actions">
                <el-button v-if="active?.chatType === 'PRIVATE'" size="small" @click="onMarkRead">标已读</el-button>
                <el-button v-if="active?.chatType === 'GROUP'" size="small" @click="onOpenMembers">群成员</el-button>
                <el-button size="small" :disabled="!active" @click="reloadMessages">刷新</el-button>
              </div>
            </div>
          </template>

          <template v-if="active">
            <div class="chat-body">
              <el-scrollbar ref="msgScrollbar" class="msg-scroll" @scroll="onMsgScroll">
                <div v-if="activePageInfo.loading && !messages.length" class="msg-empty">加载中…</div>
                <div v-else-if="!messages.length" class="msg-empty">暂无消息</div>

                <div v-for="m in messages" :key="m.clientTempId || m.messageId" class="msg-row" :class="{ mine: imStore.isMine(m) }">
                  <el-avatar :size="34" :src="imStore.isMine(m) ? imStore.myAvatar : m.senderAvatar || undefined">
                    {{ getAvatarText(imStore.isMine(m) ? imStore.myName : m.senderName) }}
                  </el-avatar>
                  <div class="msg-bubble">
                    <div class="msg-meta">
                      <span class="msg-user">{{ imStore.isMine(m) ? '我' : m.senderName || `用户 ${m.fromUserId}` }}</span>
                      <span class="msg-time">{{ formatTime(m.createTime) }}</span>
                    </div>
                    <div v-if="m.revoked" class="msg-content revoked">该消息已被撤回</div>
                    <template v-else>
                      <div v-if="m.forwardFrom" class="msg-fwd">转发消息</div>
                      <div v-if="m.msgType === 'image'" class="msg-image">
                        <el-image :src="m.content" :preview-src-list="[m.content]" fit="cover" hide-on-click-modal class="img-thumb" />
                      </div>
                      <div v-else-if="m.msgType === 'file'" class="msg-file">
                        <el-link :href="m.content" target="_blank" type="primary" :underline="false">
                          <el-icon class="mr-1"><Document /></el-icon>{{ fileName(m.content) }}
                        </el-link>
                      </div>
                      <div v-else class="msg-content">
                        <!-- P1-5: 被 @ 标记 -->
                        <el-tag v-if="m.mentioned" size="small" type="warning" effect="plain" class="mention-flag">@你</el-tag>
                        <span>{{ m.content }}</span>
                      </div>
                    </template>
                    <div v-if="!m.revoked" class="msg-actions">
                      <span v-if="imStore.canRevoke(m)" class="msg-act" @click="onRevoke(m)">撤回</span>
                      <span class="msg-act" @click="openForward(m)">转发</span>
                    </div>
                    <div v-if="showRead(m)" class="msg-read">{{ m.isRead ? '已读' : '未读' }}</div>
                    <div v-if="m.status === 'sending'" class="msg-status sending">发送中…</div>
                    <div v-else-if="m.status === 'failed'" class="msg-status failed" @click="onRetry(m)">发送失败，点击重试</div>
                  </div>
                </div>
              </el-scrollbar>

              <div class="editor">
                <div class="editor-tools">
                  <el-upload :show-file-list="false" :before-upload="onImageUpload" accept="image/*" action="#">
                    <el-button :icon="Picture" size="small" circle title="发送图片" />
                  </el-upload>
                  <el-upload :show-file-list="false" :before-upload="onFileUpload" action="#">
                    <el-button :icon="Paperclip" size="small" circle title="发送文件" />
                  </el-upload>
                  <!-- P1-5: @ 提及按钮（仅群聊显示） -->
                  <el-button v-if="active?.chatType === 'GROUP'" size="small" circle title="@ 群成员" @click="openMentionPicker"> @ </el-button>
                  <!-- P1-5: 已选 @ 成员标签 -->
                  <div v-if="active?.chatType === 'GROUP' && mentionSelected.length" class="mention-tags-inline">
                    <el-tag v-for="u in mentionSelected" :key="u.userId" size="small" closable @close="removeMention(u)">
                      @{{ u.nickName || u.userName }}
                    </el-tag>
                  </div>
                </div>
                <el-input v-model="draft" type="textarea" :rows="3" placeholder="输入消息..." @keydown="onEditorKeydown" />
                <div class="editor-actions">
                  <el-text type="info" size="small">{{ sendTip }}</el-text>
                  <el-button type="primary" @click="onSend">发送</el-button>
                </div>
              </div>
            </div>
          </template>

          <div v-else class="chat-empty-wrap">
            <el-empty description="请选择会话或先发起私聊" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-drawer v-model="memberDrawer" title="群成员" size="360px">
      <el-empty v-if="!activeMembers.length" description="暂无成员" />
      <div v-for="m in activeMembers" :key="m.userId" class="member-row">
        <el-avatar :size="32" :src="m.avatar || undefined">{{ getAvatarText(m.userName) }}</el-avatar>
        <span class="member-name">{{ m.userName }}</span>
        <el-tag v-if="m.owner" size="small" type="warning">群主</el-tag>
        <el-button v-if="isGroupOwner && !m.owner" size="small" type="danger" link @click="onRemoveMember(m)">移除</el-button>
      </div>
      <template #footer>
        <el-button v-if="isGroupOwner" type="primary" @click="openMemberSelect('add')">添加成员</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="forwardVisible" title="转发到" width="500px" append-to-body>
      <el-scrollbar max-height="400px">
        <div
          v-for="c in conversations"
          :key="c.chatType + '-' + c.targetId"
          class="fwd-item"
          :class="{ active: forwardTargetKey === c.chatType + '|' + c.targetId }"
          @click="forwardTargetKey = c.chatType + '|' + c.targetId"
        >
          <el-avatar :size="30" :src="convAvatar(c) || undefined">{{ getAvatarText(c.title) }}</el-avatar>
          <span class="fwd-name">{{ c.title || '未命名会话' }}</span>
        </div>
      </el-scrollbar>
      <template #footer>
        <el-button @click="forwardVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!forwardTargetKey" @click="confirmForward">转发</el-button>
      </template>
    </el-dialog>

    <!-- P1-5: @ 群成员选择对话框 -->
    <el-dialog v-model="mentionVisible" title="选择要 @ 的群成员" width="500px" append-to-body>
      <el-input v-model="mentionKeyword" size="small" placeholder="搜索群成员" clearable class="mention-search" />
      <el-scrollbar max-height="400px">
        <el-empty v-if="!filteredMentionCandidates.length" description="暂无可 @ 的成员" />
        <div
          v-for="m in filteredMentionCandidates"
          :key="m.userId"
          class="mention-item"
          :class="{ active: isMentionSelected(m) }"
          @click="toggleMention(m)"
        >
          <el-avatar :size="30" :src="m.avatar || undefined">{{ getAvatarText(m.nickName || m.userName) }}</el-avatar>
          <span class="mention-name">{{ m.nickName || m.userName || `用户 ${m.userId}` }}</span>
          <el-icon v-if="isMentionSelected(m)" class="mention-checked"><Check /></el-icon>
        </div>
      </el-scrollbar>
      <template #footer>
        <el-button @click="mentionVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmMention">确定</el-button>
      </template>
    </el-dialog>

    <UserSelect ref="contactSelectRef" :multiple="false" @confirm-call-back="onPickContact" />
    <UserSelect ref="memberSelectRef" :multiple="true" @confirm-call-back="onPickMembers" />
  </div>
</template>

<script setup lang="ts">
import { Check, Document, Paperclip, Picture } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import UserSelect from '@/components/UserSelect/index.vue';
import { listGroupMembers, uploadOss } from '@/api/im/chat';
import { useImStore } from '@/store/modules/im';

const imStore = useImStore();

const leftTab = ref<'chat' | 'contacts' | 'groups' | 'settings'>('chat');
const sendMode = ref<'enter' | 'ctrlEnter'>('enter');
const keyword = ref('');
const memberDrawer = ref(false);
const msgScrollbar = ref<any>(null);

// 建群
const newGroupName = ref('');
const selectedMembers = ref<any[]>([]);
const memberSelectMode = ref<'create' | 'add'>('create');
const contactSelectRef = ref<any>(null);
const memberSelectRef = ref<any>(null);

// 转发
const forwardVisible = ref(false);
const forwardMessage = ref<any>(null);
const forwardTargetKey = ref('');

// P1-5: @ 提及
const mentionVisible = ref(false);
const mentionKeyword = ref('');
const mentionCandidates = ref<any[]>([]);
const mentionSelected = ref<any[]>([]);

const sendTip = computed(() => (sendMode.value === 'enter' ? 'Enter 发送，Shift+Enter 换行' : 'Ctrl+Enter 发送'));

const conversations = computed(() => imStore.conversations);
const active = computed(() => imStore.active);
const messages = computed(() => imStore.activeMessages);
const activePageInfo = computed(() => imStore.activePageInfo);

const recentPrivate = computed(() => conversations.value.filter((c) => c.chatType === 'PRIVATE').slice(0, 12));

const activeMembers = computed(() => {
  if (!active.value || active.value.chatType !== 'GROUP') return [];
  return imStore.groupMembers[active.value.targetId] || [];
});

const isGroupOwner = computed(() => activeMembers.value.some((m: any) => String(m.userId) === imStore.myUserId && m.owner));

// P1-5: 过滤可 @ 的群成员（排除自己，按关键字搜索）
const filteredMentionCandidates = computed(() => {
  const me = imStore.myUserId;
  const kw = mentionKeyword.value.trim().toLowerCase();
  return mentionCandidates.value
    .filter((m: any) => String(m.userId) !== String(me))
    .filter((m: any) => {
      if (!kw) return true;
      const name = String(m.nickName || m.userName || '').toLowerCase();
      return name.includes(kw) || String(m.userId).includes(kw);
    });
});

const isMentionSelected = (m: any) => mentionSelected.value.some((u: any) => String(u.userId) === String(m.userId));

const toggleMention = (m: any) => {
  if (isMentionSelected(m)) {
    mentionSelected.value = mentionSelected.value.filter((u: any) => String(u.userId) !== String(m.userId));
  } else {
    mentionSelected.value = [...mentionSelected.value, m];
  }
};

const removeMention = (u: any) => {
  mentionSelected.value = mentionSelected.value.filter((x: any) => String(x.userId) !== String(u.userId));
};

const openMentionPicker = async () => {
  if (!active.value || active.value.chatType !== 'GROUP') {
    ElMessage.warning('仅群聊支持 @');
    return;
  }
  try {
    const res: any = await listGroupMembers(active.value.targetId);
    if (res?.code === 200) {
      mentionCandidates.value = res.data || [];
      mentionKeyword.value = '';
      mentionVisible.value = true;
    } else {
      ElMessage.error(res?.msg || '加载群成员失败');
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载群成员失败');
  }
};

const confirmMention = () => {
  // 将已选成员追加到草稿（@昵称 形式），由 onSend 携带 mentionUserIds 发送
  if (mentionSelected.value.length) {
    const names = mentionSelected.value.map((u: any) => `@${u.nickName || u.userName || u.userId}`).join(' ');
    const sep = draft.value && !draft.value.endsWith(' ') ? ' ' : '';
    draft.value = `${draft.value}${sep}${names} `;
  }
  mentionVisible.value = false;
};

const filteredConversations = computed(() => {
  const list = conversations.value || [];
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return list;
  return list.filter(
    (c) =>
      String(c.title || '')
        .toLowerCase()
        .includes(kw) ||
      String(c.lastContent || '')
        .toLowerCase()
        .includes(kw)
  );
});

const draft = computed<string>({
  get: () => imStore.draft,
  set: (v) => {
    imStore.draft = v;
  }
});

const getAvatarText = (name?: string) => {
  const n = (name || '?').trim();
  return n.substring(0, 1).toUpperCase();
};

const convAvatar = (c: any) => c.avatar || (c.chatType === 'GROUP' && c.memberAvatars && c.memberAvatars[0]) || '';

const previewText = (c: any) => c.lastContent || '暂无消息，快发第一条吧';

const parseDate = (v?: string | number | Date): Date | null => {
  if (v == null || v === '') return null;
  if (v instanceof Date) return isNaN(v.getTime()) ? null : v;
  if (typeof v === 'number') {
    const d = new Date(v);
    return isNaN(d.getTime()) ? null : d;
  }
  let d = new Date(v);
  if (isNaN(d.getTime())) d = new Date(String(v).replace(' ', 'T'));
  return isNaN(d.getTime()) ? null : d;
};

const formatTime = (v?: string | number | Date) => {
  const d = parseDate(v);
  if (!d) return typeof v === 'string' ? v.substring(0, 16) : '';
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  const hhmm = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  const sameDay = d.toDateString() === now.toDateString();
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  const isYesterday = d.toDateString() === yesterday.toDateString();
  if (sameDay) return hhmm;
  if (isYesterday) return `昨天 ${hhmm}`;
  if (d.getFullYear() === now.getFullYear()) return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hhmm}`;
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

const fileName = (url: string) => {
  try {
    return decodeURIComponent(url.split('/').pop()?.split('?')[0] || '文件');
  } catch {
    return '文件';
  }
};

const scrollToBottom = (force = false) => {
  nextTick(() => {
    const sb = msgScrollbar.value;
    if (!sb) return;
    const wrap = sb.wrapRef as HTMLElement | undefined;
    if (!force && wrap) {
      const distance = wrap.scrollHeight - wrap.scrollTop - wrap.clientHeight;
      if (distance > 80) return;
    }
    sb.setScrollTop(Number.MAX_SAFE_INTEGER);
  });
};

const showRead = (m: any) => imStore.isMine(m) && active.value?.chatType === 'PRIVATE' && m.status === 'sent' && !m.revoked;

// 实时新增消息（非加载更多、非切会话）时按需滚底
const loadMoreActive = ref(false);
watch(
  () => messages.value.length,
  () => {
    if (loadMoreActive.value) return;
    scrollToBottom(false);
  }
);

const onSelectConv = async (index: string) => {
  leftTab.value = 'chat';
  await imStore.selectConv(index);
  scrollToBottom(true);
};

const reloadMessages = async () => {
  await imStore.loadMessages(true);
  scrollToBottom(true);
};

const onPickContact = async (users: any[]) => {
  if (!users || !users.length) return;
  const u = users[0];
  leftTab.value = 'chat';
  await imStore.openPrivateChat(u.userId, u.nickName || u.userName);
  scrollToBottom(true);
};

const openMemberSelect = (mode: 'create' | 'add') => {
  memberSelectMode.value = mode;
  memberSelectRef.value?.open();
};

const onPickMembers = async (users: any[]) => {
  if (memberSelectMode.value === 'create') {
    selectedMembers.value = users || [];
    return;
  }
  if (active.value && active.value.chatType === 'GROUP' && users?.length) {
    const ids = users.map((u: any) => u.userId);
    const ok = await imStore.addGroupMembers(active.value.targetId, ids);
    if (ok) await imStore.loadGroupMembers(active.value.targetId);
  }
};

const removeSelectedMember = (u: any) => {
  selectedMembers.value = selectedMembers.value.filter((x: any) => x.userId !== u.userId);
};

const onCreateGroup = async () => {
  if (!newGroupName.value.trim()) {
    ElMessage.warning('请输入群名称');
    return;
  }
  if (selectedMembers.value.length < 1) {
    ElMessage.warning('请至少选择一名成员');
    return;
  }
  const ids = selectedMembers.value.map((u: any) => u.userId);
  const ok = await imStore.createGroup(newGroupName.value.trim(), ids);
  if (ok) {
    newGroupName.value = '';
    selectedMembers.value = [];
    leftTab.value = 'chat';
  }
};

const onSend = () => {
  const content = draft.value.trim();
  if (!content) return;
  if (content.length > 2000) {
    ElMessage.warning('消息内容不能超过 2000 个字符');
    return;
  }
  // P1-5: 群聊时携带已选 @ 成员 userId 列表
  const mentionUserIds =
    active.value?.chatType === 'GROUP' && mentionSelected.value.length ? mentionSelected.value.map((u: any) => u.userId) : undefined;
  void imStore.send(content, 'text', undefined, mentionUserIds);
  // P1-5: 发送后清空已选 @ 列表
  mentionSelected.value = [];
  scrollToBottom(true);
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

const onImageUpload = async (file: any) => {
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 10MB');
    return false;
  }
  try {
    const res: any = await uploadOss(file);
    if (res?.code === 200 && res.data?.url) {
      await imStore.send(res.data.url, 'image');
      scrollToBottom(true);
    } else {
      ElMessage.error('上传失败');
    }
  } catch {
    ElMessage.error('上传失败');
  }
  return false;
};

const onFileUpload = async (file: any) => {
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 20MB');
    return false;
  }
  try {
    const res: any = await uploadOss(file);
    if (res?.code === 200 && res.data?.url) {
      await imStore.send(res.data.url, 'file');
      scrollToBottom(true);
    } else {
      ElMessage.error('上传失败');
    }
  } catch {
    ElMessage.error('上传失败');
  }
  return false;
};

const onRetry = (m: any) => imStore.retry(m);
const onRevoke = (m: any) => imStore.revoke(m);

const openForward = (m: any) => {
  forwardMessage.value = m;
  forwardTargetKey.value = '';
  forwardVisible.value = true;
};

const confirmForward = async () => {
  if (!forwardMessage.value || !forwardTargetKey.value) return;
  await imStore.forward(forwardMessage.value, forwardTargetKey.value);
  forwardVisible.value = false;
};

const onMarkRead = () => imStore.markActiveRead();

const onOpenMembers = () => {
  if (active.value) imStore.loadGroupMembers(active.value.targetId);
  memberDrawer.value = true;
};

const onRemoveMember = async (m: any) => {
  if (!active.value) return;
  const ok = await imStore.removeMember(active.value.targetId, String(m.userId));
  if (ok) await imStore.loadGroupMembers(active.value.targetId);
};

const onMsgScroll = ({ scrollTop }: { scrollTop: number }) => {
  if (scrollTop < 30 && !activePageInfo.value.loading && activePageInfo.value.hasMore) {
    onLoadMore();
  }
};

const onLoadMore = async () => {
  if (loadMoreActive.value) return;
  loadMoreActive.value = true;
  const wrap = msgScrollbar.value?.wrapRef as HTMLElement | undefined;
  const oldHeight = wrap?.scrollHeight ?? 0;
  await imStore.loadMore();
  await nextTick();
  if (wrap) wrap.scrollTop = wrap.scrollHeight - oldHeight;
  loadMoreActive.value = false;
};

onMounted(() => {
  imStore.init();
  imStore.setFocus(imStore.activeKey || null);
});

watch(
  () => imStore.activeKey,
  (n) => {
    imStore.setFocus(n || null);
    // P1-5: 切换会话时清空已选 @ 列表
    mentionSelected.value = [];
  }
);

onUnmounted(() => {
  imStore.setFocus(null);
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
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.im-card :deep(.el-card__header) {
  flex-shrink: 0;
}

.im-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
}

.unread-badge :deep(.el-badge__content) {
  z-index: 1;
}

.left-tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.left-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
  flex-shrink: 0;
}

.left-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.left-tabs :deep(.el-tab-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.toolbar {
  margin-bottom: 10px;
  flex-shrink: 0;
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

.conv-scroll {
  flex: 1;
  min-height: 0;
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

.quick-list {
  margin-bottom: 12px;
}

.quick-item {
  margin-right: 8px;
  margin-bottom: 8px;
  cursor: pointer;
}

.member-tags {
  margin-top: 6px;
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

.chat-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.chat-empty-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.msg-scroll {
  flex: 1;
  min-height: 0;
}

.msg-empty {
  color: var(--el-text-color-secondary);
  text-align: center;
  margin: 24px 0;
}

.msg-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.msg-row.mine {
  flex-direction: row-reverse;
}

.msg-bubble {
  max-width: 72%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 8px 10px;
  background: #fff;
  position: relative;
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

.msg-content.revoked {
  color: var(--el-text-color-secondary);
  font-style: italic;
}

.msg-fwd {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.msg-image .img-thumb {
  width: 180px;
  max-width: 100%;
  border-radius: 8px;
  display: block;
}

.msg-file {
  display: flex;
  align-items: center;
}

.msg-actions {
  margin-top: 4px;
  display: flex;
  gap: 10px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.msg-row:hover .msg-actions {
  opacity: 1;
}

.msg-act {
  font-size: 12px;
  color: var(--el-color-primary);
  cursor: pointer;
}

.msg-read {
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.msg-status {
  margin-top: 4px;
  font-size: 12px;
}

.msg-status.sending {
  color: var(--el-text-color-secondary);
}

.msg-status.failed {
  color: var(--el-color-danger);
  cursor: pointer;
}

.editor {
  flex-shrink: 0;
  margin-top: 12px;
}

.editor-tools {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}

.editor-actions {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
}

.member-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fwd-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
}

.fwd-item:hover {
  background: var(--el-fill-color-light);
}

.fwd-item.active {
  background: var(--el-color-primary-light-9);
}

.fwd-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* P1-5: @ 提及相关样式 */
.mention-tags-inline {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  margin-left: 4px;
}

.mention-flag {
  margin-right: 6px;
  vertical-align: middle;
}

.mention-search {
  margin-bottom: 10px;
}

.mention-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
}

.mention-item:hover {
  background: var(--el-fill-color-light);
}

.mention-item.active {
  background: var(--el-color-primary-light-9);
}

.mention-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mention-checked {
  color: var(--el-color-primary);
}
</style>

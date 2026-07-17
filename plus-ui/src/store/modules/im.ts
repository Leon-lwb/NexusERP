import { ElMessage, ElNotification } from 'element-plus';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { useUserStore } from '@/store/modules/user';
import {
  addGroupMembers as addGroupMembersApi,
  createGroup as createGroupApi,
  groupHistoryWithSender,
  listConversations,
  listGroupMembers as listGroupMembersApi,
  markGroupRead as markGroupReadApi,
  markPrivateRead as markPrivateReadApi,
  privateHistoryWithSender,
  removeGroupMember as removeGroupMemberApi,
  revokeMessage as revokeMessageApi,
  sendGroupMessage,
  sendPrivateMessage
} from '@/api/im/chat';

export type ChatType = 'PRIVATE' | 'GROUP';

export interface Conversation {
  chatType: ChatType;
  targetId: string;
  title: string;
  avatar?: string;
  memberAvatars?: string[];
  lastContent?: string;
  lastTime?: string | number;
  unreadCount?: number;
}

export interface ChatMessage {
  messageId?: string;
  clientTempId?: string;
  /** P1-4: 客户端幂等键，重发时复用同一值 */
  clientMsgId?: string;
  fromUserId: string;
  toUserId?: string;
  groupId?: string;
  content: string;
  msgType?: string;
  createTime?: string | number;
  senderName?: string;
  senderAvatar?: string;
  status?: 'sending' | 'sent' | 'failed';
  revoked?: boolean;
  isRead?: boolean;
  forwardFrom?: string;
  /** P1-5: 被 @ 标记（来自后端 mentioned 字段） */
  mentioned?: boolean;
  /** P1-5: 被 @ 的群成员 userId 列表（仅发送时携带） */
  mentionUserIds?: (number | string)[];
}

interface ImPayload {
  chatType: ChatType;
  action?: string;
  /** P1-6: 推送类型（IM_MESSAGE / IM_MESSAGE_READ / IM_NOTIFICATION 等） */
  type?: string;
  messageId?: string | number;
  fromUserId?: string | number;
  toUserId?: string | number;
  groupId?: string | number;
  content?: string;
  msgType?: string;
  createTime?: string | number;
  senderName?: string;
  senderAvatar?: string;
  groupName?: string;
  mentioned?: boolean;
  /** P1-6: 已读回执专用字段 */
  messageIds?: (string | number)[];
  readAt?: string | number;
  readBy?: string | number;
}

interface ActiveConv {
  chatType: ChatType;
  targetId: string;
  title: string;
  avatar?: string;
}

interface PageInfo {
  pageNum: number;
  hasMore: boolean;
  loading: boolean;
}

const PAGE_SIZE = 50;
const REVOKE_WINDOW_MS = 2 * 60 * 1000;

let tempSeq = 0;

/**
 * P1-4: 生成客户端幂等键
 * 优先使用 crypto.randomUUID()（HTTPS 或 localhost 安全上下文），降级到 timestamp+random
 */
function generateClientMsgId(): string {
  const g = globalThis as any;
  if (g?.crypto?.randomUUID) {
    try {
      return g.crypto.randomUUID();
    } catch {
      /* fallthrough */
    }
  }
  return `cmsg_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

export const useImStore = defineStore('im', () => {
  const userStore = useUserStore();

  const conversations = ref<Conversation[]>([]);
  const messagesMap = ref<Record<string, ChatMessage[]>>({});
  const pageInfoMap = ref<Record<string, PageInfo>>({});
  const draftMap = ref<Record<string, string>>({});
  const groupMembers = ref<Record<string, any[]>>({});
  const active = ref<ActiveConv | null>(null);
  const focusedConvKey = ref<string | null>(null);
  const initialized = ref(false);

  // 标题闪烁
  let newMsgCount = 0;
  let titleTimer: ReturnType<typeof setInterval> | null = null;
  let origTitle = '';

  const myUserId = computed(() => String(userStore.userId ?? ''));
  const myAvatar = computed(() => userStore.avatar);
  const myName = computed(() => userStore.nickname || '');
  const activeKey = computed(() => (active.value ? convKey(active.value) : ''));
  const activeMessages = computed(() => (activeKey.value ? (messagesMap.value[activeKey.value] ?? []) : []));
  const activePageInfo = computed<PageInfo>(() => pageInfoMap.value[activeKey.value] ?? { pageNum: 1, hasMore: false, loading: false });
  const unreadTotal = computed(() => conversations.value.reduce((s, c) => s + (c.unreadCount || 0), 0));

  const draft = computed<string>({
    get: () => (activeKey.value ? (draftMap.value[activeKey.value] ?? '') : ''),
    set: (v) => {
      if (activeKey.value) draftMap.value[activeKey.value] = v;
    }
  });

  function convKey(c: { chatType: string; targetId: string }) {
    return `${c.chatType}|${c.targetId}`;
  }

  function isMine(m: ChatMessage) {
    if (!myUserId.value) return false;
    return String(m.fromUserId) === myUserId.value;
  }

  function ensurePageInfo(key: string): PageInfo {
    if (!pageInfoMap.value[key]) {
      pageInfoMap.value[key] = { pageNum: 1, hasMore: false, loading: false };
    }
    return pageInfoMap.value[key];
  }

  function normalizeRow(row: any): ChatMessage {
    return {
      messageId: row.messageId != null ? String(row.messageId) : undefined,
      fromUserId: String(row.fromUserId ?? ''),
      toUserId: row.toUserId != null ? String(row.toUserId) : undefined,
      groupId: row.groupId != null ? String(row.groupId) : undefined,
      content: row.content ?? '',
      msgType: row.msgType ?? 'text',
      createTime: row.createTime,
      senderName: row.senderName,
      senderAvatar: row.senderAvatar,
      revoked: row.revoked === '1',
      isRead: row.isRead === '1',
      forwardFrom: row.forwardFrom != null ? String(row.forwardFrom) : undefined,
      // P1-5: 被 @ 标记（后端 mentioned 字段为 '1'/1/true）
      mentioned: row.mentioned === '1' || row.mentioned === 1 || row.mentioned === true,
      // P1-4: 保留服务端返回的 clientMsgId（若有）
      clientMsgId: row.clientMsgId != null ? String(row.clientMsgId) : undefined,
      status: 'sent'
    };
  }

  function init() {
    if (initialized.value) return;
    initialized.value = true;
    void loadConversations();
    window.addEventListener('im-realtime', handleRealtime as EventListener);
    window.addEventListener('focus', onWindowFocus);
    document.addEventListener('visibilitychange', onVisibilityChange);
  }

  async function loadConversations() {
    const res: any = await listConversations();
    if (res?.code === 200) {
      conversations.value = (res.data || []).map((c: any) => ({
        chatType: c.chatType,
        targetId: String(c.targetId),
        title: c.title,
        avatar: c.avatar,
        memberAvatars: c.memberAvatars,
        lastContent: c.lastContent,
        lastTime: c.lastTime,
        unreadCount: Number(c.unreadCount) || 0
      }));
    }
  }

  async function loadMessages(force = false) {
    if (!active.value) return;
    const key = activeKey.value;
    if (!force && messagesMap.value[key]) return;
    const pi = ensurePageInfo(key);
    pi.loading = true;
    try {
      const page = { pageNum: 1, pageSize: PAGE_SIZE };
      let res: any;
      if (active.value.chatType === 'PRIVATE') {
        res = await privateHistoryWithSender({ peerUserId: active.value.targetId, ...page });
      } else {
        res = await groupHistoryWithSender({ groupId: active.value.targetId, ...page });
      }
      if (res && (res.code === 200 || res.rows)) {
        const rows: any[] = res.rows || [];
        messagesMap.value[key] = rows.slice().reverse().map(normalizeRow);
        pi.pageNum = 1;
        pi.hasMore = rows.length >= PAGE_SIZE;
      }
    } finally {
      pi.loading = false;
    }
  }

  async function loadMore() {
    if (!active.value) return;
    const key = activeKey.value;
    const pi = ensurePageInfo(key);
    if (pi.loading || !pi.hasMore) return;
    pi.loading = true;
    try {
      const next = pi.pageNum + 1;
      const page = { pageNum: next, pageSize: PAGE_SIZE };
      let res: any;
      if (active.value.chatType === 'PRIVATE') {
        res = await privateHistoryWithSender({ peerUserId: active.value.targetId, ...page });
      } else {
        res = await groupHistoryWithSender({ groupId: active.value.targetId, ...page });
      }
      if (res && (res.code === 200 || res.rows)) {
        const rows: any[] = res.rows || [];
        const existing = new Set((messagesMap.value[key] || []).map((m) => m.messageId).filter(Boolean));
        const older = rows
          .slice()
          .reverse()
          .map(normalizeRow)
          .filter((m) => !m.messageId || !existing.has(m.messageId));
        if (older.length) {
          messagesMap.value[key] = [...older, ...(messagesMap.value[key] || [])];
          pi.pageNum = next;
        }
        pi.hasMore = rows.length >= PAGE_SIZE;
      }
    } finally {
      pi.loading = false;
    }
  }

  async function selectConv(key: string) {
    const [chatType, targetId] = key.split('|') as [ChatType, string];
    const c = conversations.value.find((x) => x.chatType === chatType && x.targetId === targetId);
    active.value = c ? { chatType: c.chatType, targetId: c.targetId, title: c.title, avatar: c.avatar } : { chatType, targetId, title: '' };
    await loadMessages();
    await markActiveRead();
  }

  async function openPrivateChat(userId: number | string, title?: string) {
    active.value = { chatType: 'PRIVATE', targetId: String(userId), title: title || `用户 ${userId}` };
    await loadMessages(true);
    void markActiveRead();
  }

  async function markActiveRead() {
    if (!active.value) return;
    const key = activeKey.value;
    const c = conversations.value.find((x) => convKey(x) === key);
    if (c) c.unreadCount = 0;
    if (active.value.chatType === 'PRIVATE') {
      void markPrivateReadApi(active.value.targetId).catch(() => {});
    } else {
      void markGroupReadApi(active.value.targetId).catch(() => {});
    }
  }

  function updateTemp(key: string, clientTempId: string, patch: Partial<ChatMessage>) {
    const arr = messagesMap.value[key];
    if (!arr) return;
    const idx = arr.findIndex((m) => m.clientTempId === clientTempId);
    if (idx >= 0) arr[idx] = { ...arr[idx], ...patch };
  }

  function updateConversationPreview(conv: ActiveConv, content: string) {
    const key = convKey(conv);
    const c = conversations.value.find((x) => convKey(x) === key);
    if (c) {
      c.lastContent = content;
      c.lastTime = Date.now();
      conversations.value = [c, ...conversations.value.filter((x) => convKey(x) !== key)];
    } else {
      conversations.value = [
        {
          chatType: conv.chatType,
          targetId: conv.targetId,
          title: conv.title,
          avatar: conv.avatar,
          lastContent: content,
          lastTime: Date.now(),
          unreadCount: 0
        },
        ...conversations.value
      ];
    }
  }

  async function sendCore(
    conv: ActiveConv,
    key: string,
    content: string,
    msgType: string,
    clientTempId: string,
    forwardFrom?: string,
    clientMsgId?: string,
    mentionUserIds?: (number | string)[]
  ) {
    try {
      let res: any;
      if (conv.chatType === 'PRIVATE') {
        res = await sendPrivateMessage({ toUserId: conv.targetId, content, msgType, forwardFrom, clientMsgId });
      } else {
        res = await sendGroupMessage({ groupId: conv.targetId, content, msgType, forwardFrom, clientMsgId, mentionUserIds });
      }
      if (res?.code === 200) {
        const realId = res.data != null ? String(res.data) : undefined;
        updateTemp(key, clientTempId, { messageId: realId, status: 'sent' });
        updateConversationPreview(conv, content);
      } else {
        updateTemp(key, clientTempId, { status: 'failed' });
        ElMessage.error(res?.msg || '发送失败');
      }
    } catch (e: any) {
      updateTemp(key, clientTempId, { status: 'failed' });
      ElMessage.error(e?.message || '发送失败');
    }
  }

  /**
   * 发送消息
   * @param mentionUserIds P1-5: 群聊被 @ 的成员 userId 列表
   */
  async function send(
    content: string,
    msgType: 'text' | 'image' | 'file' = 'text',
    forwardFrom?: string,
    mentionUserIds?: (number | string)[]
  ) {
    if (!active.value || !content) return;
    const key = activeKey.value;
    const conv: ActiveConv = { ...active.value };
    const clientTempId = `temp_${Date.now()}_${++tempSeq}`;
    // P1-4: 生成 clientMsgId 用于幂等
    const clientMsgId = generateClientMsgId();
    // P1-5: 自方被 @ 标记（群聊发送时若 mentionUserIds 包含自己，标记 mentioned）
    const myUid = myUserId.value;
    const isMentionedMe = !!mentionUserIds && !!myUid && mentionUserIds.some((id) => String(id) === myUid);
    messagesMap.value[key] = [
      ...(messagesMap.value[key] || []),
      {
        clientTempId,
        clientMsgId,
        fromUserId: myUserId.value,
        content,
        msgType,
        forwardFrom,
        createTime: Date.now(),
        senderName: myName.value,
        senderAvatar: myAvatar.value,
        status: 'sending',
        mentionUserIds,
        mentioned: isMentionedMe
      }
    ];
    if (msgType === 'text') draftMap.value[key] = '';
    await sendCore(conv, key, content, msgType, clientTempId, forwardFrom, clientMsgId, mentionUserIds);
  }

  async function retry(m: ChatMessage) {
    if (!m.clientTempId || !active.value) return;
    const key = activeKey.value;
    const conv: ActiveConv = { ...active.value };
    // P1-4: 重发复用原 clientMsgId 实现幂等，避免网络抖动产生重复消息
    const clientMsgId = m.clientMsgId || generateClientMsgId();
    if (!m.clientMsgId) {
      updateTemp(key, m.clientTempId, { clientMsgId, status: 'sending' });
    } else {
      updateTemp(key, m.clientTempId, { status: 'sending' });
    }
    await sendCore(conv, key, m.content, m.msgType || 'text', m.clientTempId, m.forwardFrom, clientMsgId, m.mentionUserIds);
  }

  function markRevoked(key: string, messageId: string) {
    const arr = messagesMap.value[key];
    if (!arr) return;
    const idx = arr.findIndex((m) => m.messageId && m.messageId === messageId);
    if (idx >= 0) arr[idx] = { ...arr[idx], revoked: true };
  }

  async function revoke(m: ChatMessage) {
    if (!m.messageId) return;
    const key = activeKey.value;
    try {
      const res: any = await revokeMessageApi(m.messageId);
      if (res?.code === 200) {
        markRevoked(key, m.messageId);
      } else {
        ElMessage.error(res?.msg || '撤回失败');
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '撤回失败');
    }
  }

  function canRevoke(m: ChatMessage) {
    if (!isMine(m) || !m.messageId || m.revoked) return false;
    if (m.status === 'sending' || m.status === 'failed') return false;
    const d = parseDate(m.createTime);
    if (!d) return false;
    return Date.now() - d.getTime() <= REVOKE_WINDOW_MS;
  }

  async function forward(message: ChatMessage, targetKey: string) {
    const [tChatType, tTargetId] = targetKey.split('|') as [ChatType, string];
    const forwardFrom = message.messageId;
    // P1-4: 转发同样需要 clientMsgId 幂等
    const clientMsgId = generateClientMsgId();
    try {
      let res: any;
      if (tChatType === 'PRIVATE') {
        res = await sendPrivateMessage({ toUserId: tTargetId, content: message.content, msgType: message.msgType, forwardFrom, clientMsgId });
      } else {
        res = await sendGroupMessage({ groupId: tTargetId, content: message.content, msgType: message.msgType, forwardFrom, clientMsgId });
      }
      if (res?.code === 200) {
        ElMessage.success('已转发');
        await selectConv(targetKey);
      } else {
        ElMessage.error(res?.msg || '转发失败');
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '转发失败');
    }
  }

  async function createGroup(name: string, memberUserIds: (number | string)[]) {
    const res: any = await createGroupApi({ groupName: name, memberUserIds });
    if (res?.code === 200) {
      await loadConversations();
      return true;
    }
    ElMessage.error(res?.msg || '建群失败');
    return false;
  }

  async function addGroupMembers(groupId: string, userIds: (number | string)[]) {
    const res: any = await addGroupMembersApi(groupId, { memberUserIds: userIds });
    if (res?.code === 200) {
      ElMessage.success('添加成员成功');
      return true;
    }
    ElMessage.error(res?.msg || '添加成员失败');
    return false;
  }

  async function removeMember(groupId: string, userId: string) {
    const res: any = await removeGroupMemberApi(groupId, userId);
    if (res?.code === 200) {
      ElMessage.success('已移除');
      return true;
    }
    ElMessage.error(res?.msg || '移除失败');
    return false;
  }

  async function loadGroupMembers(groupId: string) {
    const res: any = await listGroupMembersApi(groupId);
    if (res?.code === 200) {
      groupMembers.value[groupId] = res.data || [];
    }
  }

  function setFocus(key: string | null) {
    focusedConvKey.value = key;
    if (key) stopFlash();
  }

  function deriveKey(p: ImPayload): string | null {
    const me = myUserId.value;
    if (p.chatType === 'PRIVATE') {
      const from = String(p.fromUserId ?? '');
      const to = String(p.toUserId ?? '');
      const peer = from === me ? to : from;
      return peer ? `PRIVATE|${peer}` : null;
    }
    return p.groupId != null ? `GROUP|${p.groupId}` : null;
  }

  function handleRealtime(ev: Event) {
    const p = (ev as CustomEvent).detail as ImPayload | undefined;
    if (!p) return;

    // P1-6: 已读回执推送
    if (p.type === 'IM_MESSAGE_READ') {
      handleReadReceipt(p);
      return;
    }

    if (p.action === 'revoke') {
      const key = deriveKey(p);
      if (key && p.messageId != null) markRevoked(key, String(p.messageId));
      return;
    }

    const key = deriveKey(p);
    if (!key) return;
    const [chatType, targetId] = key.split('|') as [ChatType, string];

    // 会话就地更新 / 新建并置顶
    let conv = conversations.value.find((c) => convKey(c) === key);
    if (!conv) {
      conv = {
        chatType,
        targetId,
        title: chatType === 'PRIVATE' ? p.senderName || `用户 ${targetId}` : p.groupName || '群聊',
        avatar: chatType === 'PRIVATE' ? p.senderAvatar : '',
        lastContent: p.content,
        lastTime: p.createTime,
        unreadCount: 0
      };
      conversations.value = [conv, ...conversations.value];
    } else {
      conv.lastContent = p.content;
      conv.lastTime = p.createTime;
      conversations.value = [conv, ...conversations.value.filter((c) => convKey(c) !== key)];
    }

    const isActive = activeKey.value === key;
    const msgId = p.messageId != null ? String(p.messageId) : undefined;
    const exists = msgId ? (messagesMap.value[key] || []).some((m) => m.messageId && m.messageId === msgId) : false;

    if (isActive) {
      conv.unreadCount = 0;
      if (!exists) {
        messagesMap.value[key] = [
          ...(messagesMap.value[key] || []),
          {
            messageId: msgId,
            fromUserId: String(p.fromUserId ?? ''),
            toUserId: p.toUserId != null ? String(p.toUserId) : undefined,
            groupId: p.groupId != null ? String(p.groupId) : undefined,
            content: p.content || '',
            msgType: p.msgType,
            createTime: p.createTime,
            senderName: p.senderName,
            senderAvatar: p.senderAvatar,
            status: 'sent',
            // P1-5: 来自推送的 mentioned 标记
            mentioned: p.mentioned === true
          }
        ];
      }
      if (chatType === 'PRIVATE') void markPrivateReadApi(targetId).catch(() => {});
    } else {
      conv.unreadCount = (conv.unreadCount || 0) + 1;
    }

    maybeNotify(p, key);
  }

  /**
   * P1-6: 处理 IM_MESSAGE_READ 已读回执推送
   * <p>当接收方 markRead 后，原发送方会收到此推送，据此把对应消息标为已读。
   * <p>payload: { chatType, peerUserId, groupId, messageIds, readAt, readBy }
   */
  function handleReadReceipt(p: ImPayload) {
    if (!p || !Array.isArray(p.messageIds) || p.messageIds.length === 0) return;

    // 计算本地会话 key
    // 私聊：peerUserId = readBy（读取消息的人），对自己而言会话是 PRIVATE|readBy
    // 群聊：会话是 GROUP|groupId
    let key: string | null;
    if (p.chatType === 'GROUP') {
      if (p.groupId == null) return;
      key = `GROUP|${p.groupId}`;
    } else {
      // readBy 是真正读取消息的人
      const readBy = p.readBy ?? p.fromUserId;
      if (readBy == null) return;
      key = `PRIVATE|${readBy}`;
    }

    const arr = messagesMap.value[key];
    if (!arr || arr.length === 0) return;

    const idSet = new Set(p.messageIds.map((id) => String(id)));
    let changed = false;
    for (let i = 0; i < arr.length; i++) {
      const m = arr[i];
      if (m.messageId && idSet.has(m.messageId) && !m.isRead) {
        arr[i] = { ...m, isRead: true };
        changed = true;
      }
    }
    if (changed) {
      messagesMap.value[key] = [...arr];
    }
  }

  function maybeNotify(p: ImPayload, key: string) {
    // 活跃会话且页面可见时已内联展示，抑制弹窗
    if (focusedConvKey.value === key && !document.hidden) return;
    const content = p.content || '';
    const title = p.chatType === 'PRIVATE' ? p.senderName || '即时消息' : p.groupName || '群消息';
    ElNotification({
      title,
      message: (p.senderName ? p.senderName + '：' : '') + (content.length > 120 ? content.slice(0, 120) + '…' : content),
      type: 'success',
      duration: 3500
    });
    if (document.hidden) startFlash();
  }

  function startFlash() {
    newMsgCount++;
    if (titleTimer) return;
    origTitle = document.title;
    titleTimer = setInterval(() => {
      document.title = document.title === origTitle ? `【${newMsgCount}条新消息】${origTitle}` : origTitle;
    }, 1000);
  }

  function stopFlash() {
    if (titleTimer) {
      clearInterval(titleTimer);
      titleTimer = null;
      document.title = origTitle;
    }
    newMsgCount = 0;
  }

  function onWindowFocus() {
    stopFlash();
  }

  function onVisibilityChange() {
    if (!document.hidden) stopFlash();
  }

  return {
    conversations,
    active,
    activeKey,
    activeMessages,
    activePageInfo,
    draft,
    groupMembers,
    focusedConvKey,
    unreadTotal,
    myUserId,
    myAvatar,
    myName,
    init,
    loadConversations,
    loadMessages,
    loadMore,
    selectConv,
    openPrivateChat,
    markActiveRead,
    send,
    retry,
    revoke,
    canRevoke,
    forward,
    createGroup,
    addGroupMembers,
    removeMember,
    loadGroupMembers,
    setFocus,
    isMine,
    convKey
  };
});

function parseDate(v?: string | number | Date): Date | null {
  if (v == null || v === '') return null;
  if (v instanceof Date) return isNaN(v.getTime()) ? null : v;
  if (typeof v === 'number') {
    const d = new Date(v);
    return isNaN(d.getTime()) ? null : d;
  }
  let d = new Date(v);
  if (isNaN(d.getTime())) d = new Date(String(v).replace(' ', 'T'));
  return isNaN(d.getTime()) ? null : d;
}

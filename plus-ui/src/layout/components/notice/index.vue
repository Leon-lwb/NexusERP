<template>
  <div v-loading="state.loading" class="layout-navbars-breadcrumb-user-news">
    <div class="head-box">
      <div class="head-box-title">消息盒子</div>
      <div class="head-box-btn" @click="readAll">全部已读</div>
    </div>
    <!-- 分类 Tab（category 前端分组，无需后端参数） -->
    <el-tabs v-model="activeTab" class="message-tabs" stretch>
      <el-tab-pane v-for="tab in tabs" :key="tab.key" :name="tab.key">
        <template #label>
          <span class="tab-label">
            {{ tab.label }}
            <span v-if="tabCount(tab.key) > 0" class="tab-count">{{ tabCount(tab.key) }}</span>
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>
    <div v-loading="state.loading" class="content-box">
      <template v-if="currentNewsList.length > 0">
        <div v-for="(v, k) in currentNewsList" :key="v.id ?? k" class="content-box-item" :class="{ unread: !v.read }" @click="openDetail(v)">
          <!-- 未读圆点 -->
          <span v-if="!v.read" class="unread-dot"></span>
          <div class="item-conten">
            <div class="content-box-title">{{ v.title || v.message }}</div>
            <!-- 正文摘要 -->
            <div v-if="v.contentPreview" class="content-box-msg">{{ v.contentPreview }}</div>
            <!-- 跳转流程详情（refType 白名单映射 / contentUrl） -->
            <div v-if="detailLink(v)" class="content-box-link">
              <a href="#" @click.stop.prevent="goDetail(v)">{{ detailLinkLabel(v) }}</a>
            </div>
            <div class="content-box-time">{{ v.time }}</div>
          </div>
          <!-- 已读/未读 -->
          <span v-if="v.read" class="el-tag el-tag--success el-tag--mini read">已读</span>
          <span v-else class="el-tag el-tag--danger el-tag--mini read">未读</span>
        </div>
        <!-- 加载更多 -->
        <div v-if="hasMore" class="load-more" @click="loadMore">加载更多</div>
        <div v-else-if="currentNewsList.length > 0" class="load-more load-more--done">没有更多了</div>
      </template>
      <el-empty v-else :description="emptyDescription"></el-empty>
    </div>
    <div v-if="newsList.length > 0" class="foot-box">共 {{ newsList.length }} 条通知 · 点击查看详情</div>

    <!-- 通知详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="currentDetail?.title || '通知详情'" width="500px" append-to-body>
      <div class="notification-detail-content" v-html="currentDetail?.content"></div>
      <div v-if="!currentDetail?.content && currentDetail?.contentPreview" class="notification-detail-content">
        {{ currentDetail.contentPreview }}
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="layoutBreadcrumbUserNews">
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import { useNoticeStore } from '@/store/modules/notice';
import { markAllNotificationRead, markNotificationRead, pageNotifications, type NotificationItem } from '@/api/im/notification';

const noticeStore = useNoticeStore();
const router = useRouter();

// 定义变量内容
const state = reactive({
  loading: false
});

interface NewsRow {
  id?: number | string;
  title?: string;
  message?: string;
  read: boolean;
  time: string;
  /** 正文（持久化列表完整 HTML；实时推送无正文时用摘要） */
  content?: string;
  contentPreview?: string;
  contentUrl?: string;
  refType?: string;
  refId?: string;
  /** 业务分类（后端常量：system / workflow_todo / workflow_notice / im_private / im_group / im_friend_request） */
  category?: string;
}

/** 分类 Tab 定义（全部 + 系统/通知/工作流） */
const TABS = [
  { key: 'all', label: '全部' },
  { key: 'system', label: '系统' },
  { key: 'notice', label: '通知' },
  { key: 'workflow', label: '工作流' }
] as const;
type TabKey = (typeof TABS)[number]['key'];

const activeTab = ref<TabKey>('all');
const tabs = TABS;

/**
 * 分类映射（前端分组，与后端 ImPayloadConstants 对齐）：
 * - system -> 系统（缺省归系统，实时兜底消息也归系统）
 * - workflow_todo / workflow_notice / ai_flow(refType) -> 工作流
 * - im_private / im_group / im_friend_request -> 通知
 */
const groupOf = (item: NewsRow): TabKey => {
  const c = item.category;
  if (c === 'workflow_todo' || c === 'workflow_notice') return 'workflow';
  if (item.refType === 'ai_flow') return 'workflow';
  if (c === 'im_private' || c === 'im_group' || c === 'im_friend_request') return 'notice';
  return 'system';
};

const newsList = ref<NewsRow[]>([]);

/** 各 Tab 未读数（角标，与铃铛未读口径一致） */
const tabCount = (key: TabKey): number => {
  if (key === 'all') return newsList.value.filter((n) => !n.read).length;
  return newsList.value.filter((n) => !n.read && groupOf(n) === key).length;
};

/** 当前 Tab 列表：分组 + 未读优先（未读在前，同组时间倒序） */
const currentNewsList = computed(() => {
  const list = activeTab.value === 'all' ? [...newsList.value] : newsList.value.filter((n) => groupOf(n) === activeTab.value);
  return list.sort((a, b) => {
    if (a.read !== b.read) return a.read ? 1 : -1;
    return b.time.localeCompare(a.time);
  });
});

const emptyDescription = computed(() => {
  switch (activeTab.value) {
    case 'workflow':
      return '暂无工作流消息';
    case 'notice':
      return '暂无通知消息';
    case 'system':
      return '暂无系统消息';
    default:
      return '消息为空';
  }
});

/* ==================== 分页加载 ==================== */

const pageNum = ref(1);
const pageSize = 50;
const total = ref(0);
const loadingMore = ref(false);

const hasMore = computed(() => newsList.value.length < total.value);

/** 去 HTML 摘要 */
const stripHtml = (html: string) => (html || '').replace(/<[^>]*>/g, '');

/** 时间格式化（后端 yyyy-MM-dd HH:mm:ss） */
function formatTime(t?: string): string {
  if (!t) return '';
  return t.length > 16 ? t.slice(0, 16) : t;
}

/** 持久化行 -> 展示行 */
const toNewsRow = (r: NotificationItem): NewsRow => ({
  id: r.notificationId,
  title: r.title,
  message: r.title,
  read: r.readFlag === '1',
  time: formatTime(r.createTime),
  content: r.content,
  contentPreview: stripHtml(r.content || '').slice(0, 80),
  refType: r.refType,
  refId: r.refId,
  category: r.category
});

/**
 * 加载通知列表（持久化 + 实时推送合并）
 * <p>持久化列表（/page，含完整 content）为底；实时推送（内存 store）按 id 合并去重：
 * 已存在 → 刷新已读状态；不存在 → 插入顶部</p>
 */
const loadHistory = async () => {
  state.loading = true;
  try {
    const res = await pageNotifications({ pageNum: 1, pageSize });
    const rows = res.rows ?? [];
    total.value = res.total ?? rows.length;
    pageNum.value = 1;
    const merged: NewsRow[] = rows.map(toNewsRow);
    // 合并实时推送（内存 store）
    mergeRealtime(merged);
    newsList.value = merged;
  } catch (e) {
    console.warn('[Notice] 加载通知失败:', e);
    // 兜底：实时推送数据
    newsList.value = noticeStore.state.notices.map((n: any) => ({
      id: n.id,
      title: n.title || n.message,
      message: n.message,
      read: n.read,
      time: n.time,
      contentPreview: n.contentPreview,
      contentUrl: n.contentUrl,
      refType: n.refType,
      refId: n.refId,
      category: n.category
    }));
  } finally {
    state.loading = false;
  }
};

/** 将实时推送（内存 store）合并进持久化列表 */
const mergeRealtime = (list: NewsRow[]) => {
  const storeNotices = noticeStore.state.notices;
  for (const n of [...storeNotices].reverse()) {
    const idx = list.findIndex((m) => m.id !== undefined && m.id === n.id);
    if (idx >= 0) {
      // 持久化行优先（有完整正文）；store 未读状态刷新（已读不强制改回未读）
      if (!n.read) list[idx].read = false;
    } else {
      list.unshift({
        id: n.id,
        title: n.title || n.message,
        message: n.message,
        read: n.read,
        time: n.time,
        contentPreview: n.contentPreview,
        contentUrl: n.contentUrl,
        refType: n.refType,
        refId: n.refId,
        category: n.category
      });
    }
  }
};

/** 加载更多（追加下一页，按 id 去重） */
const loadMore = async () => {
  if (loadingMore.value) return;
  loadingMore.value = true;
  try {
    const next = pageNum.value + 1;
    const res = await pageNotifications({ pageNum: next, pageSize });
    const rows = res.rows ?? [];
    total.value = res.total ?? rows.length;
    if (rows.length > 0) {
      pageNum.value = next;
      const existing = new Set(newsList.value.map((n) => n.id).filter((id) => id !== undefined) as (string | number)[]);
      const fresh = rows.filter((r) => !existing.has(r.notificationId)).map(toNewsRow);
      newsList.value.push(...fresh);
    }
  } catch (e) {
    ElMessage.warning('加载更多失败');
  } finally {
    loadingMore.value = false;
  }
};

/* ==================== 详情查看 ==================== */

const detailVisible = ref(false);
const currentDetail = ref<NewsRow | null>(null);

/** 点击行：展示正文详情 + 标记已读（后端成功后才更新角标） */
const openDetail = (item: NewsRow) => {
  currentDetail.value = item;
  detailVisible.value = true;
  if (!item.read && item.id !== undefined && item.id !== null) {
    markNotificationRead(item.id)
      .then(() => {
        // 按 id 在当列表中查找并更新已读状态，避免闭包 item 引用在列表刷新后失效
        const target = newsList.value.find((n) => n.id === item.id);
        if (target) target.read = true;
        noticeStore.markRead(item.id as number | string);
      })
      .catch(() => {
        ElMessage.warning('标记已读失败');
      });
  }
};

/** 全部已读 */
const readAll = async () => {
  newsList.value.forEach((n) => (n.read = true));
  noticeStore.readAll();
  try {
    await markAllNotificationRead();
  } catch (e: any) {
    ElMessage.warning('全部已读同步失败: ' + (e?.message ?? e));
  }
};

/* ==================== 跳转流程详情 ==================== */

/** 跳转链接（refType 白名单映射；contentUrl 兜底） */
const detailLink = (item: NewsRow): string | null => {
  if (item.refType === 'ai_flow' && item.refId) {
    return `/ai/flow/monitor/detail?instanceId=${item.refId}`;
  }
  if (typeof item.refType === 'string' && item.refType.startsWith('workflow') && item.refId) {
    return `/workflow/process/detail/${item.refId}`;
  }
  return item.contentUrl ?? null;
};

const detailLinkLabel = (item: NewsRow): string => {
  if (item.refType === 'ai_flow') return '查看流程详情';
  if (typeof item.refType === 'string' && item.refType.startsWith('workflow')) return '去处理';
  return '查看详情';
};

const goDetail = (item: NewsRow) => {
  const link = detailLink(item);
  if (link) router.push(link);
};

onMounted(() => {
  nextTick(() => {
    loadHistory();
  });
});
</script>

<style lang="scss" scoped>
.layout-navbars-breadcrumb-user-news {
  display: flex;
  flex-direction: column;
  min-width: 0;

  .head-box {
    display: flex;
    border-bottom: 1px solid var(--el-border-color-lighter);
    box-sizing: border-box;
    color: var(--el-text-color-primary);
    justify-content: space-between;
    height: 35px;
    align-items: center;
    .head-box-btn {
      color: var(--el-color-primary);
      font-size: 13px;
      cursor: pointer;
      opacity: 0.8;
      &:hover {
        opacity: 1;
      }
    }
  }

  .message-tabs {
    padding-top: 6px;
    :deep(.el-tabs__header) {
      margin-bottom: 0;
    }
    :deep(.el-tabs__nav-wrap::after) {
      background: var(--el-border-color-lighter);
    }
    :deep(.el-tabs__item) {
      font-size: 12px;
      height: 32px;
    }
    .tab-label {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .tab-count {
      min-width: 16px;
      height: 16px;
      padding: 0 4px;
      border-radius: 8px;
      background: var(--el-color-danger);
      color: #fff;
      font-size: 11px;
      line-height: 16px;
      text-align: center;
    }
  }

  .content-box {
    height: 300px;
    overflow: auto;
    font-size: 13px;
    padding: 8px 0 0;

    .content-box-item {
      display: flex;
      gap: 8px;
      align-items: flex-start;
      padding: 10px 12px;
      margin: 4px 0;
      border-radius: 12px;
      cursor: pointer;
      transition:
        background-color 0.2s ease,
        transform 0.2s ease;

      &:hover {
        background: var(--el-fill-color-light);
        transform: translateY(-1px);
      }

      .unread-dot {
        flex-shrink: 0;
        width: 8px;
        height: 8px;
        margin-top: 6px;
        border-radius: 50%;
        background: var(--el-color-danger);
      }

      .content-box-title {
        color: var(--el-text-color-primary);
        font-weight: 600;
        line-height: 1.5;
      }

      &.unread .content-box-title {
        font-weight: 700;
      }

      .content-box-msg {
        color: var(--el-text-color-secondary);
        margin-top: 4px;
        display: -webkit-box;
        overflow: hidden;
        text-overflow: ellipsis;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
      }

      .content-box-link {
        margin-top: 4px;
        a {
          color: var(--el-color-primary);
          font-size: 12px;
          text-decoration: none;
          &:hover {
            text-decoration: underline;
          }
        }
      }

      .content-box-time {
        color: var(--el-text-color-secondary);
        font-size: 12px;
        margin-top: 4px;
      }

      .item-conten {
        width: 100%;
        display: flex;
        flex-direction: column;
      }

      .read {
        flex-shrink: 0;
        margin-top: 2px;
      }
    }

    .load-more {
      text-align: center;
      padding: 8px 0;
      color: var(--el-color-primary);
      font-size: 12px;
      cursor: pointer;
      &:hover {
        opacity: 0.8;
      }
      &--done {
        color: var(--el-text-color-secondary);
        cursor: default;
      }
    }
  }

  .foot-box {
    height: 35px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-top: 1px solid var(--el-border-color-lighter);
  }

  .notification-detail-content {
    max-height: 400px;
    overflow-y: auto;
    font-size: 13px;
    line-height: 1.7;
    color: var(--el-text-color-primary);
    white-space: pre-wrap;
  }

  :deep(.el-empty__description p) {
    font-size: 13px;
  }
}
</style>

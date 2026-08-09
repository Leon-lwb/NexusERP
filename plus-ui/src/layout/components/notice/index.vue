<template>
  <div v-loading="state.loading" class="layout-navbars-breadcrumb-user-news">
    <div class="head-box">
      <div class="head-box-title">通知公告</div>
      <div class="head-box-btn" @click="readAll">全部已读</div>
    </div>
    <div v-loading="state.loading" class="content-box">
      <template v-if="newsList.length > 0">
        <div v-for="(v, k) in newsList" :key="v.id ?? k" class="content-box-item" @click="openDetail(v)">
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
      </template>
      <el-empty v-else :description="'消息为空'"></el-empty>
    </div>
    <div v-if="newsList.length > 0" class="foot-box">共 {{ newsList.length }} 条通知 · 点击查看详情</div>

    <!-- 通知详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="currentDetail?.title || '通知详情'" width="520px" append-to-body>
      <div class="notification-detail-content" v-html="currentDetail?.content"></div>
      <div v-if="!currentDetail?.content && currentDetail?.contentPreview" class="notification-detail-content">
        {{ currentDetail.contentPreview }}
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="layoutBreadcrumbUserNews">
import { nextTick, onMounted, reactive, ref } from 'vue';
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
}

const newsList = ref<NewsRow[]>([]);

/** 去 HTML 摘要 */
const stripHtml = (html: string) => (html || '').replace(/<[^>]*>/g, '');

/** 时间格式化（后端 yyyy-MM-dd HH:mm:ss） */
function formatTime(t?: string): string {
  if (!t) return '';
  return t.length > 16 ? t.slice(0, 16) : t;
}

/**
 * 加载通知列表（持久化 + 实时推送合并）
 * <p>持久化列表（/page，含完整 content）为底；实时推送（内存 store）按 id 合并去重：
 * 已存在 → 刷新已读状态；不存在 → 插入顶部</p>
 */
const loadHistory = async () => {
  state.loading = true;
  try {
    const res = await pageNotifications({ pageNum: 1, pageSize: 50 });
    const rows = (res.data?.rows ?? []) as NotificationItem[];
    const merged: NewsRow[] = rows.map((r) => ({
      id: r.notificationId,
      title: r.title,
      message: r.title,
      read: r.readFlag === '1',
      time: formatTime(r.createTime),
      content: r.content,
      contentPreview: stripHtml(r.content || '').slice(0, 80),
      refType: r.refType,
      refId: r.refId
    }));
    // 合并实时推送（内存 store）
    const storeNotices = noticeStore.state.notices;
    for (const n of [...storeNotices].reverse()) {
      const idx = merged.findIndex((m) => m.id !== undefined && m.id === n.id);
      if (idx >= 0) {
        // 持久化行优先（有完整正文）；实时未读状态刷新
        if (!n.read) merged[idx].read = false;
      } else {
        merged.unshift({
          id: n.id,
          title: n.title || n.message,
          message: n.message,
          read: n.read,
          time: n.time,
          contentPreview: n.contentPreview,
          contentUrl: n.contentUrl,
          refType: n.refType,
          refId: n.refId
        });
      }
    }
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
      refId: n.refId
    }));
  } finally {
    state.loading = false;
  }
};

/* ==================== 详情查看 ==================== */

const detailVisible = ref(false);
const currentDetail = ref<NewsRow | null>(null);

/** 点击行：展示正文详情 + 标记已读（后端持久） */
const openDetail = (item: NewsRow) => {
  currentDetail.value = item;
  detailVisible.value = true;
  if (!item.read) {
    item.read = true;
    if (item.id !== undefined && item.id !== null) {
      noticeStore.markRead(item.id);
      markNotificationRead(item.id).catch(() => {
        item.read = false;
        ElMessage.warning('标记已读失败');
      });
    } else {
      noticeStore.markRead(item.id as any);
    }
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
  .content-box {
    height: 300px;
    overflow: auto;
    font-size: 13px;
    .content-box-item {
      padding-top: 12px;
      display: flex;
      cursor: pointer;
      &:last-of-type {
        padding-bottom: 12px;
      }
      .content-box-title {
        color: var(--el-text-color-primary);
        font-weight: 500;
      }
      .content-box-msg {
        color: var(--el-text-color-secondary);
        margin-top: 5px;
        margin-bottom: 5px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 300px;
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
      }
      .item-conten {
        width: 100%;
        display: flex;
        flex-direction: column;
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

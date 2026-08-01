<template>
  <div class="sop-viewer">
    <el-card v-loading="loading">
      <template #header>
        <div class="viewer-header">
          <div class="header-left">
            <el-button icon="Back" @click="goBack">返回</el-button>
            <span class="sop-title">{{ sop?.sopName || 'SOP查看器' }}</span>
            <el-tag v-if="sop?.sopType" size="small">{{ typeLabel(sop.sopType) }}</el-tag>
            <el-tag v-if="sop?.currentVersion" type="success" size="small">v{{ sop.currentVersion }}</el-tag>
          </div>
          <div class="header-right">
            <el-button icon="Refresh" @click="loadSop" :disabled="!processId && !stepRecordId">刷新</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!loading && !sop" description="该工序未绑定已发布SOP">
        <template #image
          ><el-icon size="60"><Document /></el-icon
        ></template>
        <div v-if="processId" class="empty-tip">工序ID: {{ processId }}</div>
        <div v-if="stepRecordId" class="empty-tip">工序记录ID: {{ stepRecordId }}</div>
      </el-empty>

      <div v-if="sop" class="sop-content">
        <el-descriptions :column="3" border size="small" class="sop-meta">
          <el-descriptions-item label="SOP编码">{{ sop.sopCode }}</el-descriptions-item>
          <el-descriptions-item label="文件类型">{{ sop.fileType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发布人">{{ sop.approvalByName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="说明" :span="3">{{ sop.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="media-wrapper">
          <!-- PDF: iframe 渲染 -->
          <iframe v-if="sop.fileType === 'PDF' && sop.fileUrl" :src="sop.fileUrl" class="media-pdf" frameborder="0" />
          <!-- 图片: el-image -->
          <el-image
            v-else-if="sop.fileType === 'IMAGE' && sop.fileUrl"
            :src="sop.fileUrl"
            fit="contain"
            class="media-image"
            :preview-src-list="[sop.fileUrl]"
          />
          <!-- 视频: video 标签 -->
          <video v-else-if="sop.fileType === 'VIDEO' && sop.fileUrl" :src="sop.fileUrl" controls class="media-video" />
          <!-- Word: 下载链接 -->
          <div v-else-if="sop.fileType === 'WORD' && sop.fileUrl" class="media-word">
            <el-link :href="sop.fileUrl" type="primary" :underline="false" target="_blank">
              <el-icon><Download /></el-icon> 下载 Word 文档
            </el-link>
          </div>
          <!-- 无文件URL(可能OSS签名失败) -->
          <el-empty v-else description="文件不可预览(签名URL为空)">
            <el-button type="primary" @click="loadSop">重新加载</el-button>
          </el-empty>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="SopViewer">
import { getSopByProcess, getSopByStep } from '@/api/mes/sop/document';
import type { SopDocumentVO } from '@/api/mes/sop/document';
import { recordViewLog } from '@/api/mes/sop/view-log';
import { Document, Download } from '@element-plus/icons-vue';

const route = useRoute();
const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_sop_type } = toRefs<any>(proxy?.useDict('mes_sop_type'));

const sop = ref<SopDocumentVO | null>(null);
const loading = ref(true);

// 雪花ID为19位长整型，route.query 传字符串，不能 Number() 截断，保持字符串透传
const processId = computed(() => (route.query.processId ? String(route.query.processId) : ''));
const stepRecordId = computed(() => (route.query.stepRecordId ? String(route.query.stepRecordId) : ''));
const woId = computed(() => (route.query.woId ? String(route.query.woId) : undefined));
const woNo = computed(() => (route.query.woNo as string) || '');
const workstationId = computed(() => (route.query.workstationId ? String(route.query.workstationId) : undefined));

const typeLabel = (v: string) => {
  const dict = mes_sop_type.value?.find((d: any) => d.value === v);
  return dict ? dict.label : v;
};

const loadSop = async () => {
  loading.value = true;
  sop.value = null;
  try {
    let res: any;
    if (stepRecordId.value) {
      res = await getSopByStep(stepRecordId.value);
    } else if (processId.value) {
      res = await getSopByProcess(processId.value);
    } else {
      proxy!.$modal.msgWarning('缺少 processId 或 stepRecordId 参数');
      return;
    }
    sop.value = res.data;
    // 登记查阅(signature_id=null, 预留 P0-6)
    if (sop.value?.sopId) {
      recordViewLog({
        sopId: sop.value.sopId,
        versionNo: sop.value.currentVersion,
        woId: woId.value,
        woNo: woNo.value,
        processId: processId.value || undefined,
        workstationId: workstationId.value
      }).catch(() => {
        /* 查阅登记失败不阻断查看 */
      });
    }
  } catch (e) {
    proxy!.$modal.msgError('SOP加载失败');
  } finally {
    loading.value = false;
  }
};

const goBack = () => {
  router.back();
};

// 路由参数变化时重新加载(如工位切换工序)
watch(
  () => route.query,
  () => {
    if (route.query.processId || route.query.stepRecordId) {
      loadSop();
    }
  }
);

onMounted(() => loadSop());
</script>

<style scoped>
.sop-viewer {
  height: calc(100vh - 84px);
}
.viewer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.sop-title {
  font-size: 16px;
  font-weight: bold;
}
.sop-meta {
  margin-bottom: 12px;
}
.media-wrapper {
  height: calc(100vh - 280px);
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 4px;
}
.media-pdf {
  width: 100%;
  height: 100%;
  border: none;
}
.media-image {
  max-width: 100%;
  max-height: 100%;
}
.media-video {
  max-width: 100%;
  max-height: 100%;
}
.media-word {
  text-align: center;
}
.empty-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-top: 6px;
}
:deep(.el-card__body) {
  height: calc(100% - 56px);
}
</style>

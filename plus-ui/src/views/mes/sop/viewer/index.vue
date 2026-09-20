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
            <!-- MES-SOP-01：查阅登记失败不再静默，给出可见状态与补登入口 -->
            <el-tag v-if="viewLogFailed" type="warning" size="small" class="mr-2">查阅登记未成功</el-tag>
            <el-button v-if="viewLogFailed" type="warning" plain icon="RefreshRight" @click="retryViewLog">重试登记</el-button>
            <el-button icon="Refresh" @click="loadSop" :disabled="!processId && !stepRecordId && !sopId">刷新</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!loading && !sop" :description="emptyDescription">
        <template #image
          ><el-icon size="60"><Document /></el-icon
        ></template>
        <div v-if="processId" class="empty-tip">工序ID: {{ processId }}</div>
        <div v-if="stepRecordId" class="empty-tip">工序记录ID: {{ stepRecordId }}</div>
        <div v-if="sopId" class="empty-tip">SOP ID: {{ sopId }}</div>
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
          <!-- 正文取不到：绝大多数情况是该 SOP 根本没上传附件（file_oss_id 为空或 0），
               而不是 OSS 签名失败——原文案「签名URL为空」会把用户引向错误方向，故按可操作的方式表述。
               注意列表页的「预览」按钮是 v-if="scope.row.fileUrl"，这种情况会被直接隐藏、看不出来，
               只有本页会显式报错，所以这里必须把「怎么办」说清楚。 -->
          <el-empty v-else :description="noFileTip">
            <el-button type="primary" @click="loadSop">重新加载</el-button>
          </el-empty>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="SopViewer">
import { getSopByProcess, getSopByStep, getDocument } from '@/api/mes/sop/document';
import type { SopDocumentVO } from '@/api/mes/sop/document';
import { recordViewLog } from '@/api/mes/sop/view-log';
import { Document, Download } from '@element-plus/icons-vue';

const route = useRoute();
const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_sop_type } = toRefs<any>(proxy?.useDict('mes_sop_type'));

const sop = ref<SopDocumentVO | null>(null);
const loading = ref(true);
/** MES-SOP-01：查阅登记最终失败标记（403 权限码错配时原先被空 catch 静默吞掉） */
const viewLogFailed = ref(false);

// 雪花ID为19位长整型，route.query 传字符串，不能 Number() 截断，保持字符串透传
const processId = computed(() => (route.query.processId ? String(route.query.processId) : ''));
const stepRecordId = computed(() => (route.query.stepRecordId ? String(route.query.stepRecordId) : ''));
// 按 SOP 直接查阅(列表页「查阅」入口)也保持字符串透传
const sopId = computed(() => (route.query.sopId ? String(route.query.sopId) : ''));
const woId = computed(() => (route.query.woId ? String(route.query.woId) : undefined));
const woNo = computed(() => (route.query.woNo as string) || '');
const workstationId = computed(() => (route.query.workstationId ? String(route.query.workstationId) : undefined));

const typeLabel = (v: string) => {
  const dict = mes_sop_type.value?.find((d: any) => d.value === v);
  return dict ? dict.label : v;
};

// 空状态文案按实际传入的参数来源给出，避免按 sopId 查阅时误报「工序未绑定SOP」
const emptyDescription = computed(() => {
  if (stepRecordId.value) return '该工序记录未绑定已发布SOP';
  if (processId.value) return '该工序未绑定已发布SOP';
  if (sopId.value) return '未找到该SOP(可能已被删除)';
  return '缺少 processId 或 stepRecordId 或 sopId 参数';
});

/**
 * SOP 已找到、但正文 URL 为空时的提示。
 * 成因基本只有一个：该 SOP 的 file_oss_id 为空或为 0（未上传附件），
 * 此时后端 @Translation 解析不出 OSS 地址，返回空串。
 * 仓库种子数据里的 57 条 SOP 全部是 file_oss_id = 0，因此都会走到这里。
 */
const noFileTip = computed(() => {
  const code = sop.value?.sopCode ? `（${sop.value.sopCode}）` : '';
  return `该 SOP${code} 没有可预览的正文：尚未上传附件或附件已失效。请在「文档管理 → SOP文档」中为该 SOP「新增版本」并上传文件。`;
});

const loadSop = async () => {
  loading.value = true;
  sop.value = null;
  try {
    let res: any;
    if (stepRecordId.value) {
      res = await getSopByStep(stepRecordId.value);
    } else if (processId.value) {
      res = await getSopByProcess(processId.value);
    } else if (sopId.value) {
      // 列表页「查阅」入口：直接按 SOP ID 打开(走查 document/{sopId})
      res = await getDocument(sopId.value);
    } else {
      proxy!.$modal.msgWarning('缺少 processId 或 stepRecordId 或 sopId 参数');
      return;
    }
    sop.value = res.data;
    // 登记查阅(signature_id=null, 预留 P0-6)
    // MES-SOP-01：原先空 catch 会把 403（权限码错配 mes:sop:viewlog:list）等失败彻底吞掉，
    // 表现为「页面正常但 sop_view_log 无行、es_signature 无 SOP_VIEW」。
    // 现改为：失败打 warn 日志 + 自动重试一次 + 页面可见提示，避免此类静默失败复发。
    if (sop.value?.sopId) {
      const payload = {
        sopId: sop.value.sopId,
        versionNo: sop.value.currentVersion,
        woId: woId.value,
        woNo: woNo.value,
        processId: processId.value || undefined,
        workstationId: workstationId.value
      };
      submitViewLog(payload);
    }
  } catch (e) {
    proxy!.$modal.msgError('SOP加载失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 提交查阅登记：失败自动重试一次，仍失败则记录状态并在页面给出可读提示。
 * <p>MES-SOP-01：原实现为空 catch，403（查阅登记端点权限码与查看器菜单权限码错配）
 * 会被彻底吞掉 —— 页面看起来正常，但 sop_view_log 无行、es_signature 无 SOP_VIEW。</p>
 */
function submitViewLog(payload: any) {
  recordViewLog(payload)
    .then(() => {
      viewLogFailed.value = false;
    })
    .catch((err: any) => {
      console.warn('[SOP查阅登记] 首次提交失败，准备重试一次', err);
      recordViewLog(payload)
        .then(() => {
          viewLogFailed.value = false;
        })
        .catch((retryErr: any) => {
          console.error('[SOP查阅登记] 重试后仍失败，本次查阅未记录', retryErr);
          viewLogFailed.value = true;
          proxy!.$modal.msgWarning('SOP 查阅登记失败，本次查阅未记录（可点「重试登记」补登）');
        });
    });
}

/** 手动补登查阅记录（首次+重试均失败后使用） */
function retryViewLog() {
  if (!sop.value?.sopId) {
    proxy!.$modal.msgWarning('当前没有可登记的SOP');
    return;
  }
  submitViewLog({
    sopId: sop.value.sopId,
    versionNo: sop.value.currentVersion,
    woId: woId.value,
    woNo: woNo.value,
    processId: processId.value || undefined,
    workstationId: workstationId.value
  });
}

const goBack = () => {
  router.back();
};

// 路由参数变化时重新加载(如工位切换工序、列表连续查阅不同SOP)
watch(
  () => route.query,
  () => {
    if (route.query.processId || route.query.stepRecordId || route.query.sopId) {
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

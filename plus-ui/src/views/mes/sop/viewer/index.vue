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
            <!-- MES-SOP-02 门禁前置：显式「我已阅读并确认」，由查阅登记端点产出 SOP_VIEW 电子签名 -->
            <el-tag v-if="viewLogConfirmed" type="success" size="small" class="mr-2">已确认阅读 {{ confirmedTime }}</el-tag>
            <el-button v-if="sop && !viewLogConfirmed" type="primary" icon="Select" :loading="viewLogSubmitting" @click="confirmRead"
              >我已阅读并确认</el-button
            >
            <!-- MES-SOP-01：确认/登记失败不再静默，给出可见状态与补登入口 -->
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
        <!-- MES-SOP-02：未确认前给出醒目提示 —— 配置了 SOP_ACKNOWLEDGED 门禁的工序会因缺少
             非空 signature_id 的查阅记录而拒绝进站，用户必须知道「还没点确认」 -->
        <el-alert
          v-if="!viewLogConfirmed"
          class="mb-2"
          type="warning"
          show-icon
          :closable="false"
          title="尚未确认已阅读"
          description="请阅读完作业指导书后点击右上角「我已阅读并确认」完成登记；未确认时，配置了 SOP 已阅门禁的工序将无法进站。"
        />
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
/** MES-SOP-02：确认提交中 */
const viewLogSubmitting = ref(false);
/** MES-SOP-02：已确认的文档键（sopId#version），切换 SOP/版本自动回到未确认态 */
const confirmedDocKey = ref('');
/** 确认时间（展示用） */
const confirmedTime = ref('');
/** 当前文档键 */
const docKey = computed(() => (sop.value?.sopId ? `${sop.value.sopId}#${sop.value.currentVersion ?? ''}` : ''));
/** 是否已确认阅读（后端按 (operatorId, sopId, currentVersion) 且 signature_id 非空判定） */
const viewLogConfirmed = computed(() => !!docKey.value && confirmedDocKey.value === docKey.value);

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
    // MES-SOP-02 门禁前置：查阅登记改为「显式确认」触发（右上角「我已阅读并确认」按钮），
    // 不再在打开页面时隐式登记 —— 门禁按 (operatorId, sopId, currentVersion) 且 signature_id
    // 非空判定「已阅确认」，隐式登记会把「打开过」当成「已确认」。
  } catch (e) {
    proxy!.$modal.msgError('SOP加载失败');
  } finally {
    loading.value = false;
  }
};

/** 组装查阅登记入参（sopId 为雪花ID，保持字符串透传由 axios 序列化） */
function buildViewLogPayload() {
  return {
    sopId: sop.value!.sopId,
    versionNo: sop.value?.currentVersion,
    woId: woId.value,
    woNo: woNo.value,
    processId: processId.value || undefined,
    workstationId: workstationId.value
  };
}

/**
 * 显式「我已阅读并确认」（MES-SOP-02 门禁前置）。
 * <p>调用查阅登记端点 `/mes/sop/view-log`，后端据正文哈希产出 SOP_VIEW 电子签名；
 * 门禁 SOP_ACKNOWLEDGED 按 (operatorId, sopId, currentVersion) 且 signature_id 非空判定已阅。</p>
 * <p>失败不静默（MES-SOP-01）：打 warn/error 日志 + 自动重试一次 + 页面可见提示与补登入口。</p>
 */
async function confirmRead(): Promise<boolean> {
  if (!sop.value?.sopId) {
    proxy!.$modal.msgWarning('当前没有可确认的SOP');
    return false;
  }
  viewLogSubmitting.value = true;
  try {
    const ok = await submitViewLog(buildViewLogPayload());
    if (ok) {
      confirmedDocKey.value = docKey.value;
      confirmedTime.value = new Date().toLocaleString();
      proxy!.$modal.msgSuccess('已确认阅读，确认记录已登记');
    }
    return ok;
  } finally {
    viewLogSubmitting.value = false;
  }
}

/**
 * 提交查阅登记：失败自动重试一次，仍失败则记录状态并在页面给出可读提示。
 * <p>MES-SOP-01：原实现为空 catch，403（查阅登记端点权限码与查看器菜单权限码错配）
 * 会被彻底吞掉 —— 页面看起来正常，但 sop_view_log 无行、es_signature 无 SOP_VIEW。</p>
 */
async function submitViewLog(payload: ReturnType<typeof buildViewLogPayload>): Promise<boolean> {
  try {
    await recordViewLog(payload);
    viewLogFailed.value = false;
    return true;
  } catch (err) {
    console.warn('[SOP已阅确认] 首次提交失败，准备重试一次', err);
  }
  try {
    await recordViewLog(payload);
    viewLogFailed.value = false;
    return true;
  } catch (retryErr) {
    console.error('[SOP已阅确认] 重试后仍失败，本次确认未登记', retryErr);
    viewLogFailed.value = true;
    proxy!.$modal.msgWarning('SOP 已阅确认登记失败，本次确认未记录（可点「重试登记」补登）');
    return false;
  }
}

/** 手动补登（首次+重试均失败后使用），成功后同样进入已确认态 */
function retryViewLog() {
  confirmRead();
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

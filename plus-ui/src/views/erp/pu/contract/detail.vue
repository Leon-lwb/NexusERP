<template>
  <div class="app-container" v-loading="loading">
    <!-- 顶部返回：本页没有侧边栏入口（动态路由 + activeMenu），只能从采购合同列表跳进来 -->
    <div class="mb12">
      <el-button link icon="ArrowLeft" @click="goBack">返回合同列表</el-button>
    </div>

    <!-- 合同基本信息（只读） -->
    <el-card shadow="never" class="mb12">
      <template #header><span class="card-title">合同基本信息</span></template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="合同编号">{{ contract.contractNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合同名称">{{ contract.contractName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合同类型">
          <dict-tag :options="contract_type" :value="contract.contractType" />
        </el-descriptions-item>
        <el-descriptions-item label="供应商名称">{{ contract.vendorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合同状态">
          <dict-tag :options="pu_contract_status" :value="contract.status" />
        </el-descriptions-item>
        <el-descriptions-item label="合同金额(含税)">{{ formatAmount(contract.totalAmount, contract.currency) }}</el-descriptions-item>
        <el-descriptions-item label="税额合计">{{ contract.taxAmount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="不含税金额">{{ contract.amountExcludingTax ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="签订日期">{{ contract.signDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="生效日期">{{ contract.effectiveDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="失效日期">{{ contract.expiryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="付款条款" :span="3">{{ contract.paymentTerms || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ contract.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 合同明细（随合同详情一次返回，只读） -->
    <el-card shadow="never" class="mb12">
      <template #header
        ><span class="card-title">合同明细（{{ lines.length }}）</span></template
      >
      <el-table :data="lines" size="small" border max-height="380">
        <el-table-column label="行号" type="index" width="60" align="center" />
        <el-table-column label="物料编码" prop="materialCode" min-width="130" />
        <el-table-column label="物料名称" prop="materialName" min-width="130" />
        <el-table-column label="规格" prop="materialSpec" min-width="120" show-overflow-tooltip />
        <el-table-column label="数量" prop="quantity" width="100" align="right" />
        <el-table-column label="单位" prop="unit" width="80" align="center" />
        <el-table-column label="单价" prop="unitPrice" width="110" align="right" />
        <el-table-column label="税率%" prop="taxRate" width="80" align="right" />
        <el-table-column label="行金额(含税)" prop="lineAmount" width="120" align="right" />
        <el-table-column label="税额" prop="taxAmount" width="100" align="right" />
        <el-table-column label="不含税金额" prop="lineAmountExcludingTax" width="110" align="right" />
        <el-table-column label="交货日期" prop="deliveryDate" width="120" align="center" />
        <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!lines.length" description="暂无合同明细" :image-size="50" />
    </el-card>

    <!-- 合同正文：上传 / 签署 / 下载 / 验签 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">合同正文（电子签章）</span>
          <div class="card-header__actions">
            <!-- 文件选择只给 .pdf 提示；是否真的是 PDF 由服务端准入判定，其报错原样呈现给用户 -->
            <el-upload
              ref="uploadRef"
              :show-file-list="false"
              :auto-upload="false"
              :limit="1"
              accept=".pdf,application/pdf"
              :on-change="handleFileChange"
            >
              <el-button type="primary" plain icon="Upload" :loading="uploading" :disabled="uploading" v-hasPermi="['system:sign:document:upload']">
                {{ doc ? '上传新版本' : '上传正文(PDF)' }}
              </el-button>
            </el-upload>
            <el-button type="primary" icon="EditPen" :disabled="!doc" @click="openSignDialog" v-hasPermi="['system:sign:document:sign']"
              >签署</el-button
            >
            <el-button icon="Download" :disabled="!doc" @click="handleDownload" v-hasPermi="['system:sign:document:list']">下载</el-button>
            <el-button
              type="success"
              plain
              icon="CircleCheck"
              :disabled="!doc"
              :loading="verifying"
              @click="handleVerify"
              v-hasPermi="['system:sign:document:verify']"
              >验签</el-button
            >
          </div>
        </div>
      </template>

      <el-alert
        v-if="uploadError"
        class="mb12"
        type="error"
        show-icon
        :closable="true"
        :title="'正文上传被拒绝：' + uploadError"
        @close="uploadError = ''"
      />

      <!-- 空态：尚无正文 -->
      <el-empty v-if="!doc" :image-size="70" description="该合同尚未上传正文（仅支持 PDF），上传后可进行盖章签署与验签" />

      <template v-else>
        <el-descriptions v-loading="docLoading" :column="3" border size="small">
          <el-descriptions-item label="正文文件">{{ doc.fileName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ formatSize(doc.fileSize) }}</el-descriptions-item>
          <el-descriptions-item label="页数">{{ doc.pageCount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="正文版本">V{{ doc.docVersion ?? 1 }}</el-descriptions-item>
          <el-descriptions-item label="上传时间">{{ doc.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="签署状态">
            <el-tag :type="DOC_SIGN_STATUS_TAG[signStatusKey]" size="small">{{ DOC_SIGN_STATUS_TEXT[signStatusKey] }}</el-tag>
            <span v-if="doc.signedOssId" class="tip">下载将取签署后正文</span>
          </el-descriptions-item>
          <el-descriptions-item label="原文 SM3" :span="3"
            ><span class="mono">{{ doc.fileHash || '-' }}</span></el-descriptions-item
          >
        </el-descriptions>

        <!-- 验签结论：非 OK 一律按「文档已被改动」呈现 -->
        <el-alert v-if="verifyResult" class="mt12" :type="verdict.danger ? 'error' : 'success'" show-icon :closable="false">
          <template #title>
            <span class="verdict-title">
              验签结论：{{ verdict.label }}
              <span v-if="verifyResult.passed === false" class="verdict-badge">未通过</span>
            </span>
          </template>
          <div class="verdict-body">
            <div>{{ verdict.desc }}</div>
            <div class="mt4">
              签名者：{{ verifyResult.signerSubject || '-' }} · 签署时间：{{ verifyResult.signedAt || '-' }} · 摘要算法：{{
                verifyResult.digestAlgorithm || '-'
              }}
              · 台账摘要一致：{{ verifyResult.hashPassed ? '是' : '否' }}
            </div>
            <ul v-if="verifyResult.messages?.length" class="verdict-body__messages">
              <li v-for="(msg, index) in verifyResult.messages" :key="index">{{ msg }}</li>
            </ul>
          </div>
        </el-alert>
      </template>
    </el-card>

    <!-- 签署对话框：复用 PdfSealPlacer（内部已串联 印章选择 → 二级认证 → 签署接口） -->
    <el-dialog v-model="signVisible" title="合同正文签署" width="1180px" append-to-body destroy-on-close>
      <PdfSealPlacer
        v-if="signVisible && doc"
        :doc-id="doc.docId"
        :biz-type="BIZ_TYPE"
        :oss-id="doc.fileOssId"
        v-model:placements="placements"
        @success="handleSignSuccess"
      />
      <template #footer>
        <el-button @click="signVisible = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="PuContractDetail">
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import type { UploadFile } from 'element-plus';
import { getContract } from '@/api/erp/pu/contract';
import type { PuContractLineVo, PuContractVo } from '@/api/erp/pu/contract/types';
import {
  DOC_SIGN_STATUS_TAG,
  DOC_SIGN_STATUS_TEXT,
  getCurrentDocument,
  OSS_DOWNLOAD_PERMI,
  resolveDocumentOssId,
  uploadDocument,
  verifyDocumentSignature,
  type DocumentVerifyVO,
  type EsSignDocumentVO,
  type SealPlacement
} from '@/api/system/sign/document';
import { listSignature } from '@/api/system/sign/record';
import PdfSealPlacer from '@/components/sign/PdfSealPlacer.vue';
import auth from '@/plugins/auth';

/** 业务类型：采购合同（后端 SignBizType.PU_CONTRACT） */
const BIZ_TYPE = 'PU_CONTRACT';
/** 签署记录查询权限：本页只拿到文档权限时无法回查签名ID，用于给出可操作的提示而不是静默失败 */
const RECORD_LIST_PERMI = 'system:sign:record:list';
/** 正文大小上限（字节）：与后端 `spring.servlet.multipart.max-file-size` 及
 * `EsSignDocumentServiceImpl.MAX_FILE_SIZE` 同值（10MB），仅用于客户端预检（服务端仍然把关） */
const MAX_PDF_SIZE = 10 * 1024 * 1024;

const route = useRoute();
const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

/**
 * 本页有两个入口要读正文（签署对话框取正文给 pdfjs、下载取正文文件），而正文取流走系统 OSS 端点，
 * 除签章权限外还需 `system:oss:download`（签章模块的 `system:sign:document:*` 不含它）。
 * 缺它时后端以 HTTP 200 + code=403 拒绝，前端不预检的话 pdfjs 只会报「不是有效的 PDF」。
 */
const hasOssDownload = () => auth.hasPermi(OSS_DOWNLOAD_PERMI);

const contractId = computed(() => String(route.query.contractId ?? ''));

const loading = ref(false);
const contract = ref<Partial<PuContractVo>>({});
const lines = computed<PuContractLineVo[]>(() => contract.value.puContractLineList ?? []);

const docLoading = ref(false);
/** 当前生效版本的正文；该合同尚无正文时为 null */
const doc = ref<EsSignDocumentVO | null>(null);
/** 签署状态码：无正文时按未签展示（0未签 1部分签 2已签） */
const signStatusKey = computed(() => doc.value?.signStatus || '0');

const uploadRef = ref();
const uploading = ref(false);
/** 上传被服务端拒绝时的原文（非 PDF / 超限 / 校验不过），就地展示而不是只弹一次提示 */
const uploadError = ref('');

const signVisible = ref(false);
const placements = ref<SealPlacement[]>([]);
/** 本次签署产生的签名ID；验签以它为主键，页面重开时按业务键回查签名记录补齐 */
const signatureId = ref<string | number | undefined>();

const verifying = ref(false);
const verifyResult = ref<DocumentVerifyVO | null>(null);

/** 合同类型/状态走字典（与列表页同一套：contract_type 即采购合同类型字典） */
const { contract_type, pu_contract_status } = toRefs<any>(proxy.useDict('contract_type', 'pu_contract_status'));

/**
 * 验签结论文案（对应后端 DocVerifyVerdict）。
 * <p>只有 OK 是「未被改动」；其余一律按篡改/异常呈现，magnitude 的差别写在描述里。</p>
 */
interface VerdictMeta {
  label: string;
  desc: string;
  danger: boolean;
}
const VERDICT: Record<string, VerdictMeta> = {
  OK: { label: '签名有效', desc: 'CMS 验签通过，且文件没有任何未被签名覆盖的字节：文档自签署后未被修改。', danger: false },
  MODIFIED_IN_PLACE: {
    label: '签名区间被改写',
    desc: '被签名的字节已被等长改写，内容与签署时不一致 —— 最高级别的篡改信号，请立即核查。',
    danger: true
  },
  REVISION_APPENDED: {
    label: '签名后被追加修订',
    desc: '签名覆盖区间未被改动，但签署之后文件又被追加了新的 PDF 修订：已不是签署时的那个文件。',
    danger: true
  },
  UNSIGNED_TRAILING_BYTES: {
    label: '存在未被签名覆盖的字节',
    desc: '文件尾部有未签名字节，但未构成新的 PDF 修订（Acrobat 多半仍显示未修改）。严重程度低于原地改写，仍需人工确认。',
    danger: true
  },
  TRUNCATED: { label: '文件被截断', desc: '签名覆盖范围超出文件长度，被签名的原始字节已不存在，签名彻底失效。', danger: true },
  INVALID_SIGNATURE: { label: '签名无效', desc: 'CMS 层验签不通过（证书缺失、算法不支持或签名结构损坏），无法确认文档来源。', danger: true },
  NO_SIGNATURE: { label: '未找到签名', desc: '文件中没有签名，或签名域内没有任何 CMS 内容。', danger: true }
};
const verdict = computed<VerdictMeta>(
  () => VERDICT[verifyResult.value?.verdict || ''] ?? { label: '未知结论', desc: '后端返回了本页未登记的验签结论，请人工确认。', danger: true }
);

const formatSize = (size?: number) => {
  if (size === undefined || size === null) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
};

const formatAmount = (amount?: number, currency?: string) => `${amount ?? '-'} ${currency || ''}`.trim();

const goBack = () => router.push({ path: '/erp/puBiz/contract' });

/** 加载轮次令牌：A 合同的响应在途时快速切到 B，迟到的 A 响应不得覆盖 B 的数据（否则会在 B 页面上签 A 的文档） */
let loadSeq = 0;

const loadContract = async (token: number) => {
  loading.value = true;
  try {
    const { data } = await getContract(contractId.value);
    if (token !== loadSeq) return;
    contract.value = data ?? {};
  } finally {
    if (token === loadSeq) {
      loading.value = false;
    }
  }
};

const loadDocument = async (token: number) => {
  docLoading.value = true;
  try {
    // 尚无正文时后端 data 为 null（不是空对象）
    const { data } = await getCurrentDocument(BIZ_TYPE, contractId.value);
    if (token !== loadSeq) return;
    doc.value = data ?? null;
  } finally {
    if (token === loadSeq) {
      docLoading.value = false;
    }
  }
};

// ===== 上传正文 =====

const handleFileChange = async (file: UploadFile) => {
  const raw = file.raw;
  if (!raw) return;
  await doUpload(raw);
};

const doUpload = async (file: File) => {
  uploadError.value = '';
  // 客户端预检：超过 multipart 上限的请求会在进入 Controller 之前被容器拒绝（见后端
  // GlobalExceptionHandler 的 MaxUploadSizeExceededException 处理器），这里只是让用户更早、
  // 更具体地知道原因（服务端仍然把关，预检不是准入判定）。
  if (file.size > MAX_PDF_SIZE) {
    uploadError.value = `PDF 文件不能超过 ${MAX_PDF_SIZE / 1024 / 1024}MB（当前 ${file.size} 字节）`;
    proxy?.$modal.msgError(uploadError.value);
    uploadRef.value?.clearFiles();
    return;
  }
  // 换版会顶掉当前生效版本：已签署的合同先确认（旧版本与其签署记录仍留在台账里，可回溯）
  if (doc.value) {
    try {
      await ElMessageBox.confirm('上传新版本后，当前生效的正文将被替换（旧版本及其签署记录仍保留在台账中，可回溯）。是否继续？', '上传新版本', {
        type: 'warning'
      });
    } catch {
      uploadRef.value?.clearFiles();
      return;
    }
  }
  uploading.value = true;
  try {
    await uploadDocument(file, BIZ_TYPE, contractId.value);
    // 换版后上一轮的签名/验签结论不再对应当前正文，清掉以免误读
    signatureId.value = undefined;
    verifyResult.value = null;
    proxy?.$modal.msgSuccess('正文上传成功');
    await loadDocument(loadSeq);
  } catch (error: any) {
    // 服务端准入（扩展名/魔数/大小/摘要）的原文原样呈现，前端不预判文件类型
    uploadError.value = error?.message || '正文上传失败';
  } finally {
    uploading.value = false;
    uploadRef.value?.clearFiles();
  }
};

// ===== 下载 / 签署 / 验签 =====

const handleDownload = async () => {
  if (!doc.value?.docId) return;
  // 下载按钮由 `system:sign:document:list` 把关，但 `/resource/oss/download/{ossId}` 另需 OSS 下载权限：
  // 两个权限是「与」关系，而 v-hasPermi 是「或」（数组里任一满足即显示），故只能在这里显式判断并说明缺哪一条。
  if (!hasOssDownload()) {
    proxy?.$modal.msgError(`缺少「${OSS_DOWNLOAD_PERMI}」权限，无法下载合同正文，请联系管理员授权。`);
    return;
  }
  // 后端 /download 已按「已签署取签署后文件、否则取原文」给 ossId，前端不再自行判断
  const { data: ossId } = await resolveDocumentOssId(doc.value.docId);
  if (!ossId) {
    proxy?.$modal.msgWarning('该正文没有可下载的文件');
    return;
  }
  proxy?.$download?.oss?.(ossId);
};

const openSignDialog = async () => {
  if (!doc.value?.docId) {
    proxy?.$modal.msgWarning('请先上传正文后再签署');
    return;
  }
  // 签署要在页面上放置印章，必须先能读到正文（OSS 下载权限）；缺它时 pdfjs 只会报「不是有效的 PDF」，
  // 用户无从得知是权限问题，故在打开签署对话框之前就把缺哪条权限说清楚。
  if (!hasOssDownload()) {
    proxy?.$modal.msgError(`缺少「${OSS_DOWNLOAD_PERMI}」权限，无法读取合同正文用于签署，请联系管理员授权。`);
    return;
  }
  if (doc.value.signStatus === '2') {
    try {
      await ElMessageBox.confirm(
        '该正文已签署。继续签署将在当前签署后文件上追加盖章（先前签署人的印章与数字签名会保留），并新增一条签名记录。是否继续？',
        '追加签署',
        { type: 'warning' }
      );
    } catch {
      return;
    }
  }
  placements.value = [];
  signVisible.value = true;
};

const handleSignSuccess = async (newSignatureId: string | number) => {
  signVisible.value = false;
  signatureId.value = newSignatureId;
  verifyResult.value = null;
  placements.value = [];
  proxy?.$modal.msgSuccess('签署成功，已生成签署后正文');
  await loadDocument(loadSeq);
};

/**
 * 取本次验签要校验的签名ID。
 * <p>文档 VO 不下发 signatureId，而文件级验签接口以 signatureId 为主键：
 * 本次签署过就直接用返回值，否则回查签名记录按 docId 对齐（该接口另需签名记录查询权限）。</p>
 */
const resolveSignatureId = async (): Promise<string | number | undefined> => {
  if (signatureId.value) return signatureId.value;
  const docId = doc.value?.docId;
  if (!docId) return undefined;
  if (!auth.hasPermi(RECORD_LIST_PERMI)) {
    proxy?.$modal.msgWarning('无法定位该正文的签名记录（缺少签名记录查询权限），请在本页重新签署一次后再验签');
    return undefined;
  }
  const { data } = await listSignature(BIZ_TYPE, contractId.value);
  // 后端按签署时间正序返回，取该正文（docId）的最后一条
  const mine = (data ?? []).filter((item) => String(item.docId) === String(docId));
  const latest = mine[mine.length - 1];
  if (!latest) {
    proxy?.$modal.msgWarning('该正文没有对应的签名记录，无法验签');
    return undefined;
  }
  signatureId.value = latest.signatureId;
  return latest.signatureId;
};

const handleVerify = async () => {
  const id = await resolveSignatureId();
  if (!id) return;
  verifying.value = true;
  try {
    const { data } = await verifyDocumentSignature(id);
    verifyResult.value = data ?? null;
  } finally {
    verifying.value = false;
  }
};

/** 按当前 query 里的合同ID加载（缺 ID 时直接给提示，不留在空页面上） */
const loadAll = async () => {
  if (!contractId.value) {
    proxy?.$modal.msgError('缺少合同ID，无法打开详情页');
    return;
  }
  const token = ++loadSeq;
  await Promise.all([loadContract(token), loadDocument(token)]);
};

/**
 * 详情页只靠 query 传参（`router.push({ path, query: { contractId } })`），
 * 从 A 合同跳到 B 合同时 vue-router 会复用同一个组件实例（只有 mounted 不会再跑），
 * 故必须监听参数变化重载，并把上一份的签署/验签/落位状态清干净，避免 A 的数据挂在 B 上。
 */
watch(contractId, async () => {
  signatureId.value = undefined;
  verifyResult.value = null;
  placements.value = [];
  signVisible.value = false;
  doc.value = null;
  uploadError.value = '';
  await loadAll();
});

onMounted(loadAll);
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}
.mt4 {
  margin-top: 4px;
}
.mt12 {
  margin-top: 12px;
}
.card-title {
  font-weight: 600;
}
.card-header {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}
.card-header__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.tip {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.mono {
  font-family: ui-monospace, sfmono-regular, menlo, monospace;
  word-break: break-all;
}
.verdict-title {
  font-weight: 600;
}
.verdict-badge {
  padding: 0 6px;
  margin-left: 8px;
  color: #fff;
  font-size: 12px;
  background: var(--el-color-danger);
  border-radius: 3px;
}
.verdict-body__messages {
  padding-left: 18px;
  margin: 6px 0 0;
}
</style>

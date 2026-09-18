<template>
  <div class="p-2">
    <el-card shadow="never">
      <!-- 查询 -->
      <el-form ref="queryRef" :model="queryParams" :inline="true">
        <el-form-item label="业务类型" prop="bizType">
          <el-select v-model="queryParams.bizType" placeholder="请选择业务类型" style="width: 200px" @change="handleQuery">
            <el-option v-for="item in BIZ_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务单据ID" prop="bizId">
          <el-input v-model="queryParams.bizId" placeholder="请输入业务单据ID" clearable style="width: 200px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
          <span v-hasPermi="['system:sign:record:verify']">
            <el-tooltip
              placement="top"
              content="逐条重算本次筛选范围内每条签名记录自身的链哈希，并校验其与租户签名链上真实前驱的衔接；签名链为租户全局，不是单据内的独立链。"
            >
              <el-button type="warning" plain icon="CircleCheck" :loading="chainVerifying" @click="handleVerifyChain">全链验签</el-button>
            </el-tooltip>
          </span>
        </el-form-item>
      </el-form>
      <div class="mb8 text-gray-400 text-xs">
        签名记录只读（写入由各业务动作触发，链 append-only）。业务单据ID 填业务主键 ID，不是单据编号；留空表示查看该业务类型下的全部签名。
      </div>

      <!-- 验签结果 -->
      <el-alert
        v-if="verifyResult"
        class="mb8"
        :type="verifyResult.passed ? 'success' : 'error'"
        :closable="true"
        show-icon
        @close="closeVerifyResult"
      >
        <template #title>{{ verifyTitle }}</template>
        <template #default>
          <div>{{ verifyScope }}</div>
          <div>链校验：{{ verifyResult.chainPassed ? '通过' : '未通过' }}</div>
          <div v-for="(message, index) in verifyResult.messages ?? []" :key="index">{{ message }}</div>
        </template>
      </el-alert>

      <el-table v-loading="loading" :data="list" empty-text="该筛选条件下暂无签名记录">
        <el-table-column label="签名ID" align="center" prop="signatureId" width="200" show-overflow-tooltip />
        <el-table-column label="签署人" align="center" prop="signerName" show-overflow-tooltip />
        <el-table-column label="印章" align="center" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.sealType" :type="sealTypeTag(scope.row.sealType)">{{ sealTypeText(scope.row.sealType) }}</el-tag>
            <span v-else class="text-gray-400">—</span>
          </template>
        </el-table-column>
        <el-table-column label="签署含义" align="center" prop="signatureMeaning" show-overflow-tooltip />
        <el-table-column label="认证方式" align="center" width="110">
          <template #default="scope">{{ authMethodText(scope.row.authMethod) }}</template>
        </el-table-column>
        <el-table-column label="签署时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.signedAt) }}</template>
        </el-table-column>
        <el-table-column label="IP" align="center" prop="signIp" width="140" show-overflow-tooltip />
        <el-table-column label="链哈希" align="center" min-width="200" :show-overflow-tooltip="false">
          <template #default="scope">
            <div v-if="scope.row.chainHash">
              <el-tooltip :content="scope.row.chainHash" placement="top">
                <span>{{ shortHash(scope.row.chainHash) }}</span>
              </el-tooltip>
              <el-button link type="primary" icon="DocumentCopy" v-copyText="scope.row.chainHash" v-copyText:callback="onCopySuccess" />
            </div>
            <span v-else class="text-gray-400">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100" class-name="small-padding fixed-width" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button
              v-hasPermi="['system:sign:record:verify']"
              link
              type="primary"
              icon="CircleCheck"
              :loading="verifyingId === scope.row.signatureId"
              @click="handleVerify(scope.row)"
              >验签</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup name="SignRecord" lang="ts">
import { listSignature, verifySignature, verifySignatureChain, type SignatureVO, type VerifyResultVO } from '@/api/system/sign/record';
import type { FormInstance } from 'element-plus';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

/** 业务类型（后端 SignBizType 全集） */
const BIZ_TYPE_OPTIONS = [
  { value: 'APPROVAL', label: '流程审批' },
  { value: 'SOP_VIEW', label: 'SOP查阅' },
  { value: 'NCR_DISPOSITION', label: 'NCR处置' },
  { value: 'SO_CONTRACT', label: '销售合同' },
  { value: 'PU_CONTRACT', label: '采购合同' }
];
/**
 * 认证方式文案：仅映射本期支持的认证方式（SignAuthMethod.SUPPORTED = LOGIN_ONLY / PASSWORD），
 * 其余历史值原样展示，避免把未知取值渲染成空白。
 */
const AUTH_METHOD_TEXT: Record<string, string> = {
  LOGIN_ONLY: '仅登录态',
  PASSWORD: '登录密码'
};
const SEAL_TYPE_TEXT: Record<string, string> = { '1': '个人章', '2': '公司章' };

const queryRef = ref<FormInstance>();
const loading = ref(false);
/** 全链验签进行中 */
const chainVerifying = ref(false);
/** 正在单条验签的签名ID */
const verifyingId = ref<string | number>('');
const list = ref<SignatureVO[]>([]);
/** 最近一次验签结果（重新查询时清空，避免陈旧结论误导） */
const verifyResult = ref<VerifyResultVO | null>(null);
/** 本次验签的范围描述 */
const verifyScope = ref('');

const queryParams = ref<{ bizType: string; bizId?: string | number }>({
  bizType: 'APPROVAL',
  bizId: undefined
});

const authMethodText = (value?: string) => (value ? (AUTH_METHOD_TEXT[value] ?? value) : '—');
/** 印章：公司章=danger / 个人章=info / 未用章由模板渲染为「—」 */
const sealTypeText = (value?: string) => SEAL_TYPE_TEXT[value ?? ''] ?? value ?? '—';
const sealTypeTag = (value?: string) => (value === '2' ? 'danger' : 'info');
/** 链哈希过长，列表只保留前缀，完整值由 tooltip 与复制按钮提供 */
const shortHash = (hash: string) => (hash.length > 16 ? `${hash.slice(0, 16)}…` : hash);

/** 验签结果标题：失败时点明断链位置，同时给出本次校验条数 */
const verifyTitle = computed(() => {
  const result = verifyResult.value;
  if (!result) return '';
  const countText = `本次校验 ${result.checkedCount ?? 0} 条记录`;
  if (result.passed) {
    return `验签通过（${countText}）`;
  }
  return result.brokenAt != null ? `验签未通过：链在 signatureId=${result.brokenAt} 处断裂（${countText}）` : `验签未通过（${countText}）`;
});

const onCopySuccess = () => {
  proxy?.$modal.msgSuccess('链哈希已复制');
};

/** 关闭验签结果横幅 */
const closeVerifyResult = () => {
  verifyResult.value = null;
  verifyScope.value = '';
};

/** 查询签名记录（空结果是正常态，不视为错误） */
const handleQuery = async () => {
  verifyResult.value = null;
  verifyScope.value = '';
  loading.value = true;
  try {
    // 空串会被 axios 作为 bizId= 发出去、后端 Long 转换失败，故需转成 undefined 省略该参数
    const { data } = await listSignature(queryParams.value.bizType, queryParams.value.bizId || undefined);
    list.value = data ?? [];
  } finally {
    loading.value = false;
  }
};

/** 单条验签 */
const handleVerify = async (row: SignatureVO) => {
  verifyingId.value = row.signatureId;
  try {
    const { data } = await verifySignature(row.signatureId);
    verifyResult.value = data;
    verifyScope.value = `范围：签名ID=${row.signatureId}（单条验签，校验该记录自身哈希及其在租户签名链中的衔接）`;
  } finally {
    verifyingId.value = '';
  }
};

/**
 * 全链验签：校验本次筛选范围内的每条签名记录及其在租户签名链中的衔接。
 * <p>链是租户全局的（一条链横跨该租户全部业务），因此这里不是「单据内独立链」的遍历。</p>
 */
const handleVerifyChain = async () => {
  chainVerifying.value = true;
  try {
    const { data } = await verifySignatureChain(queryParams.value.bizType, queryParams.value.bizId || undefined);
    verifyResult.value = data;
    const bizText = `业务类型=${queryParams.value.bizType}${queryParams.value.bizId ? `，业务单据ID=${queryParams.value.bizId}` : '（全部单据）'}`;
    verifyScope.value = `范围：${bizText}（全链验签，链路为租户全局）`;
  } finally {
    chainVerifying.value = false;
  }
};

onMounted(handleQuery);
</script>

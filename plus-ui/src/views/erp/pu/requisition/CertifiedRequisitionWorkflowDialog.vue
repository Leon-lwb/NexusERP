<template>
  <el-dialog
    v-model="visible"
    title="认证采购申请生命周期"
    width="780px"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :before-close="beforeClose"
  >
    <el-alert
      title="仅批准原采购申请或转换为未定价 DRAFT 订单，不录入价格、到货承诺或收货；来源、权限与幂等由服务端复核。"
      type="info"
      show-icon
      :closable="false"
      class="mb-4"
    />
    <el-alert v-if="effectiveReadonly" title="当前为只读查看，不允许在本面板批准或转单。" type="info" :closable="false" class="mb-4" />
    <el-descriptions :column="2" border>
      <el-descriptions-item label="申请单号">{{ state?.document?.requisitionNo ?? '未确认' }}</el-descriptions-item>
      <el-descriptions-item label="实际状态">{{ state?.document?.status ?? '未确认' }}</el-descriptions-item>
      <el-descriptions-item label="当前版本">{{ state?.document?.version ?? '未确认' }}</el-descriptions-item>
      <el-descriptions-item label="来源类型">{{ state?.document?.sourceType ?? '未确认' }}</el-descriptions-item>
      <el-descriptions-item label="当前租户">{{ state?.context?.tenantId ?? '未确认' }}</el-descriptions-item>
      <el-descriptions-item label="处理阶段">{{ state?.phase ?? 'IDLE' }}</el-descriptions-item>
    </el-descriptions>
    <el-alert
      v-if="state?.message || localMessage"
      :title="localMessage || state?.message || '当前操作未确认'"
      :type="state?.unknownLocked ? 'error' : 'warning'"
      :closable="false"
      show-icon
      class="mt-4"
    />
    <el-alert
      v-if="state?.unknownLocked"
      title="原请求结果未知，刷新或重新打开不会解除锁存。请由授权人员独立核对原生状态及永久事件，不重复发送或更换版本绕过。"
      type="error"
      show-icon
      :closable="false"
      class="mt-4"
    />
    <el-descriptions v-if="state?.result" :column="2" border class="mt-4">
      <el-descriptions-item label="服务端结果">{{ state.result.status }}</el-descriptions-item>
      <el-descriptions-item label="结果版本">{{ state.result.version }}</el-descriptions-item>
      <el-descriptions-item label="订单单号">{{ state.result.orderNo ?? '尚未转单' }}</el-descriptions-item>
      <el-descriptions-item label="既有结果回查">{{ state.result.replayed ? '是' : '否' }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button :disabled="busy" @click="refresh">只读刷新</el-button>
      <el-button
        v-hasPermi="['pu:requisition:approve']"
        type="primary"
        :loading="busy"
        :disabled="effectiveReadonly || busy || !state?.canApprove"
        @click="execute('approve')"
        >批准原申请</el-button
      >
      <el-button
        v-hasPermi="['pu:requisition:convert']"
        type="success"
        :loading="busy"
        :disabled="effectiveReadonly || busy || !state?.canConvert"
        @click="execute('convert')"
        >{{ state?.document?.status === 'ORDERED' ? '核验既有订单' : '转为未定价订单' }}</el-button
      >
      <el-button :disabled="busy" @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="CertifiedRequisitionWorkflowDialog">
import { computed, onBeforeUnmount, ref } from 'vue';
import { ElMessageBox } from 'element-plus';
import { getToken } from '@/utils/auth';
import {
  approveCertifiedPurchaseRequisition,
  convertCertifiedPurchaseRequisition,
  getCertifiedRequisitionWorkflowContext,
  getCertifiedRequisitionWorkflowDetail
} from '@/api/erp/pu/requisition';
import { createCertifiedRequisitionWorkflow } from './certifiedWorkflow';

const props = withDefaults(defineProps<{ readOnly?: boolean }>(), { readOnly: false });
const emit = defineEmits<{ changed: [] }>();
type WorkflowController = ReturnType<typeof createCertifiedRequisitionWorkflow>;
const visible = ref(false);
const pending = ref(false);
const openedReadOnly = ref(false);
const localMessage = ref('');
const state = ref<ReturnType<WorkflowController['snapshot']>>();
const effectiveReadonly = computed(() => props.readOnly || openedReadOnly.value);
const busy = computed(() => pending.value || state.value?.phase === 'LOADING' || state.value?.phase === 'SENDING');
let panelEpoch = 0;
let boundCredential: string | null = null;

/** 存储只在协议真正读写时访问，浏览器拒绝存储由协议封闭写入，不降级为内存重试。 */
const storage = {
  getItem: (key: string) => window.localStorage.getItem(key),
  setItem: (key: string, value: string) => window.localStorage.setItem(key, value),
  removeItem: (key: string) => window.localStorage.removeItem(key)
};

/** 始终读取实际协议快照；不自行计算审批资格、版本、数量或原生结果。 */
function syncState() {
  state.value = controller.snapshot();
}

const controller = createCertifiedRequisitionWorkflow({
  readCredential: () => getToken() ?? null,
  readContext: getCertifiedRequisitionWorkflowContext,
  readDetail: getCertifiedRequisitionWorkflowDetail,
  submit: (operation, id, version, session) =>
    operation === 'approve' ? approveCertifiedPurchaseRequisition(id, version, session) : convertCertifiedPurchaseRequisition(id, version, session),
  storage,
  onChange: (snapshot) => {
    state.value = snapshot;
  }
});
syncState();

/** 每次打开仅传精确身份，详情与权限重新向服务端获取，不信任列表或可编辑表单。 */
async function open(id: string | number, options: { readOnly?: boolean } = {}) {
  if (busy.value) return;
  const epoch = ++panelEpoch;
  openedReadOnly.value = options.readOnly === true;
  boundCredential = getToken() ?? null;
  localMessage.value = '';
  visible.value = true;
  pending.value = true;
  try {
    await controller.open(id);
  } catch {
    if (epoch === panelEpoch) localMessage.value = '当前主体或申请详情未确认，请检查查询权限和会话后只读刷新。';
  } finally {
    if (epoch === panelEpoch) {
      pending.value = false;
      syncState();
    }
  }
}

/** 刷新不发送业务写入，也不删除未知意图或切换到旧转单／Flowable路径。 */
async function refresh() {
  if (busy.value) return;
  const epoch = panelEpoch;
  pending.value = true;
  localMessage.value = '';
  try {
    await controller.refresh();
  } catch {
    if (epoch === panelEpoch) localMessage.value = '只读刷新未完成，原请求锁存保留，请检查当前会话。';
  } finally {
    if (epoch === panelEpoch) {
      pending.value = false;
      syncState();
    }
  }
}

/** 明确人工确认后才调用版本化认证入口；不自动推进下一步，不拼接原异常或凭据文案。 */
async function execute(operation: 'approve' | 'convert') {
  if (effectiveReadonly.value || busy.value || !(operation === 'approve' ? state.value?.canApprove : state.value?.canConvert)) return;
  const epoch = panelEpoch;
  let submitted = false;
  pending.value = true;
  localMessage.value = '';
  try {
    const message =
      operation === 'approve'
        ? '确认批准当前原采购申请？服务端将复核完整来源、版本和占用，不批准订单价格。'
        : state.value?.document?.status === 'ORDERED'
          ? '确认回查已转单的原订单？服务端必须恢复同一结果，不创建额外订单。未知结果时不得重发。'
          : '确认转为未定价 DRAFT 订单？不录入价格或到货承诺，不收货；结果未知时不得重发。';
    await ElMessageBox.confirm(message, '确认认证生命周期操作', { type: 'warning', confirmButtonText: '确认操作', cancelButtonText: '取消' });
    if (epoch !== panelEpoch || effectiveReadonly.value) return;
    submitted = true;
    await (operation === 'approve' ? controller.approve() : controller.convert());
    if (epoch === panelEpoch) {
      syncState();
      if (state.value?.phase === 'SUCCEEDED') emit('changed');
    }
  } catch {
    if (epoch === panelEpoch)
      localMessage.value = submitted ? '请以认证流程状态为准；未确认结果时原意图保留，禁止重复操作。' : '确认已取消或未完成，未发起生命周期写请求。';
  } finally {
    if (epoch === panelEpoch) {
      pending.value = false;
      syncState();
    }
  }
}

/** 关闭只失效当前显示；协议保留原主体意图，不将刷新／重开解释为重新允许发送。 */
function close() {
  if (busy.value) return;
  panelEpoch++;
  controller.close();
  visible.value = false;
  boundCredential = null;
  syncState();
}
function beforeClose(done: () => void) {
  if (busy.value) return;
  close();
  done();
}

/** 共享凭证变化立即失效旧显示；普通焦点恢复只读核对，不自动补发任何业务请求。 */
function onCredentialBoundary() {
  if (visible.value && (getToken() ?? null) !== boundCredential) {
    controller.invalidate();
    syncState();
  }
}
function onFocus() {
  onCredentialBoundary();
  if (visible.value && !busy.value) void refresh();
}
if (typeof window !== 'undefined') {
  window.addEventListener('storage', onCredentialBoundary);
  window.addEventListener('focus', onFocus);
}
onBeforeUnmount(() => {
  panelEpoch++;
  controller.close();
  if (typeof window !== 'undefined') {
    window.removeEventListener('storage', onCredentialBoundary);
    window.removeEventListener('focus', onFocus);
  }
});
defineExpose({ open });
</script>

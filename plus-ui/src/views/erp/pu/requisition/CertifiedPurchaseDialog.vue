<template>
  <el-dialog
    v-model="visible"
    title="可信来源创建采购申请"
    width="960px"
    :close-on-click-modal="false"
    :close-on-press-escape="state.phase !== 'submitting'"
    :show-close="state.phase !== 'submitting'"
  >
    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="只接受真实物料、供应商和需求明细 ID，由服务端完整核验来源、单位与数量。无库存记录不等于 0；本页不计算额度、不补造可信事实。旧单据的查看/维护不会升级成新认证，旧低库存生成仍暂停。"
    />
    <el-form :model="sourceForm" label-width="130px" :disabled="!!state.command || state.phase === 'blocked'" class="source-form">
      <el-form-item label="物料 ID">
        <el-input v-model="sourceForm.materialId" inputmode="numeric" placeholder="当前租户已存在的真实 materialId，保留完整字符串" />
      </el-form-item>
      <el-form-item label="供应商 ID">
        <el-input v-model="sourceForm.vendorId" inputmode="numeric" placeholder="对应真实 vendorId，不按供应商名称猜测" />
      </el-form-item>
      <el-form-item label="需求窗口截止日">
        <el-date-picker v-model="sourceForm.windowEnd" type="date" value-format="YYYY-MM-DD" placeholder="windowEnd" />
      </el-form-item>
      <el-form-item label="需求明细 ID">
        <el-input
          v-model="sourceForm.detailText"
          type="textarea"
          :rows="3"
          placeholder="真实 requirementDetailIds；每行一个或逗号分隔，不填写需求头 ID，不重复，不转为数字"
        />
      </el-form-item>
    </el-form>

    <el-alert v-if="state.message" :title="state.message" :type="messageType" show-icon :closable="false" class="status-message" />
    <el-descriptions v-if="state.requestKey" :column="1" border class="status-message">
      <el-descriptions-item label="永久请求键"
        ><span class="long-value">{{ state.requestKey }}</span></el-descriptions-item
      >
      <el-descriptions-item label="恢复边界">提交前保存原载荷；超时/断线后不会自动重试，也不能修改来源或换键盲试。</el-descriptions-item>
    </el-descriptions>

    <template v-if="state.preview">
      <el-descriptions :column="2" border class="status-message">
        <el-descriptions-item label="物料 ID">{{ state.preview.materialId }}</el-descriptions-item>
        <el-descriptions-item label="供应商 ID">{{ state.preview.vendorId }}</el-descriptions-item>
        <el-descriptions-item label="服务端快照日">{{ state.preview.snapshotDate }}</el-descriptions-item>
        <el-descriptions-item label="窗口截止日">{{ state.preview.windowEnd }}</el-descriptions-item>
        <el-descriptions-item label="库存证据" :span="2">{{ inventoryEvidence }}</el-descriptions-item>
        <el-descriptions-item label="服务端来源指纹" :span="2">
          <span class="long-value">{{ state.preview.sourceFingerprint }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <p>下表只展示服务端逐物料、逐需求原值，不跨单位求和或手工改量。修改任一来源字段后，预览立即失效。</p>
      <el-table :data="state.preview.lines" border>
        <el-table-column label="物料 ID" min-width="165">
          <template #default>{{ state.preview.materialId }}</template>
        </el-table-column>
        <el-table-column prop="detailId" label="需求明细 ID" min-width="165" />
        <el-table-column prop="baseUomId" label="基本单位 ID" min-width="165" />
        <el-table-column label="覆盖需求（基本单位）" min-width="160">
          <template #default="{ row }">{{ formatCertifiedValue(row.coveredDemandBase) }}</template>
        </el-table-column>
        <el-table-column prop="orderUomId" label="订货单位 ID" min-width="165" />
        <el-table-column label="订货数量（订货单位）" min-width="160">
          <template #default="{ row }">{{ formatCertifiedValue(row.orderQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="requiredDate" label="需求日期" width="120" />
      </el-table>
    </template>

    <el-descriptions v-if="state.result" :column="2" border class="status-message">
      <el-descriptions-item label="服务端单据 ID">{{ state.result.requisitionId }}</el-descriptions-item>
      <el-descriptions-item label="申请单号">{{ state.result.requisitionNo }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ state.result.status }}</el-descriptions-item>
      <el-descriptions-item label="响应类型">{{ state.result.replayed ? '恢复原请求（未重复创建）' : '首次创建' }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button :disabled="state.phase === 'submitting'" @click="visible = false">关闭（保留原请求）</el-button>
      <el-button v-if="state.phase === 'rejected' || state.phase === 'succeeded'" @click="handleStartNew">明确开始新请求意图</el-button>
      <el-button v-if="state.phase === 'unknown'" type="warning" v-hasPermi="['pu:requisition:add']" @click="handleRecover">明确恢复原请求</el-button>
      <el-button
        type="primary"
        plain
        :loading="state.phase === 'previewing'"
        :disabled="!!state.command || state.phase === 'blocked' || state.phase === 'previewing'"
        v-hasPermi="['pu:requisition:add']"
        @click="flow?.preview()"
        >服务端完整预览</el-button
      >
      <el-button
        type="primary"
        :loading="state.phase === 'submitting'"
        :disabled="state.phase !== 'ready'"
        v-hasPermi="['pu:requisition:add']"
        @click="handleCreate"
        >按本次可信预览创建</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="CertifiedPurchaseDialog">
import { computed, ref, reactive, watch, onMounted, onBeforeUnmount } from 'vue';
import { ElMessageBox, ElMessage } from 'element-plus';
import { useUserStore } from '@/store/modules/user';
import { getToken } from '@/utils/auth';
import { previewCertifiedPurchaseSource, createCertifiedPurchaseRequisition, getCertifiedPurchaseContext } from '@/api/erp/pu/requisition';
import {
  createCertifiedPurchaseState,
  createCertifiedPurchaseFlow,
  createCertifiedPurchaseAuthGuard,
  sameCertifiedPurchaseContext,
  parseRequirementDetailIds,
  formatCertifiedValue
} from './certifiedPurchase';

const emit = defineEmits<{ created: [] }>();
const userStore = useUserStore();
const visible = ref(false);
const state = ref(createCertifiedPurchaseState());
const sourceForm = reactive({ materialId: '', vendorId: '', windowEnd: '', detailText: '' });
let flow: ReturnType<typeof createCertifiedPurchaseFlow> | undefined;
let scope = '';
let filling = false;
let opening = false;

const authGuard = createCertifiedPurchaseAuthGuard({
  readCredential: () => {
    try {
      // 直接读取共享认证存储，与响应式认证缓存交叉核对，不能只信本 Tab 的 Pinia。
      const shared = localStorage.getItem('Admin-Token');
      return typeof shared === 'string' && shared && shared === getToken() ? shared : null;
    } catch {
      return null;
    }
  },
  readContext: getCertifiedPurchaseContext,
  onInvalidated: invalidateActiveFlow
});
const messageType = computed(() =>
  state.value.phase === 'succeeded' ? 'success' : state.value.phase === 'ready' ? 'info' : state.value.phase === 'unknown' ? 'warning' : 'error'
);
const inventoryEvidence = computed(() => {
  const evidence = state.value.preview?.inventoryEvidence;
  if (evidence === 'ORIGINAL_STOCK_LINEAGE') return '服务端核验原库存谱系';
  if (evidence === 'INITIAL_EMPTY_WITH_PERSISTENT_BIRTH') return '服务端核验持久出生及初始空库存（不是把无记录当零）';
  return '未返回库存证据说明，不能自行解释为零库存';
});

function fillSource() {
  filling = true;
  const source = state.value.source;
  Object.assign(sourceForm, {
    materialId: source.materialId,
    vendorId: source.vendorId,
    windowEnd: source.windowEnd,
    detailText: source.requirementDetailIds.join('\n')
  });
  filling = false;
}

watch(
  sourceForm,
  () => {
    if (!filling)
      flow?.editSource({
        materialId: sourceForm.materialId,
        vendorId: sourceForm.vendorId,
        windowEnd: sourceForm.windowEnd || '',
        requirementDetailIds: parseRequirementDetailIds(sourceForm.detailText)
      });
  },
  { deep: true, flush: 'sync' }
);

/** 认证失效只清内存并关闭对话框，绝不清理主体分键的原 intent。 */
function invalidateActiveFlow() {
  flow?.invalidate();
  visible.value = false;
  flow = undefined;
  scope = '';
  state.value = createCertifiedPurchaseState();
  state.value.phase = 'blocked';
  state.value.message = '当前认证/有效租户上下文已变化或未确认，原 intent 保留，请重新核验主体后恢复。';
  fillSource();
}

/** 每次打开先取无副作用只读上下文；实际主体必须与本页身份一致才读取对应原请求。 */
async function open() {
  if (opening) return;
  opening = true;
  try {
    const context = await authGuard.open();
    if (!sameCertifiedPurchaseContext(context, { tenantId: userStore.tenantId, userId: userStore.userId })) {
      authGuard.invalidate();
      ElMessage.warning('当前凭证主体与本页用户/租户不一致，请刷新后重新打开；原请求已保留。');
      return;
    }
    scope = `nexus:pu:certified-request:${context.tenantId}:${context.userId}`;
    state.value = createCertifiedPurchaseState();
    flow = createCertifiedPurchaseFlow({
      state: state.value,
      preview: async (source) => previewCertifiedPurchaseSource(source, await authGuard.verify()),
      create: async (command) => createCertifiedPurchaseRequisition(command, await authGuard.verify()),
      newRequestKey: () => 'pc-pu:' + crypto.randomUUID(),
      storageKey: scope,
      storage: {
        getItem: (key) => localStorage.getItem(key),
        setItem: (key, value) => localStorage.setItem(key, value),
        removeItem: (key) => localStorage.removeItem(key)
      }
    });
    flow.restoreSaved();
    fillSource();
    visible.value = true;
  } catch {
    // 上下文未确认时始终关闭，不读取其他主体的原请求，也不回填认证结论。
    visible.value = false;
    ElMessage.warning('当前采购认证上下文未确认，请重新登录或核对有效租户；原请求已保留。');
  } finally {
    opening = false;
  }
}

async function handleCreate() {
  const activeFlow = flow;
  if (!activeFlow) return;
  try {
    await authGuard.verify();
    if (flow !== activeFlow) return;
    if (await activeFlow.submit()) {
      if (flow === activeFlow) emit('created');
    }
  } catch {
    // 认证核验失败已经关闭活动 flow，原 intent 不删除，也不发写请求。
  }
}

async function handleRecover() {
  const activeFlow = flow;
  if (!activeFlow) return;
  try {
    await authGuard.verify();
    if (flow !== activeFlow) return;
    await ElMessageBox.confirm('将仅发送已保存的原请求键与原载荷，不重新预览、不改量、不创建新的请求意图。是否明确恢复原请求？', '恢复原请求', {
      type: 'warning',
      confirmButtonText: '恢复原请求',
      cancelButtonText: '取消'
    });
    if (flow !== activeFlow) return;
    // 确认框等待期间可能换用户或租户，确认结束后必须重新核验，发送前还会再核一次。
    await authGuard.verify();
    if (flow !== activeFlow) return;
    if (await activeFlow.recover()) {
      if (flow === activeFlow) emit('created');
    }
  } catch {
    // 用户取消或认证核验失败时不重发，继续保留原请求。
  }
}

async function handleStartNew() {
  const activeFlow = flow;
  if (!activeFlow) return;
  try {
    await authGuard.verify();
    if (flow !== activeFlow) return;
    await ElMessageBox.confirm(
      '仅在服务端已明确返回成功或拒绝后开始新意图。新意图必须重新预览，不会把旧单据升级为新认证。是否继续？',
      '开始新请求意图',
      { type: 'warning' }
    );
    if (flow !== activeFlow) return;
    const session = await authGuard.verify();
    session.assertCurrent();
    if (flow === activeFlow && activeFlow.startNew()) fillSource();
  } catch {
    // 取消确认或主体变化时保留原意图及永久键，不能清理其他主体的存储。
  }
}

async function recheckContext() {
  if (!flow) return;
  try {
    await authGuard.verify();
  } catch {
    // 前台恢复时认证或有效租户不一致，守卫已关闭活动 flow 并保留原 intent。
  }
}

function handleSharedStorage(event: StorageEvent) {
  if (event.key === null || event.key === 'Admin-Token') {
    authGuard.invalidate();
    return;
  }
  // 其他 Tab 的租户上下文广播也需要重新核验，不根据事件载荷自行认定新主体。
  return recheckContext();
}

function handleVisibility() {
  if (document.visibilityState === 'visible') return recheckContext();
}

watch(
  () => [userStore.tenantId, userStore.userId, getToken()],
  () => authGuard.invalidate(),
  { flush: 'sync' }
);

onMounted(() => {
  window.addEventListener('storage', handleSharedStorage);
  window.addEventListener('focus', recheckContext);
  document.addEventListener('visibilitychange', handleVisibility);
});
onBeforeUnmount(() => {
  authGuard.invalidate();
  window.removeEventListener('storage', handleSharedStorage);
  window.removeEventListener('focus', recheckContext);
  document.removeEventListener('visibilitychange', handleVisibility);
});

defineExpose({ open });
</script>

<style scoped>
.source-form,
.status-message {
  margin-top: 16px;
}
.long-value {
  overflow-wrap: anywhere;
}
</style>

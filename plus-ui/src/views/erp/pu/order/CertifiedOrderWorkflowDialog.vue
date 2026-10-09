<template>
  <el-dialog
    v-model="visible"
    title="认证采购订单：报价与生命周期"
    width="1080px"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :before-close="beforeClose"
  >
    <el-alert
      title="只办理原订单。报价必须来自真实供应材料；录入、不同操作人复核、批准各自单独确认，不自动创建到货或收货。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-alert v-if="effectiveReadOnly" title="当前为只读查看，不发送任何业务操作。" type="warning" :closable="false" class="mt-3" />
    <el-alert
      v-if="state?.message || localMessage"
      :title="localMessage || state?.message || ''"
      :type="state?.phase === 'SUCCEEDED' ? 'success' : 'warning'"
      :closable="false"
      class="mt-3"
    />
    <el-descriptions v-if="state?.detail" :column="3" border class="mt-3">
      <el-descriptions-item label="原订单">{{ state.detail.order.orderNo }}</el-descriptions-item>
      <el-descriptions-item label="原生状态">{{ state.detail.order.status }}</el-descriptions-item>
      <el-descriptions-item label="原生版本">{{
        state.detail.order.version == null ? 'NULL（禁止猜为0）' : state.detail.order.version
      }}</el-descriptions-item>
      <el-descriptions-item label="原申请ID">{{ state.detail.order.requisitionId }}</el-descriptions-item>
      <el-descriptions-item label="当前主体">{{ state.context?.tenantId }} / {{ state.context?.userId }}</el-descriptions-item>
      <el-descriptions-item label="业务日期">{{ state.detail.serverDate }}</el-descriptions-item>
    </el-descriptions>
    <el-alert
      v-if="state?.detail && !state.detail.priceVisible"
      title="当前角色没有报价价格可见权限；空列表不表示订单没有报价。"
      type="warning"
      :closable="false"
      class="mt-3"
    />
    <el-tabs v-model="activeTab" class="mt-3">
      <el-tab-pane label="真实供应条款录入" name="terms">
        <el-form :model="terms" label-width="150px" :disabled="locked || !state?.context?.termsAddAllowed">
          <el-row :gutter="16">
            <el-col :span="12"
              ><el-form-item label="未税单价"><el-input v-model="terms.unitPriceExcludingTax" placeholder="原报价十进制文本，不默认0" /></el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="税率"><el-input v-model="terms.taxRate" placeholder="实际税率，如0.13；不得猜测" /></el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="支持币种/汇率"><el-input model-value="仅CNY／汇率1，需要真实资料确认" disabled /></el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="供应商报价引用"><el-input v-model="terms.supplierQuoteReference" maxlength="128" /></el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="供应联系人"><el-input v-model="terms.supplierContact" maxlength="128" /></el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="承诺到货日"
                ><el-date-picker v-model="terms.promisedArrivalDate" type="date" value-format="YYYY-MM-DD" /></el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="有效期开始"><el-date-picker v-model="terms.validFrom" type="date" value-format="YYYY-MM-DD" /></el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="有效期结束"><el-date-picker v-model="terms.validUntil" type="date" value-format="YYYY-MM-DD" /></el-form-item
            ></el-col>
            <el-col :span="24"
              ><el-form-item label="证据引用"
                ><el-input v-model="terms.evidenceReference" maxlength="255" placeholder="真实附件／供应确认材料引用" /></el-form-item
            ></el-col>
            <el-col :span="24"
              ><el-form-item label="证据SHA-256"
                ><el-input v-model="terms.evidenceSha256" maxlength="64" placeholder="原材料摘要，不自行构造占位摘要" /></el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="付款条款"><el-input v-model="terms.paymentTerms" maxlength="255" /></el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="交付条款"><el-input v-model="terms.deliveryTerms" maxlength="255" /></el-form-item
            ></el-col>
          </el-row>
          <el-checkbox v-model="materialReviewed">我已核对供应商真实CNY报价、有效期、交期与证据，未以零价或猜测代替。</el-checkbox>
          <div class="mt-3">
            <el-button type="primary" :disabled="!can('terms') || !materialReviewed" @click="perform('terms')">保存不可变报价版本</el-button>
          </div>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="报价复核与批准" name="review">
        <el-table :data="state?.detail?.priceVisible ? state.detail.quotes : []" border>
          <el-table-column prop="quoteId" label="原报价ID" min-width="185" />
          <el-table-column prop="quoteVersion" label="版本" width="65" />
          <el-table-column prop="unitPriceExcludingTax" label="未税单价（原值）" min-width="135" />
          <el-table-column prop="taxRate" label="税率（原值）" width="100" />
          <el-table-column prop="promisedArrivalDate" label="承诺到货" width="120" />
          <el-table-column prop="enteredBy" label="录入人ID" min-width="175" />
          <el-table-column prop="confirmedBy" label="独立确认人ID" min-width="175" />
          <el-table-column prop="supplierQuoteReference" label="报价来源" min-width="140" />
          <el-table-column prop="evidenceReference" label="证据引用" min-width="150" />
        </el-table>
        <el-form label-width="140px" class="mt-3">
          <el-form-item label="人工选择原报价"
            ><el-select
              v-model="selectedQuoteId"
              :disabled="busy || !state?.detail?.priceVisible"
              placeholder="必须明确选择当前原报价"
              style="width: 100%"
              ><el-option
                v-for="q in state?.detail?.priceVisible ? state.detail.quotes : []"
                :key="String(q.quoteId)"
                :label="`版本${q.quoteVersion} / ${q.quoteId}`"
                :value="String(q.quoteId)" /></el-select
          ></el-form-item>
        </el-form>
        <el-descriptions v-if="selectedQuote" title="所选不可变报价原值（只读）" :column="2" border class="mt-3">
          <el-descriptions-item label="原报价ID">{{ selectedQuote.quoteId }}</el-descriptions-item>
          <el-descriptions-item label="报价所属订单ID">{{ selectedQuote.orderId }}</el-descriptions-item>
          <el-descriptions-item label="原报价版本">{{ selectedQuote.quoteVersion }}</el-descriptions-item>
          <el-descriptions-item label="报价录入人ID">{{ selectedQuote.enteredBy }}</el-descriptions-item>
          <el-descriptions-item label="原报价请求键">{{ selectedQuote.requestKey }}</el-descriptions-item>
          <el-descriptions-item label="数量（原值）">{{ selectedQuote.quantity }}</el-descriptions-item>
          <el-descriptions-item label="未税单价（原值）">{{ selectedQuote.unitPriceExcludingTax }}</el-descriptions-item>
          <el-descriptions-item label="税率（原值）">{{ selectedQuote.taxRate }}</el-descriptions-item>
          <el-descriptions-item label="币种（原值）">{{ selectedQuote.currency }}</el-descriptions-item>
          <el-descriptions-item label="汇率（原值）">{{ selectedQuote.exchangeRate }}</el-descriptions-item>
          <el-descriptions-item label="承诺到货日">{{ selectedQuote.promisedArrivalDate }}</el-descriptions-item>
          <el-descriptions-item label="有效期开始">{{ selectedQuote.validFrom }}</el-descriptions-item>
          <el-descriptions-item label="有效期结束">{{ selectedQuote.validUntil }}</el-descriptions-item>
          <el-descriptions-item label="供应商报价引用">{{ selectedQuote.supplierQuoteReference }}</el-descriptions-item>
          <el-descriptions-item label="供应联系人">{{ selectedQuote.supplierContact }}</el-descriptions-item>
          <el-descriptions-item label="证据引用">{{ selectedQuote.evidenceReference }}</el-descriptions-item>
          <el-descriptions-item label="证据SHA-256"
            ><span class="break-all">{{ selectedQuote.evidenceSha256 }}</span></el-descriptions-item
          >
          <el-descriptions-item label="付款条款">{{ selectedQuote.paymentTerms }}</el-descriptions-item>
          <el-descriptions-item label="交付条款">{{ selectedQuote.deliveryTerms }}</el-descriptions-item>
          <el-descriptions-item label="原报价Hash"
            ><span class="break-all">{{ selectedQuote.quoteHash }}</span></el-descriptions-item
          >
          <el-descriptions-item label="既有确认ID">{{ selectedQuote.confirmationId ?? '无原确认' }}</el-descriptions-item>
          <el-descriptions-item label="既有确认人ID">{{ selectedQuote.confirmedBy ?? '无原确认人' }}</el-descriptions-item>
          <el-descriptions-item label="既有确认Hash"
            ><span class="break-all">{{ selectedQuote.confirmationHash ?? '无原确认摘要' }}</span></el-descriptions-item
          >
          <el-descriptions-item label="既有复核说明"
            ><span class="whitespace-pre-wrap">{{ selectedQuote.reviewNote ?? '无原复核说明' }}</span></el-descriptions-item
          >
        </el-descriptions>
        <el-alert
          v-else-if="state?.detail?.priceVisible"
          title="请人工选择原报价后查看完整不可变条款与既有确认；不会预填新报价或本次复核说明。"
          type="info"
          :closable="false"
          class="mt-3"
        />
        <el-form label-width="140px" :disabled="locked" class="mt-3">
          <el-form-item label="独立复核说明"
            ><el-input v-model="reviewNote" type="textarea" maxlength="500" placeholder="由不同操作人核对真实完整条款与证据"
          /></el-form-item>
          <el-button type="primary" :disabled="!can('confirm')" @click="perform('confirm')">独立确认原报价</el-button>
          <el-button type="success" :disabled="!can('approve')" @click="perform('approve')">批准原订单</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="原单关闭／取消" name="terminal">
        <el-alert title="关闭／取消仍受真实来源、未履约占额、收货／退货及发票守卫约束；界面不计算或代写释放数量。" type="warning" :closable="false" />
        <div class="mt-3">
          <el-button type="warning" :disabled="!can('close')" @click="perform('close')">关闭原订单</el-button
          ><el-button type="danger" :disabled="!can('cancel')" @click="perform('cancel')">取消原订单</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
    <template #footer
      ><el-button :disabled="busy" @click="refresh">只读刷新／核对</el-button><el-button :disabled="busy" @click="close">关闭</el-button></template
    >
  </el-dialog>
</template>

<script setup lang="ts" name="CertifiedOrderWorkflowDialog">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { normalizeBusinessId } from '@/utils/certified-business';
import { ElMessageBox } from 'element-plus';
import { getToken } from '@/utils/auth';
import { useUserStore } from '@/store/modules/user';
import { getCertifiedOrderContext, getCertifiedOrderDetail, submitCertifiedOrderOperation } from '@/api/erp/pu/order';
import { createCertifiedOrderWorkflow } from './certifiedOrderWorkflow';
import type { CertifiedOrderOperation, CertifiedOrderSnapshot } from './certifiedOrderWorkflow';
import type { SupplierOrderTermsInput } from '@/api/erp/pu/order/types';

const props = withDefaults(defineProps<{ readOnly?: boolean }>(), { readOnly: false });
const emit = defineEmits<{ changed: [] }>();
const user = useUserStore();
const route = useRoute();
const pagePath = () => route.path ?? route.fullPath.split(/[?#]/, 1)[0];
const ownerPagePath = pagePath();
const active = ref(true);
const ownsPage = () => pagePath() === ownerPagePath;
let boundaryListenersAttached = false;
const visible = ref(false);
const pending = ref(false);
const openedReadOnly = ref(false);
const state = ref<CertifiedOrderSnapshot>();
const activeTab = ref('terms');
const localMessage = ref('');
const selectedQuoteId = ref('');
const reviewNote = ref('');
const materialReviewed = ref(false);
/** 仅展示有价格可见权限时人工所选的原快照；不复制到 terms/reviewNote、不默认选报价。 */
const selectedQuote = computed(() => {
  const document = state.value?.detail;
  if (!document?.priceVisible || !selectedQuoteId.value) return null;
  return document.quotes.find((quote) => String(quote.quoteId) === selectedQuoteId.value) ?? null;
});
const terms = ref<SupplierOrderTermsInput>(emptyTerms());
let panelEpoch = 0;
let boundCredential: string | null = null;
let boundActor: { tenantId: string; userId: string; token: string | null } | null = null;
let boundDocumentId: string | null = null;
let boundRouteKey: string | null = null;
let confirmationEpoch = 0;
let executingDraftEpoch: number | null = null;
function actorSnapshot() {
  return { tenantId: user.tenantId, userId: normalizeBusinessId(user.userId), token: user.token ?? null };
}
function boundScopeCurrent() {
  try {
    return (
      active.value &&
      ownsPage() &&
      visible.value &&
      boundDocumentId !== null &&
      route.fullPath === boundRouteKey &&
      (getToken() ?? null) === boundCredential &&
      JSON.stringify(actorSnapshot()) === JSON.stringify(boundActor) &&
      (executingDraftEpoch === null || executingDraftEpoch === confirmationEpoch)
    );
  } catch {
    return false;
  }
}
const effectiveReadOnly = computed(() => props.readOnly || openedReadOnly.value);
const busy = computed(() => pending.value || state.value?.phase === 'LOADING' || state.value?.phase === 'SENDING');
const locked = computed(
  () =>
    !active.value ||
    !ownsPage() ||
    !visible.value ||
    effectiveReadOnly.value ||
    busy.value ||
    !['READY', 'SUCCEEDED', 'REFUSED'].includes(state.value?.phase || 'IDLE')
);
/** 仅提供浏览器真实存储，拒绝存储时协议封闭，不降级为自动重试。 */
const storage = {
  getItem: (k: string) => window.localStorage.getItem(k),
  setItem: (k: string, v: string) => window.localStorage.setItem(k, v),
  removeItem: (k: string) => window.localStorage.removeItem(k)
};
const controller = createCertifiedOrderWorkflow({
  readCredential: () => {
    if (!boundScopeCurrent()) throw new Error('原订单页面或登录主体已变化');
    return getToken();
  },
  readContext: async (session) => {
    const actor = await getCertifiedOrderContext(session);
    if (actor.tenantId !== boundActor?.tenantId || normalizeBusinessId(actor.userId) !== boundActor?.userId)
      throw new Error('订单读取主体与打开时登录主体不一致');
    return actor;
  },
  readDetail: getCertifiedOrderDetail,
  submit: submitCertifiedOrderOperation,
  storage,
  onChange: (s) => {
    state.value = s;
  }
});
state.value = controller.snapshot();
function emptyTerms(): SupplierOrderTermsInput {
  return {
    unitPriceExcludingTax: '',
    taxRate: '',
    currency: 'CNY',
    exchangeRate: '1',
    promisedArrivalDate: '',
    validFrom: '',
    validUntil: '',
    supplierQuoteReference: '',
    supplierContact: '',
    evidenceReference: '',
    evidenceSha256: '',
    paymentTerms: '',
    deliveryTerms: ''
  };
}
/** 不默认选择报价；数据和输入分离，打开仅查询。 */
async function open(id: string | number, options: { readOnly?: boolean } = {}) {
  if (!active.value || !ownsPage() || busy.value) return;
  const turn = ++panelEpoch;
  openedReadOnly.value = options.readOnly === true;
  visible.value = true;
  pending.value = true;
  localMessage.value = '';
  selectedQuoteId.value = '';
  reviewNote.value = '';
  materialReviewed.value = false;
  terms.value = emptyTerms();
  activeTab.value = 'terms';
  try {
    boundCredential = getToken() ?? null;
    boundActor = actorSnapshot();
    boundDocumentId = normalizeBusinessId(id);
    boundRouteKey = route.fullPath;
    await controller.open(boundDocumentId, { readOnly: effectiveReadOnly.value });
  } catch {
    controller.invalidate();
    boundCredential = null;
    boundActor = null;
    boundDocumentId = null;
    boundRouteKey = null;
    localMessage.value = '订单身份或读取未经确认。';
  } finally {
    pending.value = false;
    if (turn === panelEpoch) state.value = controller.snapshot();
  }
}
async function refresh() {
  if (!boundScopeCurrent() || busy.value) return;
  const turn = panelEpoch;
  pending.value = true;
  localMessage.value = '';
  try {
    await controller.refresh();
  } finally {
    pending.value = false;
    if (turn === panelEpoch) state.value = controller.snapshot();
  }
}
function can(op: CertifiedOrderOperation) {
  if (!boundScopeCurrent() || locked.value || !state.value?.context || !state.value.detail) return false;
  const a = state.value.context,
    d = state.value.detail;
  const flag = {
    terms: 'termsAddAllowed',
    confirm: 'termsConfirmAllowed',
    approve: 'approveAllowed',
    close: 'closeAllowed',
    cancel: 'cancelAllowed'
  } as const;
  if (!a[flag[op]]) return false;
  if (op === 'terms') return d.order.status === 'DRAFT';
  if (op === 'close' || op === 'cancel') return ['DRAFT', 'APPROVED', 'PARTIALLY_RECEIVED', 'PARTIAL_RECEIVED', 'RECEIVED'].includes(d.order.status);
  const q = d.quotes.find((q) => String(q.quoteId) === selectedQuoteId.value);
  if (!q || d.order.status !== 'DRAFT') return false;
  return op === 'confirm'
    ? q.confirmationId == null && String(q.enteredBy) !== String(a.userId)
    : q.confirmationId != null && q.confirmationHash === q.quoteHash && String(q.confirmedBy) !== String(q.enteredBy);
}
/** 用户确认绑定本页面；控制器再次核对原生事实，不自动采用确认后改变的版本。 */
async function perform(op: CertifiedOrderOperation) {
  if (!can(op) || (op === 'terms' && !materialReviewed.value)) return;
  const turn = panelEpoch;
  const token = boundCredential;
  const doc = state.value?.orderId;
  const confirmedDraft = confirmationEpoch;
  pending.value = true;
  localMessage.value = '';
  const input: Record<string, unknown> =
    op === 'terms'
      ? { ...terms.value }
      : op === 'confirm'
        ? { quoteId: selectedQuoteId.value, reviewNote: reviewNote.value }
        : op === 'approve'
          ? { quoteId: selectedQuoteId.value }
          : {};
  try {
    await ElMessageBox.confirm(
      `仅对原订单执行${{ terms: '真实报价录入', confirm: '独立报价确认', approve: '订单批准', close: '订单关闭', cancel: '订单取消' }[op]}，不会自动办理下一步。是否继续？`,
      '认证采购单步确认',
      { type: 'warning' }
    );
    if (
      turn !== panelEpoch ||
      !boundScopeCurrent() ||
      effectiveReadOnly.value ||
      (getToken() ?? null) !== token ||
      doc !== boundDocumentId ||
      state.value?.orderId !== doc ||
      confirmedDraft !== confirmationEpoch
    )
      return;
    // 原确认资料持续绑定到实际 dispatch；异步 fresh GET 期间变化也不能继续派发。
    executingDraftEpoch = confirmedDraft;
    await controller.execute(op, input);
    if (turn !== panelEpoch || !boundScopeCurrent()) return;
    state.value = controller.snapshot();
    if (state.value.phase === 'SUCCEEDED') {
      executingDraftEpoch = null;
      materialReviewed.value = false;
      emit('changed');
    }
  } catch {
    if (turn === panelEpoch) localMessage.value = '确认取消或结果未确认；请保留原意图，以只读核对为准。';
  } finally {
    executingDraftEpoch = null;
    pending.value = false;
    if (turn === panelEpoch) state.value = controller.snapshot();
  }
}
function close() {
  if (busy.value) return;
  panelEpoch++;
  controller.close();
  visible.value = false;
  boundCredential = null;
  boundActor = null;
  boundDocumentId = null;
  boundRouteKey = null;
}
function beforeClose(done: () => void) {
  if (!busy.value) {
    close();
    done();
  }
}
/** 停用/离开不是正常关闭：即使有在途操作也立即撤销会话及 Teleport，不解除持久 UNKNOWN。 */
function dismissBoundary() {
  panelEpoch++;
  controller.invalidate();
  visible.value = false;
  boundCredential = null;
  boundActor = null;
  boundDocumentId = null;
  boundRouteKey = null;
}
function onBoundary() {
  if (visible.value && !boundScopeCurrent()) dismissBoundary();
}
function onFocus() {
  if (!active.value || !ownsPage()) return;
  onBoundary();
  if (visible.value && !busy.value) void refresh();
}
function attachBoundaryListeners() {
  if (boundaryListenersAttached || !active.value || !ownsPage() || typeof window === 'undefined') return;
  window.addEventListener('storage', onBoundary);
  window.addEventListener('focus', onFocus);
  boundaryListenersAttached = true;
}
function detachBoundaryListeners() {
  if (!boundaryListenersAttached || typeof window === 'undefined') return;
  window.removeEventListener('storage', onBoundary);
  window.removeEventListener('focus', onFocus);
  boundaryListenersAttached = false;
}
// 同步撤销能覆盖先变化再恢复；不把后来选中的报价/材料替换进先前确认。
watch(
  () => [terms.value, selectedQuoteId.value, reviewNote.value, materialReviewed.value],
  () => confirmationEpoch++,
  { deep: true, flush: 'sync' }
);
watch(() => [user.token, user.userId, user.tenantId], onBoundary, { flush: 'sync' });
watch(
  () => props.readOnly,
  () => {
    confirmationEpoch++;
    if (visible.value) dismissBoundary();
  },
  { flush: 'sync' }
);
watch(
  () => route.fullPath,
  () => {
    dismissBoundary();
    if (ownsPage() && active.value) attachBoundaryListeners();
    else detachBoundaryListeners();
  },
  { flush: 'sync' }
);
onMounted(attachBoundaryListeners);
onActivated(() => {
  active.value = true;
  attachBoundaryListeners();
});
onDeactivated(() => {
  active.value = false;
  dismissBoundary();
  detachBoundaryListeners();
});
onBeforeUnmount(() => {
  active.value = false;
  dismissBoundary();
  detachBoundaryListeners();
});
defineExpose({ open });
</script>

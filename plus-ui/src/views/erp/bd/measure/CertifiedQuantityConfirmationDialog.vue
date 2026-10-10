<template>
  <el-dialog
    :model-value="dialogVisible"
    title="单位数量核验（真实资料确认）"
    width="800px"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @close="flow.close()"
  >
    <el-alert
      title="此入口仅核验当前单位的实际根映射、换算比例与数量精度，不补认历史库存，不代表全物料、14 物料或 134 MRP 认证。"
      type="warning"
      :closable="false"
      show-icon
      class="mb-4"
    />
    <el-alert
      v-if="blocked || state.phase === 'unknown'"
      :title="state.phase === 'unknown' ? state.message || unknownMessage : unknownMessage"
      type="error"
      :closable="false"
      show-icon
      class="mb-4"
    />
    <el-alert v-else-if="state.message" :title="state.message" :type="messageType" :closable="false" class="mb-4" />
    <div v-loading="state.phase === 'loading' || state.phase === 'submitting'">
      <el-descriptions v-if="state.snapshot" :column="2" border class="mb-4">
        <el-descriptions-item label="当前租户 / 操作者"
          >{{ state.snapshot.context.tenantId }} / {{ state.snapshot.context.userId }}</el-descriptions-item
        >
        <el-descriptions-item label="写权限提示">{{
          state.snapshot.context.confirmAllowed ? '有编辑权限提示（不是认证）' : '仅查询'
        }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ state.snapshot.unit.uomName }} / {{ state.snapshot.unit.uomCode }}</el-descriptions-item>
        <el-descriptions-item label="单位 ID">{{ state.snapshot.unit.uomId }}</el-descriptions-item>
        <el-descriptions-item label="维度 / 基本标识"
          >{{ state.snapshot.unit.category }} / {{ state.snapshot.unit.isBase === 1 ? '根单位' : '衍生单位' }}</el-descriptions-item
        >
        <el-descriptions-item label="原生映射 / 本次根身份">
          {{ state.snapshot.unit.primaryId ?? '未登记（NULL）' }} / {{ state.snapshot.root.uomId }} → {{ state.snapshot.root.uomName }}
        </el-descriptions-item>
        <el-descriptions-item label="原版本（原值提交）">{{
          state.snapshot.unit.conversionVersion === null ? '尚无版本（NULL）' : state.snapshot.unit.conversionVersion
        }}</el-descriptions-item>
        <el-descriptions-item label="根族版本">{{
          state.snapshot.root.conversionVersion === null ? '尚无版本（NULL）' : state.snapshot.root.conversionVersion
        }}</el-descriptions-item>
        <el-descriptions-item label="服务器原比例">{{ state.snapshot.unit.conversionRate ?? '未提供' }}</el-descriptions-item>
        <el-descriptions-item label="服务器原精度">{{ state.snapshot.unit.quantityScale ?? '未提供' }}</el-descriptions-item>
        <el-descriptions-item label="服务器核验时间" :span="2">{{ state.snapshot.unit.conversionVerifiedAt ?? '尚未核验' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="state.snapshot?.unit.isBase === 1"
        title="原生基本标识明确为根单位：现有服务合同以本单位 ID 为根，要求人工明确自映射及比例为一。首次原映射为 NULL 时仅按此明确合同确认，不自动填写，不作为证据。"
        type="info"
        :closable="false"
        class="mb-4"
      />
      <el-form :model="state.form" label-width="130px" :disabled="!editable">
        <el-form-item label="实际换算比例">
          <el-input v-model="state.form.conversionRate" placeholder="依据真实资料输入十进制文本，最多 12 位整数 / 6 位小数" autocomplete="off" />
        </el-form-item>
        <el-form-item label="数量精度">
          <el-select v-model="state.form.quantityScale" placeholder="人工明确选择 0–4 位，不默认" class="w-full">
            <el-option v-for="scale in 5" :key="scale - 1" :label="`${scale - 1} 位小数`" :value="String(scale - 1)" />
          </el-select>
        </el-form-item>
        <el-form-item label="真实资料引用">
          <el-input
            v-model="state.form.reference"
            type="textarea"
            :rows="3"
            maxlength="256"
            show-word-limit
            placeholder="人工填写规格书、批准的单位资料等可复核引用；无资料请勿确认"
          />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button :disabled="state.phase === 'loading' || state.phase === 'submitting'" @click="readOnlyRefresh">只读重新读取（不解锁）</el-button>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :disabled="!submittable" :loading="state.phase === 'submitting'" @click="confirmQuantity">明确确认实际资料</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { getToken } from '@/utils/auth';
import { normalizeBusinessId } from '@/utils/certified-business';
import { getQuantityConfirmationContext, getQuantityConfirmationDetail, confirmMeasureQuantity } from '@/api/erp/bd/measure';
import { createQuantityConfirmation, createQuantityConfirmationState } from './quantityConfirmation';

/** 只接受列表单位身份；列表旧值、isViewMode 或按钮 props 都不能升级为写资格。 */
const props = defineProps<{ modelValue: boolean; uomId: string | number | null }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; confirmed: [id: string] }>();
const route = useRoute();
// KeepAlive 停用不等于卸载，Teleport 也不属于缓存页面的可见子树；所有入口都绑定首次所属页。
const pagePath = () => route.path ?? route.fullPath.split(/[?#]/, 1)[0];
const ownerPagePath = pagePath();
const active = ref(true);
const dismissed = ref(false);
const ownsPage = () => pagePath() === ownerPagePath;
const dialogVisible = computed(() => active.value && ownsPage() && props.modelValue && !dismissed.value);
let boundaryListenersAttached = false;
let wasDeactivated = false;
let boundCredential: string | null = null;
let boundDocumentId: string | null = null;
let boundRouteKey: string | null = null;
const state = reactive(createQuantityConfirmationState());
const storageTick = ref(0);
const unknownMessage = '此前确认仍有未确定结果，只能只读核对。开关对话框或版本变化不会解锁，请勿重发。';
// 存储不可用时返回失败端口，不以内存替身静默降级为可写。
const storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> = {
  getItem: (key) => window.localStorage.getItem(key),
  setItem: (key, value) => window.localStorage.setItem(key, value),
  removeItem: (key) => window.localStorage.removeItem(key)
};
const flow = createQuantityConfirmation({
  api: { getContext: getQuantityConfirmationContext, getDetail: getQuantityConfirmationDetail, confirm: confirmMeasureQuantity },
  storage,
  state,
  readCredential: getToken,
  readDocumentId: () => props.uomId,
  readRouteKey: () => route.fullPath,
  requestKey: () => crypto.randomUUID()
});
const blocked = computed(() => storageTick.value >= 0 && flow.isBlocked());
const editable = computed(
  () => dialogVisible.value && ['ready', 'rejected'].includes(state.phase) && state.snapshot?.context.confirmAllowed && !blocked.value
);
const submittable = computed(() => dialogVisible.value && !blocked.value && flow.canSubmit());
const messageType = computed(() =>
  state.phase === 'confirmed' ? 'success' : state.phase === 'invalid' || state.phase === 'rejected' ? 'warning' : 'info'
);

/** 封闭内存会话及迟到回调，不触碰持久意图；即使父级暂未响应 emit，Teleport 也立即隐藏。 */
function dismissDialog() {
  dismissed.value = true;
  boundCredential = null;
  boundDocumentId = null;
  boundRouteKey = null;
  flow.close();
  if (props.modelValue) emit('update:modelValue', false);
}
function boundScopeCurrent() {
  try {
    return (
      dialogVisible.value &&
      boundCredential === getToken() &&
      boundDocumentId === normalizeBusinessId(props.uomId) &&
      boundRouteKey === route.fullPath
    );
  } catch {
    return false;
  }
}
function openCurrentUnit(id: string | number | null) {
  boundCredential = getToken();
  boundRouteKey = route.fullPath;
  try {
    boundDocumentId = normalizeBusinessId(id);
  } catch {
    boundDocumentId = null;
  }
  dismissed.value = false;
  void flow.open(id);
}

// 路由变化只能撤销原 scope，不能从另一页面隐式创建 fresh 会话；同页 query 改变也须人工重新打开。
watch(
  () => route.fullPath,
  () => {
    dismissDialog();
    if (ownsPage() && active.value) attachBoundaryListeners();
    else detachBoundaryListeners();
  },
  { flush: 'sync' }
);
watch(
  () => [props.modelValue, props.uomId, getToken()] as const,
  ([visible, id], previous) => {
    if (!visible) {
      dismissDialog();
      return;
    }
    if (!active.value || !ownsPage() || (dismissed.value && previous?.[0] === true)) {
      dismissDialog();
      return;
    }
    openCurrentUnit(id);
  },
  { immediate: true, flush: 'sync' }
);

/** storage/focus 只核对当前边界与已知锁；不在 focus 或已停用页面自动 GET/open。 */
function storageChanged() {
  if (!active.value || !ownsPage() || (props.modelValue && !boundScopeCurrent())) {
    dismissDialog();
    return;
  }
  storageTick.value++;
}
function attachBoundaryListeners() {
  if (boundaryListenersAttached || !active.value || !ownsPage() || typeof window === 'undefined') return;
  window.addEventListener('storage', storageChanged);
  window.addEventListener('focus', storageChanged);
  boundaryListenersAttached = true;
}
function detachBoundaryListeners() {
  if (!boundaryListenersAttached || typeof window === 'undefined') return;
  window.removeEventListener('storage', storageChanged);
  window.removeEventListener('focus', storageChanged);
  boundaryListenersAttached = false;
}
onMounted(attachBoundaryListeners);
onActivated(() => {
  active.value = true;
  if (wasDeactivated) {
    wasDeactivated = false;
    dismissDialog();
  }
  attachBoundaryListeners();
});
onDeactivated(() => {
  active.value = false;
  wasDeactivated = true;
  dismissDialog();
  detachBoundaryListeners();
});
onBeforeUnmount(() => {
  active.value = false;
  dismissDialog();
  detachBoundaryListeners();
});

/** 手工刷新仍是 GET，不清锁、不自动提交，不把已有比例复制进表单。 */
function readOnlyRefresh() {
  if (!dialogVisible.value) return;
  if (!boundScopeCurrent()) {
    dismissDialog();
    return;
  }
  openCurrentUnit(props.uomId);
}
async function confirmQuantity() {
  if (!boundScopeCurrent()) {
    if (props.modelValue) dismissDialog();
    return;
  }
  if ((await flow.submit()) && boundScopeCurrent() && state.phase === 'confirmed' && normalizeBusinessId(props.uomId) === state.snapshot?.unit.uomId)
    emit('confirmed', state.snapshot.unit.uomId);
}
</script>

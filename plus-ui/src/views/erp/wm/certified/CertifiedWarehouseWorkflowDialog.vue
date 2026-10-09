<template>
  <el-dialog
    :model-value="dialogVisible"
    :title="domain === 'arrivalnotice' ? '认证到货工作台' : '认证收货工作台'"
    width="980px"
    :close-on-click-modal="false"
    :close-on-press-escape="!state.busy"
    :show-close="!state.busy"
    @update:model-value="close"
  >
    <el-alert
      type="warning"
      :closable="false"
      title="query 可看不等于可写。真实数量/日期/储位须人工依据材料输入；本台不补认报价、ETA、单位、批次或 IQC，不自动串联操作。"
    />
    <el-form label-width="150px" :disabled="state.busy || state.reading" class="warehouse-input">
      <el-form-item label="对象类型"
        ><el-radio-group v-model="targetKind" @change="invalidateTarget"
          ><el-radio-button value="ORDER">订单（创建来源）</el-radio-button
          ><el-radio-button :value="domain === 'arrivalnotice' ? 'NOTICE' : 'RECEIVE'">{{
            domain === 'arrivalnotice' ? '到货通知' : '收货单'
          }}</el-radio-button></el-radio-group
        ></el-form-item
      >
      <el-form-item label="真实对象 ID"
        ><el-input v-model="targetId" placeholder="原生订单 / 通知 / 收货 ID，不经 Number 转换" @input="invalidateTarget"
      /></el-form-item>
      <el-form-item
        ><el-button data-action="inspect" @click="inspect">读取 / 刷新原生详情</el-button
        ><el-button v-if="domain === 'receive' && targetKind === 'RECEIVE'" data-action="recovery" @click="recover"
          >GET 永久恢复（只读）</el-button
        ></el-form-item
      >
    </el-form>
    <el-alert v-if="state.message" :type="state.blocked ? 'error' : 'info'" :closable="false" :title="state.message" />
    <el-descriptions v-if="state.context" :column="3" border
      ><el-descriptions-item label="当前租户">{{ state.context.tenantId }}</el-descriptions-item
      ><el-descriptions-item label="实际操作者">{{ state.context.username }} / {{ state.context.userId }}</el-descriptions-item
      ><el-descriptions-item label="部门">{{ state.context.deptId ?? '未知（不推断）' }}</el-descriptions-item></el-descriptions
    >
    <div v-if="state.detail" class="warehouse-native">
      <strong>完整原生快照（不可编辑；版本 NULL 不补 0，关联状态不猜批准）</strong>
      <pre>{{ JSON.stringify(state.detail, null, 2) }}</pre>
    </div>
    <el-form label-width="150px" :disabled="state.busy || state.reading || state.blocked" class="warehouse-input">
      <template v-if="targetKind === 'ORDER'">
        <el-form-item label="操作请求键"
          ><el-input v-model="input.requestKey" placeholder="人工明确本次唯一意图；UNKNOWN 不可换键重发"
        /></el-form-item>
        <el-form-item label="真实采购单行 ID"
          ><el-input v-model="input.purchaseLineId" placeholder="依据原生行明确输入，不自动选择或复制数量"
        /></el-form-item>
        <el-form-item label="真实数量"
          ><el-input v-model="input.quantity" placeholder="精确正数，最多 4 位小数；不以订购量作为实际量"
        /></el-form-item>
        <template v-if="domain === 'arrivalnotice'"
          ><el-form-item label="通知名称"><el-input v-model="input.noticeName" maxlength="255" /></el-form-item
          ><el-form-item label="声明到货日期"><el-input v-model="input.arrivalDate" placeholder="YYYY-MM-DD；不复制报价 ETA" /></el-form-item
        ></template>
        <template v-else
          ><el-form-item label="实际到货日期"><el-input v-model="input.actualArrivalDate" placeholder="YYYY-MM-DD；不得是未来日期" /></el-form-item
          ><el-form-item label="仓库 ID"><el-input v-model="input.warehouseId" /></el-form-item
          ><el-form-item label="库区 ID"><el-input v-model="input.areaId" /></el-form-item
          ><el-form-item label="储位 ID"><el-input v-model="input.locationId" /></el-form-item
          ><el-form-item label="可选真实通知 ID"
            ><el-input v-model="input.noticeId" placeholder="与通知行成对；另需 wm:arrivalnotice:query 核对" /></el-form-item
          ><el-form-item label="可选真实通知行 ID"><el-input v-model="input.noticeLineId" placeholder="不从订单伪造通知来源" /></el-form-item
        ></template>
        <el-form-item label="实际资料备注"><el-input v-model="input.remark" maxlength="500" type="textarea" /></el-form-item>
      </template>
      <el-form-item v-if="domain === 'arrivalnotice' && targetKind === 'NOTICE'" label="流程表单关联 ID"
        ><el-input v-model="input.flowId" placeholder="selectFlowId：从正常流程管理取得，不是 Flowable definition ID"
      /></el-form-item>
      <el-form-item
        ><el-checkbox v-model="input.materialsConfirmed"
          >已人工核对本意图所需真实材料、完整原生单据及数量/日期；缺材料保持 BLOCKED</el-checkbox
        ></el-form-item
      >
      <el-form-item>
        <el-button
          v-if="domain === 'arrivalnotice' && targetKind === 'ORDER' && permitted('wm:arrivalnotice:add')"
          :disabled="!actionReady('createNotice')"
          data-action="createNotice"
          @click="perform('createNotice')"
          >单独确认创建通知</el-button
        >
        <template v-if="domain === 'arrivalnotice' && targetKind === 'NOTICE' && permitted('wm:arrivalnotice:submit')"
          ><el-button :disabled="!actionReady('submitNotice')" data-action="submitNotice" @click="perform('submitNotice')">单独确认提交</el-button
          ><el-button :disabled="!actionReady('synchronizeNotice')" data-action="synchronizeNotice" @click="perform('synchronizeNotice')"
            >单独确认实际同步</el-button
          ></template
        >
        <el-button
          v-if="domain === 'receive' && targetKind === 'ORDER' && permitted('wm:receive:add')"
          :disabled="!actionReady('createReceive')"
          data-action="createReceive"
          @click="perform('createReceive')"
          >单独确认创建草稿</el-button
        >
        <template v-if="domain === 'receive' && targetKind === 'RECEIVE'"
          ><el-button
            v-if="permitted('wm:receive:shelf')"
            :disabled="!actionReady('shelfReceive')"
            data-action="shelfReceive"
            @click="perform('shelfReceive')"
            >单独确认认证上架</el-button
          ><el-button
            v-if="permitted('wm:receive:execute')"
            :disabled="!actionReady('executeReceive')"
            data-action="executeReceive"
            @click="perform('executeReceive')"
            >单独确认实际过账</el-button
          ></template
        >
      </el-form-item>
    </el-form>
    <el-alert
      v-if="domain === 'receive'"
      type="info"
      :closable="false"
      title="草稿不是审批/IQC/已入庫；认证单禁止旧通用编辑或虚拟库存路径。无真实审批、批次分配或 IQC 资料时只读 BLOCKED，本台不提供这些补造写接口。"
    />
    <pre v-if="state.result">{{ JSON.stringify(state.result, null, 2) }}</pre>
    <pre v-if="state.recovery">{{ JSON.stringify(state.recovery, null, 2) }}</pre>
    <template #footer><el-button :disabled="state.busy" @click="close(false)">关闭（不清未决意图）</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, shallowRef, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { getToken } from '@/utils/auth';
import { useUserStore } from '@/store/modules/user';
import auth from '@/plugins/auth';
import * as arrival from '@/api/erp/wm/arrivalnotice';
import * as receive from '@/api/erp/wm/receive';
import { createWarehouseWorkflow } from './warehouseWorkflow';
import type { WarehouseInput, WarehouseOperation, WarehouseState } from './warehouseWorkflow';
import type { WarehouseDomain, WarehouseTarget } from '@/api/erp/wm/arrivalnotice/types';

const props = withDefaults(defineProps<{ modelValue: boolean; domain: WarehouseDomain; initialId?: string; initialKind?: WarehouseTarget }>(), {
  initialId: '',
  initialKind: 'ORDER'
});
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();
const route = useRoute();
const user = useUserStore();
// KeepAlive 停用不卸载 Teleport；本实例只能由首次所属页/领域人工重新打开。
const pagePath = () => route.path ?? route.fullPath.split(/[?#]/, 1)[0];
const ownerPagePath = pagePath();
const ownerDomain = props.domain;
const active = ref(true);
const dismissed = ref(false);
const ownsPage = () => pagePath() === ownerPagePath && props.domain === ownerDomain;
const dialogVisible = computed(() => active.value && ownsPage() && props.modelValue && !dismissed.value);
let wasDeactivated = false;
let boundaryListenersAttached = false;
let boundScope: { credential: ReturnType<typeof getToken>; tenantId: unknown; userId: unknown; routeKey: string } | null = null;
const targetId = ref(props.initialId);
const targetKind = ref<WarehouseTarget>(props.initialKind);
const input = reactive<WarehouseInput>({
  requestKey: '',
  purchaseLineId: '',
  quantity: '',
  noticeName: '',
  arrivalDate: '',
  actualArrivalDate: '',
  warehouseId: '',
  areaId: '',
  locationId: '',
  noticeId: '',
  noticeLineId: '',
  remark: '',
  flowId: '',
  materialsConfirmed: false
});
const state = shallowRef<WarehouseState>({
  kind: props.initialKind,
  documentId: props.initialId,
  context: null,
  detail: null,
  busy: false,
  reading: false,
  blocked: false,
  message: '',
  result: null,
  recovery: null
});
/** 存储不可访问时封闭；不回退至内存意图，不保存 token 或真实供应资料。 */
function storagePort(): Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> {
  try {
    return window.localStorage;
  } catch {
    return {
      getItem: () => {
        throw new Error('存储不可用');
      },
      setItem: () => {
        throw new Error('存储不可用');
      },
      removeItem: () => {
        throw new Error('存储不可用');
      }
    };
  }
}
const workflow = createWarehouseWorkflow({
  domain: props.domain,
  storage: storagePort(),
  readCredential: getToken,
  readScope: () => {
    if (!scopeCurrent()) throw new Error('仓储页面会话已失效');
    return { tenantId: boundScope!.tenantId, userId: boundScope!.userId, routeKey: `${boundScope!.routeKey}|${ownerDomain}|true` };
  },
  hasPermission: (permission) => auth.hasPermi(permission),
  context: (session) =>
    props.domain === 'arrivalnotice' ? arrival.getCertifiedArrivalContext(session) : receive.getCertifiedReceiveContext(session),
  detail: (kind, id, session) =>
    props.domain === 'arrivalnotice' ? arrival.getCertifiedArrivalDetail(kind, id, session) : receive.getCertifiedReceiveDetail(kind, id, session),
  write: (op, id, body, session, flowId) => {
    switch (op) {
      case 'createNotice':
        return arrival.createCertifiedArrival(body, session);
      case 'submitNotice':
        return arrival.submitCertifiedArrival(id, flowId ?? '', session);
      case 'synchronizeNotice':
        return arrival.synchronizeCertifiedArrival(id, session);
      case 'createReceive':
        return receive.createCertifiedReceiveDraft(body, session);
      case 'shelfReceive':
        return receive.shelfCertifiedReceive(id, session);
      case 'executeReceive':
        return receive.executeCertifiedReceive(id, session);
    }
  },
  bindingContext: (session) => arrival.getCertifiedArrivalContext(session),
  bindingDetail: (id, session) => arrival.getCertifiedArrivalDetail('NOTICE', id, session),
  recovery: (id, session) => receive.recoverCertifiedReceive(id, session),
  confirm: async (message) => {
    try {
      await ElMessageBox.confirm(message, '人工独立确认', { type: 'warning', distinguishCancelAndClose: true });
      return true;
    } catch {
      return false;
    }
  },
  changed: (next) => {
    state.value = { ...next };
  }
});
const canAct = computed(
  () => dialogVisible.value && !!state.value.detail && !state.value.busy && !state.value.reading && !state.value.blocked && input.materialsConfirmed
);
const actionReady = (operation: WarehouseOperation) => canAct.value && workflow.available(operation, { ...input });
const permitted = (permission: string) => auth.hasPermi(permission);
/** 仅封闭内存会话/迟到回调；不释放、清理或替换任何持久 UNKNOWN 意图。 */
function dismissDialog() {
  dismissed.value = true;
  boundScope = null;
  input.materialsConfirmed = false;
  workflow.invalidate();
  if (props.modelValue) emit('update:modelValue', false);
}
function scopeCurrent() {
  return (
    dialogVisible.value &&
    boundScope !== null &&
    boundScope.credential === getToken() &&
    boundScope.tenantId === user.tenantId &&
    boundScope.userId === user.userId &&
    boundScope.routeKey === route.fullPath
  );
}
function requireScope() {
  if (scopeCurrent()) return true;
  // 已停用的 handler 直接退出，不取 session、不触发监听或读取。
  if (dialogVisible.value) dismissDialog();
  return false;
}
function invalidateTarget() {
  if (!requireScope()) return;
  workflow.setTarget(targetKind.value, targetId.value);
  input.materialsConfirmed = false;
}
function ensureTarget() {
  if (state.value.kind !== targetKind.value || state.value.documentId !== targetId.value) workflow.setTarget(targetKind.value, targetId.value);
}
async function inspect() {
  if (!requireScope()) return;
  ensureTarget();
  await workflow.inspect();
}
async function perform(operation: WarehouseOperation) {
  if (!requireScope()) return;
  const accepted = await workflow.perform(operation, { ...input });
  if (!accepted) input.materialsConfirmed = false;
}
async function recover() {
  if (!requireScope()) return;
  ensureTarget();
  await workflow.recover();
}
function close(value: boolean) {
  if (state.value.busy || value) return;
  dismissDialog();
}
// 路由/账号变化只能撤销原 scope；不从另一页面自动捕获新 session。
watch(
  () => [route.fullPath, user.userId, user.tenantId, user.token, getToken(), props.domain],
  () => {
    dismissDialog();
    if (active.value && ownsPage()) attachBoundaryListeners();
    else detachBoundaryListeners();
    // 缓存 vnode 不再接收父级 props；同一 tick 的异页 open 也要回写关闭。
    if (!active.value || !ownsPage()) {
      void nextTick(() => {
        if (!active.value || !ownsPage()) emit('update:modelValue', false);
      });
    }
  },
  { flush: 'sync' }
);
watch(
  () => [props.modelValue, props.initialId, props.initialKind] as const,
  ([visible], previous) => {
    if (!visible || !active.value || !ownsPage() || (dismissed.value && previous?.[0] === true)) {
      dismissDialog();
      return;
    }
    boundScope = { credential: getToken(), tenantId: user.tenantId, userId: user.userId, routeKey: route.fullPath };
    dismissed.value = false;
    targetId.value = props.initialId;
    targetKind.value = props.initialKind;
    invalidateTarget();
  },
  { immediate: true, flush: 'sync' }
);
/** 浏览器边界事件只撤销审阅；不自动 GET/open，不碰持久锁。 */
function onBoundary() {
  if (!active.value || !ownsPage()) return;
  if (props.modelValue && !scopeCurrent()) {
    dismissDialog();
    return;
  }
  if (dialogVisible.value) {
    input.materialsConfirmed = false;
    workflow.invalidate();
  }
}
function attachBoundaryListeners() {
  if (boundaryListenersAttached || !active.value || !ownsPage() || typeof window === 'undefined') return;
  window.addEventListener('storage', onBoundary);
  window.addEventListener('focus', onBoundary);
  boundaryListenersAttached = true;
}
function detachBoundaryListeners() {
  if (!boundaryListenersAttached || typeof window === 'undefined') return;
  window.removeEventListener('storage', onBoundary);
  window.removeEventListener('focus', onBoundary);
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
</script>
<style scoped>
.warehouse-input {
  margin-top: 16px;
}
.warehouse-native {
  margin: 16px 0;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 260px;
  overflow-y: auto;
  padding: 12px;
  background: var(--el-fill-color-light);
}
</style>

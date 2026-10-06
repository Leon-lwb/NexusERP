<template>
  <el-card shadow="never">
    <template #header>动作审批 · 仅批准明确选中的待处理项</template>
    <el-checkbox-group v-model="selected">
      <div v-for="action in pending" :key="action.id" class="p-3 mb-3 border border-gray-200 rounded-lg">
        <div class="flex items-center flex-wrap gap-2 mb-2">
          <el-checkbox :value="String(action.id)" :disabled="!canApproveAction(action)"
            ><strong>{{ action.title }}</strong></el-checkbox
          >
          <el-tag size="small" :type="action.riskLevel === 'HIGH' || action.riskLevel === 'CRITICAL' ? 'danger' : 'info'">
            {{ decisionRiskLabel(action.riskLevel) }}
          </el-tag>
          <el-tag size="small" type="warning">{{ action.approvalRequired === 'Y' ? '须人工批准' : '待确认' }}</el-tag>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="业务对象">{{ action.targetBizType }} · {{ action.targetBizId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="执行权限">{{ action.permissionCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="建议理由">{{ action.displayReason || '请结合证据复核' }}</el-descriptions-item>
          <el-descriptions-item label="动作内容">{{ summarize(action) }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="isPurchaseDraft(action) && !canApproveAction(action)"
          class="mt-2"
          title="当前采购动作不可批准"
          type="error"
          :closable="false"
          show-icon
          :description="purchaseBlockReason(action)"
        />
        <el-alert
          v-if="action.actionType === 'CREATE_PURCHASE_REQUISITION_DRAFT'"
          class="mt-2"
          title="采购申请草稿审批提醒"
          type="warning"
          :closable="false"
          show-icon
          :description="purchaseApprovalReminder(action)"
        />
      </div>
    </el-checkbox-group>
    <el-input
      v-model="comment"
      type="textarea"
      :rows="2"
      maxlength="500"
      show-word-limit
      placeholder="审批意见（驳回时必填至少两个字）"
      class="my-3"
    />
    <el-button type="primary" :disabled="loading || !approvableSelection.length" v-hasPermi="['ai:decision:approve']" @click="approve"
      >批准选中项</el-button
    >
    <el-button type="danger" :disabled="loading || !pending.length" v-hasPermi="['ai:decision:approve']" @click="reject">驳回全部</el-button>
  </el-card>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { DecisionAction, DecisionRunSummary, DecisionId } from '@/api/ai/decision';
import { canApproveRun, canInspectPurchaseAction, decisionRiskLabel, summarizeActionPayload } from '@/views/ai/decision/decisionViewModel';
const props = defineProps<{
  actions: DecisionAction[];
  run: DecisionRunSummary;
  actionBlockReasons?: Record<DecisionId, string>;
  loading?: boolean;
}>();
const emit = defineEmits<{
  (event: 'approve', payload: { approvedActionIds: DecisionId[]; rejectedActionIds: DecisionId[]; comment: string }): void;
  (event: 'reject', reason: string): void;
}>();
const selected = ref<DecisionId[]>([]);
const comment = ref('');
const staticPurchaseBlockReason = '来源/合同尚未核验，需重新分析；只有服务端确认的新合同可申请审批';
const pending = computed(() => props.actions.filter((a) => a.approvalStatus === 'PROPOSED' || a.approvalStatus === 'PENDING_APPROVAL'));
watch(
  () => pending.value.filter(canApproveAction).map((action) => String(action.id)),
  (ids) => {
    selected.value = selected.value.filter((id) => ids.includes(id));
  }
);
function summarize(action: DecisionAction) {
  return summarizeActionPayload(action.payloadJson);
}
function canInspect(action: DecisionAction) {
  return summarize(action) !== '载荷不可验证，请勿批准';
}
function isPurchaseDraft(action: DecisionAction) {
  return action.actionType === 'CREATE_PURCHASE_REQUISITION_DRAFT';
}
function purchaseBlockReason(action: DecisionAction) {
  const reason = props.actionBlockReasons?.[String(action.id)];
  return typeof reason === 'string' && reason.trim() ? reason.trim() : staticPurchaseBlockReason;
}
function isNewPurchaseContract(action: DecisionAction) {
  return props.run.decisionContractVersion === 2 && action.purchasePayloadVersion === 2 && canInspectPurchaseAction(action, props.run);
}
function purchaseApprovalReminder(action: DecisionAction) {
  return isNewPurchaseContract(action)
    ? '当前展示为冻结意图，审批和执行仍由ERP重新核验；勾选仅申请审批，不代表已执行或已创建采购草稿。'
    : '缺口为来源基本单位，不是已核准采购量；当前采购合同门禁仍关闭，不会创建采购草稿。请核对计量/包装、供应商起订量、同一 MRP 需求已有关联的未结申请及需求日期，核验后重新分析。';
}
function canApproveAction(action: DecisionAction) {
  if (!isPurchaseDraft(action)) return canInspect(action);
  if (
    !isNewPurchaseContract(action) ||
    !canApproveRun(props.run) ||
    (action.approvalStatus !== 'PROPOSED' && action.approvalStatus !== 'PENDING_APPROVAL') ||
    action.executionStatus !== 'NOT_STARTED'
  )
    return false;
  const reasons = props.actionBlockReasons;
  if (!reasons || typeof reasons !== 'object' || Array.isArray(reasons)) return false;
  const prototype = Object.getPrototypeOf(reasons);
  if (prototype !== Object.prototype && prototype !== null) return false;
  if (!Object.prototype.hasOwnProperty.call(reasons, action.id)) return true;
  const reason = reasons[action.id];
  return typeof reason === 'string' && !reason.trim();
}
const approvableSelection = computed(() =>
  pending.value.filter((action) => canApproveAction(action) && selected.value.includes(String(action.id))).map((action) => String(action.id))
);
async function approve() {
  if (props.loading || !approvableSelection.value.length) return;
  try {
    await ElMessageBox.confirm(`确认批准 ${approvableSelection.value.length} 个动作？未选中的动作将被驳回。`, '审批确认', { type: 'warning' });
    if (props.run.riskLevel === 'HIGH' || props.run.riskLevel === 'CRITICAL')
      await ElMessageBox.confirm('高风险动作可能创建业务草稿，请再次确认审批范围。', '二次确认', { type: 'warning' });
  } catch {
    return;
  }
  const approvedActionIds = approvableSelection.value;
  if (props.loading || !approvedActionIds.length) return;
  emit('approve', {
    approvedActionIds,
    rejectedActionIds: pending.value.map((a) => String(a.id)).filter((id) => !approvedActionIds.includes(id)),
    comment: comment.value
  });
}
async function reject() {
  if (comment.value.trim().length < 2) {
    ElMessage.warning('驳回理由至少两个字');
    return;
  }
  try {
    await ElMessageBox.confirm('确认驳回本次决策的全部动作？', '审批确认', { type: 'warning' });
  } catch {
    return;
  }
  emit('reject', comment.value.trim());
}
</script>

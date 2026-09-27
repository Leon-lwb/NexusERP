<template>
  <el-card shadow="never">
    <template #header>动作审批 · 仅批准明确选中的待处理项</template>
    <el-checkbox-group v-model="selected">
      <div v-for="action in pending" :key="action.id" class="p-3 mb-3 border border-gray-200 rounded-lg">
        <div class="flex items-center flex-wrap gap-2 mb-2">
          <el-checkbox :value="String(action.id)" :disabled="!canInspect(action)"
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
    <el-button type="primary" :disabled="loading || !selected.length || !pending.length" v-hasPermi="['ai:decision:approve']" @click="approve"
      >批准选中项</el-button
    >
    <el-button type="danger" :disabled="loading || !pending.length" v-hasPermi="['ai:decision:approve']" @click="reject">驳回全部</el-button>
  </el-card>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { DecisionAction, DecisionRunSummary, DecisionId } from '@/api/ai/decision';
import { decisionRiskLabel, summarizeActionPayload } from '@/views/ai/decision/decisionViewModel';
const props = defineProps<{ actions: DecisionAction[]; run: DecisionRunSummary; loading?: boolean }>();
const emit = defineEmits<{
  (event: 'approve', payload: { approvedActionIds: DecisionId[]; rejectedActionIds: DecisionId[]; comment: string }): void;
  (event: 'reject', reason: string): void;
}>();
const selected = ref<DecisionId[]>([]);
const comment = ref('');
const pending = computed(() => props.actions.filter((a) => a.approvalStatus === 'PROPOSED' || a.approvalStatus === 'PENDING_APPROVAL'));
watch(pending, () => {
  selected.value = selected.value.filter((id) => pending.value.some((a) => String(a.id) === id));
});
function summarize(action: DecisionAction) {
  return summarizeActionPayload(action.payloadJson);
}
function canInspect(action: DecisionAction) {
  return summarize(action) !== '载荷不可验证，请勿批准';
}
async function approve() {
  if (!selected.value.length) return;
  try {
    await ElMessageBox.confirm(`确认批准 ${selected.value.length} 个动作？未选中的动作将被驳回。`, '审批确认', { type: 'warning' });
    if (props.run.riskLevel === 'HIGH' || props.run.riskLevel === 'CRITICAL')
      await ElMessageBox.confirm('高风险动作可能创建业务草稿，请再次确认审批范围。', '二次确认', { type: 'warning' });
  } catch {
    return;
  }
  emit('approve', {
    approvedActionIds: [...selected.value],
    rejectedActionIds: pending.value.map((a) => String(a.id)).filter((id) => !selected.value.includes(id)),
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

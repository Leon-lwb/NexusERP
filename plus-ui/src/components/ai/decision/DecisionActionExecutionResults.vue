<template>
  <el-card>
    <template #header>动作审批 / 执行结果</template>
    <p class="text-gray-500 mt-0">逐项展示服务端记录；批准不等于执行，创建草稿不等于已发布。ERP 当前编号和状态需只读回查。</p>
    <el-empty v-if="!actions.length" description="本次没有动作记录" />
    <article v-for="(row, index) in rows" :key="index" class="border border-gray-200 rounded p-3 mb-3">
      <strong>{{ row.action.title }}</strong>
      <div class="flex gap-2 mt-2">
        <span
          >审批：<el-tag type="info">{{ row.view.approvalLabel }}</el-tag></span
        >
        <span
          >执行：<el-tag :type="row.view.executionType">{{ row.view.executionLabel }}</el-tag></span
        >
      </div>
      <p>结果 ID：{{ row.view.resultId }}</p>
      <p v-if="row.action.approvedAt">审批时间：{{ row.action.approvedAt }}</p>
      <p v-if="row.action.finishedAt">执行结束时间：{{ row.action.finishedAt }}</p>
      <p v-if="row.action.approvalComment">审批意见：{{ row.action.approvalComment }}</p>
      <p v-if="row.action.lastErrorCode">执行错误分类：{{ row.action.lastErrorCode }}</p>
      <template v-if="row.target">
        <el-button :loading="lookups[index]?.loading" @click="lookup(index)">回查ERP结果</el-button>
        <p v-if="lookups[index]?.error" role="alert">{{ lookups[index].error }}</p>
        <template v-if="lookups[index]?.result">
          <p>ERP 编号：{{ lookups[index].result?.code }}；本次回查状态：{{ lookups[index].result?.status }}</p>
          <el-button v-if="lookups[index].result?.destination" type="primary" @click="navigate(index)">查看ERP记录</el-button>
          <p v-else>ERP 结果已撤销，不提供草稿导航。</p>
        </template>
      </template>
    </article>
  </el-card>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { DecisionAction, DecisionRunStatus } from '@/api/ai/decision';
import { getPurchaseRequisition } from '@/api/erp/pu/requisition';
import { getScheduleGantt, listSchedule } from '@/api/erp/prd/schedule';
import {
  decisionActionExecutionView,
  decisionActionResultTarget,
  decisionScheduleResultQuery,
  verifiedDecisionErpResult,
  type DecisionErpDestination,
  type VerifiedDecisionErpResult
} from '@/views/ai/decision/decisionActionExecutionView';

const props = defineProps<{ actions: DecisionAction[]; runStatus: DecisionRunStatus }>();
const emit = defineEmits<{ (event: 'navigate', destination: DecisionErpDestination): void }>();
const rows = computed(() =>
  props.actions.map((action) => ({
    action,
    view: decisionActionExecutionView(action, props.runStatus),
    target: decisionActionResultTarget(action, props.runStatus)
  }))
);
const lookups = ref<Record<number, { loading: boolean; result?: VerifiedDecisionErpResult; error?: string }>>({});
let revision = 0;
/** 轮询更新动作/回滚状态后丢弃旧回查；旧异步响应不能重新开放失效结果。 */
watch(
  () =>
    JSON.stringify([
      props.runStatus,
      props.actions.map((a) => [a.id, a.actionType, a.approvalStatus, a.executionStatus, a.resultBizType, a.resultBizId])
    ]),
  () => {
    revision++;
    lookups.value = {};
  }
);
onBeforeUnmount(() => {
  revision++;
});

async function lookup(index: number) {
  const target = rows.value[index]?.target;
  if (!target || lookups.value[index]?.loading) return;
  const currentRevision = revision;
  lookups.value[index] = { loading: true };
  try {
    let result: VerifiedDecisionErpResult | null;
    if (target.kind === 'requisition') {
      const response = await getPurchaseRequisition(target.id);
      result = verifiedDecisionErpResult(target, response.data);
    } else {
      const gantt = await getScheduleGantt(target.id);
      if (currentRevision !== revision) return;
      const identity = decisionScheduleResultQuery({ decisionResultPlanId: gantt.data?.planId, planNo: gantt.data?.planNo });
      if (!identity || identity.id !== target.id) throw new Error('RESULT_NOT_VERIFIED');
      const response = await listSchedule({ pageNum: 1, pageSize: 10, planNo: identity.code });
      const record = response.rows?.find((row) => row.planId === target.id && row.planNo === identity.code);
      result = verifiedDecisionErpResult({ ...target, code: identity.code }, record);
    }
    if (currentRevision !== revision) return;
    if (!result) throw new Error('RESULT_NOT_VERIFIED');
    lookups.value[index] = { loading: false, result };
  } catch {
    if (currentRevision !== revision) return;
    /** 原服务端权限拒绝照常生效；不把异常、Token 或模型正文当作业务结果输出。 */
    lookups.value[index] = { loading: false, error: '回查未完成，请检查查询权限、记录是否存在及结果身份是否一致。' };
  }
}
function navigate(index: number) {
  const target = rows.value[index]?.target;
  const result = lookups.value[index]?.result;
  if (!target || !result || result.id !== target.id) return;
  const destination = result.destination;
  const expectedPath = target.kind === 'requisition' ? '/erp/pu/requisition/form' : '/erp/prd/schedule';
  if (destination?.path === expectedPath) emit('navigate', destination);
}
</script>

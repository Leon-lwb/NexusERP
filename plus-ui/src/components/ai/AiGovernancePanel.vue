<template>
  <el-card v-loading="loading" shadow="never" class="mb-4">
    <template #header
      ><div class="panel-heading">
        <span>调用台账与分币种预算</span>
        <div>
          <el-button v-if="enabled || loadError" @click="load">刷新</el-button
          ><el-button v-if="enabled" v-hasPermi="['ai:tokenUsage:budget']" type="primary" @click="editBudget()">配置预算</el-button>
        </div>
      </div></template
    >
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
    <el-alert v-else-if="!enabled" title="新调用台账尚未启用。下方历史日统计继续保留原口径。" type="info" :closable="false" />
    <template v-else>
      <el-alert
        title="费用按调用时的价格快照估算，按币种分别汇总。未知用量或价格不会显示为零费用；核对后以追加记录修正。"
        type="info"
        :closable="false"
        class="mb-3"
      />
      <el-table :data="summaries" border class="mb-4"
        ><el-table-column prop="currency" label="币种" width="130" /><el-table-column prop="knownCost" label="已知费用估值" /><el-table-column
          prop="unknownCalls"
          label="待核对调用数" /><el-table-column prop="requestCount" label="调用数" /><el-table-column
          prop="inputTokens"
          label="输入Token" /><el-table-column prop="outputTokens" label="输出Token"
      /></el-table>
      <h3 class="section-label">预算与预占</h3>
      <el-table :data="budgets" border class="mb-4"
        ><el-table-column prop="currency" label="币种" width="80" /><el-table-column label="范围" min-width="160"
          ><template #default="{ row }">{{ row.scopeType === 'TENANT_MONTH' ? '租户月度' : 'A/B批次：' + row.scopeId }}</template></el-table-column
        ><el-table-column prop="periodStart" label="账期" width="115" /><el-table-column prop="limitAmount" label="上限" /><el-table-column
          prop="spentAmount"
          label="已结算"
        /><el-table-column prop="heldAmount" label="已预占" /><el-table-column label="状态" width="110"
          ><template #default="{ row }">{{ row.blocked ? '待核对封锁' : row.enabled ? '严格拦截' : '未启用' }}</template></el-table-column
        ><el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button v-hasPermi="['ai:tokenUsage:budget']" link type="primary" @click="editBudget(row)">修改</el-button></template
          ></el-table-column
        ></el-table
      >
      <h3 class="section-label">最近100次调用</h3>
      <el-table :data="calls" border
        ><el-table-column prop="modelName" label="实际选定模型" min-width="150" show-overflow-tooltip /><el-table-column
          prop="source"
          label="来源"
          width="100"
        /><el-table-column label="币种" width="90"
          ><template #default="{ row }">{{ row.settlementCurrency ?? row.currency ?? '未确认' }}</template></el-table-column
        ><el-table-column label="费用估值" min-width="130"
          ><template #default="{ row }">{{
            (row.effectiveCost ?? row.cost) == null ? '未知／待核对' : (row.effectiveCost ?? row.cost)
          }}</template></el-table-column
        ><el-table-column prop="status" label="调用结果" min-width="150" /><el-table-column prop="inputTokens" label="输入Token" /><el-table-column
          prop="outputTokens"
          label="输出Token"
        /><el-table-column prop="requestId" label="请求关联" min-width="160" show-overflow-tooltip /><el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button v-hasPermi="['ai:tokenUsage:reconcile']" link type="primary" @click="editCorrection(row)">核对</el-button></template
          ></el-table-column
        ></el-table
      >
    </template>
  </el-card>
  <el-dialog v-model="budgetVisible" title="配置严格预算" width="min(600px, 95vw)" append-to-body>
    <el-alert
      title="启用后额度不足将拒绝新调用，在途继续结算。该范围未配置额度的币种不能调用。未知结果保留预占，需先核对。"
      type="info"
      :closable="false"
      class="mb-4"
    />
    <el-form :model="budgetForm" label-width="100px"
      ><el-form-item label="范围"
        ><el-select v-model="budgetForm.scopeType" :disabled="editing"
          ><el-option label="租户月度" value="TENANT_MONTH" /><el-option label="A/B批次" value="AB_RUN" /></el-select></el-form-item
      ><el-form-item v-if="budgetForm.scopeType === 'AB_RUN'" label="批次ID"
        ><el-input v-model="budgetForm.scopeId" :disabled="editing" /></el-form-item
      ><el-form-item label="币种"><el-input v-model="budgetForm.currency" :disabled="editing" placeholder="CNY、USD等三位币种码" /></el-form-item
      ><el-form-item label="金额上限"><el-input v-model="budgetForm.limitAmount" inputmode="decimal" placeholder="最多12位小数" /></el-form-item
      ><el-form-item label="严格拦截"><el-switch v-model="budgetForm.enabled" /></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="budgetVisible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveBudget">保存</el-button></template
    >
  </el-dialog>
  <el-dialog v-model="correctionVisible" title="追加调用对账记录" width="min(600px, 95vw)" append-to-body>
    <el-form :model="correction" label-width="100px"
      ><el-form-item label="确认币种"><el-input v-model="correction.currency" /></el-form-item
      ><el-form-item label="确认总费用"><el-input v-model="correction.confirmedAmount" inputmode="decimal" /></el-form-item
      ><el-form-item label="核对依据"><el-input v-model="correction.reason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="correctionVisible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveCorrection">保存核对</el-button></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { checkPermi } from '@/utils/permission';
import { createAiClientRequestId } from '@/utils/ai-chat-stream';
import { getAiGovernanceCapabilities, getAiCalls, getAiCostSummary, getAiBudgets, saveAiBudget, reconcileAiCall } from '@/api/ai/tokenUsage';
import type { AiCallRow, AiCostSummary, AiBudgetRow, AiBudgetInput } from '@/api/ai/tokenUsage';
const loadError = ref('');
const loading = ref(false);
const saving = ref(false);
const enabled = ref(false);
const calls = ref<AiCallRow[]>([]);
const summaries = ref<AiCostSummary[]>([]);
const budgets = ref<AiBudgetRow[]>([]);
const budgetVisible = ref(false);
const correctionVisible = ref(false);
const editing = ref(false);
const budgetForm = ref<AiBudgetInput>({ budgetId: '', scopeType: 'TENANT_MONTH', scopeId: '', currency: '', limitAmount: '', enabled: false });
const correction = ref({ callId: '', currency: '', confirmedAmount: '', reason: '', idempotencyKey: '' });
async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    enabled.value = Boolean((await getAiGovernanceCapabilities()).enabled);
    if (!enabled.value) return;
    const result = await Promise.all([getAiCostSummary(), getAiBudgets(), checkPermi(['ai:tokenUsage:query']) ? getAiCalls() : Promise.resolve([])]);
    summaries.value = result[0];
    budgets.value = result[1];
    calls.value = result[2];
  } catch {
    loadError.value = '台账暂时无法加载，请刷新重试';
  } finally {
    loading.value = false;
  }
}
function editBudget(row?: AiBudgetRow) {
  editing.value = Boolean(row);
  budgetForm.value = row
    ? {
        budgetId: row.policyId,
        scopeType: row.scopeType,
        scopeId: row.scopeId,
        currency: row.currency,
        limitAmount: row.limitAmount,
        enabled: Boolean(row.enabled)
      }
    : { budgetId: createAiClientRequestId(), scopeType: 'TENANT_MONTH', scopeId: '', currency: '', limitAmount: '', enabled: false };
  budgetVisible.value = true;
}
const validAmount = (value: string) => /^\d{1,16}(?:\.\d{1,12})?$/.test(value);
async function saveBudget() {
  const data = {
    ...budgetForm.value,
    currency: budgetForm.value.currency.trim().toUpperCase(),
    scopeId: budgetForm.value.scopeType === 'TENANT_MONTH' ? '' : budgetForm.value.scopeId.trim()
  };
  if (!/^[A-Z]{3}$/.test(data.currency) || !validAmount(data.limitAmount) || (data.scopeType === 'AB_RUN' && !data.scopeId)) {
    ElMessage.warning('请填写有效币种、范围和金额');
    return;
  }
  saving.value = true;
  try {
    await saveAiBudget(data);
    budgetVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
function editCorrection(row: AiCallRow) {
  correction.value = {
    callId: row.callId,
    currency: row.settlementCurrency ?? row.currency ?? '',
    confirmedAmount: row.effectiveCost ?? row.cost ?? '',
    reason: '',
    idempotencyKey: createAiClientRequestId()
  };
  correctionVisible.value = true;
}
async function saveCorrection() {
  const data = { ...correction.value, currency: correction.value.currency.trim().toUpperCase() };
  if (!/^[A-Z]{3}$/.test(data.currency) || !validAmount(data.confirmedAmount) || !data.reason.trim()) {
    ElMessage.warning('请填写币种、确认费用与核对依据');
    return;
  }
  saving.value = true;
  try {
    await reconcileAiCall(data.callId, data);
    correctionVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
onMounted(() => {
  void load();
});
</script>
<style scoped>
.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.section-label {
  font-size: 14px;
  margin: 16px 0 8px;
}
</style>

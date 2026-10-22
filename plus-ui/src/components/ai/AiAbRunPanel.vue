<template>
  <el-drawer v-model="visible" title="A/B运行批次" size="min(1200px, 96vw)" append-to-body>
    <el-alert v-if="!enabled" title="批次执行尚未启用。历史结果仍可在原结果入口查看。" type="info" :closable="false" />
    <template v-else>
      <div class="run-toolbar">
        <el-button :loading="loading" @click="load">刷新批次</el-button
        ><el-button v-hasPermi="['ai:abtest:edit']" type="primary" :loading="starting" @click="start">启动新批次</el-button>
      </div>
      <el-alert
        title="每批冻结问题与模型配置。恢复保留成功项；未知调用须先在调用台账核对。费用按币种分别展示。"
        type="info"
        :closable="false"
        class="mb-4"
      />
      <el-table v-loading="loading" :data="runs" highlight-current-row @current-change="select"
        ><el-table-column prop="runId" label="批次ID" min-width="220" show-overflow-tooltip /><el-table-column
          prop="createdAt"
          label="创建时间"
          min-width="160"
        /><el-table-column label="状态" min-width="130"
          ><template #default="{ row }">{{ labels[row.status] ?? row.status }}</template></el-table-column
        ><el-table-column label="操作" width="180"
          ><template #default="{ row }"
            ><el-button
              v-if="['QUEUED', 'RUNNING', 'CANCEL_REQUESTED'].includes(row.status)"
              v-hasPermi="['ai:abtest:edit']"
              link
              type="warning"
              @click.stop="cancel(row)"
              >请求取消</el-button
            ><el-button
              v-if="['FAILED', 'PARTIAL_SUCCESS', 'RECOVERY_REQUIRED'].includes(row.status)"
              v-hasPermi="['ai:abtest:edit']"
              link
              type="primary"
              @click.stop="resume(row)"
              >续跑失败项</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <template v-if="selected">
        <h3 class="section-label">{{ selected.runId }} 的逐题结果</h3>
        <el-table :data="items" border
          ><el-table-column label="题号" width="70"
            ><template #default="{ row }">{{ row.questionIndex + 1 }}</template></el-table-column
          ><el-table-column prop="modelSlot" label="侧" width="55" /><el-table-column prop="modelName" label="模型" min-width="130" /><el-table-column
            prop="question"
            label="问题"
            min-width="180"
            show-overflow-tooltip /><el-table-column label="状态" min-width="120"
            ><template #default="{ row }">{{ labels[row.status] ?? row.status }}</template></el-table-column
          ><el-table-column prop="response" label="回答" min-width="230" show-overflow-tooltip /><el-table-column
            prop="latencyMs"
            label="延迟ms"
            width="100" /><el-table-column label="评分" width="150"
            ><template #default="{ row }"
              ><el-rate
                v-model="row.score"
                :disabled="row.status !== 'SUCCEEDED' || !canEdit"
                @change="(score: number) => saveScore(row, score)" /></template></el-table-column
        ></el-table>
        <h3 class="section-label">完成与失败指标</h3>
        <el-table :data="report.models" border
          ><el-table-column prop="modelSlot" label="侧" /><el-table-column prop="totalQuestions" label="总题数" /><el-table-column
            prop="succeeded"
            label="成功" /><el-table-column prop="failed" label="失败" /><el-table-column prop="unconfirmed" label="待核对" /><el-table-column
            prop="averageLatencyMs"
            label="成功项平均延迟ms" /><el-table-column prop="averageScore" label="成功项平均评分"
        /></el-table>
        <h3 class="section-label">调用与成本</h3>
        <el-table :data="report.costs" border
          ><el-table-column prop="modelSlot" label="侧" /><el-table-column prop="currency" label="币种" /><el-table-column
            prop="knownCost"
            label="已知费用估值" /><el-table-column prop="unknownCalls" label="待核对调用数" /><el-table-column
            prop="callCount"
            label="实际调用数" /><el-table-column prop="inputTokens" label="输入Token" /><el-table-column prop="outputTokens" label="输出Token"
        /></el-table>
      </template>
    </template>
  </el-drawer>
</template>
<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { checkPermi } from '@/utils/permission';
import { createAiClientRequestId } from '@/utils/ai-chat-stream';
import {
  getAbRunCapabilities,
  listAbRuns,
  startAbRun,
  getAbRunItems,
  getAbRunReport,
  cancelAbRun,
  resumeAbRun,
  scoreAbRunItem
} from '@/api/ai/abTest';
import type { AiAbRun, AiAbRunItem, AiAbRunReport } from '@/api/ai/abTest';
const props = defineProps<{ definitionId: string | null }>();
const visible = defineModel<boolean>({ required: true });
const emit = defineEmits<{ changed: [] }>();
const loading = ref(false);
const starting = ref(false);
const enabled = ref(false);
const runs = ref<AiAbRun[]>([]);
const selected = ref<AiAbRun | null>(null);
const items = ref<AiAbRunItem[]>([]);
const report = ref<AiAbRunReport>({ models: [], costs: [] });
const canEdit = computed(() => checkPermi(['ai:abtest:edit']));
const labels: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '运行中',
  CANCEL_REQUESTED: '正在取消',
  CANCELLED: '已取消',
  SUCCEEDED: '成功',
  PARTIAL_SUCCESS: '部分成功',
  FAILED: '失败',
  RECOVERY_REQUIRED: '结果待核对'
};
let epoch = 0;
let pendingStartKey: string | null = null;
async function load() {
  const id = props.definitionId;
  const version = epoch;
  if (!id || !visible.value) return;
  loading.value = true;
  try {
    const capability = await getAbRunCapabilities();
    if (version !== epoch) return;
    enabled.value = Boolean(capability.runsEnabled);
    if (!enabled.value) return;
    const result = await listAbRuns(id);
    if (version !== epoch) return;
    runs.value = result;
    if (selected.value) {
      const current = result.find((r) => r.runId === selected.value?.runId);
      if (current) await select(current);
    }
  } finally {
    if (version === epoch) loading.value = false;
  }
}
async function select(row: AiAbRun | null) {
  if (!row) return;
  selected.value = row;
  const version = epoch;
  const id = row.runId;
  const result = await Promise.all([getAbRunItems(id), getAbRunReport(id)]);
  if (version !== epoch || selected.value?.runId !== id) return;
  items.value = result[0];
  report.value = result[1];
}
async function start() {
  const version = epoch;
  const id = props.definitionId;
  if (!id || starting.value) return;
  starting.value = true;
  pendingStartKey ??= createAiClientRequestId();
  try {
    const run = await startAbRun(id, pendingStartKey);
    if (version !== epoch) return;
    pendingStartKey = null;
    await load();
    if (version !== epoch) return;
    await select(run);
    emit('changed');
  } finally {
    if (version === epoch) starting.value = false;
  }
}
async function cancel(row: AiAbRun) {
  await cancelAbRun(row.runId);
  await load();
  emit('changed');
}
async function resume(row: AiAbRun) {
  await resumeAbRun(row.runId);
  await load();
  emit('changed');
}
async function saveScore(row: AiAbRunItem, score: number) {
  if (!selected.value) return;
  await scoreAbRunItem(selected.value.runId, row.itemId, score);
  ElMessage.success('评分已保存');
  await select(selected.value);
}
watch([visible, () => props.definitionId], () => {
  epoch++;
  starting.value = false;
  loading.value = false;
  selected.value = null;
  items.value = [];
  report.value = { models: [], costs: [] };
  pendingStartKey = null;
  if (visible.value) void load();
});
onUnmounted(() => {
  epoch++;
});
</script>
<style scoped>
.run-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.section-label {
  font-size: 14px;
  margin: 20px 0 8px;
  overflow-wrap: anywhere;
}
</style>

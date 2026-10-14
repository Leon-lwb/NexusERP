<template>
  <div class="p-4">
    <el-card v-loading="loading"
      ><template #header
        ><div class="flex justify-between">
          <strong>交叉报表设计</strong><el-button :disabled="busy" @click="startNew">新建交叉报表</el-button>
        </div></template
      >
      <el-alert
        title="交叉报表使用固定快照；所有汇总和钻取由服务器完成。数值指标必须在行维度保留物料、单位或币种口径。"
        type="info"
        :closable="false"
      />
      <el-form v-if="dsl" label-width="110px" class="mt-4" :disabled="busy"
        ><el-form-item label="编码与标题"
          ><el-input v-model="dsl.code" :disabled="!!resource" placeholder="报表编码" /><el-input v-model="dsl.title" placeholder="标题" class="mt-2"
        /></el-form-item>
        <el-form-item label="固定数据集"
          ><el-select :model-value="dsl.datasetVersionId" class="w-full" @change="chooseDataset"
            ><el-option
              v-for="item in datasets"
              :key="item.dataset.id"
              :value="item.dataset.publishedVersion!"
              :label="item.descriptor.name + ' / ' + item.dataset.publishedVersion" /></el-select
        ></el-form-item>
        <template v-if="dsl.pivot"
          ><el-form-item label="行维度"
            ><el-select v-model="dsl.pivot.rowDimensions" multiple :multiple-limit="3" class="w-full"
              ><el-option v-for="field in fields" :key="field.name" :value="field.name" :label="field.label" /></el-select></el-form-item
          ><el-form-item label="列维度"
            ><el-select v-model="dsl.pivot.columnDimensions" multiple :multiple-limit="2" class="w-full"
              ><el-option v-for="field in fields" :key="field.name" :value="field.name" :label="field.label" /></el-select
          ></el-form-item>
          <el-form-item v-for="(measure, i) in dsl.pivot.measures" :key="i" :label="'指标 ' + (i + 1)"
            ><div class="flex gap-2 w-full">
              <el-select v-model="measure.field"
                ><el-option
                  v-for="field in fields.filter((f) => f.aggregatable)"
                  :key="field.name"
                  :value="field.name"
                  :label="field.label" /></el-select
              ><el-select v-model="measure.function"><el-option v-for="fn in functions" :key="fn" :value="fn" :label="fn" /></el-select
              ><el-input v-model="measure.label" placeholder="指标标题" /><el-input-number v-model="measure.scale" :min="0" :max="12" /><el-button
                :disabled="dsl.pivot.measures.length === 1"
                @click="dsl.pivot.measures.splice(i, 1)"
                >删除</el-button
              >
            </div></el-form-item
          >
          <el-form-item><el-button :disabled="dsl.pivot.measures.length >= 5" @click="addMeasure">添加指标</el-button></el-form-item
          ><el-form-item label="分组与TopN"
            ><el-checkbox v-model="dsl.pivot.subtotals">小计</el-checkbox><el-checkbox v-model="dsl.pivot.totals">分区总计</el-checkbox
            ><el-input-number v-model="dsl.pivot.topN" :min="0" :max="100" class="ml-3" /><span class="ml-2"
              >0表示全部；其他桶保留剩余记录</span
            ></el-form-item
          > </template
        ><el-form-item
          ><el-button v-hasPermi="['report:definition:edit']" type="primary" @click="save">保存草稿</el-button
          ><el-button v-hasPermi="['report:definition:publish']" :disabled="!resource || dirty" @click="publish">发布固定版本</el-button
          ><el-button :disabled="!resource?.publishedVersion" @click="view">运行</el-button></el-form-item
        > </el-form
      ><el-empty v-else description="请先在数据集目录注册并授权数据集" />
      <el-table :data="reports" class="mt-4"
        ><el-table-column prop="name" label="交叉报表" /><el-table-column prop="code" label="编码" /><el-table-column
          prop="status"
          label="状态"
        /><el-table-column label="操作"
          ><template #default="scope"
            ><el-button link @click="edit(scope.row.id)">设计</el-button
            ><el-button v-if="scope.row.publishedVersion" link @click="router.push({ path: '/report/viewer', query: { id: scope.row.id } })"
              >运行</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </el-card>
  </div>
</template>
<script setup lang="ts">
/** 专用交叉设计器管理schema2草稿，不修改旧发布定义。 */
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listDatasets, listReports, reportDetail, createReport, saveReport, publishReport, datasetVersion } from '@/api/report';
import type { ReportDsl, ReportDefinition, DatasetView, DatasetDescriptor } from '@/types/report';
import { createDsl, RequestGate } from '@/views/report/model';
/**
 * 保存当前页面或测试的router、route、datasets、reports、resource、dsl、descriptor、baseline、loading、busy状态。
 */
const router = useRouter(),
  route = useRoute(),
  datasets = ref<DatasetView[]>([]),
  reports = ref<ReportDefinition[]>([]),
  resource = ref<ReportDefinition>(),
  dsl = ref<ReportDsl>(),
  descriptor = ref<DatasetDescriptor>(),
  baseline = ref(''),
  loading = ref(false),
  busy = ref(false);
/**
 * 保存当前页面或测试的gate、editorGate、switchGate状态。
 */
const gate = new RequestGate(),
  editorGate = new RequestGate(),
  switchGate = new RequestGate();
/**
 * 保存当前页面或测试的functions状态。
 */
const functions = ['SUM', 'COUNT', 'COUNT_ROWS', 'MIN', 'MAX', 'AVG'] as const;
/**
 * 保存当前页面或测试的fields、dirty状态。
 */
const fields = computed(() => descriptor.value?.fields || []),
  dirty = computed(() => !!dsl.value && JSON.stringify(dsl.value) !== baseline.value);
/** 新草稿固定当前数据集发布版本，已有报表不自动升级。 */
function reset() {
  editorGate.invalidate();
  resource.value = undefined;
  const first = datasets.value[0];
  if (!first?.dataset.publishedVersion) {
    dsl.value = undefined;
    return;
  }
  descriptor.value = first.descriptor;
  const next = createDsl(first.descriptor, first.dataset.publishedVersion, 'PIVOT_REPORT');
  next.schemaVersion = 2;
  next.kind = 'PIVOT';
  next.headerRows = [];
  next.footerRows = [];
  next.summaries = [];
  next.pivot = { rowDimensions: [], columnDimensions: [], measures: [], subtotals: true, totals: true, topN: 0 };
  dsl.value = next;
  addMeasure();
  baseline.value = JSON.stringify(next);
}
/** 切换会话前确认尚未保存的编辑。 */
async function maySwitch() {
  const ticket = switchGate.begin();
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('有未保存的交叉设计，确认切换？', '切换设计');
    } catch {
      return false;
    }
  }
  return switchGate.current(ticket);
}
/** 新建交叉草稿。 */
async function startNew() {
  if (await maySwitch()) reset();
}
/** 载入可访问目录和交叉定义。 */
async function load() {
  const ticket = gate.begin();
  loading.value = true;
  try {
    const [a, b] = await Promise.all([listDatasets(), listReports()]);
    if (!gate.current(ticket)) return;
    datasets.value = a.data;
    reports.value = b.data.filter((item) => item.kind === 'PIVOT');
    if (!dsl.value) reset();
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
/** 读取固定数据集契约，等待期间阻止保存。 */
async function edit(id: string) {
  if (!(await maySwitch())) return;
  const ticket = editorGate.begin();
  busy.value = true;
  try {
    const response = await reportDetail(id);
    const version = await datasetVersion(response.data.dsl.datasetVersionId);
    if (!editorGate.current(ticket)) return;
    resource.value = response.data.definition;
    dsl.value = response.data.dsl;
    descriptor.value = version.data.descriptor;
    baseline.value = JSON.stringify(dsl.value);
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 数据集选择会重建轴和指标，防止留下旧字段。 */
function chooseDataset(id: string) {
  const item = datasets.value.find((value) => value.dataset.publishedVersion === id);
  if (!item || !dsl.value) return;
  descriptor.value = item.descriptor;
  dsl.value.datasetCode = item.dataset.code;
  dsl.value.datasetVersionId = id;
  dsl.value.parameters = createDsl(item.descriptor, id, 'PIVOT_REPORT').parameters;
  dsl.value.pivot = { rowDimensions: [], columnDimensions: [], measures: [], subtotals: true, totals: true, topN: 0 };
  addMeasure();
}
/** 添加一个只读目录中的指标。 */
function addMeasure() {
  const field = fields.value.find((value) => value.aggregatable);
  if (field)
    dsl.value?.pivot?.measures.push({
      field: field.name,
      function: ['DECIMAL', 'INTEGER'].includes(field.type) ? 'SUM' : 'COUNT_ROWS',
      label: field.label,
      scale: field.type === 'DECIMAL' ? 3 : 0
    });
}
/** 保存前用真实轴字段生成基础列，服务端再次校验全量权限。 */
async function save() {
  if (!dsl.value || busy.value) return;
  const input = JSON.parse(JSON.stringify(dsl.value)) as ReportDsl;
  const names = [
    ...new Set([
      ...(input.pivot?.rowDimensions || []),
      ...(input.pivot?.columnDimensions || []),
      ...(input.pivot?.measures.map((value) => value.field) || [])
    ])
  ];
  input.columns = createDsl({ ...descriptor.value!, defaultColumns: names }, input.datasetVersionId, input.code).columns;
  const ticket = editorGate.begin();
  busy.value = true;
  try {
    const response = resource.value ? await saveReport(resource.value.id, resource.value.revision, input) : await createReport(input, null);
    if (!editorGate.current(ticket)) return;
    dsl.value = input;
    resource.value = response.data;
    baseline.value = JSON.stringify(input);
    ElMessage.success('交叉草稿已保存');
    await load();
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 发布已经保存的固定修订。 */
async function publish() {
  if (!resource.value || dirty.value || busy.value) return;
  const ticket = editorGate.begin();
  busy.value = true;
  try {
    await publishReport(resource.value.id, resource.value.revision);
    if (editorGate.current(ticket)) {
      ElMessage.success('交叉报表已发布');
      await load();
      resource.value = reports.value.find((value) => value.id === resource.value?.id);
    }
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 使用正常运行页交付固定快照。 */
function view() {
  if (resource.value) router.push({ path: '/report/viewer', query: { id: resource.value.id } });
}
onBeforeRouteLeave(async () => !dirty.value || (await maySwitch()));
onMounted(async () => {
  await load();
  if (typeof route.query.id === 'string') await edit(route.query.id);
});
onUnmounted(() => {
  gate.invalidate();
  editorGate.invalidate();
  switchGate.invalidate();
});
</script>

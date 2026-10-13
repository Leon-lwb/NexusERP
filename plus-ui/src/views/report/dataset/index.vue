<template>
  <div class="p-4">
    <el-card shadow="never"
      ><template #header
        ><div class="flex items-center justify-between">
          <div>
            <strong class="text-xl">数据集目录</strong>
            <div class="text-gray-500 mt-2">选择已有业务契约，为报表提供当前用户有权读取的数据。</div>
          </div>
          <el-button v-hasPermi="['report:dataset:manage']" type="primary" @click="openProviders">注册数据集</el-button>
        </div></template
      >
      <el-alert v-if="failed" title="数据集载入失败，请刷新重试。" type="error" :closable="false" class="mb-4" />
      <el-button class="mb-4" icon="Refresh" @click="load">刷新</el-button>
      <el-table v-loading="loading" :data="datasets" border
        ><el-table-column prop="dataset.name" label="名称" min-width="180" /><el-table-column
          prop="dataset.code"
          label="编码"
          min-width="260"
        /><el-table-column label="契约版本" width="110"
          ><template #default="scope">v{{ scope.row.descriptor.version }}</template></el-table-column
        ><el-table-column label="状态" width="100"
          ><template #default="scope"
            ><el-tag :type="scope.row.dataset.status === 'ACTIVE' ? 'success' : 'info'">{{
              scope.row.dataset.status === 'ACTIVE' ? '启用' : '停用'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="370"
          ><template #default="scope"
            ><el-button link type="primary" @click="inspect(scope.row)">字段与样例</el-button
            ><el-button
              v-hasPermi="['report:definition:add']"
              link
              :disabled="scope.row.dataset.status !== 'ACTIVE'"
              @click="router.push({ path: '/report/designer', query: { dataset: scope.row.dataset.id } })"
              >创建报表</el-button
            ><el-button v-if="scope.row.canManage" v-hasPermi="['report:dataset:manage']" link @click="toggle(scope.row)">{{
              scope.row.dataset.status === 'ACTIVE' ? '停用' : '启用'
            }}</el-button
            ><el-button
              v-hasPermi="['report:acl:manage']"
              link
              @click="
                aclId = scope.row.dataset.id;
                aclOpen = true;
              "
              >授权</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </el-card>
    <el-dialog v-model="providerOpen" title="注册业务数据集" width="750px"
      ><el-table :data="providers" border
        ><el-table-column prop="name" label="名称" /><el-table-column prop="code" label="编码" min-width="270" /><el-table-column
          label="版本"
          width="70"
          ><template #default="scope">v{{ scope.row.version }}</template></el-table-column
        ><el-table-column width="110"
          ><template #default="scope"
            ><el-button type="primary" :loading="registering === scope.row.code" @click="register(scope.row)">注册</el-button></template
          ></el-table-column
        ></el-table
      ><el-empty v-if="!providers.length" description="当前业务权限下没有可注册的数据集"
    /></el-dialog>
    <el-drawer v-model="detailOpen" :title="selected?.dataset.name || '数据集'" size="80%"
      ><template v-if="selected"
        ><el-tabs v-model="tab"
          ><el-tab-pane label="字段契约" name="fields"
            ><el-table :data="selected.descriptor.fields" border
              ><el-table-column prop="label" label="字段" /><el-table-column prop="name" label="绑定名" /><el-table-column
                prop="type"
                label="类型"
                width="120"
              /><el-table-column label="数值汇总分区"
                ><template #default="scope">{{
                  scope.row.aggregatable ? scope.row.aggregationKeys.join('、') || '无额外分区' : '不允许数值汇总'
                }}</template></el-table-column
              ></el-table
            ></el-tab-pane
          ><el-tab-pane label="数据样例" name="sample"
            ><ParameterForm v-model="parameters" :parameters="selected.descriptor.parameters" /><el-button
              v-hasPermi="['report:dataset:list']"
              type="primary"
              :loading="sampling"
              :disabled="selected.dataset.status !== 'ACTIVE'"
              @click="sample"
              >查询样例</el-button
            >
            <p class="text-gray-500">最多展示 50 行当前权限范围内的数据。</p>
            <TypedTable v-if="sampleData" :columns="sampleColumns" :rows="sampleData.rows" /></el-tab-pane></el-tabs></template
    ></el-drawer>
    <AclDialog v-model:open="aclOpen" resource-type="DATASET" :resource-id="aclId" />
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted, watch, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listDatasets, listProviders, registerDataset, datasetState, sampleDataset } from '@/api/report';
import type { DatasetView, DatasetDescriptor, DraftPreview, ReportColumn } from '@/types/report';
import { normalStyle, parameterInput, RequestGate } from '@/views/report/model';
import ParameterForm from '@/components/report/ParameterForm.vue';
import TypedTable from '@/components/report/TypedTable.vue';
import AclDialog from '@/components/report/AclDialog.vue';
defineOptions({ name: 'ReportDataset' });
const router = useRouter();
const loading = ref(false);
const failed = ref(false);
const loadGate = new RequestGate();
const datasets = ref<DatasetView[]>([]);
const providers = ref<DatasetDescriptor[]>([]);
const providerOpen = ref(false);
const registering = ref('');
const detailOpen = ref(false);
const selected = ref<DatasetView>();
const tab = ref('fields');
const parameters = ref<Record<string, string>>({});
const sampling = ref(false);
const sampleData = ref<DraftPreview>();
const aclId = ref('');
const aclOpen = ref(false);
const gate = new RequestGate();
const sampleColumns = computed<ReportColumn[]>(
  () =>
    selected.value?.descriptor.defaultColumns.map((name) => {
      const field = selected.value!.descriptor.fields.find((f) => f.name === name)!;
      return { field: name, label: field.label, width: 120, scale: field.type === 'DECIMAL' ? 4 : 0, expression: null, style: normalStyle() };
    }) || []
);
async function load() {
  const ticket = loadGate.begin();
  loading.value = true;
  failed.value = false;
  try {
    const response = await listDatasets();
    if (loadGate.current(ticket)) datasets.value = response.data;
  } catch {
    if (loadGate.current(ticket)) {
      failed.value = true;
      datasets.value = [];
    }
  } finally {
    if (loadGate.current(ticket)) loading.value = false;
  }
}
async function openProviders() {
  try {
    providers.value = (await listProviders()).data;
    providerOpen.value = true;
  } catch {}
}
async function register(descriptor: DatasetDescriptor) {
  registering.value = descriptor.code;
  try {
    await registerDataset(descriptor.code, descriptor.version);
    ElMessage.success('数据集已注册');
    await load();
  } catch {
  } finally {
    registering.value = '';
  }
}
function inspect(dataset: DatasetView) {
  invalidateSample();
  selected.value = dataset;
  sampleData.value = undefined;
  parameters.value = {};
  detailOpen.value = true;
  tab.value = 'fields';
}
async function sample() {
  if (!selected.value) return;
  const ticket = gate.begin();
  sampling.value = true;
  try {
    const result = await sampleDataset(selected.value.dataset.id, parameterInput(parameters.value));
    if (gate.current(ticket)) sampleData.value = result.data;
  } catch {
  } finally {
    if (gate.current(ticket)) sampling.value = false;
  }
}
async function toggle(dataset: DatasetView) {
  try {
    const state = dataset.dataset.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    await ElMessageBox.confirm(state === 'DISABLED' ? '停用后引用此数据集的报表将暂停运行。' : '启用此数据集？', '数据集状态');
    await datasetState(dataset.dataset.id, state, dataset.dataset.status);
    await load();
  } catch {}
}
function invalidateSample() {
  gate.invalidate();
  sampling.value = false;
  sampleData.value = undefined;
}
watch(parameters, invalidateSample, { deep: true, flush: 'sync' });
watch(
  detailOpen,
  (open) => {
    if (!open) invalidateSample();
  },
  { flush: 'sync' }
);
onUnmounted(() => {
  invalidateSample();
  loadGate.invalidate();
});
onMounted(load);
</script>

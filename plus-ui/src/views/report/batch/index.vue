<template>
  <div class="p-4">
    <el-card v-loading="loading"
      ><template #header>固定单据批量打印</template
      ><el-alert
        title="批次固定各单据的发布版本和参数，每份独立鉴权与导出；一份失败不会覆盖其余结果。完成的PDF在导出中心领取。"
        type="info"
        :closable="false"
      />
      <div class="flex gap-3 mt-4">
        <el-input v-model="code" placeholder="批次编码" /><el-input v-model="title" placeholder="批次标题" /><el-button
          :disabled="busy || documents.length >= 50"
          @click="documents.push({ reportVersionId: '', parametersText: '{}' })"
          >增加单据</el-button
        ><el-button v-hasPermi="['report:batch:manage']" type="primary" :loading="busy" @click="create">创建并入队</el-button>
      </div>
      <div v-for="(item, index) in documents" :key="index" class="flex gap-3 mt-3">
        <el-select v-model="item.reportVersionId" placeholder="固定单据发布"
          ><el-option
            v-for="report in reports"
            :key="report.id"
            :value="report.publishedVersion!"
            :label="report.name + ' / ' + report.publishedVersion" /></el-select
        ><el-input v-model="item.parametersText" type="textarea" :rows="2" placeholder='参数对象，如{"orderId":"完整ID"}' /><el-button
          :disabled="busy || documents.length === 1"
          @click="documents.splice(index, 1)"
          >删除</el-button
        >
      </div>
      <el-table :data="batches" class="mt-4"
        ><el-table-column prop="title" label="批次" /><el-table-column prop="code" label="编码" /><el-table-column label="操作"
          ><template #default="scope"
            ><el-button v-hasPermi="['report:acl:manage']" link :disabled="busy" @click="aclId = scope.row.id">资源授权</el-button
            ><el-button link @click="open(scope.row.id)">查看逐项结果</el-button
            ><el-button v-hasPermi="['report:batch:manage']" link :disabled="busy" @click="start(scope.row.id)">补充入队</el-button></template
          ></el-table-column
        ></el-table
      ><el-table v-if="selected" :data="items"
        ><el-table-column prop="number" label="序号" /><el-table-column prop="reportVersionId" label="固定版本" /><el-table-column
          prop="state"
          label="状态" /><el-table-column prop="error" label="结果码" /><el-table-column prop="jobId" label="导出任务" /></el-table
      ><el-button class="mt-3" @click="router.push('/report/exports')">打开导出中心</el-button></el-card
    >
  </div>
  <AclDialog
    :open="!!aclId"
    resource-type="BATCH"
    :resource-id="aclId"
    @update:open="
      (value) => {
        if (!value) aclId = '';
      }
    "
  />
</template>
<script setup lang="ts">
import AclDialog from '@/components/report/AclDialog.vue';
/** 当前弹窗绑定的资源ID，关闭或切换时由弹窗请求围栏保护。 */
const aclId = ref('');
/** 批次设计仅接受明确版本与精确字符串参数。 */
import { ref, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { listReports } from '@/api/report';
import { listBatches, createBatch, startBatch, batchItems, type BatchItem } from '@/api/report/batch';
import type { ReportDefinition } from '@/types/report';
import type { ReportResource } from '@/api/report/query';
import { RequestGate } from '@/views/report/model';
/**
 * 保存当前页面或测试的router、reports、batches、items、documents、code、title、selected、loading、busy状态。
 */
const router = useRouter(),
  reports = ref<ReportDefinition[]>([]),
  batches = ref<ReportResource[]>([]),
  items = ref<BatchItem[]>([]),
  documents = ref([{ reportVersionId: '', parametersText: '{}' }]),
  code = ref(''),
  title = ref(''),
  selected = ref(''),
  loading = ref(false),
  busy = ref(false);
/**
 * 保存当前页面或测试的gate、detailGate、writeGate状态。
 */
const gate = new RequestGate(),
  detailGate = new RequestGate(),
  writeGate = new RequestGate();
/** 刷新单据定义及可访问批次。 */
async function load() {
  const ticket = gate.begin();
  loading.value = true;
  try {
    const [a, b] = await Promise.all([listReports(), listBatches()]);
    if (gate.current(ticket)) {
      reports.value = a.data.filter((report) => report.kind === 'DOCUMENT' && report.status === 'PUBLISHED');
      batches.value = b.data;
    }
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
/** 参数对象中的ID和DECIMAL要求字符串，防止粘贴JSON数字精度丢失。 */
function fixedDocuments() {
  return documents.value.map((item) => {
    const value = JSON.parse(item.parametersText);
    if (
      !item.reportVersionId ||
      !value ||
      Array.isArray(value) ||
      typeof value !== 'object' ||
      Object.values(value).some((entry) => typeof entry !== 'string')
    )
      throw new Error('请选择固定版本，参数对象的值均应为字符串');
    return { reportVersionId: item.reportVersionId, parameters: value as Record<string, string> };
  });
}
/** 显式创建后把各单据独立送入持久导出中心。 */
async function create() {
  if (busy.value) return;
  let input;
  try {
    input = fixedDocuments();
  } catch (error) {
    ElMessage.warning((error as Error).message);
    return;
  }
  const ticket = writeGate.begin();
  busy.value = true;
  try {
    const response = await createBatch(code.value, title.value, input);
    if (!writeGate.current(ticket)) return;
    await startBatch(response.data.id);
    if (writeGate.current(ticket)) {
      await load();
      await open(response.data.id);
      ElMessage.success('固定批次已逐项入队');
    }
  } finally {
    if (writeGate.current(ticket)) busy.value = false;
  }
}
/** 逐项结果受当前授权保护，迟到回包不切换批次。 */
async function open(id: string) {
  const ticket = detailGate.begin();
  selected.value = id;
  items.value = [];
  try {
    const response = await batchItems(id);
    if (detailGate.current(ticket)) items.value = response.data;
  } catch {}
}
/** 对尚未成功入队的项补充处理，成功项保持幂等。 */
async function start(id: string) {
  if (busy.value) return;
  const ticket = writeGate.begin();
  busy.value = true;
  try {
    await startBatch(id);
    if (writeGate.current(ticket)) await open(id);
  } finally {
    if (writeGate.current(ticket)) busy.value = false;
  }
}
onMounted(load);
onUnmounted(() => {
  gate.invalidate();
  detailGate.invalidate();
  writeGate.invalidate();
});
</script>

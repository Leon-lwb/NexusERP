<template>
  <div class="harness">
    <header>
      <h1>业务单据网格设计</h1>
      <el-tag type="warning">隔离合成数据 · 未连接业务后端</el-tag>
      <el-button data-test="theme" @click="toggleTheme">切换深色</el-button>
      <el-button data-test="undo" :disabled="!canUndo" @click="undo">撤销</el-button>
      <el-button data-test="redo" :disabled="!canRedo" @click="redo">重做</el-button>
    </header>
    <div v-if="mounted" :key="componentKey" class="panels">
      <el-card shadow="never">
        <PaperGrid :model-value="dsl" @update:model-value="commit" @add-band="addBand" @gesture="gesture = $event" />
      </el-card>
      <aside>
        <el-card shadow="never">
          <h2>纸张与分页</h2>
          <PaperSettings :model-value="dsl" @update:model-value="commit" />
        </el-card>
        <el-card shadow="never">
          <h2>私有图片与条码</h2>
          <PrintSettings :model-value="dsl" :fields="fields" @update:model-value="commit" />
        </el-card>
      </aside>
    </div>
    <el-card v-if="businessMounted" data-test="business" shadow="never">
      <h2>合成业务列表</h2>
      <el-input v-model="businessFilter" data-test="business-filter" placeholder="保留的列表筛选" />
      <el-checkbox-group v-model="businessIds" data-test="business-selection">
        <el-checkbox v-for="document in documents" :key="document.id" :value="document.id">{{ document.code }}</el-checkbox>
      </el-checkbox-group>
      <div data-test="business-single">
        <BusinessReportButton dataset-code="SYNTHETIC_DATASET" parameter="orderId" :record-id="documents[0].id" :document="documents[0]" />
      </div>
      <div data-test="business-batch">
        <BusinessReportButton dataset-code="SYNTHETIC_DATASET" parameter="orderId" :record-ids="businessIds" :documents="documents" />
      </div>
    </el-card>
    <router-view v-if="designerMounted" />
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import { useRouter } from 'vue-router';
import PaperGrid from '@/components/report/designer/PaperGrid.vue';
import PaperSettings from '@/components/report/designer/PaperSettings.vue';
import PrintSettings from '@/components/report/PrintSettings.vue';
import BusinessReportButton from '@/components/report/BusinessReportButton.vue';
import { DslHistory } from '@/views/report/model';
import type { DatasetField, ReportDsl } from '@/types/report';
import request, { fixture, session } from './api.mjs';

/** 合成样本只包含排版值，工业业务数值不在浏览器生成。 */
function sample(): ReportDsl {
  const style = { fontSize: 10, bold: false, align: 'LEFT' as const, color: '#111111', background: '#ffffff' };
  /** 固定格使用与真实设计器相同的逻辑网格坐标。 */
  const cell = (column: number, colSpan: number, text: string) => ({ column, colSpan, rowSpan: 1, text, field: null, style: { ...style } });
  return {
    schemaVersion: 2,
    code: fixture.reportCode,
    title: '采购订单 · 合成样本',
    kind: 'DOCUMENT',
    datasetCode: 'SYNTHETIC_DATASET',
    datasetVersionId: fixture.datasetVersionId,
    parameters: [],
    summaries: [],
    repeatHeader: true,
    page: { width: 595.28, height: 841.89, marginLeft: 36, marginRight: 36, marginTop: 36, marginBottom: 36, rowHeight: 24 },
    columns: [
      { field: 'materialCode', label: '物料编码', width: 180 },
      { field: 'materialName', label: '物料名称', width: 213.28 },
      { field: 'quantity', label: '数量', width: 130 }
    ].map((column) => ({ ...column, scale: 4, expression: null, style: { ...style } })),
    headerRows: [
      { height: 32, cells: [cell(0, 3, '采购单号：SYN-20261017-001')] },
      { height: 32, cells: [cell(0, 1, '供应商：合成供应商'), cell(1, 2, '日期：2026-10-17')] }
    ],
    footerRows: [{ height: 32, cells: [cell(0, 2, '采购签字：________________'), cell(2, 1, '审核：________')] }],
    print: { orientation: 'PORTRAIT', groupBy: [], repeatFooter: false, media: [] }
  };
}

/** 历史栈直接复用产品逻辑，计数反映真实组件提交次数。 */
const dsl = ref(sample());
const history = new DslHistory();
history.reset(dsl.value);
const updates = ref(0),
  historyRevision = ref(0),
  componentKey = ref(0),
  mounted = ref(true),
  gesture = ref(false);
const canUndo = computed(() => {
  void historyRevision.value;
  return history.canUndo;
});
const canRedo = computed(() => {
  void historyRevision.value;
  return history.canRedo;
});
const fields: DatasetField[] = [
  { name: 'materialCode', label: '物料编码', type: 'STRING', permission: null, aggregationKeys: [], aggregatable: false }
];
/** 两份合成单据使用不同完整ID，名称仅供确认顺序展示。 */
const documents = [
  { id: '9223372036854775791', code: 'SYN-PO-001', counterparty: '合成供应商甲' },
  { id: '9223372036854775792', code: 'SYN-PO-002', counterparty: '合成供应商乙' }
];
const businessMounted = ref(false),
  businessFilter = ref('保留的采购筛选'),
  businessIds = ref(documents.map((document) => document.id));
const designerMounted = ref(false);
const router = useRouter();

/** 每次原子提交写入真实历史，指针预览不产生记录。 */
function commit(value: ReportDsl) {
  history.record(value);
  updates.value++;
  historyRevision.value++;
  dsl.value = value;
}
/** 撤销恢复整次手势的源快照。 */
function undo() {
  const value = history.undo();
  if (value) dsl.value = value;
  historyRevision.value++;
}
/** 重做恢复整次手势的提交结果。 */
function redo() {
  const value = history.redo();
  if (value) dsl.value = value;
  historyRevision.value++;
}
/** 固定行操作仍由真实画布发起。 */
function addBand(band: 'headerRows' | 'footerRows') {
  const value = structuredClone(JSON.parse(JSON.stringify(dsl.value))) as ReportDsl;
  value[band].push({ height: 32, cells: [{ ...value.headerRows[0].cells[0], text: '合成新增固定行' }] });
  commit(value);
}
/** 切换ElementPlus真实深色变量。 */
function toggleTheme() {
  document.documentElement.classList.toggle('dark');
}

/** 只向隔离驱动暴露观测与重置入口，不仿制组件内部行为。 */
(window as any).__reportHarness = {
  get dsl() {
    return dsl.value;
  },
  get updateCount() {
    return updates.value;
  },
  get gesture() {
    return gesture.value;
  },
  fixture,
  request,
  session,
  get businessFilter() {
    return businessFilter.value;
  },
  get businessIds() {
    return businessIds.value;
  },
  async setBusinessIds(values: string[]) {
    businessIds.value = [...values];
    await nextTick();
  },
  documents,
  async startDesigner(permissions: string[] = fixture.permissions) {
    mounted.value = businessMounted.value = false;
    session.permissions = [...permissions];
    await router.push({ path: '/report/designer', query: { id: fixture.reportId } });
    designerMounted.value = true;
    await nextTick();
  },
  async stopDesigner() {
    designerMounted.value = false;
    await nextTick();
  },
  async startBusiness() {
    mounted.value = false;
    session.permissions = [...fixture.permissions];
    businessMounted.value = true;
    await nextTick();
  },
  async setPermissions(permissions: string[]) {
    session.permissions = [...permissions];
    componentKey.value++;
    await nextTick();
  },
  async unmount() {
    mounted.value = false;
    await nextTick();
  },
  async reset() {
    session.permissions = [...fixture.permissions];
    session.tenant = fixture.tenant;
    dsl.value = sample();
    history.reset(dsl.value);
    updates.value = 0;
    historyRevision.value++;
    mounted.value = true;
    componentKey.value++;
    await nextTick();
  }
};
</script>
<style>
body {
  margin: 0;
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
  font-family: Arial, 'Microsoft YaHei', sans-serif;
}
.harness {
  padding: 20px;
  max-width: 1600px;
  margin: auto;
}
header {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
h1 {
  font-size: 22px;
  margin: 0;
}
h2 {
  font-size: 16px;
  margin-top: 0;
}
.panels {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
}
.panels > .el-card,
aside {
  min-width: 0;
}
aside {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.el-dialog {
  max-width: calc(100vw - 32px);
}
@media (max-width: 900px) {
  .harness {
    padding: 10px;
  }
  .panels {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

<template>
  <div v-loading="loading" class="p-4 report-designer">
    <el-card shadow="never"
      ><template #header
        ><div class="designer-heading">
          <div>
            <strong class="text-xl">{{ definition ? '设计 · ' + dsl?.title : '新建报表' }}</strong
            ><el-tag v-if="dirty" class="ml-3" type="warning">未保存</el-tag
            ><el-tag v-else-if="definition" class="ml-3" type="success">已保存 · 修订 {{ definition.revision }}</el-tag>
          </div>
          <div class="flex gap-2">
            <el-button @click="router.push('/report/center')">返回</el-button
            ><el-button :disabled="!canUndo || saving || publishing || applying || jsonDirty" title="撤销；最多保留 50 个设计状态" @click="undo"
              >撤销</el-button
            ><el-button :disabled="!canRedo || saving || publishing || applying || jsonDirty" title="重做；新的编辑会清除重做记录" @click="redo"
              >重做</el-button
            ><el-button :disabled="!dsl || saving || publishing || applying" :loading="checking" @click="validate">校验</el-button
            ><el-button :disabled="!dsl || saving || publishing || applying" :loading="previewing" @click="preview">数据预览</el-button
            ><el-button
              v-if="dsl"
              :key="definition ? 'edit' : 'add'"
              v-hasPermi="[definition ? 'report:definition:edit' : 'report:definition:add']"
              type="primary"
              :disabled="publishing || applying"
              :loading="saving"
              @click="save()"
              >保存草稿</el-button
            ><span
              v-if="dsl"
              :key="definition ? 'publish-edit' : 'publish-add'"
              v-hasPermi="[definition ? 'report:definition:edit' : 'report:definition:add']"
              ><el-button
                v-hasPermi="['report:definition:publish']"
                type="success"
                :disabled="saving || applying"
                :loading="publishing"
                @click="publish"
                >保存并发布</el-button
              ></span
            >
          </div>
        </div></template
      >
      <div class="designer-config">
        <el-select
          :model-value="selectedDataset?.dataset.id"
          :disabled="!!definition || saving || publishing || applying"
          placeholder="选择数据集"
          style="width: 310px"
          @change="chooseDataset"
          ><el-option
            v-for="dataset in datasets.filter((d) => d.dataset.status === 'ACTIVE')"
            :key="dataset.dataset.id"
            :label="dataset.dataset.name"
            :value="dataset.dataset.id" /></el-select
        ><template v-if="dsl"
          ><el-input
            v-model="dsl.code"
            :disabled="!!definition || saving || publishing || applying"
            placeholder="报表编码，如 STOCK_BALANCE"
            style="width: 220px" /><el-input
            v-model="dsl.title"
            :disabled="saving || publishing || applying"
            placeholder="报表标题"
            style="width: 280px" /><el-select
            v-model="catalogId"
            clearable
            placeholder="选择目录"
            :disabled="!!definition || saving || publishing || applying"
            style="width: 160px"
            ><el-option v-for="catalog in catalogs" :key="catalog.id" :label="catalog.name" :value="catalog.id" /></el-select
          ><el-select v-model="dsl.kind" :disabled="!!definition || saving" style="width: 140px" @change="fitColumns(dsl)"
            ><el-option label="列表报表" value="TABLE" /><el-option label="打印单据" value="DOCUMENT" /></el-select
        ></template>
      </div>
      <el-empty v-if="!dsl" description="选择业务数据集开始设计；尚无数据集时，请先在数据集目录注册。"
        ><el-button @click="router.push('/report/dataset')">前往数据集目录</el-button></el-empty
      >
      <el-tabs v-if="dsl" v-model="tab" v-loading="saving || publishing || applying">
        <el-tab-pane label="设计画布" name="canvas"
          ><div v-loading="saving" class="designer-grid">
            <aside class="field-palette">
              <h3>可用字段</h3>
              <p class="text-gray-500 text-xs">双击添加，或拖入明细区。</p>
              <div
                v-for="field in selectedDataset?.descriptor.fields"
                :key="field.name"
                class="field-item"
                draggable="true"
                @dragstart="dragField($event, field.name)"
                @dblclick="insertField(field)"
              >
                <span>{{ field.label }}</span
                ><small>{{ field.type }}</small>
              </div>
              <el-button class="mt-3" @click="insertFormula">添加计算列</el-button
              ><el-button v-if="dsl.kind === 'DOCUMENT'" class="mt-2" @click="fitColumns(dsl)">适配纸张宽度</el-button>
            </aside>
            <main class="canvas-area" @dragover.prevent @drop.prevent="dropField">
              <div class="canvas-title">{{ dsl.title }}</div>
              <div v-for="band in bands" :key="band.key" class="band-section">
                <div class="band-caption">
                  <span>{{ band.label }}</span
                  ><el-button link @click="addBand(band.key)">添加固定行</el-button>
                </div>
                <div v-if="dsl[band.key].length" class="band-grid" :style="bandGrid(band.key)">
                  <template v-for="(row, r) in dsl[band.key]" :key="r"
                    ><div
                      v-for="(cell, c) in row.cells"
                      :key="r + '-' + c"
                      class="band-cell"
                      :class="{ selected: isCellSelected(band.key, r, c) }"
                      :style="{
                        gridColumn: cell.column + 1 + ' / span ' + cell.colSpan,
                        gridRow: r + 1 + ' / span ' + cell.rowSpan,
                        textAlign: cell.style.align.toLowerCase() as 'left' | 'center' | 'right',
                        color: cell.style.color,
                        background: cell.style.background
                      }"
                      @click="selection = { type: 'cell', band: band.key, row: r, cell: c }"
                    >
                      {{ cell.text || (cell.field ? bindingLabel(cell.field) : '空白') }}
                    </div></template
                  >
                </div>
                <div v-for="(_, r) in dsl[band.key]" :key="'tools-' + r" class="band-row-tools">
                  <small>固定行 {{ r + 1 }}</small
                  ><el-button link @click="mergeRow(band.key, r)">整行合并</el-button
                  ><el-button link @click="splitRow(band.key, r)">按列拆分</el-button
                  ><el-button
                    link
                    type="danger"
                    @click="
                      dsl[band.key].splice(r, 1);
                      selection = undefined;
                    "
                    >删除行</el-button
                  >
                </div>
              </div>
              <div class="detail-caption">可增长明细区 · {{ dsl.columns.length }} 列</div>
              <div class="column-strip">
                <div
                  v-for="(column, index) in dsl.columns"
                  :key="column.field"
                  class="column-tile"
                  :class="{ selected: selection?.type === 'column' && selection.index === index }"
                  :style="{ minWidth: Math.max(80, column.width) + 'px' }"
                  draggable="true"
                  @dragstart="dragIndex = index"
                  @dragover.prevent
                  @drop.stop.prevent="reorder(index)"
                  @click="selection = { type: 'column', index }"
                >
                  <strong>{{ column.label }}</strong>
                  <div class="text-xs text-gray-500">{{ column.field }}</div>
                  <div class="example-cell">{{ column.expression ? '= ' + column.expression : '明细值' }}</div>
                </div>
              </div>
              <p class="text-gray-500 text-xs">拖动列调整顺序；点选列或固定区域单元格，在右侧设置样式和绑定。</p>
            </main>
            <aside class="property-panel">
              <h3>属性</h3>
              <el-form label-position="top" size="small">
                <template v-if="selectedColumn"
                  ><el-form-item label="列标题"><el-input v-model="selectedColumn.label" /></el-form-item
                  ><el-form-item label="宽度（pt）"
                    ><el-input-number v-model="selectedColumn.width" :min="20" :max="600" :precision="2" /></el-form-item
                  ><el-form-item label="小数位"><el-input-number v-model="selectedColumn.scale" :min="0" :max="12" /></el-form-item
                  ><el-form-item label="计算表达式"
                    ><el-input v-model="selectedColumn.expression" placeholder="例如 ROUND(quantity * unitPrice, 2)" type="textarea" :rows="3"
                  /></el-form-item>
                  <p class="text-xs text-gray-500">支持十进制运算、IF、ROUND、COALESCE、SUM 和 COUNT。</p>
                  <el-button type="danger" link :disabled="dsl.columns.length === 1" @click="deleteColumn">移除列</el-button></template
                >
                <template v-if="selectedCell"
                  ><el-form-item label="固定行高度（pt）"><el-input-number v-model="selectedBandRow!.height" :min="12" :max="160" /></el-form-item
                  ><el-form-item label="起始列（从 0 开始）"
                    ><el-input-number v-model="selectedCell.column" :min="0" :max="dsl.columns.length - 1" /></el-form-item
                  ><el-form-item label="合并列数"
                    ><el-input-number v-model="selectedCell.colSpan" :min="1" :max="dsl.columns.length - selectedCell.column" /></el-form-item
                  ><el-form-item label="合并行数"><el-input-number v-model="selectedCell.rowSpan" :min="1" :max="remainingBandRows" /></el-form-item
                  ><el-form-item label="文本与绑定"
                    ><el-input
                      v-model="selectedCell.text"
                      type="textarea"
                      :rows="3"
                      placeholder="如 单号：{{orderNo}}，页数：{{page_number}}/{{page_count}}" /></el-form-item
                  ><el-form-item label="字段绑定"
                    ><el-select v-model="selectedCell.field" clearable
                      ><el-option
                        v-for="field in selectedDataset?.descriptor.fields"
                        :key="field.name"
                        :label="field.label"
                        :value="field.name" /></el-select></el-form-item
                ></template>
                <template v-if="selectedStyle"
                  ><el-divider /><el-form-item label="字号"><el-input-number v-model="selectedStyle.fontSize" :min="6" :max="32" /></el-form-item
                  ><el-form-item label="对齐"
                    ><el-radio-group v-model="selectedStyle.align"
                      ><el-radio-button value="LEFT">左</el-radio-button><el-radio-button value="CENTER">中</el-radio-button
                      ><el-radio-button value="RIGHT">右</el-radio-button></el-radio-group
                    ></el-form-item
                  ><el-checkbox v-model="selectedStyle.bold">加粗</el-checkbox
                  ><el-form-item label="文字颜色"><el-color-picker v-model="selectedStyle.color" /></el-form-item
                  ><el-form-item label="背景颜色"><el-color-picker v-model="selectedStyle.background" /></el-form-item
                ></template>
                <el-empty v-if="!selectedStyle" description="选择画布中的列或单元格" :image-size="48" />
              </el-form>
            </aside></div
        ></el-tab-pane>
        <el-tab-pane label="纸张与分页" name="paper"
          ><el-form inline label-position="top"
            ><el-form-item v-for="setting in paperSettings" :key="setting.key" :label="setting.label + '（pt）'"
              ><el-input-number v-model="dsl.page[setting.key]" :min="0" :max="1700" :precision="2" /></el-form-item
            ><el-form-item label="续页列头"><el-switch v-model="dsl.repeatHeader" /></el-form-item></el-form
          ><el-alert title="纸张尺寸、字体测量和换行由服务器统一计算；校验可检查越界与跨区合并。" type="info" :closable="false"
        /></el-tab-pane>
        <el-tab-pane label="查询参数" name="parameters"
          ><el-table :data="dsl.parameters" border
            ><el-table-column prop="name" label="参数绑定" /><el-table-column label="显示名称"
              ><template #default="scope"><el-input v-model="scope.row.label" /></template></el-table-column
            ><el-table-column prop="type" label="类型" width="110" /><el-table-column label="必填" width="90"
              ><template #default="scope"><el-checkbox v-model="scope.row.required" /></template></el-table-column
            ><el-table-column label="默认值"
              ><template #default="scope"
                ><el-input v-model="scope.row.defaultValue" placeholder="日期可使用 TODAY / MONTHS_AGO_6" /></template></el-table-column></el-table
        ></el-tab-pane>
        <el-tab-pane label="汇总" name="summaries"
          ><div class="flex gap-3 mb-4">
            <el-select v-model="summaryField" placeholder="选择可汇总的数值字段" style="width: 250px"
              ><el-option v-for="field in summaryFields" :key="field.name" :label="field.label" :value="field.name" /></el-select
            ><el-button @click="addSummary">添加合计</el-button
            ><el-button @click="dsl.summaries.push({ field: null, function: 'COUNT_ROWS', groupBy: [], label: '记录条数', scale: 0 })"
              >添加记录条数</el-button
            >
          </div>
          <el-table :data="dsl.summaries" border
            ><el-table-column label="标题"
              ><template #default="scope"><el-input v-model="scope.row.label" /></template></el-table-column
            ><el-table-column prop="field" label="字段" /><el-table-column label="函数" width="145"
              ><template #default="scope"
                ><span v-if="scope.row.function === 'COUNT_ROWS'">COUNT_ROWS</span
                ><el-select v-else v-model="scope.row.function"
                  ><el-option
                    v-for="fn in ['SUM', 'MIN', 'MAX', 'COUNT']"
                    :key="fn"
                    :label="fn"
                    :value="fn" /></el-select></template></el-table-column
            ><el-table-column label="分组维度" min-width="220"
              ><template #default="scope"
                ><el-select v-model="scope.row.groupBy" multiple
                  ><el-option
                    v-for="field in selectedDataset?.descriptor.fields"
                    :key="field.name"
                    :label="field.label"
                    :value="field.name" /></el-select></template></el-table-column
            ><el-table-column label="精度" width="150"
              ><template #default="scope"><el-input-number v-model="scope.row.scale" :min="0" :max="12" size="small" /></template></el-table-column
            ><el-table-column width="70"
              ><template #default="scope"
                ><el-button type="danger" link @click="dsl.summaries.splice(scope.$index, 1)">移除</el-button></template
              ></el-table-column
            ></el-table
          >
          <p class="text-gray-500">金额、数量的币种、物料或单位分区将由数据契约强制检查。</p></el-tab-pane
        >
        <el-tab-pane label="定义文件" name="json"
          ><el-input v-model="jsonText" :disabled="saving || publishing || applying" type="textarea" :rows="22" class="dsl-json" />
          <div class="mt-4 flex gap-3">
            <el-button :loading="applying" :disabled="saving || publishing" @click="applyJson">校验并应用</el-button
            ><el-button @click="exportDsl">下载定义</el-button>
          </div></el-tab-pane
        >
      </el-tabs>
    </el-card>
    <el-dialog v-model="previewOpen" title="草稿数据预览" width="90%"
      ><template v-if="dsl"
        ><ParameterForm v-model="parameters" :parameters="dsl.parameters" /><el-button type="primary" :loading="previewing" @click="fetchPreview"
          >刷新预览</el-button
        ><el-alert
          title="草稿样例最多 200 行。全量汇总与打印分页请保存发布后使用固定快照验证。"
          type="info"
          :closable="false"
          class="my-4" /><ReportPaper v-if="previewData?.plan" :plan="previewData.plan" /><TypedTable
          v-else-if="previewData"
          :columns="dsl.columns"
          :rows="previewData.rows" /></template
    ></el-dialog>
  </div>
</template>
<script setup lang="ts">
import { ref, reactive, computed, watch, onUnmounted } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { saveAs } from 'file-saver';
import { listDatasets, listCatalogs, reportDetail, createReport, saveReport, validateDraft, previewDraft, publishReport } from '@/api/report';
import type { DatasetView, DatasetField, ReportDsl, ReportDefinition, ReportCatalog, ReportPage, DraftPreview } from '@/types/report';
import {
  createDsl,
  addField,
  removeColumn,
  moveColumn,
  fitColumns,
  normalStyle,
  parameterInput,
  summaryFor,
  serializeDsl,
  DslHistory,
  RequestGate
} from '@/views/report/model';
import ParameterForm from '@/components/report/ParameterForm.vue';
import TypedTable from '@/components/report/TypedTable.vue';
import ReportPaper from '@/components/report/ReportPaper.vue';
defineOptions({ name: 'ReportDesigner' });
const route = useRoute();
const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const publishing = ref(false);
const checking = ref(false);
const previewing = ref(false);
const definition = ref<ReportDefinition>();
const dsl = ref<ReportDsl>();
const datasets = ref<DatasetView[]>([]);
const catalogs = ref<ReportCatalog[]>([]);
const catalogId = ref<string | null>(null);
const savedJson = ref('');
const tab = ref('canvas');
const jsonText = ref('');
const jsonBaseline = ref('');
const applying = ref(false);
let editorContext = 0;
const history = reactive(new DslHistory(50));
const canUndo = computed(() => history.canUndo);
const canRedo = computed(() => history.canRedo);
const parameters = ref<Record<string, string>>({});
const previewData = ref<DraftPreview>();
const previewOpen = ref(false);
const summaryField = ref('');
const loadGate = new RequestGate();
const previewGate = new RequestGate();
let dragIndex = -1;
type Band = 'headerRows' | 'footerRows';
type Selection = { type: 'column'; index: number } | { type: 'cell'; band: Band; row: number; cell: number };
const selection = ref<Selection>();
const bands: { key: Band; label: string }[] = [
  { key: 'headerRows', label: '固定页眉' },
  { key: 'footerRows', label: '末页固定区' }
];
const paperSettings: { key: keyof ReportPage; label: string }[] = [
  { key: 'width', label: '纸张宽度' },
  { key: 'height', label: '纸张高度' },
  { key: 'marginTop', label: '上边距' },
  { key: 'marginRight', label: '右边距' },
  { key: 'marginBottom', label: '下边距' },
  { key: 'marginLeft', label: '左边距' },
  { key: 'rowHeight', label: '明细行高' }
];
const selectedDataset = computed(() => datasets.value.find((d) => d.dataset.code === dsl.value?.datasetCode));
const selectedColumn = computed(() => (selection.value?.type === 'column' ? dsl.value?.columns[selection.value.index] : undefined));
const selectedBandRow = computed(() => (selection.value?.type === 'cell' ? dsl.value?.[selection.value.band][selection.value.row] : undefined));
const selectedCell = computed(() => (selection.value?.type === 'cell' ? selectedBandRow.value?.cells[selection.value.cell] : undefined));
const remainingBandRows = computed(() =>
  selection.value?.type === 'cell' ? (dsl.value?.[selection.value.band].length || 0) - selection.value.row : 1
);
const selectedStyle = computed(() => selectedColumn.value?.style || selectedCell.value?.style);
const jsonDirty = computed(() => jsonText.value !== jsonBaseline.value);
const dirty = computed(() => jsonDirty.value || (!!dsl.value && JSON.stringify(dsl.value) !== savedJson.value));
const summaryFields = computed(
  () => selectedDataset.value?.descriptor.fields.filter((f) => f.aggregatable && ['DECIMAL', 'INTEGER'].includes(f.type)) || []
);
async function load() {
  const id = String(route.query.id || '');
  if (id && definition.value?.id === id) return;
  const ticket = loadGate.begin();
  editorContext++;
  history.reset();
  saving.value = publishing.value = applying.value = checking.value = false;
  parameters.value = {};
  catalogId.value = null;
  jsonText.value = jsonBaseline.value = savedJson.value = '';
  tab.value = 'canvas';
  previewOpen.value = false;
  definition.value = undefined;
  dsl.value = undefined;
  selection.value = undefined;
  previewData.value = undefined;
  previewGate.invalidate();
  loading.value = true;
  try {
    const [a, b] = await Promise.all([listDatasets(), listCatalogs()]);
    if (!loadGate.current(ticket)) return;
    datasets.value = a.data;
    catalogs.value = b.data;
    if (id) {
      const response = await reportDetail(id);
      if (loadGate.current(ticket)) {
        definition.value = response.data.definition;
        dsl.value = response.data.dsl;
        catalogId.value = response.data.definition.catalogId;
        savedJson.value = serializeDsl(response.data.dsl);
        history.reset(response.data.dsl);
      }
    } else {
      const selected = datasets.value.find((d) => d.dataset.id === String(route.query.dataset || ''));
      if (selected) chooseDataset(selected.dataset.id);
      else savedJson.value = '';
    }
  } catch {
  } finally {
    if (loadGate.current(ticket)) loading.value = false;
  }
}
function chooseDataset(id: string) {
  const dataset = datasets.value.find((d) => d.dataset.id === id);
  if (!dataset) return;
  dsl.value = createDsl(dataset.descriptor, dataset.dataset.publishedVersion, 'REPORT_' + Date.now().toString(36).toUpperCase());
  selection.value = undefined;
  parameters.value = {};
  savedJson.value = '';
  history.reset(dsl.value);
}
function insertField(field: DatasetField) {
  if (!dsl.value) return;
  try {
    dsl.value = addField(dsl.value, field);
  } catch (error) {
    ElMessage.warning((error as Error).message);
  }
}
function dragField(event: DragEvent, name: string) {
  event.dataTransfer?.setData('report-field', name);
}
function dropField(event: DragEvent) {
  const name = event.dataTransfer?.getData('report-field');
  const field = selectedDataset.value?.descriptor.fields.find((f) => f.name === name);
  if (field) insertField(field);
}
function reorder(index: number) {
  if (dsl.value && dragIndex >= 0) {
    dsl.value = moveColumn(dsl.value, dragIndex, index);
    selection.value = { type: 'column', index };
    dragIndex = -1;
  }
}
async function insertFormula() {
  if (!dsl.value) return;
  try {
    const result = await ElMessageBox.prompt('计算列绑定名（字母开头）', '添加计算列', {
      inputValue: 'calc_' + (dsl.value.columns.length + 1),
      inputPattern: /^[A-Za-z_][A-Za-z0-9_]{0,63}$/,
      inputErrorMessage: '输入有效的绑定名'
    });
    dsl.value = addField(dsl.value, {
      name: result.value,
      label: '计算列',
      type: 'DECIMAL',
      permission: null,
      aggregationKeys: [],
      aggregatable: false
    });
    dsl.value.columns[dsl.value.columns.length - 1].expression = '0';
    selection.value = { type: 'column', index: dsl.value.columns.length - 1 };
  } catch {}
}
function deleteColumn() {
  if (!dsl.value || selection.value?.type !== 'column') return;
  dsl.value = removeColumn(dsl.value, selection.value.index);
  selection.value = undefined;
}
function addBand(band: Band) {
  if (!dsl.value) return;
  dsl.value[band].push({
    height: 32,
    cells: [{ column: 0, colSpan: dsl.value.columns.length, rowSpan: 1, text: '', field: null, style: normalStyle() }]
  });
  selection.value = { type: 'cell', band, row: dsl.value[band].length - 1, cell: 0 };
}
function mergeRow(band: Band, index: number) {
  if (!dsl.value) return;
  const row = dsl.value[band][index];
  row.cells = [{ column: 0, colSpan: dsl.value.columns.length, rowSpan: 1, text: row.cells[0]?.text || '', field: null, style: normalStyle() }];
  selection.value = { type: 'cell', band, row: index, cell: 0 };
}
function splitRow(band: Band, index: number) {
  if (!dsl.value) return;
  const row = dsl.value[band][index];
  row.cells = dsl.value.columns.map((_, c) => ({
    column: c,
    colSpan: 1,
    rowSpan: 1,
    text: c === 0 ? row.cells[0]?.text || '' : '',
    field: null,
    style: normalStyle()
  }));
  selection.value = { type: 'cell', band, row: index, cell: 0 };
}
function bindingLabel(field: string) {
  return '{{' + field + '}}';
}
function isCellSelected(band: Band, row: number, cell: number) {
  return selection.value?.type === 'cell' && selection.value.band === band && selection.value.row === row && selection.value.cell === cell;
}
function bandGrid(band: Band) {
  return {
    gridTemplateColumns: dsl.value!.columns.map((c) => c.width + 'fr').join(' '),
    gridTemplateRows: dsl.value![band].map((r) => Math.max(32, r.height) + 'px').join(' ')
  };
}
function addSummary() {
  const field = summaryFields.value.find((f) => f.name === summaryField.value);
  if (dsl.value && field) dsl.value.summaries.push(summaryFor(field));
  else ElMessage.warning('请选择允许汇总的数值字段');
}
async function validate() {
  if (!dsl.value || checking.value || saving.value || publishing.value || applying.value) return;
  const context = editorContext;
  const sent = serializeDsl(dsl.value);
  checking.value = true;
  try {
    await validateDraft(definition.value?.id || null, JSON.parse(sent));
    if (context === editorContext && dsl.value && serializeDsl(dsl.value) === sent) ElMessage.success('定义校验通过');
  } catch {
  } finally {
    if (context === editorContext) checking.value = false;
  }
}
async function save(): Promise<boolean> {
  if (publishing.value) return false;
  return persistDraft();
}
async function persistDraft(): Promise<boolean> {
  if (!dsl.value || saving.value || applying.value) return false;
  if (jsonDirty.value) {
    ElMessage.warning('请先校验并应用定义文件中的更改');
    return false;
  }
  const context = editorContext;
  const target = definition.value;
  const catalog = catalogId.value;
  saving.value = true;
  try {
    const sent = serializeDsl(dsl.value);
    const value = JSON.parse(sent) as ReportDsl;
    await validateDraft(target?.id || null, value);
    if (context !== editorContext) return false;
    const result = target ? await saveReport(target.id, target.revision, value) : await createReport(value, catalog);
    if (context !== editorContext) return false;
    definition.value = result.data;
    savedJson.value = sent;
    if (!target) {
      history.reset(value);
      if (dsl.value) history.record(dsl.value);
    }
    if (String(route.query.id || '') !== result.data.id) await router.replace({ path: '/report/designer', query: { id: result.data.id } });
    if (context !== editorContext) return false;
    ElMessage.success('草稿已保存');
    return true;
  } catch (error) {
    if (context === editorContext && (error as { response?: { status: number } })?.response?.status === 409)
      ElMessage.warning('草稿已被其他人修改，请重新读取后再编辑');
    return false;
  } finally {
    if (context === editorContext) saving.value = false;
  }
}
async function publish() {
  if (publishing.value || saving.value || applying.value) return;
  const context = editorContext;
  publishing.value = true;
  try {
    if (!(await persistDraft()) || context !== editorContext || !definition.value) return;
    if (dirty.value) {
      ElMessage.warning('保存期间设计已变更，请再次保存后发布');
      return;
    }
    const target = definition.value;
    await publishReport(target.id, target.revision);
    if (context !== editorContext) return;
    const response = await reportDetail(target.id);
    if (context !== editorContext) return;
    definition.value = response.data.definition;
    ElMessage.success('报表已发布');
  } catch {
  } finally {
    if (context === editorContext) publishing.value = false;
  }
}
function preview() {
  previewOpen.value = true;
  void fetchPreview();
}
async function fetchPreview() {
  if (!dsl.value) return;
  const ticket = previewGate.begin();
  previewing.value = true;
  try {
    const result = await previewDraft(definition.value?.id || null, dsl.value, parameterInput(parameters.value));
    if (previewGate.current(ticket)) previewData.value = result.data;
  } catch {
  } finally {
    if (previewGate.current(ticket)) previewing.value = false;
  }
}
async function applyJson() {
  if (!dsl.value || saving.value || publishing.value || applying.value) return;
  const context = editorContext;
  const source = serializeDsl(dsl.value);
  const text = jsonText.value;
  applying.value = true;
  try {
    const candidate = JSON.parse(text) as ReportDsl;
    serializeDsl(candidate);
    await validateDraft(definition.value?.id || null, candidate);
    if (context !== editorContext) return;
    if (!dsl.value || serializeDsl(dsl.value) !== source || jsonText.value !== text) {
      ElMessage.warning('设计或定义文件已变更，请重新校验并应用');
      return;
    }
    dsl.value = candidate;
    jsonText.value = jsonBaseline.value = JSON.stringify(candidate, null, 2);
    selection.value = undefined;
    ElMessage.success('定义已应用');
  } catch {
    if (context === editorContext) ElMessage.warning('定义未通过校验，请检查内容');
  } finally {
    if (context === editorContext) applying.value = false;
  }
}
function undo() {
  restoreHistory('undo');
}
function redo() {
  restoreHistory('redo');
}
function restoreHistory(action: 'undo' | 'redo') {
  if (!dsl.value || saving.value || publishing.value || applying.value || jsonDirty.value) return;
  history.record(dsl.value);
  const restored = history[action]();
  if (!restored) return;
  dsl.value = restored;
  selection.value = undefined;
  if (tab.value === 'json') jsonText.value = jsonBaseline.value = JSON.stringify(restored, null, 2);
}
function exportDsl() {
  if (dsl.value) saveAs(new Blob([serializeDsl(dsl.value)], { type: 'application/json' }), dsl.value.code + '.report.json');
}
async function leave() {
  if (saving.value || publishing.value || applying.value) {
    ElMessage.warning('正在保存、发布或校验定义，请完成后再离开');
    return false;
  }
  if (!dirty.value) return true;
  try {
    await ElMessageBox.confirm('有未保存的设计，离开后将丢弃这些更改。', '离开报表设计', { type: 'warning' });
    return true;
  } catch {
    return false;
  }
}
watch(() => [route.query.id, route.query.dataset], load, { immediate: true, flush: 'sync' });
watch(
  [dsl, parameters],
  () => {
    previewGate.invalidate();
    previewData.value = undefined;
    previewing.value = false;
  },
  { deep: true, flush: 'sync' }
);
watch(
  dsl,
  (value) => {
    if (value) history.record(value);
  },
  { deep: true, flush: 'post' }
);
watch(tab, (value) => {
  if (value === 'json' && dsl.value && !jsonDirty.value) jsonText.value = jsonBaseline.value = JSON.stringify(dsl.value, null, 2);
});
onBeforeRouteLeave(leave);
onBeforeRouteUpdate(async (to) => (String(to.query.id || '') === definition.value?.id ? true : await leave()));
onUnmounted(() => {
  editorContext++;
  loadGate.invalidate();
  previewGate.invalidate();
});
</script>
<style scoped>
.designer-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.designer-config {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.designer-grid {
  display: grid;
  grid-template-columns: 190px minmax(300px, 1fr) 245px;
  gap: 16px;
  min-height: 550px;
}
.field-palette,
.property-panel {
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}
.field-palette h3,
.property-panel h3 {
  margin: 0 0 14px;
  font-size: 15px;
}
.field-item {
  display: flex;
  justify-content: space-between;
  padding: 9px;
  margin-bottom: 6px;
  background: #fff;
  border: 1px solid #dce4ed;
  border-radius: 4px;
  cursor: grab;
}
.field-item small {
  color: #94a3b8;
  font-size: 10px;
}
.canvas-area {
  overflow: auto;
  padding: 20px;
  background: #eef2f6;
  border: 1px solid #dbe3eb;
  border-radius: 6px;
}
.canvas-title {
  padding: 18px;
  text-align: center;
  font-size: 20px;
  font-weight: 600;
  background: #fff;
}
.band-section {
  margin-top: 12px;
}
.band-caption,
.detail-caption {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
  color: #64748b;
}
.band-grid {
  display: grid;
  background: #fff;
  position: relative;
}
.band-cell {
  padding: 8px;
  border: 1px solid #cbd5e1;
  overflow: hidden;
  cursor: pointer;
}
.band-row-tools {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 4px;
}
.detail-caption {
  margin-top: 20px;
}
.column-strip {
  display: flex;
  overflow: auto;
  background: #fff;
}
.column-tile {
  border: 1px solid #cbd5e1;
  padding: 10px;
  cursor: grab;
  max-width: 300px;
  overflow: hidden;
}
.example-cell {
  margin-top: 12px;
  border-top: 1px solid #e2e8f0;
  padding-top: 10px;
  font-size: 12px;
  color: #64748b;
}
.selected {
  outline: 2px solid #409eff;
  outline-offset: -2px;
}
.dsl-json :deep(textarea) {
  font-family: Consolas, monospace;
}
@media (max-width: 1200px) {
  .designer-grid {
    grid-template-columns: 170px minmax(250px, 1fr);
  }
  .property-panel {
    grid-column: 1 / -1;
  }
}
</style>

<template>
  <ReportPage
    v-loading="loading"
    :title="definition ? '设计 · ' + (dsl?.title || definition.name) : '新建报表'"
    description="选择业务字段，编排版式，校验后保存或发布。"
    icon="i-ep-edit"
    class="report-designer"
    :class="{ 'with-preview': previewOpen }"
    tabindex="0"
    @keydown="editorKeys"
  >
    <template #badge
      ><el-tag v-if="dirty" type="warning" effect="plain">未保存</el-tag
      ><el-tag v-else-if="definition" type="success" effect="plain">已保存 · 修订 {{ definition.revision }}</el-tag></template
    >
    <template #actions>
      <el-button v-if="definition" @click="exportPackage">导出模板包</el-button><el-button @click="router.push('/report/center')">返回</el-button
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
        ><el-button v-hasPermi="['report:definition:publish']" type="success" :disabled="saving || applying" :loading="publishing" @click="publish"
          >保存并发布</el-button
        ></span
      ></template
    >
    <el-card class="designer-main" shadow="never">
      <div class="report-section-heading">
        <div>
          <h2>报表配置</h2>
          <p class="report-section-description">先选择数据集，再设置编码、标题与报表类型。</p>
        </div>
        <span v-if="dsl" class="report-secondary">{{ dsl.columns.length }} 列 · {{ dsl.parameters.length }} 个参数</span>
      </div>
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
      <DatasetContractBar v-if="dsl" v-model="dsl" :datasets="datasets" :busy="saving || publishing || applying" @contract="boundVersion = $event" />
      <DesignerBatchTools v-if="dsl" v-model="dsl" :disabled="saving || publishing || applying" />
      <div v-if="structuralIssues.length" class="designer-issues">
        <strong>设计问题（点击定位列）</strong
        ><el-button
          v-for="issue in structuralIssues"
          :key="issue.index + issue.message"
          link
          type="danger"
          @click="
            selection = { type: 'column', index: issue.index };
            tab = 'canvas';
          "
          >{{ issue.message }}</el-button
        >
      </div>
      <el-tabs v-if="dsl" v-model="tab" v-loading="saving || publishing || applying">
        <el-tab-pane label="设计画布" name="canvas"
          ><div v-loading="saving" class="designer-grid">
            <aside class="field-palette">
              <div class="palette-heading">
                <h3>可用字段</h3>
                <span class="report-secondary">{{ visibleFields.length }}</span>
              </div>
              <el-input
                v-model="fieldSearch"
                clearable
                size="small"
                prefix-icon="Search"
                placeholder="搜索字段或类型"
                aria-label="搜索可用字段"
                class="palette-search"
              />
              <p class="report-secondary">点击添加，或拖入明细区。</p>
              <div class="field-list">
                <button
                  v-for="field in visibleFields"
                  :key="field.name"
                  type="button"
                  class="field-item"
                  draggable="true"
                  :disabled="saving || publishing || applying"
                  :aria-label="'添加字段：' + field.label"
                  @dragstart="dragField($event, field.name)"
                  @click="insertField(field)"
                >
                  <span class="field-item__name"
                    >{{ field.label }}<small>{{ field.name }}</small></span
                  ><span class="field-item__type">{{ field.type }}</span>
                </button>
              </div>
              <el-empty v-if="!visibleFields.length" :image-size="50" description="没有匹配字段"
                ><el-button v-if="fieldSearch" link type="primary" @click="fieldSearch = ''">清空搜索</el-button></el-empty
              >
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
                      role="button"
                      tabindex="0"
                      @keydown.enter.prevent="selection = { type: 'cell', band: band.key, row: r, cell: c }"
                      @keydown.space.prevent="selection = { type: 'cell', band: band.key, row: r, cell: c }"
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
                  role="button"
                  tabindex="0"
                  @keydown.enter.prevent="selection = { type: 'column', index }"
                  @keydown.space.prevent="selection = { type: 'column', index }"
                  :class="{ selected: selection?.type === 'column' && selection.index === index }"
                  :style="{ minWidth: Math.max(80, column.width) + 'px' }"
                  draggable="true"
                  @dragstart="dragIndex = index"
                  @dragover.prevent
                  @drop.stop.prevent="reorder(index)"
                  @click="selection = { type: 'column', index }"
                >
                  <strong>{{ column.label }}</strong>
                  <div class="text-xs report-secondary">{{ column.field }}</div>
                  <div class="example-cell">{{ column.expression ? '= ' + column.expression : '明细值' }}</div>
                </div>
              </div>
              <p class="report-secondary text-xs">拖动列调整顺序；点选列或固定区域单元格，在右侧设置样式和绑定。</p>
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
                  <p class="text-xs report-secondary">支持十进制运算、IF、ROUND、COALESCE、SUM 和 COUNT。</p>
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
        <el-tab-pane v-if="dsl.kind === 'DOCUMENT'" label="新版打印" name="print"
          ><PrintSettings v-model="dsl" :fields="selectedDataset?.descriptor.fields || []" :disabled="saving || publishing || applying"
        /></el-tab-pane>
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
          <p class="report-secondary">金额、数量的币种、物料或单位分区将由数据契约强制检查。</p></el-tab-pane
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
    <el-card v-if="previewOpen" class="designer-preview" shadow="never">
      <template #header
        ><div class="preview-heading"><strong>草稿数据预览</strong><el-button @click="previewOpen = false">关闭预览</el-button></div></template
      >
      <template v-if="dsl"
        ><ParameterForm
          :dataset-version-id="dsl?.datasetVersionId"
          v-model="parameters"
          :parameters="effectiveReportParameters(dsl.parameters, selectedDataset?.descriptor.parameters)" /><el-button
          type="primary"
          :loading="previewing"
          @click="fetchPreview"
          >刷新预览</el-button
        ><el-alert
          title="草稿样例最多 200 行。全量汇总与打印分页请保存发布后使用固定快照验证。"
          type="info"
          :closable="false"
          class="my-4" /><ReportPaper v-if="previewData?.plan" :plan="previewData.plan" /><TypedTable
          v-else-if="previewData"
          :columns="dsl.columns"
          :rows="previewData.rows" /></template
    ></el-card>
  </ReportPage>
</template>
<script setup lang="ts">
/** 原生报表页面组件，沿用权限、精确值和固定契约行为。 */

import { ref, reactive, computed, watch, onUnmounted } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { saveAs } from 'file-saver';
import { listDatasets, listCatalogs, reportDetail, createReport, saveReport, validateDraft, previewDraft, publishReport } from '@/api/report';
import type {
  DatasetView,
  DatasetField,
  ReportDsl,
  ReportDefinition,
  ReportCatalog,
  ReportPage as ReportPageSettings,
  DraftPreview
} from '@/types/report';
import {
  createDsl,
  addField,
  removeColumn,
  moveColumn,
  fitColumns,
  normalStyle,
  parameterInput,
  requiredParameterMessage,
  effectiveReportParameters,
  summaryFor,
  serializeDsl,
  DslHistory,
  RequestGate
} from '@/views/report/model';
import ReportPage from '@/components/report/ReportPage.vue';
import { filterFields } from '@/views/report/presentation';
import ParameterForm from '@/components/report/ParameterForm.vue';
import TypedTable from '@/components/report/TypedTable.vue';
import ReportPaper from '@/components/report/ReportPaper.vue';
import DatasetContractBar from '@/components/report/DatasetContractBar.vue';
import DesignerBatchTools from '@/components/report/DesignerBatchTools.vue';
import PrintSettings from '@/components/report/PrintSettings.vue';
import { editorIssues, boundDataset } from './editorCommands';
import { exportTemplatePackage } from '@/api/report';
import type { DatasetVersionView } from '@/types/report';
defineOptions({ name: 'ReportDesigner' });
/**
 * 保存当前页面或测试的route状态。
 */
const route = useRoute();
/**
 * 保存当前页面或测试的router状态。
 */
const router = useRouter();
/**
 * 保存当前页面或测试的loading状态。
 */
const loading = ref(false);
/**
 * 保存当前页面或测试的saving状态。
 */
const saving = ref(false);
/**
 * 保存当前页面或测试的publishing状态。
 */
const publishing = ref(false);
/**
 * 保存当前页面或测试的checking状态。
 */
const checking = ref(false);
/**
 * 保存当前页面或测试的previewing状态。
 */
const previewing = ref(false);
/**
 * 保存当前页面或测试的definition状态。
 */
const definition = ref<ReportDefinition>();
/**
 * 保存当前页面或测试的dsl状态。
 */
const dsl = ref<ReportDsl>();
/**
 * 保存当前页面或测试的datasets状态。
 */
const datasets = ref<DatasetView[]>([]);
/**
 * 保存当前页面或测试的boundVersion状态。
 */
const boundVersion = ref<DatasetVersionView>();
/**
 * 保存当前页面或测试的catalogs状态。
 */
const catalogs = ref<ReportCatalog[]>([]);
/**
 * 保存当前页面或测试的catalogId状态。
 */
const catalogId = ref<string | null>(null);
/**
 * 保存当前页面或测试的savedJson状态。
 */
const savedJson = ref('');
/**
 * 保存当前页面或测试的tab状态。
 */
const tab = ref('canvas');
/**
 * 保存当前页面或测试的jsonText状态。
 */
const jsonText = ref('');
/**
 * 保存当前页面或测试的jsonBaseline状态。
 */
const jsonBaseline = ref('');
/**
 * 保存当前页面或测试的applying状态。
 */
const applying = ref(false);
/**
 * 保存当前页面或测试的editorContext状态。
 */
let editorContext = 0;
/**
 * 保存当前页面或测试的history状态。
 */
const history = reactive(new DslHistory(50));
/**
 * 保存当前页面或测试的canUndo状态。
 */
const canUndo = computed(() => history.canUndo);
/**
 * 保存当前页面或测试的canRedo状态。
 */
const canRedo = computed(() => history.canRedo);
/**
 * 保存当前页面或测试的parameters状态。
 */
const parameters = ref<Record<string, string>>({});
/**
 * 保存当前页面或测试的previewData状态。
 */
const previewData = ref<DraftPreview>();
/**
 * 保存当前页面或测试的previewOpen状态。
 */
const previewOpen = ref(false);
/**
 * 保存当前页面或测试的summaryField状态。
 */
const summaryField = ref('');
/**
 * 保存当前页面或测试的loadGate状态。
 */
const loadGate = new RequestGate();
/**
 * 保存当前页面或测试的previewGate状态。
 */
const previewGate = new RequestGate();
/**
 * 保存当前页面或测试的dragIndex状态。
 */
let dragIndex = -1;
/** 声明Band结构，保留服务端精确值和固定版本约束。 */
type Band = 'headerRows' | 'footerRows';
/** 声明Selection结构，保留服务端精确值和固定版本约束。 */
type Selection =
  | {
      /** 保存当前 `type` 值。 */
      type: 'column';
      /** 保存当前下标。 */
      index: number;
    }
  | {
      /** 保存当前 `type` 值。 */
      type: 'cell';
      /** 保存当前 `band` 值。 */
      band: Band;
      /** 保存当前精确业务行。 */
      row: number;
      /** 保存当前 `cell` 值。 */
      cell: number;
    };
/**
 * 保存当前页面或测试的selection状态。
 */
const selection = ref<Selection>();
/**
 * 保存当前页面或测试的bands状态。
 */
const bands: {
  /** 保存当前 `key` 值。 */
  key: Band;
  /** 保存展示标题。 */
  label: string;
}[] = [
  { key: 'headerRows', label: '固定页眉' },
  { key: 'footerRows', label: '末页固定区' }
];
/**
 * 保存当前页面或测试的paperSettings状态。
 */
const paperSettings: {
  /** 保存当前 `key` 值。 */
  key: keyof ReportPageSettings;
  /** 保存展示标题。 */
  label: string;
}[] = [
  { key: 'width', label: '纸张宽度' },
  { key: 'height', label: '纸张高度' },
  { key: 'marginTop', label: '上边距' },
  { key: 'marginRight', label: '右边距' },
  { key: 'marginBottom', label: '下边距' },
  { key: 'marginLeft', label: '左边距' },
  { key: 'rowHeight', label: '明细行高' }
];
/**
 * 保存当前页面或测试的selectedDataset状态。
 */
const selectedDataset = computed(() => boundDataset(datasets.value, dsl.value, boundVersion.value));
/**
 * 保存当前页面或测试的structuralIssues状态。
 */
/** 字段搜索仅过滤契约描述，不修改已选列或数据集绑定。 */
const fieldSearch = ref('');
/** 在可用字段区展示匹配名称、绑定名或类型的字段。 */
const visibleFields = computed(() => filterFields(selectedDataset.value?.descriptor.fields || [], fieldSearch.value));
const structuralIssues = computed(() => (dsl.value ? editorIssues(dsl.value, selectedDataset.value?.descriptor) : []));
/**
 * 保存当前页面或测试的selectedColumn状态。
 */
const selectedColumn = computed(() => (selection.value?.type === 'column' ? dsl.value?.columns[selection.value.index] : undefined));
/**
 * 保存当前页面或测试的selectedBandRow状态。
 */
const selectedBandRow = computed(() => (selection.value?.type === 'cell' ? dsl.value?.[selection.value.band][selection.value.row] : undefined));
/**
 * 保存当前页面或测试的selectedCell状态。
 */
const selectedCell = computed(() => (selection.value?.type === 'cell' ? selectedBandRow.value?.cells[selection.value.cell] : undefined));
/**
 * 保存当前页面或测试的remainingBandRows状态。
 */
const remainingBandRows = computed(() =>
  selection.value?.type === 'cell' ? (dsl.value?.[selection.value.band].length || 0) - selection.value.row : 1
);
/**
 * 保存当前页面或测试的selectedStyle状态。
 */
const selectedStyle = computed(() => selectedColumn.value?.style || selectedCell.value?.style);
/**
 * 保存当前页面或测试的jsonDirty状态。
 */
const jsonDirty = computed(() => jsonText.value !== jsonBaseline.value);
/**
 * 保存当前页面或测试的dirty状态。
 */
const dirty = computed(() => jsonDirty.value || (!!dsl.value && JSON.stringify(dsl.value) !== savedJson.value));
/**
 * 保存当前页面或测试的summaryFields状态。
 */
const summaryFields = computed(
  () => selectedDataset.value?.descriptor.fields.filter((f) => f.aggregatable && ['DECIMAL', 'INTEGER'].includes(f.type)) || []
);
/** 处理当前报表的 `load` 操作。 */
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
  boundVersion.value = undefined;
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
/** 处理当前报表的 `chooseDataset` 操作。 */
function chooseDataset(id: string) {
  const dataset = datasets.value.find((d) => d.dataset.id === id);
  if (!dataset) return;
  dsl.value = createDsl(dataset.descriptor, dataset.dataset.publishedVersion, 'REPORT_' + Date.now().toString(36).toUpperCase());
  selection.value = undefined;
  parameters.value = {};
  savedJson.value = '';
  history.reset(dsl.value);
}
/** 处理当前报表的 `insertField` 操作。 */
function insertField(field: DatasetField) {
  if (!dsl.value) return;
  try {
    dsl.value = addField(dsl.value, field);
  } catch (error) {
    ElMessage.warning((error as Error).message);
  }
}
/** 处理当前报表的 `dragField` 操作。 */
function dragField(event: DragEvent, name: string) {
  event.dataTransfer?.setData('report-field', name);
}
/** 处理当前报表的 `dropField` 操作。 */
function dropField(event: DragEvent) {
  const name = event.dataTransfer?.getData('report-field');
  const field = selectedDataset.value?.descriptor.fields.find((f) => f.name === name);
  if (field) insertField(field);
}
/** 处理当前报表的 `reorder` 操作。 */
function reorder(index: number) {
  if (dsl.value && dragIndex >= 0) {
    dsl.value = moveColumn(dsl.value, dragIndex, index);
    selection.value = { type: 'column', index };
    dragIndex = -1;
  }
}
/** 处理当前报表的 `insertFormula` 操作。 */
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
/** 处理当前报表的 `deleteColumn` 操作。 */
function deleteColumn() {
  if (!dsl.value || selection.value?.type !== 'column') return;
  dsl.value = removeColumn(dsl.value, selection.value.index);
  selection.value = undefined;
}
/** 处理当前报表的 `addBand` 操作。 */
function addBand(band: Band) {
  if (!dsl.value) return;
  dsl.value[band].push({
    height: 32,
    cells: [{ column: 0, colSpan: dsl.value.columns.length, rowSpan: 1, text: '', field: null, style: normalStyle() }]
  });
  selection.value = { type: 'cell', band, row: dsl.value[band].length - 1, cell: 0 };
}
/** 处理当前报表的 `mergeRow` 操作。 */
function mergeRow(band: Band, index: number) {
  if (!dsl.value) return;
  const row = dsl.value[band][index];
  row.cells = [{ column: 0, colSpan: dsl.value.columns.length, rowSpan: 1, text: row.cells[0]?.text || '', field: null, style: normalStyle() }];
  selection.value = { type: 'cell', band, row: index, cell: 0 };
}
/** 处理当前报表的 `splitRow` 操作。 */
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
/** 处理当前报表的 `bindingLabel` 操作。 */
function bindingLabel(field: string) {
  return '{{' + field + '}}';
}
/** 处理当前报表的 `isCellSelected` 操作。 */
function isCellSelected(band: Band, row: number, cell: number) {
  return selection.value?.type === 'cell' && selection.value.band === band && selection.value.row === row && selection.value.cell === cell;
}
/** 处理当前报表的 `bandGrid` 操作。 */
function bandGrid(band: Band) {
  return {
    gridTemplateColumns: dsl.value!.columns.map((c) => c.width + 'fr').join(' '),
    gridTemplateRows: dsl.value![band].map((r) => Math.max(32, r.height) + 'px').join(' ')
  };
}
/** 处理当前报表的 `addSummary` 操作。 */
function addSummary() {
  const field = summaryFields.value.find((f) => f.name === summaryField.value);
  if (dsl.value && field) dsl.value.summaries.push(summaryFor(field));
  else ElMessage.warning('请选择允许汇总的数值字段');
}
/** 检查定义或查询是否满足受限契约。 */
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
/** 处理当前报表的 `save` 操作。 */
async function save(): Promise<boolean> {
  if (publishing.value) return false;
  return persistDraft();
}
/** 处理当前报表的 `persistDraft` 操作。 */
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
    if (
      context === editorContext &&
      (
        error as {
          /** 保存当前 `response` 值。 */
          response?: {
            /** 保存当前 `status` 值。 */
            status: number;
          };
        }
      )?.response?.status === 409
    )
      ElMessage.warning('草稿已被其他人修改，请重新读取后再编辑');
    return false;
  } finally {
    if (context === editorContext) saving.value = false;
  }
}
/** 在修订与授权检查后创建不可变发布版本。 */
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
/** 处理当前报表的 `preview` 操作。 */
function preview() {
  previewOpen.value = true;
  void fetchPreview();
}
/** 处理当前报表的 `fetchPreview` 操作。 */
async function fetchPreview() {
  if (!dsl.value) return;
  const invalid = requiredParameterMessage(dsl.value.parameters, parameters.value, selectedDataset.value?.descriptor.parameters);
  if (invalid) {
    previewGate.invalidate();
    previewData.value = undefined;
    previewing.value = false;
    ElMessage.warning(invalid);
    return;
  }
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
/** 处理当前报表的 `applyJson` 操作。 */
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
/** 处理当前报表的 `undo` 操作。 */
function undo() {
  restoreHistory('undo');
}
/** 处理当前报表的 `redo` 操作。 */
function redo() {
  restoreHistory('redo');
}
/** 处理当前报表的 `restoreHistory` 操作。 */
function restoreHistory(action: 'undo' | 'redo') {
  if (!dsl.value || saving.value || publishing.value || applying.value || jsonDirty.value) return;
  history.record(dsl.value);
  const restored = history[action]();
  if (!restored) return;
  dsl.value = restored;
  selection.value = undefined;
  if (tab.value === 'json') jsonText.value = jsonBaseline.value = JSON.stringify(restored, null, 2);
}
/** 处理当前报表的 `exportPackage` 操作。 */
async function exportPackage() {
  if (!definition.value || !dsl.value) return;
  if (dirty.value) {
    ElMessage.warning('请先保存草稿后导出模板包');
    return;
  }
  try {
    const context = editorContext;
    const id = definition.value.id;
    const code = dsl.value.code;
    const response = await exportTemplatePackage(id);
    if (context !== editorContext || definition.value?.id !== id) return;
    saveAs(new Blob([JSON.stringify(response.data)], { type: 'application/json' }), code + '.reportpkg');
  } catch {}
}
/** 处理当前报表的 `exportDsl` 操作。 */
function exportDsl() {
  if (dsl.value) saveAs(new Blob([serializeDsl(dsl.value)], { type: 'application/json' }), dsl.value.code + '.report.json');
}
/** 处理当前报表的 `editorKeys` 操作。 */
function editorKeys(event: KeyboardEvent) {
  if (!(event.ctrlKey || event.metaKey)) return;
  if (event.key.toLowerCase() === 's') {
    event.preventDefault();
    if (!saving.value && !publishing.value) void save();
    return;
  }
  if ((event.target as HTMLElement)?.closest('input,textarea,[contenteditable=true]')) return;
  if (event.key.toLowerCase() === 'z') {
    event.preventDefault();
    event.shiftKey ? redo() : undo();
  }
  if (event.key.toLowerCase() === 'y') {
    event.preventDefault();
    redo();
  }
}
/** 处理当前报表的 `leave` 操作。 */
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
.report-designer.with-preview :deep(.report-page__content) {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
  align-items: start;
  gap: 16px;
}
.with-preview .designer-grid {
  grid-template-columns: 180px minmax(0, 1fr);
}
.with-preview .property-panel {
  grid-column: 1 / -1;
}
.designer-preview {
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 100px);
  overflow: auto;
}
.preview-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
@media (max-width: 1100px) {
  .report-designer.with-preview :deep(.report-page__content) {
    grid-template-columns: minmax(0, 1fr);
  }
  .designer-preview {
    position: static;
    max-height: none;
  }
}

.designer-main {
  min-width: 0;
}
.designer-config {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.designer-grid {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr) 245px;
  gap: 16px;
  min-height: 550px;
}
.field-palette,
.property-panel {
  padding: 14px;
  background: var(--report-muted);
  border: 1px solid var(--report-line);
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
  background: var(--report-surface);
  border: 1px solid var(--report-line);
  border-radius: 4px;
  cursor: grab;
}
.field-item small {
  color: var(--el-text-color-placeholder);
  font-size: 10px;
}
.canvas-area {
  overflow: auto;
  padding: 20px;
  background: var(--el-fill-color);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}
.canvas-title {
  padding: 18px;
  text-align: center;
  font-size: 20px;
  font-weight: 600;
  background: var(--report-surface);
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
  color: var(--el-text-color-secondary);
}
.band-grid {
  display: grid;
  background: var(--report-surface);
  position: relative;
}
.band-cell {
  padding: 8px;
  border: 1px solid var(--el-border-color);
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
  background: var(--report-surface);
}
.column-tile {
  border: 1px solid var(--el-border-color);
  padding: 10px;
  cursor: grab;
  max-width: 300px;
  overflow: hidden;
}
.example-cell {
  margin-top: 12px;
  border-top: 1px solid var(--report-line);
  padding-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.selected {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}
.dsl-json :deep(textarea) {
  font-family: Consolas, monospace;
}
@media (max-width: 1200px) {
  .designer-grid {
    grid-template-columns: 180px minmax(0, 1fr);
  }
  .property-panel {
    grid-column: 1 / -1;
  }
}

.palette-heading {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}
.palette-search {
  margin-bottom: 4px;
}
.field-list {
  max-height: 440px;
  overflow: auto;
  margin: 12px -4px;
  padding: 4px;
}
.field-item {
  width: 100%;
  text-align: left;
  align-items: center;
  gap: 6px;
  font: inherit;
  color: var(--el-text-color-regular);
}
.field-item:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}
.field-item:disabled {
  cursor: default;
  opacity: 0.6;
}
.field-item__name {
  min-width: 0;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.field-item__name small {
  display: block;
  margin-top: 4px;
  font-family: Consolas, monospace;
}
.field-item__type {
  font-size: 9px;
  color: var(--el-text-color-secondary);
  background: var(--report-muted);
  padding: 3px 4px;
  border-radius: 4px;
}
.designer-issues {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  padding: 14px;
  border-radius: 8px;
  margin: 14px 0;
}
.designer-config > .el-input,
.designer-config > .el-select {
  flex: 1 1 180px;
  min-width: 0;
}
.property-panel,
.field-palette,
.canvas-area {
  min-width: 0;
}
@media (max-width: 768px) {
  .designer-grid,
  .with-preview .designer-grid {
    grid-template-columns: minmax(0, 1fr);
    min-height: auto;
  }
  .field-list {
    max-height: 200px;
  }
  .canvas-area {
    padding: 12px;
    min-height: 320px;
  }
  .property-panel {
    grid-column: auto;
  }
}
</style>

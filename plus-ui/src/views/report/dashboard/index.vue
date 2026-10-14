<template>
  <div class="p-4">
    <el-card
      ><template #header
        ><div class="flex justify-between">
          <strong>个人仪表盘</strong><el-button v-hasPermi="['report:dashboard:edit']" @click="newDashboard">新建仪表盘</el-button>
        </div></template
      >
      <div class="flex gap-3 flex-wrap">
        <el-button v-for="row in dashboards" :key="row.id" :type="selected === row.id ? 'primary' : 'default'" @click="open(row.id)"
          >{{ favorites.includes(row.id) ? '★ ' : '' }}{{ row.title }}</el-button
        ><el-button v-if="selected" @click="favorite">{{ favorites.includes(selected) ? '取消收藏' : '收藏' }}</el-button
        ><el-button v-if="selected" v-hasPermi="['report:dashboard:edit']" @click="edit(selected)">编辑</el-button>
      </div>
      <el-button v-if="selected" v-hasPermi="['report:acl:manage']" class="mt-3" @click="aclId = selected">资源授权</el-button
      ><template v-if="published && !editing"
        ><ParameterForm v-model="parameters" :parameters="published.parameters" class="mt-4" />
        <div class="flex gap-3 mt-3">
          <el-button type="primary" :loading="rendering" @click="refresh">刷新组件</el-button
          ><el-switch v-model="paused" active-text="暂停自动刷新" /><span>刷新间隔{{ published.refreshSeconds }}秒；页面隐藏时暂停</span
          ><el-button v-if="selections.length" @click="clearSelections">清除联动筛选</el-button>
        </div>
        <el-alert v-if="error" :title="error" type="error" :closable="false" class="mt-3" />
        <div class="dashboard-grid mt-4">
          <DashboardWidget
            v-for="view in rendered"
            :key="view.id"
            :view="view"
            :style="widgetLayout(view)"
            @select="(value) => link(view.id, value)"
          /></div
      ></template>
      <el-form v-if="editing && model" :disabled="busy" label-width="110px" class="mt-4"
        ><el-form-item label="编码与标题"
          ><el-input v-model="code" :disabled="!!resource" placeholder="仪表盘编码" /><el-input
            v-model="model.title"
            class="ml-3"
            placeholder="标题" /></el-form-item
        ><el-form-item label="刷新间隔"><el-input-number v-model="model.refreshSeconds" :min="30" :max="3600" /></el-form-item>
        <el-form-item label="参数定义"
          ><el-button @click="model.parameters.push({ name: 'parameter' + model.parameters.length, label: '参数', type: 'STRING', required: false })"
            >添加参数</el-button
          ></el-form-item
        >
        <div v-for="(parameter, i) in model.parameters" :key="i" class="flex gap-2 mb-3">
          <el-input v-model="parameter.name" placeholder="名称" /><el-input v-model="parameter.label" placeholder="标题" /><el-select
            v-model="parameter.type"
            ><el-option v-for="type in valueTypes" :key="type" :value="type" :label="type" /></el-select
          ><el-checkbox v-model="parameter.required">必填</el-checkbox><el-button @click="model.parameters.splice(i, 1)">删除</el-button>
        </div>
        <el-form-item label="组件"><el-button :disabled="model.widgets.length >= 12" @click="addWidget">添加组件</el-button></el-form-item
        ><el-card v-for="(widget, index) in model.widgets" :key="widget.id" class="mb-3"
          ><div class="flex gap-2 mb-3">
            <el-input v-model="widget.title" placeholder="组件标题" /><el-select v-model="widget.type"
              ><el-option v-for="type in widgetTypes" :key="type" :value="type" :label="type" /></el-select
            ><el-select :model-value="widget.reportVersionId" placeholder="固定报告版本" @change="(value) => chooseReport(widget, String(value))"
              ><el-option
                v-for="report in reports"
                :key="report.id"
                :value="report.publishedVersion!"
                :label="report.name + ' / ' + report.publishedVersion" /><el-option
                v-if="widget.reportVersionId && !reports.some((report) => report.publishedVersion === widget.reportVersionId)"
                :value="widget.reportVersionId"
                :label="'历史固定版本 / ' + widget.reportVersionId" /></el-select
            ><el-button @click="model.widgets.splice(index, 1)">删除</el-button>
          </div>
          <div v-if="['METRIC', 'BAR', 'LINE', 'PIE'].includes(widget.type)" class="flex gap-2 mb-3">
            <el-select v-if="widget.type !== 'METRIC'" v-model="widget.categoryField" placeholder="分类字段"
              ><el-option v-for="field in sources[widget.id]?.fields || []" :key="field.name" :value="field.name" :label="field.label" /></el-select
            ><el-select v-model="widget.valueField" placeholder="指标字段"
              ><el-option v-for="field in sources[widget.id]?.fields || []" :key="field.name" :value="field.name" :label="field.label" /></el-select
            ><el-select v-model="widget.function"><el-option v-for="fn in functions" :key="fn" :value="fn" :label="fn" /></el-select
            ><el-select v-if="widget.type === 'METRIC'" v-model="widget.comparison"
              ><el-option label="不比较" value="NONE" /><el-option label="上一期间" value="PREVIOUS_PERIOD" /><el-option
                label="上一日历年"
                value="PREVIOUS_YEAR" /></el-select
            ><template v-if="widget.comparison && widget.comparison !== 'NONE'"
              ><el-select v-model="widget.startParameter" placeholder="起始DATE参数"
                ><el-option
                  v-for="p in sources[widget.id]?.parameters.filter((p) => p.type === 'DATE') || []"
                  :key="p.name"
                  :value="p.name"
                  :label="p.label" /></el-select
              ><el-select v-model="widget.endParameter" placeholder="截止DATE参数"
                ><el-option
                  v-for="p in sources[widget.id]?.parameters.filter((p) => p.type === 'DATE') || []"
                  :key="p.name"
                  :value="p.name"
                  :label="p.label" /></el-select
            ></template>
          </div>
          <div v-for="parameter in sources[widget.id]?.parameters || []" :key="parameter.name" class="flex gap-2 mb-3">
            <span>{{ parameter.label }}（{{ parameter.type }}）</span
            ><el-select v-model="widget.parameterBindings[parameter.name]" clearable placeholder="映射仪表盘参数"
              ><el-option v-for="p in model.parameters.filter((p) => p.type === parameter.type)" :key="p.name" :label="p.label" :value="p.name"
            /></el-select>
          </div>
          <div class="flex gap-3 flex-wrap">
            <label>列起点<el-input-number v-model="widget.layout.x" :min="0" :max="11" /></label
            ><label>行起点<el-input-number v-model="widget.layout.y" :min="0" :max="100" /></label
            ><label>宽度<el-input-number v-model="widget.layout.width" :min="1" :max="12" /></label
            ><label>高度<el-input-number v-model="widget.layout.height" :min="1" :max="12" /></label
            ><label>移动顺序<el-input-number v-model="widget.layout.mobileOrder" :min="0" :max="100" /></label>
          </div>
          <el-button
            v-if="['BAR', 'LINE', 'PIE'].includes(widget.type)"
            class="mt-3"
            @click="widget.links.push({ targetWidget: '', sourceField: widget.categoryField || '', targetParameter: '' })"
            >添加类型化联动</el-button
          >
          <div v-for="(mapping, i) in widget.links" :key="i" class="flex gap-2 mt-3">
            <el-select v-model="mapping.targetWidget" placeholder="目标组件"
              ><el-option
                v-for="target in model.widgets.filter((value) => value.id !== widget.id)"
                :key="target.id"
                :value="target.id"
                :label="target.title" /></el-select
            ><el-select v-model="mapping.sourceField" placeholder="源分类"
              ><el-option v-if="widget.categoryField" :value="widget.categoryField" :label="widget.categoryField" /></el-select
            ><el-select v-model="mapping.targetParameter" placeholder="同类型目标参数"
              ><el-option
                v-for="p in sources[mapping.targetWidget]?.parameters || []"
                :key="p.name"
                :value="p.name"
                :label="p.label + ' / ' + p.type" /></el-select
            ><el-button @click="widget.links.splice(i, 1)">删除联动</el-button>
          </div> </el-card
        ><el-form-item
          ><el-button type="primary" :loading="busy" @click="save">保存草稿</el-button
          ><el-button v-hasPermi="['report:dashboard:publish']" :disabled="!resource || dirty" @click="publish">发布固定版本</el-button
          ><el-button @click="cancelEdit">退出设计</el-button></el-form-item
        > </el-form
      ><el-empty v-if="!published && !editing" description="选择已发布仪表盘，或创建自己的组件布局"
    /></el-card>
  </div>
  <AclDialog
    :open="!!aclId"
    resource-type="DASHBOARD"
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
/** 独立仪表盘原生设计与查看，隐藏时停止刷新，异步数据不能跨会话覆盖。 */
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listReports } from '@/api/report';
import {
  listDashboards,
  dashboardDetail,
  dashboardPublished,
  renderDashboard,
  createDashboard,
  saveDashboard,
  publishDashboard,
  dashboardFavorites,
  favoriteDashboard,
  dashboardSource,
  type DashboardDefinition,
  type DashboardWidget as Widget,
  type DashboardView,
  type DashboardSource,
  type DashboardSelection
} from '@/api/report/dashboard';
import type { ReportDefinition, TypedValue } from '@/types/report';
import type { ReportResource } from '@/api/report/query';
import { RequestGate, parameterInput } from '@/views/report/model';
import { canRefresh, widgetLayout } from './model';
import ParameterForm from '@/components/report/ParameterForm.vue';
import DashboardWidget from '@/components/report/DashboardWidget.vue';
/**
 * 保存当前页面或测试的dashboards、favorites、reports、selected、published、model、resource、code、baseline、editing、busy、rendering、paused、visible、parameters、rendered、sources、selections、error状态。
 */
const dashboards = ref<ReportResource[]>([]),
  favorites = ref<string[]>([]),
  reports = ref<ReportDefinition[]>([]),
  selected = ref(''),
  published = ref<DashboardDefinition>(),
  model = ref<DashboardDefinition>(),
  resource = ref<ReportResource>(),
  code = ref(''),
  baseline = ref(''),
  editing = ref(false),
  busy = ref(false),
  rendering = ref(false),
  paused = ref(false),
  visible = ref(document.visibilityState === 'visible'),
  parameters = ref<Record<string, string>>({}),
  rendered = ref<DashboardView[]>([]),
  sources = ref<Record<string, DashboardSource>>({}),
  selections = ref<DashboardSelection[]>([]),
  error = ref('');
/**
 * 保存当前页面或测试的gate、viewGate、renderGate、editorGate、switchGate状态。
 */
const gate = new RequestGate(),
  viewGate = new RequestGate(),
  renderGate = new RequestGate(),
  editorGate = new RequestGate(),
  switchGate = new RequestGate();
/**
 * 保存当前页面或测试的timer、widgetSerial状态。
 */
let timer: ReturnType<typeof setInterval> | undefined,
  widgetSerial = 0;
/**
 * 保存当前页面或测试的widgetTypes、functions、valueTypes状态。
 */
const widgetTypes = ['METRIC', 'BAR', 'LINE', 'PIE', 'TABLE', 'PIVOT'] as const,
  functions = ['SUM', 'COUNT', 'COUNT_ROWS', 'MIN', 'MAX', 'AVG'],
  valueTypes = ['STRING', 'ID', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME'];
/**
 * 保存当前页面或测试的dirty状态。
 */
const dirty = computed(() => editing.value && JSON.stringify({ code: code.value, model: model.value }) !== baseline.value);
/** 目录与收藏分别刷新，收藏不隐式获得权限。 */
async function load() {
  const ticket = gate.begin();
  const [a, b] = await Promise.all([listDashboards(), dashboardFavorites()]);
  if (gate.current(ticket)) {
    dashboards.value = a.data;
    favorites.value = b.data;
  }
}
/** 保留未保存设计的选择机会，迟到确认不覆盖新会话。 */
async function maySwitch() {
  const ticket = switchGate.begin();
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('有未保存的仪表盘设计，确认切换？', '切换仪表盘');
    } catch {
      return false;
    }
  }
  return switchGate.current(ticket);
}
/** 选择发布仪表盘并使旧回包、联动和定时器失效。 */
async function open(id: string) {
  if (!(await maySwitch())) return;
  const ticket = viewGate.begin();
  editorGate.invalidate();
  renderGate.invalidate();
  editing.value = false;
  selected.value = id;
  published.value = undefined;
  rendered.value = [];
  parameters.value = {};
  selections.value = [];
  error.value = '';
  schedule();
  try {
    const response = await dashboardPublished(id);
    if (!viewGate.current(ticket)) return;
    published.value = response.data;
    await refresh();
    schedule();
  } catch {
    if (viewGate.current(ticket)) error.value = '发布仪表盘不可读取，请检查状态和权限';
  }
}
/** 每次刷新只接收当前会话的结果，并保留服务端的局部错误。 */
async function refresh() {
  if (!selected.value || editing.value || rendering.value) return;
  const ticket = renderGate.begin();
  rendering.value = true;
  error.value = '';
  try {
    const response = await renderDashboard(selected.value, parameterInput(parameters.value), selections.value);
    if (renderGate.current(ticket)) rendered.value = response.data.widgets;
  } catch {
    if (renderGate.current(ticket)) error.value = '刷新失败，请检查参数、权限或刷新限额';
  } finally {
    if (renderGate.current(ticket)) rendering.value = false;
  }
}
/** 隐藏页面及暂停状态都停止网络定时请求。 */
function schedule() {
  if (timer) clearInterval(timer);
  timer = undefined;
  if (canRefresh(visible.value, paused.value, editing.value, selected.value) && published.value)
    timer = setInterval(
      () => {
        if (canRefresh(visible.value, paused.value, editing.value, selected.value)) void refresh();
      },
      Math.max(30, published.value.refreshSeconds) * 1000
    );
}
/** 仅处理当前发布批准的源分类联动，原始值不转Number。 */
async function link(id: string, value: TypedValue) {
  const widget = published.value?.widgets.find((w) => w.id === id);
  if (!widget) return;
  for (const mapping of widget.links) {
    if (mapping.sourceField !== widget.categoryField) continue;
    selections.value = selections.value.filter((entry) => entry.sourceWidget !== id || entry.targetWidget !== mapping.targetWidget);
    selections.value.push({ sourceWidget: id, targetWidget: mapping.targetWidget, sourceField: mapping.sourceField, value });
  }
  renderGate.invalidate();
  rendering.value = false;
  await refresh();
}
/** 清除联动不改变主参数。 */
async function clearSelections() {
  renderGate.invalidate();
  rendering.value = false;
  rendered.value = [];
  selections.value = [];
  await refresh();
}
/** 收藏是显式用户操作。 */
async function favorite() {
  if (!selected.value) return;
  await favoriteDashboard(selected.value, !favorites.value.includes(selected.value));
  await load();
}
/** 添加自有组件，默认使用计数避免字符串被误当金额。 */
function addWidget() {
  if (!model.value) return;
  const id = 'widget_' + widgetSerial++;
  model.value.widgets.push({
    id,
    type: 'METRIC',
    componentVersion: 1,
    title: '新指标',
    reportVersionId: null,
    parameterBindings: {},
    categoryField: null,
    valueField: null,
    function: 'COUNT_ROWS',
    comparison: 'NONE',
    startParameter: null,
    endParameter: null,
    layout: { x: 0, y: model.value.widgets.length * 3, width: 6, height: 3, mobileOrder: model.value.widgets.length },
    links: []
  });
}
/** 初始化独立草稿及当前可用报告目录。 */
async function newDashboard() {
  if (!(await maySwitch())) return;
  const ticket = editorGate.begin();
  viewGate.invalidate();
  renderGate.invalidate();
  editing.value = true;
  schedule();
  resource.value = undefined;
  code.value = '';
  model.value = { formatVersion: 1, title: '个人仪表盘', parameters: [], widgets: [], refreshSeconds: 30 };
  sources.value = {};
  widgetSerial = 0;
  addWidget();
  baseline.value = JSON.stringify({ code: code.value, model: model.value });
  const response = await listReports();
  if (editorGate.current(ticket)) reports.value = response.data.filter((report) => report.status === 'PUBLISHED' && report.kind !== 'DOCUMENT');
}
/** 固定报告选择显式清空旧字段和参数映射。 */
async function chooseReport(widget: Widget, id: string) {
  const ticket = editorGate.begin();
  widget.reportVersionId = id;
  widget.parameterBindings = {};
  widget.valueField = null;
  widget.categoryField = null;
  widget.links = [];
  busy.value = true;
  try {
    const response = await dashboardSource(id);
    if (!editorGate.current(ticket)) return;
    sources.value[widget.id] = response.data;
    widget.valueField = response.data.fields[0]?.name || null;
    widget.categoryField = response.data.fields[0]?.name || null;
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 编辑固定历史版本时读取该版本契约，不切换到报告的新指针。 */
async function edit(id: string) {
  if (!(await maySwitch())) return;
  const ticket = editorGate.begin();
  viewGate.invalidate();
  renderGate.invalidate();
  editing.value = true;
  schedule();
  busy.value = true;
  try {
    const [a, b] = await Promise.all([dashboardDetail(id), listReports()]);
    if (!editorGate.current(ticket)) return;
    resource.value = a.data;
    code.value = a.data.code;
    model.value = JSON.parse(a.data.json) as DashboardDefinition;
    sources.value = {};
    reports.value = b.data.filter((report) => report.status === 'PUBLISHED' && report.kind !== 'DOCUMENT');
    widgetSerial = 100;
    for (const widget of model.value.widgets)
      if (widget.reportVersionId) {
        const response = await dashboardSource(widget.reportVersionId);
        if (!editorGate.current(ticket)) return;
        sources.value[widget.id] = response.data;
      }
    baseline.value = JSON.stringify({ code: code.value, model: model.value });
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 保存当前草稿及其明确组件版本。 */
async function save() {
  if (!model.value || busy.value) return;
  const ticket = editorGate.begin();
  const sent = JSON.parse(JSON.stringify(model.value)) as DashboardDefinition;
  const sentCode = code.value;
  busy.value = true;
  try {
    const response = resource.value ? await saveDashboard(resource.value, sent.title, sent) : await createDashboard(sentCode, sent.title, sent);
    if (editorGate.current(ticket)) {
      resource.value = response.data;
      baseline.value = JSON.stringify({ code: sentCode, model: sent });
      ElMessage.success('仪表盘草稿已保存');
      await load();
    }
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 发布只使用已经保存的revision。 */
async function publish() {
  if (!resource.value || dirty.value || busy.value) return;
  const ticket = editorGate.begin();
  busy.value = true;
  try {
    await publishDashboard(resource.value);
    const response = await dashboardDetail(resource.value.id);
    if (editorGate.current(ticket)) {
      resource.value = response.data;
      await load();
      ElMessage.success('仪表盘固定版本已发布');
    }
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 退出设计前保留未保存编辑的确认。 */
async function cancelEdit() {
  if (!(await maySwitch())) return;
  editing.value = false;
  model.value = undefined;
  await load();
  if (selected.value) await open(selected.value);
  schedule();
}
/** 页面可见性变化不执行隐藏刷新。 */
function visibilityChanged() {
  visible.value = document.visibilityState === 'visible';
  schedule();
}
watch([paused, editing, visible], schedule);
watch(
  parameters,
  () => {
    renderGate.invalidate();
    rendered.value = [];
    rendering.value = false;
  },
  { deep: true, flush: 'sync' }
);
onBeforeRouteLeave(async () => !dirty.value || (await maySwitch()));
onMounted(() => {
  document.addEventListener('visibilitychange', visibilityChanged);
  void load();
});
onUnmounted(() => {
  gate.invalidate();
  viewGate.invalidate();
  renderGate.invalidate();
  editorGate.invalidate();
  switchGate.invalidate();
  if (timer) clearInterval(timer);
  document.removeEventListener('visibilitychange', visibilityChanged);
});
</script>
<style scoped>
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  grid-auto-rows: 90px;
  gap: 16px;
}
.dashboard-grid > * {
  min-width: 0;
}
@media (max-width: 768px) {
  .dashboard-grid {
    display: flex;
    flex-direction: column;
  }
  .dashboard-grid > * {
    order: var(--mobile-order);
    min-height: 280px;
  }
}
</style>

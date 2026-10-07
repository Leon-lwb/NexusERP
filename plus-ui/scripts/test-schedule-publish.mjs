import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { runInNewContext } from 'node:vm';
import { parse } from '@vue/compiler-sfc';
import { build, transform } from 'esbuild';

const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const viewSource = readFileSync(resolve(uiRoot, 'src/views/erp/prd/schedule/index.vue'), 'utf8');
const typesSource = readFileSync(resolve(uiRoot, 'src/api/erp/prd/schedule/types.ts'), 'utf8');
const { descriptor, errors } = parse(viewSource);
const template = descriptor.template?.content ?? '';
const script = descriptor.scriptSetup?.content ?? '';
let policyPromise;
let executionViewPromise;
function loadExecutionView() {
  executionViewPromise ??= transform(readFileSync(resolve(uiRoot, 'src/views/ai/decision/decisionActionExecutionView.ts'), 'utf8'), {
    loader: 'ts',
    format: 'esm'
  }).then(({ code }) => import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`));
  return executionViewPromise;
}

function loadPolicy() {
  policyPromise ??= build({
    entryPoints: [resolve(uiRoot, 'src/views/erp/prd/schedule/schedulePolicy.ts')],
    bundle: true,
    platform: 'node',
    format: 'esm',
    write: false
  }).then(({ outputFiles }) => import(`data:text/javascript;base64,${Buffer.from(outputFiles[0].contents).toString('base64')}`));
  return policyPromise;
}

const v2 = { planId: '9007199254740993', planNo: 'APS-2', status: 'COMPLETED', capacityCalcVersion: 2 };

test('publish policy requires COMPLETED and numeric version 2, and rejects all PUBLISHED versions', async () => {
  const { isPublishableSchedulePlan } = await loadPolicy();
  assert.equal(isPublishableSchedulePlan(v2), true);
  for (const capacityCalcVersion of [0, null, undefined, '2', 3]) {
    assert.equal(isPublishableSchedulePlan({ ...v2, capacityCalcVersion }), false, `reject version ${capacityCalcVersion}`);
  }
  assert.equal(isPublishableSchedulePlan({ planId: v2.planId, status: 'COMPLETED' }), false, 'missing version');
  for (const capacityCalcVersion of [0, null, undefined, 2, 3]) {
    assert.equal(isPublishableSchedulePlan({ ...v2, status: 'PUBLISHED', capacityCalcVersion }), false);
  }
  assert.equal(isPublishableSchedulePlan({ ...v2, status: 'RUNNING' }), false);
  assert.equal(isPublishableSchedulePlan(null), false);
});

test('legacy and unknown plans are labeled without claiming v2 provenance was checked', async () => {
  const { isLegacySchedulePlan, scheduleCapacityLabel } = await loadPolicy();
  for (const plan of [{ capacityCalcVersion: 0 }, { capacityCalcVersion: null }, {}]) {
    assert.equal(isLegacySchedulePlan(plan), true);
    assert.equal(scheduleCapacityLabel(plan), '历史口径，非新版可信产能');
  }
  assert.equal(isLegacySchedulePlan(v2), false);
  assert.equal(scheduleCapacityLabel(v2), '排程版本 2');
  assert.equal(isLegacySchedulePlan({ capacityCalcVersion: 3 }), false);
  assert.match(scheduleCapacityLabel({ capacityCalcVersion: 3 }), /版本未识别|不支持/);
});

test('schedule view binds toolbar and row buttons to the same guard and keeps publish permission', () => {
  assert.deepEqual(errors, [], 'SFC parses');
  assert.match(typesSource, /capacityCalcVersion\?:\s*number\s*\|\s*null\s*;/);
  assert.match(script, /const canPublish = computed\(\(\) => isPublishableSchedulePlan\(toolbarPlan\.value\)\)/);
  assert.match(template, /:disabled="!canPublish"\s+@click="handlePublish"\s+v-hasPermi="\['prd:schedule:publish'\]"/);
  const actions = template.match(/<el-table-column label="操作"[\s\S]*?<\/el-table-column>/)?.[0] ?? '';
  assert.match(
    actions,
    /<el-tooltip\s+v-if="isPublishableSchedulePlan\(scope\.row\)"[^>]*>[\s\S]*?<el-button[\s\S]*?v-hasPermi="\['prd:schedule:publish'\]"\s+@click\.stop="handlePublishRow\(scope\.row\)"/
  );
  assert.match(template, /scope\.row\.status === 'PUBLISHED' \? '已发布' : '已生成'/);
});

test('list row publish warns that 8h generation estimates are not publication approval', () => {
  const list = template.match(/<el-table\b[\s\S]*?<\/el-table>/)?.[0];
  const actions = list?.match(/<el-table-column label="操作"[\s\S]*?<\/el-table-column>/)?.[0];
  assert.ok(actions, 'the warning must live in the list row, not only the run dialog');
  const tooltip = actions.match(/<el-tooltip[^>]*content="([^"]+)"[^>]*>[\s\S]*?<\/el-tooltip>/)?.[0];
  assert.ok(tooltip, 'the row publish control needs a tooltip explaining its prerequisites');
  assert.match(tooltip, /v-if="isPublishableSchedulePlan\(scope\.row\)"/, 'keep the v2 COMPLETED row entry point');
  assert.match(tooltip, />发布（需核验）<\/el-button>/, 'show a visible reminder even without hovering');
  assert.doesNotMatch(tooltip, /:disabled\s*=/, 'do not disable every v2 publish button');
  const hint = tooltip.match(/content="([^"]+)"/)?.[1] ?? '';
  assert.match(hint, /生成时[^"<]*8h\/日[^"<]*不代表可发布/);
  assert.match(hint, /发布需[^"<]*对应资源[^"<]*同日期[^"<]*真实 ACTIVE 班次产能日历[^"<]*足额剩余产能/);
  assert.match(hint, /最终由后端发布事务校验/);
  assert.doesNotMatch(hint, /已通过|已校验/, 'the UI must not claim this plan passed a calendar check');
});

test('run dialog keeps the 8h generation fallback but warns that publication needs verified calendars', () => {
  const dialog = template.match(/<!-- 执行排产对话框 -->([\s\S]*?)<!-- 甘特图抽屉 -->/)?.[1];
  assert.ok(dialog, 'warning belongs to the run dialog');
  assert.match(dialog, /无产能日历日期在生成时[^<]*8h\/日/, '8h is only a generation estimate');
  const warning = dialog.match(/<el-alert[^>]*type="warning"[\s\S]*?<\/el-alert>/)?.[0];
  assert.ok(warning, 'publication prerequisites must be visibly called out');
  assert.match(warning, /非委外逐日负荷[^<]*对应资源[^<]*日期[^<]*班次[^<]*ACTIVE\s+产能日历/);
  assert.match(warning, /无对应日历[^<]*不能发布/);
  assert.match(warning, /服务端[^<]*校验/);
});

test('run dialog distinguishes a generated version-2 plan from server-published AI-ready output', () => {
  const dialog = template.match(/<!-- 执行排产对话框 -->([\s\S]*?)<!-- 甘特图抽屉 -->/)?.[1];
  assert.ok(dialog);
  assert.match(dialog, /排程版本 2[^<]*不等于可发布/);
  assert.match(dialog, /服务端发布成功[^<]*可信 AI 交付风险分析/);
});

test('direct handlePublishRow call does not confirm or PUT legacy/published plans, but allows v2', async () => {
  const { isPublishableSchedulePlan } = await loadPolicy();
  const declaration = script.match(/const handlePublishRow = async \(row: SchedulePlanVO\) => \{[\s\S]*?\n\};/)?.[0];
  assert.ok(declaration, 'publish handler exists');
  const { code } = await transform(`${declaration}\nglobalThis.publishHandler = handlePublishRow;`, { loader: 'ts', format: 'cjs' });
  const calls = { confirm: 0, requests: [], success: 0, reload: 0, warnings: 0 };
  const publishing = { ...v2 };
  const selectedPlan = { value: publishing };
  const context = {
    selectedPlan,
    isPublishableSchedulePlan,
    proxy: {
      $modal: {
        confirm: async () => {
          calls.confirm++;
        },
        msgWarning: () => {
          calls.warnings++;
        },
        msgSuccess: () => {
          calls.success++;
        }
      }
    },
    publishSchedule: async (id) => {
      calls.requests.push(id);
    },
    getList: () => {
      calls.reload++;
    }
  };
  runInNewContext(code, context);
  for (const plan of [
    { ...v2, capacityCalcVersion: 0 },
    { ...v2, capacityCalcVersion: null },
    { planId: v2.planId, planNo: v2.planNo, status: 'COMPLETED' },
    { ...v2, status: 'PUBLISHED' },
    { ...v2, status: 'PUBLISHED', capacityCalcVersion: 0 }
  ]) {
    await context.publishHandler(plan);
    assert.equal(calls.confirm, 0, 'blocked plan must not ask to publish');
    assert.deepEqual(calls.requests, [], 'blocked plan must not issue PUT');
  }
  assert.equal(calls.success, 0);
  assert.equal(calls.reload, 0);
  assert.equal(calls.warnings, 5);
  await context.publishHandler(publishing);
  assert.equal(calls.confirm, 1);
  assert.deepEqual(calls.requests, [v2.planId], 'version 2 uses the original string plan ID');
  assert.equal(calls.success, 1);
  assert.equal(calls.reload, 1);
  assert.equal(publishing.status, 'PUBLISHED', 'row immediately reflects successful publication');
  assert.equal(selectedPlan.value.status, 'PUBLISHED', 'toolbar selection must stop being publishable');
  await context.publishHandler(publishing);
  assert.deepEqual(calls.requests, [v2.planId], 'a repeated click never sends a second PUT');
});

test('list and Gantt flag legacy without rewriting stored status or chart data', () => {
  assert.match(template, /<el-table-column label="产能口径"[\s\S]*?scheduleCapacityLabel\(scope\.row\)/);
  assert.match(template, /排程版本 2/);
  assert.ok(/v-if="isLegacySchedulePlan\(ganttPlan\)"/.test(template), 'displayed plan supplies the legacy warning');
  assert.ok(/ganttPlan\?\.capacityCalcVersion === 2/.test(template), 'displayed plan supplies the v2 badge');
});
async function mountScheduleView(overrides = {}) {
  /** 新增只读深链依赖接线，保留原发布/拒绝断言与真实处理函数。 */
  const { decisionScheduleResultQuery, decisionScheduleResultRows } = await loadExecutionView();
  const { isLegacySchedulePlan, isPublishableSchedulePlan, isDecisionReadySchedulePlan, scheduleCapacityLabel, formatScheduleGanttTooltip } =
    await loadPolicy();
  const requests = [];
  const riskCalls = [];
  const watchers = [];
  const unmountCallbacks = [];
  const alerts = [];
  const charts = [];
  const listCalls = [];
  const mutations = [];
  const context = {
    ref: (value) => ({ value }),
    computed: (getter) => ({
      get value() {
        return getter();
      }
    }),
    getCurrentInstance: () => ({
      proxy: {
        $modal: {
          msgError: (text) => alerts.push(text),
          msgWarning: (text) => alerts.push(text),
          msgSuccess: () => {},
          confirm: async () => {}
        }
      }
    }),
    useRouter: () => ({ push: async () => {} }),
    useRoute: () => ({ query: {} }),
    decisionScheduleResultQuery,
    decisionScheduleResultRows,
    onMounted: () => {},
    onBeforeUnmount: (callback) => unmountCallbacks.push(callback),
    watch: (source, callback) => watchers.push({ source, callback }),
    nextTick: async () => {},
    dayjs: () => ({ startOf: () => ({ add: () => ({ format: () => 'derived-day' }) }), diff: () => 0, format: () => 'derived-day' }),
    echarts: {
      init: () => {
        const chart = {
          options: null,
          disposed: false,
          setOption(options) {
            this.options = options;
          },
          resize() {},
          dispose() {
            this.disposed = true;
          }
        };
        charts.push(chart);
        return chart;
      }
    },
    listSchedule: (query) => new Promise((resolve, reject) => listCalls.push({ query: { ...query }, resolve, reject })),
    publishSchedule: async (id) => mutations.push({ action: 'publish', id }),
    delSchedule: async (ids) => mutations.push({ action: 'delete', ids: [...ids] }),
    getScheduleGantt: (id) => new Promise((resolve, reject) => requests.push({ id, resolve, reject })),
    runDeliveryRisk: async (args) => {
      riskCalls.push(args);
      return { data: { runId: 'run' } };
    },
    isLegacySchedulePlan,
    isPublishableSchedulePlan,
    isDecisionReadySchedulePlan,
    scheduleCapacityLabel,
    formatScheduleGanttTooltip,
    ...overrides
  };
  /** 转译实际 SFC 的 setup，检验真实处理函数与响应式行为，而非复制出的模型。 */
  const withoutImports = script.replace(/^import\b[\s\S]*?;\r?$/gm, '');
  const { code } = await transform(
    `${withoutImports}\nglobalThis.view = {
    handleViewGantt, handleRowClick, handleSelectionChange, handlePublish,
    handleQuery, handleDelete, getList,
    handleOpenSelectedGantt: typeof handleOpenSelectedGantt === 'function' ? handleOpenSelectedGantt : undefined,
    handleDeliveryDecision, renderGantt, selectedPlan,
    planTableRef: typeof planTableRef === 'undefined' ? undefined : planTableRef,
    toolbarPlan: typeof toolbarPlan === 'undefined' ? undefined : toolbarPlan,
    checkedPlans: typeof checkedPlans === 'undefined' ? undefined : checkedPlans,
    canPublish, planList, ids, single, multiple, total, loading, queryParams,
    ganttPlan: typeof ganttPlan === 'undefined' ? undefined : ganttPlan,
    gantt, ganttTitle, ganttLoading, ganttChartRef, showGanttDrawer,
    selectedPlanId
  };`,
    { loader: 'ts', format: 'cjs' }
  );
  runInNewContext(code, context);
  context.view.ganttChartRef.value = {};
  return { view: context.view, requests, riskCalls, watchers, unmountCallbacks, alerts, charts, listCalls, mutations };
}

const planA = { ...v2, planId: '9007199254741001', planNo: 'A', capacityCalcVersion: 0 };
const planB = { ...v2, planId: '9007199254741002', planNo: 'B' };
function ganttFor(plan, rows = []) {
  return { planId: plan.planId, planNo: plan.planNo, rows, loads: [], startDate: '2026-09-29', horizonDays: 14 };
}

test('toolbar GETs its selected plan; switching plans clears old chart, title and version before rendering B', async () => {
  assert.ok(/@click="handleOpenSelectedGantt"/.test(template), 'toolbar opens and loads the selected Gantt');
  assert.match(template, /@click\.stop="handleViewGantt\(scope\.row\)"/);
  const { view, requests, charts } = await mountScheduleView();
  const first = view.handleViewGantt(planA);
  requests[0].resolve({ data: ganttFor(planA) });
  await first;
  assert.equal(view.ganttTitle.value, '排程甘特图 - A');
  assert.equal(view.ganttPlan.value.capacityCalcVersion, 0);
  const second = view.handleRowClick(planB);
  assert.equal(requests[1].id, planB.planId, 'selecting B in an open drawer loads B');
  assert.equal(view.gantt.value, null, 'A data removed immediately');
  assert.equal(view.ganttPlan.value, null, 'A provenance removed immediately');
  assert.equal(view.ganttTitle.value, '排程甘特图', 'B title cannot be attached to A data');
  assert.equal(charts[0].disposed, true);
  requests[1].resolve({ data: ganttFor(planB) });
  await second;
  assert.equal(view.gantt.value.planId, planB.planId);
  assert.equal(view.ganttTitle.value, '排程甘特图 - B');
});

test('stale A response cannot replace newer B; toolbar uses the same request path and displayed identity', async () => {
  const { view, requests, riskCalls } = await mountScheduleView();
  view.planList.value = [planA, planB];
  const first = view.handleViewGantt(planA);
  view.selectedPlan.value = planB;
  view.selectedPlanId.value = planB.planId;
  const second = view.handleOpenSelectedGantt();
  assert.deepEqual(
    requests.map(({ id }) => id),
    [planA.planId, planB.planId]
  );
  requests[1].resolve({ data: ganttFor(planB) });
  await second;
  requests[0].resolve({ data: ganttFor(planA) });
  await first;
  assert.equal(view.gantt.value.planId, planB.planId);
  assert.equal(view.ganttPlan.value.planId, planB.planId);
  assert.equal(view.ganttTitle.value, '排程甘特图 - B');
  view.ganttPlan.value = { ...planB, status: 'PUBLISHED' };
  await view.handleDeliveryDecision();
  assert.equal(riskCalls.length, 1);
  assert.equal(riskCalls[0].bizId, planB.planId, 'protected risk action follows displayed chart, not stale selection');
});

test('close, unmount and planId mismatch all fail closed against late chart responses', async () => {
  const { view, requests, watchers, unmountCallbacks, alerts, charts } = await mountScheduleView();
  const first = view.handleViewGantt(planA);
  view.showGanttDrawer.value = false;
  watchers.find(({ source }) => source === view.showGanttDrawer)?.callback(false);
  requests[0].resolve({ data: ganttFor(planA) });
  await first;
  assert.equal(view.gantt.value, null);
  assert.equal(view.ganttPlan.value, null);
  assert.equal(view.ganttLoading.value, false);
  assert.equal(charts.length, 0);
  const next = view.handleViewGantt(planB);
  requests[1].resolve({ data: ganttFor(planA) });
  await next;
  assert.equal(view.gantt.value, null, 'a mismatched response must not be rendered');
  assert.equal(view.ganttPlan.value, null);
  assert.ok(alerts.length > 0, 'unexpected plan ID is reported');
  const after = view.handleViewGantt(planB);
  unmountCallbacks.forEach((callback) => callback());
  requests[2].resolve({ data: ganttFor(planB) });
  await after;
  assert.equal(view.gantt.value, null, 'unmounted drawer cannot be repopulated');
});

test('AI text interpretation is disabled because latest-only tool cannot analyze the displayed plan', () => {
  assert.ok(/<el-button[^>]*\bdisabled\b[^>]*>AI 文字解读（不支持按当前方案）<\/el-button>/.test(template), 'AI text action is visibly unavailable');
  assert.doesNotMatch(template, /<AiChatDrawer/);
  assert.doesNotMatch(script, /handleAiInterpret|aiChatRef|aps_schedule|AiChatDrawer/);
  assert.match(template, /@click="handleDeliveryDecision"[\s\S]*?v-hasPermi="\['ai:decision:run'\]"/);
});

test('tooltip escapes malicious labels and takes each hovered task’s raw start/end rather than rounded day', async () => {
  const { formatScheduleGanttTooltip } = await loadPolicy();
  const row = { woNo: '<img src=x onerror=evil()>', materialName: 'A&"\'<script>' };
  const bars = [
    {
      taskId: 'one',
      startTime: '2026-09-29T08:01:00',
      endTime: '2026-09-29T10:19:00',
      processName: 'first',
      durationDays: 1,
      resourceType: 'WORKSHOP'
    },
    {
      taskId: 'two',
      startTime: '2026-09-30T11:32:00',
      endTime: '2026-10-01T00:12:00',
      processName: '<b>second</b>',
      durationDays: 2,
      resourceType: 'WORKSHOP'
    }
  ];
  const escaped = formatScheduleGanttTooltip(row, bars[1]);
  assert.doesNotMatch(escaped, /<img|<script>|<b>second<\/b>/);
  assert.match(escaped, /&lt;img src=x onerror=evil\(\)&gt;/);
  assert.match(escaped, /A&amp;&quot;&#39;&lt;script&gt;/);
  assert.match(escaped, /起: 2026-09-30T11:32:00/);
  assert.match(escaped, /止: 2026-10-01T00:12:00/);
  const { view, requests, charts } = await mountScheduleView();
  const loading = view.handleViewGantt(planB);
  requests[0].resolve({ data: ganttFor(planB, [{ ...row, bars, delay: false }]) });
  await loading;
  const chart = charts.at(-1);
  const seriesBar = chart.options.series[0].data[1];
  const html = chart.options.tooltip.formatter({ value: seriesBar.value, data: seriesBar });
  assert.match(html, /起: 2026-09-30T11:32:00/);
  assert.match(html, /止: 2026-10-01T00:12:00/);
  assert.doesNotMatch(html, /<img|<script>|<b>second<\/b>|derived-day/);
});
const publishableA = { ...planA, capacityCalcVersion: 2 };

/** 工具栏必须定位到唯一确定的目标行；存在勾选行时，不能回退到最近点击的行。 */
test('two checked plans disable single-plan toolbar actions even after clicking publishable A', async () => {
  const { view, mutations, requests } = await mountScheduleView();
  view.planList.value = [publishableA, planB];
  view.handleSelectionChange([publishableA, planB]);
  view.handleRowClick(publishableA);
  assert.equal(view.toolbarPlan.value, null);
  assert.equal(view.canPublish.value, false);
  await view.handlePublish();
  await view.handleOpenSelectedGantt();
  assert.equal(mutations.length, 0, 'no PUT for multi-selection');
  assert.equal(requests.length, 0, 'no Gantt GET for multi-selection');
  assert.ok(/:disabled="!toolbarPlan"/.test(template), 'toolbar Gantt is visibly disabled for ambiguous selections');
});

test('checked B remains the explicit toolbar target after clicking A, never silently publishes A', async () => {
  const { view, mutations, requests, listCalls } = await mountScheduleView();
  const publishableB = { ...planB, status: 'COMPLETED' };
  view.planList.value = [publishableA, publishableB];
  view.handleSelectionChange([publishableB]);
  view.handleRowClick(publishableA);
  assert.equal(view.toolbarPlan.value.planId, publishableB.planId);
  assert.equal(view.canPublish.value, true);
  assert.ok(template.includes('当前工具栏方案：{{ toolbarPlan.planNo }}'), 'toolbar explicitly names the checked target');
  const publishButton = template.match(/<el-button[^>]*@click="handlePublish"[^>]*>([\s\S]*?)<\/el-button/)?.[1];
  assert.ok(!publishButton?.includes('toolbarPlan.planNo'), 'publish button stays compact for long plan numbers');
  await view.handlePublish();
  assert.equal(mutations.length, 1);
  assert.equal(mutations[0].id, publishableB.planId);
  assert.notEqual(mutations[0].id, publishableA.planId);
  assert.equal(listCalls.length, 1, 'successful publish refreshes the list');
  await view.handleOpenSelectedGantt();
  assert.equal(requests.length, 0, 'list refresh makes toolbar unable to GET stale data');
  listCalls[0].resolve({ rows: [publishableA, { ...publishableB, status: 'PUBLISHED' }], total: 2 });
});

test('single checked B still lets toolbar view B with its plan number visible', async () => {
  const { view, requests } = await mountScheduleView();
  view.planList.value = [publishableA, planB];
  view.handleRowClick(publishableA);
  view.handleSelectionChange([planB]);
  assert.ok(template.includes('当前工具栏方案：{{ toolbarPlan.planNo }}'), 'Gantt target is visible alongside the toolbar');
  const ganttButton = template.match(/<el-button[^>]*@click="handleOpenSelectedGantt"[^>]*>([\s\S]*?)<\/el-button/)?.[1];
  assert.ok(!ganttButton?.includes('toolbarPlan.planNo'), 'Gantt button stays compact for long plan numbers');
  const loading = view.handleOpenSelectedGantt();
  assert.equal(requests[0].id, planB.planId);
  requests[0].resolve({ data: ganttFor(planB) });
  await loading;
  assert.equal(view.gantt.value.planId, planB.planId);
});

test('no checked rows allow a clicked plan for the toolbar', async () => {
  const { view, requests, mutations, listCalls } = await mountScheduleView();
  const clickedA = { ...publishableA, status: 'COMPLETED' };
  view.planList.value = [clickedA, planB];
  view.handleRowClick(clickedA);
  assert.equal(view.toolbarPlan.value.planId, clickedA.planId);
  assert.equal(view.canPublish.value, true);
  const loading = view.handleOpenSelectedGantt();
  assert.equal(requests[0].id, clickedA.planId);
  requests[0].resolve({ data: ganttFor(clickedA) });
  await loading;
  await view.handlePublish();
  assert.equal(mutations[0].id, clickedA.planId);
  listCalls[0].resolve({ rows: [clickedA, planB], total: 2 });
});

test('refresh immediately invalidates old selection; absent row is removed after list GET', async () => {
  const { view, listCalls, mutations, requests } = await mountScheduleView();
  view.planList.value = [publishableA];
  view.handleRowClick(publishableA);
  view.handleSelectionChange([publishableA]);
  const pending = view.getList();
  assert.equal(view.toolbarPlan.value, null, 'pending refresh may not operate on stale selection');
  assert.equal(view.canPublish.value, false);
  await view.handlePublish();
  await view.handleOpenSelectedGantt();
  assert.equal(mutations.length, 0);
  assert.equal(requests.length, 0);
  listCalls[0].resolve({ rows: [planB], total: 1 });
  await pending;
  assert.equal(view.selectedPlanId.value, '');
  assert.equal(view.selectedPlan.value, null);
  assert.equal(view.ids.value.length, 0);
  assert.equal(view.toolbarPlan.value, null);
  assert.equal(view.canPublish.value, false);
});

test('fresh list replaces a formerly COMPLETED row with PUBLISHED; old selection cannot publish', async () => {
  const { view, listCalls, mutations } = await mountScheduleView();
  view.planList.value = [publishableA];
  view.handleRowClick(publishableA);
  const pending = view.getList();
  listCalls[0].resolve({ rows: [{ ...publishableA, status: 'PUBLISHED' }], total: 1 });
  await pending;
  assert.equal(view.toolbarPlan.value, null, 'refresh requires a fresh selection');
  view.handleRowClick(view.planList.value[0]);
  assert.equal(view.canPublish.value, false, 'new PUBLISHED state is authoritative');
  await view.handlePublish();
  assert.equal(mutations.length, 0);
});

test('new filtered GET wins even if the old list response arrives last; cannot restore A selection', async () => {
  const { view, listCalls, mutations } = await mountScheduleView();
  view.planList.value = [publishableA, planB];
  view.handleRowClick(publishableA);
  const older = view.getList();
  view.queryParams.value.status = 'PUBLISHED';
  view.handleQuery();
  assert.equal(listCalls.length, 2);
  assert.equal(listCalls[1].query.status, 'PUBLISHED');
  listCalls[1].resolve({ rows: [{ ...planB, status: 'PUBLISHED' }], total: 1 });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(view.loading.value, false);
  view.handleRowClick(view.planList.value[0]);
  listCalls[0].resolve({ rows: [publishableA], total: 1 });
  await older;
  assert.equal(view.planList.value[0].planId, planB.planId);
  assert.equal(view.selectedPlanId.value, planB.planId);
  assert.equal(view.canPublish.value, false);
  await view.handlePublish();
  assert.equal(mutations.length, 0);
});

test('deletion refresh clears the target before and after a removed row disappears', async () => {
  const { view, listCalls, mutations, requests } = await mountScheduleView();
  view.planList.value = [publishableA, planB];
  view.handleSelectionChange([publishableA]);
  view.handleRowClick(publishableA);
  await view.handleDelete();
  assert.equal(mutations[0].action, 'delete');
  assert.equal(listCalls.length, 1, 'delete starts list reload');
  assert.equal(view.canPublish.value, false);
  await view.handleOpenSelectedGantt();
  assert.equal(requests.length, 0);
  listCalls[0].resolve({ rows: [planB], total: 1 });
  await Promise.resolve();
  assert.equal(view.toolbarPlan.value, null);
  assert.equal(view.selectedPlanId.value, '');
});
test('older list GET resolving first never clears the loading lock for the newer request', async () => {
  const { view, listCalls, mutations } = await mountScheduleView();
  view.planList.value = [publishableA];
  view.handleRowClick(publishableA);
  const older = view.getList();
  const newer = view.getList();
  listCalls[0].resolve({ rows: [publishableA], total: 1 });
  await older;
  assert.equal(view.loading.value, true);
  assert.equal(view.toolbarPlan.value, null);
  await view.handlePublish();
  assert.equal(mutations.length, 0);
  listCalls[1].resolve({ rows: [planB], total: 1 });
  await newer;
  assert.equal(view.loading.value, false);
  assert.equal(view.planList.value[0].planId, planB.planId);
});
test('a late selection-change event for a row absent from the new page cannot restore stale IDs', async () => {
  const { view, requests, mutations } = await mountScheduleView();
  view.planList.value = [planB];
  view.handleSelectionChange([publishableA]);
  assert.equal(view.ids.value.length, 0);
  assert.equal(view.multiple.value, true);
  assert.equal(view.toolbarPlan.value, null);
  await view.handlePublish();
  await view.handleOpenSelectedGantt();
  assert.equal(mutations.length, 0);
  assert.equal(requests.length, 0);
});

test('refresh closes the currently shown old chart before its plan disappears from the list', async () => {
  const { view, requests, listCalls } = await mountScheduleView();
  view.planList.value = [publishableA];
  const showing = view.handleViewGantt(publishableA);
  requests[0].resolve({ data: ganttFor(publishableA) });
  await showing;
  assert.equal(view.gantt.value.planId, publishableA.planId);
  const refreshed = view.getList();
  assert.equal(view.showGanttDrawer.value, false);
  assert.equal(view.gantt.value, null);
  listCalls[0].resolve({ rows: [planB], total: 1 });
  await refreshed;
  assert.equal(view.selectedPlanId.value, '');
});

test('row Gantt action does not choose a toolbar target; an actual row click still does', async () => {
  const { view, requests, watchers } = await mountScheduleView();
  view.planList.value = [publishableA, planB];
  assert.match(template, /@click\.stop="handleViewGantt\(scope\.row\)"/);
  const opening = view.handleViewGantt(planB);
  requests[0].resolve({ data: ganttFor(planB) });
  await opening;
  assert.equal(view.toolbarPlan.value, null, 'the row button does not select a plan for the toolbar');
  view.showGanttDrawer.value = false;
  watchers.find(({ source }) => source === view.showGanttDrawer)?.callback(false);
  assert.equal(view.toolbarPlan.value, null, 'closing the drawer does not leave behind an implicit selection');
  view.handleRowClick(publishableA);
  assert.equal(view.toolbarPlan.value.planId, publishableA.planId);
  const second = view.handleViewGantt(planB);
  requests[1].resolve({ data: ganttFor(planB) });
  await second;
  assert.equal(view.toolbarPlan.value.planId, publishableA.planId, 'row button must not override explicit row selection');
});

test('refresh clears real Element Plus checkboxes, including when the latest GET rejects', async () => {
  assert.match(template, /<el-table[^>]*ref="planTableRef"/);
  const { view, listCalls, mutations } = await mountScheduleView();
  view.planList.value = [publishableA];
  const uiSelection = [publishableA];
  view.planTableRef.value = {
    clearSelection: () => {
      uiSelection.length = 0;
      view.handleSelectionChange([]);
    }
  };
  view.handleSelectionChange(uiSelection);
  const failed = view.getList();
  assert.equal(uiSelection.length, 0, 'real checkbox state is cleared even while GET is pending');
  listCalls[0].reject(new Error('list GET failed'));
  await assert.rejects(failed, /list GET failed/);
  assert.equal(view.loading.value, false);
  assert.equal(view.ids.value.length, 0);
  assert.equal(view.toolbarPlan.value, null);
  assert.equal(mutations.length, 0);
});

test('an older failed GET cannot erase the newer response or its fresh selection', async () => {
  const { view, listCalls } = await mountScheduleView();
  view.planList.value = [publishableA];
  const older = view.getList();
  const newer = view.getList();
  listCalls[1].resolve({ rows: [planB], total: 1 });
  await newer;
  view.handleSelectionChange([planB]);
  listCalls[0].reject(new Error('stale request failed'));
  await assert.rejects(older, /stale request failed/);
  assert.equal(view.planList.value[0].planId, planB.planId);
  assert.equal(view.toolbarPlan.value.planId, planB.planId);
});

test('only a published numeric v2 schedule permits protected risk analysis', async () => {
  const { isDecisionReadySchedulePlan } = await loadPolicy();
  const pending = { ...planB, status: 'COMPLETED' };
  const trusted = { ...planB, status: 'PUBLISHED' };
  assert.equal(isDecisionReadySchedulePlan(trusted), true);
  for (const plan of [
    planA,
    pending,
    { ...planA, status: 'PUBLISHED' },
    { ...trusted, capacityCalcVersion: null },
    { ...trusted, capacityCalcVersion: 3 },
    { ...trusted, capacityCalcVersion: '2' },
    { ...trusted, capacityCalcVersion: undefined },
    null
  ]) {
    assert.equal(isDecisionReadySchedulePlan(plan), false);
  }
  assert.match(template, /:disabled="!ganttPlan \|\| !gantt \|\| !isDecisionReadySchedulePlan\(ganttPlan\)"/);
  const { view, requests, riskCalls } = await mountScheduleView();
  for (const plan of [planA, pending, { ...planA, status: 'PUBLISHED' }, trusted]) {
    const loading = view.handleViewGantt(plan);
    requests.at(-1).resolve({ data: ganttFor(plan) });
    await loading;
    await view.handleDeliveryDecision();
  }
  assert.equal(riskCalls.length, 1, 'direct handler invocation cannot bypass the disabled button');
  assert.equal(riskCalls[0].bizId, trusted.planId);
  view.ganttPlan.value = { ...planA, status: 'PUBLISHED' };
  await view.handleDeliveryDecision();
  assert.equal(riskCalls.length, 1, 'changed provenance is checked again at invocation time');
});

test('delete confirmation cannot delete a newly checked plan instead of the confirmed target', async () => {
  let confirm;
  const { view, mutations } = await mountScheduleView({
    getCurrentInstance: () => ({
      proxy: {
        $modal: {
          confirm: () =>
            new Promise((resolve) => {
              confirm = resolve;
            }),
          msgWarning: () => {},
          msgSuccess: () => {}
        }
      }
    })
  });
  view.planList.value = [publishableA, planB];
  view.handleSelectionChange([publishableA]);
  const deletion = view.handleDelete();
  view.handleSelectionChange([planB]);
  confirm();
  await deletion;
  assert.equal(mutations.length, 0, 'changing checkboxes during confirmation aborts the deletion');
});

test('delete confirmation cannot delete after filters refresh or selection changes away and back', async () => {
  let confirm;
  const { view, listCalls, mutations } = await mountScheduleView({
    getCurrentInstance: () => ({
      proxy: {
        $modal: {
          confirm: () =>
            new Promise((resolve) => {
              confirm = resolve;
            }),
          msgWarning: () => {},
          msgSuccess: () => {}
        }
      }
    })
  });
  view.planList.value = [publishableA, planB];
  view.handleSelectionChange([publishableA]);
  const deletion = view.handleDelete();
  view.queryParams.value.status = 'PUBLISHED';
  view.handleQuery();
  listCalls[0].resolve({ rows: [planB], total: 1 });
  await Promise.resolve();
  confirm();
  await deletion;
  assert.equal(mutations.length, 0, 'a different list invalidates the pending confirmation');
  view.handleSelectionChange([planB]);
  const second = view.handleDelete();
  view.handleSelectionChange([publishableA]);
  view.handleSelectionChange([planB]);
  confirm();
  await second;
  assert.equal(mutations.length, 0, 'even transient checkbox changes invalidate the confirmation');
});

test('selection event from the previous page cannot reselect a refreshed row with the same plan ID', async () => {
  const { view, listCalls } = await mountScheduleView();
  const previous = { ...publishableA };
  view.planList.value = [previous];
  view.handleSelectionChange([previous]);
  const refreshed = view.getList();
  const current = { ...previous, status: 'PUBLISHED' };
  listCalls[0].resolve({ rows: [current], total: 1 });
  await refreshed;
  view.handleSelectionChange([previous]);
  assert.equal(view.ids.value.length, 0, 'an old row instance is not a fresh checkbox selection');
  assert.equal(view.toolbarPlan.value, null);
  view.handleSelectionChange([current]);
  assert.equal(view.toolbarPlan.value, current, 'an actual new row remains selectable');
});

test('failed filtered GET leaves no stale rows that can reselect a cleared checkbox', async () => {
  const { view, listCalls } = await mountScheduleView();
  const previous = { ...publishableA };
  view.planList.value = [previous];
  view.total.value = 1;
  view.handleSelectionChange([previous]);
  const failed = view.getList();
  listCalls[0].reject(new Error('list unavailable'));
  await assert.rejects(failed, /list unavailable/);
  view.handleSelectionChange([previous]);
  assert.equal(view.ids.value.length, 0, 'old selection event cannot reactivate an unchecked plan');
  assert.equal(view.planList.value.length, 0, 'failed filter must not display its previous results');
  assert.equal(view.total.value, 0);
});

test('a stale failed GET does not blank the latest list or discard a new checkbox', async () => {
  const { view, listCalls } = await mountScheduleView();
  const older = view.getList();
  const newer = view.getList();
  listCalls[1].resolve({ rows: [planB], total: 1 });
  await newer;
  view.handleSelectionChange([view.planList.value[0]]);
  listCalls[0].reject(new Error('older request failed'));
  await assert.rejects(older, /older request failed/);
  assert.equal(view.total.value, 1);
  assert.equal(view.planList.value[0].planId, planB.planId);
  assert.equal(view.toolbarPlan.value.planId, planB.planId);
});

test('a freshly checked version-2 plan cannot run a decision before publish, but can afterward', async () => {
  const { view, requests, riskCalls } = await mountScheduleView();
  const candidate = { ...publishableA, status: 'COMPLETED' };
  view.planList.value = [candidate];
  const opening = view.handleViewGantt(candidate);
  requests[0].resolve({ data: ganttFor(candidate) });
  await opening;
  await view.handleDeliveryDecision();
  assert.equal(riskCalls.length, 0);
  candidate.status = 'PUBLISHED';
  await view.handleDeliveryDecision();
  assert.equal(riskCalls.length, 1);
  assert.equal(riskCalls[0].bizId, candidate.planId);
});

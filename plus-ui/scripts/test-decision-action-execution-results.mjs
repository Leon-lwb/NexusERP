import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc';
import { build, transform } from 'esbuild';
import { createRenderer, createSSRApp, h, KeepAlive, nextTick, reactive } from 'vue';
import { renderToString } from 'vue/server-renderer';

const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const helperPath = resolve(uiRoot, 'src/views/ai/decision/decisionActionExecutionView.ts');
const componentPath = resolve(uiRoot, 'src/components/ai/decision/DecisionActionExecutionResults.vue');
const require = createRequire(import.meta.url);
const helper = existsSync(helperPath)
  ? await import(
      `data:text/javascript;base64,${Buffer.from((await transform(readFileSync(helperPath, 'utf8'), { loader: 'ts', format: 'esm' })).code).toString('base64')}`
    )
  : {};
function fn(name) {
  assert.equal(typeof helper[name], 'function', `结果安全函数 ${name} 必须存在`);
  return helper[name];
}
const id = '9007199254740993';
const action = (patch = {}) => ({
  id: '9007199254740995',
  runId: '9007199254740997',
  actionType: 'CREATE_PURCHASE_REQUISITION_DRAFT',
  title: '真实采购申请动作',
  approvalRequired: 'Y',
  approvalStatus: 'APPROVED',
  executionStatus: 'SUCCEEDED',
  resultBizType: 'PU_PURCHASE_REQUISITION',
  resultBizId: id,
  resultBizCode: 'PR20261007001',
  approvedAt: '2026-10-07 12:00:00',
  finishedAt: '2026-10-07 12:00:01',
  ...patch
});
const purchase = { requisitionId: id, requisitionNo: 'PR20261007001', status: 'DRAFT' };
/** 普通编辑语义必须来自明确MANUAL且无认证标记，不用缺失sourceType伪装可编辑来源。 */
const manualPurchase = { ...purchase, sourceType: 'MANUAL', decisionIdempotencyKey: null, decisionRunId: null, decisionActionId: null };

const scheduleAction = (patch = {}) =>
  action({
    actionType: 'CREATE_SCHEDULE_PLAN_DRAFT',
    resultBizType: 'PRD_SCHEDULE_PLAN',
    resultBizCode: 'APS20261007001',
    ...patch
  });
const schedule = { planId: id, planNo: 'APS20261007001', status: 'COMPLETED' };

test('真实白名单动作/结果组合只产生字符串 ID 回查目标，不产生未经回查的链接', () => {
  assert.deepEqual(fn('decisionActionResultTarget')(action()), { kind: 'requisition', id });
  assert.deepEqual(fn('decisionActionResultTarget')(scheduleAction()), { kind: 'schedule', id });
  assert.equal(fn('decisionActionResultTarget')(action()).destination, undefined);
});
for (const status of [
  'NOT_STARTED',
  'EXECUTING',
  'FAILED_PRECHECK',
  'FAILED',
  'SKIPPED',
  'COMPENSATING',
  'COMPENSATED',
  'COMPENSATE_FAILED',
  'UNKNOWN'
]) {
  test(`动作 ${status} 即使有结果 ID 也不可成功或回查`, () => {
    const candidate = action({ executionStatus: status });
    assert.equal(fn('decisionActionResultTarget')(candidate), null);
    assert.notEqual(fn('decisionActionExecutionView')(candidate).executionType, 'success');
  });
}
for (const badId of [undefined, null, '', 42, 9007199254740992, '0', '-1', '01', '1e3', '1/../2', '1?x=2', '9223372036854775808']) {
  test(`不安全/非字符串结果 ID 不导航：${String(badId)}`, () => {
    assert.equal(fn('decisionActionResultTarget')(action({ resultBizId: badId })), null);
  });
}
test('未知类型、伪类型及原目标/payload/模型正文不能代替真实执行结果', () => {
  const target = fn('decisionActionResultTarget');
  for (const patch of [
    { actionType: 'UNKNOWN' },
    { resultBizType: 'PURCHASE_REQUISITION' },
    { resultBizType: '/erp/pu/requisition' },
    { resultBizType: 'PRD_SCHEDULE_PLAN' },
    {
      resultBizId: undefined,
      targetBizId: id,
      targetBizType: 'PU_PURCHASE_REQUISITION',
      payloadJson: JSON.stringify({ resultBizId: id }),
      resultJson: JSON.stringify({ resultBizId: id })
    }
  ])
    assert.equal(target(action(patch)), null);
});
test('通知只标内部受理，绝不是采购/排程草稿，注入草稿结果类型也不能导航', () => {
  const candidate = action({ actionType: 'SEND_MANAGER_NOTIFICATION', resultBizType: 'SYS_NOTIFICATION', resultBizId: undefined });
  assert.equal(fn('decisionActionResultTarget')(candidate), null);
  assert.match(fn('decisionActionExecutionView')(candidate).executionLabel, /内部通知.*非ERP草稿/);
  assert.equal(fn('decisionActionResultTarget')(action({ actionType: 'SEND_EXPEDITE_NOTICE' })), null);
});
test('审批/执行分别展示，驳回及未授权绝不伪装已批准/创建', () => {
  const view = fn('decisionActionExecutionView');
  assert.equal(view(action({ approvalStatus: 'REJECTED', executionStatus: 'SKIPPED' })).approvalLabel, '已驳回');
  assert.equal(view(action({ approvalStatus: 'NOT_AUTHORIZED', executionStatus: 'NOT_STARTED' })).approvalLabel, '未授权');
  assert.equal(view(action({ approvalStatus: 'PENDING_APPROVAL', executionStatus: 'NOT_STARTED' })).approvalLabel, '待审批');
  assert.equal(view(action()).approvalLabel, '已批准');
});
for (const runStatus of ['ROLLING_BACK', 'ROLLED_BACK']) {
  test(`运行 ${runStatus} 不保留过时成功导航`, () => {
    assert.equal(fn('decisionActionResultTarget')(action(), runStatus), null);
    assert.notEqual(fn('decisionActionExecutionView')(action(), runStatus).executionType, 'success');
  });
}
test('ERP 只读回查必须匹配真实字符串 ID、编号和状态后才给固定查看路由', () => {
  const verify = fn('verifiedDecisionErpResult');
  const target = fn('decisionActionResultTarget')(action());
  assert.deepEqual(verify(target, purchase), {
    id,
    code: purchase.requisitionNo,
    status: 'DRAFT',
    destination: { path: '/erp/pu/requisition/form', query: { id, action: 'view', decisionResultRequisitionId: id } }
  });
  for (const record of [
    null,
    {},
    { ...purchase, requisitionId: '100' },
    { ...purchase, requisitionId: 9007199254740992 },
    { ...purchase, requisitionNo: '../../x' },
    { ...purchase, status: '' },
    { ...purchase, status: undefined }
  ])
    assert.equal(verify(target, record), null);
});
test('没有执行编号可由真实 ERP 记录补齐，不可从正文推导', () => {
  const target = fn('decisionActionResultTarget')(action({ resultBizCode: undefined }));
  assert.equal(fn('verifiedDecisionErpResult')(target, purchase).code, purchase.requisitionNo);
});
test('已取消 ERP 记录展示真实状态但没有草稿导航', () => {
  const verified = fn('verifiedDecisionErpResult')(fn('decisionActionResultTarget')(action()), { ...purchase, status: 'CANCELLED' });
  assert.equal(verified.status, 'CANCELLED');
  assert.equal(verified.destination, null);
});
test('排程深链来自真实回查记录，全部 ID 保持原始字符串', () => {
  const verified = fn('verifiedDecisionErpResult')(fn('decisionActionResultTarget')(scheduleAction()), schedule);
  assert.deepEqual(verified.destination, { path: '/erp/prd/schedule', query: { decisionResultPlanId: id, planNo: schedule.planNo } });
  assert.deepEqual(fn('decisionScheduleResultQuery')(verified.destination.query), { id, code: schedule.planNo });
});
test('排程深链拒绝数组/路径注入/数字 ID，并且定位只保留精确匹配行', () => {
  const parseQuery = fn('decisionScheduleResultQuery');
  for (const query of [
    {},
    { decisionResultPlanId: [id], planNo: schedule.planNo },
    { decisionResultPlanId: 42, planNo: schedule.planNo },
    { decisionResultPlanId: id, planNo: ['APS'] },
    { decisionResultPlanId: id, planNo: '../../x' }
  ])
    assert.equal(parseQuery(query), null);
  const rows = [schedule, { ...schedule, planId: '100' }, { ...schedule, planNo: 'OTHER' }, { ...schedule, planId: 9007199254740992 }];
  assert.deepEqual(fn('decisionScheduleResultRows')(rows, { id, code: schedule.planNo }), [schedule]);
  assert.deepEqual(fn('decisionScheduleResultRows')(rows, null), rows);
});

async function compileComponent(api = {}) {
  assert.ok(existsSync(componentPath), '真实结果 SFC 必须存在');
  const { descriptor, errors } = parse(readFileSync(componentPath, 'utf8'), { filename: componentPath });
  assert.deepEqual(errors, []);
  const compiled = compileScript(descriptor, { id: 'f02-results', inlineTemplate: true });
  const bundled = await build({
    stdin: { contents: compiled.content, resolveDir: dirname(componentPath), sourcefile: componentPath, loader: 'ts' },
    alias: { '@': resolve(uiRoot, 'src') },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    write: false,
    logLevel: 'silent',
    plugins: [
      {
        name: 'readonly-api-boundary',
        setup(builder) {
          builder.onResolve({ filter: /^@\/api\/erp\// }, (args) => ({ path: args.path, external: true }));
        }
      }
    ],
    external: ['vue']
  });
  const mod = { exports: {} };
  new Function('require', 'module', 'exports', bundled.outputFiles[0].text)(
    (name) => (name.startsWith('@/api/erp/') ? api : require(name)),
    mod,
    mod.exports
  );
  return mod.exports.default;
}
function register(app, withTable = true) {
  app.component('el-card', {
    setup(_props, { slots }) {
      return () => h('section', [slots.header?.(), slots.default?.()]);
    }
  });
  app.component('el-tag', {
    props: ['type'],
    setup(props, { slots }) {
      return () => h('span', { 'data-type': props.type }, slots.default?.());
    }
  });
  app.component('el-button', {
    props: ['loading', 'disabled'],
    setup(props, { slots, attrs }) {
      return () => h('button', { ...attrs, disabled: props.disabled || props.loading }, slots.default?.());
    }
  });
  app.component('el-empty', {
    props: ['description'],
    setup(props) {
      return () => h('aside', props.description);
    }
  });
  if (withTable) {
    app.component('el-table', { render: () => h('div') });
    app.component('el-table-column', { render: () => h('div') });
  }
}
async function render(actions, runStatus = 'SUCCEEDED') {
  const app = createSSRApp(await compileComponent(), { actions, runStatus });
  register(app);
  return renderToString(app);
}
const descendants = (node) => [node, ...(node.children ?? []).flatMap(descendants)];
function renderer() {
  return createRenderer({
    createElement: (tag) => ({ tag, props: {}, style: {}, children: [] }),
    createText: (text) => ({ tag: '#text', text, children: [] }),
    createComment: (text) => ({ tag: '#comment', text, children: [] }),
    setText: (node, text) => {
      node.text = text;
    },
    setElementText: (node, text) => {
      node.children = [{ tag: '#text', text, children: [] }];
    },
    patchProp: (node, key, _old, value) => {
      node.props[key] = value;
    },
    insert(node, parent, anchor) {
      if (node.parent) {
        const i = node.parent.children.indexOf(node);
        if (i >= 0) node.parent.children.splice(i, 1);
      }
      node.parent = parent;
      const i = anchor ? parent.children.indexOf(anchor) : -1;
      parent.children.splice(i < 0 ? parent.children.length : i, 0, node);
    },
    remove(node) {
      const i = node.parent?.children.indexOf(node) ?? -1;
      if (i >= 0) node.parent.children.splice(i, 1);
    },
    parentNode: (node) => node.parent,
    nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null
  });
}
const text = (node) => (node.tag === '#comment' ? '' : (node.text ?? '') + (node.children ?? []).map(text).join(''));
async function mount(actions, api, runStatus = 'SUCCEEDED') {
  const props = reactive({ actions, runStatus });
  const navigations = [];
  const Component = await compileComponent(api);
  const app = renderer().createApp({ setup: () => () => h(Component, { ...props, onNavigate: (route) => navigations.push(route) }) });
  register(app);
  const root = { tag: 'root', children: [] };
  app.mount(root);
  const button = (label) => descendants(root).find((node) => node.tag === 'button' && text(node).includes(label));
  const click = async (label) => {
    const node = button(label);
    assert.ok(node, `${label} 按钮必须存在`);
    assert.ok(!node.props.disabled);
    await node.props.onClick();
    await nextTick();
  };
  return { app, props, root, button, click, navigations };
}

test('实际详情 Vue 模板在审批面板消失后仍独立渲染每个动作结果', async () => {
  const path = resolve(uiRoot, 'src/views/ai/decision/detail.vue');
  const { descriptor, errors } = parse(readFileSync(path, 'utf8'), { filename: path });
  assert.deepEqual(errors, []);
  const resultNode = descendants(descriptor.template.ast).find((node) => node.tag === 'DecisionActionExecutionResults');
  assert.ok(resultNode, '详情必须有独立持久结果组件');
  assert.ok(!resultNode.props.some((prop) => prop.type === 7 && prop.name === 'if'), '结果区不能被 WAITING_APPROVAL 守卫隐藏');
  const compiled = compileTemplate({ source: descriptor.template.content, filename: path, id: 'f02-detail' });
  assert.deepEqual(compiled.errors, []);
  const result = await build({
    stdin: { contents: compiled.code, loader: 'js' },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    external: ['vue'],
    write: false
  });
  const mod = { exports: {} };
  new Function('require', 'module', 'exports', result.outputFiles[0].text)(require, mod, mod.exports);
  const ctx = {
    detail: {
      run: { status: 'PARTIAL_SUCCESS' },
      actions: [action(), action({ id: '12', title: '驳回动作', approvalStatus: 'REJECTED', executionStatus: 'SKIPPED' })]
    },
    loading: false,
    runId: '42',
    timeline: [],
    liveNodes: {},
    projected: null,
    result: null,
    outcome: null,
    isStale: false,
    submitting: false,
    evidenceOpen: false,
    evidence: [],
    previewOpen: false,
    preview: null,
    canApproveRun: () => false,
    refresh() {},
    download() {},
    recalculate() {},
    approve() {},
    reject() {},
    rejectWithoutActions() {},
    previewScenario() {},
    navigateEvidence() {},
    navigateResult() {}
  };
  const app = createSSRApp({ render: mod.exports.render, setup: () => ctx });
  register(app);
  app.directive('loading', { getSSRProps: () => ({}) });
  app.directive('hasPermi', { getSSRProps: () => ({}) });
  for (const name of [
    'DecisionProgressPanel',
    'DecisionSummaryCard',
    'DecisionKpiCompare',
    'DecisionScenarioTable',
    'DecisionRunTimeline',
    'DecisionEvidenceDrawer',
    'DecisionScenarioPreview'
  ])
    app.component(name, { render: () => h('div') });
  app.component('DecisionActionApproval', { render: () => h('div', '审批面板占位') });
  app.component('DecisionActionExecutionResults', await compileComponent());
  const markup = await renderToString(app);
  assert.match(markup, /真实采购申请动作/);
  assert.match(markup, /驳回动作/);
  assert.match(markup, /已驳回/);
  assert.doesNotMatch(markup, /审批面板占位/);
});
test('真实结果组件显示审批与执行时间/状态、无 target 不造链接', async () => {
  const markup = await render([
    action(),
    action({ id: '13', title: '失败动作', executionStatus: 'FAILED_PRECHECK', lastErrorCode: 'SNAPSHOT_STALE' })
  ]);
  assert.match(markup, /已批准/);
  assert.match(markup, /2026-10-07 12:00:00/);
  assert.match(markup, /SNAPSHOT_STALE/);
  assert.doesNotMatch(markup, /PR20261007001/);
  assert.match(markup, /9007199254740993/);
  assert.doesNotMatch(markup, /查看ERP记录/);
  const unknown = await render([action({ resultBizType: 'UNKNOWN' })]);
  assert.doesNotMatch(unknown, /回查ERP结果|查看ERP记录/);
});
test('真实组件的采购只读查询保留雪花字符串，实际状态回查后才可查看', async () => {
  const calls = [];
  const mounted = await mount([action()], {
    getPurchaseRequisition: async (value) => {
      calls.push(value);
      return { data: purchase };
    }
  });
  try {
    assert.equal(mounted.button('查看ERP记录'), undefined);
    await mounted.click('回查ERP结果');
    assert.deepEqual(calls, [id]);
    assert.match(text(mounted.root), /DRAFT/);
    await mounted.click('查看ERP记录');
    assert.deepEqual(mounted.navigations, [{ path: '/erp/pu/requisition/form', query: { id, action: 'view', decisionResultRequisitionId: id } }]);
  } finally {
    mounted.app.unmount();
  }
});
test('真实组件的排程回查严格 ID/编号匹配，不能把分页第一行当作真实结果', async () => {
  const calls = [];
  const mounted = await mount([scheduleAction()], {
    getScheduleGantt: async (value) => {
      calls.push(value);
      return { data: schedule };
    },
    listSchedule: async (query) => {
      calls.push(query);
      return { rows: [{ ...schedule, planId: '100' }, schedule] };
    }
  });
  try {
    await mounted.click('回查ERP结果');
    assert.equal(calls[0], id);
    assert.equal(calls[1].planNo, schedule.planNo);
    assert.match(text(mounted.root), /COMPLETED/);
    await mounted.click('查看ERP记录');
    assert.deepEqual(mounted.navigations, [{ path: '/erp/prd/schedule', query: { decisionResultPlanId: id, planNo: schedule.planNo } }]);
  } finally {
    mounted.app.unmount();
  }
});
for (const [label, response] of [
  ['权限拒绝', new Error('403 token=must-not-leak')],
  ['身份错配', { ...purchase, requisitionId: '100' }],
  ['空记录', null]
]) {
  test(`真实组件 ${label} 不导航、不暴露异常正文`, async () => {
    const mounted = await mount([action()], {
      getPurchaseRequisition: async () => {
        if (response instanceof Error) throw response;
        return { data: response };
      }
    });
    try {
      await mounted.click('回查ERP结果');
      assert.equal(mounted.button('查看ERP记录'), undefined);
      assert.deepEqual(mounted.navigations, []);
      assert.match(text(mounted.root), /回查未完成/);
      assert.doesNotMatch(text(mounted.root), /must-not-leak/);
    } finally {
      mounted.app.unmount();
    }
  });
}
test('查询期间动作变成补偿后，旧只读响应不能恢复成功或导航', async () => {
  let complete;
  const mounted = await mount([action()], {
    getPurchaseRequisition: () =>
      new Promise((resolve) => {
        complete = resolve;
      })
  });
  try {
    const pending = mounted.click('回查ERP结果');
    await nextTick();
    mounted.props.actions = [action({ executionStatus: 'COMPENSATED' })];
    await nextTick();
    complete({ data: purchase });
    await pending;
    assert.equal(mounted.button('查看ERP记录'), undefined);
    assert.equal(mounted.button('回查ERP结果'), undefined);
    assert.deepEqual(mounted.navigations, []);
    assert.match(text(mounted.root), /已补偿/);
  } finally {
    mounted.app.unmount();
  }
});
test('真实排程脚本消费字符串深链并精准过滤，不新增自动写入动作', () => {
  const path = resolve(uiRoot, 'src/views/erp/prd/schedule/index.vue');
  const { descriptor, errors } = parse(readFileSync(path, 'utf8'), { filename: path });
  assert.deepEqual(errors, []);
  assert.match(descriptor.scriptSetup.content, /decisionScheduleResultQuery\(route\.query\)/);
  assert.match(descriptor.scriptSetup.content, /decisionScheduleResultRows\(/);
  assert.doesNotMatch(descriptor.scriptSetup.content, /Number\(route\.query|parseInt\(route\.query/);
});

test('未知审批/执行状态包括原型键也必须安全降级，不崩溃或假成功', () => {
  const view = fn('decisionActionExecutionView');
  for (const unknown of ['__proto__', 'constructor', 'toString']) {
    const result = view(action({ approvalStatus: unknown, executionStatus: unknown }));
    assert.equal(result.approvalLabel, '未知审批状态');
    assert.equal(result.executionLabel, '未知执行状态');
    assert.notEqual(result.executionType, 'success');
  }
});
test('回查校验拒绝伪目标类型，不允许隐式退化为排程路由', () => {
  assert.equal(fn('verifiedDecisionErpResult')({ kind: '../../x', id }, schedule), null);
});
test('草稿执行成功与审批已驳回/未知的矛盾记录不能当作成功或可导航', () => {
  for (const approvalStatus of ['REJECTED', 'NOT_AUTHORIZED', 'UNKNOWN']) {
    const candidate = action({ approvalStatus });
    assert.equal(fn('decisionActionResultTarget')(candidate), null);
    assert.notEqual(fn('decisionActionExecutionView')(candidate).executionType, 'success');
  }
});
test('编号来自只读记录而非动作未声明字段或正文自称编号', async () => {
  const mounted = await mount([action({ resultBizCode: 'MODEL-FAKE', payloadJson: '{"requisitionNo":"MODEL-FAKE"}' })], {
    getPurchaseRequisition: async () => ({ data: purchase })
  });
  try {
    assert.doesNotMatch(text(mounted.root), /MODEL-FAKE/);
    await mounted.click('回查ERP结果');
    assert.match(text(mounted.root), /PR20261007001/);
    assert.doesNotMatch(text(mounted.root), /MODEL-FAKE/);
  } finally {
    mounted.app.unmount();
  }
});
test('排程两次只读结果编号不一致不能导航', async () => {
  const mounted = await mount([scheduleAction()], {
    getScheduleGantt: async () => ({ data: schedule }),
    listSchedule: async () => ({ rows: [{ ...schedule, planNo: 'OTHER' }] })
  });
  try {
    await mounted.click('回查ERP结果');
    assert.equal(mounted.button('查看ERP记录'), undefined);
    assert.deepEqual(mounted.navigations, []);
  } finally {
    mounted.app.unmount();
  }
});

async function mountSchedule(query) {
  const path = resolve(uiRoot, 'src/views/erp/prd/schedule/index.vue');
  const { descriptor, errors } = parse(readFileSync(path, 'utf8'), { filename: path });
  assert.deepEqual(errors, []);
  const compiled = compileScript(descriptor, { id: 'f02-schedule-mount', inlineTemplate: true });
  const bundled = await build({
    stdin: {
      contents: "import { ref, computed, getCurrentInstance, onMounted, onBeforeUnmount, watch, nextTick } from 'vue';\n" + compiled.content,
      resolveDir: dirname(path),
      sourcefile: path,
      loader: 'ts'
    },
    alias: { '@': resolve(uiRoot, 'src') },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    write: false,
    logLevel: 'silent',
    plugins: [
      {
        name: 'schedule-boundaries',
        setup(builder) {
          builder.onResolve({ filter: /^@\/api\// }, (args) => ({ path: args.path, external: true }));
        }
      }
    ],
    external: ['vue', 'vue-router', 'echarts', 'dayjs']
  });
  const route = reactive({ query });
  const calls = [];
  const writes = [];
  const filterControls = [];
  const records = [
    { ...schedule, strategy: 'EDD', planName: '真实结果', workshopId: '1001' },
    { ...schedule, planId: '100', strategy: 'EDD', planName: '同号方案', workshopId: '1001' },
    { planId: '200', planNo: 'OLD20261007', status: 'PUBLISHED', strategy: 'CR', planName: '旧已发布方案', workshopId: '1002' }
  ];
  const api = {
    listSchedule: async (params) => {
      calls.push({ ...params });
      /** 模拟实际后端条件：编号/名称模糊匹配，状态/策略/车间等值过滤。 */
      const rows = records.filter(
        (row) =>
          (!params.planNo || row.planNo.includes(params.planNo)) &&
          (!params.planName || row.planName.includes(params.planName)) &&
          (!params.status || row.status === params.status) &&
          (!params.strategy || row.strategy === params.strategy) &&
          (params.workshopId == null || row.workshopId === params.workshopId)
      );
      return { rows, total: rows.length };
    },
    getScheduleGantt: async () => {
      throw new Error('深链不自动查甘特/发布');
    },
    runSchedule: () => writes.push('run'),
    publishSchedule: () => writes.push('publish'),
    delSchedule: () => writes.push('delete'),
    runDeliveryRisk: () => writes.push('decision')
  };
  const mod = { exports: {} };
  new Function('require', 'module', 'exports', bundled.outputFiles[0].text)(
    (name) => {
      if (name.startsWith('@/api/')) return api;
      if (name === 'vue-router') return { useRoute: () => route, useRouter: () => ({ push: () => writes.push('navigate') }) };
      if (name === 'echarts')
        return {
          init: () => {
            throw new Error('深链不自动打开甘特图');
          }
        };
      return require(name);
    },
    mod,
    mod.exports
  );
  const app = renderer().createApp(mod.exports.default);
  app.config.globalProperties.$modal = {
    msgError() {},
    msgWarning() {},
    msgSuccess() {},
    confirm() {
      writes.push('confirm');
    }
  };
  app.directive('loading', {});
  app.directive('hasPermi', {});
  register(app, false);
  for (const name of [
    'el-form',
    'el-form-item',
    'el-row',
    'el-col',
    'el-dialog',
    'el-drawer',
    'el-tooltip',
    'el-text',
    'el-radio',
    'el-radio-group',
    'el-divider'
  ]) {
    app.component(name, {
      setup(_props, { slots }) {
        return () => h('div', slots.default?.());
      }
    });
  }
  for (const name of [
    'el-input',
    'el-option',
    'right-toolbar',
    'pagination',
    'el-table-column',
    'el-date-picker',
    'el-input-number',
    'el-alert',
    'el-progress'
  ]) {
    app.component(name, { render: () => h('div') });
  }
  app.component('el-select', {
    props: ['modelValue'],
    setup(props, { emit }) {
      filterControls.push({ set: (value) => emit('update:modelValue', value), current: () => props.modelValue });
      return () => h('div');
    }
  });
  app.component('el-table', {
    props: ['data'],
    setup(props, { expose }) {
      expose({ clearSelection() {} });
      return () => h('output', JSON.stringify(props.data));
    }
  });
  const root = { tag: 'root', children: [] };
  app.mount(root);
  await nextTick();
  await nextTick();
  const setFilters = async (strategy, status) => {
    filterControls[0].set(strategy);
    filterControls[1].set(status);
    await nextTick();
  };
  const search = async () => {
    const button = descendants(root).find((node) => node.tag === 'button' && text(node).trim() === '搜索');
    assert.ok(button, '必须通过真实模板的搜索按钮执行筛选');
    await button.props.onClick();
    await nextTick();
    await nextTick();
  };
  return { app, route, calls, writes, root, setFilters, search };
}
test('实际排程 SFC mount 与同页 query watch 都定位真实行而不触发写流程', async () => {
  const mounted = await mountSchedule({ decisionResultPlanId: id, planNo: schedule.planNo });
  try {
    assert.equal(mounted.calls[0].planNo, schedule.planNo);
    assert.match(text(mounted.root), /9007199254740993/);
    assert.doesNotMatch(text(mounted.root), /"planId":"100"/);
    mounted.route.query = { decisionResultPlanId: '100', planNo: schedule.planNo };
    await nextTick();
    await nextTick();
    assert.equal(mounted.calls.length, 2);
    assert.match(text(mounted.root), /"planId":"100"/);
    assert.doesNotMatch(text(mounted.root), /9007199254740993/);
    assert.deepEqual(mounted.writes, []);
  } finally {
    mounted.app.unmount();
  }
});
test('实际排程 SFC 不把数组/注入深链参数发送给查询服务', async () => {
  const mounted = await mountSchedule({ decisionResultPlanId: [id], planNo: '../../outside' });
  try {
    assert.equal(mounted.calls[0].planNo, undefined);
    assert.deepEqual(mounted.writes, []);
  } finally {
    mounted.app.unmount();
  }
});

test('采购只读深链身份必须与真实结果 ID 完全相同且不允许编辑/数组/数字', () => {
  const parseQuery = fn('decisionPurchaseResultQuery');
  const query = { id, action: 'view', decisionResultRequisitionId: id };
  assert.equal(parseQuery(query), id);
  for (const patch of [{ action: 'edit' }, { id: [id] }, { id: 42 }, { decisionResultRequisitionId: '100' }, { id: '../../x' }]) {
    assert.equal(parseQuery({ ...query, ...patch }), null);
  }
});
async function mountPurchase(query, get, componentProps = {}) {
  const path = resolve(uiRoot, 'src/views/erp/pu/requisition/form.vue');
  const { descriptor, errors } = parse(readFileSync(path, 'utf8'), { filename: path });
  assert.deepEqual(errors, []);
  const compiled = compileScript(descriptor, { id: 'f02-purchase-mount', inlineTemplate: true });
  const bundled = await build({
    stdin: {
      contents: "import { ref, computed, getCurrentInstance, toRefs, onMounted, onBeforeUnmount, watch, nextTick } from 'vue';\n" + compiled.content,
      resolveDir: dirname(path),
      sourcefile: path,
      loader: 'ts'
    },
    alias: { '@': resolve(uiRoot, 'src') },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    write: false,
    logLevel: 'silent',
    plugins: [
      {
        name: 'purchase-boundaries',
        setup(builder) {
          /** 父表单KeepAlive测试隔离新增对话框，专属组件另有真实挂载矩阵；纯协议仍真实bundle。 */
          builder.onResolve({ filter: /CertifiedRequisitionWorkflowDialog\.vue$/ }, (args) => ({ path: args.path, external: true }));
          builder.onResolve({ filter: /^@\/(api|components)\// }, (args) => ({ path: args.path, external: true }));
        }
      }
    ],
    external: ['vue', 'vue-router']
  });
  const route = reactive({ path: '/erp/pu/requisition/form', query });
  const calls = [];
  const writes = [];
  const alerts = [];
  const inputs = [];
  const api = {
    getPurchaseRequisition: (value) => {
      calls.push(value);
      return get(value);
    },
    getProcesss: async () => ({ data: [] }),
    updatePurchaseRequisition: () => writes.push('update'),
    convertPurchaseRequisition: () => writes.push('convert'),
    submitRequisitionFlow: () => writes.push('approve'),
    previewCode: () => writes.push('code')
  };
  const mod = { exports: {} };
  new Function('require', 'module', 'exports', bundled.outputFiles[0].text)(
    (name) => {
      if (name.startsWith('@/api/')) return api;
      if (name.startsWith('@/components/')) return { render: () => h('div') };
      if (name.endsWith('CertifiedRequisitionWorkflowDialog.vue')) return { render: () => h('div') };
      if (name === 'vue-router') return { useRoute: () => route, useRouter: () => ({ push: () => writes.push('navigate') }) };
      return require(name);
    },
    mod,
    mod.exports
  );
  const PurchaseForm = mod.exports.default;
  const OtherView = { name: '其他标签页', render: () => h('aside', '其他标签页') };
  /** 实际KeepAlive复用真实SFC，切回不重新执行onMounted。 */
  const app = renderer().createApp({
    setup: () => () =>
      h(KeepAlive, null, {
        default: () =>
          route.path === '/erp/pu/requisition/form'
            ? h(PurchaseForm, { ...componentProps, key: '采购表单缓存' })
            : h(OtherView, { key: '其他标签缓存' })
      })
  });
  app.config.globalProperties.useDict = () => reactive({ pu_purchase_requisition_status: [], pu_source_type: [] });
  app.config.globalProperties.$modal = {
    msgError: (message) => alerts.push(message),
    msgWarning: (message) => alerts.push(message),
    msgSuccess() {}
  };
  app.directive('loading', {});
  app.directive('hasPermi', {});
  const tags = new Set([...descriptor.template.content.matchAll(/<([A-Za-z][\w-]*)\b/g)].map((match) => match[1]));
  for (const name of tags)
    if ((name.includes('-') || /^[A-Z]/.test(name)) && name !== 'MaterialSelectDialog' && name !== 'el-table' && name !== 'el-input') {
      app.component(name, {
        props: ['modelValue', 'disabled'],
        setup(props, { slots }) {
          return () =>
            h('section', { 'data-disabled': props.disabled }, [
              props.modelValue == null ? '' : String(props.modelValue),
              slots.header?.(),
              name === 'el-table-column' ? [] : slots.default?.()
            ]);
        }
      });
    }
  app.component('el-input', {
    props: ['modelValue', 'disabled'],
    setup(props, { attrs, emit }) {
      inputs.push({ placeholder: attrs.placeholder, set: (value) => emit('update:modelValue', value), current: () => props.modelValue });
      return () => h('section', props.modelValue == null ? '' : String(props.modelValue));
    }
  });
  app.component('el-table', {
    props: ['data'],
    setup(props) {
      return () => h('output', JSON.stringify(props.data));
    }
  });
  const root = { tag: 'root', children: [] };
  app.mount(root);
  await nextTick();
  await nextTick();
  return { app, route, root, calls, writes, alerts, inputs };
}
test('实际采购 SFC 承接同页决策只读深链，不把缓存的第一张单当成第二张', async () => {
  const secondId = '9007199254740999';
  const second = { requisitionId: secondId, requisitionNo: 'PR20261007002', status: 'DRAFT' };
  const mounted = await mountPurchase({ id, action: 'view', decisionResultRequisitionId: id }, async (value) => ({
    data: value === id ? purchase : second
  }));
  try {
    assert.match(text(mounted.root), /PR20261007001/);
    mounted.route.query = { id: secondId, action: 'view', decisionResultRequisitionId: secondId };
    await nextTick();
    await nextTick();
    assert.deepEqual(mounted.calls, [id, secondId]);
    assert.match(text(mounted.root), /PR20261007002/);
    assert.doesNotMatch(text(mounted.root), /PR20261007001/);
    assert.deepEqual(mounted.writes, []);
  } finally {
    mounted.app.unmount();
  }
});
test('实际采购 SFC 不向 GET 发送伪造结果ID/编辑深链，不开放保存/提交按钮', async () => {
  const mounted = await mountPurchase({ id: '../../x', action: 'edit', decisionResultRequisitionId: id }, async () => ({ data: purchase }));
  try {
    assert.deepEqual(mounted.calls, []);
    assert.deepEqual(mounted.writes, []);
    assert.doesNotMatch(text(mounted.root), /保存|提交审批|PR20261007001/);
  } finally {
    mounted.app.unmount();
  }
});
test('实际采购 SFC 的旧响应及权限拒绝不恢复旧单据或输出异常正文', async () => {
  const pending = [];
  const secondId = '9007199254740999';
  const mounted = await mountPurchase(
    { id, action: 'view', decisionResultRequisitionId: id },
    () => new Promise((resolve, reject) => pending.push({ resolve, reject }))
  );
  try {
    mounted.route.query = { id: secondId, action: 'view', decisionResultRequisitionId: secondId };
    await nextTick();
    assert.equal(pending.length, 2);
    pending[1].reject(new Error('403 forbidden private-token'));
    await nextTick();
    await nextTick();
    pending[0].resolve({ data: purchase });
    await nextTick();
    await nextTick();
    assert.doesNotMatch(text(mounted.root), /PR20261007001|private-token/);
    assert.match(mounted.alerts.join(''), /回查未完成/);
    assert.deepEqual(mounted.writes, []);
  } finally {
    for (const request of pending) request.resolve({ data: purchase });
    mounted.app.unmount();
  }
});

for (const ordinaryAction of ['view', 'edit']) {
  for (const outcome of ['成功', '权限拒绝']) {
    test(`P2-1 普通${ordinaryAction}迟到A不能覆盖结果B${outcome}后的表单和明细`, async () => {
      const pending = [];
      const secondId = '9007199254740999';
      const ordinaryA = { ...manualPurchase, puPurchaseRequisitionLineList: [{ materialCode: '普通A行' }] };
      const resultB = {
        requisitionId: secondId,
        requisitionNo: 'PR20261007002',
        status: 'DRAFT',
        puPurchaseRequisitionLineList: [{ materialCode: '核验B行' }]
      };
      const mounted = await mountPurchase({ id, action: ordinaryAction }, () => new Promise((resolve, reject) => pending.push({ resolve, reject })));
      try {
        assert.deepEqual(mounted.calls, [id], '普通入口必须仍走原详情查询');
        mounted.route.query = { id: secondId, action: 'view', decisionResultRequisitionId: secondId };
        await nextTick();
        assert.deepEqual(mounted.calls, [id, secondId], '同组件进入结果B必须查询真实B');
        if (outcome === '成功') pending[1].resolve({ data: resultB });
        else pending[1].reject(new Error('403 private-token-should-not-leak'));
        await nextTick();
        await nextTick();
        if (outcome === '成功') assert.match(text(mounted.root), /PR20261007002.*核验B行/s, '必须先核验并展示真实B及明细');
        else assert.match(mounted.alerts.join(''), /回查未完成/, '权限拒绝保留失败提示');
        pending[0].resolve({ data: ordinaryA });
        await nextTick();
        await nextTick();
        assert.doesNotMatch(text(mounted.root), /PR20261007001|普通A行|private-token-should-not-leak/, '迟到普通A绝不能恢复单号或明细');
        if (outcome === '成功') assert.match(text(mounted.root), /PR20261007002.*核验B行/s, '旧普通响应不得覆盖核验B');
        assert.deepEqual(mounted.writes, [], '只读边界不得触发任何业务写操作');
      } finally {
        for (const request of pending) request.resolve({ data: ordinaryA });
        mounted.app.unmount();
      }
    });
  }
  test(`P2-1 普通${ordinaryAction}在原路由仍正常回写，不改变编辑/查看语义`, async () => {
    const mounted = await mountPurchase({ id, action: ordinaryAction }, async () => ({
      data: { ...manualPurchase, puPurchaseRequisitionLineList: [{ materialCode: '普通有效明细' }] }
    }));
    try {
      assert.match(text(mounted.root), /PR20261007001.*普通有效明细/s, '当前普通详情正常展示单据和明细');
      assert.equal(text(mounted.root).includes('保存'), ordinaryAction === 'edit', '普通edit保持可编辑，普通view保持只读');
      assert.deepEqual(mounted.writes, [], '验证加载不提交业务');
    } finally {
      mounted.app.unmount();
    }
  });
}
for (const disabled of [true, false]) {
  test(`P2-1 props流程嵌入disabled=${disabled}仍由原payload分支加载和更新`, async () => {
    const payload = reactive({ ...manualPurchase, requisitionNo: 'FLOW20261007001', puPurchaseRequisitionLineList: [{ materialCode: '流程明细' }] });
    const mounted = await mountPurchase(
      {},
      async () => {
        throw new Error('流程嵌入不得触发路由详情查询');
      },
      { disabled, customFormData: payload }
    );
    try {
      assert.deepEqual(mounted.calls, [], '无路由ID的流程嵌入只认props');
      assert.match(text(mounted.root), /FLOW20261007001.*流程明细/s, '原props的头行数据保持');
      payload.requisitionNo = 'FLOW20261007002';
      await nextTick();
      await nextTick();
      assert.match(text(mounted.root), /FLOW20261007002/, '原深层props更新仍生效');
      assert.equal(text(mounted.root).includes('保存'), !disabled, '原props禁用语义不改');
      assert.deepEqual(mounted.writes, []);
    } finally {
      mounted.app.unmount();
    }
  });
}
test('P2-2 实际排程已筛PUBLISHED/CR后合法结果深链必须定位COMPLETED/EDD真实方案', async () => {
  const mounted = await mountSchedule({});
  try {
    await mounted.setFilters('CR', 'PUBLISHED');
    await mounted.search();
    assert.equal(mounted.calls.at(-1).status, 'PUBLISHED', '普通状态筛选必须实际送到服务');
    assert.equal(mounted.calls.at(-1).strategy, 'CR', '普通策略筛选必须实际送到服务');
    assert.match(text(mounted.root), /OLD20261007/, 'stub必须依筛选返回实际旧方案');
    assert.doesNotMatch(text(mounted.root), /9007199254740993/, 'stub不得忽略筛选返回目标结果');
    mounted.route.query = { decisionResultPlanId: id, planNo: schedule.planNo };
    await nextTick();
    await nextTick();
    assert.equal(mounted.calls.at(-1).status, undefined, '仅合法深链移除旧状态筛选');
    assert.equal(mounted.calls.at(-1).strategy, undefined, '仅合法深链移除旧策略筛选');
    assert.equal(mounted.calls.at(-1).pageNum, 1, '结果定位从第一页开始');
    assert.equal(mounted.calls.at(-1).planNo, schedule.planNo, '只按核验的结果编号定位');
    assert.match(text(mounted.root), /9007199254740993/, '真实COMPLETED结果不能被旧筛选排除');
    assert.doesNotMatch(text(mounted.root), /OLD20261007|"planId":"100"/, '定位仍精确核对ID而非第一页同号方案');
    assert.deepEqual(mounted.writes, []);
  } finally {
    mounted.app.unmount();
  }
});
for (const query of [{ planNo: 'APS20261007001' }, { decisionResultPlanId: [id], planNo: schedule.planNo }]) {
  test(`P2-2 普通或非法结果route更新保留已有筛选：${JSON.stringify(query)}`, async () => {
    const mounted = await mountSchedule({});
    try {
      await mounted.setFilters('CR', 'PUBLISHED');
      await mounted.search();
      mounted.route.query = query;
      await nextTick();
      await nextTick();
      assert.equal(mounted.calls.at(-1).status, 'PUBLISHED', '普通/非法更新不能重置状态筛选');
      assert.equal(mounted.calls.at(-1).strategy, 'CR', '普通/非法更新不能重置策略筛选');
      assert.match(text(mounted.root), /OLD20261007/, '普通查询仍遵守原筛选');
      assert.deepEqual(mounted.writes, []);
    } finally {
      mounted.app.unmount();
    }
  });
}

for (const ordinaryAction of ['view', 'edit']) {
  for (const oldResponseTime of ['切出期间', '新响应之后']) {
    test(`质量回归 普通${ordinaryAction} pending→切出→KeepAlive返回，${oldResponseTime}迟到旧响应不能阻止恢复或覆盖新单`, async () => {
      const pending = [];
      const oldRecord = { ...manualPurchase, requisitionNo: 'OLD-PENDING', puPurchaseRequisitionLineList: [{ materialCode: '旧响应明细' }] };
      const newRecord = { ...manualPurchase, requisitionNo: 'RESTORED-CURRENT', puPurchaseRequisitionLineList: [{ materialCode: '恢复新明细' }] };
      const mounted = await mountPurchase({ id, action: ordinaryAction }, () => new Promise((resolve, reject) => pending.push({ resolve, reject })));
      try {
        assert.deepEqual(mounted.calls, [id], '首次普通详情请求仍按合法ID查询');
        mounted.route.path = '/erp/so/orderheader';
        mounted.route.query = { id: '1000', action: 'view' };
        await nextTick();
        await nextTick();
        assert.match(text(mounted.root), /其他标签页/, '必须实际切出并缓存普通表单');
        assert.deepEqual(mounted.calls, [id], '其他path的业务ID绝不能变成采购详情查询');
        if (oldResponseTime === '切出期间') {
          pending[0].resolve({ data: oldRecord });
          await nextTick();
          await nextTick();
        }
        mounted.route.path = '/erp/pu/requisition/form';
        mounted.route.query = { id, action: ordinaryAction };
        await nextTick();
        await nextTick();
        assert.deepEqual(mounted.calls, [id, id], '返回尚未载入的普通身份必须恢复只读查询，而非永久空表单');
        pending[1].resolve({ data: newRecord });
        await nextTick();
        await nextTick();
        if (oldResponseTime === '新响应之后') {
          pending[0].resolve({ data: oldRecord });
          await nextTick();
          await nextTick();
        }
        assert.match(text(mounted.root), /RESTORED-CURRENT.*恢复新明细/s, '恢复的新响应必须填充当前头行');
        assert.doesNotMatch(text(mounted.root), /OLD-PENDING|旧响应明细/, '安全序列及路由守卫仍阻断旧响应');
        assert.equal(text(mounted.root).includes('保存'), ordinaryAction === 'edit', '恢复保持普通查看/编辑语义');
        assert.deepEqual(mounted.writes, [], '标签恢复只能读，不能提交业务');
      } finally {
        for (const request of pending) request.resolve({ data: oldRecord });
        mounted.app.unmount();
      }
    });
  }
  test(`质量回归 已加载普通${ordinaryAction}切标签返回不重查，${ordinaryAction === 'edit' ? '未保存修改不被抹掉' : '只读内容保持'}`, async () => {
    const mounted = await mountPurchase({ id, action: ordinaryAction }, async () => ({ data: { ...manualPurchase, requisitionName: '原申请名称' } }));
    try {
      if (ordinaryAction === 'edit') {
        const input = mounted.inputs.find((item) => item.placeholder === '请输入申请名称');
        assert.ok(input, '通过真实模板v-model输入未保存名称');
        input.set('未保存修改-保留');
        await nextTick();
        assert.match(text(mounted.root), /未保存修改-保留/);
      }
      mounted.route.path = '/im/chat';
      mounted.route.query = {};
      await nextTick();
      await nextTick();
      mounted.route.path = '/erp/pu/requisition/form';
      mounted.route.query = { id, action: ordinaryAction };
      await nextTick();
      await nextTick();
      assert.deepEqual(mounted.calls, [id], '已载入身份切标签返回不能每次重查');
      assert.match(text(mounted.root), ordinaryAction === 'edit' ? /未保存修改-保留/ : /原申请名称/, '缓存表单当前内容必须保留');
      assert.equal(text(mounted.root).includes('保存'), ordinaryAction === 'edit', '已加载普通模式保持');
      assert.deepEqual(mounted.writes, []);
    } finally {
      mounted.app.unmount();
    }
  });
  test(`质量回归 决策结果返回合法普通${ordinaryAction}必须重新加载普通单据并恢复非结果模式`, async () => {
    const ordinaryId = '9007199254740999';
    const ordinary = {
      ...manualPurchase,
      requisitionId: ordinaryId,
      requisitionNo: 'ORDINARY-RETURN',
      puPurchaseRequisitionLineList: [{ materialCode: '普通返回明细' }]
    };
    const mounted = await mountPurchase({ id, action: 'view', decisionResultRequisitionId: id }, async (value) => ({
      data: value === id ? purchase : ordinary
    }));
    try {
      assert.match(text(mounted.root), /决策结果只读/);
      mounted.route.query = { id: ordinaryId, action: ordinaryAction };
      await nextTick();
      await nextTick();
      assert.deepEqual(mounted.calls, [id, ordinaryId], '不能把前一张结果当作普通返回单据');
      assert.match(text(mounted.root), /ORDINARY-RETURN.*普通返回明细/s, '真实普通头行必须恢复');
      assert.doesNotMatch(text(mounted.root), /决策结果只读|PR20261007001/, '结果只读标题和旧单不得残留');
      assert.equal(text(mounted.root).includes('保存'), ordinaryAction === 'edit', '普通edit必须恢复编辑，view保持只读');
      assert.deepEqual(mounted.writes, []);
    } finally {
      mounted.app.unmount();
    }
  });
}
for (const query of [
  { action: 'view' },
  { id: [id], action: 'view' },
  { id: '../../x', action: 'edit' },
  { id: 42, action: 'view' },
  { id: '9223372036854775808', action: 'view' },
  { id, action: 'UNKNOWN' },
  { action: 'add' },
  { id, action: 'add' }
]) {
  test(`质量回归 非法/新增普通入口不得误发详情GET：${JSON.stringify(query)}`, async () => {
    const mounted = await mountPurchase(query, async () => ({ data: purchase }));
    try {
      assert.deepEqual(mounted.calls, [], '新增/非法入口不能查询其他详情');
      assert.deepEqual(mounted.writes, [], '不能触发任何业务写入');
    } finally {
      mounted.app.unmount();
    }
  });
}
for (const disabled of [true, false]) {
  test(`质量回归 props流程嵌入disabled=${disabled}切标签及带普通参数返回不抢占payload`, async () => {
    const payload = reactive({
      ...manualPurchase,
      requisitionNo: 'FLOW-KEEPALIVE',
      puPurchaseRequisitionLineList: [{ materialCode: '流程保持明细' }]
    });
    const mounted = await mountPurchase({}, async () => ({ data: { ...purchase, requisitionNo: '错误路由单据' } }), {
      disabled,
      customFormData: payload
    });
    try {
      mounted.route.path = '/workflow/process';
      mounted.route.query = { id: '1000', action: 'view' };
      await nextTick();
      await nextTick();
      mounted.route.path = '/erp/pu/requisition/form';
      mounted.route.query = { id, action: 'edit' };
      await nextTick();
      await nextTick();
      assert.deepEqual(mounted.calls, [], '流程props分支不能被普通路由恢复查询抢占');
      payload.requisitionNo = 'FLOW-STILL-UPDATES';
      await nextTick();
      await nextTick();
      assert.match(text(mounted.root), /FLOW-STILL-UPDATES.*流程保持明细/s, '原props深层更新继续生效');
      assert.equal(text(mounted.root).includes('保存'), !disabled, '原props禁用语义不改');
      assert.deepEqual(mounted.writes, []);
    } finally {
      mounted.app.unmount();
    }
  });
}

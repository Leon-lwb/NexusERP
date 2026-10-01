import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { compileScript, parse } from '@vue/compiler-sfc';
import { createRenderer, h, nextTick, shallowRef } from 'vue';

const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const helperPath = resolve(uiRoot, 'src/views/ai/decision/decisionDisplay.ts');
const boardPath = 'src/views/ai/decision/index.vue';
const detailPath = 'src/views/ai/decision/detail.vue';
const summaryPath = 'src/components/ai/decision/DecisionSummaryCard.vue';
const drawerPath = 'src/components/ai/decision/DecisionEvidenceDrawer.vue';
const snowflakeId = '9007199254753012345'; /** 刻意使用与上报记录不同的 ID。 */
let helperModule;

async function displayHelper() {
  /** 在每个测试内断言：辅助函数缺失属于行为失败，而不是顶层导入错误。 */
  assert.ok(existsSync(helperPath), 'the agreed pure decisionDisplay.ts helper must exist');
  helperModule ??= (async () => {
    const output = await build({ entryPoints: [helperPath], bundle: true, write: false, format: 'esm', platform: 'neutral', logLevel: 'silent' });
    return import(`data:text/javascript;base64,${Buffer.from(output.outputFiles[0].contents).toString('base64')}`);
  })();
  const result = await helperModule;
  assert.equal(typeof result.decisionEvidenceEmptyDescription, 'function', 'export decisionEvidenceEmptyDescription(status)');
  assert.equal(typeof result.decisionBusinessObjectDisplay, 'function', 'export decisionBusinessObjectDisplay(run)');
  return result;
}

function sfc(path) {
  const source = readFileSync(resolve(uiRoot, path), 'utf8');
  const { descriptor, errors } = parse(source, { filename: path });
  assert.equal(errors.length, 0, `${path} must have a parseable Vue template`);
  assert.ok(descriptor.template?.ast, `${path} must have a Vue template`);
  return { source, template: descriptor.template.ast, script: descriptor.scriptSetup?.content ?? '' };
}

function descendants(node, predicate) {
  const found = [];
  function visit(current) {
    if (predicate(current)) found.push(current);
    for (const child of current.children ?? []) visit(child);
  }
  visit(node);
  return found;
}

function elements(node, tag) {
  return descendants(node, (child) => child.type === 1 && child.tag === tag);
}

function staticProp(node, name) {
  return node.props.find((p) => p.type === 6 && p.name === name)?.value?.content;
}

function binding(node, name) {
  return node.props.find((p) => p.type === 7 && p.name === 'bind' && p.arg?.content === name)?.exp?.content;
}

function fragment(sfcFile, node) {
  return sfcFile.source.slice(node.loc.start.offset, node.loc.end.offset);
}

function expressions(node) {
  const all = [];
  descendants(node, (current) => {
    if (current.type === 5) all.push(current.content.content);
    if (current.type === 1) {
      for (const prop of current.props) if (prop.type === 7 && prop.exp?.content) all.push(prop.exp.content);
    }
    return false;
  });
  return all;
}

function businessColumn(board, tableName, columnLabel) {
  const table = elements(board.template, 'el-table').find((node) => binding(node, 'data') === tableName);
  assert.ok(table, `${tableName} table must be present`);
  const column = elements(table, 'el-table-column').find((node) => staticProp(node, 'label') === columnLabel);
  assert.ok(column, `${tableName} table must include its ${columnLabel} column`);
  return column;
}

test('CREATED and RUNNING with no evidence ask to wait for analysis', async () => {
  const { decisionEvidenceEmptyDescription: describe } = await displayHelper();
  for (const status of ['CREATED', 'RUNNING']) {
    const message = describe(status);
    assert.match(message, /分析/, `${status}: refer to analysis`);
    assert.match(message, /等待|进行中|尚未|分析中|稍后/, `${status}: indicate analysis is pending`);
    assert.doesNotMatch(message, /分析.*(?:结束|完成|失败)/, `${status}: analysis has not ended`);
  }
});

test('FAILED with no evidence explains failure rather than asking to keep waiting', async () => {
  const { decisionEvidenceEmptyDescription: describe } = await displayHelper();
  const message = describe('FAILED');
  assert.match(message, /分析.*失败|失败.*分析/, 'failed analysis must be identified');
  assert.match(message, /证据/, 'explain the lack of evidence');
  assert.doesNotMatch(message, /等待|待.*完成|尚未.*完成|分析中|稍后.*刷新/, 'failed analysis must not appear active');
});

test('all other known post-analysis states mean analysis ended without evidence, not workflow ended', async () => {
  const { decisionEvidenceEmptyDescription: describe } = await displayHelper();
  const afterAnalysis = [
    'WAITING_APPROVAL',
    'APPROVED',
    'EXECUTING',
    'SUCCEEDED',
    'PARTIAL_SUCCESS',
    'STALE',
    'SUPERSEDED',
    'REJECTED',
    'ROLLING_BACK',
    'ROLLED_BACK',
    'CANCELLED'
  ];
  for (const status of afterAnalysis) {
    const message = describe(status);
    assert.match(message, /分析.*(?:结束|完成)/, `${status}: only the analysis is over`);
    assert.match(message, /证据/, `${status}: mention missing evidence`);
    assert.doesNotMatch(message, /等待|(?:整体|整个|审批|执行|流程|决策).*?(?:结束|完成)/, `${status}: do not claim approval/execution ended`);
  }
});

test('unknown, absent, and blank states have neutral no-evidence/status-unknown copy', async () => {
  const { decisionEvidenceEmptyDescription: describe } = await displayHelper();
  for (const status of [undefined, null, '', '  ', 'FUTURE_STATE']) {
    const message = describe(status);
    assert.match(message, /证据/, `${String(status)}: mention evidence`);
    assert.match(message, /未知|待确认|无法确定|未明确|不明/, `${String(status)}: status is unknown`);
    assert.doesNotMatch(message, /等待|分析中|分析.*(?:完成|结束|失败)/, `${String(status)}: must not invent progress`);
  }
});

test('WORK_ORDER with missing code shows no fake number or naked 19-digit ID', async () => {
  const { decisionBusinessObjectDisplay: show } = await displayHelper();
  const result = show({ bizType: 'WORK_ORDER', bizCode: null, bizId: snowflakeId });
  assert.deepEqual(result, { label: '生产工单编号暂不可用', internalIdText: `内部 ID：${snowflakeId}` });
  assert.ok(!result.label.includes(snowflakeId), 'business label must not expose the internal ID');
});

test('each business type and unknown/missing type maps to the exact Chinese fallback', async () => {
  const { decisionBusinessObjectDisplay: show } = await displayHelper();
  for (const [bizType, typeName] of [
    ['WORK_ORDER', '生产工单'],
    ['SALES_ORDER', '销售订单'],
    ['SCHEDULE_PLAN', '排产计划'],
    ['SHORTAGE_HORIZON', '缺料分析时段'],
    ['FUTURE_TYPE', '业务对象'],
    [null, '业务对象'],
    [undefined, '业务对象']
  ]) {
    assert.equal(show({ bizType, bizCode: '', bizId: snowflakeId }).label, `${typeName}编号暂不可用`, String(bizType));
  }
});

test('undefined, null, empty, and whitespace-only bizCode all require a business-type fallback', async () => {
  const { decisionBusinessObjectDisplay: show } = await displayHelper();
  for (const bizCode of [undefined, null, '', '   ', '\t\n']) {
    assert.deepEqual(
      show({ bizType: 'SALES_ORDER', bizCode, bizId: snowflakeId }),
      {
        label: '销售订单编号暂不可用',
        internalIdText: `内部 ID：${snowflakeId}`
      },
      JSON.stringify(bizCode)
    );
  }
});

test('nonblank real bizCode wins verbatim, even with digits, symbols or surrounding spaces', async () => {
  const { decisionBusinessObjectDisplay: show } = await displayHelper();
  for (const bizCode of ['WO-007/9007199254753012345#A', '  SCGD-017  ']) {
    assert.deepEqual(show({ bizType: 'WORK_ORDER', bizCode, bizId: snowflakeId }), {
      label: bizCode,
      internalIdText: `内部 ID：${snowflakeId}`
    });
  }
});

test('missing internal ID does not invent a work order ID or a global horizon', async () => {
  const { decisionBusinessObjectDisplay: show } = await displayHelper();
  for (const bizId of [undefined, null, '', '  ']) {
    assert.deepEqual(show({ bizType: 'WORK_ORDER', bizCode: null, bizId }), {
      label: '生产工单编号暂不可用',
      internalIdText: ''
    });
  }
  assert.deepEqual(show({ bizType: 'WORK_ORDER', bizCode: 'WO-007' }), { label: 'WO-007', internalIdText: '' });
});

test('displaying a business object never mutates the supplied run', async () => {
  const { decisionBusinessObjectDisplay: show } = await displayHelper();
  const run = Object.freeze({ bizType: 'WORK_ORDER', bizCode: '  WO-017  ', bizId: snowflakeId });
  assert.deepEqual(show(run), { label: run.bizCode, internalIdText: `内部 ID：${snowflakeId}` });
  assert.deepEqual(run, { bizType: 'WORK_ORDER', bizCode: '  WO-017  ', bizId: snowflakeId });
});

test('parsed resultJson businessRef.code is displayed when run.bizCode is null', async () => {
  const { decisionBusinessObjectDisplay: show } = await displayHelper();
  const run = Object.freeze({ bizType: 'WORK_ORDER', bizCode: null, bizId: snowflakeId });
  const resultJson = JSON.stringify({ businessRef: { code: 'WO-2026-UNIQUE-482' } });
  const parsedResult = JSON.parse(resultJson);
  assert.deepEqual(
    show(run, parsedResult.businessRef.code),
    {
      label: 'WO-2026-UNIQUE-482',
      internalIdText: `内部 ID：${snowflakeId}`
    },
    'trust parsed result businessRef.code rather than replacing it with a fallback or internal ID'
  );
  assert.equal(run.bizCode, null, 'display must not change the run');
});

test('nonblank trusted reference code wins over run.bizCode verbatim, including surrounding whitespace', async () => {
  const { decisionBusinessObjectDisplay: show } = await displayHelper();
  const run = Object.freeze({ bizType: 'SALES_ORDER', bizCode: 'SO-RUN-042', bizId: snowflakeId });
  const referenceCode = '  SO-RESULT-991  ';
  assert.deepEqual(show(run, referenceCode), {
    label: referenceCode,
    internalIdText: `内部 ID：${snowflakeId}`
  });
  assert.equal(run.bizCode, 'SO-RUN-042', 'display must not mutate the input run');
  assert.equal(referenceCode, '  SO-RESULT-991  ', 'do not trim a valid reference code for display');
});

test('missing or blank reference code falls through to real run code, then the original type fallback', async () => {
  const { decisionBusinessObjectDisplay: show } = await displayHelper();
  const run = Object.freeze({ bizType: 'WORK_ORDER', bizCode: '  WO-RUN-145  ', bizId: snowflakeId });
  const noCode = Object.freeze({ bizType: 'WORK_ORDER', bizCode: '  ', bizId: snowflakeId });
  for (const referenceCode of [undefined, null, '', '   ', '\t\n']) {
    assert.deepEqual(
      show(run, referenceCode),
      {
        label: run.bizCode,
        internalIdText: `内部 ID：${snowflakeId}`
      },
      `${JSON.stringify(referenceCode)} falls back to the unchanged run code`
    );
    assert.deepEqual(
      show(noCode, referenceCode),
      {
        label: '生产工单编号暂不可用',
        internalIdText: `内部 ID：${snowflakeId}`
      },
      `${JSON.stringify(referenceCode)} without run code retains the original Chinese fallback`
    );
  }
  assert.deepEqual(show(run), { label: run.bizCode, internalIdText: `内部 ID：${snowflakeId}` }, 'one-argument API remains identical');
  assert.equal(noCode.bizCode, '  ', 'input must not be mutated');
});

test('decision board imports the single agreed business display helper', () => {
  const { script } = sfc(boardPath);
  assert.match(script, /\bdecisionBusinessObjectDisplay\b/, 'board must use the shared display function');
  assert.match(script, /from\s*['"]\.\/decisionDisplay(?:\.ts)?['"]/, 'board must import it from decisionDisplay.ts');
});

for (const [tableName, columnLabel] of [
  ['highRisk', '业务编号'],
  ['rows', '业务对象']
]) {
  test(`${tableName} business column uses the shared label and a labeled-ID tooltip, never naked bizId`, () => {
    const board = sfc(boardPath);
    const column = businessColumn(board, tableName, columnLabel);
    const cell = fragment(board, column);
    assert.doesNotMatch(cell, /\bscope\.row\.bizId\b/, `${tableName}: never use naked bizId as a business label`);
    assert.ok(
      expressions(column).some((value) => /\.label\b/.test(value) && /\bscope\.row\b/.test(value)),
      `${tableName}: render the display helper's label for this row`
    );
    const tooltips = descendants(column, (node) => node.type === 1 && (node.tag === 'el-tooltip' || binding(node, 'title') != null));
    assert.ok(
      tooltips.some((node) =>
        [binding(node, 'content'), binding(node, 'title')].some(
          (value) => /\binternalIdText\b/.test(value ?? '') && /\bscope\.row\b/.test(value ?? '')
        )
      ),
      `${tableName}: put the separately labeled internalIdText in this row's tooltip`
    );
  });
}

test('evidence drawer does not hardcode a waiting description for every empty state', () => {
  const { template } = sfc(drawerPath);
  const empty = elements(template, 'el-empty')[0];
  assert.ok(empty, 'empty evidence must have an empty-state component');
  assert.equal(staticProp(empty, 'description'), undefined, 'empty description cannot be the same fixed waiting text for FAILED');
});

test('evidence drawer receives status and uses the shared empty-description helper only for empty evidence', () => {
  const drawer = sfc(drawerPath);
  const empty = elements(drawer.template, 'el-empty')[0];
  assert.ok(empty, 'drawer must have an empty-evidence state');
  assert.match(drawer.script, /\bstatus\??\s*:/, 'drawer must declare a status prop');
  const description = binding(empty, 'description');
  assert.ok(description, 'empty-state description must depend on status rather than be static');
  assert.match(`${drawer.script}\n${description}`, /\bdecisionEvidenceEmptyDescription\b/, 'empty description must use the shared status rule');
  assert.match(`${drawer.script}\n${description}`, /\bstatus\b/, 'status must reach the empty-description rule');
  assert.ok(
    expressions(empty).some((value) => value === description),
    'dynamic copy must belong to the zero-evidence state'
  );
});

test('evidence with contents still groups by domain/type and guards navigation', () => {
  const drawer = sfc(drawerPath);
  assert.ok(elements(drawer.template, 'el-collapse').length, 'nonempty evidence should remain grouped');
  assert.match(drawer.script, /\$\{item\.sourceDomain\}\/\$\{item\.sourceType\}/, 'group key retains source domain and type');
  assert.match(drawer.script, /\bsafeInternalPath\b/, 'navigation must still validate the internal path');
  assert.match(drawer.script, /emit\(['"]navigate['"]/, 'validated navigation must still emit the path');
});

test('decision detail passes its actual run.status into the evidence drawer', () => {
  const { template } = sfc(detailPath);
  const drawer = elements(template, 'DecisionEvidenceDrawer')[0];
  assert.ok(drawer, 'detail must keep its evidence drawer');
  assert.equal(binding(drawer, 'status'), 'detail.run.status', 'pass the real detail.run.status, not a fixed/guessed status');
});

test('shared SummaryCard passes the parsed businessRef.code and real run into the display helper', () => {
  const summary = sfc(summaryPath);
  assert.match(summary.script, /from\s*['"]@\/views\/ai\/decision\/decisionDisplay(?:\.ts)?['"]/, 'import the shared display rule');
  assert.match(
    summary.script,
    /\bresult\s*=\s*computed[\s\S]*?JSON\.parse\(props\.run\.run\.resultJson\)[\s\S]*?catch\s*\{[\s\S]*?return null;/,
    'resultJson is parsed defensively and invalid or absent results remain null'
  );
  assert.match(
    summary.script,
    /\bdecisionBusinessObjectDisplay\s*\(\s*props\.run\.run\s*,\s*result\.value\?\.businessRef\?\.code\s*\)/,
    'computed display receives BOTH the actual run and parsed result.businessRef.code'
  );
  const card = elements(summary.template, 'el-card').find((node) => staticProp(node, 'class')?.split(/\s+/).includes('decision-summary'));
  assert.ok(card, 'shared decision-summary card must remain');
  const visible = expressions(card);
  assert.ok(
    visible.some((value) => /\.label\b/.test(value) && /businessObject/.test(value)),
    'render the computed helper label'
  );
  assert.ok(
    visible.some((value) => /\.internalIdText\b/.test(value) && /businessObject/.test(value)),
    'render a separately labeled internal ID'
  );
  assert.doesNotMatch(fragment(summary, card), /\brun\.run\.bizId\b|全局时段|\brun\.run\.bizCode\b/, 'do not display naked ID or invent a fallback');
});

test('shared SummaryCard preserves evidence/recalculate events, risk, confidence, failure and narrative', () => {
  const summary = sfc(summaryPath);
  const card = elements(summary.template, 'el-card').find((node) => staticProp(node, 'class')?.split(/\s+/).includes('decision-summary'));
  assert.ok(card, 'shared summary card exists');
  const markup = fragment(summary, card);
  assert.match(markup, /交付风险摘要/, 'retain original header');
  assert.match(markup, /查看证据[\s\S]*重新分析/, 'retain controls');
  assert.match(markup, /emit\(['"]open-evidence['"]\)/, 'evidence button emits open-evidence');
  assert.match(markup, /emit\(['"]recalculate['"]\)/, 'recalculate button emits recalculate');
  for (const field of ['riskTagType', 'decisionRiskLabel', 'riskScore', 'confidenceLevel', 'decisionSummaryText', 'executiveSummary', 'warnings']) {
    assert.ok(markup.includes(field), `shared summary retains ${field}`);
  }
  assert.match(markup, /status\s*===\s*['"]FAILED['"]/, 'FAILED status alert remains');
  assert.match(markup, /<el-alert/, 'failure and warning alerts remain visible');
});

test('decision detail reuses exactly one shared SummaryCard, not duplicated inline markup', () => {
  const detail = sfc(detailPath);
  const cards = elements(detail.template, 'DecisionSummaryCard');
  assert.equal(cards.length, 1, 'only one shared SummaryCard receives the detail');
  const card = cards[0];
  assert.equal(binding(card, 'run'), 'detail', 'pass the whole real DecisionRunDetail');
  assert.equal(staticProp(card, 'class'), 'mb-4', 'retain summary spacing');
  const onEvent = (name) => card.props.find((p) => p.type === 7 && p.name === 'on' && p.arg?.content === name)?.exp?.content;
  assert.match(onEvent('open-evidence') ?? '', /^evidenceOpen\s*=\s*true$/, 'wire evidence event to existing drawer state');
  assert.equal(onEvent('recalculate'), 'recalculate', 'wire recalculate event to existing action');
  assert.match(
    detail.script,
    /import\s+DecisionSummaryCard\s+from\s+['"]@\/components\/ai\/decision\/DecisionSummaryCard\.vue['"]/,
    'import shared component'
  );
  assert.equal(
    elements(detail.template, 'el-card').filter((node) => /decision-summary|交付风险摘要/.test(fragment(detail, node))).length,
    0,
    'remove the duplicated inline summary and header/controls'
  );
  assert.doesNotMatch(
    detail.script,
    /\bdecisionBusinessObjectDisplay\b|\bbusinessObject\s*=|\bdecisionRiskLabel\b|\briskTagType\b|\bdecisionSummaryText\b/,
    'do not retain inline-only display/risk/summary imports or computed'
  );
  assert.ok(elements(detail.template, 'DecisionKpiCompare').length, 'other detail results remain rendered');
  assert.ok(elements(detail.template, 'DecisionEvidenceDrawer').length, 'evidence drawer remains');
});

const summaryRequire = createRequire(import.meta.url);
const summaryRunId = '9007199254753012346';
const noWorkOrderCode = 'NO_ANALYZABLE_WORK_ORDER';
const genericFailureTitle = '本次分析失败，可点击重新分析';
const originalFailedSummary = '分析失败，请重新分析；如持续失败，请提供运行 ID 联系管理员。';
const noWorkOrderReason = '当前决策范围没有可分析工单，不能发布风险结论。';
const noWorkOrderNextStep = '请选择包含未完工工单的新排程再分析。';
const safeNoWorkOrderFailure = noWorkOrderReason + noWorkOrderNextStep;
/** 全部敏感标记均为合成数据，不读取登录令牌、配置或真实业务数据。 */
const unsafeFailureText =
  'Bearer SYNTHETIC_ERROR_CANARY; SELECT SYNTHETIC_PRIVATE_COLUMN FROM SYNTHETIC_PRIVATE_TABLE; ' +
  '<script>__synthetic_decision_script__()</script>; MODEL_RAW_FAILURE_CANARY';
let summaryComponentPromise;

/** 编译仓库中的真实摘要卡片，保留 Vue 运行时；不写构建文件，也不替换业务逻辑。 */
function compiledSummaryCard() {
  summaryComponentPromise ??= (async () => {
    const componentPath = resolve(uiRoot, summaryPath);
    const { descriptor, errors } = parse(readFileSync(componentPath, 'utf8'), { filename: componentPath });
    assert.deepEqual(errors, [], 'the real SummaryCard SFC must parse');
    const compiled = compileScript(descriptor, { id: 'decision-summary-failure-reason', inlineTemplate: true });
    const output = await build({
      stdin: { contents: compiled.content, resolveDir: dirname(componentPath), sourcefile: componentPath, loader: 'ts' },
      alias: { '@': resolve(uiRoot, 'src') },
      bundle: true,
      platform: 'node',
      format: 'cjs',
      external: ['vue'],
      write: false,
      logLevel: 'silent'
    });
    const mod = { exports: {} };
    new Function('require', 'module', 'exports', output.outputFiles[0].text)(summaryRequire, mod, mod.exports);
    return mod.exports.default;
  })();
  return summaryComponentPromise;
}

function summaryHost(tag, text = '') {
  return { tag, text, props: {}, children: [], parent: null };
}

/** 使用真实 Vue renderer 的离线宿主树，沿用审批门禁测试的组件桩与事件落地方式。 */
function summaryRenderer() {
  return createRenderer({
    createElement: (tag) => summaryHost(tag),
    createText: (text) => summaryHost('#text', text),
    createComment: (text) => summaryHost('#comment', text),
    setText: (node, text) => {
      node.text = text;
    },
    setElementText: (node, text) => {
      node.text = text;
      node.children = [];
    },
    patchProp: (node, key, _previous, value) => {
      node.props[key] = value;
    },
    insert: (child, parent, anchor) => {
      if (child.parent) {
        const previous = child.parent.children.indexOf(child);
        if (previous !== -1) child.parent.children.splice(previous, 1);
      }
      child.parent = parent;
      const position = anchor ? parent.children.indexOf(anchor) : -1;
      parent.children.splice(position < 0 ? parent.children.length : position, 0, child);
    },
    remove: (child) => {
      const index = child.parent?.children.indexOf(child) ?? -1;
      if (index !== -1) child.parent.children.splice(index, 1);
      child.parent = null;
    },
    parentNode: (node) => node.parent,
    nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null
  });
}

function summaryNodes(root, predicate) {
  const nodes = [];
  function visit(node) {
    if (predicate(node)) nodes.push(node);
    for (const child of node.children) visit(child);
  }
  visit(root);
  return nodes;
}

function summaryText(node) {
  return node.tag === '#comment' ? '' : node.text + node.children.map(summaryText).join('');
}

function summaryDetail(overrides = {}) {
  return Object.freeze({
    run: Object.freeze({
      id: summaryRunId,
      bizId: snowflakeId,
      bizCode: 'SCHEDULE-FIXTURE-001',
      bizType: 'SCHEDULE_PLAN',
      sceneCode: 'DELIVERY_RISK',
      status: 'FAILED',
      version: 1,
      riskScore: 64,
      riskLevel: 'HIGH',
      confidenceLevel: 'HIGH',
      ...overrides
    }),
    evidence: Object.freeze([]),
    actions: Object.freeze([])
  });
}

async function mountSummary(detail) {
  const SummaryCard = await compiledSummaryCard();
  const root = summaryHost('root');
  const events = [];
  const currentDetail = shallowRef(detail);
  const app = summaryRenderer().createApp({
    setup() {
      return () =>
        h(SummaryCard, {
          run: currentDetail.value,
          onOpenEvidence: () => events.push('open-evidence'),
          onRecalculate: () => events.push('recalculate')
        });
    }
  });
  app.component('el-card', {
    setup(_props, { slots }) {
      return () => h('section', [slots.header?.(), slots.default?.()]);
    }
  });
  app.component('el-button', {
    props: ['disabled', 'link', 'type'],
    setup(props, { slots }) {
      return () => h('button', { disabled: props.disabled, 'data-button-type': props.type }, slots.default?.());
    }
  });
  app.component('el-tag', {
    props: ['type'],
    setup(props, { slots }) {
      return () => h('span', { 'data-risk-type': props.type }, slots.default?.());
    }
  });
  app.component('el-alert', {
    props: ['title', 'description', 'type', 'closable'],
    setup(props) {
      return () =>
        h('aside', { 'data-severity': props.type, 'data-closable': props.closable }, [h('strong', props.title), h('span', props.description)]);
    }
  });
  app.mount(root);
  await nextTick();
  return {
    root,
    events,
    failures: () => summaryNodes(root, (node) => node.tag === 'aside' && node.props['data-severity'] === 'error'),
    button: (label) => summaryNodes(root, (node) => node.tag === 'button' && summaryText(node) === label)[0],
    async click(node) {
      assert.ok(node, 'the existing summary button must render');
      assert.notEqual(node.props.disabled, true, 'the failure display must not disable existing summary controls');
      assert.equal(typeof node.props.onClick, 'function', 'the actual rendered button must retain its click listener');
      node.props.onClick({ type: 'click' });
      await nextTick();
    },
    async update(nextDetail) {
      currentDetail.value = nextDetail;
      await nextTick();
    },
    unmount: () => app.unmount()
  };
}

/** 无论断言成功或失败都卸载卡片，避免离线用例之间残留组件状态。 */
async function withSummary(detail, assertions) {
  const ui = await mountSummary(detail);
  try {
    await assertions(ui);
  } finally {
    ui.unmount();
  }
}

function assertFailureTitle(ui, expected, message) {
  const alerts = ui.failures();
  assert.equal(alerts.length, 1, 'FAILED renders exactly one error alert');
  assert.equal(summaryText(alerts[0]), expected, message);
  assert.equal(alerts[0].props['data-closable'], false, 'preserve the non-closable failure alert');
}

/** 同时检查文本、宿主属性与节点类型；转义后的敏感字符串也不能作为错误详情泄露。 */
function assertNoRawFailure(ui) {
  const nodes = summaryNodes(ui.root, () => true);
  const snapshot = JSON.stringify(nodes.map(({ tag, text, props }) => ({ tag, text, props })));
  for (const marker of [
    'Bearer',
    'SYNTHETIC_ERROR_CANARY',
    'SELECT',
    'SYNTHETIC_PRIVATE_TABLE',
    '__synthetic_decision_script__',
    'MODEL_RAW_FAILURE_CANARY',
    'FORGED_CODE_CANARY',
    noWorkOrderCode
  ]) {
    assert.ok(!snapshot.includes(marker), `${marker} must not appear in rendered text or host attributes`);
  }
  assert.equal(
    nodes.some((node) => node.tag === 'script'),
    false,
    'no error payload may create a script element'
  );
}

test('rendered FAILED with exact NO_ANALYZABLE_WORK_ORDER shows the fixed safe reason and next step', async () => {
  await withSummary(summaryDetail({ errorCode: noWorkOrderCode }), (ui) => {
    assertFailureTitle(ui, safeNoWorkOrderFailure, 'show the fixed reason and ask for a new schedule containing unfinished work orders');
    assert.ok(summaryText(ui.root).includes(noWorkOrderReason));
    assert.ok(summaryText(ui.root).includes(noWorkOrderNextStep));
    assert.equal(
      summaryNodes(ui.root, (node) => node.tag === 'p')
        .map(summaryText)
        .join(''),
      originalFailedSummary
    );
  });
});

test('rendered FAILED with unknown, historical or absent errorCode retains the existing generic failure', async () => {
  for (const fields of [
    {},
    { errorCode: undefined },
    { errorCode: null },
    { errorCode: '' },
    { errorCode: '   ' },
    { errorCode: 'FUTURE_FAILURE_CODE' },
    { errorCode: 'LEGACY_NO_ANALYZABLE_WORK_ORDER' }
  ]) {
    await withSummary(summaryDetail(fields), (ui) => {
      assertFailureTitle(ui, genericFailureTitle, 'unrecognized or missing codes must keep the existing fallback');
      assert.ok(!summaryText(ui.root).includes(noWorkOrderReason));
      assert.ok(!summaryText(ui.root).includes(noWorkOrderNextStep));
      assert.equal(
        summaryNodes(ui.root, (node) => node.tag === 'p')
          .map(summaryText)
          .join(''),
        originalFailedSummary
      );
    });
  }
});

test('rendered FAILED requires exact primitive code equality, never normalization, prefixes or coercion', async () => {
  for (const errorCode of [
    'no_analyzable_work_order',
    ` ${noWorkOrderCode}`,
    `${noWorkOrderCode} `,
    `${noWorkOrderCode}\n`,
    `${noWorkOrderCode}_LEGACY`,
    `PREFIX_${noWorkOrderCode}`,
    `${noWorkOrderCode}<script>FORGED_CODE_CANARY()</script>`,
    `${noWorkOrderCode}; ${unsafeFailureText}`,
    [noWorkOrderCode],
    { code: noWorkOrderCode },
    { toString: () => noWorkOrderCode }
  ]) {
    await withSummary(summaryDetail({ errorCode, errorMessage: unsafeFailureText }), (ui) => {
      assertFailureTitle(ui, genericFailureTitle, 'forged or normalized codes must not select the trusted reason');
      assert.ok(!summaryText(ui.root).includes(noWorkOrderReason));
      assertNoRawFailure(ui);
    });
  }
});

test('rendered non-FAILED states never show a failure reason even with a leftover recognized code', async () => {
  for (const status of [
    'CREATED',
    'RUNNING',
    'WAITING_APPROVAL',
    'APPROVED',
    'EXECUTING',
    'SUCCEEDED',
    'PARTIAL_SUCCESS',
    'STALE',
    'SUPERSEDED',
    'REJECTED',
    'ROLLING_BACK',
    'ROLLED_BACK',
    'CANCELLED',
    'FUTURE_STATE',
    undefined,
    null,
    ''
  ]) {
    await withSummary(summaryDetail({ status, errorCode: noWorkOrderCode, errorMessage: unsafeFailureText }), (ui) => {
      assert.equal(ui.failures().length, 0, `${String(status)} must not have a FAILED alert`);
      const text = summaryText(ui.root);
      for (const failureText of [genericFailureTitle, noWorkOrderReason, noWorkOrderNextStep]) {
        assert.ok(!text.includes(failureText), `${String(status)} must not inherit failure text`);
      }
      assertNoRawFailure(ui);
    });
  }
});

test('rendered FAILED never leaks backend errorMessage, SQL, bearer tokens or raw model failure text', async () => {
  for (const errorCode of [undefined, 'UNKNOWN_FAILURE', `${noWorkOrderCode}<script>FORGED_CODE_CANARY()</script>`, noWorkOrderCode]) {
    await withSummary(
      summaryDetail({
        errorCode,
        errorMessage: unsafeFailureText,
        resultJson: JSON.stringify({ narrative: { executiveSummary: unsafeFailureText }, warnings: [] })
      }),
      (ui) => {
        assertNoRawFailure(ui);
        assertFailureTitle(ui, errorCode === noWorkOrderCode ? safeNoWorkOrderFailure : genericFailureTitle, 'only fixed local copy is allowed');
      }
    );
  }
});

test('rendered failure controls still emit evidence and recalculate without changing snowflake IDs or input', async () => {
  for (const errorCode of [undefined, 'LEGACY_FAILURE', noWorkOrderCode]) {
    const detail = summaryDetail({ errorCode });
    const before = JSON.stringify(detail);
    await withSummary(detail, async (ui) => {
      assert.deepEqual(ui.events, [], 'rendering a failure must not trigger analysis or evidence events automatically');
      assert.ok(summaryText(ui.root).includes(`内部 ID：${snowflakeId}`), 'the full 19-digit business ID remains a labeled string');
      assert.equal(summaryNodes(ui.root, (node) => node.tag === 'button').length, 2, 'retain both existing summary controls');
      await ui.click(ui.button('查看证据'));
      await ui.click(ui.button('重新分析'));
      assert.deepEqual(ui.events, ['open-evidence', 'recalculate'], 'real button listeners preserve the existing event contract');
    });
    assert.equal(typeof detail.run.id, 'string');
    assert.equal(typeof detail.run.bizId, 'string');
    assert.equal(detail.run.id, summaryRunId);
    assert.equal(detail.run.bizId, snowflakeId);
    assert.equal(JSON.stringify(detail), before, 'display and clicks never mutate or numerically coerce the supplied run');
  }
});

test('rendered successful summary keeps business reference, narrative, risk, confidence and warning content', async () => {
  const executiveSummary = '原交付风险摘要：请核对资源负荷。';
  const warning = '原始警告：数据版本需复核。';
  /**
   * 风险分数空值保持未知占位，不补为零；真实零分必须原样显示，不能被吞掉。
   */
  for (const [riskScore, scoreText] of [
    [64, '风险分数 64'],
    [null, '风险分数 —'],
    [undefined, '风险分数 —'],
    [0, '风险分数 0']
  ]) {
    await withSummary(
      summaryDetail({
        status: 'SUCCEEDED',
        riskScore,
        resultJson: JSON.stringify({ businessRef: { code: 'SCHEDULE-RESULT-007' }, narrative: { executiveSummary }, warnings: [warning] })
      }),
      (ui) => {
        const text = summaryText(ui.root);
        for (const original of [
          '交付风险摘要',
          'SCHEDULE-RESULT-007',
          `内部 ID：${snowflakeId}`,
          '高风险',
          scoreText,
          '置信度 HIGH',
          executiveSummary
        ]) {
          assert.ok(text.includes(original), `preserve rendered summary content: ${original}`);
        }
        const renderedScores = summaryNodes(ui.root, (node) => node.tag === 'strong' && summaryText(node).startsWith('风险分数')).map(summaryText);
        assert.deepEqual(renderedScores, [scoreText], `riskScore ${String(riskScore)} must retain its exact rendered text without zero substitution`);
        assert.ok(!text.includes('SCHEDULE-FIXTURE-001'), 'parsed businessRef.code still takes precedence over run.bizCode');
        assert.equal(summaryNodes(ui.root, (node) => node.props['data-risk-type'] === 'danger').length, 1);
        const warnings = summaryNodes(ui.root, (node) => node.tag === 'aside' && node.props['data-severity'] === 'warning');
        assert.equal(warnings.length, 1);
        assert.equal(summaryText(warnings[0]), warning);
        assert.equal(ui.failures().length, 0);
      }
    );
  }
});

test('recognized FAILED reason does not depend on valid resultJson or expose its raw contents', async () => {
  for (const resultJson of [undefined, null, '', '{invalid-json: SYNTHETIC_ERROR_CANARY']) {
    await withSummary(summaryDetail({ errorCode: noWorkOrderCode, resultJson, errorMessage: unsafeFailureText }), (ui) => {
      assertNoRawFailure(ui);
      assertFailureTitle(ui, safeNoWorkOrderFailure, 'trusted failure copy works for failed runs with no result archive');
      assert.equal(
        summaryNodes(ui.root, (node) => node.tag === 'p')
          .map(summaryText)
          .join(''),
        originalFailedSummary
      );
    });
  }
});

test('real renderer updates remove stale known failure copy on error-code and status changes', async () => {
  await withSummary(summaryDetail({ errorCode: noWorkOrderCode, errorMessage: unsafeFailureText }), async (ui) => {
    assertFailureTitle(ui, safeNoWorkOrderFailure, 'start with the known deterministic failure');
    await ui.update(summaryDetail({ errorCode: 'UNKNOWN_FAILURE', errorMessage: unsafeFailureText }));
    assertFailureTitle(ui, genericFailureTitle, 'changing to an unknown code restores the existing generic failure');
    assert.ok(!summaryText(ui.root).includes(noWorkOrderReason));
    await ui.update(summaryDetail({ status: 'SUCCEEDED', errorCode: noWorkOrderCode, errorMessage: unsafeFailureText }));
    assert.equal(ui.failures().length, 0, 'a leftover code cannot preserve a FAILED alert after a successful update');
    assert.ok(!summaryText(ui.root).includes(noWorkOrderReason));
    assert.ok(!summaryText(ui.root).includes(noWorkOrderNextStep));
    assertNoRawFailure(ui);
  });
});

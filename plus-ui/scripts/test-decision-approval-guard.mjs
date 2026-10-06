import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { compileScript, parse } from '@vue/compiler-sfc';
import { build } from 'esbuild';
import { createRenderer, createSSRApp, h, inject, nextTick, provide, shallowReactive } from 'vue';
import { renderToString } from 'vue/server-renderer';

const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const componentPath = resolve(uiRoot, 'src/components/ai/decision/DecisionActionApproval.vue');
const { descriptor, errors } = parse(readFileSync(componentPath, 'utf8'), { filename: componentPath });
assert.deepEqual(errors, []);
const require = createRequire(import.meta.url);
const warning =
  '缺口为来源基本单位，不是已核准采购量；当前采购合同门禁仍关闭，不会创建采购草稿。请核对计量/包装、供应商起订量、同一 MRP 需求已有关联的未结申请及需求日期，核验后重新分析。';
const staticBlockReason = '来源/合同尚未核验，需重新分析；只有服务端确认的新合同可申请审批';
const serverBlockReason = '当前动作来源未通过合同核验，请重新分析。';

let componentPromise;
function component() {
  componentPromise ??= (async () => {
    const compiled = compileScript(descriptor, { id: 'decision-approval-guard', inlineTemplate: true });
    const result = await build({
      stdin: { contents: compiled.content, resolveDir: dirname(componentPath), sourcefile: componentPath, loader: 'ts' },
      alias: { '@': resolve(uiRoot, 'src') },
      bundle: true,
      platform: 'node',
      format: 'cjs',
      external: ['vue', 'element-plus'],
      write: false,
      logLevel: 'silent'
    });
    const mod = { exports: {} };
    const withElementStub = (name) => (name === 'element-plus' ? { ElMessage: { warning() {} }, ElMessageBox: { confirm() {} } } : require(name));
    new Function('require', 'module', 'exports', result.outputFiles[0].text)(withElementStub, mod, mod.exports);
    return mod.exports.default;
  })();
  return componentPromise;
}

const passthrough = {
  setup(_props, { slots }) {
    return () => h('section', slots.default?.());
  }
};
const action = (actionType, id, approvalStatus = 'PENDING_APPROVAL') => ({
  id,
  runId: '2105159630382362626',
  actionType,
  title: actionType === 'CREATE_PURCHASE_REQUISITION_DRAFT' ? '创建待审采购申请草稿' : '交期优先排程草案',
  approvalStatus,
  approvalRequired: 'Y',
  executionStatus: 'NOT_STARTED',
  riskLevel: 'HIGH',
  targetBizType: 'SHORTAGE_HORIZON',
  permissionCode: 'ai:decision:approve',
  payloadJson: JSON.stringify({
    runId: '2105159630382362626',
    requiredDate: '2026-10-07',
    materialLines: [{ materialId: '1001', gap: '5.4292', requiredDate: '2026-10-07' }]
  })
});
const run = { id: '2105159630382362626', bizType: 'SHORTAGE_HORIZON', sceneCode: 'DELIVERY_RISK', status: 'WAITING_APPROVAL' };

async function render(actions, actionBlockReasons, candidateRun = run) {
  const app = createSSRApp(await component(), { actions, run: candidateRun, actionBlockReasons });
  app.directive('hasPermi', { getSSRProps: () => ({}) });
  app.component('el-card', {
    setup(_props, { slots }) {
      return () => h('section', [slots.header?.(), slots.default?.()]);
    }
  });
  app.component('el-checkbox-group', passthrough);
  app.component('el-checkbox', {
    props: ['value', 'disabled'],
    setup(props, { slots }) {
      return () => h('label', [h('input', { type: 'checkbox', value: props.value, disabled: props.disabled }), slots.default?.()]);
    }
  });
  app.component('el-descriptions', passthrough);
  app.component('el-descriptions-item', {
    props: ['label'],
    setup(props, { slots }) {
      return () => h('p', [props.label, ': ', slots.default?.()]);
    }
  });
  app.component('el-tag', passthrough);
  app.component('el-input', passthrough);
  app.component('el-button', {
    props: ['disabled'],
    setup(props, { slots }) {
      return () => h('button', { disabled: props.disabled }, slots.default?.());
    }
  });
  app.component('el-alert', {
    props: ['title', 'description', 'type'],
    setup(props) {
      return () => h('aside', { 'data-severity': props.type }, [h('strong', props.title), h('span', props.description)]);
    }
  });
  return renderToString(app);
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

test('blocked purchase card explains source-unit gap without promising immediate draft creation', async () => {
  const markup = await render([action('CREATE_PURCHASE_REQUISITION_DRAFT', 'purchase')]);
  assert.match(markup, /data-severity="warning"/);
  assert.ok(markup.includes(warning), 'the complete caution must appear in the rendered purchase card');
  assert.match(markup, /采购申请草稿/);
  assert.doesNotMatch(markup, /510个|已核准采购量：5\.4292/, 'do not invent a verified purchasing quantity or existing request count');
});

test('non-purchase actions and non-pending purchase actions get no purchase-specific warning', async () => {
  for (const actions of [[action('CREATE_SCHEDULE_DRAFT', 'schedule')], [action('CREATE_PURCHASE_REQUISITION_DRAFT', 'done', 'APPROVED')]]) {
    const markup = await render(actions);
    assert.doesNotMatch(markup, /data-severity="warning"|缺口为来源基本单位|采购申请草稿/);
  }
  const markup = await render([action('CREATE_PURCHASE_REQUISITION_DRAFT', 'purchase'), action('CREATE_SCHEDULE_DRAFT', 'schedule')]);
  assert.equal((markup.match(/data-severity="warning"/g) ?? []).length, 1, 'only the purchase card gets the warning');
});

test('warning is scoped inside the action loop and does not add an approval block', async () => {
  const freshTemplate = parse(readFileSync(componentPath, 'utf8'), { filename: componentPath + '?template-guard' }).descriptor.template.ast;
  const loop = descendants(
    freshTemplate,
    (node) => node.type === 1 && node.props.some((prop) => prop.type === 7 && prop.name === 'for' && prop.exp?.content === 'action in pending')
  )[0];
  assert.ok(loop, 'pending action cards must still be rendered individually');
  const alert = descendants(
    loop,
    (node) =>
      node.type === 1 &&
      node.tag === 'el-alert' &&
      node.props.some((prop) => prop.type === 6 && prop.name === 'title' && prop.value?.content === '采购申请草稿审批提醒')
  )[0];
  assert.ok(alert, 'the existing warning must remain inside each matching action card');
  assert.equal(
    alert.props.find((prop) => prop.type === 7 && prop.name === 'if')?.exp?.content,
    "action.actionType === 'CREATE_PURCHASE_REQUISITION_DRAFT'"
  );
  const markup = await render([action('CREATE_PURCHASE_REQUISITION_DRAFT', 'purchase')]);
  const checkbox = markup.match(/<input\b[^>]*\bvalue="purchase"[^>]*>/)?.[0];
  assert.ok(checkbox, 'purchase draft checkbox must be rendered');
  assert.match(checkbox, /\bdisabled\b/, 'P0 purchase draft is blocked even when its payload is displayable');
  assert.match(markup, /来源\/合同尚未核验.*重新分析/s, 'purchase card must explain why approval is blocked');
});

test('DecisionRunDetail has an optional string-keyed action block reason contract', () => {
  const apiSource = readFileSync(resolve(uiRoot, 'src/api/ai/decision.ts'), 'utf8');
  const detailInterface = apiSource.match(/export interface DecisionRunDetail\s*\{([^}]*)\}/s)?.[1];
  assert.ok(detailInterface, 'the detail response interface must exist');
  assert.match(detailInterface, /actionBlockReasons\?\s*:\s*Record<DecisionId,\s*string>\s*;/);
});

test('decision detail forwards the optional reason map to the approval card', () => {
  const detailPath = resolve(uiRoot, 'src/views/ai/decision/detail.vue');
  const { descriptor: detailDescriptor, errors: detailErrors } = parse(readFileSync(detailPath, 'utf8'), { filename: detailPath });
  assert.deepEqual(detailErrors, []);
  const approval = descendants(detailDescriptor.template.ast, (node) => node.type === 1 && node.tag === 'DecisionActionApproval')[0];
  assert.ok(approval, 'the detail page must render the approval card');
  assert.equal(
    approval.props.find((prop) => prop.type === 7 && prop.name === 'bind' && prop.arg?.content === 'action-block-reasons')?.exp?.content,
    'detail.actionBlockReasons',
    'always use the latest GET detail reason map, not action payload or browser-controlled state'
  );
});

test('server short reason takes precedence for only its purchase action, without unlocking approval', async () => {
  const markup = await render([action('CREATE_PURCHASE_REQUISITION_DRAFT', 'purchase'), action('CREATE_SCHEDULE_DRAFT', 'schedule')], {
    purchase: serverBlockReason,
    schedule: 'non-purchase reason must not appear'
  });
  assert.ok(markup.includes(serverBlockReason), 'show the server reason in the blocked purchase card');
  assert.ok(markup.includes(warning), 'preserve the existing source-unit purchasing warning');
  assert.ok(!markup.includes(staticBlockReason), 'do not show the fallback when the server has a reason');
  assert.ok(!markup.includes('non-purchase reason must not appear'), 'never show purchase-specific reasons on other actions');
  assert.match(markup.match(/<input\b[^>]*\bvalue="purchase"[^>]*>/)?.[0] ?? '', /\bdisabled\b/);
  assert.doesNotMatch(markup.match(/<input\b[^>]*\bvalue="schedule"[^>]*>/)?.[0] ?? '', /\bdisabled\b/);
});

test('absent, blank, or irrelevant server map keeps the static P0 warning without unlocking purchase', async () => {
  for (const map of [undefined, {}, { purchase: '' }, { purchase: '   ' }, { otherPurchase: serverBlockReason }]) {
    const markup = await render([action('CREATE_PURCHASE_REQUISITION_DRAFT', 'purchase')], map);
    assert.ok(markup.includes(staticBlockReason), 'older JVM and empty reasons use the static P0 warning');
    assert.match(markup.match(/<input\b[^>]*\bvalue="purchase"[^>]*>/)?.[0] ?? '', /\bdisabled\b/);
  }
});
function host(tag, text = '') {
  return { tag, text, props: {}, children: [], parent: null };
}

function interactiveRenderer() {
  return createRenderer({
    createElement: (tag) => host(tag),
    createText: (text) => host('#text', text),
    createComment: (text) => host('#comment', text),
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

function findHost(root, predicate) {
  if (predicate(root)) return root;
  for (const child of root.children) {
    const found = findHost(child, predicate);
    if (found) return found;
  }
}

function textOf(node) {
  return node.text + node.children.map(textOf).join('');
}

async function mount(actions, actionBlockReasons, candidateRun = run) {
  const root = host('root');
  const apiWrites = [];
  let updateSelection;
  const liveProps = shallowReactive({ actions, run: candidateRun, actionBlockReasons });
  const Approval = await component();
  const app = interactiveRenderer().createApp({
    setup() {
      return () =>
        h(Approval, {
          ...liveProps,
          onApprove: (payload) => apiWrites.push({ endpoint: 'approveDecision', payload }),
          onReject: (reason) => apiWrites.push({ endpoint: 'rejectDecision', reason })
        });
    }
  });
  app.directive('hasPermi', { mounted() {}, updated() {} });
  app.component('el-card', {
    setup(_props, { slots }) {
      return () => h('section', [slots.header?.(), slots.default?.()]);
    }
  });
  app.component('el-checkbox-group', {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    setup(props, { slots, emit }) {
      provide('selection', {
        get value() {
          return props.modelValue;
        },
        update: (ids) => emit('update:modelValue', ids)
      });
      updateSelection = (ids) => emit('update:modelValue', ids);
      return () => h('section', slots.default?.());
    }
  });
  app.component('el-checkbox', {
    props: ['value', 'disabled'],
    setup(props, { slots }) {
      const selection = inject('selection');
      return () =>
        h('label', [
          h('input', {
            type: 'checkbox',
            value: props.value,
            disabled: props.disabled,
            onClick: () => {
              if (!props.disabled) selection.update([...selection.value.filter((id) => id !== props.value), props.value]);
            }
          }),
          slots.default?.()
        ]);
    }
  });
  app.component('el-descriptions', passthrough);
  app.component('el-descriptions-item', passthrough);
  app.component('el-tag', passthrough);
  app.component('el-alert', passthrough);
  app.component('el-input', {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    setup(props, { emit }) {
      return () => h('textarea', { value: props.modelValue, onInput: (event) => emit('update:modelValue', event.target.value) });
    }
  });
  app.component('el-button', {
    props: ['disabled'],
    setup(props, { slots }) {
      return () => h('button', { disabled: props.disabled }, slots.default?.());
    }
  });
  app.mount(root);
  await nextTick();
  const input = (value) => findHost(root, (node) => node.tag === 'input' && node.props.value === value);
  const button = (label) => findHost(root, (node) => node.tag === 'button' && textOf(node).includes(label));
  const forceClick = async (node) => {
    await node?.props.onClick?.();
    await nextTick();
  };
  const click = async (node) => {
    if (node && !node.props.disabled) await forceClick(node);
  };
  return {
    root,
    apiWrites,
    input,
    button,
    click,
    forceClick,
    updateSelection: async (ids) => {
      updateSelection(ids);
      await nextTick();
    },
    updateProps: async (next) => {
      Object.assign(liveProps, next);
      await nextTick();
    },
    unmount: () => app.unmount()
  };
}

test('purchase-only P0 action blocks approval API, while manual reject stays available', async () => {
  for (const status of ['PROPOSED', 'PENDING_APPROVAL']) {
    const ui = await mount([action('CREATE_PURCHASE_REQUISITION_DRAFT', 'purchase', status)]);
    assert.equal(ui.input('purchase').props.disabled, true);
    assert.equal(ui.button('批准选中项').props.disabled, true);
    assert.equal(ui.button('驳回全部').props.disabled, false);
    await ui.click(ui.input('purchase'));
    await ui.updateSelection(['purchase']); /** 即使复选框模型被伪造，也不能发出批准事件。 */
    await ui.forceClick(ui.button('批准选中项')); /** 即使禁用按钮收到合成事件，也必须忽略该事件。 */
    assert.deepEqual(ui.apiWrites, [], 'no approval endpoint may be called for purchase-only selection');
    const comment = findHost(ui.root, (node) => node.tag === 'textarea');
    comment.props.onInput({ target: { value: '来源计量未核，重新分析' } });
    await nextTick();
    await ui.click(ui.button('驳回全部'));
    assert.deepEqual(ui.apiWrites, [{ endpoint: 'rejectDecision', reason: '来源计量未核，重新分析' }]);
    ui.unmount();
  }
});

test('self-declared v2 purchase payload is blocked without trusting client version', async () => {
  const fakeV2 = action('CREATE_PURCHASE_REQUISITION_DRAFT', 'fake-v2');
  fakeV2.payloadJson = JSON.stringify({ purchase_payload_version: 2, requirement_detail_ids: ['12'], materialLines: [] });
  const ui = await mount([fakeV2]);
  assert.equal(ui.input('fake-v2').props.disabled, true);
  assert.equal(ui.button('批准选中项').props.disabled, true);
  await ui.updateSelection(['fake-v2']);
  await ui.forceClick(ui.button('批准选中项'));
  assert.deepEqual(ui.apiWrites, []);
  ui.unmount();
});

test('non-purchase action alone can be approved; unselected blocked purchase is explicitly rejected', async () => {
  const ui = await mount([action('CREATE_PURCHASE_REQUISITION_DRAFT', 'purchase'), action('CREATE_SCHEDULE_DRAFT', 'schedule')]);
  assert.equal(ui.input('purchase').props.disabled, true);
  assert.equal(ui.input('schedule').props.disabled, false);
  await ui.click(ui.input('schedule'));
  assert.equal(ui.button('批准选中项').props.disabled, false);
  await ui.click(ui.button('批准选中项'));
  assert.deepEqual(
    ui.apiWrites,
    [
      {
        endpoint: 'approveDecision',
        payload: { approvedActionIds: ['schedule'], rejectedActionIds: ['purchase'], comment: '' }
      }
    ],
    'this checks only the submitted selection; server policy may reject the request'
  );
  ui.unmount();
});

test('forged selection cannot add a purchase action to approvedActionIds', async () => {
  const ui = await mount([action('CREATE_PURCHASE_REQUISITION_DRAFT', 'purchase'), action('CREATE_SCHEDULE_DRAFT', 'schedule')]);
  await ui.updateSelection(['purchase', 'schedule']);
  await ui.click(ui.button('批准选中项'));
  assert.deepEqual(ui.apiWrites, [
    {
      endpoint: 'approveDecision',
      payload: { approvedActionIds: ['schedule'], rejectedActionIds: ['purchase'], comment: '' }
    }
  ]);
  ui.unmount();
});

test('server reason cannot approve purchase; other action still submits with purchase rejected', async () => {
  const ui = await mount([action('CREATE_PURCHASE_REQUISITION_DRAFT', 'purchase'), action('CREATE_SCHEDULE_DRAFT', 'schedule')], {
    purchase: serverBlockReason
  });
  assert.equal(ui.input('purchase').props.disabled, true);
  assert.equal(ui.input('schedule').props.disabled, false);
  await ui.updateSelection(['purchase']);
  await ui.forceClick(ui.button('批准选中项'));
  assert.deepEqual(ui.apiWrites, [], 'no approval API event when only blocked purchase is selected');
  await ui.click(ui.input('schedule'));
  await ui.click(ui.button('批准选中项'));
  assert.deepEqual(
    ui.apiWrites,
    [
      {
        endpoint: 'approveDecision',
        payload: { approvedActionIds: ['schedule'], rejectedActionIds: ['purchase'], comment: '' }
      }
    ],
    'server policy may still reject a mixed submission'
  );
  ui.unmount();
});

test('server reason never disables manual rejection of a purchase-only run', async () => {
  const ui = await mount([action('CREATE_PURCHASE_REQUISITION_DRAFT', 'purchase')], { purchase: serverBlockReason });
  assert.equal(ui.button('驳回全部').props.disabled, false);
  const comment = findHost(ui.root, (node) => node.tag === 'textarea');
  comment.props.onInput({ target: { value: '来源不合规，重新分析' } });
  await nextTick();
  await ui.click(ui.button('驳回全部'));
  assert.deepEqual(ui.apiWrites, [{ endpoint: 'rejectDecision', reason: '来源不合规，重新分析' }]);
  ui.unmount();
});

test('blocked legacy purchase notice cannot contradict its no-execution gate', async () => {
  const markup = await render([action('CREATE_PURCHASE_REQUISITION_DRAFT', 'purchase')]);
  assert.match(markup, /当前采购合同门禁仍关闭/);
  assert.match(markup, /不会创建采购草稿/);
  assert.doesNotMatch(markup, /审批会启动创建草稿|批准后(?:自动)?创建采购草稿/);
  assert.match(markup, /当前采购动作不可批准/);
  assert.match(markup, /<button[^>]*disabled[^>]*>批准选中项<\/button>/);
  assert.match(markup, /<button(?![^>]*\bdisabled\b)[^>]*>驳回全部<\/button>/);
});

const newRun = {
  ...run,
  bizType: 'WORK_ORDER',
  bizId: '9007199254745006',
  decisionContractVersion: 2,
  version: 1
};
function newPurchase(id = '9007199254747001', quantity = '6') {
  return {
    ...action('CREATE_PURCHASE_REQUISITION_DRAFT', id),
    purchasePayloadVersion: 2,
    targetBizType: 'WORK_ORDER',
    targetBizId: newRun.bizId,
    payloadJson: JSON.stringify({
      purchase_payload_version: 2,
      runId: newRun.id,
      materialId: '9007199254741011',
      vendorId: '9007199254741021',
      windowEnd: '2026-10-31',
      materialVersion: 'M-1',
      vendorVersion: 'V-1',
      supplierTermsId: '9007199254741031',
      supplierTermsVersion: 'T-1',
      baseUomId: '26',
      baseUomVersion: 'Q-1',
      orderUomId: '26',
      orderUomVersion: 'Q-1',
      conversionVersion: 'Q-1',
      requirement_detail_ids: ['9007199254740993'],
      frozenLines: [
        {
          detailId: '9007199254740993',
          baseUomId: '26',
          coveredDemandBase: quantity,
          orderUomId: '26',
          requestedOrderQty: quantity,
          requiredDate: '2026-10-07'
        }
      ],
      expectedSourceFingerprint: 'ab'.repeat(32)
    })
  };
}
function checkboxMarkup(markup, id) {
  return markup.match(new RegExp('<input\\b[^>]*\\bvalue="' + id + '"[^>]*>'))?.[0] || '';
}
async function assertBlockedPurchase(candidate, candidateRun = newRun, ...reasonMaps) {
  const reasons = reasonMaps.length ? reasonMaps[0] : {};
  const markup = await render([candidate], reasons, candidateRun);
  assert.match(checkboxMarkup(markup, candidate.id), /\bdisabled\b/, '未知、历史或未核验采购不得启用复选框');
  const ui = await mount([candidate], reasons, candidateRun);
  assert.equal(ui.input(String(candidate.id)).props.disabled, true, '真实UI必须保留采购门禁');
  await ui.updateSelection([candidate.id]);
  await ui.forceClick(ui.button('批准选中项'));
  assert.deepEqual(ui.apiWrites, [], '伪造选中项或合成事件不得绕过门禁');
  ui.unmount();
}

test('运行与动作接口分别保留可空服务端版本marker', () => {
  const source = readFileSync(resolve(uiRoot, 'src/api/ai/decision.ts'), 'utf8');
  const runType = source.match(/export interface DecisionRunSummary\s*\{([^}]*)\}/s)?.[1];
  const actionType = source.match(/export interface DecisionAction\s*\{([^}]*)\}/s)?.[1];
  assert.match(runType || '', /decisionContractVersion\?\s*:\s*number\s*\|\s*null\s*;/, '运行marker必须兼容旧NULL');
  assert.match(actionType || '', /purchasePayloadVersion\?\s*:\s*number\s*\|\s*null\s*;/, '动作marker必须兼容旧NULL');
});

test('真实SSR为三份服务端新版六、六、十二合同显示可选而非已执行', async () => {
  const actions = ['6', '6', '12'].map((quantity, index) => newPurchase((9007199254747001n + BigInt(index)).toString(), quantity));
  const markup = await render(actions, {}, newRun);
  for (const candidate of actions) {
    assert.ok(checkboxMarkup(markup, candidate.id), '必须渲染完整雪花动作ID');
    assert.doesNotMatch(checkboxMarkup(markup, candidate.id), /\bdisabled\b/, '后台明确核验的完整新版候选应可手动勾选');
  }
  assert.match(markup, /冻结覆盖量 6（基本单位ID 26）/, '冻结六必须保留真实单位ID');
  assert.match(markup, /冻结覆盖量 12（基本单位ID 26）/, '冻结十二必须保留真实单位ID');
  assert.match(markup, /冻结意图，审批和执行仍由ERP重新核验/, 'UI只展示冻结意图，不签发来源认证');
  assert.doesNotMatch(markup, /当前采购动作不可批准|所有采购草稿动作|门禁仍关闭/, '新版可选候选不能继续显示全禁用提示');
  assert.ok(!markup.includes('ab'.repeat(32)), '完整来源指纹不能进入SSR');
  assert.match(markup, /<button[^>]*disabled[^>]*>批准选中项<\/button>/, '默认仍不选中任何动作');
});

test('真实mount只输出手动选中的新版雪花动作ID', async () => {
  const actions = ['6', '6', '12'].map((quantity, index) => newPurchase((9007199254747001n + BigInt(index)).toString(), quantity));
  const ui = await mount(actions, {}, newRun);
  assert.equal(ui.button('批准选中项').props.disabled, true, '新合同不能自动选中');
  assert.deepEqual(ui.apiWrites, [], '展示不得触发任何审批事件');
  for (const candidate of actions) assert.equal(ui.input(candidate.id).props.disabled, false, '新候选应支持显式勾选');
  await ui.click(ui.input(actions[0].id));
  await ui.click(ui.input(actions[2].id));
  await ui.click(ui.button('批准选中项'));
  assert.deepEqual(
    ui.apiWrites,
    [
      {
        endpoint: 'approveDecision',
        payload: {
          approvedActionIds: [actions[0].id, actions[2].id],
          rejectedActionIds: [actions[1].id],
          comment: ''
        }
      }
    ],
    '仅校验组件输出事件，不调用真实API且不绕过服务端认证'
  );
  ui.unmount();
});

for (const approvalStatus of ['PROPOSED', 'PENDING_APPROVAL']) {
  test('新版待审批状态允许显式选中：' + approvalStatus, async () => {
    const candidate = { ...newPurchase(), approvalStatus };
    const ui = await mount([candidate], {}, newRun);
    assert.equal(ui.input(candidate.id).props.disabled, false, '合法待处理状态应可选');
    await ui.click(ui.input(candidate.id));
    await ui.click(ui.button('批准选中项'));
    assert.deepEqual(ui.apiWrites[0]?.payload.approvedActionIds, [candidate.id], '动作ID必须原样输出');
    ui.unmount();
  });
}

for (const [label, reasons] of [
  ['缺Map', undefined],
  ['空值Map', null],
  ['数组Map', []],
  ['非记录Map', new Map()]
]) {
  test('新版候选拒绝未知后台核验结果：' + label, async () => {
    const candidate = newPurchase();
    const markup = await render([candidate], reasons, newRun);
    assert.ok(markup.includes(staticBlockReason), '未知来源应显示新的固定回退消息');
    await assertBlockedPurchase(candidate, newRun, reasons);
  });
}
for (const [label, reasons] of [
  ['明确空Map', {}],
  ['当前空原因', { '9007199254747001': '   ' }],
  ['其他动作被阻断', { other: serverBlockReason }]
]) {
  test('已收到完整核验Map且当前无阻断原因可选：' + label, async () => {
    const candidate = newPurchase();
    const markup = await render([candidate], reasons, newRun);
    assert.doesNotMatch(checkboxMarkup(markup, candidate.id), /\bdisabled\b/, '明确空Map不等于未知Map');
    assert.ok(!markup.includes(staticBlockReason), '可选新版候选不应误显示回退阻断消息');
  });
}

test('精确服务端原因仅阻断对应新版采购，不删除或扩大原因', async () => {
  const first = newPurchase();
  const second = newPurchase('9007199254747002', '12');
  const reasons = { [first.id]: serverBlockReason };
  const markup = await render([first, second], reasons, newRun);
  assert.ok(markup.includes(serverBlockReason), '必须展示精确服务端阻断原因');
  assert.match(checkboxMarkup(markup, first.id), /\bdisabled\b/, '带原因的新版仍不可批准');
  assert.doesNotMatch(checkboxMarkup(markup, second.id), /\bdisabled\b/, '其他已核验候选不能被静态全禁用');
  const ui = await mount([first, second], reasons, newRun);
  await ui.updateSelection([first.id, second.id]);
  await ui.click(ui.button('批准选中项'));
  assert.deepEqual(
    ui.apiWrites,
    [{ endpoint: 'approveDecision', payload: { approvedActionIds: [second.id], rejectedActionIds: [first.id], comment: '' } }],
    '被阻断项不得混入批准ID'
  );
  assert.deepEqual(reasons, { [first.id]: serverBlockReason }, '组件不得删改服务端原因Map');
  ui.unmount();
});

for (const marker of [undefined, null, 1, '2', 2.5, 3]) {
  for (const field of ['decisionContractVersion', 'purchasePayloadVersion']) {
    test('历史或伪造marker不升级：' + field + '=' + String(marker), async () => {
      const candidate = newPurchase();
      const candidateRun = { ...newRun };
      (field === 'decisionContractVersion' ? candidateRun : candidate)[field] = marker;
      await assertBlockedPurchase(candidate, candidateRun);
    });
  }
}

test('完整payload自称v2也不能替代两个服务端marker', async () => {
  const candidate = newPurchase();
  delete candidate.purchasePayloadVersion;
  const candidateRun = { ...newRun, decisionContractVersion: null };
  await assertBlockedPurchase(candidate, candidateRun);
});

test('三份旧采购即使新运行marker也仍然禁用', async () => {
  const old = ['old-six-a', 'old-six-b', 'old-twelve'].map((id) => action('CREATE_PURCHASE_REQUISITION_DRAFT', id));
  old[2].purchasePayloadVersion = 2;
  const markup = await render(old, {}, newRun);
  for (const candidate of old) assert.match(checkboxMarkup(markup, candidate.id), /\bdisabled\b/, '旧物料行不得升级为冻结合同');
  const ui = await mount(old, {}, newRun);
  await ui.updateSelection(old.map((candidate) => candidate.id));
  await ui.forceClick(ui.button('批准选中项'));
  assert.deepEqual(ui.apiWrites, [], '旧采购不得通过伪造选择批准');
  ui.unmount();
});

for (const status of ['CREATED', 'RUNNING', 'APPROVED', 'EXECUTING', 'SUCCEEDED', 'STALE', 'SUPERSEDED', 'FAILED', 'CANCELLED']) {
  test('新版运行非待审批保持关闭：' + status, async () => {
    await assertBlockedPurchase(newPurchase(), { ...newRun, status });
  });
}
test('显式过期运行即使待审批状态也不能批准', async () => {
  await assertBlockedPurchase(newPurchase(), { ...newRun, stale: true });
});
for (const executionStatus of [undefined, null, 'EXECUTING', 'SUCCEEDED', 'FAILED_PRECHECK', 'FAILED', 'SKIPPED', 'COMPENSATING', 'COMPENSATED']) {
  test('新版动作不是未开始执行则保持关闭：' + String(executionStatus), async () => {
    await assertBlockedPurchase({ ...newPurchase(), executionStatus });
  });
}
for (const approvalStatus of ['APPROVED', 'REJECTED', 'NOT_AUTHORIZED']) {
  test('新版已处理动作不能重新选中：' + approvalStatus, async () => {
    const candidate = { ...newPurchase(), approvalStatus };
    const ui = await mount([candidate], {}, newRun);
    assert.equal(ui.input(candidate.id), undefined, '已处理动作不进入待审批列表');
    await ui.updateSelection([candidate.id]);
    await ui.forceClick(ui.button('批准选中项'));
    assert.deepEqual(ui.apiWrites, [], '已处理动作不得再次输出审批事件');
    ui.unmount();
  });
}

const bindingErrors = [
  [
    'payload运行不匹配',
    (candidate) => {
      const payload = JSON.parse(candidate.payloadJson);
      payload.runId = '2105159630382362627';
      candidate.payloadJson = JSON.stringify(payload);
    }
  ],
  [
    '动作运行不匹配',
    (candidate) => {
      candidate.runId = '2105159630382362627';
    }
  ],
  [
    '动作运行数值',
    (candidate) => {
      candidate.runId = 21;
    }
  ],
  [
    '运行身份数值',
    (_candidate, candidateRun) => {
      candidateRun.id = 21;
    }
  ],
  [
    '动作身份数值',
    (candidate) => {
      candidate.id = 21;
    }
  ],
  [
    '动作目标非工单',
    (candidate) => {
      candidate.targetBizType = 'SHORTAGE_HORIZON';
    }
  ],
  [
    '运行对象非工单',
    (_candidate, candidateRun) => {
      candidateRun.bizType = 'SHORTAGE_HORIZON';
    }
  ],
  [
    '工单目标不匹配',
    (candidate) => {
      candidate.targetBizId = '9007199254745007';
    }
  ],
  [
    '目标身份缺失',
    (candidate) => {
      delete candidate.targetBizId;
    }
  ],
  [
    '工单身份数值',
    (_candidate, candidateRun) => {
      candidateRun.bizId = 21;
    }
  ],
  [
    '载荷不完整',
    (candidate) => {
      const payload = JSON.parse(candidate.payloadJson);
      delete payload.vendorId;
      candidate.payloadJson = JSON.stringify(payload);
    }
  ],
  [
    '载荷未知元数据',
    (candidate) => {
      const payload = JSON.parse(candidate.payloadJson);
      payload.metadata = '测试隐藏内容';
      candidate.payloadJson = JSON.stringify(payload);
    }
  ],
  [
    '载荷非新版',
    (candidate) => {
      const payload = JSON.parse(candidate.payloadJson);
      payload.purchase_payload_version = '2';
      candidate.payloadJson = JSON.stringify(payload);
    }
  ],
  [
    '数量不相等',
    (candidate) => {
      const payload = JSON.parse(candidate.payloadJson);
      payload.frozenLines[0].requestedOrderQty = '7';
      candidate.payloadJson = JSON.stringify(payload);
    }
  ]
];
for (const [label, mutate] of bindingErrors) {
  test('新版安全结构与运行工单身份严格绑定：' + label, async () => {
    const candidate = newPurchase();
    const candidateRun = { ...newRun };
    mutate(candidate, candidateRun);
    await assertBlockedPurchase(candidate, candidateRun);
  });
}
for (const reason of [null, 1, {}]) {
  test('当前阻断原因类型未知不能当作明确允许：' + JSON.stringify(reason), async () => {
    const candidate = newPurchase();
    await assertBlockedPurchase(candidate, newRun, { [candidate.id]: reason });
  });
}

for (const [label, update] of [
  ['核验Map丢失', (candidate) => ({ actionBlockReasons: undefined })],
  ['核验后出现原因', (candidate) => ({ actionBlockReasons: { [candidate.id]: serverBlockReason } })],
  ['运行marker回退', () => ({ run: { ...newRun, decisionContractVersion: null } })],
  ['动作开始执行', (candidate) => ({ actions: [{ ...candidate, executionStatus: 'EXECUTING' }] })]
]) {
  test('选中后刷新守卫变化仍阻止合成审批事件：' + label, async () => {
    const candidate = newPurchase();
    const ui = await mount([candidate], {}, newRun);
    assert.equal(ui.input(candidate.id).props.disabled, false, '测试须从合法新版可选状态开始');
    await ui.click(ui.input(candidate.id));
    assert.equal(ui.button('批准选中项').props.disabled, false, '选中后才允许发起确认');
    await ui.updateProps(update(candidate));
    assert.equal(ui.input(candidate.id).props.disabled, true, '刷新后必须立即恢复门禁');
    assert.equal(ui.button('批准选中项').props.disabled, true, '旧选择不能保留批准资格');
    await ui.forceClick(ui.button('批准选中项'));
    assert.deepEqual(ui.apiWrites, [], '最终事件输出前仍须复核当前状态');
    await ui.updateProps({ actions: [candidate], run: newRun, actionBlockReasons: {} });
    assert.equal(ui.button('批准选中项').props.disabled, true, '恢复核验后不得自动恢复已失效的勾选');
    ui.unmount();
  });
}

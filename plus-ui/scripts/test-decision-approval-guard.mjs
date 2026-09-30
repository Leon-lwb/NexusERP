import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { compileScript, parse } from '@vue/compiler-sfc';
import { build } from 'esbuild';
import { createRenderer, createSSRApp, h, inject, nextTick, provide } from 'vue';
import { renderToString } from 'vue/server-renderer';

const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const componentPath = resolve(uiRoot, 'src/components/ai/decision/DecisionActionApproval.vue');
const { descriptor, errors } = parse(readFileSync(componentPath, 'utf8'), { filename: componentPath });
assert.deepEqual(errors, []);
const require = createRequire(import.meta.url);
const warning =
  '缺口为来源基本单位，不是已核准采购量；当前采购合同门禁仍关闭，不会创建采购草稿。请核对计量/包装、供应商起订量、同一 MRP 需求已有关联的未结申请及需求日期，核验后重新分析。';
const staticBlockReason =
  '历史口径、来源计量未核，需重新分析；当前所有采购草稿动作（包括标称新版）均不可批准，服务端仍会拒绝。可驳回本次决策；其他合法动作可单独审批。';
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

async function render(actions, actionBlockReasons) {
  const app = createSSRApp(await component(), { actions, run, actionBlockReasons });
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
  assert.match(markup, /历史.*来源计量未核.*重新分析/s, 'purchase card must explain why approval is blocked');
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

async function mount(actions, actionBlockReasons) {
  const root = host('root');
  const apiWrites = [];
  let updateSelection;
  const app = interactiveRenderer().createApp(await component(), {
    actions,
    run,
    actionBlockReasons,
    onApprove: (payload) => apiWrites.push({ endpoint: 'approveDecision', payload }),
    onReject: (reason) => apiWrites.push({ endpoint: 'rejectDecision', reason })
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

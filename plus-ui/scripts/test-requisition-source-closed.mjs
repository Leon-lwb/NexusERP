import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parse as parseTemplate } from '@vue/compiler-dom';
import { parse as parseSfc } from '@vue/compiler-sfc';
import ts from 'typescript';
import axios, { AxiosError } from 'axios';
import * as Vue from 'vue';

const source = readFileSync(new URL('../src/views/erp/pu/requisition/form.vue', import.meta.url), 'utf8');
const { descriptor, errors } = parseSfc(source);
assert.deepEqual(errors, []);
const script = ts.createSourceFile('requisition-form.ts', descriptor.scriptSetup.content, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
const allNodes = (node) => [node, ...(node.children ?? []).flatMap(allNodes)];
const elements = allNodes(parseTemplate(descriptor.template.content)).filter((node) => node.type === 1);
const attr = (node, name) => node.props.find((prop) => prop.type === 6 && prop.name === name)?.value?.content;
const directive = (node, name, argument) =>
  node.props.find((prop) => prop.type === 7 && prop.name === name && (!argument || prop.arg?.content === argument))?.exp?.content;
const sourceField = elements.find((node) => node.tag === 'el-form-item' && attr(node, 'label') === '来源类型');
assert.ok(sourceField, '来源类型表单项应保留');
const select = allNodes(sourceField).find((node) => node.tag === 'el-select' && directive(node, 'model') === 'form.sourceType');
const option = allNodes(sourceField).find((node) => node.tag === 'el-option' && directive(node, 'for')?.includes('sourceTypeOptions'));
assert.ok(select && option, '来源类型选择框及字典选项应保留');

function sourceState() {
  const form = { value: { sourceType: 'MANUAL' } };
  const pu_source_type = {
    value: [
      { value: 'MANUAL', label: '手工' },
      { value: 'REQ_CONVERT', label: '需求转入' },
      { value: 'RECEIVE_LINK', label: '到货联动' },
      { value: 'AI_DECISION', label: 'AI交付风险决策' }
    ]
  };
  const declarations = script.statements.filter(ts.isVariableStatement).flatMap((statement) => statement.declarationList.declarations);
  const names = ['pendingSourceLabels', 'isSourceLedgerPending', 'sourceTypeOptions'];
  const definitions = names.map((name) => {
    const declaration = declarations.find((item) => ts.isIdentifier(item.name) && item.name.text === name);
    assert.ok(declaration?.initializer, `缺少来源状态计算：${name}`);
    return `const ${name} = ${declaration.initializer.getText(script)};`;
  });
  const js = ts.transpileModule(definitions.join('\n'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None }
  }).outputText;
  const computed = (fn) => ({
    get value() {
      return fn();
    }
  });
  const state = new Function(
    'computed',
    'form',
    'pu_source_type',
    `${js}\nreturn { pendingSourceLabels, isSourceLedgerPending, sourceTypeOptions };`
  )(computed, form, pu_source_type);
  return { form, ...state };
}
const evaluate = (expression, context) => new Function(...Object.keys(context), `return (${expression});`)(...Object.values(context));

test('新增表单明确提示来源占用未接入，且说明需求转入暂不可用', () => {
  const alert = elements.find((node) => node.tag === 'el-alert' && attr(node, 'title')?.includes('来源占用未接入'));
  assert.ok(alert, '新增表单须常显禁用原因，不能仅靠悬停提示');
  assert.match(attr(alert, 'title'), /需求转入.*暂不(?:可|能)/);
  assert.match(directive(alert, 'if'), /!form\.requisitionId/);
  assert.match(attr(alert, 'title'), /后端|服务端/);
});

test('来源占用尚未接入的来源不可选；MANUAL 和其他合法来源仍可选', () => {
  const state = sourceState();
  const disabled = directive(option, 'bind', 'disabled');
  assert.ok(disabled, '受限字典项应显式置灰');
  for (const sourceType of ['REQ_CONVERT', 'MRP', 'PRD_MRP', 'MATERIAL_REQUIREMENT', 'LOW_STOCK', 'MES_SPARE_LOW_STOCK']) {
    assert.equal(state.isSourceLedgerPending(sourceType), true, `${sourceType} 应受限`);
    assert.equal(evaluate(disabled, { dict: { value: sourceType }, isSourceLedgerPending: state.isSourceLedgerPending }), true);
  }
  for (const sourceType of ['MANUAL', 'RECEIVE_LINK']) {
    assert.equal(state.isSourceLedgerPending(sourceType), false, `${sourceType} 不能被误禁用`);
    assert.equal(evaluate(disabled, { dict: { value: sourceType }, isSourceLedgerPending: state.isSourceLedgerPending }), false);
  }
  const options = state.sourceTypeOptions.value;
  assert.ok(options.some((dict) => dict.value === 'MANUAL'));
  assert.ok(options.some((dict) => dict.value === 'RECEIVE_LINK'));
  assert.ok(
    options.some((dict) => dict.value === 'REQ_CONVERT'),
    '置灰时仍须看得到需求转入字典项'
  );
  assert.ok(!options.some((dict) => dict.value === 'AI_DECISION'), '手工新增仍不能冒充 AI 来源');
});

test('历史来源值可只读识别，未收录字典的 MES 低库存来源也显示名称', () => {
  const state = sourceState();
  const disabled = directive(select, 'bind', 'disabled');
  assert.ok(disabled, '历史受限来源的下拉框应只读');
  const isDisabled = (sourceType) => evaluate(disabled, { form: { sourceType }, isSourceLedgerPending: state.isSourceLedgerPending });
  assert.equal(isDisabled('MANUAL'), false);
  assert.equal(isDisabled('RECEIVE_LINK'), false);
  assert.equal(isDisabled('REQ_CONVERT'), true);
  assert.equal(isDisabled('MES_SPARE_LOW_STOCK'), true);
  assert.equal(isDisabled('AI_DECISION'), true);
  state.form.value.sourceType = 'REQ_CONVERT';
  assert.equal(state.sourceTypeOptions.value.find((dict) => dict.value === 'REQ_CONVERT')?.label, '需求转入');
  state.form.value.sourceType = 'MES_SPARE_LOW_STOCK';
  assert.match(state.sourceTypeOptions.value.find((dict) => dict.value === 'MES_SPARE_LOW_STOCK')?.label ?? '', /备件.*低库存/);
  state.form.value.sourceType = 'AI_DECISION';
  assert.ok(
    state.sourceTypeOptions.value.some((dict) => dict.value === 'AI_DECISION'),
    '已有 AI 单据仍可只读显示'
  );
});

// 本脚本独占覆盖可信创建入口，原有历史来源禁用断言继续保留。
const viewUrl = new URL('../src/views/erp/pu/requisition/', import.meta.url);
function loadTs(url, request) {
  const { outputText } = ts.transpileModule(readFileSync(url, 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
  });
  const exports = {};
  new Function('exports', 'require', outputText)(exports, (name) => {
    assert.equal(name, '@/utils/request');
    return { default: request };
  });
  return exports;
}
const ids = {
  materialId: '9007199254740993',
  vendorId: '9007199254740994',
  windowEnd: '2026-10-31',
  requirementDetailIds: ['9007199254740995', '9007199254740996']
};
const fingerprint = 'a'.repeat(64);
const sourcePreview = (input = ids) => ({
  materialId: input.materialId,
  vendorId: input.vendorId,
  windowEnd: input.windowEnd,
  snapshotDate: '2026-10-04',
  sourceFingerprint: fingerprint,
  inventoryEvidence: 'ORIGINAL_STOCK_LINEAGE',
  catalog: { material: { materialId: input.materialId }, baseUnit: { unitId: '9007199254740997' }, orderUnit: { unitId: '9007199254740998' } },
  lines: input.requirementDetailIds.map((detailId) => ({
    detailId,
    requiredDate: '2026-10-30',
    baseUomId: '9007199254740997',
    orderUomId: '9007199254740998',
    coveredDemandBase: '12.50',
    orderQuantity: '2.50'
  }))
});
const purchaseResult = (replayed = false) => ({
  requisitionId: '9007199254741000',
  requisitionNo: 'TEST-ONLY',
  status: 'DRAFT',
  version: 0,
  replayed,
  sourceFingerprint: fingerprint,
  lines: ids.requirementDetailIds.map((requirementDetailId, index) => ({
    lineId: index === 0 ? '9007199254741001' : '9007199254741002',
    requirementDetailId,
    materialId: ids.materialId,
    baseUomId: '9007199254740997',
    applyQuantity: '12.50',
    requiredDate: '2026-10-30'
  }))
});
const ok = (data) => ({ code: 200, data });
const memoryStorage = () => {
  const data = new Map();
  return { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: (key) => data.delete(key) };
};
function flowFixture(overrides = {}) {
  const module = loadTs(new URL('certifiedPurchase.ts', viewUrl));
  const calls = { preview: [], create: [], keys: 0 };
  const state = module.createCertifiedPurchaseState();
  const storage = overrides.storage ?? memoryStorage();
  const flow = module.createCertifiedPurchaseFlow({
    state,
    storage,
    storageKey: 'test:tenant:user',
    newRequestKey: () => {
      calls.keys++;
      return `test-request-key-${calls.keys.toString().padStart(8, '0')}`;
    },
    preview: async (data) => {
      calls.preview.push(structuredClone(data));
      return ok(sourcePreview(data));
    },
    create: async (data) => {
      calls.create.push(structuredClone(data));
      return ok(purchaseResult());
    },
    ...overrides
  });
  flow.editSource(structuredClone(ids));
  return { flow, state, calls, storage, module };
}
function functionBody(name) {
  const declaration = script.statements.find((node) => ts.isFunctionDeclaration(node) && node.name?.text === name);
  assert.ok(declaration?.body, `缺少旧表单函数：${name}`);
  return declaration.body.getText(script);
}

test('普通 BO 新增彻底关闭，历史单据只走原维护接口', () => {
  const body = functionBody('submitForm');
  assert.doesNotMatch(body, /addPurchaseRequisition\s*\(/);
  const warn = [];
  let writes = 0;
  const proxy = { $modal: { msgWarning: (msg) => warn.push(msg) } };
  /** 新增依赖按真实保护函数执行；本例为无身份的普通新增，不用常量false替代守卫。 */
  const legacyGuard = () =>
    new Function('isViewMode', 'props', 'isDecisionResultRoute', 'protectsLegacyWrites', 'proxy', functionBody('legacyWriteBlocked'))(
      { value: false },
      { disabled: false },
      () => false,
      { value: false },
      proxy
    );
  new Function('form', 'proxy', 'route', 'updatePurchaseRequisition', 'router', 'legacyWriteBlocked', body)(
    { value: { requisitionId: null } },
    proxy,
    { query: { action: 'add' } },
    () => {
      writes++;
    },
    {},
    legacyGuard
  );
  assert.equal(writes, 0);
  assert.match(warn.join(''), /普通.*新增.*关闭|可信来源/);
  assert.match(body, /updatePurchaseRequisition\(form\.value\)/);
  assert.doesNotMatch(source, /createCertifiedPurchaseRequisition|expectedSourceFingerprint/, '历史表单不能授予认证');
  const save = elements.find((node) => node.tag === 'el-button' && directive(node, 'on', 'click') === 'submitForm');
  assert.match(directive(save, 'if'), /form\.requisitionId/);
});

test('实际列表接独立可信来源创建对话框，低库存旧通道保持暂停', () => {
  const list = readFileSync(new URL('index.vue', viewUrl), 'utf8');
  assert.match(list, /可信来源创建/);
  assert.match(list, /CertifiedPurchaseDialog/);
  const { descriptor } = parseSfc(list);
  const ast = ts.createSourceFile('list.ts', descriptor.scriptSetup.content, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
  const body = (name) => ast.statements.find((node) => ts.isFunctionDeclaration(node) && node.name?.text === name).body.getText(ast);
  assert.doesNotMatch(body('handleAdd'), /router\.push|action:\s*'add'/);
  assert.match(body('handleGenerateFromLowStock'), /暂停/);
  assert.doesNotMatch(body('handleGenerateFromLowStock'), /generateFromLowStock\s*\(/);
});

test('API 白名单映射预览 detailIds 和创建命令，不接受自算数量或 verified', async () => {
  const calls = [];
  const request = async (config) => {
    calls.push(config);
    return ok({});
  };
  request.defaults = { transformResponse: [(data) => data] };
  const api = loadTs(new URL('../src/api/erp/pu/requisition/index.ts', import.meta.url), request);
  assert.equal(typeof api.previewCertifiedPurchaseSource, 'function');
  assert.equal(typeof api.createCertifiedPurchaseRequisition, 'function');
  const command = { ...ids, requestKey: 'test-request-key-00000001', expectedSourceFingerprint: fingerprint, verified: true, quantity: 999 };
  await api.previewCertifiedPurchaseSource(command, { token: 'memory-session-a', assertCurrent: () => {} });
  await api.createCertifiedPurchaseRequisition(command, { token: 'memory-session-a', assertCurrent: () => {} });
  assert.deepEqual(calls[0].data, {
    materialId: ids.materialId,
    vendorId: ids.vendorId,
    windowEnd: ids.windowEnd,
    detailIds: ids.requirementDetailIds
  });
  assert.equal(calls[0].url, '/erp/ai/certified-source/preview');
  assert.equal(calls[1].url, '/erp/pu/requisition/certified');
  assert.deepEqual(
    Object.keys(calls[1].data).sort(),
    ['requestKey', 'materialId', 'vendorId', 'windowEnd', 'requirementDetailIds', 'expectedSourceFingerprint'].sort()
  );
  assert.equal(calls[1].headers.repeatSubmit, false);
  assert.ok(calls.every((config) => config.method === 'post'));
});

test('API 保留明确业务拒绝，传输错误不伪装成成功', async () => {
  const refusal = { code: 409, msg: '来源拒绝：库存记录缺失' };
  const request = async (config) => {
    for (const transform of config.transformResponse) transform(refusal, {}, 200);
    throw new Error(refusal.msg);
  };
  request.defaults = { transformResponse: [(data) => data] };
  const api = loadTs(new URL('../src/api/erp/pu/requisition/index.ts', import.meta.url), request);
  assert.equal(typeof api.previewCertifiedPurchaseSource, 'function');
  const response = await api.previewCertifiedPurchaseSource(ids, { token: 'memory-session-a', assertCurrent: () => {} });
  assert.equal(response.code, 409);
  assert.match(response.msg, /来源拒绝/);
});

test('意图先生成永久键；未预览不能提交，完整预览后只发服务端指纹', async () => {
  const { flow, state, calls, storage } = flowFixture();
  assert.equal(await flow.submit(), false);
  assert.equal(calls.create.length, 0);
  assert.equal(await flow.preview(), true);
  assert.equal(calls.keys, 1);
  assert.ok(storage.getItem('test:tenant:user').includes(state.requestKey));
  assert.equal(await flow.submit(), true);
  assert.deepEqual(calls.create[0], { requestKey: state.requestKey, ...ids, expectedSourceFingerprint: fingerprint });
  assert.equal(state.phase, 'succeeded');
});

test('任一来源字段变更使预览失效，不能沿用指纹提交', async () => {
  for (const patch of [
    { materialId: '9007199254741010' },
    { vendorId: '9007199254741011' },
    { windowEnd: '2026-11-01' },
    { requirementDetailIds: ['9007199254741012'] }
  ]) {
    const { flow, state, calls } = flowFixture();
    await flow.preview();
    flow.editSource({ ...ids, ...patch });
    assert.equal(state.preview, null);
    assert.equal(await flow.submit(), false);
    assert.equal(calls.create.length, 0);
  }
});

test('旧预览迟到不会覆盖新来源预览', async () => {
  let resolveOld;
  let count = 0;
  const { flow, state } = flowFixture({
    preview: async (input) => {
      count++;
      if (count === 1)
        return new Promise((resolve) => {
          resolveOld = resolve;
        });
      return ok(sourcePreview(input));
    }
  });
  const old = flow.preview();
  flow.editSource({ ...ids, vendorId: '9007199254741011' });
  await flow.preview();
  resolveOld(ok(sourcePreview(ids)));
  assert.equal(await old, false);
  assert.equal(state.preview.vendorId, '9007199254741011');
});

test('来源拒绝和无记录不当零；缺指纹、空明细、数字 ID、缺单位数量都禁止创建', async () => {
  const invalid = [
    { code: 500, msg: '来源拒绝：无可靠库存记录' },
    ok({ ...sourcePreview(), sourceFingerprint: null }),
    ok({ ...sourcePreview(), lines: [] }),
    ok({ ...sourcePreview(), materialId: 9007199254740992 }),
    ok({ ...sourcePreview(), lines: sourcePreview().lines.map((line) => ({ ...line, baseUomId: null })) }),
    ok({ ...sourcePreview(), lines: sourcePreview().lines.map((line) => ({ ...line, coveredDemandBase: null })) })
  ];
  for (const response of invalid) {
    const { flow, state, calls } = flowFixture({ preview: async () => response });
    assert.equal(await flow.preview(), false);
    assert.equal(state.preview, null);
    assert.equal(await flow.submit(), false);
    assert.equal(calls.create.length, 0);
    assert.ok(state.message);
  }
  const { module } = flowFixture();
  assert.match(module.formatCertifiedValue(null), /未返回|未知/);
  assert.notEqual(module.formatCertifiedValue(null), '0');
  assert.equal(module.formatCertifiedValue('0'), '0');
});

test('ID 只能是字符串，重复、空来源和无效日期不能送预览', async () => {
  for (const patch of [
    { materialId: 42 },
    { vendorId: '0' },
    { requirementDetailIds: [] },
    { requirementDetailIds: [ids.requirementDetailIds[0], ids.requirementDetailIds[0]] },
    { windowEnd: '2026-02-30' }
  ]) {
    const { flow, calls } = flowFixture();
    flow.editSource({ ...ids, ...patch });
    assert.equal(await flow.preview(), false);
    assert.equal(calls.preview.length, 0);
  }
  assert.deepEqual(flowFixture().module.parseRequirementDetailIds('9007199254740995，\n9007199254740996'), ids.requirementDetailIds);
});

test('结果未知只能明确恢复原请求，原键原载荷跨关闭和重载保持', async () => {
  const writes = [];
  const storage = memoryStorage();
  const { flow, state, calls } = flowFixture({
    storage,
    create: async (data) => {
      writes.push(JSON.stringify(data));
      throw new Error('timeout');
    }
  });
  await flow.preview();
  assert.equal(await flow.submit(), false);
  assert.equal(state.phase, 'unknown');
  const key = state.requestKey;
  assert.equal(flow.editSource({ ...ids, vendorId: '99' }), false);
  assert.equal(flow.startNew(), false);
  assert.equal(await flow.submit(), false);
  assert.equal(writes.length, 1, '不能自动重试');
  assert.equal(await flow.recover(), false);
  assert.equal(writes[0], writes[1]);
  assert.equal(state.requestKey, key);
  assert.equal(calls.keys, 1);
  const restoredWrites = [];
  const restored = flowFixture({
    storage,
    create: async (data) => {
      restoredWrites.push(JSON.stringify(data));
      return ok(purchaseResult(true));
    }
  });
  assert.equal(restored.flow.restoreSaved(), true);
  assert.equal(restored.state.phase, 'unknown');
  assert.equal(restoredWrites.length, 0);
  assert.equal(await restored.flow.recover(), true);
  assert.equal(restoredWrites[0], writes[0]);
  assert.equal(restored.calls.keys, 0);
  assert.match(restored.state.message, /恢复.*原|原.*恢复/);
});

test('并发双击不重发，原载荷发送前已持久保存', async () => {
  let finish;
  const storage = memoryStorage();
  const { flow, state } = flowFixture({
    storage,
    create: async (command) => {
      assert.deepEqual(JSON.parse(storage.getItem('test:tenant:user')).command, command);
      return new Promise((resolve) => {
        finish = resolve;
      });
    }
  });
  await flow.preview();
  const sent = flow.submit();
  assert.equal(state.phase, 'submitting');
  assert.equal(await flow.submit(), false);
  assert.equal(await flow.recover(), false);
  finish(ok(purchaseResult()));
  assert.equal(await sent, true);
});

test('明确业务拒绝不显示成功，只能明确开始新意图才换键', async () => {
  const { flow, state, calls } = flowFixture({ create: async () => ({ code: 409, msg: '来源已变化，拒绝创建' }) });
  await flow.preview();
  assert.equal(await flow.submit(), false);
  assert.equal(state.phase, 'rejected');
  assert.equal(state.result, null);
  assert.match(state.message, /拒绝/);
  assert.equal(calls.keys, 1);
  assert.equal(await flow.submit(), false);
  assert.equal(flow.startNew(), true);
  await flow.preview();
  assert.equal(calls.keys, 2);
});

test('不完整或串单的成功信封仍视为结果未知', async () => {
  for (const data of [
    {},
    { ...purchaseResult(), requisitionId: 42 },
    { ...purchaseResult(), sourceFingerprint: 'b'.repeat(64) },
    { ...purchaseResult(), lines: [] }
  ]) {
    const { flow, state } = flowFixture({ create: async () => ok(data) });
    await flow.preview();
    assert.equal(await flow.submit(), false);
    assert.equal(state.phase, 'unknown');
    assert.equal(state.result, null);
  }
});

test('本地存储失败不发写 API，损坏待恢复请求不得自动丢弃换键', async () => {
  const storage = memoryStorage();
  const { flow, state, calls } = flowFixture({ storage });
  await flow.preview();
  storage.setItem = () => {
    throw new Error('quota');
  };
  assert.equal(await flow.submit(), false);
  assert.equal(calls.create.length, 0);
  assert.notEqual(state.phase, 'succeeded');
  const corrupt = memoryStorage();
  corrupt.setItem('test:tenant:user', '{bad json');
  const restored = flowFixture({ storage: corrupt });
  assert.equal(restored.flow.restoreSaved(), false);
  assert.equal(restored.state.phase, 'blocked');
  assert.equal(await restored.flow.preview(), false);
  assert.equal(restored.calls.keys, 0);
});

test('独立对话框有中文边界说明、逐物料单位数量和明确恢复操作', () => {
  const dialog = readFileSync(new URL('CertifiedPurchaseDialog.vue', viewUrl), 'utf8');
  assert.deepEqual(parseSfc(dialog).errors, []);
  assert.match(dialog, /无记录.*0|无库存记录.*0/);
  assert.match(dialog, /旧单据|历史单据/);
  assert.match(dialog, /恢复原请求/);
  for (const field of ['coveredDemandBase', 'orderQuantity', 'baseUomId', 'orderUomId']) assert.ok(dialog.includes(field));
  assert.doesNotMatch(dialog, /parseInt|parseFloat|Number\(.*Id|verified\s*[:=]/);
});

test('旧普通 BO API 也不再发新增写入，不能绕过页面重新打开旧来源', async () => {
  const writes = [];
  const request = async (config) => {
    writes.push(config);
    return ok({});
  };
  request.defaults = { transformResponse: [(data) => data] };
  const api = loadTs(new URL('../src/api/erp/pu/requisition/index.ts', import.meta.url), request);
  await assert.rejects(api.addPurchaseRequisition({ sourceType: 'MANUAL', puPurchaseRequisitionLineList: [] }), /普通.*新增.*关闭|可信来源/);
  assert.equal(writes.length, 0);
});

test('另一窗口未恢复的原请求不能被当前意图覆盖或清理', async () => {
  const storage = memoryStorage();
  const original = flowFixture({
    storage,
    create: async () => {
      throw new Error('timeout');
    }
  });
  const other = flowFixture({ storage, newRequestKey: () => 'another-window-request-0001' });
  await original.flow.preview();
  await original.flow.submit();
  const saved = storage.getItem('test:tenant:user');
  assert.equal(await other.flow.preview(), false);
  assert.equal(other.state.phase, 'blocked');
  assert.equal(other.flow.startNew(), false);
  assert.equal(storage.getItem('test:tenant:user'), saved);
  assert.equal(other.calls.preview.length, 0);
});

// 使用真实 Axios 的变换、拦截器和纯内存 adapter，绝不连接业务服务。
function adapterFixture(options = {}) {
  const storage = memoryStorage();
  storage.setItem('Admin-Token', 'memory-session-a');
  const writes = [];
  const identities = [];
  const service = axios.create({
    adapter: async (config) => {
      const authorization = config.headers.get('Authorization');
      let data;
      let status = 200;
      if (config.url === '/erp/pu/requisition/certified/context') {
        identities.push(authorization);
        const account = authorization === 'Bearer memory-session-a' ? { userId: '101', tenantId: '000001' } : { userId: '202', tenantId: '000002' };
        data = ok({ ...account, userId: options.userId ?? account.userId, tenantId: options.effectiveTenant ?? account.tenantId });
        if (options.identityWait) await options.identityWait();
      } else if (config.url === '/erp/ai/certified-source/preview') {
        const input = JSON.parse(config.data);
        data = ok(sourcePreview({ ...input, requirementDetailIds: input.detailIds }));
      } else {
        writes.push({ data: JSON.parse(config.data), authorization });
        if (options.transportFailure) throw new AxiosError('内存模拟超时，结果未知', AxiosError.ETIMEDOUT, config);
        status = options.httpStatus ?? 200;
        data = options.envelope ?? ok(purchaseResult());
      }
      const response = { data: JSON.stringify(data), status, statusText: '', headers: {}, config, request: {} };
      if (config.validateStatus && !config.validateStatus(status)) {
        throw new AxiosError('内存模拟 HTTP 响应异常', AxiosError.ERR_BAD_RESPONSE, config, {}, response);
      }
      return response;
    }
  });
  // 与全局请求器一致：未声明固定凭证时，发送时从共享认证缓存取值。
  service.interceptors.request.use((config) => {
    if (config.headers.isToken !== false) config.headers.set('Authorization', 'Bearer ' + storage.getItem('Admin-Token'));
    return config;
  });
  // 保留真实 Axios 的 HTTP 异常，仅模拟全局业务信封解包及拒绝。
  service.interceptors.response.use((response) => {
    if (response.data.code !== 200) throw new Error('内存模拟服务端拒绝');
    return response.data;
  });
  return { storage, writes, identities, service, options, api: loadTs(new URL('../src/api/erp/pu/requisition/index.ts', import.meta.url), service) };
}
const memorySession = (fixture) => ({
  token: fixture.storage.getItem('Admin-Token'),
  assertCurrent: () => {
    if (fixture.storage.getItem('Admin-Token') !== 'memory-session-a') throw new Error('内存主体已变化');
  }
});

function dialogFixture(fixture, confirm = async () => true) {
  const file = readFileSync(new URL('CertifiedPurchaseDialog.vue', viewUrl), 'utf8');
  const { descriptor } = parseSfc(file);
  const { outputText } = ts.transpileModule(descriptor.scriptSetup.content, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
  });
  const listeners = new Map();
  const pinia = Vue.reactive({ userId: '101', tenantId: '000001' });
  const window = {
    addEventListener: (name, callback) => listeners.set(name, callback),
    removeEventListener: (name) => listeners.delete(name)
  };
  const document = { visibilityState: 'visible', ...window };
  const api = new Function(
    'require',
    'exports',
    'defineEmits',
    'defineExpose',
    'localStorage',
    'window',
    'document',
    'crypto',
    outputText +
      `
    return { open, handleCreate, handleRecover, handleStartNew, sourceForm, state, visible, get flow() { return flow; } };`
  )(
    (name) => {
      if (name === 'vue') return { ...Vue, onMounted: (callback) => callback(), onBeforeUnmount: () => {} };
      if (name === 'element-plus') return { ElMessageBox: { confirm }, ElMessage: { warning: () => {} } };
      if (name === '@/store/modules/user') return { useUserStore: () => pinia };
      if (name === '@/utils/auth') return { getToken: () => fixture.storage.getItem('Admin-Token') };
      if (name === '@/api/erp/pu/requisition') return fixture.api;
      if (name === './certifiedPurchase') return loadTs(new URL('certifiedPurchase.ts', viewUrl));
      throw new Error('未声明的测试依赖');
    },
    {},
    () => () => {},
    () => {},
    fixture.storage,
    window,
    document,
    { randomUUID: () => 'test-dialog-request-0001' }
  );
  return Object.assign(api, { pinia, listeners });
}
async function pendingDialog(options = {}, confirm) {
  const fixture = adapterFixture({ transportFailure: true, ...options });
  const dialog = dialogFixture(fixture, confirm);
  await dialog.open();
  Object.assign(dialog.sourceForm, {
    materialId: ids.materialId,
    vendorId: ids.vendorId,
    windowEnd: ids.windowEnd,
    detailText: ids.requirementDetailIds.join('\n')
  });
  await dialog.flow.preview();
  await dialog.handleCreate();
  const key = 'nexus:pu:certified-request:000001:101';
  assert.equal(fixture.writes.length, 1);
  assert.equal(dialog.state.value.phase, 'unknown');
  return { fixture, dialog, key, saved: fixture.storage.getItem(key) };
}

test('真实 adapter：HTTP 5xx 或 HTTP 200 的 R 5xx 都保持未知、原键与原载荷', async () => {
  for (const [httpStatus, code] of [
    [503, 503],
    [503, 409],
    [500, 500],
    [200, 500],
    [200, 503]
  ]) {
    const fixture = adapterFixture({ httpStatus, envelope: { code, msg: '模拟服务端未确认结果' } });
    const session = memorySession(fixture);
    const { flow, state, storage } = flowFixture({
      storage: fixture.storage,
      create: (data) => fixture.api.createCertifiedPurchaseRequisition(data, session)
    });
    await flow.preview();
    assert.equal(await flow.submit(), false);
    const saved = storage.getItem('test:tenant:user');
    assert.equal(state.phase, 'unknown', `HTTP ${httpStatus}/R ${code} 不可被当作确定拒绝`);
    assert.equal(flow.startNew(), false);
    assert.equal(await flow.recover(), false);
    assert.equal(storage.getItem('test:tenant:user'), saved);
    assert.deepEqual(fixture.writes[0].data, fixture.writes[1].data);
  }
});

test('真实 adapter：仅已确认的 HTTP/R 4xx 业务拒绝可进入 rejected', async () => {
  for (const httpStatus of [200, 409]) {
    const fixture = adapterFixture({ httpStatus, envelope: { code: 409, msg: '模拟确定业务冲突' } });
    const { flow, state } = flowFixture({ create: (data) => fixture.api.createCertifiedPurchaseRequisition(data, memorySession(fixture)) });
    await flow.preview();
    assert.equal(await flow.submit(), false);
    assert.equal(state.phase, 'rejected');
    assert.equal(flow.startNew(), true);
  }
});

test('真实 adapter：另一 Tab 换凭证而 Pinia 不变，恢复前必须关闭且不发送 A 载荷', async () => {
  const { fixture, dialog, key, saved } = await pendingDialog();
  fixture.storage.setItem('Admin-Token', 'memory-session-b');
  await dialog.handleRecover();
  assert.equal(fixture.writes.length, 1, '不得在 B 认证上下文发出 A 原请求');
  assert.equal(dialog.visible.value, false);
  assert.equal(fixture.storage.getItem(key), saved);
  assert.equal(dialog.pinia.userId, '101', '用例故意保留旧 Pinia 缓存');
});

test('真实 adapter：确认框等待期间换凭证，确认完成后必须再次核验主体', async () => {
  let confirmNow;
  const { fixture, dialog, key, saved } = await pendingDialog(
    {},
    () =>
      new Promise((resolve) => {
        confirmNow = resolve;
      })
  );
  const recovery = dialog.handleRecover();
  for (let attempt = 0; !confirmNow && attempt < 20; attempt++) await new Promise((resolve) => setTimeout(resolve, 0));
  assert.ok(confirmNow);
  fixture.storage.setItem('Admin-Token', 'memory-session-b');
  confirmNow(true);
  await recovery;
  assert.equal(fixture.writes.length, 1);
  assert.equal(dialog.visible.value, false);
  assert.equal(fixture.storage.getItem(key), saved);
});

test('真实 adapter：open 重新核身份，不根据旧 Pinia 恢复另一主体分键的意图', async () => {
  const { fixture, dialog, key, saved } = await pendingDialog();
  fixture.storage.setItem('Admin-Token', 'memory-session-b');
  await dialog.open();
  assert.equal(dialog.visible.value, false);
  assert.equal(dialog.flow, undefined);
  assert.equal(fixture.storage.getItem(key), saved);
  assert.equal(fixture.writes.length, 1);
});

test('真实 adapter：storage 事件及 focus 下的有效租户变化都会失效活动 flow，不清原键', async () => {
  for (const event of ['storage', 'focus']) {
    const { fixture, dialog, key, saved } = await pendingDialog();
    const listener = dialog.listeners.get(event);
    assert.equal(typeof listener, 'function', `必须监听 ${event}`);
    if (event === 'storage') {
      fixture.storage.setItem('Admin-Token', 'memory-session-b');
      await listener({ key: 'Admin-Token' });
    } else {
      fixture.options.effectiveTenant = '000002';
      await listener();
    }
    assert.equal(dialog.visible.value, false);
    assert.equal(dialog.flow, undefined);
    assert.equal(fixture.storage.getItem(key), saved);
    assert.equal(fixture.writes.length, 1);
  }
});

test('真实 adapter：身份核验后、实际 dispatch 前换凭证，最后一跳也不得发写请求', async () => {
  const fixture = adapterFixture();
  const dialog = dialogFixture(fixture);
  await dialog.open();
  Object.assign(dialog.sourceForm, {
    materialId: ids.materialId,
    vendorId: ids.vendorId,
    windowEnd: ids.windowEnd,
    detailText: ids.requirementDetailIds.join('\n')
  });
  await dialog.flow.preview();
  fixture.service.interceptors.request.use((config) => {
    if (config.url === '/erp/pu/requisition/certified') fixture.storage.setItem('Admin-Token', 'memory-session-b');
    return config;
  });
  await dialog.handleCreate();
  assert.equal(fixture.writes.length, 0);
  assert.equal(dialog.visible.value, false);
  const saved = fixture.storage.getItem('nexus:pu:certified-request:000001:101');
  assert.ok(saved);
  assert.ok(!saved.includes('memory-session-a') && !saved.includes('memory-session-b'), '原 intent 不持久化凭证');
});

test('真实 adapter：只读 context 接受安全小整数身份，拒绝不安全或非整数主体', async () => {
  for (const userId of [101, 101.5, Number.MAX_SAFE_INTEGER + 1]) {
    const fixture = adapterFixture({ userId });
    const dialog = dialogFixture(fixture);
    await dialog.open();
    assert.equal(dialog.visible.value, userId === 101);
    assert.equal(fixture.writes.length, 0);
    if (userId === 101) assert.ok(dialog.flow);
    else assert.equal(dialog.flow, undefined);
  }
});

test('真实 adapter：只读 context 回包前凭证变更，迟到身份不能绑定活动 flow', async () => {
  let finish;
  const fixture = adapterFixture({
    identityWait: () =>
      new Promise((resolve) => {
        finish = resolve;
      })
  });
  const dialog = dialogFixture(fixture);
  const opening = dialog.open();
  for (let attempt = 0; !finish && attempt < 20; attempt++) await new Promise((resolve) => setTimeout(resolve, 0));
  assert.ok(finish);
  fixture.storage.setItem('Admin-Token', 'memory-session-b');
  finish();
  await opening;
  assert.equal(dialog.visible.value, false);
  assert.equal(dialog.flow, undefined);
  assert.equal(fixture.writes.length, 0);
});

test('真实 adapter：原主体重新核验后只能恢复保留的原键原载荷，不持久化凭证', async () => {
  const { fixture, dialog, key, saved } = await pendingDialog();
  fixture.storage.setItem('Admin-Token', 'memory-session-b');
  await dialog.listeners.get('storage')({ key: 'Admin-Token' });
  fixture.storage.setItem('Admin-Token', 'memory-session-a');
  fixture.options.transportFailure = false;
  fixture.options.envelope = ok(purchaseResult(true));
  await dialog.open();
  assert.equal(dialog.state.value.phase, 'unknown');
  assert.equal(fixture.storage.getItem(key), saved);
  await dialog.handleRecover();
  assert.equal(dialog.state.value.phase, 'succeeded');
  assert.equal(fixture.writes.length, 2);
  assert.deepEqual(fixture.writes[0].data, fixture.writes[1].data);
  assert.equal(fixture.storage.getItem(key), saved);
  assert.ok(!saved.includes('memory-session-a') && !saved.includes('memory-session-b'));
});

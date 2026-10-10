import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parse as parseTemplate } from '@vue/compiler-dom';
import { parse as parseSfc } from '@vue/compiler-sfc';
import ts from 'typescript';

/** 检查实际模板，并仅使用内存 API 执行实际 SFC 处理函数。 */
/** 不需要调用服务、生成构建产物或修改依赖。 */
const source = readFileSync(new URL('../src/views/erp/pu/order/form.vue', import.meta.url), 'utf8');
const { descriptor, errors } = parseSfc(source);
assert.deepEqual(errors, []);
const script = ts.createSourceFile('purchase-order-form.ts', descriptor.scriptSetup.content, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
const nodes = (node) => [node, ...(node.children ?? []).flatMap(nodes)];
const elements = nodes(parseTemplate(descriptor.template.content)).filter((node) => node.type === 1);
const attr = (node, name) => node.props.find((prop) => prop.type === 6 && prop.name === name)?.value?.content;
const directive = (node, name, argument) =>
  node.props.find((prop) => prop.type === 7 && prop.name === name && (!argument || prop.arg?.content === argument))?.exp?.content;
const evaluate = (expression, context) => new Function(...Object.keys(context), `return (${expression});`)(...Object.values(context));

function handlers(names, context) {
  const definitions = names.map((name) => {
    const fn = script.statements.find((statement) => ts.isFunctionDeclaration(statement) && statement.name?.text === name);
    assert.ok(fn, `SFC handler ${name} should exist`);
    return fn.getText(script);
  });
  const js = ts.transpileModule(definitions.join('\n'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None }
  }).outputText;
  return new Function(...Object.keys(context), `${js}\nreturn { ${names.join(', ')} };`)(...Object.values(context));
}

function saveContext(formData, lines) {
  const calls = [];
  const form = { value: formData };
  const lineList = { value: lines };
  const route = { path: '/erp/pu/order/form', query: { id: formData.orderId, action: formData.orderId ? 'edit' : 'add' } };
  const native = {
    order: {
      tenantId: '000000',
      orderId: formData.orderId,
      orderNo: formData.orderNo,
      requisitionId: formData.requisitionId ?? null,
      vendorId: '1001',
      version: null,
      quoteId: null,
      originalCommandPresent: false,
      requisitionSourceType: formData.requisitionId ? 'MANUAL' : null,
      decisionRunId: null,
      decisionActionId: null,
      sourceRequestKey: null,
      selectFlowId: null
    },
    genericEditAllowedHint: true,
    allocations: []
  };
  const context = {
    form,
    lineList,
    route,
    props: { disabled: false, customFormData: null },
    isViewMode: { value: false },
    sourceProtected: { value: !formData.orderId },
    legacyWritePending: { value: false },
    originalOrderId: { value: formData.orderId ?? null },
    nativeOrderSnapshot: { value: structuredClone(native) },
    detailRequestSeq: { value: 0 },
    loadedRouteSignature: { value: '' },
    loadedCredential: { value: 'synthetic-token' },
    loadedActor: { value: { tenantId: '000000', userId: '1002' } },
    autoGenFlag: { value: false },
    getToken: () => 'synthetic-token',
    normalizeBusinessId: (value) => {
      assert.ok(typeof value === 'string' && /^[1-9]\d*$/.test(value));
      return value;
    },
    createBusinessSession: (_read, guard) => ({ token: 'synthetic-token', assertCurrent: () => guard?.() }),
    getCertifiedOrderContext: async () => ({ tenantId: '000000', userId: '1002' }),
    getCertifiedOrderDetail: async () => structuredClone(native),
    getPurchaseOrder: async (id) => {
      assert.equal(id, formData.orderId);
      return { data: structuredClone(formData) };
    },
    tryAutoSelect: () => {},
    addPurchaseOrder: async (data) => calls.push({ method: 'add', data: structuredClone(data) }),
    updatePurchaseOrder: async (data) => calls.push({ method: 'update', data: structuredClone(data) }),
    proxy: { $modal: { msgSuccess: (message) => calls.push({ message }), msgWarning: (message) => calls.push({ warning: message }) } },
    router: { push: (path) => calls.push({ path }) }
  };
  const methods = handlers(
    [
      'round2',
      'recalcLine',
      'recalcLines',
      'recalcHeader',
      'routeIdentity',
      'ordinaryNative',
      'getInfo',
      'assertOriginalFormBinding',
      'requireLegacyOrder',
      'submitForm'
    ],
    context
  );
  context.loadedRouteSignature.value = methods.routeIdentity();
  return { ...context, ...methods, calls };
}

test('采购申请号仅只读展示，不再提供申请预填按钮', () => {
  const field = elements.find((node) => node.tag === 'el-form-item' && attr(node, 'prop') === 'requisitionNo');
  assert.ok(field, '旧订单的申请号字段应保留');
  const input = nodes(field).find((node) => node.tag === 'el-input');
  assert.equal(directive(input, 'model'), 'form.requisitionNo');
  assert.ok(
    input.props.some((prop) => prop.type === 6 && prop.name === 'readonly'),
    '申请号必须只读'
  );
  assert.ok(!nodes(field).some((node) => node.tag === 'el-button'), '不能再从订单表单选择申请预填');
});

test('新增订单提前提示去采购申请列表转单，提示本身不触发写操作', () => {
  const alert = elements.find((node) => node.tag === 'el-alert' && attr(node, 'title') === '带申请来源的订单请在采购申请列表转单后编辑');
  assert.ok(alert, '新增时应明确展示申请转单入口说明');
  assert.match(attr(alert, 'description'), /不支持.*无申请来源.*手工新增/);
  assert.match(attr(alert, 'description'), /服务端|后端/);
  const visible = directive(alert, 'if');
  assert.ok(visible, '只在新增可编辑表单显示引导');
  assert.equal(evaluate(visible, { form: { orderId: null }, isViewMode: false, props: { disabled: false } }), true);
  assert.equal(evaluate(visible, { form: { orderId: '2105000000000000100' }, isViewMode: false, props: { disabled: false } }), false);
  assert.equal(evaluate(visible, { form: {}, isViewMode: true, props: { disabled: false } }), false);
  assert.equal(evaluate(visible, { form: {}, isViewMode: false, props: { disabled: true } }), false);
  assert.ok(!nodes(alert).some((node) => node.props?.some((prop) => prop.type === 7 && prop.name === 'on')), '提示不能挂自动转单或写事件');
});

test('表单不再引用预填或转单接口，也不虚报已生成采购订单', () => {
  assert.doesNotMatch(source, /\b(?:generateFromRequisition|handleSelectRequisition|convertPurchaseRequisition)\b/);
  assert.doesNotMatch(source, /已根据申请单生成采购订单/);
});

test('无申请来源的手工新增按真实服务拒绝保持封闭，不发送普通新增', async () => {
  const saveButton = elements.find((node) => node.tag === 'el-button' && directive(node, 'on', 'click') === 'submitForm');
  assert.ok(saveButton, '明确普通历史单的保存入口仍保留');
  assert.equal(evaluate(directive(saveButton, 'if'), { isViewMode: false, props: { disabled: false }, sourceProtected: true }), false);
  assert.equal(evaluate(directive(saveButton, 'if'), { isViewMode: false, props: { disabled: false }, sourceProtected: false }), true);
  const state = saveContext({ orderId: null, orderNo: 'PO-MANUAL', requisitionId: null, requisitionNo: null, exchangeRate: 1, freightAmount: 5 }, [
    { materialId: '1001', orderQuantity: 2, unitPrice: 10, taxRate: 0.13 }
  ]);
  state.recalcLines();
  assert.equal(state.form.value.totalAmount, 25, '原普通单金额计算保持可用，不用计算结果授予来源资格');
  await state.submitForm();
  assert.equal(state.calls.filter((call) => call.method).length, 0);
  assert.equal(state.form.value.requisitionId, null);
  assert.equal(state.form.value.requisitionNo, null);
  assert.equal(state.lineList.value[0].requisitionLineId, undefined);
});

test('旧订单读取并编辑仍保留申请头行来源，不清除来源冒充手工', async () => {
  const data = {
    orderId: '2105000000000000100',
    orderNo: 'PO-CONVERTED',
    exchangeRate: 1,
    freightAmount: 5,
    requisitionId: '2105000000000000200',
    requisitionNo: 'REQ-001',
    lineList: [{ requisitionLineId: '2105000000000000201', materialId: '1001', orderQuantity: 2, unitPrice: 10 }]
  };
  const state = saveContext(data, []);
  await state.getInfo(data.orderId);
  assert.equal(state.form.value.requisitionNo, 'REQ-001');
  await state.submitForm();
  const apiCalls = state.calls.filter((call) => call.method);
  assert.equal(apiCalls.length, 1);
  assert.equal(apiCalls[0].method, 'update');
  assert.equal(apiCalls[0].data.totalAmount, 25, '合法普通历史单的提交金额覆盖保持，不支持的新增仍为零写入');
  assert.equal(apiCalls[0].data.requisitionId, data.requisitionId);
  assert.equal(apiCalls[0].data.requisitionNo, data.requisitionNo);
  assert.equal(apiCalls[0].data.lineList[0].requisitionLineId, data.lineList[0].requisitionLineId);
});

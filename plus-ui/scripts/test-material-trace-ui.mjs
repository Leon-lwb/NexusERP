import test from 'node:test';
import { createServer } from 'node:http';
import axios from 'axios';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc';
import { build } from 'esbuild';

const output = await build({
  entryPoints: ['scripts/material-trace-ui-fixture.ts'],
  bundle: true,
  write: false,
  format: 'esm',
  platform: 'node',
  plugins: [
    {
      name: 'actual-vue-interactions',
      setup(build) {
        build.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'request', namespace: 'fixture' }));
        build.onResolve({ filter: /^@\/components\/MaterialSelectDialog\/index.vue$/ }, () => ({ path: 'materialDialog', namespace: 'fixture' }));
        build.onLoad({ filter: /.*/, namespace: 'fixture' }, ({ path }) => ({
          contents:
            path === 'request'
              ? 'export default (config) => globalThis.__traceUiRequest(config);'
              : `import { defineComponent, h } from 'vue'; export default defineComponent({ emits: ['select'], setup(_props, { emit, expose }) { expose({ open: () => emit('select', { materialId: '9223372036854775807', materialCode: 'MAT-1', materialName: '测试物料' }) }); return () => h('material-dialog'); } });`,
          loader: 'js',
          resolveDir: process.cwd()
        }));
        build.onLoad({ filter: /\.vue$/ }, async ({ path }) => {
          const { descriptor } = parse(await readFile(path, 'utf8'), { filename: path });
          const script = compileScript(descriptor, { id: path, inlineTemplate: true });
          return { contents: script.content, loader: 'ts', resolveDir: dirname(path) };
        });
      }
    }
  ]
});
const ui = await import(`data:text/javascript;base64,${Buffer.from(output.outputFiles[0].text).toString('base64')}`);
const policy = { batchManaged: true, serialManaged: true, allowMixedLot: false, version: 'v:opaque', stage: 'STRICT' };
const object = {
  objectType: 'SN',
  identityId: '9223372036854775807',
  localSnId: '9223372036854775806',
  canonicalSnCode: 'SN-1',
  materialId: '9223372036854775805',
  materialCode: 'MAT-1',
  batchId: '9223372036854775804',
  batchCode: 'LOT-1',
  policy
};

/** 实际组件在用户点击之前不发布歧义候选。 */
test('CodeResolver 候选列表必须点击后才能绑定', async () => {
  const resolved = [];
  const states = [];
  globalThis.__traceUiRequest = async () => ({
    data: { status: 'AMBIGUOUS', candidates: [object, { ...object, identityId: '2', canonicalSnCode: 'SN-2' }] }
  });
  const component = ui.mount('resolver', { scene: 'TRANSFER', onResolved: (value) => resolved.push(value), onState: (state) => states.push(state) });
  await component.vm.resolve('EXT-AMBIGUOUS');
  await ui.nextTick();
  assert.equal(resolved.length, 0);
  assert.match(states.at(-1).error, /歧义/);
  await ui.clickText(component.root, '选择此项');
  assert.equal(resolved.length, 1);
  assert.equal(resolved[0].identityId, '9223372036854775807');
  assert.equal(states.at(-1).error, '');
  component.unmount();
});
test('CodeResolver 旧异步结果不会覆盖新扫描或新来源上下文', async () => {
  const resolved = [];
  let finishFirst;
  globalThis.__traceUiRequest = ({ data }) =>
    data.rawCode === 'FIRST'
      ? new Promise((resolve) => {
          finishFirst = resolve;
        })
      : Promise.resolve({ data: { ...object, status: 'RESOLVED', identityId: '2', canonicalSnCode: 'SN-2' } });
  const component = ui.mount('resolver', { scene: 'TRANSFER', context: { sourceDocId: '1' }, onResolved: (value) => resolved.push(value) });
  const first = component.vm.resolve('FIRST');
  await component.vm.resolve('SECOND');
  finishFirst({ data: { ...object, status: 'RESOLVED' } });
  await first;
  assert.equal(resolved.length, 1);
  assert.equal(resolved[0].identityId, '2');
  const oldContext = component.vm.resolve('FIRST');
  component.update({ context: { sourceDocId: '99' } });
  await ui.nextTick();
  finishFirst({ data: { ...object, status: 'RESOLVED' } });
  await oldContext;
  assert.equal(resolved.length, 1);
  component.unmount();
});
test('SnSelector 完整 OCR 解析失败时保持原物料 ID、编码、批次与明细版本', async () => {
  const row = {
    materialId: '9223372036854775805',
    materialCode: 'MAT-1',
    batchId: '9223372036854775804',
    batchCode: 'LOT-1',
    quantity: '1',
    detailVersion: 'detail:opaque',
    policy
  };
  let sent;
  globalThis.__traceUiRequest = async (config) => {
    sent = config.data;
    return { data: { ...object, status: 'RESOLVED', materialId: '99', materialCode: 'OTHER' } };
  };
  const component = ui.mount('selector', {
    row,
    header: { receiveId: '9223372036854775803' },
    scene: 'PURCHASE_RECEIVE',
    bindObject: true,
    onChange: (value) => Object.assign(row, value)
  });
  const ocr = { materialCode: 'OTHER', matchedMaterial: { materialId: '99' }, rawTexts: ['all evidence'] };
  await component.vm.resolve(ocr);
  assert.deepEqual(JSON.parse(sent.rawCode), ocr);
  assert.equal(row.materialId, '9223372036854775805');
  assert.equal(row.materialCode, 'MAT-1');
  assert.equal(row.batchId, '9223372036854775804');
  assert.equal(row.detailVersion, 'detail:opaque');
  assert.match(row._traceError, /物料/);
  assert.equal(row.snItems, undefined);
  component.unmount();
});
test('SerialRegistration 使用实际登记 UI 重放原命令且不标记在库', async () => {
  const requests = [];
  globalThis.__traceUiRequest = async (config) => {
    requests.push(config);
    return {
      data: {
        reservationId: '9223372036854775806',
        commandId: config.data.commandId,
        fingerprint: 'sha256:original',
        identities: [{ identityId: '9223372036854775805', snCode: 'SN-1', materialId: '9223372036854775807' }],
        status: 'RESERVED'
      }
    };
  };
  const component = ui.mount('registration', {});
  component.vm.open();
  await ui.nextTick();
  await ui.clickText(component.root, '选择');
  await ui.clickText(component.root, '确认登记');
  await ui.clickText(component.root, '重放原登记命令');
  assert.equal(requests.length, 2);
  assert.equal(requests[0].url, '/erp/wm/sn/reserve');
  assert.deepEqual(requests[0].data, requests[1].data);
  assert.equal(requests[0].data.materialId, '9223372036854775807');
  assert.equal(requests[0].data.status, undefined);
  assert.equal(requests[0].data.sourceType, 'ERP_MANUAL');
  assert.equal(requests[0].data.sourceId, null);
  component.unmount();
});
/** 子组件只发出完整行更新，不直接改写父组件传入的业务对象。 */
test('SnSelector 通过事件提交原子行更新而不修改 prop', async () => {
  const row = { materialId: '9223372036854775805', materialCode: 'MAT-1', batchId: '9223372036854775804', quantity: '1', policy };
  const changes = [];
  globalThis.__traceUiRequest = async () => ({ data: { ...object, status: 'RESOLVED' } });
  const component = ui.mount('selector', { row, header: {}, scene: 'TRANSFER', onChange: (change) => changes.push(change) });
  await component.vm.resolve('SN-1');
  assert.equal(row.snItems, undefined);
  assert.ok(changes.some((change) => change.snItems?.[0].identityId === '9223372036854775807'));
  component.unmount();
});

/** Vue 编译器验证任务内改动的实际页面，禁止相邻模板结构被误删。 */
test('所有变更 Vue 页面保持模板可编译', async () => {
  // 固定校验文件范围，使暂存、提交和合并后的干净工作区保持相同测试覆盖。
  const files = [
    'plus-ui/src/components/MaterialSelectDialog/index.vue',
    'plus-ui/src/components/cv/LabelCapture.vue',
    'plus-ui/src/components/material-trace/BatchRecallPanel.vue',
    'plus-ui/src/components/material-trace/BatchTracePanel.vue',
    'plus-ui/src/components/material-trace/CodeResolver.vue',
    'plus-ui/src/components/material-trace/CompletionManifestPanel.vue',
    'plus-ui/src/components/material-trace/ComponentCorrectionDialog.vue',
    'plus-ui/src/components/material-trace/IdentityTracePanel.vue',
    'plus-ui/src/components/material-trace/MaterialTraceLines.vue',
    'plus-ui/src/components/material-trace/ProductOutputDialog.vue',
    'plus-ui/src/components/material-trace/ProductionReceiptDialog.vue',
    'plus-ui/src/components/material-trace/SerialAliasDialog.vue',
    'plus-ui/src/components/material-trace/SerialRegistration.vue',
    'plus-ui/src/components/material-trace/SerialRetireDialog.vue',
    'plus-ui/src/components/material-trace/SnSelector.vue',
    'plus-ui/src/components/material-trace/SubmissionStateNotice.vue',
    'plus-ui/src/components/material-trace/TraceEvidence.vue',
    'plus-ui/src/components/material-trace/TraceSectionTable.vue',
    'plus-ui/src/views/erp/bd/material/index.vue',
    'plus-ui/src/views/erp/bd/serial-number-rule/index.vue',
    'plus-ui/src/views/erp/fin/cost/index.vue',
    'plus-ui/src/views/erp/wm/barcode/index.vue',
    'plus-ui/src/views/erp/wm/batch/form.vue',
    'plus-ui/src/views/erp/wm/batch/index.vue',
    'plus-ui/src/views/erp/wm/miscissue/form.vue',
    'plus-ui/src/views/erp/wm/miscrecpt/form.vue',
    'plus-ui/src/views/erp/wm/productionissue/form.vue',
    'plus-ui/src/views/erp/wm/productionrecpt/index.vue',
    'plus-ui/src/views/erp/wm/receive/form.vue',
    'plus-ui/src/views/erp/wm/receive/index.vue',
    'plus-ui/src/views/erp/wm/return/form.vue',
    'plus-ui/src/views/erp/wm/sales-issue/form.vue',
    'plus-ui/src/views/erp/wm/sales-issue/index.vue',
    'plus-ui/src/views/erp/wm/sn/form.vue',
    'plus-ui/src/views/erp/wm/sn/index.vue',
    'plus-ui/src/views/erp/wm/stock/index.vue',
    'plus-ui/src/views/erp/wm/stocktaking/form.vue',
    'plus-ui/src/views/erp/wm/trace/index.vue',
    'plus-ui/src/views/erp/wm/trace/repair/index.vue',
    'plus-ui/src/views/erp/wm/transfer/form.vue',
    'plus-ui/src/views/erp/wm/transfer/index.vue',
    'plus-ui/src/views/mes/lt/consumption/index.vue',
    'plus-ui/src/views/mes/pro/dispatch/components/ScanExecute.vue',
    'plus-ui/src/views/mes/pro/dispatch/detail.vue',
    'plus-ui/src/views/mes/pro/dispatch/index.vue',
    'plus-ui/src/views/mes/pro/flow-card/detail.vue',
    'plus-ui/src/views/mes/pro/sn/index.vue',
    'plus-ui/src/views/mes/pro/sn/trace.vue',
    'plus-ui/src/views/mes/pro/workorder/detail.vue'
  ];
  for (const filename of files) {
    const source = await readFile(filename.slice('plus-ui/'.length), 'utf8');
    const { descriptor, errors } = parse(source, { filename });
    assert.deepEqual(errors, [], filename);
    const template = compileTemplate({ source: descriptor.template.content, id: filename, filename });
    assert.deepEqual(template.errors, [], filename);
  }
});

/** 请求实际组件的下一页，其他授权模块不丢失，上页从缓存读取。 */
test('IdentityTracePanel 实际翻页保留其它模块并使用原 section 游标', async () => {
  const id = '9223372036854775807';
  const initial = {
    identityId: id,
    scope: 'IDENTITY',
    truncated: true,
    parts: [
      {
        module: 'ERP',
        identityId: id,
        scope: 'IDENTITY',
        sections: {
          STOCK: { rows: [{ stockId: 'stock-first', quantity: '0.100000000000000001' }], hasMore: true, nextCursor: '9223372036854775806' }
        },
        evidenceGaps: []
      },
      {
        module: 'MES',
        identityId: id,
        scope: 'IDENTITY',
        sections: { TEST: { rows: [{ testId: 'MES-EVIDENCE-KEEP' }], hasMore: false } },
        evidenceGaps: []
      }
    ],
    evidenceGaps: []
  };
  const requests = [];
  globalThis.__traceUiRequest = async (config) => {
    requests.push(config);
    return {
      data: {
        identityId: id,
        scope: 'IDENTITY',
        parts: [
          {
            module: 'ERP',
            identityId: id,
            scope: 'IDENTITY',
            sections: { STOCK: { rows: [{ stockId: 'stock-next' }], hasMore: false } },
            evidenceGaps: []
          }
        ]
      }
    };
  };
  const component = ui.mount('identity', { result: initial });
  await ui.nextTick();
  await ui.clickText(component.root, 'ERP STOCK 下一页');
  assert.equal(requests[0].params.cursor, '9223372036854775806');
  assert.equal(requests[0].params.module, 'ERP');
  assert.equal(requests[0].params.section, 'STOCK');
  assert.match(ui.renderedText(component.root), /stock-next/);
  assert.match(ui.renderedText(component.root), /MES-EVIDENCE-KEEP/);
  await ui.clickText(component.root, '上一页');
  assert.equal(requests.length, 1);
  assert.match(ui.renderedText(component.root), /0.100000000000000001/);
  component.unmount();
});
test('IdentityTracePanel 切换身份后忽略旧分页的迟到结果', async () => {
  const id = '9223372036854775807';
  const initial = {
    identityId: id,
    scope: 'IDENTITY',
    parts: [
      {
        module: 'ERP',
        identityId: id,
        scope: 'IDENTITY',
        sections: { STOCK: { rows: [{ stockId: 'first' }], hasMore: true, nextCursor: 'cursor-A' } }
      }
    ]
  };
  let finish;
  globalThis.__traceUiRequest = () =>
    new Promise((resolve) => {
      finish = resolve;
    });
  const component = ui.mount('identity', { result: initial });
  await ui.nextTick();
  const next = ui.clickText(component.root, 'ERP STOCK 下一页');
  component.update({
    result: {
      ...initial,
      identityId: 'NEW-IDENTITY',
      parts: [
        { module: 'ERP', identityId: 'NEW-IDENTITY', scope: 'IDENTITY', sections: { STOCK: { rows: [{ stockId: 'NEW-STOCK' }], hasMore: false } } }
      ]
    }
  });
  await ui.nextTick();
  finish({
    data: {
      ...initial,
      parts: [{ module: 'ERP', identityId: id, scope: 'IDENTITY', sections: { STOCK: { rows: [{ stockId: 'LATE-OLD-STOCK' }], hasMore: false } } }]
    }
  });
  await next;
  assert.match(ui.renderedText(component.root), /NEW-STOCK/);
  assert.doesNotMatch(ui.renderedText(component.root), /LATE-OLD-STOCK/);
  component.unmount();
});
test('BatchRecallPanel 实际分页使用 afterIdentity，并发出受影响正式身份', async () => {
  const requests = [];
  const identities = [];
  const batchId = '9223372036854775807';
  globalThis.__traceUiRequest = async (config) => {
    requests.push(config);
    return {
      data:
        config.params.afterIdentity === '0'
          ? {
              batchId,
              scope: 'BATCH_COMPONENT_RECALL',
              parts: [],
              rows: [{ identityId: '9223372036854775806' }],
              hasMore: true,
              nextCursor: '9223372036854775806',
              depthReached: '2',
              evidenceGaps: []
            }
          : {
              batchId,
              scope: 'BATCH_COMPONENT_RECALL',
              parts: [],
              rows: [{ identityId: '9223372036854775805' }],
              hasMore: false,
              truncated: true,
              truncatedByDepth: true,
              evidenceGaps: ['历史映射缺口']
            }
    };
  };
  const component = ui.mount('recall', { batchId, onIdentity: (value) => identities.push(value) });
  await Promise.resolve();
  await ui.nextTick();
  await ui.clickText(component.root, '召回下一页');
  assert.equal(requests[0].params.afterIdentity, '0');
  assert.equal(requests[1].params.afterIdentity, '9223372036854775806');
  assert.equal(requests[1].params.depth, 10);
  assert.match(ui.renderedText(component.root), /深度/);
  assert.match(ui.renderedText(component.root), /历史映射缺口/);
  await ui.clickText(component.root, '查看实物追溯');
  assert.deepEqual(identities, ['9223372036854775805']);
  component.unmount();
});

/** HTTP仅连接本测试的回环随机端口，不连接真实ERP服务、数据库或外部地址。 */
async function untilVisible(root, expected) {
  for (let attempt = 0; attempt < 250; attempt++) {
    await ui.nextTick();
    if (expected.test(ui.renderedText(root))) return;
    await new Promise((resolve) => setTimeout(resolve, 10));
  }
  assert.match(ui.renderedText(root), expected);
}
const scopeImpact = (mesWoId, quantity = '0.100000000000000001') => ({
  module: 'MES',
  scope: 'WORK_ORDER',
  consumptionId: '9223372036854775803',
  mesWoId,
  componentMaterialId: '9223372036854775802',
  consumedQuantity: quantity,
  allocatedQuantity: '0.000000000000000001',
  unallocatedQuantity: '0.100000000000000000'
});
test('真实HTTP召回：工单范围与身份各自分页，保留精度、长ID和小数原文', async () => {
  const batchId = '9223372036854775807';
  const scopeCursor = 'MES/工单+9223372036854775807/?s=1&x=2';
  const requests = [];
  const server = createServer((request, response) => {
    const url = new URL(request.url, 'http://127.0.0.1');
    requests.push({ method: request.method, path: url.pathname, params: Object.fromEntries(url.searchParams) });
    const scopeNext = url.searchParams.get('scopeCursor') === scopeCursor;
    const identityNext = url.searchParams.get('afterIdentity') !== '0';
    const data = {
      batchId,
      scope: 'BATCH_COMPONENT_RECALL',
      parts: [],
      rows: identityNext
        ? [{ identityId: 'IDENTITY-PAGE-2' }]
        : [
            { identityId: 'IDENTITY-PAGE-1' },
            { identityId: 'BATCH-POSSIBLE', scope: 'BATCH_POSSIBLE' },
            { identityId: 'WO-POSSIBLE', scope: 'WORK_ORDER_POSSIBLE' }
          ],
      hasMore: !identityNext,
      nextCursor: identityNext ? null : '9223372036854775806',
      scopeImpacts: {
        rows: [scopeImpact(scopeNext ? 'WO-PAGE-2-9223372036854775805' : 'WO-PAGE-1-9223372036854775804')],
        hasMore: !scopeNext,
        nextCursor: scopeNext ? null : scopeCursor
      },
      evidenceGaps: ['只有工单消耗事实，未证明全部单件绑定']
    };
    // 分页调用只能更新它自己的范围，响应中另一个范围的数据不可覆盖已显示的页。
    if (scopeNext && !identityNext) {
      data.rows = [{ identityId: 'SCOPE-RESPONSE-MUST-NOT-REPLACE-IDENTITY' }];
      data.truncated = true;
      data.truncatedByCapacity = true;
    }
    if (scopeNext && identityNext) data.scopeImpacts.rows = [scopeImpact('IDENTITY-RESPONSE-MUST-NOT-REPLACE-WO')];
    response.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
    response.end(JSON.stringify({ code: 200, data }));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const client = axios.create({ baseURL: `http://127.0.0.1:${server.address().port}`, adapter: 'http', proxy: false });
  globalThis.__traceUiRequest = async (config) => (await client(config)).data;
  const component = ui.mount('recall', { batchId });
  try {
    await untilVisible(component.root, /WO-PAGE-1/);
    let text = ui.renderedText(component.root);
    assert.match(text, /逐件确认/);
    assert.match(text, /批次可能影响/);
    assert.match(text, /工单可能影响/);
    assert.match(text, /工单范围.*未确认逐件/);
    assert.match(text, /0\.100000000000000001/);
    assert.match(text, /9223372036854775803/);
    assert.match(text, /0\.000000000000000001/);
    await ui.clickText(component.root, '工单范围下一页');
    assert.equal(requests.at(-1).params.scopeCursor, scopeCursor);
    assert.equal(requests.at(-1).params.afterIdentity, '0');
    text = ui.renderedText(component.root);
    assert.match(text, /WO-PAGE-2/);
    assert.match(text, /容量截断/);
    assert.match(text, /IDENTITY-PAGE-1/);
    assert.doesNotMatch(text, /MUST-NOT-REPLACE/);
    await ui.clickText(component.root, '召回下一页');
    assert.equal(requests.at(-1).params.afterIdentity, '9223372036854775806');
    assert.equal(requests.at(-1).params.scopeCursor, scopeCursor);
    text = ui.renderedText(component.root);
    assert.match(text, /IDENTITY-PAGE-2/);
    assert.match(text, /WO-PAGE-2/);
    assert.doesNotMatch(text, /MUST-NOT-REPLACE/);
    const count = requests.length;
    await ui.clickText(component.root, '工单范围上一页');
    assert.match(ui.renderedText(component.root), /WO-PAGE-1/);
    assert.match(ui.renderedText(component.root), /IDENTITY-PAGE-2/);
    await ui.clickText(component.root, '上一页');
    assert.match(ui.renderedText(component.root), /IDENTITY-PAGE-1/);
    assert.equal(requests.length, count);
    assert.ok(requests.every((request) => request.method === 'GET' && request.path === '/erp/wm/trace/recall' && request.params.batchId === batchId));
  } finally {
    component.unmount();
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
});
test('工单范围分页缺失游标或游标重复时阻断且保留身份页', async () => {
  const requests = [];
  const batchId = 'BATCH-CURSOR';
  globalThis.__traceUiRequest = async (config) => {
    requests.push(config);
    return {
      data: {
        batchId,
        scope: 'BATCH_COMPONENT_RECALL',
        parts: [],
        rows: [{ identityId: 'IDENTITY-RETAINED' }],
        hasMore: false,
        scopeImpacts: { rows: [scopeImpact(`WORK-ORDER-${requests.length}`)], hasMore: true, nextCursor: 'scope:repeat' }
      }
    };
  };
  const component = ui.mount('recall', { batchId });
  await untilVisible(component.root, /WORK-ORDER-1/);
  await ui.clickText(component.root, '工单范围下一页');
  await ui.clickText(component.root, '工单范围下一页');
  assert.equal(requests.length, 2);
  assert.match(ui.renderedText(component.root), /游标未推进/);
  assert.match(ui.renderedText(component.root), /IDENTITY-RETAINED/);
  component.unmount();
  globalThis.__traceUiRequest = async () => ({
    data: {
      batchId,
      scope: 'BATCH_COMPONENT_RECALL',
      parts: [],
      rows: [],
      hasMore: false,
      scopeImpacts: { rows: [scopeImpact('MISSING-CURSOR')], hasMore: true }
    }
  });
  const missing = ui.mount('recall', { batchId });
  await untilVisible(missing.root, /MISSING-CURSOR/);
  await ui.clickText(missing.root, '工单范围下一页');
  assert.match(ui.renderedText(missing.root), /未提供有效游标/);
  missing.unmount();
});
test('批次变化后迟到的工单范围页不能覆盖新批次，旧契约缺字段不宣称零影响', async () => {
  let finish;
  globalThis.__traceUiRequest = (config) =>
    config.params.scopeCursor
      ? new Promise((resolve) => {
          finish = resolve;
        })
      : Promise.resolve({
          data: {
            batchId: config.params.batchId,
            scope: 'BATCH_COMPONENT_RECALL',
            parts: [],
            rows: [{ identityId: `IDENTITY-${config.params.batchId}` }],
            hasMore: false,
            ...(config.params.batchId === 'OLD'
              ? { scopeImpacts: { rows: [scopeImpact('OLD-WORK-ORDER')], hasMore: true, nextCursor: 'scope:old' } }
              : {})
          }
        });
  const component = ui.mount('recall', { batchId: 'OLD' });
  await untilVisible(component.root, /OLD-WORK-ORDER/);
  const next = ui.clickText(component.root, '工单范围下一页');
  component.update({ batchId: 'NEW' });
  await untilVisible(component.root, /IDENTITY-NEW/);
  finish({
    data: {
      batchId: 'OLD',
      scope: 'BATCH_COMPONENT_RECALL',
      parts: [],
      rows: [],
      hasMore: false,
      scopeImpacts: { rows: [scopeImpact('LATE-OLD-WORK-ORDER')], hasMore: false }
    }
  });
  await next;
  assert.match(ui.renderedText(component.root), /未提供工单范围/);
  assert.doesNotMatch(ui.renderedText(component.root), /OLD-WORK-ORDER/);
  assert.match(ui.renderedText(component.root), /IDENTITY-NEW/);
  component.unmount();
});

const manualIdentity = {
  snId: '9223372036854775806',
  identityId: '9223372036854775806',
  snCode: 'MANUAL-SN-1',
  sourceDocType: 'ERP_MANUAL',
  sourceDocId: null,
  status: 'REGISTERED',
  identityVersion: '9223372036854775805',
  stockId: null,
  warehouseId: null,
  locationId: null
};
const reasonPlaceholder = '请填写弃用原因（必填，最多 500 字）';
test('真实HTTP弃用：提交后ACK丢失，关闭再开仍重放原版本/原因/命令，仅接POST不删号', async () => {
  const requests = [];
  const retired = [];
  let writes = 0;
  const server = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    const data = body ? JSON.parse(body) : undefined;
    requests.push({ method: request.method, url: request.url, data });
    if (request.method === 'POST' && ++writes === 1) {
      request.socket.destroy();
      return;
    }
    const result =
      request.method === 'GET'
        ? writes
          ? { ...manualIdentity, status: 'RETIRED', identityVersion: '9223372036854775806' }
          : manualIdentity
        : {
            commandId: data.commandId,
            identityId: manualIdentity.identityId,
            reservationId: '9223372036854775804',
            reservationStatus: 'CANCELLED',
            status: 'RETIRED',
            version: '9223372036854775806',
            operationId: '9223372036854775803'
          };
    response.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
    response.end(JSON.stringify({ code: 200, data: result }));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const client = axios.create({ baseURL: `http://127.0.0.1:${server.address().port}`, adapter: 'http', proxy: false });
  globalThis.__traceUiRequest = async (config) => (await client(config)).data;
  const component = ui.mount('retire', { onRetired: (result) => retired.push(result) });
  try {
    await component.vm.open(manualIdentity);
    await ui.nextTick();
    await ui.inputText(component.root, reasonPlaceholder, '  登记选错物料，保留原号码和原因  ');
    await ui.clickText(component.root, '确认弃用');
    assert.equal(writes, 1);
    assert.equal(retired.length, 0);
    assert.match(ui.renderedText(component.root), /socket|hang up/);
    await ui.clickText(component.root, '关闭');
    await component.vm.open(manualIdentity);
    await ui.nextTick();
    assert.equal(requests.filter((request) => request.method === 'GET').length, 1, '原结果待确认时不得用新详情重建命令');
    await ui.clickText(component.root, '确认弃用');
    assert.equal(writes, 2);
    assert.equal(retired.length, 1);
    assert.match(ui.renderedText(component.root), /已弃用.*原号码永久保留/);
    assert.deepEqual(requests[1].data, requests[2].data);
    assert.equal(requests[1].data.expectedVersion, manualIdentity.identityVersion);
    assert.equal(requests[1].data.reason, '  登记选错物料，保留原号码和原因  ');
    assert.ok(requests[1].data.commandId);
    assert.equal(requests[0].method, 'GET');
    assert.equal(requests[0].url, `/erp/wm/sn/${manualIdentity.identityId}`);
    assert.ok(requests.slice(1).every((request) => request.method === 'POST' && request.url === `/erp/wm/sn/${manualIdentity.identityId}/retire`));
    await ui.clickText(component.root, '重放原弃用命令');
    assert.deepEqual(requests[3].data, requests[1].data);
  } finally {
    component.unmount();
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
});
for (const [name, change, message] of [
  ['缺少原版本', { identityVersion: undefined }, /身份原版本/],
  ['版本被Number化', { identityVersion: 1 }, /身份原版本/],
  ['已在库身份', { status: 'IN_STOCK' }, /仅允许/],
  ['MES来源身份', { sourceDocType: 'MES' }, /仅允许/],
  ['绑定库位身份', { locationId: '9223372036854775802' }, /库存维度/]
]) {
  test(`弃用弹窗对${name}明确阻断，不发POST`, async () => {
    const requests = [];
    globalThis.__traceUiRequest = async (config) => {
      requests.push(config);
      return { data: { ...manualIdentity, ...change } };
    };
    const component = ui.mount('retire', {});
    await component.vm.open(manualIdentity);
    await ui.nextTick();
    assert.match(ui.renderedText(component.root), message);
    await assert.rejects(ui.clickText(component.root, '确认弃用'), /不可用/);
    assert.equal(requests.length, 1);
    assert.equal(requests[0].method, 'get');
    component.unmount();
  });
}
test('弃用必须填写有效原因；服务端拒绝使用证据时不宣称成功', async () => {
  const requests = [];
  const retired = [];
  globalThis.__traceUiRequest = async (config) => {
    requests.push(config);
    if (config.method === 'get') return { data: manualIdentity };
    throw new Error('身份已有单据使用证据');
  };
  const component = ui.mount('retire', { onRetired: (result) => retired.push(result) });
  await component.vm.open(manualIdentity);
  await ui.nextTick();
  for (const reason of [' ', '字'.repeat(501)]) {
    await ui.inputText(component.root, reasonPlaceholder, reason);
    await ui.clickText(component.root, '确认弃用');
    assert.match(ui.renderedText(component.root), /原因必填|500字/);
    assert.equal(requests.length, 1);
  }
  await ui.inputText(component.root, reasonPlaceholder, '登记错误');
  await ui.clickText(component.root, '确认弃用');
  assert.match(ui.renderedText(component.root), /单据使用证据/);
  assert.equal(retired.length, 0);
  component.unmount();
});
test('SN原列表与详情提供remove权限弃用入口，保持永久保留号码行为', async () => {
  for (const path of ['src/views/erp/wm/sn/index.vue', 'src/views/erp/wm/sn/form.vue']) {
    const source = await readFile(path, 'utf8');
    assert.match(source, /retireDialog\?\.open/);
    assert.match(source, /v-hasPermi="\['wm:sn:remove'\]"/);
    assert.match(source, /<SerialRetireDialog/);
    assert.doesNotMatch(source, /handleDelete|delSn/);
  }
});

test('真实HTTP批次根事实挂在Recall：独立库存分页保留客户去向/来源/MES lot，不猜费用', async () => {
  const requests = [];
  const batchId = '9223372036854775807';
  const cursor = 'batch/库存+9223372036854775806';
  const parts = [
    {
      module: 'ERP',
      batchId,
      scope: 'BATCH',
      sections: {
        STOCK: { rows: [{ warehouseName: 'CURRENT-WAREHOUSE', quantity: '0.100000000000000001' }], hasMore: true, nextCursor: cursor },
        MOVEMENTS: { rows: [{ customerName: 'ACTUAL-CUSTOMER', documentId: '9223372036854775805' }], hasMore: false },
        SOURCE: { rows: [{ warehouseName: 'SOURCE-WAREHOUSE' }], hasMore: false }
      },
      evidenceGaps: ['仅授权ERP字段']
    },
    { module: 'MES', batchId, scope: 'BATCH', sections: { LOTS: { rows: [{ lotCode: 'MES-ACTUAL-LOT' }], hasMore: false } } }
  ];
  const server = createServer((request, response) => {
    const url = new URL(request.url, 'http://127.0.0.1');
    requests.push({ path: url.pathname, params: Object.fromEntries(url.searchParams) });
    const data = url.pathname.endsWith('/recall')
      ? { batchId, scope: 'BATCH_COMPONENT_RECALL', rows: [], hasMore: false, parts, scopeImpacts: { rows: [], hasMore: false } }
      : {
          batchId,
          scope: 'BATCH',
          parts: [{ ...parts[0], sections: { STOCK: { rows: [{ warehouseName: 'NEXT-CURRENT-WAREHOUSE' }], hasMore: false } } }]
        };
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ code: 200, data }));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const client = axios.create({ baseURL: `http://127.0.0.1:${server.address().port}`, adapter: 'http', proxy: false });
  globalThis.__traceUiRequest = async (config) => (await client(config)).data;
  const component = ui.mount('recall', { batchId });
  try {
    await untilVisible(component.root, /CURRENT-WAREHOUSE/);
    assert.equal(requests.length, 1);
    assert.match(ui.renderedText(component.root), /当前仓库.*STOCK/);
    assert.match(ui.renderedText(component.root), /客户去向/);
    assert.match(ui.renderedText(component.root), /0\.100000000000000001/);
    assert.doesNotMatch(ui.renderedText(component.root), /unitCost|txAmount/);
    await ui.clickText(component.root, 'ERP STOCK 批次下一页');
    assert.equal(requests.at(-1).path, `/erp/wm/trace/batch/${batchId}`);
    assert.deepEqual(requests.at(-1).params, { limit: '500', module: 'ERP', section: 'STOCK', cursor });
    const text = ui.renderedText(component.root);
    for (const value of ['NEXT-CURRENT-WAREHOUSE', 'ACTUAL-CUSTOMER', 'SOURCE-WAREHOUSE', 'MES-ACTUAL-LOT']) assert.ok(text.includes(value), value);
    const count = requests.length;
    await ui.clickText(component.root, 'ERP STOCK 批次上一页');
    assert.equal(requests.length, count);
    assert.match(ui.renderedText(component.root), /CURRENT-WAREHOUSE/);
  } finally {
    component.unmount();
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
});

/** 真实HTTP覆盖产出先写、ACK丢失、原完成失败与重试；没有PC直接入库。 */
test('实际产出UI先保存真实来源整lot，再走原complete；ACK丢失和后续完成失败均不新增产出', async () => {
  const cardId = '9223372036854775807',
    stepRecordId = '9223372036854775806',
    materialId = '9223372036854775805',
    mesWoId = '9223372036854775804';
  const calls = [];
  let writes = 0,
    finishes = 0;
  const context = {
    cardId,
    stepRecordId,
    materialId,
    mesWoId,
    isFinalStep: true,
    policy: { ...policy, serialManaged: false },
    outputs: [],
    sources: [
      {
        lotId: '9223372036854775803',
        lotCode: 'REAL-SCRAP',
        sourceAllocationId: '9223372036854775802',
        materialId,
        mesWoId,
        quantity: '2.000000000000000001',
        status: 'SCRAPPED',
        qcStatus: 'FAIL'
      }
    ]
  };
  const server = createServer(async (request, response) => {
    const url = new URL(request.url, 'http://127.0.0.1');
    let body = '';
    for await (const chunk of request) body += chunk;
    calls.push({ path: url.pathname, method: request.method, params: Object.fromEntries(url.searchParams), data: body ? JSON.parse(body) : null });
    if (request.method === 'GET') {
      response.end(
        JSON.stringify({
          code: 200,
          data: { cardId, materialId, mesWoId, steps: [{ cardId, stepRecordId, stepStatus: 'IN_PROGRESS', productOutputContext: context }] }
        })
      );
      return;
    }
    if (url.pathname.endsWith('/product-output')) {
      writes++;
      if (writes === 1) {
        response.destroy();
        return;
      }
      response.end(JSON.stringify({ code: 200, data: ['9223372036854775801'] }));
      return;
    }
    finishes++;
    if (finishes === 1) {
      response.writeHead(409);
      response.end('original complete temporary failure');
      return;
    }
    response.end(JSON.stringify({ code: 200, data: null }));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const client = axios.create({ baseURL: `http://127.0.0.1:${server.address().port}`, adapter: 'http', proxy: false });
  globalThis.__traceUiRequest = async (config) => (await client(config)).data;
  const component = ui.mount('outputs', {});
  try {
    const completed = component.vm.run({ cardId, stepRecordId, goodQty: '0', scrapQty: '2.000000000000000001' }, () =>
      client.put('/mes/pro/flow-card/complete-step', null, {
        params: { cardId, stepRecordId, goodQty: '0', defectQty: '0', scrapQty: '2.000000000000000001' }
      })
    );
    await untilVisible(component.root, /实际报废 2\.000000000000000001/);
    await ui.inputText(component.root, '选择实际产出批次', '9223372036854775803/9223372036854775802');
    await ui.clickText(component.root, '保存实际产出并完工');
    assert.equal(finishes, 0);
    assert.equal(writes, 1);
    await ui.clickText(component.root, '重试原产出命令并完工');
    assert.equal(finishes, 1);
    assert.equal(writes, 2);
    await ui.clickText(component.root, '产出已保存，重试原完工');
    await completed;
    assert.equal(finishes, 2);
    assert.equal(writes, 2);
    const outputCalls = calls.filter((row) => row.path.endsWith('/product-output'));
    assert.deepEqual(outputCalls[0].data, outputCalls[1].data);
    assert.equal(outputCalls[0].data.lines[0].sourceAllocationId, '9223372036854775802');
    assert.equal(outputCalls[0].data.lines[0].quantity, '2.000000000000000001');
    assert.equal(outputCalls[0].data.lines[0].result, 'SCRAP');
    assert.ok(calls.findIndex((row) => row.path.endsWith('/product-output')) < calls.findIndex((row) => row.path.endsWith('/complete-step')));
    assert.equal(
      calls.some((row) => /stock|receipt/.test(row.path)),
      false
    );
  } finally {
    component.unmount();
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
});
test('batch-only缺原allocation读模型显式阻断原完成；普通数量路径仍原样完成', async () => {
  let complete = 0;
  let batchOnly = true;
  globalThis.__traceUiRequest = async (config) => ({
    data: config.url.includes('/flow-card/')
      ? { cardId: '1', materialId: '3', mesWoId: '4', steps: [{ stepRecordId: '2', stepStatus: 'IN_PROGRESS' }] }
      : { isBatchManage: batchOnly ? '1' : '0', isSerialManage: '0', allowMixedLot: '0', trackingVersion: '1', trackingStage: 'STRICT' }
  });
  const component = ui.mount('outputs', {});
  component.vm
    .run({ cardId: '1', stepRecordId: '2', goodQty: '1', scrapQty: '0' }, async () => {
      complete++;
    })
    .catch(() => {});
  await untilVisible(component.root, /原生产分配来源/);
  assert.equal(complete, 0);
  component.unmount();
  batchOnly = false;
  const ordinary = ui.mount('outputs', {});
  await ordinary.vm.run({ cardId: '1', stepRecordId: '2', goodQty: '1', scrapQty: '0' }, async () => {
    complete++;
  });
  assert.equal(complete, 1);
  ordinary.unmount();
});
test('manifest实际UI取消ACK丢失重试原修订与原因，只有CANCELLED开放refreeze', async () => {
  let manifest = {
    manifestId: '9223372036854775807',
    mesWoId: '9223372036854775806',
    revision: '17',
    status: 'APPLIED',
    goodQty: '1',
    scrapQty: '0'
  };
  let requests = [];
  let loseAck = true;
  globalThis.__traceUiRequest = async (config) => {
    requests.push(structuredClone(config));
    if (config.method === 'get') return { data: { completionState: { latest: structuredClone(manifest), revisions: [structuredClone(manifest)] } } };
    if (config.url.endsWith('/request-cancel')) {
      if (loseAck) {
        loseAck = false;
        throw new Error('ACK lost');
      }
      manifest.status = 'CANCEL_REQUESTED';
      return { data: null };
    }
    manifest = { ...manifest, revision: '18', status: 'FROZEN' };
    return { data: manifest };
  };
  const component = ui.mount('manifest', { mesWoId: manifest.mesWoId });
  try {
    await untilVisible(component.root, /APPLIED/);
    await ui.clickText(component.root, '申请取消当前修订');
    assert.equal(requests.filter((row) => row.method === 'post').length, 0);
    await ui.inputText(component.root, '填写取消或重新冻结原因', '核对真实产品批次后修订');
    await ui.clickText(component.root, '申请取消当前修订');
    await ui.clickText(component.root, '重试原取消请求');
    assert.match(ui.renderedText(component.root), /等待ERP取消屏障确认/);
    await assert.rejects(ui.clickText(component.root, '重新冻结已取消修订'), /按钮不可用/);
    const writes = requests.filter((row) => row.method === 'post');
    assert.deepEqual(writes[0].data, writes[1].data);
    assert.equal(writes[0].data.revision, '17');
    manifest.status = 'CANCELLED';
    await ui.clickText(component.root, '刷新服务端状态');
    await ui.inputText(component.root, '填写取消或重新冻结原因', '按审阅后的实际产出重新冻结');
    await ui.clickText(component.root, '重新冻结已取消修订');
    const last = requests.findLast((row) => row.method === 'post');
    assert.equal(last.url, '/mes/pro/completion-manifest/work-order/9223372036854775806/refreeze');
    assert.equal(last.data.cancelledRevision, '17');
    assert.match(ui.renderedText(component.root), /FROZEN/);
  } finally {
    component.unmount();
  }
});

test('R5无SN产出召回：受影响批次独立分页并按需读取实际客户去向，不被工单页覆盖', async () => {
  const root = '9223372036854775807',
    first = '9223372036854775806',
    second = '9223372036854775805',
    batchCursor = 'batch/影响+cursor',
    scopeCursor = 'scope/wo+cursor';
  const calls = [];
  const server = createServer((request, response) => {
    const url = new URL(request.url, 'http://127.0.0.1');
    const params = Object.fromEntries(url.searchParams);
    calls.push({ path: url.pathname, params });
    let data;
    if (url.pathname.includes('/batch/'))
      data = {
        batchId: first,
        scope: 'BATCH',
        parts: [
          {
            module: 'ERP',
            batchId: first,
            scope: 'BATCH',
            sections: {
              STOCK: { rows: [{ warehouseName: 'FINISHED-CURRENT-STOCK' }], hasMore: false },
              MOVEMENTS: { rows: [{ customerName: 'FINISHED-ACTUAL-CUSTOMER' }], hasMore: false }
            }
          }
        ]
      };
    else
      data = {
        batchId: root,
        scope: 'BATCH_COMPONENT_RECALL',
        parts: [],
        rows: [],
        hasMore: false,
        scopeImpacts: {
          rows: [scopeImpact(params.scopeCursor ? 'WO-NEXT' : 'WO-FIRST')],
          hasMore: !params.scopeCursor,
          nextCursor: params.scopeCursor ? null : scopeCursor
        },
        batchImpacts: {
          rows: [{ batchId: params.batchCursor ? second : first, scope: params.batchCursor ? 'BATCH_POSSIBLE' : 'WORK_ORDER_POSSIBLE' }],
          hasMore: !params.batchCursor,
          nextCursor: params.batchCursor ? null : batchCursor
        },
        evidenceGaps: ['仅工单范围、未确认逐件']
      };
    response.end(JSON.stringify({ code: 200, data }));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const client = axios.create({ baseURL: `http://127.0.0.1:${server.address().port}`, adapter: 'http', proxy: false });
  globalThis.__traceUiRequest = async (config) => (await client(config)).data;
  const component = ui.mount('recall', { batchId: root });
  try {
    await untilVisible(component.root, new RegExp(first));
    assert.equal(calls.length, 1);
    assert.match(ui.renderedText(component.root), /工单可能影响/);
    await ui.clickText(component.root, '查看受影响批次事实/客户去向');
    await untilVisible(component.root, /FINISHED-ACTUAL-CUSTOMER/);
    assert.equal(calls.length, 2);
    assert.equal(calls[1].path, `/erp/wm/trace/batch/${first}`);
    assert.match(ui.renderedText(component.root), /FINISHED-CURRENT-STOCK/);
    await ui.clickText(component.root, '受影响批次下一页');
    assert.equal(calls.at(-1).params.batchCursor, batchCursor);
    assert.equal(calls.at(-1).params.afterIdentity, '0');
    assert.equal(calls.at(-1).params.scopeCursor, undefined);
    assert.match(ui.renderedText(component.root), new RegExp(second));
    assert.match(ui.renderedText(component.root), /批次可能影响/);
    assert.match(ui.renderedText(component.root), /WO-FIRST/);
    await ui.clickText(component.root, '工单范围下一页');
    assert.equal(calls.at(-1).params.scopeCursor, scopeCursor);
    assert.equal(calls.at(-1).params.batchCursor, batchCursor);
    assert.match(ui.renderedText(component.root), new RegExp(second));
    const count = calls.length;
    await ui.clickText(component.root, '受影响批次上一页');
    assert.equal(calls.length, count);
    assert.match(ui.renderedText(component.root), new RegExp(first));
    assert.match(ui.renderedText(component.root), /WO-NEXT/);
  } finally {
    component.unmount();
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
});
test('where-used沿真实identity原URL读取受影响批次，缺batchImpacts不谎报零影响', async () => {
  const id = '9223372036854775807';
  const calls = [];
  globalThis.__traceUiRequest = async (config) => {
    calls.push(config);
    return {
      data: {
        componentIdentityId: id,
        scope: 'IDENTITY_COMPONENT_USAGE',
        rows: [],
        hasMore: false,
        scopeImpacts: { rows: [], hasMore: false },
        batchImpacts: { rows: [{ batchId: '21', scope: 'WORK_ORDER_POSSIBLE' }], hasMore: false }
      }
    };
  };
  const component = ui.mount('recall', { componentIdentityId: id });
  await untilVisible(component.root, /21/);
  assert.equal(calls[0].url, `/erp/wm/trace/where-used/${id}`);
  assert.match(ui.renderedText(component.root), /工单可能影响/);
  component.unmount();
  globalThis.__traceUiRequest = async () => ({ data: { batchId: '20', scope: 'BATCH_COMPONENT_RECALL', parts: [], rows: [], hasMore: false } });
  const older = ui.mount('recall', { batchId: '20' });
  await untilVisible(older.root, /服务端未提供受影响批次分页/);
  assert.doesNotMatch(ui.renderedText(older.root), /0 个受影响批次/);
  older.unmount();
});

/** 本地原码与正式码分别由服务端提供，前端不能通过同码或缺失字段捏造登记证明。 */
for (const scenario of [
  { name: '不同原码单独显示', canonical: 'ERP-CANONICAL', local: 'MES-ORIGINAL', showLocal: true },
  { name: '同码不重复显示', canonical: 'ERP-CANONICAL', local: 'ERP-CANONICAL', showLocal: false },
  { name: '无正式登记证据仍仅显示本地原码', canonical: null, local: 'MES-ORIGINAL', showLocal: true }
]) {
  test(`IdentityTracePanel码语义：${scenario.name}`, async () => {
    const requests = [];
    globalThis.__traceUiRequest = async (config) => {
      requests.push(config);
      throw new Error('不得以编码补造关联');
    };
    const id = '9223372036854775807';
    const component = ui.mount('identity', {
      result: {
        identityId: id,
        scope: 'IDENTITY',
        parts: [
          {
            module: 'MES',
            identityId: id,
            localSnId: '9223372036854775806',
            canonicalSnCode: scenario.canonical,
            localSnCode: scenario.local,
            scope: 'IDENTITY',
            sections: {},
            evidenceGaps: scenario.canonical ? [] : ['ERP正式登记证据缺失']
          }
        ]
      }
    });
    try {
      await ui.nextTick();
      const text = ui.renderedText(component.root);
      if (scenario.showLocal) assert.match(text, new RegExp(`MES 本地原码：${scenario.local}`));
      else assert.doesNotMatch(text, /本地原码/);
      if (scenario.canonical === null) {
        assert.match(text, /正式 SN：未提供正式登记证据/);
        assert.match(text, /ERP正式登记证据缺失/);
        assert.doesNotMatch(text, /正式 SN：MES-ORIGINAL/);
      } else assert.match(text, /ERP-CANONICAL/);
      assert.equal(requests.length, 0);
    } finally {
      component.unmount();
    }
  });
}
test('IdentityTracePanel分页接受服务端canonical null并保留本地原码，不能沿用旧正式码显示', async () => {
  const id = '9223372036854775807';
  const requests = [];
  globalThis.__traceUiRequest = async (config) => {
    requests.push(config);
    return {
      data: {
        identityId: id,
        scope: 'IDENTITY',
        parts: [
          {
            module: 'MES',
            identityId: id,
            localSnId: '9223372036854775806',
            canonicalSnCode: null,
            localSnCode: 'MES-ORIGINAL',
            scope: 'IDENTITY',
            sections: { TEST: { rows: [], hasMore: false } },
            evidenceGaps: ['ERP正式登记证据缺失']
          }
        ]
      }
    };
  };
  const component = ui.mount('identity', {
    result: {
      identityId: id,
      scope: 'IDENTITY',
      parts: [
        {
          module: 'MES',
          identityId: id,
          canonicalSnCode: 'PREVIOUS-CODE',
          scope: 'IDENTITY',
          sections: { TEST: { rows: [], hasMore: true, nextCursor: 'opaque-code-metadata-page' } }
        }
      ]
    }
  });
  try {
    await ui.nextTick();
    await ui.clickText(component.root, 'MES TEST 下一页');
    const text = ui.renderedText(component.root);
    assert.match(text, /MES 本地原码：MES-ORIGINAL/);
    assert.match(text, /正式 SN：未提供正式登记证据/);
    assert.doesNotMatch(text, /PREVIOUS-CODE/);
    assert.match(text, /ERP正式登记证据缺失/);
    assert.equal(requests.length, 1);
    assert.equal(requests[0].url, `/erp/wm/trace/identity/${id}`);
    assert.deepEqual(requests[0].params, { limit: 500, module: 'MES', section: 'TEST', cursor: 'opaque-code-metadata-page' });
  } finally {
    component.unmount();
  }
});

test('实际生产入库入口：batch-only原URL、原快照重放跨库存变化，读取确认状态不冒充已确认', async () => {
  const id = '9223372036854775807';
  const requests = [];
  let posts = 0;
  let confirmed = false;
  const original = {
    recptId: id,
    recptCode: 'RECEIPT-REAL',
    woId: '9223372036854775806',
    woNo: 'WO-REAL',
    materialId: '9223372036854775805',
    materialCode: 'PRODUCT-BATCH-ONLY',
    materialName: '实际批次产品',
    quantity: '0.100000000000000001',
    warehouseId: '9223372036854775804',
    locationId: '9223372036854775803',
    detailVersion: 'opaque/source-before-post',
    sourceHeader: { woId: '9223372036854775806', version: 'source-header-original' },
    status: 'DRAFT',
    policy: { ...policy, serialManaged: false, allowMixedLot: false }
  };
  let command;
  const metadata = () => ({
    submissionState: confirmed ? 'CONFIRMED' : 'CROSS_MODULE_PENDING',
    commandId: command,
    postingGroupIds: ['9223372036854775802'],
    submissionWarnings: confirmed ? [] : ['NEW，等待跨库消费']
  });
  const server = createServer(async (request, response) => {
    let text = '';
    for await (const chunk of request) text += chunk;
    const data = text ? JSON.parse(text) : undefined;
    requests.push({ method: request.method, url: request.url, data });
    if (request.method === 'GET') {
      response.end(
        JSON.stringify({
          code: 200,
          data: posts
            ? { ...original, status: 'EXECUTED', detailVersion: 'changed-after-post-and-move', quantity: '999', submissionSnapshot: metadata() }
            : original
        })
      );
      return;
    }
    posts++;
    command = data.commandId;
    if (posts === 1) {
      response.destroy();
      return;
    }
    response.end(JSON.stringify({ code: 200, data: metadata() }));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const client = axios.create({ baseURL: `http://127.0.0.1:${server.address().port}`, adapter: 'http', proxy: false });
  globalThis.__traceUiRequest = async (config) => (await client(config)).data;
  const executed = [];
  const component = ui.mount('receipt', { onExecuted: (result) => executed.push(result) });
  try {
    await component.vm.open(id);
    await ui.nextTick();
    await ui.clickText(component.root, '按原完工明细执行入库');
    assert.equal(posts, 1);
    assert.equal(executed.length, 0);
    await component.vm.open(id);
    assert.equal(requests.filter((row) => row.method === 'GET').length, 1);
    await ui.clickText(component.root, '重放原入库命令');
    assert.equal(posts, 2);
    assert.match(ui.renderedText(component.root), /跨模块待确认/);
    assert.doesNotMatch(ui.renderedText(component.root), /跨模块已确认/);
    const writes = requests.filter((row) => row.method === 'PUT');
    assert.equal(writes[0].url, `/erp/wm/productionrecpt/execute/${id}`);
    assert.deepEqual(writes[0].data, writes[1].data);
    assert.equal(writes[0].data.quantity, original.quantity);
    assert.equal(writes[0].data.detailVersion, original.detailVersion);
    assert.deepEqual(writes[0].data.sourceHeader, original.sourceHeader);
    assert.deepEqual(writes[0].data.snItems, []);
    confirmed = true;
    await ui.clickText(component.root, '刷新原单状态');
    assert.match(ui.renderedText(component.root), /跨模块已确认/);
    await ui.clickText(component.root, '重放原入库命令');
    assert.deepEqual(requests.filter((row) => row.method === 'PUT')[2].data, writes[0].data);
    assert.equal(executed.length, 2);
  } finally {
    component.unmount();
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
});
test('实际组件修订UI：原取消修订+原分录净额+真实来源/工序；ACK丢失只重放原命令', async () => {
  const manifest = {
    manifestId: '9223372036854775807',
    mesWoId: '9223372036854775806',
    revision: '17',
    status: 'CANCELLED',
    goodQty: '1',
    scrapQty: '0'
  };
  const sn = {
    snId: '9223372036854775805',
    snCode: 'MES-LOCAL',
    identityId: '9223372036854775804',
    identityVersion: '9223372036854775803',
    mesWoId: manifest.mesWoId,
    cardId: '9223372036854775802',
    snStatus: 'COMPLETED',
    handoffStatus: 'NONE'
  };
  const materialId = '9223372036854775801',
    entryId = '9223372036854775800',
    consumptionId = '9223372036854775799',
    stepId = '9223372036854775798';
  const calls = [];
  let posts = 0;
  const server = createServer(async (request, response) => {
    const url = new URL(request.url, 'http://127.0.0.1');
    let text = '';
    for await (const chunk of request) text += chunk;
    const data = text ? JSON.parse(text) : undefined;
    calls.push({ path: url.pathname, method: request.method, params: Object.fromEntries(url.searchParams), data });
    if (request.method === 'POST') {
      posts++;
      if (posts === 1) {
        response.destroy();
        return;
      }
      response.end(
        JSON.stringify({
          code: 200,
          data: {
            version: (BigInt(sn.identityVersion) + 1n).toString(),
            reversalEntryIds: ['9223372036854775797'],
            allocationEntryIds: ['9223372036854775796']
          }
        })
      );
      return;
    }
    let body;
    if (url.pathname.endsWith('/sn/list')) body = { rows: [sn], total: '1' };
    else if (url.pathname.includes('/consumption/by-wo/'))
      body = {
        data: [
          {
            consumptionId,
            woId: manifest.mesWoId,
            materialId,
            materialCode: 'COMP',
            materialName: '实际组件',
            lotId: '9223372036854775795',
            lotCode: 'NEW-LOT',
            consumedQty: '0.200000000000000001'
          }
        ]
      };
    else if (url.pathname.includes('/sn/trace/'))
      body = {
        data: {
          sn,
          componentConsumptions: [
            {
              entry_id: entryId,
              local_sn_id: sn.snId,
              identity_id: sn.identityId,
              component_material_id: materialId,
              component_lot_id: '9223372036854775794',
              quantity: '0.300000000000000001',
              entry_type: 'CONSUME',
              policy_version: '7'
            },
            {
              entry_id: '9223372036854775793',
              original_entry_id: entryId,
              local_sn_id: sn.snId,
              identity_id: sn.identityId,
              component_material_id: materialId,
              component_lot_id: '9223372036854775794',
              quantity: '0.1',
              entry_type: 'REVERSE',
              policy_version: '7'
            }
          ],
          forwardSteps: [{ stepRecordId: stepId, stepStatus: 'COMPLETED', processCode: 'ASSEMBLY', processName: '实际装配工序' }]
        }
      };
    else body = { data: { policy: { ...policy, allowMixedLot: false, version: '7' } } };
    response.end(JSON.stringify({ code: 200, ...body }));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const client = axios.create({ baseURL: `http://127.0.0.1:${server.address().port}`, adapter: 'http', proxy: false });
  globalThis.__traceUiRequest = async (config) => (await client(config)).data;
  const corrected = [];
  const component = ui.mount('correction', { onCorrected: (result) => corrected.push(result) });
  try {
    await component.vm.open(manifest);
    await ui.nextTick();
    await ui.selectText(component.root, '选择该工单实际产品SN', sn.snId);
    await ui.selectText(component.root, '选择实际消耗的组件', materialId);
    assert.match(ui.renderedText(component.root), /0.200000000000000001/);
    await ui.inputText(component.root, `冲销 ${entryId}`, '0.200000000000000001');
    await ui.clickText(component.root, '增加来源分配');
    await ui.inputText(component.root, '选择实际消耗批次', consumptionId);
    await ui.inputText(component.root, '选择该产品已完成工序', stepId);
    await ui.inputText(component.root, '新分配精确数量', '0.200000000000000001');
    await ui.inputText(component.root, '填写实际消耗修订原因', '核对原产品实际组件批次');
    await ui.clickText(component.root, '提交原修订');
    assert.equal(posts, 1);
    assert.equal(corrected.length, 0);
    await ui.inputText(component.root, '填写实际消耗修订原因', '不能改掉原命令内容');
    await ui.clickText(component.root, '重试原修订命令');
    assert.equal(posts, 2);
    assert.equal(corrected.length, 1);
    assert.match(ui.renderedText(component.root), /MES修订已保存/);
    const writes = calls.filter((row) => row.method === 'POST');
    assert.equal(writes[0].path, '/mes/lt/trace/sn-consumption/correct');
    assert.deepEqual(writes[0].data, writes[1].data);
    assert.equal(writes[0].data.expectedVersion, sn.identityVersion);
    assert.equal(writes[0].data.cancelledRevision, '17');
    assert.equal(writes[0].data.snId, sn.snId);
    assert.equal(writes[0].data.reversals[0].originalEntryId, entryId);
    assert.equal(writes[0].data.allocations[0].consumptionId, consumptionId);
    assert.equal(writes[0].data.allocations[0].stepRecordId, stepId);
    assert.equal(writes[0].data.allocations[0].quantity, '0.200000000000000001');
    assert.equal(
      calls.some((row) => /stock|productionrecpt|refreeze/.test(row.path)),
      false
    );
    assert.equal(calls.find((row) => row.path.endsWith('/sn/list')).params.mesWoId, manifest.mesWoId);
  } finally {
    component.unmount();
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
});

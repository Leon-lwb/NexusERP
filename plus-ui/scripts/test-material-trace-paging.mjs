import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
const output = await build({
  entryPoints: ['src/components/material-trace/tracePaging.ts'],
  bundle: true,
  write: false,
  format: 'esm',
  platform: 'node'
});
const m = await import(`data:text/javascript;base64,${Buffer.from(output.outputFiles[0].text).toString('base64')}`);
const page = (rows, nextCursor, hasMore) => ({ rows, nextCursor, hasMore });
const initial = {
  identityId: '9223372036854775807',
  scope: 'IDENTITY',
  parts: [
    {
      module: 'ERP',
      identityId: '9223372036854775807',
      scope: 'IDENTITY',
      sections: {
        STOCK: page([{ stockId: '9223372036854775806', quantity: '0.100000000000000001' }], 'cursor:first', true),
        ALIASES: page([{ code: 'EXT-1' }], null, false)
      },
      evidenceGaps: ['仅有ERP来源']
    },
    {
      module: 'MES',
      identityId: '9223372036854775807',
      scope: 'IDENTITY',
      sections: { TEST: page([{ testId: '9223372036854775805' }], null, false) },
      evidenceGaps: []
    }
  ],
  evidenceGaps: [],
  truncated: true
};

/** 翻页只更新明确的模块与 section，不覆盖其它来源证据。 */
test('identity 分节分页合并保留其它模块、别名和原数量文本', () => {
  const response = {
    identityId: initial.identityId,
    scope: 'IDENTITY',
    parts: [
      {
        module: 'ERP',
        identityId: initial.identityId,
        scope: 'IDENTITY',
        sections: { STOCK: page([{ stockId: '9223372036854775804' }], null, false) },
        evidenceGaps: []
      }
    ]
  };
  const merged = m.mergeIdentitySection(initial, { module: 'ERP', section: 'STOCK' }, response);
  assert.equal(merged.parts[0].sections.STOCK.rows[0].stockId, '9223372036854775804');
  assert.deepEqual(merged.parts[0].sections.ALIASES, initial.parts[0].sections.ALIASES);
  assert.deepEqual(merged.parts[1], initial.parts[1]);
  assert.equal(initial.parts[0].sections.STOCK.rows[0].quantity, '0.100000000000000001');
  assert.equal(initial.parts[0].sections.STOCK.nextCursor, 'cursor:first');
});
test('不同身份、未授权模块或缺失 section 不会静默采用首个结果', () => {
  assert.throws(() => m.mergeIdentitySection(initial, { module: 'ERP', section: 'STOCK' }, { ...initial, identityId: 'other' }), /身份/);
  assert.throws(() => m.mergeIdentitySection(initial, { module: 'ERP', section: 'STOCK' }, { ...initial, parts: [initial.parts[1]] }), /模块|分节/);
  assert.throws(() => m.mergeIdentitySection(initial, { module: 'ERP', section: 'CONSUMPTION' }, initial), /模块|分节/);
});
test('游标页只有服务端给出有效 nextCursor 才能继续，0 字符串也按原值保留', () => {
  assert.equal(m.pageCursor(page([], '9223372036854775807', true)), '9223372036854775807');
  assert.equal(m.pageCursor(page([], '0', true)), '0');
  assert.equal(m.pageCursor(page([], 'ignored', false)), undefined);
  assert.throws(() => m.pageCursor(page([], undefined, true)), /游标/);
});
test('分页表格显示不截断雪花身份和长小数', () => {
  assert.equal(m.traceCell('9223372036854775807'), '9223372036854775807');
  assert.equal(m.traceCell('0.100000000000000001'), '0.100000000000000001');
  assert.equal(m.traceCell(0), '0');
});
test('召回明确区分深度截断、容量截断和后续页', () => {
  assert.match(m.recallBoundary({ hasMore: false, truncatedByDepth: true }), /深度/);
  assert.match(m.recallBoundary({ hasMore: false, truncatedByCapacity: true }), /容量/);
  assert.match(m.recallBoundary({ hasMore: true, nextCursor: '9223372036854775807' }), /后续/);
  assert.match(m.recallBoundary({ hasMore: false, truncated: true }), /截断/);
});

/** 未附范围的身份沿用逐件证明；可能影响与未知精度均不能升级为已确认。 */
test('召回精度明确区分逐件、批次可能、工单可能及未知范围', () => {
  assert.equal(m.recallPrecision(undefined).label, '逐件确认');
  assert.equal(m.recallPrecision('BATCH_POSSIBLE').label, '批次可能影响');
  assert.equal(m.recallPrecision('WORK_ORDER_POSSIBLE').label, '工单可能影响');
  assert.notEqual(m.recallPrecision('FUTURE_SCOPE').label, '逐件确认');
  assert.notEqual(m.recallPrecision(null).label, '逐件确认');
});
test('工单范围还有页时保留独立游标，不把身份页完成误报为召回完成', () => {
  assert.match(
    m.recallBoundary({ hasMore: false, scopeImpacts: { rows: [], hasMore: true, nextCursor: 'MES/工单+9223372036854775807' } }),
    /工单范围.*后续/
  );
});

test('批次分节页保留其它授权模块、客户去向和来源，不混同来源仓与当前库存', () => {
  const current = {
    batchId: '9223372036854775807',
    scope: 'BATCH',
    parts: [
      {
        module: 'ERP',
        batchId: '9223372036854775807',
        scope: 'BATCH',
        sections: {
          STOCK: page([{ warehouseId: 'CURRENT' }], 'next-stock', true),
          MOVEMENTS: page([{ customerId: 'CUSTOMER' }], null, false),
          SOURCE: page([{ warehouseId: 'SOURCE' }], null, false)
        }
      },
      { module: 'MES', batchId: '9223372036854775807', scope: 'BATCH', sections: { LOTS: page([{ lotId: 'LOT' }], null, false) } }
    ]
  };
  const response = { ...current, parts: [{ ...current.parts[0], sections: { STOCK: page([{ warehouseId: 'NEW-CURRENT' }], null, false) } }] };
  const merged = m.mergeBatchSection(current, { module: 'ERP', section: 'STOCK' }, response);
  assert.equal(merged.parts[0].sections.STOCK.rows[0].warehouseId, 'NEW-CURRENT');
  assert.deepEqual(merged.parts[0].sections.SOURCE, current.parts[0].sections.SOURCE);
  assert.deepEqual(merged.parts[0].sections.MOVEMENTS, current.parts[0].sections.MOVEMENTS);
  assert.deepEqual(merged.parts[1], current.parts[1]);
  assert.throws(() => m.mergeBatchSection(current, { module: 'ERP', section: 'STOCK' }, { ...response, batchId: 'OTHER' }), /批次/);
  assert.throws(() => m.mergeBatchSection(current, { module: 'ERP', section: 'STOCK' }, { ...response, parts: [current.parts[1]] }), /模块|分节/);
});

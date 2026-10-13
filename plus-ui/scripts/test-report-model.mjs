import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
await mkdir('node_modules/.tmp', { recursive: true });
await build({
  entryPoints: ['src/views/report/model.ts'],
  outfile: 'node_modules/.tmp/report-model.mjs',
  bundle: true,
  format: 'esm',
  platform: 'node'
});
const m = await import(pathToFileURL(resolve('node_modules/.tmp/report-model.mjs')));
const descriptor = {
  code: 'TEST',
  name: '测试',
  version: 1,
  document: true,
  permission: 'test:list',
  fields: [
    { name: 'id', label: 'ID', type: 'ID', aggregationKeys: [], aggregatable: true },
    { name: 'qty', label: '数量', type: 'DECIMAL', aggregationKeys: ['unit'], aggregatable: true },
    { name: 'unit', label: '单位', type: 'STRING', aggregationKeys: [], aggregatable: true }
  ],
  parameters: [{ name: 'orderId', label: '单据', type: 'ID', required: true }],
  defaultColumns: ['id', 'qty']
};
test('typed display preserves snowflake IDs', () =>
  assert.equal(m.formatTyped({ type: 'ID', value: '9223372036854775807' }, 2), '9223372036854775807'));
test('decimal rounding matches server HALF_UP without Number', () => {
  assert.equal(m.formatTyped({ type: 'DECIMAL', value: '9007199254740993.005' }, 2), '9007199254740993.01');
  assert.equal(m.formatTyped({ type: 'DECIMAL', value: '-1.005' }, 2), '-1.01');
  assert.equal(m.formatTyped({ type: 'DECIMAL', value: '0.1' }, 4), '0.1000');
});
test('nullable and boolean cells have explicit text', () => {
  assert.equal(m.formatTyped({ type: 'STRING', value: null }, 0), '');
  assert.equal(m.formatTyped({ type: 'BOOLEAN', value: 'false' }, 0), '否');
});
test('new native documents fit the printable width', () => {
  const d = m.createDsl(descriptor, '9223372036854775807', 'REPORT_A');
  assert.equal(d.kind, 'DOCUMENT');
  assert.ok(Math.abs(d.columns.reduce((n, c) => n + c.width, 0) - (d.page.width - d.page.marginLeft - d.page.marginRight)) < 0.001);
  assert.equal(d.datasetVersionId, '9223372036854775807');
});
test('column changes retain full-width merged header and leave source unchanged', () => {
  const d = m.createDsl(descriptor, '1', 'REPORT_A');
  const next = m.addField(d, descriptor.fields[2]);
  assert.equal(next.headerRows[0].cells[0].colSpan, 3);
  assert.equal(d.columns.length, 2);
  const removed = m.removeColumn(next, 1);
  assert.equal(removed.headerRows[0].cells[0].colSpan, 2);
  assert.deepEqual(
    removed.columns.map((c) => c.field),
    ['id', 'unit']
  );
});
test('duplicate fields and removing last detail column are rejected', () => {
  const d = m.createDsl(descriptor, '1', 'REPORT_A');
  assert.throws(() => m.addField(d, descriptor.fields[0]));
  assert.throws(() => m.removeColumn(m.removeColumn(d, 0), 0));
});
test('reorder checks bounds and preserves exact values', () => {
  const d = m.createDsl(descriptor, '1', 'REPORT_A');
  assert.deepEqual(
    m.moveColumn(d, 0, 1).columns.map((c) => c.field),
    ['qty', 'id']
  );
  assert.throws(() => m.moveColumn(d, 0, 8));
});
test('server defaults are omitted from input and IDs are never coerced', () =>
  assert.deepEqual(m.parameterInput({ orderId: '9223372036854775807', start: '', qty: '0.100' }), { orderId: '9223372036854775807', qty: '0.100' }));
test('scope changes and late replies cannot replace a newer run', () => {
  const gate = new m.RequestGate();
  const a = gate.begin();
  const b = gate.begin();
  assert.equal(gate.current(a), false);
  assert.equal(gate.current(b), true);
  gate.invalidate();
  assert.equal(gate.current(b), false);
});
test('adding numeric summaries retains required unit dimensions', () => {
  const summary = m.summaryFor(descriptor.fields[1]);
  assert.deepEqual(summary.groupBy, ['unit']);
  assert.equal(summary.function, 'SUM');
  assert.throws(() => m.summaryFor({ ...descriptor.fields[1], aggregatable: false }));
});
test('export availability distinguishes fixed snapshots and live runs', () => {
  assert.deepEqual(m.exportFormats('LIVE', 9000), ['XLSX_DATA']);
  assert.deepEqual(m.exportFormats('SNAPSHOT', 10), ['PDF', 'XLSX_DATA', 'XLSX_LAYOUT']);
});
test('native DSL serialization rejects frontend editor keys', () => {
  const d = m.createDsl(descriptor, '1', 'REPORT_A');
  assert.throws(() => m.serializeDsl({ ...d, selectedCell: 1 }));
  assert.equal(JSON.parse(m.serializeDsl(d)).schemaVersion, 1);
});

test('snapshot mode matches case-insensitive aggregate expressions including unary MIN and MAX', () => {
  const d = m.createDsl({ ...descriptor, document: false }, '1', 'REPORT_A');
  const expression = (value) => ({ ...d, summaries: [], columns: [{ ...d.columns[1], expression: value }] });
  assert.equal(m.requiresSnapshot(expression('sum(qty)')), true);
  assert.equal(m.requiresSnapshot(expression('CoUnT()')), true);
  assert.equal(m.requiresSnapshot(expression('MIN(COALESCE(qty, 0))')), true);
  assert.equal(m.requiresSnapshot(expression('MAX(qty)')), true);
  assert.equal(m.requiresSnapshot(expression('MIN(qty, 0)')), false);
  assert.equal(m.requiresSnapshot(expression('MAX(ROUND(qty, 2), 0)')), false);
  assert.equal(m.requiresSnapshot(expression('MAX(qty, MIN(qty))')), true);
  assert.equal(m.requiresSnapshot({ ...d, summaries: [{ field: null, function: 'COUNT_ROWS', groupBy: ['unit'], label: 'count', scale: 0 }] }), true);
});

test('bounded editor history preserves exact ID and decimal text without editor keys in DSL', () => {
  const history = new m.DslHistory(3);
  const d = m.createDsl(descriptor, '9223372036854775807', 'REPORT_A');
  d.parameters.push({ name: 'exact', label: 'exact', type: 'DECIMAL', required: false, defaultValue: '9007199254740993.005' });
  history.reset(d);
  for (const title of ['one', 'two', 'three']) {
    d.title = title;
    history.record(d);
  }
  assert.equal(history.undo().title, 'two');
  const restored = history.undo();
  assert.equal(restored.title, 'one');
  assert.equal(history.canUndo, false);
  assert.equal(restored.datasetVersionId, '9223372036854775807');
  assert.equal(restored.parameters.at(-1).defaultValue, '9007199254740993.005');
  assert.deepEqual(Object.keys(restored), Object.keys(d));
  assert.equal(JSON.parse(m.serializeDsl(restored)).datasetVersionId, '9223372036854775807');
});
test('new editor edits clear the redo branch and resetting a load discards prior history', () => {
  const history = new m.DslHistory(5);
  const d = m.createDsl(descriptor, '1', 'REPORT_A');
  history.reset(d);
  history.record({ ...d, title: 'one' });
  history.record({ ...d, title: 'two' });
  assert.equal(history.undo().title, 'one');
  assert.equal(history.canRedo, true);
  history.record({ ...d, title: 'different branch' });
  assert.equal(history.canRedo, false);
  assert.equal(history.redo(), undefined);
  history.reset({ ...d, title: 'new route' });
  assert.equal(history.canUndo, false);
  assert.equal(history.canRedo, false);
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
await build({
  entryPoints: ['src/views/report/designer/editorCommands.ts'],
  outfile: 'node_modules/.tmp/report-editor-commands.mjs',
  bundle: true,
  format: 'esm',
  platform: 'node'
});
const m = await import(pathToFileURL(resolve('node_modules/.tmp/report-editor-commands.mjs')));
const dsl = {
  schemaVersion: 1,
  datasetCode: 'TEST',
  datasetVersionId: '100',
  columns: [
    {
      field: 'a',
      label: 'A',
      width: 100,
      scale: 0,
      expression: null,
      style: { fontSize: 10, bold: false, align: 'LEFT', color: '#111111', background: '#ffffff' }
    },
    {
      field: 'b',
      label: 'B',
      width: 100,
      scale: 0,
      expression: null,
      style: { fontSize: 10, bold: false, align: 'LEFT', color: '#111111', background: '#ffffff' }
    }
  ],
  parameters: [],
  headerRows: [],
  footerRows: [],
  summaries: [],
  repeatHeader: true
};
test('batch styles are immutable and affect only selected columns', () => {
  const next = m.styleColumns(dsl, [1], { bold: true });
  assert.equal(next.columns[1].style.bold, true);
  assert.equal(next.columns[0].style.bold, false);
  assert.equal(dsl.columns[1].style.bold, false);
});
test('copy paste creates distinct computed bindings and preserves source', () => {
  const text = m.copyColumns(dsl, [0]);
  const next = m.pasteColumns(dsl, text);
  assert.equal(next.columns.length, 3);
  assert.equal(next.columns[2].field, 'a_copy');
  assert.equal(next.columns[2].expression, 'a');
  assert.equal(dsl.columns.length, 2);
});
test('clipboard rejects foreign datasets, over-capacity and illegal payloads', () => {
  assert.throws(() => m.pasteColumns({ ...dsl, datasetCode: 'OTHER' }, m.copyColumns(dsl, [0])));
  assert.throws(() => m.pasteColumns(dsl, '<script>'));
  assert.throws(() => m.styleColumns(dsl, [10], { bold: true }));
});
test('bound descriptors cannot silently follow a newer head or a foreign response', () => {
  const heads = [{ dataset: { code: 'TEST', publishedVersion: '200' }, descriptor: { version: 2 }, canManage: true }];
  assert.equal(m.boundDataset(heads, dsl, undefined), undefined);
  assert.equal(m.boundDataset(heads, dsl, { id: '100', code: 'OTHER', descriptor: { version: 1 } }), undefined);
  assert.equal(m.boundDataset(heads, dsl, { id: '100', code: 'TEST', descriptor: { version: 1 } }).descriptor.version, 1);
});

test('structural errors identify their exact editable column without computing values', () => {
  const issues = m.editorIssues({ ...dsl, columns: [...dsl.columns, { ...dsl.columns[0] }] }, { fields: [{ name: 'a' }] });
  assert.ok(issues.some((i) => i.index === 1 && i.message.includes('b')));
  assert.ok(issues.some((i) => i.index === 2 && i.message.includes('重复')));
});

test('document paste keeps printable width and full-width fixed bands', () => {
  const row = { height: 24, cells: [{ column: 0, colSpan: 2, rowSpan: 1, text: '标题', field: null, style: dsl.columns[0].style }] };
  const original = {
    ...structuredClone(dsl),
    kind: 'DOCUMENT',
    page: { width: 600, marginLeft: 20, marginRight: 20 },
    headerRows: [structuredClone(row)],
    footerRows: [structuredClone(row)]
  };
  const next = m.pasteColumns(original, m.copyColumns(original, [0]));
  assert.ok(Math.abs(next.columns.reduce((sum, c) => sum + c.width, 0) - 560) < 0.000001);
  assert.equal(next.headerRows[0].cells[0].colSpan, 3);
  assert.equal(next.footerRows[0].cells[0].colSpan, 3);
  assert.equal(original.headerRows[0].cells[0].colSpan, 2);
});

test('contract upgrade adds required parameters and removes or resets incompatible definitions', () => {
  const original = {
    ...structuredClone(dsl),
    parameters: [
      { name: 'removed', label: '旧参数', type: 'ID', required: false, defaultValue: '9223372036854775807' },
      { name: 'stable', label: '自定日期标题', type: 'DATE', required: false, defaultValue: '2026-10-01' },
      { name: 'changed', label: '旧类型', type: 'BOOLEAN', required: false, defaultValue: 'true' }
    ]
  };
  const target = {
    id: '200',
    code: 'TEST',
    descriptor: {
      parameters: [
        { name: 'added', label: '新单据', type: 'ID', required: true, defaultValue: null },
        { name: 'stable', label: '日期', type: 'DATE', required: true, defaultValue: '2026-01-01' },
        { name: 'changed', label: '新类型', type: 'ID', required: false, defaultValue: null }
      ]
    }
  };
  const next = m.upgradeDatasetVersion(original, target);
  assert.equal(next.datasetVersionId, '200');
  assert.deepEqual(
    next.parameters.map((p) => p.name),
    ['added', 'stable', 'changed']
  );
  assert.equal(next.parameters[0].required, true);
  assert.equal(next.parameters[0].defaultValue, null);
  assert.equal(next.parameters[1].required, true);
  assert.equal(next.parameters[1].defaultValue, '2026-10-01');
  assert.equal(next.parameters[1].label, '自定日期标题');
  assert.equal(next.parameters[2].type, 'ID');
  assert.equal(next.parameters[2].defaultValue, null);
  assert.equal(original.datasetVersionId, '100');
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
await build({
  entryPoints: ['src/views/report/designer/gridCommands.ts'],
  outfile: 'node_modules/.tmp/report-grid.mjs',
  bundle: true,
  format: 'esm',
  platform: 'node'
});
const grid = await import(pathToFileURL(resolve('node_modules/.tmp/report-grid.mjs')));

/** 创建带完整字符串业务值的独立单据草稿。 */
function document() {
  const style = { fontSize: 10, bold: false, align: 'LEFT', color: '#111111', background: '#ffffff' };
  const cell = (column, text = '') => ({ column, colSpan: 1, rowSpan: 1, text, field: null, style: { ...style } });
  return {
    schemaVersion: 1,
    code: 'TEST',
    title: '合成单据',
    kind: 'DOCUMENT',
    datasetCode: 'TEST',
    datasetVersionId: '9223372036854775807',
    columns: ['code', 'amount', 'unit'].map((field) => ({ field, label: field, width: 150, scale: 4, expression: null, style: { ...style } })),
    parameters: [{ name: 'orderId', type: 'ID', label: '订单', required: true, defaultValue: '9223372036854775807' }],
    page: { width: 510, height: 800, marginLeft: 30, marginRight: 30, marginTop: 30, marginBottom: 30, rowHeight: 24 },
    headerRows: [
      { height: 32, cells: [cell(0, '单号'), cell(1, '金额'), cell(2)] },
      { height: 32, cells: [cell(0), cell(1), cell(2)] }
    ],
    footerRows: [{ height: 24, cells: [cell(0, '签名'), cell(1), cell(2)] }],
    summaries: [],
    repeatHeader: true
  };
}

test('毫米与pt往返保持精度并拒绝非法尺寸', () => {
  assert.ok(Math.abs(grid.ptToMm(grid.mmToPt(210)) - 210) < 1e-9);
  assert.throws(() => grid.mmToPt(NaN));
  assert.throws(() => grid.ptToMm(Infinity));
});

test('纸张预设和方向只交换一次并保持旧草稿版本', () => {
  const source = document();
  const landscape = grid.setPaper(source, 'A4', 'LANDSCAPE');
  assert.ok(Math.abs(grid.ptToMm(grid.effectivePaper(landscape).width) - 297) < 0.01);
  assert.equal(landscape.schemaVersion, 1);
  assert.equal(landscape.print, undefined);
  assert.deepEqual(grid.setPaper(landscape, 'A4', 'LANDSCAPE').page, landscape.page);
  const next = grid.setPaper(
    { ...source, schemaVersion: 2, print: { orientation: 'PORTRAIT', groupBy: [], repeatFooter: false, media: [] } },
    'A5',
    'LANDSCAPE'
  );
  assert.equal(next.print.orientation, 'LANDSCAPE');
  assert.ok(Math.abs(grid.ptToMm(grid.effectivePaper(next).width) - 210) < 0.01);
  assert.deepEqual(source, document());
});

test('修改纸张保留ID并检查可打印宽度及媒体越界', () => {
  const source = document();
  const next = grid.setPaper(source, 'A5', 'PORTRAIT');
  assert.equal(next.parameters[0].defaultValue, source.parameters[0].defaultValue);
  assert.ok(Math.abs(next.columns.reduce((total, column) => total + column.width, 0) - (next.page.width - 60)) < 1e-7);
  assert.throws(() => grid.setPaper({ ...source, columns: Array.from({ length: 40 }, () => source.columns[0]) }, 'A5', 'PORTRAIT'));
  const media = { kind: 'QR', field: 'code', assetVersionId: null, x: 450, y: 40, width: 50, height: 50, repeat: false };
  assert.throws(() =>
    grid.setPaper(
      { ...source, schemaVersion: 2, print: { orientation: 'PORTRAIT', groupBy: [], repeatFooter: false, media: [media] } },
      'A5',
      'PORTRAIT'
    )
  );
});

test('拖列边界守恒总宽且钳制相邻列最小宽度', () => {
  const source = document();
  const next = grid.resizeColumnBoundary(source, 0, 999);
  assert.equal(next.columns[0].width, 280);
  assert.equal(next.columns[1].width, 20);
  assert.equal(next.columns[2].width, 150);
  assert.equal(source.columns[0].width, 150);
  assert.throws(() => grid.resizeColumnBoundary(source, 2, 10));
  assert.throws(() => grid.resizeColumnBoundary(source, 0, NaN));
});

test('固定行拖动限制高度且不改变单元格和值', () => {
  const source = document();
  assert.equal(grid.resizeBandRow(source, 'headerRows', 0, 200).headerRows[0].height, 160);
  assert.equal(grid.resizeBandRow(source, 'headerRows', 0, -100).headerRows[0].height, 12);
  assert.throws(() => grid.resizeBandRow(source, 'detailRows', 0, 10));
});

test('矩形跨行合并保留左上内容并完整拆分覆盖', () => {
  const source = document();
  const selection = { band: 'headerRows', top: 0, left: 0, bottom: 1, right: 1 };
  const merged = grid.mergeCells(source, selection);
  assert.equal(merged.headerRows[0].cells[0].text, '单号');
  assert.equal(merged.headerRows[0].cells[0].colSpan, 2);
  assert.equal(merged.headerRows[0].cells[0].rowSpan, 2);
  assert.equal(merged.headerRows[1].cells.length, 1);
  const split = grid.splitCells(merged, selection);
  assert.equal(split.headerRows[0].cells.length, 3);
  assert.equal(split.headerRows[1].cells.length, 3);
  assert.equal(split.headerRows[0].cells[0].text, '单号');
  assert.equal(split.headerRows[1].cells[0].text, '');
  assert.deepEqual(source, document());
  assert.throws(() => grid.mergeCells(source, { ...selection, band: 'detailRows' }));
  assert.throws(() => grid.mergeCells(source, { ...selection, bottom: 2 }));
});

test('局部选区不得切断已有跨格，批量样式只作用于完整选区', () => {
  const source = document();
  const merged = grid.mergeCells(source, { band: 'headerRows', top: 0, left: 0, bottom: 1, right: 1 });
  const partial = { band: 'headerRows', top: 0, left: 1, bottom: 1, right: 2 };
  assert.throws(() => grid.mergeCells(merged, partial));
  assert.throws(() => grid.styleCells(merged, partial, { bold: true }));
  const next = grid.styleCells(source, { band: 'headerRows', top: 0, left: 0, bottom: 1, right: 1 }, { bold: true, align: 'CENTER' });
  assert.equal(next.headerRows[1].cells[1].style.bold, true);
  assert.equal(next.headerRows[0].cells[2].style.bold, false);
  assert.equal(next.footerRows[0].cells[0].style.bold, false);
});

test('媒体拖动及缩放不越界且二维码保持正方形', () => {
  const source = {
    ...document(),
    schemaVersion: 2,
    print: {
      orientation: 'PORTRAIT',
      groupBy: [],
      repeatFooter: false,
      media: [{ kind: 'QR', field: 'code', assetVersionId: null, x: 50, y: 50, width: 72, height: 72, repeat: false }]
    }
  };
  const moved = grid.positionMedia(source, 0, { x: -100, y: 9999 });
  assert.equal(moved.print.media[0].x, 30);
  assert.equal(moved.print.media[0].y, 698);
  const resized = grid.positionMedia(source, 0, { width: 100, height: 40 });
  assert.equal(resized.print.media[0].width, 100);
  assert.equal(resized.print.media[0].height, 100);
  assert.equal(source.print.media[0].width, 72);
  assert.throws(() => grid.positionMedia(source, 0, { width: NaN }));
});

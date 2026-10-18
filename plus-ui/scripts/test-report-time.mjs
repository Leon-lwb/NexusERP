import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { mkdir } from 'node:fs/promises';

await mkdir('node_modules/.tmp', { recursive: true });
await build({
  entryPoints: ['src/views/report/time.ts'],
  outfile: 'node_modules/.tmp/report-time.mjs',
  bundle: true,
  format: 'esm',
  platform: 'node'
});
/** 使用产品时间展示函数检查元数据，不依赖浏览器所在时区。 */
const { formatReportDateTime, formatReportTimeCell } = await import('../node_modules/.tmp/report-time.mjs');

for (const [value, expected] of [
  ['2026-10-17T16:30:45.123456789Z', '2026-10-18 00:30:45'],
  ['2026-10-18T00:30:45-04:00', '2026-10-18 12:30:45'],
  ['2026-10-18T00:00+08:00', '2026-10-18 00:00:00'],
  ['2026-10-18T16:00:00Z', '2026-10-19 00:00:00'],
  ['2026-10-18 09:05:06.123456', '2026-10-18 09:05:06'],
  ['2026-10-18T09:05', '2026-10-18 09:05:00'],
  ['2024-02-29', '2024-02-29'],
  ['2000-02-29', '2000-02-29'],
  [null, '—'],
  [undefined, '—'],
  ['', '—'],
  ['invalid-time', 'invalid-time'],
  ['2026-02-30T00:00:00Z', '2026-02-30T00:00:00Z'],
  ['1900-02-29T00:00:00Z', '1900-02-29T00:00:00Z'],
  ['2026-10-18T24:00:00Z', '2026-10-18T24:00:00Z'],
  ['2026-10-18T00:00:00+18:01', '2026-10-18T00:00:00+18:01']
]) {
  test('报表时间展示：' + String(value), () => assert.equal(formatReportDateTime(value), expected));
}

/** 审计与旧接口可能提供毫秒或秒时间戳，均使用业务时区。 */
test('数值、字符串时间戳及Date对象保持相同的显示结果', () => {
  const milliseconds = Date.parse('2026-10-17T16:30:45.123Z');
  for (const value of [
    milliseconds,
    String(milliseconds),
    Math.floor(milliseconds / 1000),
    String(Math.floor(milliseconds / 1000)),
    new Date(milliseconds)
  ]) {
    assert.equal(formatReportDateTime(value), '2026-10-18 00:30:45');
  }
  assert.equal(formatReportDateTime(0), '1970-01-01 08:00:00');
  assert.equal(formatReportDateTime(new Date(NaN)), '—');
});

/** 保持原始排序键与精确值，formatter不改写行数据。 */
test('表格时间formatter只转换显示文字', () => {
  const row = { createdAt: '2026-10-17T16:30:45.123Z', id: '9223372036854775807' };
  const before = structuredClone(row);
  assert.equal(formatReportTimeCell(row, { property: 'createdAt' }), '2026-10-18 00:30:45');
  assert.equal(formatReportTimeCell(row, { property: 'expires' }), '—');
  assert.deepEqual(row, before);
});

/** 跨浏览器时区保持元数据与服务端打印的一致口径。 */
test('系统时区变化不影响日期与北京时间显示', () => {
  const original = process.env.TZ;
  try {
    for (const zone of ['UTC', 'America/New_York', 'Asia/Tokyo']) {
      process.env.TZ = zone;
      assert.equal(formatReportDateTime('2026-10-17T16:30:45Z'), '2026-10-18 00:30:45');
      assert.equal(formatReportDateTime('2026-10-18 00:30:45'), '2026-10-18 00:30:45');
      assert.equal(formatReportDateTime('2026-10-18'), '2026-10-18');
    }
  } finally {
    if (original === undefined) delete process.env.TZ;
    else process.env.TZ = original;
  }
});

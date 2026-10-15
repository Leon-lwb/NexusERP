import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { mkdir } from 'node:fs/promises';
await mkdir('node_modules/.tmp', { recursive: true });
await build({
  entryPoints: ['src/views/report/presentation.ts'],
  outfile: 'node_modules/.tmp/report-presentation.mjs',
  bundle: true,
  format: 'esm',
  platform: 'node'
});
/** 导入只处理界面资源和字段描述的展示辅助函数。 */
const { filterReports, reportCounts, filterFields, statusPresentation } = await import('../node_modules/.tmp/report-presentation.mjs');
/** 使用超过安全整数范围的字符串 ID，避免测试意外容忍数值转换。 */
const reports = [
  { id: '9223372036854775807', name: '库存余额', code: 'STOCK_BALANCE', kind: 'TABLE', status: 'PUBLISHED', catalogId: '9223372036854775806' },
  { id: '9223372036854775806', name: '出库单', code: 'SHIPMENT', kind: 'DOCUMENT', status: 'DRAFT', catalogId: null },
  { id: '9223372036854775805', name: '库存交叉', code: 'STOCK_PIVOT', kind: 'PIVOT', status: 'OFFLINE', catalogId: '9223372036854775806' },
  { id: '9223372036854775804', name: '历史库存', code: 'STOCK_OLD', kind: 'TABLE', status: 'ARCHIVED', catalogId: '9223372036854775805' }
];
/** 验证搜索忽略首尾空格及大小写，并能按编码查找。 */
test('资源搜索匹配名称或编码且忽略空白和大小写', () => {
  assert.deepEqual(
    filterReports(reports, { keyword: '  stock_balance  ' }).map((row) => row.id),
    [reports[0].id]
  );
  assert.equal(filterReports(reports, { keyword: '库存' }).length, 3);
  assert.equal(filterReports(reports, { keyword: '不存在' }).length, 0);
});
/** 验证交叉类型和多个筛选条件共同生效且不改变原数据。 */
test('类型、目录和状态组合筛选保留完整 ID', () => {
  const before = JSON.stringify(reports);
  assert.deepEqual(filterReports(reports, { kind: 'PIVOT', status: 'OFFLINE', catalogId: reports[0].catalogId }), [reports[2]]);
  assert.equal(filterReports(reports, { catalogId: '9223372036854775805', status: 'PUBLISHED' }).length, 0);
  assert.equal(JSON.stringify(reports), before);
});
/** 验证清空条件恢复所有可见资源，不生成额外业务数据。 */
test('空条件显示所有接口返回的可见资源', () => {
  assert.equal(filterReports(reports, {}).length, reports.length);
  assert.equal(filterReports([], {}).length, 0);
});
/** 验证统计只计数资源，不将 ID 或报表业务值转换为 Number。 */
test('统计包括草稿、发布、下线和归档', () => {
  assert.deepEqual(reportCounts(reports), { all: 4, PUBLISHED: 1, DRAFT: 1, OFFLINE: 1, ARCHIVED: 1 });
  assert.equal(reportCounts([]).all, 0);
});
/** 验证设计器按中文标签、绑定名及类型检索字段，且不改变字段顺序。 */
test('设计器字段检索匹配标签、绑定名和类型', () => {
  const fields = [
    { name: 'stockId', label: '库存 ID', type: 'ID' },
    { name: 'amount', label: '库存金额', type: 'DECIMAL' }
  ];
  assert.deepEqual(filterFields(fields, ' 金额 '), [fields[1]]);
  assert.deepEqual(filterFields(fields, 'STOCKID'), [fields[0]]);
  assert.deepEqual(filterFields(fields, 'decimal'), [fields[1]]);
  assert.deepEqual(filterFields(fields, ''), fields);
  assert.deepEqual(filterFields(fields, 'missing'), []);
});
/** 验证常见任务状态中文化，未知服务状态仍可辨认。 */
test('中文状态覆盖任务和资源，未知状态安全回退', () => {
  assert.deepEqual(statusPresentation('SUCCEEDED'), { label: '已完成', type: 'success' });
  assert.equal(statusPresentation('READY').label, '待领取');
  assert.equal(statusPresentation('CANCEL_REQUESTED').label, '取消中');
  assert.deepEqual(statusPresentation('FUTURE_STATE'), { label: 'FUTURE_STATE', type: 'info' });
  assert.equal(statusPresentation(null).label, '—');
});

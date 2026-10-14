import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
/** 查询编辑器只生成逻辑模型，精确参数保持文本。 */
await build({
  entryPoints: ['src/views/report/query/model.ts'],
  outfile: 'node_modules/.tmp/report-query-model.mjs',
  bundle: true,
  format: 'esm',
  platform: 'node'
});
/**
 * 保存当前页面或测试的model状态。
 */
const model = await import(pathToFileURL(resolve('node_modules/.tmp/report-query-model.mjs')));
/** 保存逻辑字段及参数，不保存物理SQL。 */
test('query editor creates only the declared logical model', () => {
  const result = model.queryModel(
    1,
    'stock',
    ['stock.material'],
    [{ field: 'stock.id', alias: 'id', aggregate: null, group: false }],
    [{ field: 'stock.id', operator: 'EQ', parameter: 'orderId', type: 'ID', required: true }],
    []
  );
  assert.equal(result.parameters[0].type, 'ID');
  assert.equal(result.filters[0].parameter, 'orderId');
  assert.equal(result.joins[0], 'stock.material');
  assert.equal('sql' in result, false);
});
/** 不用浮点数处理业务标识及十进制。 */
test('query preview input retains exact IDs and decimal text', () => {
  const value = model.queryParameters({ orderId: '9223372036854775807', amount: '9007199254740993.005', blank: '' });
  assert.equal(value.orderId, '9223372036854775807');
  assert.equal(value.amount, '9007199254740993.005');
  assert.equal('blank' in value, false);
});

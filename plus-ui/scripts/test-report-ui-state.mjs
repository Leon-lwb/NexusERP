import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

await mkdir('node_modules/.tmp', { recursive: true });
/** 编译实际纯函数模块，以合成信封验证权限和恢复指引。 */
async function moduleUnderTest(path, name) {
  const outfile = resolve('node_modules/.tmp/report-ui-' + name + '.mjs');
  await build({ entryPoints: [path], outfile, bundle: true, format: 'esm', platform: 'node' });
  return import(pathToFileURL(outfile));
}

test('JSON及Blob报表错误保留业务码，不改写原始异常', async () => {
  const { readReportFailure } = await moduleUnderTest('src/api/report/errors.ts', 'errors');
  const data = { code: 409, msg: '版本变化', errorCode: 'REPORT_VERSION_CHANGED' };
  const error = { response: { status: 409, data }, marker: 'original' };
  const result = await readReportFailure(error);
  assert.equal(result.code, 'REPORT_VERSION_CHANGED');
  assert.equal(result.action, 'SELECT_TEMPLATE');
  assert.equal(error.response.data, data);
  const blob = await readReportFailure({
    response: { status: 410, data: new Blob([JSON.stringify({ errorCode: 'SNAPSHOT_EXPIRED' })], { type: 'application/json' }) }
  });
  assert.equal(blob.action, 'NEW_RUN');
});

test('撤权和未知错误不建议盲目重发写请求', async () => {
  const { readReportFailure } = await moduleUnderTest('src/api/report/errors.ts', 'errors');
  const denied = await readReportFailure({ response: { status: 403, data: { errorCode: 'SCOPE_DENIED' } } });
  assert.equal(denied.action, 'ACCESS');
  const unknown = await readReportFailure(new Error('private storage path'));
  assert.equal(unknown.action, 'NONE');
  assert.doesNotMatch(unknown.message, /private storage path/);
});

test('合并错误保留安全单据序号，容量与编码冲突提供明确恢复方向', async () => {
  const { readReportFailure } = await moduleUnderTest('src/api/report/errors.ts', 'errors');
  const denied = await readReportFailure({
    response: { status: 403, data: { errorCode: 'SCOPE_DENIED', msg: '第2项（item_no=1）：内部路径不应显示' } }
  });
  assert.equal(denied.itemNumber, 1);
  assert.match(denied.message, /第2项/);
  assert.doesNotMatch(denied.message, /内部路径/);
  const limit = await readReportFailure({ response: { status: 413, data: { errorCode: 'BATCH_PDF_PAGE_LIMIT' } } });
  assert.match(limit.message, /页数|减少/);
  const conflict = await readReportFailure({ response: { status: 409, data: { errorCode: 'BATCH_CODE_CONFLICT' } } });
  assert.equal(conflict.action, 'NEW_RUN');
});

test('单据显示字段仅匹配完整ID，丢失精度的数字不得恢复为ID', async () => {
  const { captureBusinessDocuments, exactBusinessId } = await moduleUnderTest('src/components/report/businessDocument.ts', 'documents');
  const exact = '9223372036854775807';
  const rows = captureBusinessDocuments(
    [exact, 'second'],
    [
      { id: exact, code: 'SO-001', counterparty: '合成客户' },
      { id: 'other', code: '不匹配' }
    ]
  );
  assert.equal(rows[0].code, 'SO-001');
  assert.equal(rows[0].id, exact);
  assert.equal(rows[1].code, 'second');
  assert.equal(exactBusinessId(Number(exact)), '');
  assert.equal(exactBusinessId(12), '12');
});

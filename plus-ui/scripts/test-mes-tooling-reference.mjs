/** MES 工装关联显示回归：禁止列表页回退展示 19 位工装 ID。 */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parse as parseSfc, compileTemplate } from '@vue/compiler-sfc';
import ts from 'typescript';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

function evaluate(source, filename = 'mesToolingReference.ts') {
  const { outputText, diagnostics } = ts.transpileModule(source, {
    fileName: filename,
    reportDiagnostics: true,
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
  });
  assert.deepEqual(
    diagnostics.filter((entry) => entry.category === ts.DiagnosticCategory.Error),
    [],
    '工装引用工具应可转译'
  );
  const exports = {};
  new Function('exports', outputText)(exports);
  return exports;
}

test('工装引用按字符串 ID 建表并输出正式编码与名称', () => {
  const helper = evaluate(read('src/utils/mesToolingReference.ts'));
  const references = helper.buildToolingReferenceMap([
    { toolingId: '2108880196330000001', toolingCode: 'TG20261010001', toolingName: 'CNC液压虎钳夹具' }
  ]);
  assert.equal(helper.formatToolingReference(references, 2108880196330000001n), 'TG20261010001 · CNC液压虎钳夹具');
  assert.equal(helper.formatToolingReference(references, '999'), '工装 999');
});

for (const page of ['maintenance-plan', 'maintenance-record', 'usage-record']) {
  test(`${page} 批量加载工装引用且模板不再裸显工装ID`, () => {
    const source = read(`src/views/mes/tg/${page}/index.vue`);
    const { descriptor, errors } = parseSfc(source);
    assert.deepEqual(errors, [], `${page} SFC 应可解析`);
    const compiled = compileTemplate({ source: descriptor.template.content, filename: `${page}.vue`, id: page });
    assert.deepEqual(compiled.errors, [], `${page} 模板应可编译`);
    assert.match(source, /mesToolingReference/);
    assert.match(source, /pageSize:\s*1000/);
    assert.match(source, /toolingReference\(scope\.row\.toolingId\)/);
    assert.doesNotMatch(source, /label="工装ID"/);
    assert.doesNotMatch(source, /scope\.row\.toolingCode\s*\|\|\s*scope\.row\.toolingId/);
  });
}

test('标准前端单测命令包含工装引用回归', () => {
  const pkg = JSON.parse(read('package.json'));
  assert.match(pkg.scripts['test:unit'], /test-mes-tooling-reference\.mjs/);
});

test('保养记录作为不可篡改追溯台账时不暴露编辑删除入口', () => {
  const source = read('src/views/mes/tg/maintenance-record/index.vue');
  assert.doesNotMatch(source, /type="selection"/);
  assert.doesNotMatch(source, /mes:tg:record:(?:edit|remove)/);
  assert.doesNotMatch(source, /handleUpdate\(scope\.row\)|handleDelete\(scope\.row\)/);
});

test('使用记录只允许补全未归还上下文且不提交服务端权威字段', () => {
  const source = read('src/views/mes/tg/usage-record/index.vue');
  assert.doesNotMatch(source, /type="selection"/);
  assert.doesNotMatch(source, /mes:tg:usage:remove|handleDelete\(scope\.row\)/);
  assert.match(source, /v-if="scope\.row\.status === 'OUT'"/);
  assert.doesNotMatch(source, /v-model="form\.(?:usageCount|usageHours|status)"/);
  assert.match(source, /operatorId:\s*\[\{ required: true/);
  assert.match(source, /delete formData\.(?:status|usageCount|usageHours)/);
});

import { createUnitTestPlan } from './unit-test-plan.mjs';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const scripts = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8')).scripts;
const command = scripts['test:full-flow-ui'];
const firstSerialInvocation = command.split(/\s*&&\s*/).find((part) => part.startsWith('node --test --test-concurrency=1 '));
const registered = new Set(firstSerialInvocation.slice('node --test --test-concurrency=1 '.length).split(/\s+/));
const suites = [
  'scripts/test-warehouse-safety-test-wiring.mjs',
  'scripts/test-wm-rule-import-casing.mjs',
  'src/views/erp/wm/productionissue/atp-reference-amount.test.mjs',
  'src/views/erp/wm/productionissue/revision2.test.mjs'
];

test('仓储写安全回归必须由标准单测入口执行而非只写临时命令', () => {
  assert.equal(scripts['test:unit'], 'node scripts/unit-test-entry.mjs');
  assert.ok(
    createUnitTestPlan().some((step) => step.npmScript === 'test:full-flow-ui'),
    '标准单测必须包含全流程回归入口'
  );
  assert.ok(registered.has(suites[0]), '接线回归本身必须挂入标准入口');
});

for (const suite of suites.slice(1)) {
  test(`标准串行单测实际注册 ${suite}`, () => {
    assert.ok(registered.has(suite), '只打印文件名或另存日志不等于执行测试');
  });
}

import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
const root = new URL('../', import.meta.url);
const { scripts } = JSON.parse(readFileSync(new URL('package.json', root), 'utf8'));
function registeredTests(command) {
  const commands = command.split(/\s*&&\s*/);
  const prefix = 'node --test --test-concurrency=1 ';
  const nodeTest = commands.find((part) => part.startsWith(prefix));
  assert.ok(nodeTest, 'serial node --test invocation must be present');
  const files = nodeTest.slice(prefix.length).trim().split(/\s+/);
  const receiveSuite = 'src/views/erp/wm/receive/form-status.test.mjs';
  if (commands.includes(`node ${receiveSuite}`)) files.push(receiveSuite);
  return new Set(files);
}
test('full-flow UI regressions are included in the standard unit command', () => {
  assert.match(scripts['test:unit'], /npm run test:full-flow-ui/);
});
test('all new UI regression suites are registered and run serially', () => {
  const command = scripts['test:full-flow-ui'];
  assert.equal(typeof command, 'string');
  assert.match(command, /--test-concurrency=1/);
  for (const file of [
    'scripts/test-ui-task7.mjs',
    'scripts/test-production-issue-warehouse.mjs',
    'scripts/test-decision-display.mjs',
    'scripts/test-decision-shortage-unit.mjs',
    'scripts/test-decision-approval-guard.mjs',
    'scripts/test-workflow-explicit-imports.mjs',
    'scripts/test-build-setup-extend-memory.mjs',
    'scripts/test-build-auto-components-memory.mjs',
    'scripts/test-schedule-publish.mjs',
    'src/views/erp/wm/receive/form-status.test.mjs'
  ]) {
    assert.ok(existsSync(new URL(file, root)), file);
    assert.ok(registeredTests(command).has(file), `${file} must be executed by node`);
  }
});
test('echoing an omitted test filename is not execution', () => {
  const suite = 'scripts/test-build-auto-components-memory.mjs';
  const fake = scripts['test:full-flow-ui'].replace(suite, '') + ' && echo ' + suite;
  assert.equal(registeredTests(fake).has(suite), false, 'a printed filename cannot count as a runnable test');
});
test('the regression entry is not recursively dependent on test:unit', () => {
  assert.doesNotMatch(scripts['test:full-flow-ui'] || '', /test:unit/);
});

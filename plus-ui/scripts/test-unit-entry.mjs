import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const uiRoot = fileURLToPath(new URL('../', import.meta.url));
const scripts = JSON.parse(readFileSync(join(uiRoot, 'package.json'), 'utf8')).scripts;
const npmCli = process.env.npm_execpath || join(dirname(process.execPath), 'node_modules/npm/bin/npm-cli.js');
const fixture = (t) => {
  const directory = mkdtempSync(join(tmpdir(), 'nexus test entry spaces '));
  t.after(() => {
    assert.equal(dirname(realpathSync(directory)), realpathSync(tmpdir()), 'cleanup stays inside the test temporary directory');
    assert.ok(directory.includes('nexus test entry spaces '));
    rmSync(directory, { recursive: true, force: true });
  });
  mkdirSync(join(directory, 'scripts'));
  return directory;
};
const readEvents = (directory) =>
  readFileSync(join(directory, 'events.jsonl'), 'utf8')
    .trim()
    .split('\n')
    .map((line) => JSON.parse(line));

async function plans() {
  assert.ok(existsSync(join(uiRoot, 'scripts/unit-test-plan.mjs')), 'structured test plans must exist');
  return import('./unit-test-plan.mjs');
}

test('legacy npm argument selects only every knowledge/MCP test, without legacy/report/layout', (t) => {
  const directory = fixture(t);
  for (const file of readdirSync(join(uiRoot, 'scripts')).filter((name) => name.endsWith('.mjs') && !name.startsWith('test-'))) {
    cpSync(join(uiRoot, 'scripts', file), join(directory, 'scripts', file));
  }
  const recorder = `import { appendFileSync } from 'node:fs'; appendFileSync('events.jsonl', JSON.stringify({name: process.argv[2], args: process.argv.slice(3)}) + String.fromCharCode(10));`;
  writeFileSync(join(directory, 'scripts/record.mjs'), recorder);
  writeFileSync(join(directory, 'scripts/legacy-fixture.mjs'), `import './record.mjs';`);
  const testsDirectory = join(directory, 'tests/ai-knowledge-mcp');
  mkdirSync(testsDirectory, { recursive: true });
  // A new filename must be discovered too; this catches hard-coded suite lists.
  const files = [...readdirSync(join(uiRoot, 'tests/ai-knowledge-mcp')).filter((name) => name.endsWith('.test.mjs')), 'newly-added.test.mjs'];
  for (const file of files) {
    writeFileSync(
      join(testsDirectory, file),
      `import { appendFileSync } from 'node:fs'; import { test } from 'node:test'; test(${JSON.stringify(file)}, () => appendFileSync('events.jsonl', JSON.stringify({ name: ${JSON.stringify(file)} }) + String.fromCharCode(10)));`
    );
  }
  const command = scripts['test:unit'].replace(/"esbuild [^"]+"/, '"node scripts/legacy-fixture.mjs"');
  writeFileSync(
    join(directory, 'package.json'),
    JSON.stringify({
      type: 'module',
      scripts: { ...scripts, 'test:unit': command, 'test:report': 'node scripts/record.mjs report', 'test:layout': 'node scripts/record.mjs layout' }
    })
  );
  const env = { ...process.env };
  delete env.NODE_TEST_CONTEXT;
  const result = spawnSync(process.execPath, [npmCli, 'run', 'test:unit', '--', '--ai-knowledge-mcp'], {
    cwd: directory,
    env,
    encoding: 'utf8',
    timeout: 30000
  });
  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
  assert.deepEqual(
    readEvents(directory)
      .map((event) => event.name)
      .sort(),
    files.sort(),
    'npm arguments must reach the selector before any other suite runs'
  );
});

test('default plan preserves every original suite and bundle in sequence, including report then layout', async () => {
  const { createUnitTestPlan } = await plans();
  assert.equal(scripts['test:unit'], 'node scripts/unit-test-entry.mjs');
  const plan = createUnitTestPlan([]);
  const expected = [
    'scripts/ai-knowledge-mcp-unit-entry.mjs',
    'src/utils/safeExpression.ts',
    'scripts/test-safe-expression.mjs',
    'test:decision-config',
    'src/views/ai/decision/decisionViewModel.ts',
    'scripts/test-decision-view-model.mjs',
    'scripts/test-decision-ui-entries.mjs',
    'src/views/ai/flow/designer/useFlowStore.ts',
    'scripts/test-system-flow-layout.mjs',
    'test:full-flow-ui',
    'test:competition-demo',
    'scripts/test-mes-spare-management.mjs',
    'scripts/test-mes-tooling-reference.mjs',
    'scripts/test-list-initialization.mjs',
    'scripts/test-cv-defect.mjs',
    'test:report',
    'test:layout'
  ];
  const registered = plan.flatMap((step) => (step.npmScript ? [step.npmScript] : step.args));
  let cursor = -1;
  for (const argument of expected) {
    const next = registered.indexOf(argument, cursor + 1);
    assert.ok(next > cursor, `${argument} must be executed in the original order`);
    cursor = next;
  }
  assert.ok(
    plan.every((step) => step.npmScript || Array.isArray(step.args)),
    'spawn arguments must be structured'
  );
});

test('explicit AI entry includes model/decision/flow/engineering and builds generated bundles before consumers', async () => {
  const { createUnitTestPlan } = await plans();
  assert.equal(scripts['test:ai'], 'node scripts/unit-test-entry.mjs --ai');
  const plan = createUnitTestPlan(['--ai']);
  const args = plan.flatMap((step) => step.args || []);
  for (const file of [
    'scripts/ai-knowledge-mcp-unit-entry.mjs',
    'scripts/test-ai-model-config.mjs',
    'scripts/test-decision-config.mjs',
    'scripts/test-decision-view-model.mjs',
    'scripts/test-decision-ui-entries.mjs',
    'scripts/test-decision-display.mjs',
    'scripts/test-decision-shortage-unit.mjs',
    'scripts/test-decision-approval-guard.mjs',
    'scripts/test-decision-action-execution-results.mjs',
    'scripts/test-system-flow-layout.mjs',
    'scripts/test-flow-instance-layout.mjs',
    'scripts/test-flow-trigger-current-contract.mjs',
    'scripts/test-engineering-model.mjs',
    'scripts/test-engineering-artifacts.mjs',
    'scripts/test-engineering-obj.mjs',
    'scripts/test-engineering-chat.mjs',
    'src/views/ai/decision/decisionDisplay.test.mjs',
    'src/views/ai/flow/designer/NodeParameterForm.entity-id.test.mjs'
  ])
    assert.ok(args.includes(file), `${file} must be executed`);
  for (const [output, consumer] of [
    ['node_modules/.tmp/decision-config.mjs', 'scripts/test-decision-config.mjs'],
    ['node_modules/.tmp/decision-view-model.mjs', 'scripts/test-decision-view-model.mjs'],
    ['node_modules/.tmp/system-flow-store.mjs', 'scripts/test-system-flow-layout.mjs']
  ]) {
    const build = plan.findIndex((step) => step.args?.includes(`--outfile=${output}`));
    const run = plan.findIndex((step) => step.args?.includes(consumer));
    assert.ok(build >= 0 && run > build, `${output} must be built before ${consumer} even with a clean .tmp`);
  }
  assert.ok(!args.includes('scripts/test-flow-trigger-display.mjs'), 'retired draft migration is not part of the current contract');
  assert.throws(() => createUnitTestPlan(['--unknown']), /不支持|Unsupported/);
  assert.throws(() => createUnitTestPlan(['--ai', '--ai-knowledge-mcp']), /不支持|Unsupported/);
});

test('runner preserves argv with spaces and shell syntax, runs sequentially, and stops at a failing child', async (t) => {
  const runnerPath = join(uiRoot, 'scripts/unit-test-runner.mjs');
  assert.ok(existsSync(runnerPath), 'structured child runner must exist');
  const { runSteps } = await import('./unit-test-runner.mjs');
  const diagnostics = [];
  t.mock.method(console, 'error', (message) => diagnostics.push(message));
  const directory = fixture(t);
  const record = join(directory, 'child with spaces.mjs');
  writeFileSync(
    record,
    `import { appendFileSync } from 'node:fs'; appendFileSync('events.jsonl', JSON.stringify({ name: process.argv[2], args: process.argv.slice(3) }) + String.fromCharCode(10)); if (process.argv[2] === 'fail') process.exit(23);`
  );
  const special = 'spaces "quotes" & echo injected $(whoami)';
  const result = runSteps(
    [
      { name: 'first', args: [record, 'first', special] },
      { name: 'failure', args: [record, 'fail'] },
      { name: 'must not run', args: [record, 'last'] }
    ],
    { cwd: directory, stdio: 'pipe' }
  );
  assert.equal(result, 23);
  assert.deepEqual(readEvents(directory), [
    { name: 'first', args: [special] },
    { name: 'fail', args: [] }
  ]);
  assert.equal(runSteps([{ name: 'missing module', args: [resolve(directory, 'absent.mjs')] }], { cwd: directory, stdio: 'pipe' }), 1);
  assert.deepEqual(diagnostics, ['[failure] exited with 23', '[missing module] exited with 1']);
});

test('AI bootstrap creates and loads real decision bundles from an empty temporary output directory', async (t) => {
  const { createUnitTestPlan } = await plans();
  const { runSteps } = await import('./unit-test-runner.mjs');
  const directory = fixture(t);
  assert.equal(existsSync(join(directory, 'node_modules/.tmp')), false);
  const steps = createUnitTestPlan(['--ai']).filter((step) => step.args?.some((arg) => arg.startsWith('--outfile=')));
  const relocated = steps.map((step) => ({
    ...step,
    args: step.args.map((arg) => (arg === 'node_modules/esbuild/bin/esbuild' || arg.startsWith('src/') ? resolve(uiRoot, arg) : arg))
  }));
  assert.equal(runSteps(relocated, { cwd: directory, stdio: 'pipe' }), 0);
  for (const output of ['decision-config', 'decision-view-model', 'system-flow-store']) {
    assert.ok(existsSync(join(directory, `node_modules/.tmp/${output}.mjs`)), `${output} must be generated without prior tmp state`);
  }
  const config = await import(pathToFileURL(join(directory, 'node_modules/.tmp/decision-config.mjs')).href);
  assert.equal(config.validateDecisionConfig(config.createDefaultDecisionConfig()), null);
  const decision = await import(pathToFileURL(join(directory, 'node_modules/.tmp/decision-view-model.mjs')).href);
  assert.equal(decision.shouldPollDecision('RUNNING'), true);
});

test('knowledge discovery rejects an empty suite and ignores helper files and directories', async (t) => {
  const { knowledgeTestFiles } = await plans();
  const directory = fixture(t);
  const tests = join(directory, 'tests/ai-knowledge-mcp');
  mkdirSync(tests, { recursive: true });
  writeFileSync(join(tests, 'helper.mjs'), '');
  mkdirSync(join(tests, 'not-a-file.test.mjs'));
  assert.throws(() => knowledgeTestFiles(directory), /No AI knowledge\/MCP tests/);
  writeFileSync(join(tests, 'b.test.mjs'), '');
  writeFileSync(join(tests, 'a.test.mjs'), '');
  assert.deepEqual(knowledgeTestFiles(directory), ['tests/ai-knowledge-mcp/a.test.mjs', 'tests/ai-knowledge-mcp/b.test.mjs']);
});

test('package unit entry uses only the structured launcher and rejects legacy command strings', async () => {
  const { createUnitTestPlan } = await plans();
  assert.equal(scripts['test:unit'], 'node scripts/unit-test-entry.mjs');
  const legacyCommand = createUnitTestPlan()
    .filter((step) => step.npmScript)
    .map((step) => `npm run ${step.npmScript}`)
    .join(' && ');
  assert.throws(() => createUnitTestPlan([legacyCommand]), /Unsupported/);
  assert.throws(() => createUnitTestPlan(['echo npm run test:full-flow-ui']), /Unsupported/);
});

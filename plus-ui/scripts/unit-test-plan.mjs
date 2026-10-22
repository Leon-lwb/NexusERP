import { readdirSync } from 'node:fs';
import { join } from 'node:path';
import { uiRoot } from './unit-test-runner.mjs';

const node = (name, ...args) => ({ name, args });
const npm = (script) => ({ name: script, npmScript: script });
const serial = (name, files) => node(name, '--test', '--test-concurrency=1', ...files);
const bundle = (source, output, ...args) =>
  node(
    `bundle ${output}`,
    'node_modules/esbuild/bin/esbuild',
    source,
    '--bundle',
    '--format=esm',
    ...args,
    `--outfile=node_modules/.tmp/${output}.mjs`
  );
const knowledge = () => node('AI knowledge/MCP', 'scripts/ai-knowledge-mcp-unit-entry.mjs');
const decisionView = () => bundle('src/views/ai/decision/decisionViewModel.ts', 'decision-view-model');
const flowStore = () => bundle('src/views/ai/flow/designer/useFlowStore.ts', 'system-flow-store', '--platform=node', '--packages=external');

export function knowledgeTestFiles(root = uiRoot) {
  const files = readdirSync(join(root, 'tests/ai-knowledge-mcp'), { withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name.endsWith('.test.mjs'))
    .map((entry) => `tests/ai-knowledge-mcp/${entry.name}`)
    .sort();
  if (!files.length) throw new Error('No AI knowledge/MCP tests discovered');
  return files;
}

const contracts = () =>
  serial('AI stage-one entry/menu contracts', [
    'scripts/test-unit-entry.mjs',
    'scripts/test-ai-stage-one-permissions.mjs',
    'scripts/test-wm-rule-import-casing.mjs'
  ]);

export function createUnitTestPlan(args = [], root = uiRoot) {
  if (args.length === 1 && args[0] === '--ai-knowledge-mcp') return [knowledge()];
  if (args.length === 1 && args[0] === '--ai') {
    const aiScripts = readdirSync(join(root, 'scripts'))
      .filter((file) => /^test-(?:ai-model|decision-|engineering-).*\.mjs$/.test(file))
      .sort()
      .map((file) => `scripts/${file}`);
    return [
      knowledge(),
      bundle('src/views/ai/config/decisionValidation.ts', 'decision-config'),
      decisionView(),
      flowStore(),
      serial('AI model/decision/flow/engineering', [
        ...aiScripts,
        'scripts/test-chat-knowledge-selection.mjs',
        'scripts/test-system-flow-layout.mjs',
        'scripts/test-flow-instance-layout.mjs',
        'scripts/test-flow-trigger-current-contract.mjs',
        'src/views/ai/decision/decisionDisplay.test.mjs',
        'src/views/ai/flow/designer/NodeParameterForm.entity-id.test.mjs'
      ]),
      contracts()
    ];
  }
  if (args.length) throw new Error(`Unsupported test selector: ${args.join(', ')}; use --ai or --ai-knowledge-mcp`);
  return [
    knowledge(),
    bundle('src/utils/safeExpression.ts', 'safe-expression'),
    node('safe expression', 'scripts/test-safe-expression.mjs'),
    npm('test:decision-config'),
    decisionView(),
    node('decision view model', 'scripts/test-decision-view-model.mjs'),
    node('decision UI entries', 'scripts/test-decision-ui-entries.mjs'),
    flowStore(),
    node('system Flow layout', 'scripts/test-system-flow-layout.mjs'),
    npm('test:full-flow-ui'),
    npm('test:competition-demo'),
    serial('MES/list/CV', [
      'scripts/test-mes-spare-management.mjs',
      'scripts/test-mes-tooling-reference.mjs',
      'scripts/test-list-initialization.mjs',
      'scripts/test-cv-defect.mjs'
    ]),
    npm('test:report'),
    npm('test:layout'),
    contracts()
  ];
}

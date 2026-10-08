import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { transformSync } from 'esbuild';
import { compileScript, parse } from '@vue/compiler-sfc';
import * as pinia from 'pinia';
import * as vue from 'vue';

const root = new URL('../', import.meta.url);
const source = (path) => readFileSync(new URL(path, root), 'utf8');
const { descriptor, errors } = parse(source('src/views/ai/flow/monitor/InstanceDetail.vue'));
assert.deepEqual(errors, []);

// Execute production SFC setup and the real store. Only browser/network boundaries are replaced.
function moduleFromTypeScript(content, imports) {
  const { code } = transformSync(content, { loader: 'ts', format: 'cjs', target: 'node20' });
  const module = { exports: {} };
  const require = (id) => {
    assert.ok(Object.hasOwn(imports, id), `Unexpected dependency: ${id}`);
    return imports[id];
  };
  new Function('require', 'module', 'exports', code)(require, module, module.exports);
  return module.exports;
}
const storeModule = moduleFromTypeScript(source('src/views/ai/flow/designer/useFlowStore.ts'), { pinia, vue });
const componentScript = compileScript(descriptor, { id: 'flow-instance-layout-test' }).content;

const analysis = {
  nodes: [
    { id: 'context', type: 'industrial.deliveryContext', data: { label: 'context' } },
    { id: 'baseline', type: 'industrial.baselineRisk', data: { label: 'baseline' } },
    { id: 'shortage', type: 'industrial.shortageSimulation', data: { label: 'shortage' } },
    { id: 'aps', type: 'industrial.apsCompare', data: { label: 'aps' } },
    { id: 'merge', type: 'merge', data: { label: 'merge' } },
    { id: 'rank', type: 'industrial.scenarioRank', data: { label: 'rank' } },
    { id: 'llm', type: 'llm', data: { label: 'llm' } },
    { id: 'guard', type: 'industrial.decisionGuard', data: { label: 'guard' } },
    { id: 'actionProposal', type: 'industrial.actionProposal', data: { label: 'actionProposal' } }
  ],
  edges: [
    ...['baseline', 'shortage', 'aps'].map((target) => ({ source: 'context', target })),
    ...['baseline', 'shortage', 'aps'].map((source) => ({ source, target: 'merge' })),
    { source: 'merge', target: 'rank' },
    { source: 'rank', target: 'llm' },
    { source: 'llm', target: 'guard' },
    { source: 'guard', target: 'actionProposal' }
  ]
};

function setup(configSnapshot, configJson = JSON.stringify(analysis)) {
  pinia.setActivePinia(pinia.createPinia());
  const calls = [];
  const runs = analysis.nodes.map((node) => ({ nodeId: node.id, status: 'SUCCEEDED' }));
  const component = moduleFromTypeScript(componentScript, {
    vue: { ...vue, onMounted() {}, onBeforeUnmount() {} },
    'vue-router': {
      useRoute: () => ({ query: { instanceId: 'history-instance', flowId: 'current-flow' } }),
      useRouter: () => ({})
    },
    'element-plus': { ElMessage: {} },
    '../designer/useFlowStore': storeModule,
    '../designer/FlowCanvas.vue': { default: {} },
    '../components/JsonViewer.vue': { default: {} },
    get '../flowTriggerDisplay'() {
      return moduleFromTypeScript(source('src/views/ai/flow/flowTriggerDisplay.ts'), {});
    },
    '@/api/ai/flow': {
      getInstance: async (id) => {
        calls.push(['getInstance', id]);
        return { data: { id, flowId: 'current-flow', configSnapshot, status: 'SUCCEEDED' } };
      },
      getFlow: async (id) => {
        calls.push(['getFlow', id]);
        return { data: { configJson } };
      },
      listNodeRuns: async () => ({ data: runs }),
      retryInstance: async () => {
        throw new Error('Not used by layout tests');
      }
    }
  }).default;
  const view = component.setup({}, { expose() {} });
  const store = storeModule.useFlowStore();
  const fitted = [];
  if (view.canvasRef) {
    view.canvasRef.value = {
      fitAllNodes() {
        fitted.push(store.nodes.map((node) => ({ id: node.id, position: { ...node.position } })));
      }
    };
  }
  return { view, store, calls, fitted };
}

function assertSeparated(store) {
  assert.equal(store.nodes.length, analysis.nodes.length);
  for (const [index, node] of store.nodes.entries()) {
    assert.ok(Number.isFinite(node.position.x) && Number.isFinite(node.position.y));
    for (const other of store.nodes.slice(index + 1)) {
      assert.ok(
        Math.abs(node.position.x - other.position.x) >= 160 || Math.abs(node.position.y - other.position.y) >= 80,
        `History nodes ${node.id} and ${other.id} must not overlap`
      );
    }
  }
  for (const edge of store.edges) {
    const from = store.nodes.find((node) => node.id === edge.source);
    const to = store.nodes.find((node) => node.id === edge.target);
    assert.ok(to.position.x > from.position.x, `${edge.source} -> ${edge.target} must flow left-to-right`);
  }
}

test('history lays out all nine industrial snapshot nodes without positions', async () => {
  const snapshot = JSON.stringify(analysis);
  const { view, store } = setup(snapshot);
  await view.loadDetail('history-instance');
  assertSeparated(store);
  assert.equal(store.toConfigJson(), snapshot, 'display layout must not rewrite the instance snapshot');
  assert.equal(store.dirty, false);
  assert.ok(
    store.nodes.every((node) => node.data.status === 'SUCCEEDED'),
    'runtime coloring must survive layout'
  );
});

test('history separates legacy snapshot nodes that all have the same saved position', async () => {
  const snapshot = JSON.stringify({ ...analysis, nodes: analysis.nodes.map((node) => ({ ...node, position: [0, 0] })) });
  const { view, store } = setup(snapshot);
  await view.loadDetail('history-instance');
  assertSeparated(store);
  assert.equal(store.toConfigJson(), snapshot);
});

test('history repairs snapshots with a mixture of valid and missing coordinates', async () => {
  const snapshot = JSON.stringify({
    ...analysis,
    nodes: analysis.nodes.map((node, index) => ({ ...node, ...(index % 2 ? { position: [index * 260, 40] } : {}) }))
  });
  const { view, store } = setup(snapshot);
  await view.loadDetail('history-instance');
  assertSeparated(store);
  assert.equal(store.toConfigJson(), snapshot);
});

test('history retains valid manually saved coordinates instead of unconditionally relaying out', async () => {
  const positioned = analysis.nodes.map((node, index) => ({ ...node, position: [120 + index * 300, 200 + index * 100] }));
  const snapshot = JSON.stringify({ ...analysis, nodes: positioned });
  const { view, store } = setup(snapshot);
  await view.loadDetail('history-instance');
  assert.deepEqual(
    store.nodes.map((node) => node.position),
    positioned.map((node) => ({ x: node.position[0], y: node.position[1] }))
  );
  assert.equal(store.toConfigJson(), snapshot);
});

test('instance snapshots take precedence over a newer current flow definition', async () => {
  const snapshot = JSON.stringify(analysis);
  const { view, store, calls } = setup(snapshot, JSON.stringify({ nodes: [], edges: [] }));
  await view.loadDetail('history-instance');
  assert.equal(store.nodes.length, 9);
  assert.equal(
    calls.some(([method]) => method === 'getFlow'),
    false,
    'do not substitute current coordinates or topology for history'
  );
});

test('older instances without a snapshot also get display-only layout in the current-config fallback', async () => {
  const configJson = JSON.stringify(analysis);
  const { view, store, calls } = setup(undefined, configJson);
  await view.loadDetail('history-instance');
  assertSeparated(store);
  assert.deepEqual(
    calls.find(([method]) => method === 'getFlow'),
    ['getFlow', 'current-flow']
  );
  assert.equal(store.toConfigJson(), configJson);
});

test('the readonly canvas fits the loaded snapshot, not the initially empty graph', async () => {
  const { view, store, fitted } = setup(JSON.stringify(analysis));
  await view.loadDetail('history-instance');
  assert.equal(fitted.length, 1, 'fitAllNodes must be called after asynchronous graph loading');
  assert.deepEqual(
    fitted[0],
    store.nodes.map((node) => ({ id: node.id, position: node.position }))
  );
  assert.equal(fitted[0].length, 9);
});

test('the actual readonly FlowCanvas is bound to the viewport-fitting ref', () => {
  const { ast } = descriptor.template;
  let canvas;
  function visit(node) {
    if (node.type === 1 && node.tag === 'FlowCanvas') canvas = node;
    for (const child of node.children ?? []) visit(child);
  }
  visit(ast);
  assert.ok(canvas, 'instance detail must render its graph');
  assert.equal(canvas.props.find((prop) => prop.type === 6 && prop.name === 'ref')?.value?.content, 'canvasRef');
  assert.ok(canvas.props.some((prop) => prop.type === 6 && prop.name === 'readonly'));
});

test('the history-layout regression is executed by the standard unit entry', () => {
  const { scripts } = JSON.parse(source('package.json'));
  assert.match(scripts['test:unit'], /npm run test:full-flow-ui/);
  assert.match(scripts['test:full-flow-ui'], /node --test[^&]*scripts\/test-flow-instance-layout\.mjs/);
});

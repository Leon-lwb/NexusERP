import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createPinia, setActivePinia } from 'pinia';
import { useFlowStore } from '../node_modules/.tmp/system-flow-store.mjs';

setActivePinia(createPinia());
const store = useFlowStore();
const analysis = JSON.stringify({
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
});
store.fromConfigJson(analysis, { displayOnlyLayout: true });
assert.equal(store.nodes.length, 9);
for (const [index, node] of store.nodes.entries()) {
  assert.ok(Number.isFinite(node.position.x) && Number.isFinite(node.position.y));
  for (const other of store.nodes.slice(index + 1)) {
    assert.ok(
      Math.abs(node.position.x - other.position.x) >= 160 || Math.abs(node.position.y - other.position.y) >= 80,
      `${node.id} and ${other.id} must not overlap`
    );
  }
}
for (const edge of store.edges) {
  const source = store.nodes.find((node) => node.id === edge.source);
  const target = store.nodes.find((node) => node.id === edge.target);
  assert.ok(target.position.x > source.position.x, `${edge.source} -> ${edge.target} must flow left-to-right`);
}
assert.equal(store.toConfigJson(), analysis, 'display-only positions must not rewrite the protected graph');
const colliding = JSON.stringify({
  ...JSON.parse(analysis),
  nodes: JSON.parse(analysis).nodes.map((node) => ({ ...node, position: [0, 0] }))
});
store.fromConfigJson(colliding, { displayOnlyLayout: true });
assert.equal(
  new Set(store.nodes.map((node) => `${node.position.x},${node.position.y}`)).size,
  9,
  'legacy system coordinates at the same location must also be separated'
);
assert.equal(store.toConfigJson(), colliding, 'repairing visual collisions must not rewrite the stored graph');

assert.equal(store.dirty, false);

const execution = JSON.stringify({ nodes: [{ id: 'execute', type: 'industrial.executeApprovedActions', data: { label: 'execute' } }], edges: [] });
store.fromConfigJson(execution, { displayOnlyLayout: true });
assert.deepEqual(store.nodes[0].position, { x: 40, y: 40 });
assert.equal(store.toConfigJson(), execution);
const positioned = JSON.stringify({
  nodes: [{ id: 'execute', type: 'industrial.executeApprovedActions', position: [320, 140], data: { label: 'execute' } }],
  edges: []
});
store.fromConfigJson(positioned, { displayOnlyLayout: true });
assert.deepEqual(store.nodes[0].position, { x: 320, y: 140 }, 'stored coordinates must be honored');
assert.equal(store.toConfigJson(), positioned);
store.fromConfigJson(analysis);
assert.deepEqual(store.nodes[0].position, { x: 0, y: 0 }, 'normal flows must keep their existing behavior');
const cyclic = JSON.stringify({
  nodes: [
    { id: 'a', type: 'llm', data: { label: 'a' } },
    { id: 'b', type: 'llm', data: { label: 'b' } }
  ],
  edges: [
    { source: 'a', target: 'b' },
    { source: 'b', target: 'a' }
  ]
});
store.fromConfigJson(cyclic, { displayOnlyLayout: true });
assert.ok(
  store.nodes.every((node) => Number.isFinite(node.position.x) && node.position.x < 2000),
  'even corrupt cyclic graphs must not lock up the designer'
);
assert.equal(store.toConfigJson(), cyclic);

const designer = readFileSync('src/views/ai/flow/designer/index.vue', 'utf8');
assert.match(designer, /:readonly="isSystemDecisionFlow"/, 'protected system canvas must be read-only');
assert.match(designer, /displayOnlyLayout:\s*isSystemDecisionFlow\.value/, 'system graphs must opt in to visual-only layout');
assert.match(designer, /canvasRef\.value\?\.fitAllNodes\(\)/, 'auto-arranged system nodes must fit into the viewport');

assert.match(designer, /if \(isSystemDecisionFlow\.value\) \{\s*ElMessage\.warning/, 'system flow cannot enter generic save path');
const canvas = readFileSync('src/views/ai/flow/designer/FlowCanvas.vue', 'utf8');
assert.match(canvas, /new ResizeObserver/, 'system preview must re-fit after the canvas width changes');
assert.match(canvas, /requestAnimationFrame\(/, 're-fit must wait for vue-flow to measure its resized viewport');
assert.match(canvas, /if \(!props\.readonly \|\| !props\.nodes\.length\) return;/, 'editing ordinary flows must not be auto-fitted on resize');
assert.match(canvas, /canvasResizeObserver\?\.disconnect\(\)/, 'observer must be disconnected when leaving the designer');
assert.match(
  canvas,
  /function onDrop\(event: DragEvent\) \{\s*if \(props\.readonly\) return;/,
  'read-only system canvas must reject HTML5 node drops'
);

process.stdout.write('受保护系统流程画布布局检查通过\n');

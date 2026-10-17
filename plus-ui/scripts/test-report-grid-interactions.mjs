import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { parse, compileScript } from '@vue/compiler-sfc';
import { createRenderer, reactive, h, nextTick } from 'vue';
import { readFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';

await build({
  entryPoints: ['src/components/report/designer/PaperGrid.vue'],
  outfile: 'node_modules/.tmp/report-grid-interactions.mjs',
  bundle: true,
  format: 'esm',
  platform: 'node',
  external: ['vue'],
  plugins: [
    {
      name: 'grid-fixture',
      setup(builder) {
        builder.onResolve({ filter: /^(element-plus|@\/api\/report\/assets)$/ }, (args) => ({ path: args.path, namespace: 'fixture' }));
        builder.onLoad({ filter: /.*/, namespace: 'fixture' }, (args) => ({
          contents:
            args.path === 'element-plus'
              ? 'export const ElMessage={warning:()=>{}};'
              : 'export const assetImage=async()=>new Blob([], {type:"image/png"});',
          loader: 'js'
        }));
        builder.onResolve({ filter: /^@\// }, (args) => ({ path: resolve('src', args.path.slice(2) + (args.path.endsWith('.vue') ? '' : '.ts')) }));
        builder.onLoad({ filter: /\.vue$/ }, async (args) => ({
          contents: compileScript(parse(await readFile(args.path, 'utf8')).descriptor, { id: args.path }).content,
          loader: 'ts',
          resolveDir: dirname(args.path)
        }));
      }
    }
  ]
});
const PaperGrid = (await import(pathToFileURL(resolve('node_modules/.tmp/report-grid-interactions.mjs')))).default;
const renderer = createRenderer({
  createElement: () => ({}),
  createComment: () => ({}),
  createText: () => ({}),
  insert: () => {},
  remove: () => {},
  setElementText: () => {},
  setText: () => {},
  patchProp: () => {},
  parentNode: () => null,
  nextSibling: () => null
});

/** 挂载合成纸张画布，记录原子草稿提交。 */
function mount() {
  const style = { fontSize: 10, bold: false, align: 'LEFT', color: '#111111', background: '#ffffff' };
  const updates = [];
  const props = reactive({
    modelValue: {
      schemaVersion: 1,
      code: 'TEST',
      title: '测试',
      kind: 'DOCUMENT',
      datasetCode: 'TEST',
      datasetVersionId: '9223372036854775807',
      parameters: [],
      summaries: [],
      repeatHeader: true,
      page: { width: 510, height: 800, marginLeft: 30, marginRight: 30, marginTop: 30, marginBottom: 30, rowHeight: 24 },
      columns: ['code', 'name', 'amount'].map((field) => ({ field, label: field, width: 150, scale: 4, expression: null, style: { ...style } })),
      headerRows: [
        { height: 32, cells: [0, 1, 2].map((column) => ({ column, colSpan: 1, rowSpan: 1, text: String(column), field: null, style: { ...style } })) }
      ],
      footerRows: []
    },
    disabled: false,
    'onUpdate:modelValue': (value) => updates.push(value)
  });
  const component = { ...PaperGrid, render: () => null };
  const app = renderer.createApp({ render: () => h(component, props) });
  const vm = app.mount({});
  return { props, updates, state: vm.$.subTree.component.setupState, unmount: () => app.unmount() };
}
/** 构造指针事件并提供独立捕获目标。 */
function pointer(clientX, clientY = 0) {
  return {
    pointerId: 1,
    button: 0,
    clientX,
    clientY,
    preventDefault() {},
    stopPropagation() {},
    currentTarget: { setPointerCapture() {}, releasePointerCapture() {} }
  };
}

test('一次拖动只在松开时提交一次，过程不污染父草稿和撤销历史', () => {
  const subject = mount();
  subject.state.startColumnDrag(pointer(0), 0);
  subject.state.moveDrag(pointer(40));
  subject.state.moveDrag(pointer(60));
  assert.equal(subject.updates.length, 0);
  assert.equal(subject.props.modelValue.columns[0].width, 150);
  subject.state.finishDrag(pointer(60));
  subject.state.finishDrag(pointer(60));
  assert.equal(subject.updates.length, 1);
  assert.equal(subject.updates[0].columns[0].width + subject.updates[0].columns[1].width, 300);
  assert.equal(subject.updates[0].datasetVersionId, '9223372036854775807');
  subject.unmount();
});

test('合并后拆分重新解析当前锚点，后续Shift选区不会扩大到旧行跨度', async () => {
  const subject = mount();
  subject.props.modelValue.headerRows.push(structuredClone(JSON.parse(JSON.stringify(subject.props.modelValue.headerRows[0]))));
  subject.state.selectCell('headerRows', 0, 0, { shiftKey: false });
  subject.state.selectCell('headerRows', 1, 1, { shiftKey: true });
  subject.state.mergeSelection();
  subject.props.modelValue = subject.updates.at(-1);
  await nextTick();
  subject.state.selectCell('headerRows', 0, 0, { shiftKey: false });
  subject.state.splitSelection();
  subject.props.modelValue = subject.updates.at(-1);
  await nextTick();
  subject.state.selectCell('headerRows', 0, 1, { shiftKey: true });
  subject.state.styleSelection({ bold: true });
  const result = subject.updates.at(-1);
  assert.deepEqual(
    result.headerRows[1].cells.map((cell) => cell.style.bold),
    [false, false, false]
  );
  assert.equal(result.headerRows[0].cells[0].style.bold, true);
  assert.equal(result.headerRows[0].cells[1].style.bold, true);
  subject.unmount();
});

test('取消手势或外部草稿变化不会提交旧的拖动结果', async () => {
  const subject = mount();
  subject.state.startColumnDrag(pointer(0), 0);
  subject.state.moveDrag(pointer(30));
  subject.state.cancelDrag();
  subject.state.finishDrag(pointer(30));
  assert.equal(subject.updates.length, 0);
  subject.state.startColumnDrag(pointer(0), 0);
  subject.state.moveDrag(pointer(30));
  subject.props.modelValue = { ...subject.props.modelValue, title: '新的草稿' };
  await nextTick();
  subject.state.finishDrag(pointer(30));
  assert.equal(subject.updates.length, 0);
  subject.unmount();
});

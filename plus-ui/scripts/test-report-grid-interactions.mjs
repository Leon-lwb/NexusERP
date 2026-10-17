import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { parse, compileScript } from '@vue/compiler-sfc';
import { createRenderer, reactive, h, nextTick, getCurrentInstance } from 'vue';
import { ElInputNumber } from 'element-plus/es/components/input-number/index.mjs';
import { selectProps } from 'element-plus/es/components/select/src/select.mjs';
import { useSelect } from 'element-plus/es/components/select/src/useSelect.mjs';
import { ID_INJECTION_KEY } from 'element-plus/es/hooks/use-id/index.mjs';
import { Element } from 'domhandler';
import { is as matchesSelector } from 'css-select';
import { readFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';

/** Node宿主补齐真实组件的FocusEvent校验，测试结束后恢复原全局值。 */
const originalFocusEvent = globalThis.FocusEvent;
if (!originalFocusEvent) globalThis.FocusEvent = class FocusEvent extends Event {};
after(() => {
  if (!originalFocusEvent) delete globalThis.FocusEvent;
});

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
              : 'export const assetImage=async()=>new Blob([], {type:"image/png"});export const listAssets=async()=>({data:[]});export const assetMetadata=(id)=>globalThis.__gridAssets(id);',
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
function mount(viewportWidth) {
  const style = { fontSize: 10, bold: false, align: 'LEFT', color: '#111111', background: '#ffffff' };
  const updates = [];
  const selections = [];
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
    selection: undefined,
    contextKey: 'report-A:user-A:tenant-A',
    onSelect: (value) => {
      selections.push(value);
      props.selection = value;
    },
    'onUpdate:modelValue': (value) => updates.push(value)
  });
  const component = {
    ...PaperGrid,
    render:
      viewportWidth === undefined
        ? () => null
        : function () {
            const instance = getCurrentInstance();
            return h('div', {
              ref: (element) => {
                if (element) {
                  if (element.clientWidth === undefined) element.clientWidth = viewportWidth;
                  instance.setupState.viewport = element;
                }
              }
            });
          }
  };
  const app = renderer.createApp({ render: () => h(component, props) });
  const vm = app.mount({});
  return { props, updates, selections, state: vm.$.subTree.component.setupState, unmount: () => app.unmount() };
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

test('适应宽度在下一帧合并尺寸变化，忽略仅高度通知并在卸载时取消', async () => {
  const originals = { observer: globalThis.ResizeObserver, frame: globalThis.requestAnimationFrame, cancel: globalThis.cancelAnimationFrame };
  const frames = new Map();
  let frameId = 0;
  let resized;
  let disconnected = false;
  globalThis.ResizeObserver = class {
    constructor(callback) {
      resized = callback;
    }
    observe() {}
    disconnect() {
      disconnected = true;
    }
  };
  globalThis.requestAnimationFrame = (callback) => {
    const id = ++frameId;
    frames.set(id, callback);
    return id;
  };
  globalThis.cancelAnimationFrame = (id) => frames.delete(id);
  const subject = mount(500);
  try {
    await nextTick();
    const initial = subject.state.zoom;
    subject.state.viewport.clientWidth = 600;
    resized([]);
    assert.equal(subject.state.zoom, initial, 'ResizeObserver回调不能同步改变布局');
    subject.state.viewport.clientWidth = 700;
    resized([]);
    assert.equal(frames.size, 1);
    const callback = frames.values().next().value;
    frames.clear();
    callback();
    await nextTick();
    assert.equal(subject.state.zoom, (700 - 64) / (510 * (4 / 3)));
    resized([]);
    assert.equal(frames.size, 0, '相同可用宽度的高度变化不再次排版');
    subject.state.viewport.clientWidth = 800;
    resized([]);
    assert.equal(frames.size, 1);
    subject.unmount();
    assert.equal(frames.size, 0);
    assert.equal(disconnected, true);
  } finally {
    if (!disconnected) subject.unmount();
    if (originals.observer) globalThis.ResizeObserver = originals.observer;
    else delete globalThis.ResizeObserver;
    if (originals.frame) globalThis.requestAnimationFrame = originals.frame;
    else delete globalThis.requestAnimationFrame;
    if (originals.cancel) globalThis.cancelAnimationFrame = originals.cancel;
    else delete globalThis.cancelAnimationFrame;
  }
});

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

/** 配置两个媒体，便于验证当前位置选择与删除失效。 */
function mediaSubject() {
  const subject = mount();
  subject.props.modelValue.schemaVersion = 2;
  subject.props.modelValue.print = {
    orientation: 'PORTRAIT',
    groupBy: [],
    repeatFooter: false,
    media: [0, 1].map(() => ({ kind: 'QR', field: 'code', assetVersionId: null, x: 50, y: 50, width: 72, height: 72, repeat: false }))
  };
  return subject;
}

/** 构造可观察默认行为的画布按键。 */
function key(keyName, values = {}) {
  return {
    key: keyName,
    shiftKey: false,
    ctrlKey: false,
    metaKey: false,
    altKey: false,
    isComposing: false,
    target: { closest: () => null },
    prevented: false,
    preventDefault() {
      this.prevented = true;
    },
    stopPropagation() {},
    ...values
  };
}

test('选中媒体向父级报告统一选区，删除后当前媒体位置失效', async () => {
  const subject = mediaSubject();
  await nextTick();
  subject.state.startMediaDrag(pointer(0), 1, false);
  assert.deepEqual(subject.selections.at(-1), { type: 'media', index: 1 });
  subject.state.cancelDrag();
  subject.props.modelValue.print.media.pop();
  await nextTick();
  assert.equal(subject.selections.at(-1), undefined);
  subject.unmount();
});

test('连续方向键只维护候选，最后keyup提交一次且Shift使用5毫米', async () => {
  const subject = mediaSubject();
  await nextTick();
  subject.props.selection = { type: 'media', index: 0 };
  await nextTick();
  subject.state.editorKeydown(key('ArrowRight'));
  subject.state.editorKeydown(key('ArrowRight', { repeat: true }));
  subject.state.editorKeydown(key('ArrowDown', { shiftKey: true }));
  assert.equal(subject.updates.length, 0);
  assert.equal(subject.props.modelValue.print.media[0].x, 50);
  assert.ok(Math.abs(subject.state.design.print.media[0].x - 50 - 72 / 25.4) < 1e-9);
  subject.state.editorKeyup(key('ArrowRight'));
  assert.equal(subject.updates.length, 0);
  subject.state.editorKeyup(key('ArrowDown'));
  subject.state.editorKeyup(key('ArrowDown'));
  assert.equal(subject.updates.length, 1);
  assert.ok(Math.abs(subject.updates[0].print.media[0].y - 50 - (5 * 72) / 25.4) < 1e-9);
  subject.unmount();
});

test('输入框、选择器、可编辑区域及组合输入保持原按键行为', async () => {
  const subject = mediaSubject();
  await nextTick();
  subject.props.selection = { type: 'media', index: 0 };
  await nextTick();
  for (const values of [
    { target: { closest: () => ({ tagName: 'INPUT' }) } },
    { target: { closest: () => ({ role: 'combobox' }) } },
    { target: { closest: () => ({ isContentEditable: true }) } },
    { isComposing: true },
    { keyCode: 229 },
    { ctrlKey: true },
    { altKey: true }
  ]) {
    const event = key('ArrowRight', values);
    subject.state.editorKeydown(event);
    assert.equal(event.prevented, false);
  }
  subject.state.editorKeyup(key('ArrowRight'));
  assert.equal(subject.updates.length, 0);
  subject.unmount();
});

test('Escape取消方向键候选，失焦提交一次；模板及身份切换丢弃所有候选', async () => {
  const subject = mediaSubject();
  await nextTick();
  subject.props.selection = { type: 'media', index: 0 };
  await nextTick();
  subject.state.editorKeydown(key('ArrowRight'));
  subject.state.cancelWithEscape(key('Escape'));
  subject.state.editorKeyup(key('ArrowRight'));
  assert.equal(subject.updates.length, 0);
  assert.equal(subject.state.design.print.media[0].x, 50);
  subject.state.editorKeydown(key('ArrowRight'));
  subject.state.finishKeyboard();
  subject.state.finishKeyboard();
  assert.equal(subject.updates.length, 1);
  subject.state.editorKeydown(key('ArrowRight'));
  subject.props.contextKey = 'report-B:user-A:tenant-A';
  await nextTick();
  subject.state.editorKeyup(key('ArrowRight'));
  assert.equal(subject.updates.length, 1);
  assert.equal(subject.selections.at(-1), undefined);
  subject.props.selection = { type: 'media', index: 0 };
  await nextTick();
  subject.state.startMediaDrag(pointer(0), 0, false);
  subject.state.moveDrag(pointer(40));
  subject.props.contextKey = 'report-B:user-B:tenant-B';
  await nextTick();
  subject.state.finishDrag(pointer(40));
  assert.equal(subject.updates.length, 1);
  subject.unmount();
});

/** 使用同一编译规则挂载属性面板，接口只投影固定版本元数据。 */
async function inspectorSubject(selection = { type: 'media', index: 0 }, realNumbers = false) {
  const output = 'node_modules/.tmp/report-selection-inspector' + (realNumbers ? '-events' : '') + '.mjs';
  await build({
    entryPoints: ['src/components/report/designer/SelectionInspector.vue'],
    outfile: output,
    bundle: true,
    format: 'esm',
    platform: 'node',
    external: ['vue'],
    plugins: [
      {
        name: 'inspector-fixture',
        setup(builder) {
          builder.onResolve({ filter: /^(element-plus|@\/api\/report\/assets)$/ }, (args) => ({ path: args.path, namespace: 'fixture' }));
          builder.onLoad({ filter: /.*/, namespace: 'fixture' }, (args) => ({
            contents:
              args.path === 'element-plus'
                ? 'export const ElMessage={warning:()=>{}};'
                : 'export const listAssets=async()=>({data:[]});export const assetImage=async()=>new Blob([],{type:"image/png"});export const assetMetadata=(id)=>globalThis.__gridAssets(id);',
            loader: 'js'
          }));
          builder.onResolve({ filter: /^@\// }, (args) => ({ path: resolve('src', args.path.slice(2) + (args.path.endsWith('.vue') ? '' : '.ts')) }));
          builder.onLoad({ filter: /\.vue$/ }, async (args) => ({
            contents:
              realNumbers && !args.path.endsWith('SelectionInspector.vue')
                ? 'export default {render:()=>null};'
                : compileScript(parse(await readFile(args.path, 'utf8')).descriptor, { id: args.path, inlineTemplate: realNumbers }).content,
            loader: 'ts',
            resolveDir: dirname(args.path)
          }));
        }
      }
    ]
  });
  const Inspector = (await import(pathToFileURL(resolve(output)))).default;
  const source = mediaSubject();
  const modelValue = JSON.parse(JSON.stringify(source.props.modelValue));
  source.unmount();
  const updates = [],
    selections = [];
  const props = reactive({
    modelValue,
    selection,
    contextKey: 'report-A:user-A',
    disabled: false,
    fields: [
      { name: 'code', label: '编码', type: 'STRING' },
      { name: 'orderNo', label: '单号', type: 'STRING' },
      { name: 'amount', label: '金额', type: 'DECIMAL' }
    ],
    'onUpdate:modelValue': (value) => {
      updates.push(value);
      if (realNumbers) props.modelValue = value;
    },
    onSelect: (value) => selections.push(value)
  });
  const component = realNumbers ? Inspector : { ...Inspector, render: () => null };
  const app = renderer.createApp({ render: () => h(component, props) });
  const numbers = new Map();
  if (realNumbers) installNumberFixture(app, numbers);
  const vm = app.mount({});
  return { props, updates, selections, numbers, state: vm.$.subTree.component.setupState, unmount: () => app.unmount() };
}

/** 保留真实ElInputNumber的计算和事件，仅将原生输入及图标绘制替换为无DOM宿主。 */
function installNumberFixture(app, numbers) {
  const InputHost = {
    setup(_props, { expose }) {
      expose({ input: { setAttribute() {}, removeAttribute() {}, addEventListener() {} } });
      return () => h('input');
    }
  };
  const NumberControl = {
    ...ElInputNumber,
    setup(props, context) {
      const instance = getCurrentInstance();
      const render = ElInputNumber.setup(props, context);
      return () => {
        const tree = render(instance.proxy, []);
        const input = tree.children.find((node) => node.type?.name === 'ElInput');
        const increase = tree.children.find((node) => node.props?.class?.includes('el-input-number__increase'));
        const decrease = tree.children.find((node) => node.props?.class?.includes('el-input-number__decrease'));
        const stepUp = increase.dirs[0].value,
          stepDown = decrease.dirs[0].value;
        increase.dirs = decrease.dirs = undefined;
        increase.children = decrease.children = [];
        tree.dynamicChildren = increase.dynamicChildren = decrease.dynamicChildren = null;
        input.type = InputHost;
        numbers.set(props.ariaLabel, {
          value: input.props['model-value'],
          increase: stepUp,
          decrease: stepDown,
          input: input.props.onInput,
          change: input.props.onChange,
          blur: () => input.props.onBlur(new FocusEvent('blur')),
          enter: () => {
            const event = key('Enter', { code: 'Enter' });
            input.props.onKeydown(event);
            for (const handler of [].concat(context.attrs.onKeydown || [])) handler(event);
          }
        });
        return tree;
      };
    }
  };
  const Container = {
    setup(_props, { slots }) {
      return () => h('fixture-container', slots.default?.());
    }
  };
  for (const name of [
    'ElForm',
    'ElFormItem',
    'ElInput',
    'ElEmpty',
    'ElButton',
    'ElSelect',
    'ElOption',
    'ElDivider',
    'ElRadioGroup',
    'ElRadioButton',
    'ElCheckbox',
    'ElColorPicker',
    'ElAlert'
  ])
    app.component(name, Container);
  app.component('ElInputNumber', NumberControl);
  app.directive('hasPermi', {});
}

test('媒体毫米输入在提交时形成一次更新，重复失焦不重复记录且QR保持正方形', async () => {
  const subject = await inspectorSubject();
  subject.state.inputs.width = 40;
  assert.equal(subject.updates.length, 0);
  subject.state.commitDimension('width');
  subject.state.commitDimension('width');
  assert.equal(subject.updates.length, 1);
  assert.ok(Math.abs(subject.updates[0].print.media[0].width - (40 * 72) / 25.4) < 1e-9);
  assert.equal(subject.updates[0].print.media[0].width, subject.updates[0].print.media[0].height);
  assert.equal(subject.props.modelValue.print.media[0].width, 72);
  subject.unmount();
});

test('列宽属性复用相邻列守恒边界，删除媒体后清空选择', async () => {
  const subject = await inspectorSubject({ type: 'column', index: 0 });
  subject.state.inputs.width = 999;
  subject.state.commitDimension('width');
  assert.equal(subject.updates[0].columns[0].width, 280);
  assert.equal(subject.updates[0].columns[1].width, 20);
  subject.props.selection = { type: 'media', index: 0 };
  await nextTick();
  subject.state.removeSelected();
  assert.equal(subject.updates.at(-1).print.media.length, 1);
  assert.equal(subject.selections.at(-1), undefined);
  subject.unmount();
});

test('属性字段拒绝未批准编码，输入候选在模板切换后失效', async () => {
  const subject = await inspectorSubject();
  subject.state.setField('amount');
  assert.equal(subject.updates.length, 0);
  subject.state.setField('orderNo');
  assert.equal(subject.updates.at(-1).print.media[0].field, 'orderNo');
  subject.state.inputs.x = 60;
  subject.props.contextKey = 'report-B:user-A';
  await nextTick();
  subject.state.commitDimension('x');
  assert.equal(subject.updates.length, 1);
  subject.unmount();
});

test('固定图片不可访问时保留原版本，元数据不请求目录也不切最新', async () => {
  const calls = [];
  globalThis.__gridAssets = async (id) => {
    calls.push(id);
    throw Error('不可访问');
  };
  const subject = await inspectorSubject();
  const media = subject.props.modelValue.print.media[0];
  media.kind = 'IMAGE';
  media.assetVersionId = '9223372036854775806';
  media.field = null;
  await nextTick();
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(calls, ['9223372036854775806']);
  assert.equal(subject.state.assetState.status, 'unavailable');
  assert.equal(subject.props.modelValue.print.media[0].assetVersionId, '9223372036854775806');
  assert.equal(subject.updates.length, 0);
  subject.unmount();
});

test('固定版本名称和像素尺寸可重读，身份切换拒绝迟到元数据与图库选择', async () => {
  const pending = [];
  globalThis.__gridAssets = (id) =>
    new Promise((resolve) => {
      pending.push(() => resolve({ data: { versionId: id, resourceId: 'asset-A', name: '合成印章', width: 320, height: 160 } }));
    });
  const subject = await inspectorSubject();
  Object.assign(subject.props.modelValue.print.media[0], { kind: 'IMAGE', assetVersionId: 'asset-version-A', field: null });
  await nextTick();
  pending[0]();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(subject.state.assetState.metadata.name, '合成印章');
  assert.equal(subject.state.assetState.metadata.width, 320);
  void subject.state.loadMetadata();
  subject.state.openPicker();
  subject.props.contextKey = 'report-B:user-B';
  await nextTick();
  subject.state.selectAsset({ versionId: 'asset-version-B', title: '另一印章' });
  assert.equal(subject.updates.length, 0);
  pending[1]();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(subject.state.assetState.status, 'loading');
  pending[2]();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(subject.state.assetState.status, 'ready');
  subject.unmount();
});

/** 挂载页面脚本，以真实选区、撤销栈和焦点对象验证编辑会话。 */
async function designerSubject() {
  const apiNames = [
    'listDatasets',
    'listCatalogs',
    'reportDetail',
    'createReport',
    'saveReport',
    'validateDraft',
    'previewDraft',
    'publishReport',
    'exportTemplatePackage'
  ];
  await build({
    entryPoints: ['src/views/report/designer/index.vue'],
    outfile: 'node_modules/.tmp/report-grid-page.mjs',
    bundle: true,
    format: 'esm',
    platform: 'node',
    external: ['vue'],
    plugins: [
      {
        name: 'designer-page-fixture',
        setup(builder) {
          builder.onResolve({ filter: /^(vue-router|element-plus|file-saver|@\/api\/report|@\/store\/modules\/user)$/ }, (args) => ({
            path: args.path,
            namespace: 'fixture'
          }));
          builder.onLoad({ filter: /.*/, namespace: 'fixture' }, (args) => {
            if (args.path === '@/store/modules/user') return { contents: 'export const useUserStore=()=>globalThis.__gridPage.user;', loader: 'js' };
            if (args.path === 'element-plus')
              return {
                contents:
                  'export const ElMessage={warning:()=>{},success:()=>{}};export const ElMessageBox={confirm:async()=>true,prompt:()=>globalThis.__gridPage.prompt?.()||Promise.resolve({value:"calc"})};',
                loader: 'js'
              };
            if (args.path === 'file-saver') return { contents: 'export const saveAs=()=>{};', loader: 'js' };
            if (args.path === 'vue-router')
              return {
                contents:
                  'export const useRoute=()=>globalThis.__gridPage.route;export const useRouter=()=>({push:()=>{}});export const onBeforeRouteLeave=()=>{};export const onBeforeRouteUpdate=()=>{};',
                loader: 'js'
              };
            return {
              contents: apiNames.map((name) => 'export const ' + name + '=(...args)=>globalThis.__gridPage.api.' + name + '(...args);').join('\n'),
              loader: 'js'
            };
          });
          builder.onResolve({ filter: /^@\// }, (args) => ({ path: resolve('src', args.path.slice(2) + (args.path.endsWith('.vue') ? '' : '.ts')) }));
          builder.onLoad({ filter: /\.vue$/ }, async (args) => ({
            contents: args.path.replaceAll('\\', '/').endsWith('/views/report/designer/index.vue')
              ? compileScript(parse(await readFile(args.path, 'utf8')).descriptor, { id: args.path }).content
              : 'export default {render:()=>null};',
            loader: 'ts',
            resolveDir: dirname(args.path)
          }));
        }
      }
    ]
  });
  const source = mediaSubject();
  const dsl = JSON.parse(JSON.stringify(source.props.modelValue));
  source.unmount();
  const fixture = {
    route: reactive({ query: { id: 'report-A' } }),
    user: reactive({ userId: 'user-A', tenantId: 'tenant-A' }),
    api: Object.fromEntries(apiNames.map((name) => [name, async () => ({ data: [] })]))
  };
  fixture.api.reportDetail = async (id) => ({ data: { definition: { id, name: id, revision: 1 }, dsl } });
  globalThis.__gridPage = fixture;
  const Designer = (await import(pathToFileURL(resolve('node_modules/.tmp/report-grid-page.mjs')))).default;
  const component = { ...Designer, render: () => null };
  const app = renderer.createApp(component);
  const vm = app.mount({});
  await new Promise((resolve) => setImmediate(resolve));
  return { fixture, state: vm.$.setupState, unmount: () => app.unmount() };
}

test('侧栏打开聚焦内容并恢复触发点，Escape依次取消手势、关闭侧栏、退出专注', async () => {
  const subject = await designerSubject();
  let focused = 0,
    restored = 0;
  subject.state.focusMode = true;
  subject.state.sidebarContent = { focus: () => focused++ };
  subject.state.openSidebar('properties', { currentTarget: { isConnected: true, focus: () => restored++ } });
  subject.state.sidebarOpened();
  assert.equal(focused, 1);
  subject.state.paperGrid = { cancelGesture: () => true };
  subject.state.escapeEditor(key('Escape'));
  assert.equal(subject.state.sidebar, 'properties');
  assert.equal(subject.state.focusMode, true);
  subject.state.paperGrid = { cancelGesture: () => false };
  subject.state.escapeEditor(key('Escape'));
  assert.equal(subject.state.sidebar, undefined);
  subject.state.sidebarClosed();
  assert.equal(restored, 1);
  assert.equal(subject.state.focusMode, true);
  subject.state.escapeEditor(key('Escape'));
  assert.equal(subject.state.focusMode, false);
  subject.unmount();
});

test('撤销后选区按当前坐标解析，身份切换清空草稿、预览和历史', async () => {
  const subject = await designerSubject();
  subject.state.selection = { type: 'cell', band: 'headerRows', row: 0, column: 1 };
  subject.state.dsl.headerRows[0].cells[1].text = '变更';
  await nextTick();
  subject.state.undo();
  assert.deepEqual(subject.state.selection, { type: 'cell', band: 'headerRows', row: 0, column: 1 });
  assert.equal(subject.state.dsl.headerRows[0].cells[1].text, '1');
  subject.state.previewOpen = true;
  subject.fixture.user.userId = 'user-B';
  await nextTick();
  assert.equal(subject.state.selection, undefined);
  assert.equal(subject.state.previewOpen, false);
  assert.equal(subject.state.dsl, undefined);
  assert.equal(subject.state.canUndo, false);
  subject.unmount();
});

test('连续媒体移动提交后的单次撤销恢复整次动作，编辑器辅助状态不进入DSL', async () => {
  const page = await designerSubject();
  const subject = mediaSubject();
  await nextTick();
  subject.props.selection = { type: 'media', index: 0 };
  await nextTick();
  for (let repeat = 0; repeat < 8; repeat++) subject.state.editorKeydown(key('ArrowRight', { repeat: repeat > 0 }));
  subject.state.editorKeyup(key('ArrowRight'));
  assert.equal(subject.updates.length, 1);
  page.state.selection = { type: 'media', index: 0 };
  page.state.dsl = subject.updates[0];
  await nextTick();
  assert.ok(page.state.dsl.print.media[0].x > 50);
  page.state.undo();
  assert.equal(page.state.dsl.print.media[0].x, 50);
  assert.deepEqual(page.state.selection, { type: 'media', index: 0 });
  const serialized = JSON.stringify(page.state.dsl);
  page.state.toggleFocus();
  page.state.openSidebar('fields');
  assert.equal(JSON.stringify(page.state.dsl), serialized);
  page.unmount();
  subject.unmount();
});

test('外部草稿变化或禁用丢弃键盘候选，边界无位移不产生更新', async () => {
  const subject = mediaSubject();
  await nextTick();
  subject.props.selection = { type: 'media', index: 0 };
  await nextTick();
  subject.state.editorKeydown(key('ArrowRight'));
  subject.props.modelValue.title = '外部修订';
  await nextTick();
  subject.state.editorKeyup(key('ArrowRight'));
  assert.equal(subject.updates.length, 0);
  subject.state.editorKeydown(key('ArrowRight'));
  subject.props.disabled = true;
  await nextTick();
  subject.state.editorKeyup(key('ArrowRight'));
  assert.equal(subject.updates.length, 0);
  subject.props.disabled = false;
  subject.props.modelValue.print.media[0].x = 30;
  await nextTick();
  subject.state.editorKeydown(key('ArrowLeft', { shiftKey: true }));
  subject.state.editorKeyup(key('ArrowLeft'));
  assert.equal(subject.updates.length, 0);
  subject.unmount();
});

test('组合输入Enter不确认属性，显式替换资产保持尺寸且只绑定所选版本', async () => {
  globalThis.__gridAssets = async (versionId) => ({ data: { versionId, resourceId: 'asset-A', name: '合成印章', width: 320, height: 160 } });
  const subject = await inspectorSubject();
  subject.state.inputs.width = 40;
  subject.state.commitOnEnter(key('Enter', { isComposing: true }), () => subject.state.commitDimension('width'));
  subject.state.commitOnEnter(key('Enter', { keyCode: 229 }), () => subject.state.commitDimension('width'));
  assert.equal(subject.updates.length, 0);
  Object.assign(subject.props.modelValue.print.media[0], { kind: 'IMAGE', assetVersionId: 'original-version', field: null });
  await nextTick();
  subject.state.openPicker();
  subject.state.selectAsset({ versionId: '9223372036854775806', title: '新印章' });
  assert.equal(subject.updates.length, 1);
  assert.equal(subject.updates[0].print.media[0].assetVersionId, '9223372036854775806');
  assert.equal(subject.updates[0].print.media[0].width, 72);
  assert.equal(subject.props.modelValue.print.media[0].assetVersionId, 'original-version');
  subject.state.selectAsset({ versionId: 'another-version', title: '迟到选择' });
  assert.equal(subject.updates.length, 1);
  subject.unmount();
});

test('元数据响应版本不符时显示不可访问，不修改已绑定版本', async () => {
  globalThis.__gridAssets = async () => ({ data: { versionId: 'latest-version', resourceId: 'asset-A', name: '最新印章', width: 320, height: 160 } });
  const subject = await inspectorSubject();
  Object.assign(subject.props.modelValue.print.media[0], { kind: 'IMAGE', assetVersionId: 'fixed-version', field: null });
  await nextTick();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(subject.state.assetState.status, 'unavailable');
  assert.equal(subject.props.modelValue.print.media[0].assetVersionId, 'fixed-version');
  assert.equal(subject.updates.length, 0);
  subject.unmount();
});

test('切换数据集关闭旧侧栏并拒绝迟到计算列输入', async () => {
  const subject = await designerSubject();
  let resolvePrompt;
  subject.fixture.prompt = () =>
    new Promise((resolve) => {
      resolvePrompt = resolve;
    });
  subject.state.openSidebar('properties');
  const pending = subject.state.insertFormula();
  subject.state.datasets = [
    {
      dataset: { id: 'dataset-B', publishedVersion: 'version-B' },
      descriptor: {
        code: 'NEW_DATASET',
        document: true,
        fields: [{ name: 'code', label: '编码', type: 'STRING' }],
        defaultColumns: ['code'],
        parameters: []
      }
    }
  ];
  subject.state.chooseDataset('dataset-B');
  resolvePrompt({ value: 'obsoleteFormula' });
  await pending;
  assert.equal(subject.state.sidebar, undefined);
  assert.deepEqual(
    subject.state.dsl.columns.map((column) => column.field),
    ['code']
  );
  subject.unmount();
});

test('真实数字组件的媒体加减无需焦点即提交，重复blur不新增历史且QR仍正方形', async (context) => {
  const subject = await inspectorSubject({ type: 'media', index: 0 }, true);
  context.after(subject.unmount);
  for (const [label, property] of [
    ['宽度（mm）', 'width'],
    ['高度（mm）', 'height'],
    ['横坐标（mm）', 'x'],
    ['纵坐标（mm）', 'y']
  ]) {
    const previousCount = subject.updates.length;
    const oldValue = Number(subject.numbers.get(label).value);
    subject.numbers.get(label).increase();
    assert.equal(subject.updates.length, previousCount + 1, label + '步进应立即提交');
    const expected = Number((oldValue + 0.5).toFixed(2));
    assert.ok(Math.abs(subject.props.modelValue.print.media[0][property] - (expected * 72) / 25.4) < 1e-7);
    assert.equal(subject.props.modelValue.print.media[0].width, subject.props.modelValue.print.media[0].height);
    await nextTick();
    assert.equal(Number(subject.numbers.get(label).value), expected);
    subject.numbers.get(label).blur();
    subject.numbers.get(label).blur();
    assert.equal(subject.updates.length, previousCount + 1);
  }
  const previousWidth = subject.props.modelValue.print.media[0].width;
  subject.numbers.get('宽度（mm）').decrease();
  assert.ok(subject.props.modelValue.print.media[0].width < previousWidth);
  assert.equal(subject.props.modelValue.print.media[0].width, subject.props.modelValue.print.media[0].height);
});

test('真实数字组件列宽步进立即提交并守恒，普通键入在change与Enter及blur组合中只提交一次', async (context) => {
  const subject = await inspectorSubject({ type: 'column', index: 0 }, true);
  context.after(subject.unmount);
  subject.numbers.get('列宽度（mm）').increase();
  assert.equal(subject.updates.length, 1);
  assert.equal(subject.props.modelValue.columns[0].width + subject.props.modelValue.columns[1].width, 300);
  await nextTick();
  subject.numbers.get('列宽度（mm）').input('60');
  await nextTick();
  subject.numbers.get('列宽度（mm）').input('65');
  await nextTick();
  assert.equal(subject.updates.length, 1, '连续键入不应逐字符提交');
  subject.numbers.get('列宽度（mm）').change('65');
  subject.numbers.get('列宽度（mm）').enter();
  await nextTick();
  subject.numbers.get('列宽度（mm）').blur();
  assert.equal(subject.updates.length, 2);
  assert.ok(Math.abs(subject.props.modelValue.columns[0].width - (65 * 72) / 25.4) < 1e-7);
  subject.numbers.get('列宽度（mm）').input('70');
  await nextTick();
  subject.numbers.get('列宽度（mm）').enter();
  subject.numbers.get('列宽度（mm）').change('70');
  await nextTick();
  subject.numbers.get('列宽度（mm）').blur();
  subject.numbers.get('列宽度（mm）').blur();
  assert.equal(subject.updates.length, 3, 'Enter先于change仍只提交一次');
  subject.numbers.get('列宽度（mm）').input('999');
  await nextTick();
  subject.numbers.get('列宽度（mm）').change('999');
  assert.equal(subject.props.modelValue.columns[0].width, 280);
  assert.equal(subject.props.modelValue.columns[1].width, 20);
});

test('真实数字组件的固定行高与字号步进立即提交并沿用高度和字号边界', async (context) => {
  const subject = await inspectorSubject({ type: 'cell', band: 'headerRows', row: 0, column: 0 }, true);
  context.after(subject.unmount);
  subject.numbers.get('固定行高度（mm）').increase();
  assert.equal(subject.updates.length, 1);
  assert.ok(subject.props.modelValue.headerRows[0].height > 32);
  await nextTick();
  subject.numbers.get('字号').increase();
  assert.equal(subject.updates.length, 2);
  assert.equal(subject.props.modelValue.headerRows[0].cells[0].style.fontSize, 11);
  await nextTick();
  subject.numbers.get('固定行高度（mm）').input('999');
  await nextTick();
  subject.numbers.get('固定行高度（mm）').change('999');
  assert.equal(subject.props.modelValue.headerRows[0].height, 160);
  await nextTick();
  subject.numbers.get('固定行高度（mm）').blur();
  subject.numbers.get('字号').blur();
  assert.equal(subject.updates.length, 3);
});

test('真实数字组件的小数位步进立即提交，失焦不重复记录', async (context) => {
  const subject = await inspectorSubject({ type: 'column', index: 0 }, true);
  context.after(subject.unmount);
  subject.numbers.get('小数位').increase();
  assert.equal(subject.updates.length, 1);
  assert.equal(subject.props.modelValue.columns[0].scale, 5);
  await nextTick();
  subject.numbers.get('小数位').blur();
  subject.numbers.get('小数位').blur();
  assert.equal(subject.updates.length, 1);
});

/** 使用真实CSS选择器解析DOM属性，焦点节点与Element Plus的输入保持一致。 */
function popupInput(role = 'combobox') {
  const root = new Element('div', { class: role === 'combobox' ? 'el-select' : 'el-input-number' });
  const input = new Element('input', { role, 'aria-expanded': 'true', 'aria-controls': 'inner-popup', 'aria-haspopup': 'listbox' });
  input.parent = root;
  root.children = [input];
  input.closest = (selector) => {
    for (let element = input; element; element = element.parent) if (matchesSelector(element, selector)) return element;
    return null;
  };
  return input;
}

/** 挂载Element Plus真实选择器状态与键盘处理，省略依赖浏览器的渲染。 */
function realSelectSubject() {
  let state;
  const app = renderer.createApp(
    {
      props: selectProps,
      setup(props, { emit }) {
        state = useSelect(props, emit);
        return () => null;
      }
    },
    { modelValue: 'code' }
  );
  app.provide(ID_INJECTION_KEY, { prefix: 1, current: 0 });
  app.mount({});
  state.expanded.value = true;
  return { state, unmount: () => app.unmount() };
}

test('真实选择器焦点留在展开输入时首个Escape只收起菜单，后续才关闭侧栏和专注', async (context) => {
  const page = await designerSubject(),
    select = realSelectSubject(),
    input = popupInput();
  context.after(page.unmount);
  context.after(select.unmount);
  page.state.focusMode = true;
  page.state.openSidebar('properties');
  const event = key('Escape', { code: 'Escape', target: input });
  page.state.escapeEditor(event);
  assert.equal(event.prevented, false, 'window捕获阶段应将展开选择器的Escape交给目标');
  select.state.handleKeydown(event);
  await nextTick();
  assert.equal(select.state.expanded.value, false);
  assert.equal(event.prevented, true);
  assert.equal(page.state.sidebar, 'properties');
  assert.equal(page.state.focusMode, true);
  input.attribs['aria-expanded'] = 'false';
  page.state.escapeEditor(key('Escape', { code: 'Escape', target: input }));
  assert.equal(page.state.sidebar, undefined);
  assert.equal(page.state.focusMode, true);
  page.state.escapeEditor(key('Escape', { code: 'Escape', target: input }));
  assert.equal(page.state.focusMode, false);
});

test('专注模式中的展开选择器和数字输入弹层先消费Escape，活动画布候选仍优先取消', async (context) => {
  const page = await designerSubject();
  context.after(page.unmount);
  for (const role of ['combobox', 'spinbutton']) {
    const input = popupInput(role);
    page.state.focusMode = true;
    page.state.paperGrid = { cancelGesture: () => true };
    const active = key('Escape', { code: 'Escape', target: input });
    page.state.escapeEditor(active);
    assert.equal(active.prevented, true, '活动手势先取消');
    assert.equal(page.state.focusMode, true);
    page.state.paperGrid = { cancelGesture: () => false };
    const expanded = key('Escape', { code: 'Escape', target: input });
    page.state.escapeEditor(expanded);
    assert.equal(expanded.prevented, false, role + '弹层仍展开时不退出专注');
    assert.equal(page.state.focusMode, true);
    input.attribs['aria-expanded'] = 'false';
    page.state.escapeEditor(key('Escape', { code: 'Escape', target: input }));
    assert.equal(page.state.focusMode, false);
  }
});

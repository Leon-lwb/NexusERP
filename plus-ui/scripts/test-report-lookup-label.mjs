import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { resolve, dirname } from 'node:path';

/** 空宿主操作用于保留组件响应式行为，浏览器布局另用真实页面验证。 */
const noop = () => {};
/** 先提供最小客户端环境，再加载VueUse与Element Plus，使选项缓存监听生效。 */
globalThis.document = {
  createElement: () => ({ style: {}, setAttribute: noop, removeAttribute: noop }),
  querySelector: () => null,
  addEventListener: noop,
  removeEventListener: noop
};
/** 测试环境没有实际窗口，也不会发起网络请求。 */
globalThis.window = { document, navigator: globalThis.navigator ?? { userAgent: 'node' }, addEventListener: noop, removeEventListener: noop };
/** 所有依赖使用同一个CJS Vue实例，避免注入上下文断开。 */
const require = createRequire(resolve('package.json'));
/** 当前项目安装的真实响应式运行时。 */
const { createRenderer, reactive, nextTick, h } = require('vue');
/** 将当前组件模板一起编译，覆盖只测试setup时遗漏的标签缓存问题。 */
const { parse, compileScript } = require('@vue/compiler-sfc');
/** TypeScript模块仅在内存中转换。 */
const { transformSync } = require('esbuild');
/** 当前项目组件库目录。 */
const ep = dirname(require.resolve('element-plus/package.json'));
/** 保留真实选择器setup、选项Map和selectedLabel监听。 */
const Select = require(resolve(ep, 'lib/components/select/src/select.vue_vue_type_script_lang.js')).default;
/** 保留真实选项的注册、销毁和标签计算。 */
const Option = require(resolve(ep, 'lib/components/select/src/option.vue_vue_type_script_lang.js')).default;

/**
 * 在内存中加载编译模块，API依赖由测试夹具提供。
 * @param source 当前源码
 * @param imports 依赖解析函数
 * @returns 编译后的模块导出
 */
function evaluate(source, imports = require) {
  const module = { exports: {} };
  const { code } = transformSync(source, { loader: 'ts', format: 'cjs' });
  new Function('require', 'module', 'exports', code)(imports, module, module.exports);
  return module.exports;
}
/** 最小渲染宿主，只省略布局与弹层，不替换选择器逻辑。 */
const renderer = createRenderer({
  createElement: () => ({}),
  createComment: () => ({}),
  createText: () => ({}),
  insert: noop,
  remove: noop,
  setElementText: noop,
  setText: noop,
  patchProp: noop,
  parentNode: () => null,
  nextSibling: () => null
});
/** 等待异步API、Vue渲染和选项Map的post监听完成。 */
const flush = async () => {
  await new Promise(setImmediate);
  await nextTick();
};

/** 直接断言真实ElSelect的输入框标签，完整ID不会随名称更新被提交或截断。 */
test('异步回显更新真实ElSelect已选标签并保留完整ID', async (t) => {
  let complete;
  const pending = new Promise((resolve) => {
    complete = resolve;
  });
  const api = { reportLookup: async () => ({ data: { options: [], total: 0 } }), reportLookupSelected: () => pending };
  const model = evaluate(readFileSync('src/views/report/model.ts', 'utf8'));
  const filename = resolve('src/components/report/ParameterLookup.vue');
  const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename });
  const Lookup = evaluate(compileScript(descriptor, { id: 'lookup-label-regression', inlineTemplate: true }).content, (name) =>
    name === '@/api/report' ? api : name === '@/views/report/model' ? model : require(name)
  ).default;
  const changes = [];
  const props = reactive({
    modelValue: '9223372036854775807',
    version: 'v',
    parameter: 'orderId',
    label: '采购订单',
    'onUpdate:modelValue': (value) => changes.push(value)
  });
  const app = renderer.createApp({ render: () => h(Lookup, props) });
  app.component('el-select', {
    ...Select,
    inheritAttrs: false,
    /** 省略DOM布局，仍渲染真实选项并执行其注册生命周期。 */
    render() {
      return this.$slots.default?.();
    }
  });
  app.component('el-option', { ...Option, render: () => null });
  app.component('el-button', { inheritAttrs: false, render: () => null });
  t.after(() => app.unmount());
  const root = app.mount({});
  await flush();
  const select = root.$.subTree.component.subTree.children[0].component;
  assert.equal(select.setupState.selectedLabel, '正在读取采购订单名称…');
  complete({ data: { value: props.modelValue, label: 'PO-SYNTH · 合成供应商' } });
  await flush();
  await flush();
  assert.equal(select.setupState.optionsArray[0].currentLabel, 'PO-SYNTH · 合成供应商');
  assert.equal(select.setupState.selectedLabel, 'PO-SYNTH · 合成供应商');
  assert.equal(props.modelValue, '9223372036854775807');
  assert.deepEqual(changes, []);
});

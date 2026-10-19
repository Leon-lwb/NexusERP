import { createRenderer, defineComponent, h, inject, nextTick, provide, reactive, ref } from 'vue';
import ProductionReceiptDialog from '../src/components/material-trace/ProductionReceiptDialog.vue';
import ComponentCorrectionDialog from '../src/components/material-trace/ComponentCorrectionDialog.vue';
import ProductOutputDialog from '../src/components/material-trace/ProductOutputDialog.vue';
import CompletionManifestPanel from '../src/components/material-trace/CompletionManifestPanel.vue';
import CodeResolver from '../src/components/material-trace/CodeResolver.vue';
import SnSelector from '../src/components/material-trace/SnSelector.vue';
import SerialRetireDialog from '../src/components/material-trace/SerialRetireDialog.vue';
import SerialRegistration from '../src/components/material-trace/SerialRegistration.vue';
import IdentityTracePanel from '../src/components/material-trace/IdentityTracePanel.vue';
import BatchRecallPanel from '../src/components/material-trace/BatchRecallPanel.vue';

/** 用 Vue 真实运行时与内存宿主执行组件事件，Element 外观只做薄占位。 */
const renderer = createRenderer<any, any>({
  createElement: (tag) => ({ tag, props: {}, children: [], parent: null }),
  createText: (text) => ({ tag: '#text', text, children: [], props: {}, parent: null }),
  createComment: (text) => ({ tag: '#comment', text, children: [], props: {}, parent: null }),
  insert(node, parent, anchor) {
    if (node.parent) {
      const index = node.parent.children.indexOf(node);
      if (index >= 0) node.parent.children.splice(index, 1);
    }
    node.parent = parent;
    const index = anchor ? parent.children.indexOf(anchor) : -1;
    if (index < 0) parent.children.push(node);
    else parent.children.splice(index, 0, node);
  },
  remove(node) {
    if (node.parent) {
      const index = node.parent.children.indexOf(node);
      if (index >= 0) node.parent.children.splice(index, 1);
    }
  },
  setText(node, text) {
    node.text = text;
  },
  setElementText(node, text) {
    node.text = text;
    node.children = [];
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] || null,
  patchProp(node, key, _previous, value) {
    node.props[key] = value;
  }
});
const tableKey = Symbol('testTable');
const Panel = defineComponent({
  setup(_props, { slots }) {
    return () => h('panel', null, [slots.header?.(), slots.default?.(), slots.footer?.()]);
  }
});
const Input = defineComponent({
  props: ['modelValue', 'disabled'],
  setup(props, { slots, attrs }) {
    return () => h('input', { ...attrs, ...props }, [slots.default?.(), slots.append?.()]);
  }
});
const Button = defineComponent({
  props: ['disabled', 'loading'],
  setup(props, { slots, attrs }) {
    return () => h('button', { ...attrs, ...props }, slots.default?.());
  }
});
const Table = defineComponent({
  props: ['data'],
  setup(props, { slots }) {
    provide(tableKey, props);
    return () => h('table', null, slots.default?.());
  }
});
const Column = defineComponent({
  props: ['prop'],
  setup(props, { slots }) {
    const table = inject<any>(tableKey);
    return () =>
      h(
        'column',
        null,
        (table?.data || []).map((row: any, index: number) =>
          slots.default ? slots.default({ row, $index: index }) : h('span', null, String(row[props.prop] ?? ''))
        )
      );
  }
});

/** 挂载实际业务组件并保留外部属性更新能力。 */
export function mount(
  name: 'resolver' | 'selector' | 'registration' | 'retire' | 'identity' | 'recall' | 'outputs' | 'manifest' | 'correction' | 'receipt',
  props: Record<string, any>
) {
  const child = ref<any>();
  const liveProps = reactive(props);
  const root = { tag: 'root', props: {}, children: [] as any[], parent: null };
  const component = {
    resolver: CodeResolver,
    selector: SnSelector,
    registration: SerialRegistration,
    retire: SerialRetireDialog,
    identity: IdentityTracePanel,
    recall: BatchRecallPanel,
    outputs: ProductOutputDialog,
    manifest: CompletionManifestPanel,
    correction: ComponentCorrectionDialog,
    receipt: ProductionReceiptDialog
  }[name];
  const app = renderer.createApp({
    setup() {
      return () => h(component, { ...liveProps, ref: child });
    }
  });
  for (const name of [
    'ElDialog',
    'ElForm',
    'ElFormItem',
    'ElDescriptions',
    'ElDescriptionsItem',
    'ElAlert',
    'ElTag',
    'ElOption',
    'ElCard',
    'ElCollapse',
    'ElCollapseItem'
  ])
    app.component(name, Panel);
  app.component('ElSelect', Input);
  app.directive('hasPermi', {});
  app.component('ElInput', Input);
  app.component('ElInputNumber', Input);
  app.component('ElButton', Button);
  app.component('ElTable', Table);
  app.component('ElTableColumn', Column);
  app.directive('loading', {});
  app.mount(root);
  return {
    get vm() {
      return child.value;
    },
    root,
    update(values: Record<string, any>) {
      Object.assign(liveProps, values);
    },
    unmount: () => app.unmount()
  };
}

/** 查找宿主按钮并触发真实 Vue 绑定的业务事件。 */
export async function clickText(root: any, label: string) {
  const text = (node: any): string => (node.text || '') + (node.children || []).map(text).join('');
  const visit = (node: any): any => (node.tag === 'button' && text(node).trim() === label ? node : (node.children || []).map(visit).find(Boolean));
  const node = visit(root);
  if (!node) throw new Error(`未找到按钮 ${label}`);
  if (node.props.disabled) throw new Error(`按钮不可用 ${label}`);
  await node.props.onClick();
  await nextTick();
}
export { nextTick };

/** 读取内存宿主显示文字和提示标题，用于验证真实分页交互。 */
export function renderedText(node: any): string {
  return (node.text || '') + (typeof node.props?.title === 'string' ? node.props.title : '') + (node.children || []).map(renderedText).join('');
}

/** 通过真实v-model事件填写业务输入，不直接改组件内部状态。 */
export async function inputText(root: any, placeholder: string, value: string) {
  const visit = (node: any): any =>
    node.tag === 'input' && node.props.placeholder === placeholder ? node : (node.children || []).map(visit).find(Boolean);
  const node = visit(root);
  if (!node) throw new Error(`未找到输入 ${placeholder}`);
  node.props['onUpdate:modelValue'](value);
  await nextTick();
}

/** 选择控件先更新v-model，再触发真实change事件，保留原异步业务读取。 */
export async function selectText(root: any, placeholder: string, value: string) {
  await inputText(root, placeholder, value);
  const visit = (node: any): any =>
    node.tag === 'input' && node.props.placeholder === placeholder ? node : (node.children || []).map(visit).find(Boolean);
  await visit(root)?.props.onChange?.(value);
  await nextTick();
}

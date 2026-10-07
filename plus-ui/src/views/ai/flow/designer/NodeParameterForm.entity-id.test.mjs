import assert from 'node:assert/strict';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { compileScript, parse } from '@vue/compiler-sfc';
import { createPinia } from 'pinia';
import { createRenderer, h, nextTick, shallowRef } from 'vue';

const here = dirname(fileURLToPath(import.meta.url));
const uiRoot = resolve(here, '../../../../..');
const require = createRequire(import.meta.url);
const artifactDir = resolve(process.env.F01_TEST_OUTPUT_DIR ?? resolve(uiRoot, '..', '.codex', 'f01-node-tests'), `${Date.now()}-${process.pid}`);
const ids = ['2103871286244564993', '9007199254740993'];
let fixtures;
let componentPromise;
let storePromise;

const entityApi = {
  async listModelOptions(capability) {
    assert.equal(capability, 'TEXT');
    return { data: fixtures.map((id) => ({ id, modelName: '测试模型', providerName: '离线' })) };
  },
  async listKnowledge(query) {
    assert.deepEqual(query, { pageNum: 1, pageSize: 100 });
    return { rows: fixtures.map((id) => ({ id, knowledgeName: '测试知识库' })) };
  }
};

async function compileModule(path, name, vue = false) {
  mkdirSync(artifactDir, { recursive: true });
  const output = await build({
    entryPoints: [path],
    bundle: true,
    platform: 'node',
    format: 'cjs',
    external: ['vue', 'pinia', '@element-plus/icons-vue', 'f01:entity-api'],
    alias: { '@': resolve(uiRoot, 'src') },
    write: false,
    logLevel: 'silent',
    plugins: [
      {
        name: 'f01-offline-component',
        setup(builder) {
          builder.onResolve({ filter: /^@\/api\/ai\/(model|knowledge)$/ }, () => ({ path: 'f01:entity-api', external: true }));
          builder.onLoad({ filter: /[\\/]api[\\/]ai[\\/](model|knowledge)\.ts$/ }, () => ({
            contents: 'export { listModelOptions, listKnowledge } from "f01:entity-api";',
            loader: 'js'
          }));
          if (vue) {
            builder.onLoad({ filter: /\.vue$/ }, ({ path: sourcePath }) => {
              const { descriptor, errors } = parse(readFileSync(sourcePath, 'utf8'), { filename: sourcePath });
              assert.deepEqual(errors, []);
              const compiled = compileScript(descriptor, { id: 'f01-entity-id', inlineTemplate: true });
              return { contents: compiled.content, loader: 'ts', resolveDir: dirname(sourcePath) };
            });
          }
        }
      }
    ]
  });
  writeFileSync(resolve(artifactDir, name), output.outputFiles[0].text);
  const mod = { exports: {} };
  new Function('require', 'module', 'exports', output.outputFiles[0].text)(
    (specifier) => (specifier === 'f01:entity-api' ? entityApi : require(specifier)),
    mod,
    mod.exports
  );
  return mod.exports;
}

function host(tag, text = '') {
  return { tag, text, props: {}, children: [], parent: null };
}

function renderer() {
  return createRenderer({
    createElement: (tag) => host(tag),
    createText: (text) => host('#text', text),
    createComment: (text) => host('#comment', text),
    setText: (node, text) => {
      node.text = text;
    },
    setElementText: (node, text) => {
      node.text = text;
      node.children = [];
    },
    patchProp: (node, key, _previous, value) => {
      node.props[key] = value;
    },
    insert: (child, parent, anchor) => {
      if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1);
      child.parent = parent;
      const index = anchor ? parent.children.indexOf(anchor) : -1;
      parent.children.splice(index < 0 ? parent.children.length : index, 0, child);
    },
    remove: (child) => {
      child.parent?.children.splice(child.parent.children.indexOf(child), 1);
      child.parent = null;
    },
    parentNode: (node) => node.parent,
    nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null
  });
}

function find(root, tag) {
  return [...(root.tag === tag ? [root] : []), ...root.children.flatMap((node) => find(node, tag))];
}

async function mountForm(source, modelValue, optionIds = ids, suppliedSchema) {
  fixtures = optionIds;
  componentPromise ??= compileModule(resolve(here, 'NodeParameterForm.vue'), 'node-parameter-form.cjs', true);
  storePromise ??= compileModule(resolve(here, 'useFlowStore.ts'), 'flow-store.cjs');
  const [{ default: Form }, { useFlowStore }] = await Promise.all([componentPromise, storePromise]);
  const store = useFlowStore(createPinia());
  const schema = suppliedSchema ?? {
    name: source === 'aiModel' ? 'modelId' : 'knowledgeId',
    label: source === 'aiModel' ? '模型' : '知识库',
    type: 'number',
    required: true,
    optionsSource: source,
    defaultValue: '0'
  };
  store.nodes = [{ id: 'node-f01', position: { x: 10, y: 20 }, data: { label: '离线节点', type: 'llm', config: { [schema.name]: modelValue } } }];
  const events = [];
  const currentSchema = shallowRef(schema);
  const root = host('root');
  const app = renderer().createApp({
    setup() {
      return () =>
        h(Form, {
          schema: currentSchema.value,
          modelValue: store.nodes[0].data.config[schema.name],
          'onUpdate:modelValue': (value) => {
            events.push(value);
            store.nodes[0].data.config[schema.name] = value;
          }
        });
    }
  });
  app.component('el-form-item', {
    setup(_props, { slots }) {
      return () => h('form-item', slots.default?.());
    }
  });
  for (const [component, tag] of [
    ['el-select', 'entity-select'],
    ['el-input-number', 'number-input']
  ]) {
    app.component(component, {
      props: ['modelValue'],
      emits: ['update:modelValue'],
      setup(props, { emit, slots }) {
        return () => h(tag, { modelValue: props.modelValue, onSelect: (value) => emit('update:modelValue', value) }, slots.default?.());
      }
    });
  }
  app.component('el-option', {
    props: ['label', 'value'],
    setup(props) {
      return () => h('entity-option', { label: props.label, value: props.value });
    }
  });
  for (const component of ['el-switch', 'el-input', 'el-button', 'el-tooltip']) {
    app.component(component, {
      setup(_props, { slots }) {
        return () => h(component, slots.default?.());
      }
    });
  }
  app.mount(root);
  await Promise.resolve();
  await nextTick();
  return {
    store,
    schema,
    events,
    select: () => find(root, 'entity-select')[0],
    options: () => find(root, 'entity-option'),
    number: () => find(root, 'number-input')[0],
    async choose(value) {
      find(root, 'entity-select')[0].props.onSelect(value);
      await nextTick();
    },
    async changeNumber(value) {
      find(root, 'number-input')[0].props.onSelect(value);
      await nextTick();
    },
    async reload(json) {
      store.fromConfigJson(json);
      await nextTick();
    },
    unmount: () => app.unmount()
  };
}

async function withForm(source, value, assertions, optionIds = ids, schema) {
  const ui = await mountForm(source, value, optionIds, schema);
  try {
    await assertions(ui);
  } finally {
    ui.unmount();
  }
}

// 控件只承接事件；参数转换、响应式回显和保存/回读均执行仓库真实代码。
for (const source of ['aiModel', 'aiKnowledge']) {
  for (const id of ids) {
    test(`${source} 实际选中 ${id} 不丢雪花 ID`, async () => {
      await withForm(source, undefined, async (ui) => {
        const option = ui.options().find((item) => item.props.value === id);
        assert.ok(option);
        await ui.choose(option.props.value);
        assert.equal(ui.events.at(-1), id);
        assert.equal(ui.select().props.modelValue, option.props.value);
        assert.equal(ui.store.nodes[0].data.config[ui.schema.name], id);
      });
    });
  }

  test(`${source} 组件参数经现有配置 JSON 保存和回读仍逐字相同`, async () => {
    await withForm(source, undefined, async (ui) => {
      await ui.choose(ids[0]);
      const saved = ui.store.toConfigJson();
      assert.equal(JSON.parse(saved).nodes[0].data.config[ui.schema.name], ids[0]);
      assert.ok(saved.includes(`"${ui.schema.name}":"${ids[0]}"`));
      await ui.choose(ids[1]);
      await ui.reload(saved);
      assert.equal(ui.store.nodes[0].data.config[ui.schema.name], ids[0]);
      assert.equal(ui.select().props.modelValue, ids[0]);
      assert.equal(ui.store.toConfigJson(), saved);
    });
  });

  test(`${source} 候选拒绝非法及已失真数字，不伪造字符串 ID`, async () => {
    const invalid = [
      Number(ids[0]),
      Number.MAX_SAFE_INTEGER + 1,
      NaN,
      Infinity,
      -1,
      1.5,
      null,
      undefined,
      '',
      'abc',
      '1e3',
      '01',
      '9223372036854775808'
    ];
    await withForm(
      source,
      undefined,
      (ui) => {
        assert.deepEqual(
          ui.options().map((option) => option.props.value),
          [ids[0], '42', '0']
        );
      },
      [ids[0], 42, 0, ...invalid]
    );
  });

  test(`${source} 已失真 modelValue 不显示为可选实体且不偷偷修写原配置`, async () => {
    const damaged = Number(ids[0]);
    await withForm(source, damaged, (ui) => {
      assert.equal(ui.select().props.modelValue, '');
      assert.equal(ui.store.nodes[0].data.config[ui.schema.name], damaged);
      assert.deepEqual(ui.events, []);
    });
  });

  test(`${source} 历史安全整数回显可匹配字符串候选，选择后仍无损`, async () => {
    await withForm(
      source,
      42,
      async (ui) => {
        assert.equal(ui.select().props.modelValue, '42');
        await ui.choose('42');
        assert.equal(ui.events.at(-1), '42');
        assert.equal(JSON.parse(ui.store.toConfigJson()).nodes[0].data.config[ui.schema.name], '42');
      },
      [42]
    );
  });

  test(`${source} 非法更新不能变成伪修复 ID`, async () => {
    await withForm(source, ids[0], async (ui) => {
      for (const value of [Number(ids[0]), 'abc', '1.5', '1e3', {}, true]) {
        await ui.choose(value);
        assert.equal(ui.events.at(-1), undefined);
        assert.equal(ui.select().props.modelValue, '');
      }
    });
  });

  test(`${source} 清空仍发出 undefined 且保存不写 null 或默认 ID`, async () => {
    await withForm(source, ids[0], async (ui) => {
      for (const value of ['', null, undefined]) {
        await ui.choose(value);
        assert.equal(ui.events.at(-1), undefined);
        assert.equal(ui.select().props.modelValue, '');
        assert.ok(!Object.hasOwn(JSON.parse(ui.store.toConfigJson()).nodes[0].data.config, ui.schema.name));
      }
    });
  });

  test(`${source} 默认零保持零，不自动选择其他模型或知识库`, async () => {
    await withForm(source, '0', async (ui) => {
      assert.equal(ui.select().props.modelValue, '0');
      assert.deepEqual(ui.events, []);
      await ui.choose('0');
      assert.equal(ui.events.at(-1), '0');
      assert.equal(JSON.parse(ui.store.toConfigJson()).nodes[0].data.config[ui.schema.name], '0');
    });
  });
}

test('普通温度与超时参数仍保存为 number', async () => {
  for (const [name, value] of [
    ['temperature', 0.7],
    ['timeoutSeconds', 300]
  ]) {
    await withForm(
      undefined,
      0,
      async (ui) => {
        assert.equal(ui.select(), undefined);
        await ui.changeNumber(value);
        assert.equal(ui.events.at(-1), value);
        const savedValue = JSON.parse(ui.store.toConfigJson()).nodes[0].data.config[name];
        assert.equal(typeof savedValue, 'number');
        assert.equal(savedValue, value);
      },
      [],
      { name, label: name, type: 'number' }
    );
  }
});

test('实体 ID helper 接受无损文本、默认零及安全整数', async () => {
  const path = resolve(here, 'flowEntityId.ts');
  assert.ok(existsSync(path), '需提供专属实体 ID helper');
  const { normalizeFlowEntityId: normalize } = await compileModule(path, 'flow-entity-id.cjs');
  for (const id of [...ids, '9223372036854775807', '0']) assert.equal(normalize(id), id);
  for (const value of [0, 42, Number.MAX_SAFE_INTEGER]) assert.equal(normalize(value), String(value));
});

test('实体 ID helper 拒绝非法或已失真来源', async () => {
  const path = resolve(here, 'flowEntityId.ts');
  assert.ok(existsSync(path), '需提供专属实体 ID helper');
  const { normalizeFlowEntityId: normalize } = await compileModule(path, 'flow-entity-id-invalid.cjs');
  for (const value of [undefined, null, '', ' ', '01', '1.0', '1e3', '-1', '9223372036854775808', Number(ids[0]), NaN, Infinity, -1, 1.5, true, {}]) {
    assert.equal(normalize(value), undefined);
  }
});

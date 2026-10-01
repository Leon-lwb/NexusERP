import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { build } from 'esbuild';
import { parse, compileScript } from '@vue/compiler-sfc';
import { createSSRApp, h } from 'vue';
import { renderToString } from '@vue/server-renderer';

/** 编译实际字典组件，补齐测试环境中由生产自动导入插件提供的响应式函数。 */
async function loadDictTag() {
  const source = readFileSync(new URL('../src/components/DictTag/index.vue', import.meta.url), 'utf8');
  const { descriptor } = parse(source);
  const compiled = compileScript(descriptor, { id: 'dict-tag-empty-value-contract', inlineTemplate: true });
  const output = new URL('../node_modules/.tmp/dict-tag-empty-value.mjs', import.meta.url);
  await build({
    stdin: {
      contents: `import { computed } from 'vue';\n${compiled.content}`,
      loader: 'ts',
      resolveDir: fileURLToPath(new URL('..', import.meta.url))
    },
    outfile: fileURLToPath(output),
    bundle: true,
    packages: 'external',
    format: 'esm',
    platform: 'node',
    logLevel: 'silent'
  });
  return (await import(output.href + `?t=${Date.now()}`)).default;
}
const component = await loadDictTag();
const options = [{ value: '0', label: '启用', elTagType: 'default', elTagClass: '' }];

/** 仅保存离线渲染告警，空值不应伪装成字典中的正常业务状态。 */
async function renderValue(props) {
  const warnings = [];
  const app = createSSRApp({ render: () => h(component, { options, ...props }) });
  app.config.warnHandler = (message) => warnings.push(message);
  app.component('ElTag', {
    setup:
      (_, { slots }) =>
      () =>
        h('span', slots.default?.())
  });
  return { html: await renderToString(app), warnings };
}

/** 提取实际可见文本，忽略服务端渲染锚点和标签，但不吞掉零值或异常占位文字。 */
function visibleText(html) {
  return html
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<[^>]*>/g, '')
    .trim();
}

for (const [name, props] of [
  ['省略', {}],
  ['未定义', { value: undefined }],
  ['空值', { value: null }],
  ['空文本', { value: '' }]
]) {
  test(`字典标签${name}状态保持空白且不触发类型告警`, async () => {
    const result = await renderValue(props);
    assert.deepEqual(result.warnings, []);
    assert.equal(visibleText(result.html), '');
  });
}

test('数值零与文本零仍匹配同一个真实字典值', async () => {
  for (const value of [0, '0']) {
    const result = await renderValue({ value });
    assert.deepEqual(result.warnings, []);
    assert.match(result.html, /启用/);
  }
});

test('雪花编号文本未匹配时保持完整且不转为浮点数', async () => {
  const value = '2105519018947354625';
  const result = await renderValue({ value });
  assert.deepEqual(result.warnings, []);
  assert.match(result.html, new RegExp(value));
});

test('数组字典值保持原有展示，空值放行不会放宽对象类型', async () => {
  const snowflake = '2105519018947354625';
  for (const value of [['0'], [0], [0, snowflake]]) {
    const array = await renderValue({ value });
    assert.deepEqual(array.warnings, []);
    assert.match(array.html, /启用/);
    if (value.includes(snowflake)) assert.match(array.html, new RegExp(snowflake));
  }
  const invalid = await renderValue({ value: { status: '0' } });
  assert.ok(invalid.warnings.some((message) => message.includes('Invalid prop')));
});

import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc';
import { CircleCheck, Loading } from '@element-plus/icons-vue';
import { renderToString } from '@vue/server-renderer';
import { createSSRApp, h } from 'vue';
import { loadProductionModule } from './component-harness.mjs';

const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const require = createRequire(import.meta.url);
const assistantPath = 'src/views/ai/assistant/index.vue';
const baselineRef = process.env.AI_TOOL_DURATION_BASELINE_REF;
let templatePromise;

function pageSource() {
  if (!baselineRef) return readFileSync(resolve(uiRoot, assistantPath), 'utf8');
  assert.match(baselineRef, /^[0-9a-f]{7,40}$/, '基线必须是明确提交哈希');
  // 只读原始页面复验 RED，不回退工作树，也不执行页面的发送或 SSE 逻辑。
  return execFileSync('git', ['show', `${baselineRef}:plus-ui/${assistantPath}`], { cwd: uiRoot, encoding: 'utf8' });
}

async function toolTemplate() {
  const { descriptor, errors } = parse(pageSource(), { filename: assistantPath });
  assert.deepEqual(errors, []);
  const script = compileScript(descriptor, { id: 'f06-tool-duration' });
  const matches = [];
  function visit(node) {
    if (node.type === 1 && node.props.some((prop) => prop.type === 6 && prop.name === 'class' && prop.value?.content === 'tool-calls')) {
      matches.push(node);
    }
    for (const child of node.children ?? []) visit(child);
  }
  visit(descriptor.template.ast);
  assert.equal(matches.length, 1, '必须定位唯一的真实工具调用模板，不能复制一份条件代替生产模板');
  const compiled = compileTemplate({
    source: matches[0].loc.source,
    filename: assistantPath,
    id: 'f06-tool-duration',
    compilerOptions: { bindingMetadata: script.bindings }
  });
  assert.deepEqual(compiled.errors, []);
  const output = await build({
    absWorkingDir: uiRoot,
    stdin: { contents: compiled.code, resolveDir: uiRoot, loader: 'js' },
    bundle: true,
    write: false,
    format: 'cjs',
    platform: 'node',
    packages: 'external',
    logLevel: 'silent'
  });
  const module = { exports: {} };
  new Function('require', 'module', 'exports', output.outputFiles[0].text)(require, module, module.exports);
  const setup = { CircleCheck, Loading };
  if (script.imports.formatToolDuration) {
    assert.equal(script.imports.formatToolDuration.source, './toolDuration', '模板必须使用生产纯 helper 的真实导入');
    setup.formatToolDuration = (await loadProductionModule('src/views/ai/assistant/toolDuration.ts')).formatToolDuration;
  }
  return { render: module.exports.render, setup };
}

async function renderDuration(duration, status = 'completed') {
  templatePromise ??= toolTemplate();
  const template = await templatePromise;
  const app = createSSRApp(
    { props: ['msg'], setup: () => template.setup, render: template.render },
    { msg: { role: 'assistant', toolCalls: [{ toolName: '离线展示工具', status, duration }] } }
  );
  app.component('el-icon', {
    setup:
      (_props, { slots }) =>
      () =>
        h('span', slots.default?.())
  });
  return renderToString(app);
}

function durationText(html) {
  return html.match(/<span class="tool-duration">([^<]*)<\/span>/)?.[1];
}

for (const [label, duration, expected] of [
  ['未知哨兵 -1', -1, '耗时未知'],
  ['真实 0', 0, '0ms'],
  ['真实正值', 12.5, '12.5ms'],
  ['缺失值', undefined, '耗时未知'],
  ['非有限 NaN', NaN, '耗时未知'],
  ['非法数字字符串', '0', '耗时未知']
]) {
  test(`assistant 真实模板：${label} 正确展示`, async () => {
    const html = await renderDuration(duration);
    assert.equal(durationText(html), expected, html);
    if (expected === '耗时未知') assert.doesNotMatch(html, /-1ms|>0ms</, '未知耗时不能成为 -1ms 或伪造为 0ms');
  });
}

test('assistant 真实模板：运行中不提前显示耗时', async () => {
  assert.equal(durationText(await renderDuration(0, 'running')), undefined);
});

let formatterPromise;
async function formatter() {
  formatterPromise ??= loadProductionModule('src/views/ai/assistant/toolDuration.ts');
  const { formatToolDuration } = await formatterPromise;
  assert.equal(typeof formatToolDuration, 'function', '必须使用生产纯 helper，不能在测试中重写判定逻辑');
  return formatToolDuration;
}

for (const [label, duration, expected] of [
  ['零', 0, '0ms'],
  ['负零', -0, '0ms'],
  ['正整数', 1, '1ms'],
  ['正小数', 12.5, '12.5ms'],
  ['不改单位或取整', 123456.75, '123456.75ms'],
  ['最大有限数', Number.MAX_VALUE, '1.7976931348623157e+308ms']
]) {
  test(`耗时纯 helper：${label} 按原数值展示`, async () => {
    assert.equal((await formatter())(duration), expected);
  });
}

for (const [label, duration] of [
  ['缺失', undefined],
  ['空值', null],
  ['未知哨兵', -1],
  ['其他负值', -0.1],
  ['NaN', NaN],
  ['正无穷', Infinity],
  ['负无穷', -Infinity],
  ['字符串零', '0'],
  ['字符串负值', '-1'],
  ['字符串正值', '12'],
  ['空字符串', ''],
  ['布尔假', false],
  ['布尔真', true],
  ['普通对象', {}],
  ['数组', []],
  ['大整数类型', 1n],
  ['装箱数字', new Number(5)],
  ['符号类型', Symbol('离线边界')]
]) {
  test(`耗时纯 helper：${label} 不强制转数字、不冒充零`, async () => {
    assert.equal((await formatter())(duration), '耗时未知');
  });
}

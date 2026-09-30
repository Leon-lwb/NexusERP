import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { isAbsolute } from 'node:path';
import { build } from 'esbuild';
import { rollup } from 'rollup';
import rawSetupExtend from 'unplugin-vue-setup-extend-plus/vite';

/** 仅在内存中编译此小型工厂，不加载应用或 Vite 配置，也不写出构建产物。 */
const compiled = await build({
  entryPoints: [fileURLToPath(new URL('../vite/plugins/setup-extend.ts', import.meta.url))],
  bundle: true,
  platform: 'node',
  format: 'esm',
  write: false,
  plugins: [
    {
      name: 'test-resolve-installed-packages',
      setup(builder) {
        builder.onResolve({ filter: /^[^./]/ }, ({ path: specifier, kind }) => {
          if (kind === 'entry-point' || isAbsolute(specifier)) return;
          return { path: import.meta.resolve(specifier), external: true };
        });
      }
    }
  ]
});
const { default: createSetupExtend } = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputFiles[0].text).toString('base64')}`);
const fixtureId = fileURLToPath(new URL('build-memory-fixture.vue', import.meta.url)).replaceAll('\\', '/');
const namedSfc = '<template><div>{{ n }}</div></template>\n<script setup lang="ts" name="MemoryFixture">\nconst n = 1;\n</script>';
const scriptId = `${fixtureId}?vue&type=script&setup=true&lang.ts`;
const scriptSource = 'globalThis.memoryFixture = (globalThis.memoryFixture || 0) + 1;\n'.repeat(200);

function hookHandler(hook) {
  return typeof hook === 'function' ? hook : hook?.handler;
}

async function configuredPlugin(command, sourcemap) {
  const plugins = [createSetupExtend()].flat();
  assert.equal(plugins.length, 1, 'the installed setup-extend factory should expose one transform plugin');
  const plugin = plugins[0];
  const resolved = hookHandler(plugin.configResolved);
  if (resolved) await resolved.call({}, { command, build: { sourcemap } });
  return plugin;
}

async function transform(plugin, code, id) {
  return hookHandler(plugin.transform).call({}, code, id);
}

for (const sourcemap of [false, undefined]) {
  test(`build sourcemap=${sourcemap}: component-name transformation returns no unused mapping`, async () => {
    const plugin = await configuredPlugin('build', sourcemap);
    const result = await transform(plugin, namedSfc, fixtureId);
    assert.match(result.code, /name:\s*"MemoryFixture"/);
    assert.equal(result.map === null, true, 'a sourcemap-disabled build must not retain a per-character plugin map');
  });
}

test('build without sourcemaps: already-compiled script code is unchanged and has no retained map', async () => {
  const plugin = await configuredPlugin('build', false);
  const result = await transform(plugin, scriptSource, scriptId);
  assert.equal(result.code, scriptSource);
  assert.equal(result.map === null, true, 'the identity transform must not allocate a decoded map in Rollup');
});

async function rollupFixture(plugin) {
  let sawRollupContext = false;
  const originalTransform = hookHandler(plugin.transform);
  const checkedTransform = function (...args) {
    /** 插件适配器必须接收到真实的 Rollup 转换上下文，而不是手工构造的桩对象。 */
    assert.equal(typeof this.getModuleInfo, 'function');
    assert.ok(this.getModuleInfo(scriptId), 'the transform hook sees its module through Rollup this');
    sawRollupContext = true;
    return originalTransform.apply(this, args);
  };
  const checkedPlugin = {
    ...plugin,
    transform: typeof plugin.transform === 'function' ? checkedTransform : { ...plugin.transform, handler: checkedTransform }
  };
  const bundle = await rollup({
    input: scriptId,
    plugins: [
      {
        name: 'test-single-memory-fixture',
        resolveId(id) {
          if (id === scriptId) return id;
        },
        load(id) {
          if (id === scriptId) return scriptSource;
        }
      },
      checkedPlugin
    ]
  });
  try {
    const module = bundle.cache.modules.find(({ id }) => id === scriptId);
    assert.ok(module, 'the real Rollup fixture must not be skipped');
    assert.ok(sawRollupContext, 'the plugin transform must run with the real Rollup this context');
    const { output } = await bundle.generate({ format: 'es', sourcemap: false });
    assert.equal(output.length, 1);
    assert.equal(output[0].type, 'chunk');
    assert.equal(output[0].map, null);
    return { code: output[0].code, mapChainLength: module.sourcemapChain.length };
  } finally {
    await bundle.close();
  }
}

test('unwrapped Rollup baseline retains maps; patched plugin drops them without changing generated JS', async () => {
  const baseline = await rollupFixture([rawSetupExtend({})].flat()[0]);
  const optimized = await rollupFixture(await configuredPlugin('build', false));
  assert.equal(baseline.mapChainLength, 1, 'anti-fraud: the installed upstream transform retains exactly one decoded map');
  assert.equal(optimized.mapChainLength, 0, 'Rollup must not retain the patched plugin mapping');
  assert.match(baseline.code, /globalThis\.memoryFixture/);
  assert.equal(optimized.code, baseline.code, 'the entire generated JS must match the unwrapped upstream output');
});

test('plugin shape, metadata and other hooks survive wrapping', () => {
  const baseline = rawSetupExtend({});
  const optimized = createSetupExtend();
  assert.equal(Array.isArray(optimized), Array.isArray(baseline), 'single/plugin-array shape must be preserved');
  const originalPlugins = [baseline].flat();
  const wrappedPlugins = [optimized].flat();
  assert.equal(wrappedPlugins.length, originalPlugins.length);
  for (const [index, original] of originalPlugins.entries()) {
    const wrapped = wrappedPlugins[index];
    assert.equal(wrapped.name, original.name, 'plugin identity is unchanged');
    assert.equal(wrapped.enforce, original.enforce, 'hook ordering metadata is unchanged');
    assert.equal(wrapped.apply, original.apply, 'plugin applicability metadata is unchanged');
    for (const key of Object.keys(original)) {
      assert.ok(Object.hasOwn(wrapped, key), `upstream hook or metadata ${key} must survive wrapping`);
    }
    assert.equal(typeof hookHandler(wrapped.transform), typeof hookHandler(original.transform));
    if (typeof original.transform === 'object') {
      assert.equal(wrapped.transform.order, original.transform.order, 'transform hook priority is unchanged');
    }
    if (original.transformInclude) {
      for (const id of [fixtureId, scriptId, '/not-a-component.js']) {
        assert.equal(wrapped.transformInclude(id), original.transformInclude(id), `transform filter should agree on ${id}`);
      }
    }
  }
});
for (const [command, sourcemap] of [
  ['serve', false],
  ['build', true],
  ['build', 'hidden'],
  ['build', 'inline']
]) {
  test(`${command} sourcemap=${sourcemap}: explicit/development source mappings are preserved`, async () => {
    const plugin = await configuredPlugin(command, sourcemap);
    const result = await transform(plugin, namedSfc, fixtureId);
    assert.match(result.code, /name:\s*"MemoryFixture"/);
    assert.ok(result.map?.mappings?.length > 0, 'do not remove maps from debug/source-mapped builds');
    const script = await transform(plugin, scriptSource, scriptId);
    assert.equal(script.code, scriptSource);
    assert.ok(script.map?.mappings?.length > 0);
  });
}

test('sourcemap suppression changes only the map, not component code, across build policies', async () => {
  const withoutMap = await transform(await configuredPlugin('build', false), namedSfc, fixtureId);
  const withMap = await transform(await configuredPlugin('build', true), namedSfc, fixtureId);
  assert.equal(withoutMap.code, withMap.code);
});

test('array plugins retain object-hook metadata and forward the original hook this context', async () => {
  /** 使用内存替身检验数组与对象形式钩子的契约；当前安装的插件未暴露这些形式。 */
  const simulatedUpstream = `export default () => [{
    name: 'setup-extend-test', enforce: 'pre', apply: 'build',
    transformInclude(id) { return id.endsWith('.vue'); },
    configResolved: { order: 'pre', handler(config) { this.configHookCommand = config.command; } },
    transform: { order: 'post', handler(code) {
      this.transformHookCode = code;
      return { code, map: { version: 3, sources: ['fixture.js'], names: [], mappings: 'AAAA' } };
    } }
  }];`;
  const stubbed = await build({
    entryPoints: [fileURLToPath(new URL('../vite/plugins/setup-extend.ts', import.meta.url))],
    bundle: true,
    platform: 'node',
    format: 'esm',
    write: false,
    plugins: [
      {
        name: 'test-synthetic-plugin-factory',
        setup(builder) {
          builder.onResolve({ filter: /^unplugin-vue-setup-extend-plus\/vite$/ }, () => ({
            path: 'synthetic-setup-extend',
            namespace: 'test-factory'
          }));
          builder.onLoad({ filter: /.*/, namespace: 'test-factory' }, () => ({ contents: simulatedUpstream, loader: 'js' }));
        }
      }
    ]
  });
  const { default: createStubbedSetupExtend } = await import(
    `data:text/javascript;base64,${Buffer.from(stubbed.outputFiles[0].text).toString('base64')}`
  );
  const plugins = createStubbedSetupExtend();
  assert.equal(Array.isArray(plugins), true);
  assert.equal(plugins.length, 1);
  const [plugin] = plugins;
  assert.equal(plugin.name, 'setup-extend-test');
  assert.equal(plugin.enforce, 'pre');
  assert.equal(plugin.apply, 'build');
  assert.equal(plugin.configResolved.order, 'pre');
  assert.equal(plugin.transform.order, 'post');
  assert.equal(plugin.transformInclude(fixtureId), true);
  assert.equal(plugin.transformInclude('/not-a-component.js'), false);
  const receiver = {};
  await hookHandler(plugin.configResolved).call(receiver, { command: 'build', build: { sourcemap: false } });
  const result = await hookHandler(plugin.transform).call(receiver, scriptSource, scriptId);
  assert.equal(receiver.configHookCommand, 'build', 'configResolved forwards the original hook this');
  assert.equal(receiver.transformHookCode, scriptSource, 'transform forwards the original hook this');
  assert.equal(result.code, scriptSource);
  assert.equal(result.map, null);
});

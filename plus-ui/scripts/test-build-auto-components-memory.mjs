import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import path, { isAbsolute } from 'node:path';
import { test } from 'node:test';
import { build } from 'esbuild';
import { rollup } from 'rollup';
import rawAutoImport from 'unplugin-auto-import/vite';
import rawComponents from 'unplugin-vue-components/vite';

const pluginDir = fileURLToPath(new URL('../vite/plugins/', import.meta.url));
const sourceId = fileURLToPath(new URL('../src/memory-auto-components.ts', import.meta.url)).replaceAll('\\', '/');
const templateId = `${sourceId.slice(0, -3)}.vue?vue&type=template&id=memory`;
const autoFixture = { id: sourceId, code: 'export const memoryProbe = ref(0);', transformed: /from ['"]vue['"]/ };
const componentFixture = {
  id: templateId,
  code: 'import { resolveComponent as _resolveComponent } from "vue"; export const memoryProbe = _resolveComponent("ElButton");',
  transformed: /from ['"]element-plus['"]/
};
const combinedFixture = {
  id: templateId,
  code: 'import { resolveComponent as _resolveComponent } from "vue"; export const count = ref(0), widget = _resolveComponent("ElButton");',
  transformed: /from ['"]element-plus['"]/
};

function hookHandler(hook) {
  return typeof hook === 'function' ? hook : hook?.handler;
}

const rawFactories = {
  auto: () => rawAutoImport({ imports: ['vue'], dirs: [], dts: false, eslintrc: { enabled: false }, viteOptimizeDeps: false }),
  components: () =>
    rawComponents({
      dirs: [],
      dts: false,
      resolvers: [
        function resolve(name) {
          if (name === 'ElButton') return { name: 'ElButton', from: 'element-plus' };
        }
      ]
    })
};

async function compileFactory(entryName, replacement) {
  const source = fileURLToPath(new URL(`../vite/plugins/${entryName}.ts`, import.meta.url));
  const contents =
    replacement ??
    (entryName === 'auto-import'
      ? `import real from ${JSON.stringify(import.meta.resolve('unplugin-auto-import/vite'))};
       export default (options) => real({ ...options, imports: ['vue'], dirs: [], dts: false, eslintrc: { enabled: false }, viteOptimizeDeps: false });`
      : `import real from ${JSON.stringify(import.meta.resolve('unplugin-vue-components/vite'))};
       export default (options) => real({ ...options, dirs: [], dts: false,
         resolvers: [function resolve(name) { if (name === 'ElButton') return { name: 'ElButton', from: 'element-plus' }; }] });`);
  const compiled = await build({
    entryPoints: [source],
    bundle: true,
    platform: 'node',
    format: 'esm',
    write: false,
    define: { __dirname: JSON.stringify(pluginDir) },
    plugins: [
      {
        name: 'safe-installed-plugin-factory',
        setup(builder) {
          builder.onResolve({ filter: /^unplugin-(auto-import|vue-components)\/vite$/ }, ({ path }) => ({ path, namespace: 'safe-factory' }));
          builder.onLoad({ filter: /.*/, namespace: 'safe-factory' }, () => ({ contents, loader: 'js' }));
          builder.onResolve({ filter: /^[^./]/ }, ({ path, kind }) => {
            if (kind === 'entry-point' || isAbsolute(path)) return;
            return { path: import.meta.resolve(path), external: true };
          });
        }
      }
    ]
  });
  const { default: factory } = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputFiles[0].text).toString('base64')}`);
  return factory;
}

const createAuto = await compileFactory('auto-import');
const createComponents = await compileFactory('components');

async function configured(plugin, command = 'build', sourcemap = false) {
  const config = { root: process.cwd(), command, build: { sourcemap, watch: undefined } };
  const configure = hookHandler(plugin.configResolved);
  if (configure) await configure.call({}, config);
  return plugin;
}

async function rollupFixture(fixture, plugins) {
  let sawContext = false;
  const checked = plugins.map((plugin) => {
    const original = hookHandler(plugin.transform);
    if (!original) return plugin;
    const handler = function (...args) {
      assert.equal(typeof this.getModuleInfo, 'function', 'the original transform must see the Rollup context');
      assert.ok(this.getModuleInfo(fixture.id));
      sawContext = true;
      return original.apply(this, args);
    };
    return { ...plugin, transform: typeof plugin.transform === 'function' ? handler : { ...plugin.transform, handler } };
  });
  const bundle = await rollup({
    input: fixture.id,
    external: ['vue', 'element-plus'],
    plugins: [
      {
        name: 'in-memory-source-and-asset',
        resolveId(id) {
          if (id === fixture.id) return id;
        },
        load(id) {
          if (id === fixture.id) return fixture.code;
        },
        buildStart() {
          this.emitFile({ type: 'asset', fileName: 'probe.txt', source: 'same-asset' });
        }
      },
      ...checked
    ]
  });
  try {
    const module = bundle.cache.modules.find((item) => item.id === fixture.id);
    assert.ok(module, 'the real module graph must not be skipped');
    assert.ok(sawContext, 'at least one real plugin must transform the virtual module');
    const { output } = await bundle.generate({ format: 'es', sourcemap: false });
    const chunk = output.find((item) => item.type === 'chunk');
    assert.ok(chunk);
    assert.match(chunk.code, fixture.transformed, 'the real plugin must change the generated JS');
    assert.equal(chunk.map, null);
    return {
      chainLength: module.sourcemapChain.length,
      code: chunk.code,
      assets: output.filter((item) => item.type === 'asset').map(({ fileName, source }) => ({ fileName, source }))
    };
  } finally {
    await bundle.close();
  }
}

for (const [name, fixture, create, raw] of [
  ['auto-import', autoFixture, createAuto, rawFactories.auto],
  ['components', componentFixture, createComponents, rawFactories.components]
]) {
  test(`${name}: real Rollup map chain 1 -> 0 with identical full JS and emitted asset`, async () => {
    const baseline = await rollupFixture(fixture, [await configured(raw())]);
    const optimized = await rollupFixture(fixture, [await configured(create(path))]);
    assert.equal(baseline.chainLength, 1, 'the actual upstream must retain exactly one map');
    assert.equal(optimized.chainLength, 0, `${name} must not retain a map in a sourcemap-disabled build`);
    assert.equal(optimized.code, baseline.code);
    assert.deepEqual(optimized.assets, baseline.assets);
    assert.deepEqual(optimized.assets, [{ fileName: 'probe.txt', source: 'same-asset' }]);
  });
}

test('both real plugins on one module: Rollup map chain 2 -> 0 without changing complete JS or asset', async () => {
  const baseline = await rollupFixture(combinedFixture, [await configured(rawFactories.auto()), await configured(rawFactories.components())]);
  const optimized = await rollupFixture(combinedFixture, [await configured(createAuto(path)), await configured(createComponents(path))]);
  assert.equal(baseline.chainLength, 2);
  assert.equal(optimized.chainLength, 0);
  assert.equal(optimized.code, baseline.code);
  assert.deepEqual(optimized.assets, baseline.assets);
});

for (const [name, fixture, create, raw] of [
  ['auto-import', autoFixture, createAuto, rawFactories.auto],
  ['components', componentFixture, createComponents, rawFactories.components]
]) {
  for (const [command, sourcemap, shouldMap] of [
    ['build', false, false],
    ['build', undefined, false],
    ['serve', false, true],
    ['build', true, true],
    ['build', 'hidden', true],
    ['build', 'inline', true]
  ]) {
    test(`${name}: ${command} sourcemap=${sourcemap} keeps mapping policy and real conversion`, async () => {
      const plugin = await configured(create(path), command, sourcemap);
      const result = await hookHandler(plugin.transform).call(
        {
          error(e) {
            throw e;
          }
        },
        fixture.code,
        fixture.id
      );
      assert.match(result.code, fixture.transformed);
      assert.equal(result.map === null, !shouldMap);
      if (shouldMap) assert.ok(result.map?.mappings?.length > 0);
    });
  }
  test(`${name}: metadata, other hooks, and transform hook options survive wrapping`, () => {
    const original = raw();
    const wrapped = create(path);
    assert.equal(Array.isArray(wrapped), Array.isArray(original));
    assert.equal(wrapped.name, original.name);
    assert.equal(wrapped.enforce, original.enforce);
    assert.equal(wrapped.apply, original.apply);
    for (const key of Object.keys(original)) assert.ok(Object.hasOwn(wrapped, key), `${name}: ${key}`);
    assert.equal(typeof wrapped.transform, typeof original.transform);
    if (typeof original.transform === 'object') {
      assert.deepEqual(wrapped.transform.filter, original.transform.filter);
      assert.equal(wrapped.transform.order, original.transform.order);
    }
  });
}

test('array shape, object-hook order/filter, receiver, metadata, and opaque return values survive', async () => {
  const synthetic = `export default () => [{
    name: 'synthetic-components', enforce: 'post', apply: 'build', api: { marker: true },
    configResolved: { order: 'pre', handler(config) { this.configSeen = config.command; } },
    transform: { order: 'post', filter: { id: /probe/ }, handler(code) {
      this.transformSeen = code;
      if (code === 'undefined') return undefined;
      if (code === 'map') return new Map([['keep', true]]);
      return { code, map: { version: 3, sources: ['probe.ts'], names: [], mappings: 'AAAA' }, meta: { marker: true } };
    } }
  }, { name: 'synthetic-pass-through', enforce: 'pre' }];`;
  const create = await compileFactory('components', synthetic);
  const plugins = create(path);
  assert.equal(Array.isArray(plugins), true);
  assert.equal(plugins.length, 2);
  const [first, untouched] = plugins;
  assert.equal(first.name, 'synthetic-components');
  assert.equal(first.enforce, 'post');
  assert.equal(first.apply, 'build');
  assert.deepEqual(first.api, { marker: true });
  assert.equal(first.configResolved.order, 'pre');
  assert.equal(first.transform.order, 'post');
  assert.deepEqual(first.transform.filter, { id: /probe/ });
  assert.equal(untouched.name, 'synthetic-pass-through');
  const receiver = {};
  await hookHandler(first.configResolved).call(receiver, { command: 'build', build: { sourcemap: false } });
  const mapped = await hookHandler(first.transform).call(receiver, 'ordinary', 'probe.ts');
  assert.equal(receiver.configSeen, 'build');
  assert.equal(receiver.transformSeen, 'ordinary');
  assert.deepEqual(mapped, { code: 'ordinary', map: null, meta: { marker: true } });
  assert.equal(await hookHandler(first.transform).call(receiver, 'undefined', 'probe.ts'), undefined);
  const opaque = await hookHandler(first.transform).call(receiver, 'map', 'probe.ts');
  assert.ok(opaque instanceof Map);
  assert.equal(opaque.get('keep'), true);
});

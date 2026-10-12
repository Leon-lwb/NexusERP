/**
 * Run: node --test --test-concurrency=1 scripts/test-cv-defect.mjs
 * Executes real API serialization, geometry, and compiled Vue setup/lifecycle.
 * Only the HTTP boundary and browser image decoding are isolated; no services or datasets are loaded.
 */
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { build } from 'esbuild';
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc';
import * as Vue from 'vue';

const require = createRequire(import.meta.url);
const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const bundles = new Map();
const detection = { className: 'scratches', confidence: 0.92, bbox: [120, 40, 360, 180] };
const inference = (scenario = 'defect_metal', detections = [detection]) => ({
  success: true,
  scenario,
  detections,
  count: detections.length,
  processTimeMs: 23.75
});
const imageFile = (name = 'metal-test-014.png') => new File(['local-image-bytes'], name, { type: 'image/png', lastModified: 1234 });

async function load(relative, overrides = {}) {
  const filename = resolve(uiRoot, relative);
  assert.ok(existsSync(filename), `工业缺陷检测功能文件尚未实现：${relative}`);
  if (!bundles.has(relative)) {
    bundles.set(
      relative,
      build({
        absWorkingDir: uiRoot,
        entryPoints: [filename],
        bundle: true,
        write: false,
        platform: 'node',
        format: 'cjs',
        packages: 'external',
        define: { 'import.meta.env': '{}' },
        logLevel: 'silent',
        plugins: [
          {
            name: 'cv-defect-runtime',
            setup(builder) {
              builder.onResolve({ filter: /^@\// }, ({ path }) =>
                relative === 'src/utils/request.ts' || path === '@/utils/request' || (relative.endsWith('.vue') && path === '@/api/cv/defect')
                  ? { path, external: true }
                  : { path: resolve(uiRoot, 'src', path.slice(2) + '.ts') }
              );
              builder.onLoad({ filter: /\.vue$/ }, ({ path }) => {
                const { descriptor, errors } = parse(readFileSync(path, 'utf8'), { filename: path });
                assert.deepEqual(errors, [], '实际 Vue 页面必须解析成功');
                const script = compileScript(descriptor, { id: 'cv-defect-test' });
                const template = compileTemplate({
                  source: descriptor.template.content,
                  filename: path,
                  id: 'cv-defect-test',
                  compilerOptions: { bindingMetadata: script.bindings }
                });
                assert.deepEqual(template.errors, [], '实际页面模板必须编译成功');
                return { contents: script.content, loader: 'ts', resolveDir: dirname(path) };
              });
            }
          }
        ]
      }).then((built) => built.outputFiles[0].text)
    );
  }
  const module = { exports: {} };
  new Function('require', 'module', 'exports', await bundles.get(relative))(
    (name) => (Object.hasOwn(overrides, name) ? overrides[name] : require(name)),
    module,
    module.exports
  );
  return module.exports;
}

const helpers = () => load('src/views/cv/defect/defectViewModel.ts');
function node(tag, text = '') {
  return { tag, text, parent: null, children: [] };
}
const renderer = Vue.createRenderer({
  createElement: (tag) => node(tag),
  createText: (text) => node('#text', text),
  createComment: (text) => node('#comment', text),
  setText: (element, text) => (element.text = text),
  setElementText: (element, text) => (element.text = text),
  patchProp() {},
  parentNode: (element) => element.parent,
  nextSibling: () => null,
  insert(element, parent) {
    element.parent = parent;
    parent.children.push(element);
  },
  remove(element) {
    const siblings = element.parent?.children;
    if (siblings) siblings.splice(siblings.indexOf(element), 1);
  }
});
async function mount(t, detect = async (_file, scenario) => ({ data: inference(scenario) })) {
  const calls = [];
  const api = await load('src/api/cv/defect.ts', { '@/utils/request': () => assert.fail('页面挂载不得自动发送请求') });
  const { default: Page } = await load('src/views/cv/defect/index.vue', {
    '@/api/cv/defect': {
      ...api,
      detectDefect: (...args) => {
        calls.push(args);
        return detect(...args);
      }
    }
  });
  // Test the actual setup and lifecycle; visual layout/image decoding is verified in a real browser.
  Page.render = () => Vue.h('defect-page');
  const app = renderer.createApp(Page);
  const proxy = app.mount(node('root'));
  t.after(() => app.unmount());
  const state = proxy.$.setupState;
  function select(file = imageFile()) {
    state.onFileChange({ raw: file });
    state.imageRef = { naturalWidth: 1200, naturalHeight: 400 };
    state.onImageLoad();
    return file;
  }
  return { state, select, calls, unmount: () => app.unmount() };
}
function deferred() {
  let resolvePromise;
  let reject;
  const promise = new Promise((resolve, rejectPromise) => {
    resolvePromise = resolve;
    reject = rejectPromise;
  });
  return { promise, resolve: resolvePromise, reject };
}

test('缺陷 API 只发送手选图片和明确工业场景，保留 multipart file 字段', async () => {
  const calls = [];
  const { detectDefect, defectScenarios } = await load('src/api/cv/defect.ts', {
    '@/utils/request': async (config) => {
      calls.push(config);
      return { data: inference(config.data.get('scenario')) };
    }
  });
  assert.deepEqual(
    defectScenarios.map((item) => item.value),
    ['defect_metal', 'defect_pcb']
  );
  assert.deepEqual(
    defectScenarios.map((item) => item.label),
    ['金属表面缺陷', 'PCB缺陷']
  );
  const file = imageFile();
  for (const scenario of ['defect_metal', 'defect_pcb']) {
    const response = await detectDefect(file, scenario);
    const request = calls.at(-1);
    assert.equal(request.url, '/cv/detect/defect');
    assert.equal(request.method, 'post');
    assert.equal(request.data.get('file'), file);
    assert.equal(request.data.get('scenario'), scenario);
    assert.deepEqual([...request.data.keys()].sort(), ['file', 'scenario']);
    assert.equal(response.data.scenario, scenario);
  }
  for (const invalid of ['', 'general', 'defect_unknown', 'defect_pcb ']) {
    await assert.rejects(async () => detectDefect(file, invalid), /场景/);
  }
  assert.equal(calls.length, 2, '错误场景必须在网络请求前拒绝');
});

test('原图像素坐标换算为百分比，容器缩放与宽高比变化后仍对齐', async () => {
  const { defectBoxStyle } = await helpers();
  const style = defectBoxStyle(detection, { width: 1200, height: 400 });
  assert.equal(style.left, '10%');
  assert.equal(style.top, '10%');
  assert.equal(style.width, '20%');
  assert.equal(style.height, '35%');
  // At 600x200 the original [120,40,360,180] becomes [60,20,180,90].
  assert.equal((parseFloat(style.left) / 100) * 600, 60);
  assert.equal((parseFloat(style.height) / 100) * 200, 70);
  assert.deepEqual(detection.bbox, [120, 40, 360, 180], '禁止改写真实原图坐标');
});

test('边界框裁剪到原图范围，坏坐标和未加载原图不产生假框', async () => {
  const { defectBoxStyle } = await helpers();
  const clipped = defectBoxStyle({ ...detection, bbox: [-20, -5, 1300, 450] }, { width: 1200, height: 400 });
  assert.deepEqual([clipped.left, clipped.top, clipped.width, clipped.height], ['0%', '0%', '100%', '100%']);
  for (const bbox of [
    [1, 2, 1, 9],
    [3, 4, 2, 5],
    [0, 0, NaN, 8],
    [1, 2],
    [1300, 0, 1400, 20]
  ]) {
    assert.equal(defectBoxStyle({ ...detection, bbox }, { width: 1200, height: 400 }).display, 'none');
  }
  assert.equal(defectBoxStyle(detection, { width: 0, height: 0 }).display, 'none');
});

test('中文结果按实际类别计数并保留真实置信度和本次推理耗时', async () => {
  const { summarizeDefects, defectClassLabel } = await helpers();
  const actual = inference('defect_metal', [detection, { ...detection, confidence: 0.81 }, { ...detection, className: 'inclusion' }]);
  const rows = summarizeDefects(actual);
  assert.equal(rows.length, 2);
  assert.equal(rows[0].className, 'scratches');
  assert.equal(rows[0].count, 2);
  assert.equal(rows[0].confidence, 0.92);
  assert.equal(rows[0].processTimeMs, 23.75);
  assert.equal(defectClassLabel('scratches', 'defect_metal'), '划痕');
  assert.equal(defectClassLabel('missing_hole', 'defect_pcb'), '缺孔');
  assert.equal(defectClassLabel('custom_class', 'defect_pcb'), 'custom_class', '未知类别保留模型原值');
});

test('不接受失败推理、场景错配、坏坐标或伪造计数', async () => {
  const { validateDefectResult } = await helpers();
  assert.doesNotThrow(() => validateDefectResult(inference(), 'defect_metal'));
  for (const bad of [
    { ...inference(), success: false },
    inference('defect_pcb'),
    { ...inference(), count: 9 },
    { ...inference(), processTimeMs: NaN },
    inference('defect_metal', [{ ...detection, confidence: 1.5 }]),
    inference('defect_metal', [{ ...detection, bbox: [1, 2] }])
  ]) {
    assert.throws(() => validateDefectResult(bad, 'defect_metal'), /检测|场景|结果/);
  }
});

test('页面首次挂载不调用模型，手选文件后真实返回才显示结果', async (t) => {
  const { state, select, calls } = await mount(t);
  assert.equal(calls.length, 0);
  assert.equal(state.scenario, 'defect_metal');
  assert.equal(state.result, undefined);
  const file = select();
  await state.handleDetect();
  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], file);
  assert.equal(calls[0][1], 'defect_metal');
  assert.deepEqual(state.result.detections[0].bbox, [120, 40, 360, 180]);
  assert.equal(state.summaryRows[0].count, 1);
  assert.equal(state.loading, false);
  assert.equal(state.errorMessage, '');
  assert.equal(state.file.name, 'metal-test-014.png', '显示本次检测所用的确切本地文件名');
});

test('无图片或图片尚未解码时不发送检测请求', async (t) => {
  const { state, calls } = await mount(t);
  await state.handleDetect();
  assert.equal(calls.length, 0);
  assert.match(state.errorMessage, /图片/);
  state.onFileChange({ raw: imageFile() });
  await state.handleDetect();
  assert.equal(calls.length, 0);
  assert.match(state.errorMessage, /图片/);
});

test('请求期间保持 loading 并阻止重复推理', async (t) => {
  const pending = deferred();
  const { state, select, calls } = await mount(t, () => pending.promise);
  select();
  const run = state.handleDetect();
  assert.equal(state.loading, true);
  await state.handleDetect();
  assert.equal(calls.length, 1);
  pending.resolve({ data: inference() });
  await run;
  assert.equal(state.loading, false);
  assert.equal(state.result.count, 1);
});

test('切换场景清空旧结果，迟到响应不得覆盖新场景或关闭新请求 loading', async (t) => {
  const first = deferred();
  const second = deferred();
  const { state, select, calls } = await mount(t, (_file, scenario) => (scenario === 'defect_metal' ? first.promise : second.promise));
  select();
  const oldRun = state.handleDetect();
  state.scenario = 'defect_pcb';
  assert.equal(state.result, undefined);
  const newRun = state.handleDetect();
  first.resolve({ data: inference() });
  await oldRun;
  assert.equal(state.result, undefined);
  assert.equal(state.loading, true);
  second.resolve({ data: inference('defect_pcb') });
  await newRun;
  assert.equal(state.result.scenario, 'defect_pcb');
  assert.equal(calls.length, 2);
});

test('网络和模型异常清空旧结果并显示可读失败原因', async (t) => {
  let shouldFail = false;
  const { state, select } = await mount(t, async () => {
    if (shouldFail) throw new Error('Network Error');
    return { data: inference() };
  });
  select();
  await state.handleDetect();
  shouldFail = true;
  await state.handleDetect();
  assert.equal(state.result, undefined);
  assert.equal(state.loading, false);
  assert.match(state.errorMessage, /网络|连接/);
});

test('模型报告 success=false 时不得显示零缺陷成功状态', async (t) => {
  const { state, select } = await mount(t, async () => ({ data: { ...inference(), success: false } }));
  select();
  await state.handleDetect();
  assert.equal(state.result, undefined);
  assert.match(state.errorMessage, /检测/);
  assert.equal(state.loading, false);
});

test('真实零缺陷结果仍保留场景、计数与推理耗时', async (t) => {
  const { state, select } = await mount(t, async () => ({ data: inference('defect_metal', []) }));
  select();
  await state.handleDetect();
  assert.equal(state.result.count, 0);
  assert.equal(state.result.processTimeMs, 23.75);
  assert.equal(state.errorMessage, '');
});

test('换图、移除和卸载释放预览资源，移除后丢弃迟到结果', async (t) => {
  const revoked = [];
  t.mock.method(URL, 'revokeObjectURL', (value) => revoked.push(value));
  const pending = deferred();
  const { state, select, unmount } = await mount(t, () => pending.promise);
  select();
  const oldPreview = state.previewUrl;
  select(imageFile('metal-test-015.png'));
  assert.ok(revoked.includes(oldPreview));
  const newPreview = state.previewUrl;
  const run = state.handleDetect();
  state.clearImage();
  pending.resolve({ data: inference() });
  await run;
  assert.equal(state.file, undefined);
  assert.equal(state.previewUrl, '');
  assert.equal(state.result, undefined);
  assert.ok(revoked.includes(newPreview));
  select(imageFile('metal-test-016.png'));
  const lastPreview = state.previewUrl;
  unmount();
  assert.ok(revoked.includes(lastPreview), '组件卸载时必须释放当前图片预览');
});

test('空文件、非图片和超限图片不会上传，预览解码失败可以恢复', async (t) => {
  const { state, calls, select } = await mount(t);
  for (const invalid of [
    new File([], 'empty.png', { type: 'image/png' }),
    new File(['text'], 'file.txt', { type: 'text/plain' }),
    new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' })
  ]) {
    state.onFileChange({ raw: invalid });
    assert.equal(state.file, undefined);
    assert.match(state.errorMessage, /图片|文件/);
  }
  select();
  state.onImageError();
  assert.equal(state.file, undefined);
  assert.match(state.errorMessage, /图片/);
  assert.equal(calls.length, 0);
});

/** Exercise the actual shared interceptor, with only its network/storage/UI ports isolated. */
async function requestBoundary(t) {
  const OriginalDate = globalThis.Date;
  globalThis.Date = class extends OriginalDate {
    constructor(...args) {
      super(...(args.length ? args : [1700000000000]));
    }
    static now() {
      return 1700000000000;
    }
  };
  t.after(() => {
    globalThis.Date = OriginalDate;
  });
  const oldMessage = globalThis.ElMessage;
  globalThis.ElMessage = Object.assign(() => {}, { error() {}, warning() {} });
  t.after(() => {
    globalThis.ElMessage = oldMessage;
  });
  let requestInterceptor;
  let responseInterceptor;
  let responseError;
  let cached;
  const calls = [];
  const service = (config) =>
    Promise.resolve({ ...config, headers: { ...config.headers } })
      .then(requestInterceptor)
      .then((config) => {
        calls.push(config);
        return {
          data: { code: 200, data: inference(config.data instanceof FormData ? config.data.get('scenario') : 'defect_metal') },
          config,
          request: { responseType: undefined },
          headers: {}
        };
      })
      .then(responseInterceptor, responseError);
  service.interceptors = {
    request: {
      use(handler) {
        requestInterceptor = handler;
      }
    },
    response: {
      use(handler, error) {
        responseInterceptor = handler;
        responseError = error;
      }
    }
  };
  const request = await load('src/utils/request.ts', {
    axios: { defaults: { headers: {} }, create: () => service },
    '@/utils/auth': { getToken: () => '' },
    '@/plugins/cache': {
      session: {
        getJSON: () => cached,
        setJSON(_key, value) {
          cached = value;
        }
      }
    },
    '@/enums/RespEnum': await load('src/enums/RespEnum.ts'),
    '@/utils/errorCode': { errorCode: {} },
    '@/utils/ruoyi': {},
    '@/utils/crypto': {},
    '@/utils/jsencrypt': {},
    '@/router': {},
    '@/store/modules/user': {}
  });
  const api = await load('src/api/cv/defect.ts', { '@/utils/request': request.default });
  return { ...api, request: request.default, calls };
}

test('快速连续检测不同图片和场景不会被通用表单重复提交逻辑拦截', async (t) => {
  const api = await requestBoundary(t);
  await api.detectDefect(imageFile('metal-a.jpg'), 'defect_metal');
  const second = await Promise.allSettled([api.detectDefect(imageFile('pcb-b.jpg'), 'defect_pcb')]);
  assert.equal(second[0].status, 'fulfilled', '500ms 内不同图片的只读推理必须能发到服务器');
  assert.equal(api.calls.length, 2);
  assert.equal(api.calls[0].data.get('scenario'), 'defect_metal');
  assert.equal(api.calls[1].data.get('scenario'), 'defect_pcb');
});

test('检测查询的请求设置不改变其他业务写请求的重复提交保护', async (t) => {
  const api = await requestBoundary(t);
  await api.request({ url: '/test-business-write', method: 'post', data: { id: '9007199254740993' }, headers: {} });
  await assert.rejects(api.request({ url: '/test-business-write', method: 'post', data: { id: '9007199254740993' }, headers: {} }), /重复提交/);
  assert.equal(api.calls.length, 1);
});

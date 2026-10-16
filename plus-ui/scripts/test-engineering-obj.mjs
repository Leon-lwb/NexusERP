import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import { loadEngineeringModule, loadEngineeringUI, mountEngineeringUI, engineeringUIEnvironment, deferred } from './engineering-test-utils.mjs';
const r = await loadEngineeringModule('src/components/ai/engineering/model-resources.ts');
const v = await loadEngineeringModule('src/components/ai/engineering/obj-viewer.ts');
const taskId = '2000000000000000002';
const pngBlob = () =>
  new Blob([Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWQAAAABJRU5ErkJggg==', 'base64')], {
    type: 'image/png'
  });
const obj = { artifactId: '3', taskId, type: 'PCB_MODEL_OBJ', groupId: 'model-1', relativePath: 'model/board.obj', size: 200 };
const mtl = { artifactId: '4', taskId, type: 'PCB_MODEL_MTL', groupId: 'model-1', relativePath: 'model/board.mtl', size: 200 };
const texture = { artifactId: '5', taskId, type: 'PCB_MODEL_TEXTURE', groupId: 'model-1', relativePath: 'model/texture.png', size: 100 };
const manifest = { taskId, artifacts: [obj, mtl, texture] };

test('实际解析合成OBJ几何及MTL材质', async () => {
  const objText = await readFile(new URL('../tests/fixtures/engineering/board.obj', import.meta.url), 'utf8');
  const mtlText = await readFile(new URL('../tests/fixtures/engineering/board.mtl', import.meta.url), 'utf8');
  const parsed = v.parseEngineeringObj(objText, mtlText, () => {
    throw new Error('不能联网');
  });
  assert.equal(parsed.object.children[0].geometry.attributes.position.count, 6);
  assert.ok(parsed.bounds.max.x > parsed.bounds.min.x);
  assert.equal(parsed.hasMaterials, true);
  v.disposeEngineeringObject(parsed.object);
});
test('几何无材质可预览，空几何和顶点超额必须失败', () => {
  const triangle = 'v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3';
  const parsed = v.parseEngineeringObj(triangle);
  assert.equal(parsed.hasMaterials, false);
  v.disposeEngineeringObject(parsed.object);
  assert.throws(() => v.parseEngineeringObj('garbage'), /几何/);
  assert.throws(() => v.parseEngineeringObj(triangle, undefined, undefined, 2), /顶点/);
});
test('外部纹理、路径逃逸和未知引用在网络请求前被拒绝', async () => {
  for (const path of [
    'https://evil/a.png',
    'http://evil/a.png',
    'file:C:/a.png',
    '//host/a.png',
    '\\\\host\\a.png',
    'C:/a.png',
    '/a.png',
    '../a.png',
    '%2e%2e/a.png',
    'missing.png'
  ]) {
    let calls = 0;
    const resources = new r.EngineeringModelResources(manifest, obj, async () => {
      calls++;
      return new Blob();
    });
    await assert.rejects(resources.prepare('mtllib board.mtl', 'map_Kd ' + path));
    assert.equal(calls, 0);
    resources.dispose();
  }
});
test('跨任务或跨产物组引用不允许解析', () => {
  for (const replacement of [
    { ...texture, taskId: '9' },
    { ...texture, groupId: 'model-2' }
  ]) {
    const resources = new r.EngineeringModelResources({ ...manifest, artifacts: [obj, replacement] }, obj, async () => new Blob());
    assert.throws(() => resources.resolveReference('texture.png', obj.relativePath), /清单|产物组|任务/);
    resources.dispose();
  }
});
test('纹理重复引用仅请求一次，URLModifier拒绝任意默认网络路径', async () => {
  let calls = 0;
  const revoked = [];
  const resources = new r.EngineeringModelResources(
    manifest,
    obj,
    async () => {
      calls++;
      return pngBlob();
    },
    {
      createObjectURL: () => 'blob:texture',
      revokeObjectURL: (url) => revoked.push(url)
    }
  );
  await resources.prepare('mtllib board.mtl', 'map_Kd texture.png\nmap_Ks texture.png');
  assert.equal(calls, 1);
  assert.equal(resources.urlModifier('texture.png'), 'blob:texture');
  assert.equal(resources.urlModifier('blob:texture'), 'blob:texture');
  assert.throws(() => resources.urlModifier('https://evil/a.png'));
  resources.dispose();
  resources.dispose();
  assert.deepEqual(revoked, ['blob:texture']);
});
test('销毁中止旧纹理读取，不分配新URL', async () => {
  const pending = deferred();
  let signal;
  let urls = 0;
  const resources = new r.EngineeringModelResources(
    manifest,
    obj,
    (_artifact, s) => {
      signal = s;
      return pending.promise;
    },
    {
      createObjectURL: () => {
        urls++;
        return 'blob:x';
      },
      revokeObjectURL: () => {}
    }
  );
  const reading = resources.prepare('mtllib board.mtl', 'map_Kd texture.png');
  resources.dispose();
  pending.resolve(new Blob());
  await assert.rejects(reading);
  assert.equal(signal.aborted, true);
  assert.equal(urls, 0);
});
test('统一生命周期仅释放一次帧、观察器、控件、模型和渲染器', () => {
  const calls = [];
  const release = v.createViewerDisposer({
    cancelFrame: () => calls.push('frame'),
    disconnect: () => calls.push('observer'),
    controls: { dispose: () => calls.push('controls') },
    renderer: { dispose: () => calls.push('renderer'), forceContextLoss: () => calls.push('context') },
    disposeObject: () => calls.push('object'),
    resources: { dispose: () => calls.push('resources') }
  });
  release();
  release();
  assert.deepEqual(calls, ['frame', 'observer', 'controls', 'object', 'renderer', 'context', 'resources']);
});

test('缺少MTL时仍保留真实OBJ几何，明确无材质', async () => {
  const resources = new r.EngineeringModelResources({ ...manifest, artifacts: [obj] }, obj, async () => {
    throw new Error('无材质不应联网');
  });
  assert.deepEqual(resources.materialArtifacts('mtllib board.mtl'), []);
  assert.equal(await resources.prepare('mtllib board.mtl'), undefined);
  resources.dispose();
});

test('纹理内容拒绝SVG和HTML，不为非栅格内容创建URL', async () => {
  let urls = 0;
  const resources = new r.EngineeringModelResources(
    manifest,
    obj,
    async () => new Blob(['<svg><image href="https://evil/a.png" /></svg>'], { type: 'image/svg+xml' }),
    {
      createObjectURL: () => {
        urls++;
        return 'blob:bad';
      },
      revokeObjectURL: () => {}
    }
  );
  await assert.rejects(resources.prepare('mtllib board.mtl', 'map_Kd texture.png'), /纹理|图片/);
  assert.equal(urls, 0);
  resources.dispose();
});

test('超大OBJ在读取完整字符串前拒绝，仍可从产物列表下载', async () => {
  const text = await loadEngineeringModule('src/components/ai/engineering/model-text.ts');
  let reads = 0;
  class OversizedBlob extends Blob {
    get size() {
      return text.MAX_OBJ_PREVIEW_BYTES + 1;
    }
    async text() {
      reads++;
      return 'v 0 0 0';
    }
  }
  await assert.rejects(text.readModelText(new OversizedBlob(), 'OBJ'), /预览|过大/);
  assert.equal(reads, 0);
});
test('解析前限制面展开规模和单行长度，避免几何分配后才拒绝', () => {
  assert.throws(() => v.parseEngineeringObj('v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\nf 1 2 3', undefined, undefined, 3), /展开|顶点/);
  assert.throws(() => v.parseEngineeringObj('f ' + '1 '.repeat(40000)), /单行|过长/);
});

for (const record of ['l', 'p'])
  test(`${record}记录的空白展开必须在分配几何前计入预算`, async () => {
    const text = await loadEngineeringModule('src/components/ai/engineering/model-text.ts');
    const source = 'v 0 0 0\nv 1 0 0\n' + record + ' 1          2\n';
    assert.throws(() => text.assertSafeObjText(source, 6), /展开|顶点/);
  });
test('折线预算遵循加载器实际位置数量，精确上限允许解析', () => {
  const parsed = v.parseEngineeringObj('v 0 0 0\nv 1 0 0\nl 1 2 1 2\n', undefined, undefined, 4);
  try {
    assert.equal(parsed.object.children[0].geometry.attributes.position.count, 4);
  } finally {
    v.disposeEngineeringObject(parsed.object);
  }
});
test('OBJ续行在解析器拼接放大几何前被拒绝', async () => {
  const text = await loadEngineeringModule('src/components/ai/engineering/model-text.ts');
  const continuation = String.fromCharCode(92, 10);
  const source = 'v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 ' + continuation + '3 1 2 3\n';
  assert.throws(() => text.assertSafeObjText(source, 6), /续行/);
});
test('实际OBJ模板从空清单更新到新模型后加载，并在卸载时释放', async () => {
  const initial = { taskId, artifacts: [], missingTypes: ['PCB_MODEL_OBJ'], state: 'RUNNING', revision: 0, canManufacture: false };
  const { page, reads, viewers } = await mountObjPreview(initial);
  try {
    assert.equal(reads.length, 0);
    page.state.manifest = { ...initial, revision: 1, artifacts: [obj] };
    await page.flush();
    assert.equal(page.findAll((node) => node.props?.class === 'obj-viewport').length, 1);
    assert.equal(reads.length, 1);
    assert.equal(reads[0].id, obj.artifactId);
    assert.equal(viewers.length, 1);
    assert.equal(viewers[0].vertices, 3);
  } finally {
    page.unmount();
  }
  assert.equal(viewers[0].disposed, true);
  assert.equal(reads[0].signal.aborted, true);
});

/** 复用真实模板与解析器，只替换浏览器 WebGL 边界。 */
async function mountObjPreview(initial, content = () => 'v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n') {
  const reads = [],
    viewers = [];
  engineeringUIEnvironment({
    getEngineeringArtifactContent: async (id, signal) => {
      reads.push({ id, signal });
      return new Blob([content(id)]);
    }
  });
  globalThis.__engineeringViewer = (container, parsed, resources) => {
    const viewer = {
      container,
      vertices: parsed.object.children[0].geometry.attributes.position.count,
      hasMaterials: parsed.hasMaterials,
      disposed: false,
      dispose() {
        this.disposed = true;
        v.disposeEngineeringObject(parsed.object);
        resources.dispose();
      },
      fit() {},
      reset() {}
    };
    viewers.push(viewer);
    return viewer;
  };
  const options = {
    renderTemplate: true,
    plugins: [
      {
        name: 'webgl-boundary',
        setup(builder) {
          builder.onResolve({ filter: /^\.\/obj-viewer$/ }, ({ resolveDir }) => ({ path: resolveDir + '/obj-viewer.ts', namespace: 'viewer-test' }));
          builder.onResolve({ filter: /^review-actual-viewer$/ }, ({ importer }) => ({ path: importer, namespace: 'file' }));
          builder.onLoad({ filter: /.*/, namespace: 'viewer-test' }, () => ({
            contents: `export { parseEngineeringObj, disposeEngineeringObject } from 'review-actual-viewer'; export const createEngineeringViewer=(...args)=>globalThis.__engineeringViewer(...args);`
          }));
        }
      }
    ]
  };
  const component = (await loadEngineeringUI('src/components/ai/engineering/PcbObjViewer.vue', true, options)).default;
  const page = await mountEngineeringUI(component, { manifest: initial }, options);
  return { page, reads, viewers };
}

test('同组缺少引用MTL时统一材质降级，兼容服务端modelGroups与旧清单', async () => {
  for (const modelGroups of [
    undefined,
    [{ groupId: 'model-1', status: 'DEGRADED', uniformMaterial: true, missingPaths: ['model/missing.mtl'], truncated: false }]
  ]) {
    const degraded = { ...manifest, artifacts: [obj, mtl], modelGroups };
    let reads = 0;
    const resources = new r.EngineeringModelResources(degraded, obj, async () => {
      reads++;
      throw new Error('缺材质降级不应读取贴图');
    });
    try {
      assert.deepEqual(resources.materialArtifacts('mtllib board.mtl missing.mtl'), []);
      assert.equal(resources.materialMissing, true);
      assert.equal(await resources.prepare('mtllib board.mtl missing.mtl'), undefined);
      assert.equal(reads, 0);
    } finally {
      resources.dispose();
    }
  }
});
test('模型组降级也必须拒绝OBJ中的外链、绝对路径与上级目录', () => {
  for (const reference of ['https://evil/board.mtl', 'file:C:/board.mtl', '//host/board.mtl', '../board.mtl', 'C:/board.mtl', '%2e%2e/board.mtl']) {
    const degraded = {
      ...manifest,
      artifacts: [obj],
      modelGroups: [{ groupId: 'model-1', status: 'DEGRADED', uniformMaterial: true, missingPaths: ['model/missing.mtl'], truncated: false }]
    };
    const resources = new r.EngineeringModelResources(degraded, obj, async () => {
      throw new Error('非法引用不能联网');
    });
    try {
      assert.throws(() => resources.materialArtifacts('mtllib missing.mtl ' + reference), /路径|外部|目录/);
    } finally {
      resources.dispose();
    }
  }
});
test('缺MTL真实模板仍渲染统一几何并标识材质缺失，不读取无关MTL', async () => {
  const degraded = {
    ...manifest,
    artifacts: [obj, mtl],
    modelGroups: [{ groupId: 'model-1', status: 'DEGRADED', uniformMaterial: true, missingPaths: ['model/missing.mtl'], truncated: false }]
  };
  const { page, reads, viewers } = await mountObjPreview(degraded, () => 'mtllib missing.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n');
  try {
    assert.equal(viewers.length, 1);
    assert.equal(viewers[0].vertices, 3);
    assert.equal(viewers[0].hasMaterials, false);
    assert.deepEqual(
      reads.map((read) => read.id),
      [obj.artifactId]
    );
    assert.ok(page.findAll((node) => typeof node.text === 'string' && node.text.includes('材质缺失')).length);
    assert.equal(page.findAll((node) => node.tag === 'ElAlert' && node.props.type === 'error').length, 0);
  } finally {
    page.unmount();
  }
});

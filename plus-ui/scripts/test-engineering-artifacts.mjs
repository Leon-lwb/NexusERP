import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  loadEngineeringApi,
  loadEngineeringModule,
  loadEngineeringUI,
  mountEngineeringUI,
  engineeringUIEnvironment,
  deferred
} from './engineering-test-utils.mjs';
const a = await loadEngineeringModule('src/views/ai/engineering/artifact-model.ts');
const artifact = { artifactId: '9223372036854775807', type: 'PCB_PREVIEW_2D', relativePath: 'preview/top.png', size: 67 };

test('空清单和缺失PNG返回空列表，不制造预览', () => {
  assert.deepEqual(a.artifactsOfType(undefined, 'PCB_PREVIEW_2D'), []);
  assert.deepEqual(a.artifactsOfType({ artifacts: [{ ...artifact, type: 'PCB_SOURCE' }] }, 'PCB_PREVIEW_2D'), []);
});
test('超过限制的产物在发起网络请求前拒绝', async () => {
  let calls = 0;
  await assert.rejects(
    a.readArtifact({ ...artifact, size: 104857601 }, async () => {
      calls++;
    }),
    /过大/
  );
  assert.equal(calls, 0);
});
test('JSON形式的401和403不能当PNG或下载正文', async () => {
  for (const code of [401, 403]) {
    await assert.rejects(
      a.readArtifact(artifact, async () => new Blob([JSON.stringify({ code, msg: '无权限', traceId: 'trace-a' })], { type: 'application/json' })),
      (error) => error.status === code && error.traceId === 'trace-a'
    );
  }
});
test('PNG真实签名校验，拒绝伪装图片和过大像素', async () => {
  const png = new Uint8Array(24);
  png.set([137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82]);
  new DataView(png.buffer).setUint32(16, 200);
  new DataView(png.buffer).setUint32(20, 100);
  assert.deepEqual(await a.validatePng(new Blob([png])), { width: 200, height: 100 });
  await assert.rejects(a.validatePng(new Blob(['<svg/>'])), /PNG/);
  new DataView(png.buffer).setUint32(16, 999999);
  await assert.rejects(a.validatePng(new Blob([png])), /像素/);
});
test('BOM保留十进制数量与null，DRC违规和截断忠实显示', async () => {
  const bom = await a.readBom(
    new Blob([JSON.stringify({ rows: [{ designator: 'R1', value: null, quantity: '9007199254740993.10' }], truncated: true })])
  );
  assert.equal(bom.rows[0].value, null);
  assert.equal(bom.rows[0].quantity, '9007199254740993.10');
  assert.equal(bom.truncated, true);
  const drc = await a.readDrc(
    new Blob([
      JSON.stringify({
        status: 'VIOLATIONS',
        errorCount: 1,
        warningCount: 0,
        violations: [{ severity: 'ERROR', message: '间距不足' }],
        truncated: true
      })
    ])
  );
  assert.equal(drc.status, 'VIOLATIONS');
  assert.equal(drc.violations[0].message, '间距不足');
  assert.equal(drc.truncated, true);
  await assert.rejects(a.readDrc(new Blob(['{}'])), /状态/);
});
test('切换图片只保留当前请求并回收全部Blob URL', async () => {
  const revoked = [];
  let serial = 0;
  const slot = new a.ArtifactBlobSlot({ createObjectURL: () => 'blob:' + ++serial, revokeObjectURL: (url) => revoked.push(url) });
  const old = deferred();
  const first = slot.load(() => old.promise);
  await slot.load(async () => new Blob(['new']));
  old.resolve(new Blob(['old']));
  assert.equal(await first, undefined);
  assert.equal(slot.url, 'blob:1');
  await slot.load(async () => new Blob(['next']));
  slot.dispose();
  slot.dispose();
  assert.deepEqual(revoked, ['blob:1', 'blob:2']);
});
test('下载使用服务端清单文件名，不执行文件内容，释放URL', async () => {
  const clicked = [];
  const revoked = [];
  await a.saveArtifactBlob(
    new Blob(['<script>bad()</script>']),
    { ...artifact, fileName: '设计草稿.zip' },
    {
      createObjectURL: () => 'blob:authorized',
      revokeObjectURL: (url) => revoked.push(url),
      click: (url, name) => clicked.push({ url, name }),
      defer: (fn) => fn()
    }
  );
  assert.deepEqual(clicked, [{ url: 'blob:authorized', name: '设计草稿.zip' }]);
  assert.deepEqual(revoked, ['blob:authorized']);
});

test('真实BOM组件对403显示权限错误，不将错误正文作为条目', async () => {
  engineeringUIEnvironment({
    getEngineeringArtifactReport: async () => {
      throw { response: { status: 403, data: { code: 403, msg: '拒绝', data: { traceId: 'trace-b' } } } };
    }
  });
  const page = await mountEngineeringUI((await loadEngineeringUI('src/components/ai/engineering/PcbBomTable.vue')).default, {
    artifact: { ...artifact, type: 'PCB_BOM' }
  });
  assert.match(page.setup.error, /权限/);
  assert.equal(page.setup.report, undefined);
  page.unmount();
});
test('Blob鉴权错误保留实际R信封data中的跟踪号', async () => {
  await assert.rejects(
    a.readArtifact(
      artifact,
      async () => new Blob([JSON.stringify({ code: 403, msg: '拒绝', data: { traceId: 'trace-b' } })], { type: 'application/json' })
    ),
    (error) => error.status === 403 && error.traceId === 'trace-b'
  );
});

test('实际content API仅请求后端鉴权Blob并保留响应下载文件名', async () => {
  const requests = [];
  const controller = new AbortController();
  const blob = new Blob(['archive']);
  globalThis.__engineeringApiRequest = async (method, url, config) => {
    requests.push({ method, url, config });
    return config.transformResponse[0](blob, { 'content-disposition': "attachment; filename*=UTF-8''%E6%9D%BF%E5%8D%A1.zip" });
  };
  const api = await loadEngineeringModule('src/api/ai/engineering.ts', [
    {
      name: 'authenticated-request',
      setup(builder) {
        builder.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'request', namespace: 'request-test' }));
        builder.onLoad({ filter: /.*/, namespace: 'request-test' }, () => ({
          contents: `export default { get: (url, config) => globalThis.__engineeringApiRequest('get', url, config), post: (url, data) => globalThis.__engineeringApiRequest('post', url, { data }), delete: (url) => globalThis.__engineeringApiRequest('delete', url, {}) };`
        }));
      }
    }
  ]);
  const result = await api.getEngineeringArtifactContent('9223372036854775807', controller.signal);
  assert.equal(result, blob);
  assert.equal(requests[0].url, '/ai/engineering/artifacts/9223372036854775807/content');
  assert.equal(requests[0].config.responseType, 'blob');
  assert.equal(requests[0].config.signal, controller.signal);
  assert.throws(() => api.getEngineeringArtifactContent('https://evil/a.png'));
  assert.equal(requests.length, 1);
});

test('清单DRC不可用时，报告中的PASS不能覆盖服务端状态', async () => {
  engineeringUIEnvironment({
    getEngineeringArtifactReport: async () => ({ code: 200, data: { status: 'PASS', issues: [], errorCount: 0, warningCount: 0, truncated: false } })
  });
  const page = await mountEngineeringUI((await loadEngineeringUI('src/components/ai/engineering/PcbDrcReport.vue')).default, {
    artifact: { ...artifact, type: 'PCB_DRC_REPORT' },
    summary: { status: 'UNAVAILABLE' }
  });
  assert.equal(page.setup.report.status, 'PASS');
  assert.equal(page.setup.presentation.label, 'DRC 不可用');
  page.unmount();
});

test('服务端规范BOM和DRC摘要形状按items/issues解码', async () => {
  const bom = await a.readBom(
    new Blob([JSON.stringify({ bom: { items: [{ designator: 'U1', value: null, quantity: '1.000000' }], truncated: true } })])
  );
  assert.equal(bom.rows[0].quantity, '1.000000');
  assert.equal(bom.truncated, true);
  const drc = await a.readDrc(
    new Blob([
      JSON.stringify({
        drc: { status: 'VIOLATIONS', issues: [{ severity: 'ERROR', message: '线宽不足' }], errorCount: 1, warningCount: 0, truncated: false }
      })
    ])
  );
  assert.equal(drc.violations[0].message, '线宽不足');
  assert.equal(drc.errorCount, 1);
});

test('规范报告API使用鉴权report路由及R信封，保留取消信号', async () => {
  const requests = [];
  const response = { code: 200, data: { items: [{ designator: 'U1', quantity: '1' }], total: 1, truncated: false } };
  globalThis.__engineeringApiRequest = async (method, url, config) => {
    requests.push({ method, url, config });
    return response;
  };
  const api = await loadEngineeringApi();
  const controller = new AbortController();
  assert.equal(await api.getEngineeringArtifactReport('9223372036854775807', controller.signal), response);
  assert.equal(requests[0].url, '/ai/engineering/artifacts/9223372036854775807/report');
  assert.equal(requests[0].method, 'get');
  assert.equal(requests[0].config.signal, controller.signal);
  assert.equal(requests[0].config.responseType, undefined);
  assert.throws(() => api.getEngineeringArtifactReport('../2'));
});
test('BOM展示规范report数量与截断信息，不下载原始报告', async () => {
  let rawReads = 0;
  const reports = [];
  engineeringUIEnvironment({
    getEngineeringArtifactReport: async (id, signal) => {
      reports.push({ id, signal });
      return { code: 200, data: { items: [{ designator: 'U1', quantity: '1', value: null }], total: 10001, truncated: true } };
    },
    getEngineeringArtifactContent: async () => {
      rawReads++;
      return new Blob([JSON.stringify({ items: [{ designator: 'U1', quantity: 1 }] })]);
    }
  });
  const page = await mountEngineeringUI((await loadEngineeringUI('src/components/ai/engineering/PcbBomTable.vue')).default, {
    artifact: { ...artifact, type: 'PCB_BOM' }
  });
  try {
    assert.equal(page.setup.report.rows[0].quantity, '1');
    assert.equal(page.setup.report.truncated, true);
    assert.equal(rawReads, 0);
    assert.equal(reports.length, 1);
    assert.equal(reports[0].id, artifact.artifactId);
    assert.ok(reports[0].signal instanceof AbortSignal);
  } finally {
    page.unmount();
  }
});
test('DRC展示规范report明细，拒绝回退原始content', async () => {
  let rawReads = 0;
  engineeringUIEnvironment({
    getEngineeringArtifactReport: async () => ({
      code: 200,
      data: { status: 'VIOLATIONS', errorCount: 1, warningCount: 0, issues: [{ severity: 'ERROR', message: '线宽不足' }], truncated: true }
    }),
    getEngineeringArtifactContent: async () => {
      rawReads++;
      return new Blob(['{}']);
    }
  });
  const page = await mountEngineeringUI((await loadEngineeringUI('src/components/ai/engineering/PcbDrcReport.vue')).default, {
    artifact: { ...artifact, type: 'PCB_DRC_REPORT' }
  });
  try {
    assert.equal(page.setup.report.status, 'VIOLATIONS');
    assert.equal(page.setup.report.violations[0].message, '线宽不足');
    assert.equal(page.setup.report.truncated, true);
    assert.equal(rawReads, 0);
  } finally {
    page.unmount();
  }
});

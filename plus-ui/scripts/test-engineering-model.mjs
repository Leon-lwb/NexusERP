import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  loadEngineeringModule,
  loadEngineeringApi,
  loadEngineeringUI,
  mountEngineeringUI,
  engineeringUIEnvironment,
  deferred,
  clock
} from './engineering-test-utils.mjs';
const m = await loadEngineeringModule('src/views/ai/engineering/model.ts');

test('雪花ID保留字符串，拒绝已失真的数字和路径', () => {
  assert.equal(m.engineeringId('9223372036854775807'), '9223372036854775807');
  for (const value of [9223372036854775807, '../2', '2/3', '', '0', '01']) assert.throws(() => m.engineeringId(value));
});
test('旧任务响应和已失效请求不能覆盖新任务', () => {
  const gate = new m.EngineeringRequestGate();
  const old = gate.begin('9223372036854775807');
  const current = gate.begin('2000000000000000002');
  assert.equal(gate.isCurrent(old), false);
  assert.equal(gate.isCurrent(current), true);
  assert.equal(current.taskId, '2000000000000000002');
  gate.invalidate();
  assert.equal(gate.isCurrent(current), false);
});
test('仅非终态轮询，未获下载权限时无下载入口', () => {
  for (const state of ['SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELED', 'EXPIRED']) assert.equal(m.shouldPoll(state), false);
  for (const state of ['CREATED', 'WAITING_DEVICE', 'RUNNING']) assert.equal(m.shouldPoll(state), true);
  assert.equal(m.shouldPoll('UNKNOWN'), false);
  assert.deepEqual(m.downloadableArtifacts([{ artifactId: '1', type: 'PCB_SOURCE' }], false), []);
  assert.equal(m.drcPresentation('UNAVAILABLE').type, 'warning');
  assert.notEqual(m.drcPresentation('UNAVAILABLE').label, m.drcPresentation('PASS').label);
});
test('三秒轮询终态停止，隐藏时中止，返回后重新查询', async () => {
  const scheduler = clock();
  let reads = 0;
  let signal;
  const poller = new m.EngineeringPoller({
    scheduler,
    load: async (s) => {
      signal = s;
      return ++reads < 2;
    }
  });
  await poller.start();
  assert.equal(scheduler.timers.values().next().value.delay, 3000);
  poller.setVisible(false);
  assert.equal(signal.aborted, true);
  assert.equal(scheduler.timers.size, 0);
  await poller.setVisible(true);
  assert.equal(reads, 2);
  assert.equal(scheduler.timers.size, 0);
  poller.stop();
});
test('卸载阻止未完成读取安排下一次轮询', async () => {
  const pending = deferred();
  const scheduler = clock();
  let signal;
  const poller = new m.EngineeringPoller({
    scheduler,
    load: (s) => {
      signal = s;
      return pending.promise;
    }
  });
  const reading = poller.start();
  poller.stop();
  pending.resolve(true);
  await reading;
  assert.equal(signal.aborted, true);
  assert.equal(scheduler.timers.size, 0);
});
test('403和404立即停止，普通网络错误重试有上限', async () => {
  for (const status of [401, 403, 404]) {
    const scheduler = clock();
    const poller = new m.EngineeringPoller({
      scheduler,
      load: async () => {
        throw { response: { status } };
      }
    });
    await poller.start();
    assert.equal(scheduler.timers.size, 0);
    poller.stop();
  }
  const scheduler = clock();
  let reads = 0;
  const poller = new m.EngineeringPoller({
    scheduler,
    load: async () => {
      reads++;
      throw new Error('断网');
    }
  });
  await poller.start();
  while (scheduler.timers.size) await scheduler.tick();
  assert.equal(reads, 6);
  poller.stop();
});

test('工程功能默认关闭，诊断只显示实际传输模式和能力', () => {
  assert.equal(m.engineeringFeatureEnabled(undefined), false);
  assert.equal(m.engineeringFeatureEnabled('false'), false);
  assert.equal(m.engineeringFeatureEnabled('true'), true);
  const diagnostic = m.engineeringTransport({
    transportMode: 'CLI_COMPATIBILITY',
    capabilities: ['EXPORT_PNG'],
    diagnosticCode: 'MCP_NOT_SUPPORTED'
  });
  assert.match(diagnostic.notice, /MCP_NOT_SUPPORTED/);
  assert.equal(diagnostic.label, 'CLI_COMPATIBILITY');
});

test('实际设备DTO的心跳能力、传输诊断和版本被正确适配', () => {
  const device = m.normalizeEngineeringDevice(
    {
      deviceId: '9223372036854775807',
      status: 'ACTIVE',
      lastHeartbeat: '2026-10-16T01:00:00Z',
      capabilities: {
        connectorVersion: '0.1.0',
        cliVersion: '1.0.15',
        editorVersion: '3.2',
        transportMode: 'CLI_COMPATIBILITY',
        capabilities: ['EXPORT_PNG'],
        diagnosticCode: 'MCP_NOT_SUPPORTED'
      }
    },
    Date.parse('2026-10-16T01:00:10Z')
  );
  assert.equal(device.deviceId, '9223372036854775807');
  assert.deepEqual(device.capabilities, ['EXPORT_PNG']);
  assert.equal(device.transportMode, 'CLI_COMPATIBILITY');
  assert.equal(device.connectorVersion, '0.1.0');
  assert.equal(device.onlineLabel, '近期心跳');
  assert.equal(device.diagnosticCode, 'MCP_NOT_SUPPORTED');
});
test('实际设备页面默认关闭时不请求后端，启用后展示服务端结果', async () => {
  let calls = 0;
  engineeringUIEnvironment({
    listEngineeringDevices: async () => {
      calls++;
      return { data: [{ deviceId: '1', capabilities: [], online: false }] };
    }
  });
  const disabled = await mountEngineeringUI((await loadEngineeringUI('src/views/ai/engineering/device/index.vue', false)).default);
  assert.equal(calls, 0);
  disabled.unmount();
  const enabled = await mountEngineeringUI((await loadEngineeringUI('src/views/ai/engineering/device/index.vue')).default);
  assert.equal(calls, 1);
  assert.equal(enabled.setup.devices[0].deviceId, '1');
  enabled.unmount();
});
test('预览页切换taskId后旧接口响应不能覆盖当前任务', async () => {
  const { reactive } = await import('vue');
  const oldTask = deferred();
  const oldManifest = deferred();
  const route = reactive({ params: { taskId: '2' } });
  engineeringUIEnvironment({
    route,
    getEngineeringTask: (id) => (id === '2' ? oldTask.promise : Promise.resolve({ data: { taskId: id, state: 'SUCCEEDED', deviceId: '1' } })),
    getEngineeringManifest: (id) => (id === '2' ? oldManifest.promise : Promise.resolve({ data: { taskId: id, artifacts: [], missingTypes: [] } }))
  });
  const page = await mountEngineeringUI((await loadEngineeringUI('src/views/ai/engineering/preview/index.vue')).default);
  route.params.taskId = '3';
  await page.flush();
  assert.equal(page.setup.task.taskId, '3');
  oldTask.resolve({ data: { taskId: '2', state: 'RUNNING' } });
  oldManifest.resolve({ data: { taskId: '2', artifacts: [] } });
  await page.flush();
  assert.equal(page.setup.task.taskId, '3');
  page.unmount();
});

test('429读取重试遵循Retry-After，避免持续轰击后端', async () => {
  const scheduler = clock();
  const poller = new m.EngineeringPoller({
    scheduler,
    load: async () => {
      throw { response: { status: 429, headers: { 'retry-after': '12' } } };
    }
  });
  await poller.start();
  assert.ok(scheduler.timers.values().next().value.delay >= 12000);
  poller.stop();
});

test('任务状态筛选基于实际用户任务，分页总数与筛选结果一致', async () => {
  const calls = [];
  globalThis.__engineeringApiRequest = async (_method, _url, config) => {
    calls.push(config.params);
    return config.params.pageNum === 1
      ? {
          code: 200,
          rows: [
            { taskId: '2', state: 'SUCCEEDED' },
            { taskId: '3', state: 'RUNNING' }
          ],
          total: 3
        }
      : { code: 200, rows: [{ taskId: '4', state: 'SUCCEEDED' }], total: 3 };
  };
  const api = await loadEngineeringApi();
  const result = await api.listEngineeringTasks({ state: 'SUCCEEDED', pageNum: 2, pageSize: 1 });
  assert.equal(result.rows[0].taskId, '4');
  assert.equal(result.total, 2);
  assert.equal(calls.length, 2);
});
test('缺少原始生成参数时重试先展示已审模板确认，不发送空参数生成', async () => {
  const commands = [];
  engineeringUIEnvironment({
    createEngineeringTask: async (command) => {
      commands.push(command);
      return { data: { taskId: '5' } };
    },
    listEngineeringTemplates: async () => ({
      data: [{ templateId: 'temperature', templateVersion: '1', templateSha256: 'a'.repeat(64), title: '温湿度模板' }]
    })
  });
  const page = await mountEngineeringUI((await loadEngineeringUI('src/views/ai/engineering/task/index.vue')).default);
  await page.setup.retry({ taskId: '2', deviceId: '1', action: 'CREATE_PCB_DRAFT', state: 'FAILED' });
  assert.equal(commands.length, 0);
  assert.equal(page.setup.retryVisible, true);
  assert.equal(page.setup.retryTemplates.length, 1);
  page.unmount();
});

test('任务列表切换分页时，旧清单读取不覆盖当前页', async () => {
  const oldManifest = deferred();
  let pageNum = 0;
  engineeringUIEnvironment({
    listEngineeringTasks: async () => ({ rows: [{ taskId: ++pageNum === 1 ? '2' : '3', state: 'SUCCEEDED' }], total: 2 }),
    getEngineeringManifest: (taskId) => (taskId === '2' ? oldManifest.promise : Promise.resolve({ data: { taskId, missingTypes: [] } }))
  });
  const page = await mountEngineeringUI((await loadEngineeringUI('src/views/ai/engineering/task/index.vue')).default);
  await page.setup.refresh();
  assert.equal(page.setup.tasks[0].taskId, '3');
  oldManifest.resolve({ data: { taskId: '2', missingTypes: ['PCB_MODEL_OBJ'] } });
  await page.flush();
  assert.equal(page.setup.tasks[0].taskId, '3');
  page.unmount();
});
test('新草稿重试确认使用明确参数，网络重发复用同一幂等键', async () => {
  const commands = [];
  const template = { templateId: 'temperature', templateVersion: '1', templateSha256: 'a'.repeat(64) };
  engineeringUIEnvironment({
    listEngineeringTemplates: async () => ({ data: [template] }),
    createEngineeringTask: async (command) => {
      commands.push(command);
      if (commands.length === 1) throw new Error('网络中断');
      return { data: { taskId: '5' } };
    }
  });
  const page = await mountEngineeringUI((await loadEngineeringUI('src/views/ai/engineering/task/index.vue')).default);
  await page.setup.retry({ taskId: '2', deviceId: '1', action: 'CREATE_PCB_DRAFT', state: 'FAILED' });
  await page.setup.submitRetry();
  assert.equal(commands.length, 0);
  Object.assign(page.setup.retryForm, { templateKey: page.setup.templateKey(template), boardWidthMm: '50.0000', boardHeightMm: '35.0', layers: 2 });
  await page.setup.submitRetry();
  await page.setup.submitRetry();
  assert.equal(commands.length, 2);
  assert.equal(commands[0].idempotencyKey, commands[1].idempotencyKey);
  assert.equal(commands[0].parameters.boardWidthMm, '50.0000');
  assert.equal(commands[0].templateSha256, template.templateSha256);
  page.unmount();
});

test('生命周期停止后，已排队的刷新不能重新启动请求', async () => {
  const { defineComponent, onMounted } = await import('vue');
  const { useEngineeringPolling } = await loadEngineeringUI('src/views/ai/engineering/useEngineeringPolling.ts');
  let reads = 0;
  const host = defineComponent({
    setup() {
      const polling = useEngineeringPolling(
        async () => {
          reads++;
          return false;
        },
        () => {}
      );
      onMounted(polling.stop);
      return {};
    }
  });
  const mounted = await mountEngineeringUI(host);
  assert.equal(reads, 0);
  mounted.unmount();
});

const reviewedTemplate = {
  templateId: 'temperature-humidity-demo',
  templateVersion: '1.0.0',
  templateSha256: 'a'.repeat(64),
  parameters: {
    boardWidthMm: { required: true, min: '40', max: '100', defaultValue: '50', enumValues: [] },
    boardHeightMm: { required: true, min: '30', max: '100', defaultValue: '35', enumValues: [] },
    layers: { required: true, min: null, max: null, defaultValue: 2, enumValues: [2] }
  },
  optionalModules: []
};
async function retryPage(template = reviewedTemplate) {
  const commands = [];
  engineeringUIEnvironment({
    listEngineeringTemplates: async () => ({ data: [template] }),
    createEngineeringTask: async (command) => {
      commands.push(command);
      return { data: { taskId: '5' } };
    }
  });
  const page = await mountEngineeringUI((await loadEngineeringUI('src/views/ai/engineering/task/index.vue')).default);
  await page.setup.retry({ taskId: '2', deviceId: '1', action: 'CREATE_PCB_DRAFT', state: 'FAILED' });
  page.setup.retryForm.templateKey = page.setup.templateKey(template);
  await page.flush();
  return { page, commands };
}
test('模板选择应用服务端十进制默认值与板层枚举', async () => {
  const { page } = await retryPage();
  try {
    assert.equal(page.setup.retryForm.boardWidthMm, '50');
    assert.equal(page.setup.retryForm.boardHeightMm, '35');
    assert.equal(page.setup.retryForm.layers, 2);
    assert.deepEqual(page.setup.retryLayers, [2]);
  } finally {
    page.unmount();
  }
});
test('新草稿不发送没有选择的optionalModules字段', async () => {
  const { page, commands } = await retryPage();
  try {
    Object.assign(page.setup.retryForm, { boardWidthMm: '50', boardHeightMm: '35', layers: 2 });
    await page.setup.submitRetry();
    assert.equal(commands.length, 1);
    assert.equal(Object.hasOwn(commands[0].parameters, 'optionalModules'), false);
  } finally {
    page.unmount();
  }
});
for (const [field, value] of [
  ['boardWidthMm', '39.9999'],
  ['boardWidthMm', '100.0001'],
  ['boardHeightMm', '29.9999'],
  ['boardHeightMm', '100.0001'],
  ['layers', 4]
])
  test(`模板约束在请求前拒绝${field}=${value}`, async () => {
    const { page, commands } = await retryPage();
    try {
      Object.assign(page.setup.retryForm, { boardWidthMm: '50', boardHeightMm: '35', layers: 2, [field]: value });
      await page.setup.submitRetry();
      assert.equal(commands.length, 0);
      assert.ok(page.setup.retryError);
    } finally {
      page.unmount();
    }
  });
test('模板尺寸边界按十进制文本比较，允许精确边界并保留输入精度', async () => {
  const template = structuredClone(reviewedTemplate);
  template.parameters.boardWidthMm.min = '50.00001';
  template.parameters.boardWidthMm.max = '50.0001';
  const { page, commands } = await retryPage(template);
  try {
    Object.assign(page.setup.retryForm, { boardWidthMm: '50.0000', boardHeightMm: '35', layers: 2 });
    await page.setup.submitRetry();
    assert.equal(commands.length, 0);
    page.setup.retryForm.boardWidthMm = '50.0001';
    await page.setup.submitRetry();
    assert.equal(commands.length, 1);
    assert.equal(commands[0].parameters.boardWidthMm, '50.0001');
  } finally {
    page.unmount();
  }
});
test('切换模板重置默认值与可选模块，不继承上个模板参数', async () => {
  const other = { ...structuredClone(reviewedTemplate), templateId: 'other', optionalModules: ['radio'] };
  other.parameters.boardWidthMm.defaultValue = '60.0';
  other.parameters.layers.defaultValue = 4;
  other.parameters.layers.enumValues = [4];
  const { page } = await retryPage();
  try {
    page.setup.retryForm.optionalModules = ['stale'];
    page.setup.retryTemplates.push(other);
    page.setup.retryForm.templateKey = page.setup.templateKey(other);
    await page.flush();
    assert.equal(page.setup.retryForm.boardWidthMm, '60.0');
    assert.equal(page.setup.retryForm.layers, 4);
    assert.deepEqual(page.setup.retryLayers, [4]);
    assert.deepEqual(page.setup.retryForm.optionalModules, []);
  } finally {
    page.unmount();
  }
});

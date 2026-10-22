import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { chat, components, nextTick } from '../ai-knowledge-mcp/chat-stream-harness.mjs';

const artifactPath = process.env.NEXUS_AI_ROUNDTRIP_ARTIFACT;
assert.ok(artifactPath, '必须由 test:ai:roundtrip 先执行 Java 夹具并提供本轮产物，不能跳过后端后报成功');
const artifact = JSON.parse(readFileSync(artifactPath, 'utf8'));
const scenarios = new Map(artifact.scenarios.map((scenario) => [scenario.name, scenario]));
const timeline = artifact.timeline.map((dtoJson) => {
  assert.equal(typeof dtoJson, 'string', '保留后端实际序列化的 SseMessageDto JSON');
  const dto = JSON.parse(dtoJson);
  assert.deepEqual(dto.userIds, [7]);
  assert.equal(typeof dto.message, 'string');
  return JSON.parse(dto.message);
});
const eventsFor = (scenario) => timeline.filter((event) => event.clientRequestId === scenario.request.clientRequestId);
const response = (scenario) => JSON.parse(scenario.http.bodyJson);

/** 固定测试随机端口，让真实发送函数生成夹具中的 nonce；后端事件及其关联字段保持原样。 */
async function startScenario(state, scenario) {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'crypto');
  const bytes = Buffer.from(scenario.request.clientRequestId.replaceAll('-', ''), 'hex');
  Object.defineProperty(globalThis, 'crypto', {
    configurable: true,
    value: {
      getRandomValues(target) {
        assert.equal(target.length, 16);
        target.set(bytes);
        return target;
      }
    }
  });
  try {
    const request = await state.start(scenario.request.content);
    assert.equal(request.data.clientRequestId, scenario.request.clientRequestId);
    assert.equal(request.data.content, scenario.request.content);
    return request;
  } finally {
    if (descriptor) Object.defineProperty(globalThis, 'crypto', descriptor);
    else delete globalThis.crypto;
  }
}

/** 只解析真实信封的 message 字段，然后直接调用组件生产处理函数。 */
function replay(state, events) {
  for (const detail of events) state.onAiSseMessage({ detail });
}

/** 回执也使用控制器生成的完整 R 正文，保留字符串雪花ID。 */
async function ack(request, scenario) {
  request.http.resolve(response(scenario));
  await request.done;
  await nextTick();
}

/** 成功结果必须对应真实门面交给合成存储的消息，而非预设的前端假ID。 */
function assertSuccess(state, scenario) {
  const persisted = scenario.persisted.find((message) => message.role === 'ASSISTANT');
  assert.ok(persisted);
  assert.equal(state.session.value, response(scenario).data);
  assert.equal(state.messages.value.at(-1).content, persisted.content);
  assert.equal(state.messages.value.at(-1).id, persisted.id);
  assert.ok(BigInt(persisted.id) > BigInt(Number.MAX_SAFE_INTEGER));
  assert.equal(state.loading.value, false);
  assert.equal(state.thinking.value, false);
}

const scenarioNames = ['denied-controller', 'denied-facade', 'ordinary-success', 'session-a', 'session-b', 'model-error'];

test('Java 产物包含普通用户、前置授权、异步并发、ACK 竞态、持久化ID与失败的完整场景', () => {
  assert.equal(artifact.formatVersion, 1);
  assert.equal(artifact.synthetic, true);
  assert.deepEqual([...scenarios.keys()], scenarioNames);
  assert.deepEqual(artifact.principal, {
    userId: '7',
    tenantId: '000007',
    permissions: ['ai:assistant:send'],
    superAdmin: false
  });
  assert.deepEqual(artifact.executor, { threads: 2, queueCapacity: 4, ordinaryCompletedBeforeAck: true });
  assert.equal(timeline.length, 15);
  const requestIds = new Set();
  for (const scenario of scenarios.values()) {
    assert.equal(scenario.http.status, 200, '业务授权结果由 R.code 表示');
    const events = eventsFor(scenario);
    if (scenario.name.startsWith('denied-')) {
      assert.equal(response(scenario).code, 403);
      assert.deepEqual(events, []);
      assert.deepEqual(scenario.persisted, []);
      continue;
    }
    assert.equal(response(scenario).code, 200);
    assert.equal(typeof response(scenario).data, 'string');
    const requestId = events[0].requestId;
    assert.match(requestId, /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i);
    requestIds.add(requestId);
    events.forEach((event, index) => {
      assert.equal(event.requestId, requestId);
      assert.equal(event.sessionId, response(scenario).data);
      assert.equal(event.sequence, index + 1);
      if (event.messageId !== undefined) assert.equal(typeof event.messageId, 'string');
    });
    assert.equal(events.filter((event) => ['AI_DONE', 'AI_ERROR'].includes(event.type)).length, 1);
    assert.equal(events.at(-1).type, scenario.name === 'model-error' ? 'AI_ERROR' : 'AI_DONE');
    for (const message of scenario.persisted) {
      assert.equal(message.tenantId, '000007');
      assert.equal(message.sessionId, response(scenario).data);
      assert.equal(typeof message.id, 'string');
    }
  }
  assert.equal(requestIds.size, 4, '两个会话与其他生成均有独立服务端执行ID');
});

test('成功、失败及迟到回调均清理两个复用工作线程与请求线程的身份和 SSE 关联', () => {
  assert.ok(artifact.contextCleanup, 'Java 必须导出实际完成的清理检查');
  assert.ok(artifact.contextCleanup.workerTasks > 0);
  assert.equal(artifact.contextCleanup.workerThreads, 2);
  assert.equal(artifact.contextCleanup.requestThread, true);
  assert.equal(artifact.contextCleanup.unscopedSseProbes, artifact.contextCleanup.workerTasks + 1);
});

for (const relative of components) {
  for (const earlyEvents of [true, false]) {
    test(`${relative}: 真实普通用户成功信封，${earlyEvents ? 'SSE 完成先于 HTTP ACK' : 'HTTP ACK 先于 SSE'}`, async () => {
      const scenario = scenarios.get('ordinary-success');
      const state = chat(relative, artifact.principal.permissions);
      const request = await startScenario(state, scenario);
      try {
        if (!earlyEvents) {
          await ack(request, scenario);
          assert.equal(state.loading.value, true, 'ACK 不得代替 SSE 终态');
        }
        const events = eventsFor(scenario);
        replay(state, events.slice(0, -1));
        assert.equal(state.loading.value, true, '正文到达后仍等待保存成功');
        assert.equal(state.messages.value.at(-1).id, undefined);
        replay(state, events.slice(-1));
        assertSuccess(state, scenario);
        if (earlyEvents) await ack(request, scenario);
        assertSuccess(state, scenario);
        const completed = JSON.stringify(state.messages.value);
        replay(state, events);
        assert.equal(JSON.stringify(state.messages.value), completed, '重播旧信封不能恢复加载或重复正文');
      } finally {
        state.unmount();
      }
    });
  }

  test(`${relative}: 两个真实异步会话交替广播时仅消费本轮正文和终态`, async () => {
    const fixtureA = scenarios.get('session-a');
    const fixtureB = scenarios.get('session-b');
    const states = [chat(relative, artifact.principal.permissions), chat(relative, artifact.principal.permissions)];
    const requests = [await startScenario(states[0], fixtureA), await startScenario(states[1], fixtureB)];
    try {
      await ack(requests[0], fixtureA);
      await ack(requests[1], fixtureB);
      const broadcasts = timeline.filter((event) =>
        [fixtureA, fixtureB].some((scenario) => event.clientRequestId === scenario.request.clientRequestId)
      );
      assert.deepEqual(
        broadcasts.filter((event) => event.type === 'AI_CONTENT_INCREMENT').map((event) => event.content),
        ['甲的', '乙的', '回答', '回答']
      );
      for (const detail of broadcasts) {
        for (const [index, state] of states.entries()) {
          const own = [fixtureA, fixtureB][index].request.clientRequestId === detail.clientRequestId;
          const before = JSON.stringify({ messages: state.messages.value, loading: state.loading.value, session: state.session.value });
          replay(state, [detail]);
          if (!own)
            assert.equal(JSON.stringify({ messages: state.messages.value, loading: state.loading.value, session: state.session.value }), before);
        }
      }
      assertSuccess(states[0], fixtureA);
      assertSuccess(states[1], fixtureB);
    } finally {
      states.forEach((state) => state.unmount());
    }
  });

  test(`${relative}: 切换新会话后，旧 SSE 与迟到 HTTP ACK 均不能覆盖当前结果`, async () => {
    const fixtureA = scenarios.get('session-a');
    const fixtureB = scenarios.get('session-b');
    const state = chat(relative, artifact.principal.permissions);
    const old = await startScenario(state, fixtureA);
    state.newChat();
    const current = await startScenario(state, fixtureB);
    try {
      replay(state, eventsFor(fixtureA));
      assert.deepEqual(
        state.messages.value.map((message) => message.content),
        [fixtureB.request.content]
      );
      assert.equal(state.loading.value, true);
      replay(state, eventsFor(fixtureB));
      await ack(current, fixtureB);
      await ack(old, fixtureA);
      assertSuccess(state, fixtureB);
    } finally {
      state.unmount();
    }
  });

  test(`${relative}: 真实模型失败终态停止加载，正文和错误不会伪造持久化消息ID`, async () => {
    const scenario = scenarios.get('model-error');
    const state = chat(relative, artifact.principal.permissions);
    const request = await startScenario(state, scenario);
    try {
      await ack(request, scenario);
      const events = eventsFor(scenario);
      replay(state, events.slice(0, -1));
      assert.equal(state.loading.value, true);
      replay(state, events.slice(-1));
      assert.equal(state.messages.value.at(-1).content, '[错误] AI 对话失败，请稍后重试。');
      assert.equal(state.loading.value, false);
      assert.equal(state.thinking.value, false);
      assert.ok(state.messages.value.every((message) => !message.id));
      assert.ok(!JSON.stringify(events).includes('synthetic-provider-failure'));
      assert.deepEqual(
        scenario.persisted.map((message) => message.role),
        ['USER']
      );
    } finally {
      state.unmount();
    }
  });

  for (const denied of ['denied-controller', 'denied-facade']) {
    test(`${relative}: ${denied} 前置拒绝回执由真实发送失败处理函数收尾`, async () => {
      const scenario = scenarios.get(denied);
      const state = chat(relative, artifact.principal.permissions);
      const request = await startScenario(state, scenario);
      try {
        request.http.reject(new Error(response(scenario).msg));
        await request.done;
        assert.equal(state.loading.value, false);
        assert.equal(state.thinking.value, false);
        assert.equal(state.session.value, null);
        assert.match(state.messages.value.at(-1).content, /^\[请求失败\]/);
      } finally {
        state.unmount();
      }
    });
  }
}

test('页面和抽屉同时接收同一份真实用户 SSE 时间线时互不串流', async () => {
  const fixtures = [scenarios.get('session-a'), scenarios.get('session-b')];
  const states = components.map((relative) => chat(relative, artifact.principal.permissions));
  const requests = [];
  for (const [index, state] of states.entries()) requests.push(await startScenario(state, fixtures[index]));
  try {
    for (const state of states) replay(state, timeline);
    for (const [index, state] of states.entries()) {
      await ack(requests[index], fixtures[index]);
      assertSuccess(state, fixtures[index]);
    }
  } finally {
    states.forEach((state) => state.unmount());
  }
});

test('普通发送权限用户在真实页面使用 Java 持久化消息ID反馈', async () => {
  const scenario = scenarios.get('ordinary-success');
  const state = chat(components[0], artifact.principal.permissions);
  const request = await startScenario(state, scenario);
  try {
    replay(state, eventsFor(scenario));
    await ack(request, scenario);
    assertSuccess(state, scenario);
    const message = state.messages.value.at(-1);
    await state.feedback(message, 'LIKE');
    assert.deepEqual(state.feedbacks, [{ id: scenario.persisted.at(-1).id, feedback: 'LIKE' }]);
    assert.equal(message.feedback, 'LIKE');
  } finally {
    state.unmount();
  }
});

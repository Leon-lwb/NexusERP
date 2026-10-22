import assert from 'node:assert/strict';
import { chat, components, loadStreamHelper, messageId, nextTick, serverA, serverB, sessionA, sessionB, test } from './chat-stream-harness.mjs';

async function started(relative = components[0]) {
  const state = chat(relative);
  const request = await state.start();
  state.event(request, 'AI_AGENT_THINKING', JSON.stringify({ agentName: 'ErpQueryAgent', message: '开始分析您的问题...' }));
  assert.equal(state.messages.value.at(-1).thinking, '开始分析您的问题...');
  return { state, request };
}
for (const type of ['AI_CONTENT_INCREMENT', 'AI_CONTENT', 'AI_DONE', 'AI_ERROR']) {
  test(`assistant ${type} clears the temporary agent status`, async () => {
    const { state, request } = await started();
    state.messages.value.at(-1).reasoning = '已有推理内容';
    state.event(request, type, type === 'AI_ERROR' ? '输出截断' : '工单结果', 2);
    assert.equal(state.messages.value.at(-1).thinking, undefined);
    assert.equal(state.messages.value.find((message) => message.reasoning)?.reasoning, '已有推理内容');
    assert.equal(state.thinking.value, false);
  });
}
for (const type of ['AI_DONE', 'AI_ERROR']) {
  test(`late progress cannot restore an active analysis label after ${type}`, async () => {
    const { state, request } = await started();
    state.event(request, 'AI_CONTENT', '完整回答', 2);
    state.event(request, type, '模型返回失败', 3);
    state.event(request, 'AI_AGENT_THINKING', JSON.stringify({ message: '开始分析您的问题...' }), 4);
    assert.ok(state.messages.value.every((message) => !message.thinking));
    assert.equal(state.loading.value, false);
  });
}
for (const relative of components) {
  test(`${relative} appends real increments and waits for the completion event`, async () => {
    const state = chat(relative);
    const request = await state.start();
    state.event(request, 'AI_THINKING');
    state.event(request, 'AI_CONTENT_INCREMENT', '第一部分', 2);
    state.event(request, 'AI_CONTENT_INCREMENT', '第二部分', 3);
    assert.equal(state.messages.value.at(-1).content, '第一部分第二部分');
    assert.equal(state.loading.value, true);
    state.event(request, 'AI_CONTENT', '第一部分第二部分', 4);
    assert.equal(state.loading.value, true, 'full content must not finish the request before persistence/DONE');
    state.event(request, 'AI_DONE', undefined, 5, { messageId });
    assert.equal(state.messages.value.at(-1).content, '第一部分第二部分');
    assert.equal(state.messages.value.at(-1).id, messageId);
    assert.equal(state.loading.value, false);
  });

  test(`${relative} sends a client nonce before HTTP and rejects session A content/DONE after moving to B`, async () => {
    const state = chat(relative);
    state.session.value = sessionA;
    const requestA = await state.start('A');
    state.newChat();
    state.session.value = sessionB;
    const requestB = await state.start('B');
    state.event(requestA, 'AI_CONTENT_INCREMENT', '旧内容', 1);
    state.event(requestA, 'AI_DONE', undefined, 2, { messageId });
    assert.deepEqual(
      state.messages.value.map((message) => message.content),
      ['B']
    );
    assert.equal(state.loading.value, true);
    assert.match(requestB.data.clientRequestId, /^[a-f0-9-]{36}$/i);
    assert.notEqual(requestB.data.clientRequestId, requestA.data.clientRequestId);
    state.event(requestB, 'AI_CONTENT_INCREMENT', 'B回答', 1, { sessionId: sessionB, requestId: serverB });
    assert.equal(state.messages.value.at(-1).content, 'B回答');
  });
}

for (const relative of components) {
  for (const earlyEvent of [false, true]) {
    test(`${relative} binds a new session with ${earlyEvent ? 'SSE before ACK' : 'ACK before SSE'}`, async () => {
      const state = chat(relative);
      const request = await state.start();
      if (earlyEvent) {
        state.event(request, 'AI_CONTENT_INCREMENT', '先到的回答');
        assert.equal(state.session.value, sessionA);
        state.event(request, 'AI_DONE', undefined, 2, { messageId });
      }
      request.http.resolve({ data: sessionA });
      await request.done;
      assert.equal(state.session.value, sessionA);
      if (!earlyEvent) {
        state.event(request, 'AI_CONTENT_INCREMENT', '先到的回答');
        state.event(request, 'AI_DONE', undefined, 2, { messageId });
      }
      assert.equal(state.messages.value.at(-1).id, messageId);
      assert.equal(state.loading.value, false);
      if (state.feedback) {
        await state.feedback(state.messages.value.at(-1), 'LIKE');
        assert.deepEqual(state.feedbacks, [{ id: messageId, feedback: 'LIKE' }]);
      }
    });
  }

  test(`${relative} drops missing metadata, conflicting bindings and nonascending sequences`, async () => {
    const state = chat(relative);
    const request = await state.start();
    state.onAiSseMessage({ detail: { type: 'AI_CONTENT', content: '未关联内容' } });
    state.event(request, 'AI_CONTENT_INCREMENT', '有效', 2);
    for (const [sequence, extra] of [
      [2, {}],
      [1, {}],
      [3, { requestId: serverB }],
      [3, { sessionId: sessionB }],
      [3, { sessionId: Number(sessionA) }],
      [3, { sessionId: '-1' }],
      [3, { messageId: Number(messageId) }],
      [0, {}],
      ['3', {}],
      [NaN, {}],
      [3, { clientRequestId: undefined }],
      [3, { requestId: '' }]
    ])
      state.event(request, 'AI_CONTENT_INCREMENT', '污染', sequence, extra);
    assert.equal(state.messages.value.at(-1).content, '有效');
    state.event(request, 'AI_CONTENT_INCREMENT', '后续', 3);
    assert.equal(state.messages.value.at(-1).content, '有效后续');
    state.event(request, 'AI_DONE', undefined, 4, { messageId });
    state.event(request, 'AI_CONTENT', '迟到完整内容', 5);
    state.event(request, 'AI_IMAGE', JSON.stringify({ url: 'late' }), 6);
    state.event(request, 'AI_ERROR', '迟到错误', 7);
    state.event(request, 'AI_DONE', undefined, 8, { messageId: sessionB });
    assert.equal(state.messages.value.at(-1).content, '有效后续');
    assert.equal(state.messages.value.at(-1).id, messageId);
    assert.equal(state.messages.value.length, 2);
  });

  test(`${relative} same-session new request ignores the preceding HTTP failure and DONE`, async () => {
    const state = chat(relative);
    state.session.value = sessionA;
    const first = await state.start('第一问');
    state.event(first, 'AI_CONTENT', '第一答');
    state.event(first, 'AI_DONE', undefined, 2, { messageId });
    const second = await state.start('第二问');
    assert.notEqual(first.data.clientRequestId, second.data.clientRequestId);
    first.http.reject(new Error('旧HTTP失败'));
    await first.done;
    state.event(first, 'AI_CONTENT_INCREMENT', '迟到内容', 3);
    state.event(first, 'AI_DONE', undefined, 4, { messageId });
    assert.equal(state.loading.value, true);
    assert.equal(state.messages.value.at(-1).content, '第二问');
    state.event(second, 'AI_CONTENT', '第二答', 1, { requestId: serverB });
    state.event(second, 'AI_DONE', undefined, 2, { requestId: serverB, messageId: sessionB });
    assert.equal(state.messages.value.at(-1).id, sessionB);
  });

  test(`${relative} terminal SSE cannot be overwritten by a late HTTP failure`, async () => {
    const state = chat(relative);
    const request = await state.start();
    state.event(request, 'AI_CONTENT', '已完成');
    state.event(request, 'AI_DONE', undefined, 2, { messageId });
    request.http.reject(new Error('迟到网络失败'));
    await request.done;
    assert.deepEqual(
      state.messages.value.map((message) => message.content),
      ['查询工单', '已完成']
    );
    assert.equal(state.messages.value.at(-1).id, messageId);
  });

  test(`${relative} rejects unsafe or mismatching ACKs without changing a bound session`, async () => {
    for (const sid of [Number(sessionA), '', '0', '-1', '1.5', {}, '9223372036854775808', sessionB]) {
      const state = chat(relative);
      const request = await state.start();
      state.event(request, 'AI_CONTENT', '已绑定回答');
      request.http.resolve({ data: sid });
      await request.done;
      assert.equal(state.session.value, sessionA);
      assert.equal(state.loading.value, false);
      assert.match(state.messages.value.at(-1).content, /^\[请求失败\].*回执/);
      const snapshot = JSON.stringify(state.messages.value);
      state.event(request, 'AI_CONTENT_INCREMENT', 'ACK错误后的迟到内容', 2);
      state.event(request, 'AI_DONE', undefined, 3, { messageId });
      assert.equal(JSON.stringify(state.messages.value), snapshot, 'malformed ACK must reset active stream');
    }
  });

  for (const action of ['newChat', 'clear', 'unmount', ...(relative.includes('Drawer') ? ['close', 'hide'] : [])]) {
    for (const failure of [false, true]) {
      test(`${relative} ${action} resets SSE and ignores late HTTP ${failure ? 'failure' : 'success'}`, async () => {
        const state = chat(relative);
        const request = await state.start();
        if (action === 'hide') {
          state.visible.value = false;
          await nextTick();
        } else state[action]();
        const snapshot = JSON.stringify(state.messages.value);
        const loading = state.loading.value;
        state.event(request, 'AI_CONTENT_INCREMENT', '迟到');
        state.event(request, 'AI_DONE', undefined, 2, { messageId });
        if (failure) request.http.reject(new Error('旧失败'));
        else request.http.resolve({ data: sessionA });
        await request.done;
        assert.equal(state.session.value, null);
        assert.equal(JSON.stringify(state.messages.value), snapshot);
        assert.equal(state.loading.value, loading);
      });
    }
  }

  test(`${relative} script send guard covers Enter and programmatic callers without send permission`, async () => {
    const state = chat(relative, ['ai:assistant:query']);
    await state.start();
    assert.equal(state.calls.length, 0);
    assert.equal(state.messages.value.length, 0);
    assert.equal(state.loading.value, false);
  });
}

test('page and drawer use different nonces even for the same session and cannot consume each other’s events', async () => {
  const page = chat(components[0]),
    drawer = chat(components[1]);
  page.session.value = drawer.session.value = sessionA;
  const pageRequest = await page.start('页面'),
    drawerRequest = await drawer.start('抽屉');
  assert.notEqual(pageRequest.data.clientRequestId, drawerRequest.data.clientRequestId);
  for (const [state, other] of [
    [page, drawerRequest],
    [drawer, pageRequest]
  ]) {
    state.event(other, 'AI_CONTENT', '另一个组件的内容');
    state.event(other, 'AI_DONE', undefined, 2, { messageId });
    assert.equal(state.messages.value.length, 1);
    assert.equal(state.loading.value, true);
  }
});

test('page switching A to B rejects old SSE, HTTP and history completions', async () => {
  const state = chat(components[0]);
  state.session.value = sessionA;
  const request = await state.start('A');
  const switching = state.switchSession(sessionB);
  state.event(request, 'AI_CONTENT', 'A的回答');
  state.event(request, 'AI_DONE', undefined, 2);
  request.http.resolve({ data: sessionA });
  await request.done;
  assert.equal(state.session.value, sessionB);
  assert.equal(state.messages.value.length, 0);
  assert.equal(state.loading.value, true);
  state.newChat();
  const fresh = await state.start('新会话');
  state.histories[0].resolve({ data: [{ id: messageId, role: 'ASSISTANT', content: 'B历史' }] });
  await switching;
  assert.equal(state.loading.value, true);
  assert.deepEqual(
    state.messages.value.map((message) => message.content),
    ['新会话']
  );
  state.event(fresh, 'AI_CONTENT', '新答');
});

test('query permission gates page history/list reads independently of send', async () => {
  const state = chat(components[0], ['ai:assistant:send']);
  await state.switchSession(sessionA);
  assert.equal(state.histories.length, 0);
  assert.equal(state.session.value, null);
  await state.start();
  assert.equal(state.calls.length, 1);
});

test('shared helper validates IDs, binding, sequence, terminal state and both ACK orders', () => {
  const { createAiChatStream, normalizeAiChatId } = loadStreamHelper();
  for (const value of ['1', sessionA, '9223372036854775807', 1, Number.MAX_SAFE_INTEGER]) {
    assert.equal(normalizeAiChatId(value), String(value));
  }
  for (const value of [
    '',
    '0',
    '-1',
    '01',
    '1.1',
    '1e2',
    ' 1',
    '1 ',
    '9223372036854775808',
    Number(sessionA),
    NaN,
    Infinity,
    -1,
    0,
    1.5,
    true,
    {},
    null,
    undefined
  ]) {
    assert.equal(normalizeAiChatId(value), null, String(value));
  }
  for (const early of [true, false]) {
    const state = createAiChatStream();
    const nonce = state.begin(null);
    assert.match(nonce, /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i);
    const event = { type: 'AI_CONTENT', content: '回答', clientRequestId: nonce, requestId: serverA, sessionId: sessionA, sequence: 1 };
    if (!early) assert.equal(state.ack(nonce, sessionA), true);
    assert.equal(state.accept(event), true);
    assert.equal(state.sessionId, sessionA);
    assert.equal(state.ack(nonce, sessionB), false);
    assert.equal(state.ack(nonce, sessionA), true);
    assert.equal(state.accept({ ...event, sequence: 1 }), false);
    assert.equal(state.accept({ ...event, sequence: 0 }), false);
    assert.equal(state.accept({ ...event, sequence: 2, requestId: serverB }), false);
    assert.equal(state.accept({ ...event, sequence: 2, sessionId: sessionB }), false);
    assert.equal(state.accept({ ...event, sequence: 2, type: 'AI_DONE', messageId }), true);
    assert.equal(state.accept({ ...event, sequence: 3 }), false);
    assert.equal(state.ack(nonce, sessionA), true, 'ACK may arrive after DONE');
    state.reset();
    assert.equal(state.isCurrent(nonce), false);
    assert.equal(state.ack(nonce, sessionA), false);
    assert.equal(state.accept(event), false);
  }
});

test('invalid first events never bind or consume a sequence; Flow/engineering keep separate contracts', () => {
  const { createAiChatStream } = loadStreamHelper();
  const state = createAiChatStream();
  const nonce = state.begin(null);
  const event = { type: 'AI_CONTENT', content: '回答', clientRequestId: nonce, requestId: serverA, sessionId: sessionA, sequence: 1 };
  for (const extra of [
    { clientRequestId: undefined },
    { clientRequestId: serverB },
    { requestId: undefined },
    { requestId: 'invalid' },
    { sessionId: undefined },
    { sessionId: Number(sessionA) },
    { sessionId: '0' },
    { messageId: 2 },
    { messageId: '' },
    { sequence: Number.MAX_SAFE_INTEGER + 1 },
    { sequence: 0 },
    { sequence: 1.5 },
    { sequence: '1' },
    { type: 'AI_FLOW_STATUS' },
    { type: 'AI_FLOW_NODE_START' },
    { type: 'AI_ENGINEERING_TASK' }
  ]) {
    assert.equal(state.accept({ ...event, ...extra }), false, JSON.stringify(extra));
    assert.equal(state.sessionId, null);
  }
  assert.equal(state.accept(event), true);
  const next = state.begin(sessionA);
  assert.notEqual(next, nonce);
  assert.equal(state.isCurrent(nonce), false);
  assert.equal(state.ack(nonce, sessionB), false);
  assert.equal(state.accept({ ...event, sequence: 2 }), false);
  assert.equal(state.accept({ ...event, clientRequestId: next, requestId: serverB, sequence: 1 }), true);
  assert.throws(() => state.begin(Number(sessionA)));
});

for (const relative of components) {
  test(`${relative} all ordinary chat types require correlation; an image is not terminal`, async () => {
    const state = chat(relative);
    const request = await state.start();
    const types = [
      'AI_THINKING',
      'AI_CONTENT_INCREMENT',
      'AI_CONTENT',
      'AI_DONE',
      'AI_ERROR',
      'AI_CHART',
      'AI_IMAGE',
      'AI_WEB_SEARCH',
      'AI_THINKING_TOKEN',
      'AI_TOOL_START',
      'AI_TOOL_END',
      'AI_AGENT_THINKING'
    ];
    for (const type of types) state.onAiSseMessage({ detail: { type, content: '{}' } });
    assert.equal(state.messages.value.length, 1);
    assert.equal(state.loading.value, true);
    state.event(request, 'AI_IMAGE', JSON.stringify({ url: '/image.png', prompt: '图片' }));
    assert.match(state.messages.value.at(-1).content, /image\.png/);
    assert.equal(state.loading.value, true);
    state.event(request, 'AI_DONE', undefined, 2, { messageId });
    assert.equal(state.messages.value.at(-1).id, messageId);
    assert.equal(state.loading.value, false);
  });

  test(`${relative} safely accepts legacy raw and wrapped HTTP ID formats`, async () => {
    for (const response of [sessionA, { data: sessionA }, 123, { data: 123 }]) {
      const state = chat(relative);
      const request = await state.start();
      request.http.resolve(response);
      await request.done;
      assert.equal(state.session.value, String(response?.data ?? response));
      assert.equal(state.loading.value, true);
    }
  });

  test(`${relative} current HTTP failure resets the stream before late server completion`, async () => {
    const state = chat(relative);
    const request = await state.start();
    state.event(request, 'AI_CONTENT_INCREMENT', '部分回答');
    request.http.reject(new Error('当前失败'));
    await request.done;
    const snapshot = JSON.stringify(state.messages.value);
    state.event(request, 'AI_DONE', undefined, 2, { messageId });
    state.event(request, 'AI_CONTENT', '迟到回答', 3);
    assert.equal(JSON.stringify(state.messages.value), snapshot);
    assert.match(state.messages.value.at(-1).content, /当前失败/);
    assert.equal(state.loading.value, false);
  });
}

for (const relative of components) {
  test(`${relative} AI_ERROR is terminal even when later content and DONE arrive`, async () => {
    const state = chat(relative);
    const request = await state.start();
    state.event(request, 'AI_CONTENT_INCREMENT', '部分回答');
    state.event(request, 'AI_ERROR', '模型失败', 2);
    const snapshot = JSON.stringify(state.messages.value);
    state.event(request, 'AI_CONTENT', '错误后的迟到回答', 3);
    state.event(request, 'AI_DONE', undefined, 4, { messageId });
    assert.equal(JSON.stringify(state.messages.value), snapshot);
    assert.equal(state.loading.value, false);
    assert.match(state.messages.value.at(-1).content, /模型失败/);
  });
}

const contentBearingTypes = [
  'AI_CONTENT_INCREMENT',
  'AI_CONTENT',
  'AI_THINKING_TOKEN',
  'AI_ERROR',
  'AI_TOOL_START',
  'AI_TOOL_END',
  'AI_AGENT_THINKING',
  'AI_WEB_SEARCH',
  'AI_IMAGE',
  'AI_CHART'
];
const malformedContents = [{}, { content: undefined }, { content: null }, { content: 7 }, { content: false }, { content: {} }, { content: [] }];

test('malformed content never binds helper identities, consumes sequence or terminates a request', () => {
  const { createAiChatStream } = loadStreamHelper();
  const state = createAiChatStream();
  const clientRequestId = state.begin(null);
  const metadata = { clientRequestId, requestId: serverA, sessionId: sessionA, sequence: 50 };
  for (const type of contentBearingTypes) {
    for (const payload of malformedContents) {
      assert.equal(state.accept({ ...metadata, type, ...payload }), false, `${type}: ${JSON.stringify(payload)}`);
      assert.equal(state.sessionId, null, 'rejected content cannot bind the session');
      assert.equal(state.isCurrent(clientRequestId), true, 'a malformed ERROR cannot end the current request');
    }
  }
  const valid = { ...metadata, requestId: serverB, sessionId: sessionB, sequence: 1, type: 'AI_CONTENT', content: '' };
  assert.equal(state.accept(valid), true, 'another server/session and a lower sequence must still be bindable');
  for (const payload of malformedContents) {
    const invalid = { ...valid, sequence: 50 };
    delete invalid.content;
    assert.equal(state.accept({ ...invalid, ...payload }), false);
  }
  assert.equal(state.accept({ ...valid, sequence: 2, content: '有效回答' }), true, 'malformed content cannot advance a bound sequence');
  for (const payload of [{}, { content: null }]) {
    const optional = createAiChatStream();
    const nonce = optional.begin(null);
    const event = { clientRequestId: nonce, requestId: serverA, sessionId: sessionA, ...payload };
    assert.equal(optional.accept({ ...event, type: 'AI_THINKING', sequence: 1 }), true);
    assert.equal(optional.accept({ ...event, type: 'AI_DONE', sequence: 2, messageId }), true);
  }
});

for (const relative of components) {
  test(`${relative} malformed content cannot bind identities or poison the next valid token sequence`, async () => {
    const state = chat(relative);
    const request = await state.start();
    const metadata = { clientRequestId: request.data.clientRequestId, requestId: serverA, sessionId: sessionA, sequence: 50 };
    for (const type of contentBearingTypes) {
      for (const payload of malformedContents) {
        state.onAiSseMessage({ detail: { ...metadata, type, ...payload } });
        assert.equal(state.session.value, null, `${type}: malformed first event must not bind`);
        assert.deepEqual(
          state.messages.value.map((message) => message.content),
          ['查询工单']
        );
        assert.equal(state.thinking.value, false);
        assert.equal(state.loading.value, true, 'a malformed ERROR cannot terminate the request');
      }
    }
    state.event(request, 'AI_CONTENT_INCREMENT', '有效', 1, { requestId: serverB, sessionId: sessionB });
    assert.equal(state.session.value, sessionB, 'valid first token must bind its own server/session');
    assert.equal(state.messages.value.at(-1).content, '有效');
    for (const type of ['AI_CONTENT_INCREMENT', 'AI_CONTENT', 'AI_THINKING_TOKEN', 'AI_ERROR']) {
      for (const payload of malformedContents) {
        state.onAiSseMessage({ detail: { ...metadata, requestId: serverB, sessionId: sessionB, type, ...payload } });
      }
    }
    state.event(request, 'AI_CONTENT_INCREMENT', '追加', 2, { requestId: serverB, sessionId: sessionB });
    assert.equal(state.messages.value.at(-1).content, '有效追加');
    assert.equal(state.messages.value.at(-1).reasoning, undefined);
    assert.equal(state.messages.value.length, 2);
    assert.equal(state.loading.value, true);
    state.event(request, 'AI_DONE', null, 3, { requestId: serverB, sessionId: sessionB, messageId });
    assert.equal(state.messages.value.at(-1).id, messageId);
    assert.equal(state.loading.value, false);
  });
}

for (const relative of components) {
  test(relative + ' keeps partial text while cancellation is pending and confirms one cancelled terminal', async () => {
    const state = chat(relative);
    const request = await state.start();
    state.event(request, 'AI_CONTENT_INCREMENT', '部分正文', 1);
    state.event(request, 'AI_CANCEL_REQUESTED', '', 2);
    assert.equal(state.loading.value, true);
    state.event(request, 'AI_CONTENT_INCREMENT', '迟到正文', 3);
    assert.equal(state.messages.value.at(-1).content, '部分正文');
    state.event(request, 'AI_CANCELLED', '', 4, { messageId });
    assert.equal(state.loading.value, false);
    assert.match(state.messages.value.at(-1).content, /部分正文[\s\S]*已取消/);
    state.event(request, 'AI_DONE', undefined, 5, { messageId });
    assert.equal(state.loading.value, false);
  });
}

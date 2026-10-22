import assert from 'node:assert/strict';
import { test } from 'node:test';
import { loadEngineeringModule, loadEngineeringUI, mountEngineeringUI, engineeringUIEnvironment, deferred } from './engineering-test-utils.mjs';
const e = await loadEngineeringModule('src/components/ai/engineering/engineering-events.ts');
const { createAiChatStream } = await loadEngineeringModule('src/utils/ai-chat-stream.ts');
const state = { sessionId: '9223372036854775807', requestId: 'req-current' };
const event = {
  type: 'AI_ENGINEERING_TASK',
  content: JSON.stringify({ ...state, taskId: '2000000000000000002', state: 'WAITING_DEVICE', stage: 'CHECKING' })
};

test('未主动选择设备不发送工程标识，同一次网络重试复用请求ID', () => {
  let serial = 0;
  const requests = new e.EngineeringChatRequests(() => 'request-' + ++serial);
  assert.deepEqual(requests.prepare('', 'message-1'), {});
  const first = requests.prepare('9223372036854775807', 'message-1');
  assert.deepEqual(first, { engineeringDeviceId: '9223372036854775807', engineeringRequestId: 'request-1' });
  assert.deepEqual(requests.prepare('9223372036854775807', 'message-1'), first);
  assert.notEqual(requests.prepare('9223372036854775807', 'message-2').engineeringRequestId, first.engineeringRequestId);
  requests.clear();
  assert.notEqual(requests.prepare('9223372036854775807', 'message-1').engineeringRequestId, first.engineeringRequestId);
});
test('只接受真实类型化工程事件，工具文字不能构造卡片', () => {
  assert.equal(e.parseEngineeringTaskEvent({ type: 'AI_CONTENT', content: '已生成PCB，成功' }), undefined);
  assert.equal(e.parseEngineeringTaskEvent({ type: 'AI_ENGINEERING_TASK', content: '{bad' }), undefined);
  assert.equal(
    e.parseEngineeringTaskEvent({
      type: 'AI_ENGINEERING_TASK',
      content: JSON.stringify({ ...state, taskId: 9223372036854775807, state: 'SUCCEEDED' })
    }),
    undefined
  );
  assert.equal(e.parseEngineeringTaskEvent(event).taskId, '2000000000000000002');
});
test('不同会话和旧请求不能改变当前任务卡片', () => {
  const parsed = e.parseEngineeringTaskEvent(event);
  assert.equal(e.acceptEngineeringTaskEvent(parsed, state), true);
  assert.equal(e.acceptEngineeringTaskEvent(parsed, { ...state, sessionId: '2' }), false);
  assert.equal(e.acceptEngineeringTaskEvent(parsed, { ...state, requestId: 'old' }), false);
  assert.equal(e.acceptEngineeringTaskEvent(parsed, { sessionId: null, requestId: 'req-current' }), false);
});
test('历史任务从服务端数据恢复并去重，忽略其他会话', () => {
  const task = { taskId: '2000000000000000002', sessionId: state.sessionId, state: 'PARTIAL_SUCCESS', revision: 1 };
  const restored = e.mergeEngineeringTasks([], [task, { ...task, revision: 2 }, { ...task, taskId: '3', sessionId: '2' }], state.sessionId);
  assert.equal(restored.length, 1);
  assert.equal(restored[0].revision, 2);
  assert.equal(restored[0].state, 'PARTIAL_SUCCESS');
});

test('实际任务卡的打开工程操作只提交受控OPEN_PROJECT命令', async () => {
  const commands = [];
  engineeringUIEnvironment({
    createEngineeringTask: async (command) => {
      commands.push(command);
      return { data: { taskId: '5' } };
    }
  });
  const card = await mountEngineeringUI((await loadEngineeringUI('src/components/ai/engineering/EngineeringTaskCard.vue')).default, {
    task: { taskId: '9223372036854775807', deviceId: '1', sessionId: '11', state: 'PARTIAL_SUCCESS', action: 'CREATE_PCB_DRAFT' }
  });
  await card.setup.openProject();
  assert.equal(commands[0].action, 'OPEN_PROJECT');
  assert.equal(commands[0].parentTaskId, '9223372036854775807');
  assert.equal(Object.hasOwn(commands[0], 'path'), false);
  card.unmount();
});
test('实际会话控制器拒绝旧会话迟到响应，任务可脱离聊天流恢复', async () => {
  const { defineComponent, ref } = await import('vue');
  const pending = deferred();
  engineeringUIEnvironment({
    listEngineeringTasks: ({ sessionId }) =>
      sessionId === '11' ? pending.promise : Promise.resolve({ rows: [{ taskId: '3', sessionId, state: 'SUCCEEDED', revision: 1 }], total: 1 })
  });
  const { useEngineeringChat } = await loadEngineeringUI('src/components/ai/engineering/useEngineeringChat.ts');
  const session = ref('11');
  let controller;
  const host = defineComponent({
    setup() {
      controller = useEngineeringChat(session);
      return {};
    }
  });
  const mounted = await mountEngineeringUI(host);
  session.value = '12';
  await mounted.flush();
  assert.equal(controller.tasks[0].taskId, '3');
  pending.resolve({ rows: [{ taskId: '2', sessionId: '11', state: 'RUNNING' }], total: 1 });
  await mounted.flush();
  assert.equal(controller.tasks[0].sessionId, '12');
  mounted.unmount();
});

test('任务修订号按整数版本排序，不按字符串字典序降级', () => {
  const task = { taskId: '2', sessionId: '11', revision: '10', state: 'SUCCEEDED' };
  const tasks = e.mergeEngineeringTasks([task], [{ ...task, revision: '9', state: 'RUNNING' }], '11');
  assert.equal(tasks[0].state, 'SUCCEEDED');
});
test('工程通知丢失时，AI_DONE仍通过任务接口恢复真实卡片', async () => {
  const { defineComponent, ref } = await import('vue');
  let reads = 0;
  engineeringUIEnvironment({
    listEngineeringTasks: async ({ sessionId }) => ({
      rows: ++reads > 1 ? [{ taskId: '2', sessionId, revision: 1, state: 'SUCCEEDED' }] : [],
      total: reads > 1 ? 1 : 0
    })
  });
  const { useEngineeringChat } = await loadEngineeringUI('src/components/ai/engineering/useEngineeringChat.ts');
  let controller;
  const host = defineComponent({
    setup() {
      controller = useEngineeringChat(ref('11'));
      return {};
    }
  });
  const mounted = await mountEngineeringUI(host);
  assert.equal(controller.tasks.length, 0);
  window.dispatchEvent(new CustomEvent('ai-sse-message', { detail: { type: 'AI_DONE' } }));
  await mounted.flush();
  assert.equal(controller.tasks[0]?.taskId, '2');
  mounted.unmount();
});

for (const relative of ['src/views/ai/assistant/index.vue', 'src/components/ai/AiChatDrawer.vue']) {
  for (const outcome of ['success', 'failure']) {
    test(`${relative} 切换会话后迟到的发送回执 ${outcome} 不能改变当前会话或工程请求`, async () => {
      const { readFile } = await import('node:fs/promises');
      const { parse } = await import('@vue/compiler-sfc');
      const ts = await import('typescript');
      const { descriptor } = parse(await readFile(new URL('../' + relative, import.meta.url), 'utf8'));
      const source = ts.createSourceFile(relative, descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
      const method = source.statements.find((node) => ts.isFunctionDeclaration(node) && node.name?.text === 'sendMessage');
      const code = ts.transpileModule(method.getText(source), { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
      const pending = deferred();
      const session = { value: '11' };
      const epoch = { value: 0 };
      const requests = [];
      let engineeringSent = 0;
      const context = {
        canSend: { value: true },
        managedRequestId: { value: '' },
        cancelStatus: { value: '' },
        chatRequestsEnabled: { value: false },
        chatStream: createAiChatStream(),
        inputText: { value: '生成工程草稿' },
        loading: { value: false },
        thinking: { value: false },
        messages: { value: [] },
        currentSessionId: session,
        sessionId: session,
        chatEpoch: epoch,
        engineering: {
          prepare: () => ({ engineeringDeviceId: '1', engineeringRequestId: 'req-a' }),
          sent: () => {
            engineeringSent++;
          }
        },
        prepareChatKnowledgeRequest: () => ({}),
        chatMode: { value: 'auto' },
        selectedKnowledgeId: { value: '' },
        knowledgeOptions: { value: [] },
        knowledgeLoading: { value: false },
        knowledgeLoadError: { value: '' },
        canListKnowledge: { value: true },
        proxy: { $modal: { msgWarning: () => {} } },
        nextTick: async () => {},
        scrollToBottom: () => {},
        sendAiMessage: (payload) => {
          requests.push(payload);
          return pending.promise;
        },
        selectedModel: { value: '' },
        enableThinking: { value: false },
        enableWebSearch: { value: false },
        selectedToolIds: { value: [] }
      };
      const send = new Function(...Object.keys(context), code + '\nreturn sendMessage;')(...Object.values(context));
      const request = send();
      await Promise.resolve();
      assert.equal(requests.length, 1, 'the real send function must reach HTTP before switching');
      assert.equal(requests[0].engineeringDeviceId, '1');
      assert.equal(requests[0].engineeringRequestId, 'req-a');
      assert.equal(requests[0].sessionId, '11');
      assert.match(requests[0].clientRequestId, /^[0-9a-f-]{36}$/i);
      session.value = '12';
      epoch.value++;
      context.chatStream.reset();
      context.messages.value = [{ role: 'assistant', content: 'current session B' }];
      context.loading.value = false;
      if (outcome === 'success') pending.resolve({ data: '11' });
      else pending.reject(new Error('late request failure'));
      await request;
      assert.equal(session.value, '12');
      assert.equal(engineeringSent, 0, 'a stale ACK cannot complete the current engineering request');
      assert.deepEqual(context.messages.value, [{ role: 'assistant', content: 'current session B' }]);
      assert.equal(context.loading.value, false);
    });
  }
}

test('实际控制器仅接受当前类型化回执，成功文字不能伪造工程状态', async () => {
  const { defineComponent, ref } = await import('vue');
  let taskReads = 0;
  let actual;
  engineeringUIEnvironment({
    listEngineeringDevices: async () => ({ data: [{ deviceId: '1', capabilities: ['CREATE_PCB_DRAFT'], online: true }] }),
    getEngineeringTask: async (taskId) => {
      taskReads++;
      actual = { taskId, sessionId: '11', deviceId: '1', revision: 2, state: 'PARTIAL_SUCCESS' };
      return { data: actual };
    },
    listEngineeringTasks: async () => ({ rows: actual ? [actual] : [], total: actual ? 1 : 0 })
  });
  const { useEngineeringChat } = await loadEngineeringUI('src/components/ai/engineering/useEngineeringChat.ts');
  let controller;
  const host = defineComponent({
    setup() {
      controller = useEngineeringChat(ref('11'));
      return {};
    }
  });
  const mounted = await mountEngineeringUI(host);
  controller.selectDevice('1');
  const fields = controller.prepare('生成工程');
  const emit = (type, content) => window.dispatchEvent(new CustomEvent('ai-sse-message', { detail: { type, content } }));
  emit('AI_CONTENT', '已经生成成功');
  emit('AI_ENGINEERING_TASK', JSON.stringify({ taskId: '2', sessionId: '11', requestId: 'old', state: 'SUCCEEDED' }));
  await mounted.flush();
  assert.equal(taskReads, 0);
  assert.equal(controller.tasks.length, 0);
  emit('AI_ENGINEERING_TASK', JSON.stringify({ taskId: '2', sessionId: '11', requestId: fields.engineeringRequestId, state: 'SUCCEEDED' }));
  await mounted.flush();
  assert.equal(taskReads, 1);
  assert.equal(controller.tasks[0].state, 'PARTIAL_SUCCESS');
  mounted.unmount();
});

test('actual task card notifies once on completion and does not notify restored history', async () => {
  engineeringUIEnvironment();
  const cardComponent = (await loadEngineeringUI('src/components/ai/engineering/EngineeringTaskCard.vue')).default;
  const messages = [];
  const task = { taskId: '2111024604854018050', deviceId: '1', action: 'CHECK_CONNECTION', state: 'RUNNING', revision: 1 };
  const options = { modal: { msgSuccess: (message) => messages.push(message) } };
  const card = await mountEngineeringUI(cardComponent, { task }, options);
  try {
    card.state.task = { ...task, state: 'SUCCEEDED', revision: 2, connectionDiagnostic: { connected: true, cliVersion: '4.1.60' } };
    await card.flush();
    assert.deepEqual(messages, ['连接检查通过']);
    card.state.task = { ...card.state.task, revision: 3 };
    await card.flush();
    assert.equal(messages.length, 1);
  } finally {
    card.unmount();
  }
  const restored = await mountEngineeringUI(cardComponent, { task: { ...task, state: 'SUCCEEDED' } }, options);
  restored.unmount();
  assert.equal(messages.length, 1);
});

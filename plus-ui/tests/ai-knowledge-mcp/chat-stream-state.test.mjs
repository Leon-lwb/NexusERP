import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import ts from 'typescript';
import { parse } from '@vue/compiler-sfc';

function handler(relative) {
  const { descriptor, errors } = parse(readFileSync(new URL('../../' + relative, import.meta.url), 'utf8'));
  assert.deepEqual(errors, []);
  const file = ts.createSourceFile(relative, descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const declarations = file.statements.filter(
    (node) => ts.isFunctionDeclaration(node) && (node.name.text === 'onAiSseMessage' || node.name.text === 'clearAssistantThinking')
  );
  assert.ok(declarations.some((node) => node.name.text === 'onAiSseMessage'));
  const code = ts.transpileModule(declarations.map((node) => node.getText(file)).join('\n'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022 }
  }).outputText;
  const messages = { value: [] };
  const thinking = { value: false };
  const loading = { value: true };
  const dispatch = new Function(
    'messages',
    'thinking',
    'loading',
    'nextTick',
    'scrollToBottom',
    'loadSessions',
    'mountEchartsContainers',
    code + '\nreturn onAiSseMessage;'
  )(
    messages,
    thinking,
    loading,
    (fn) => Promise.resolve().then(fn),
    () => {},
    () => {},
    () => {}
  );
  return { messages, thinking, loading, send: (type, content) => dispatch({ detail: { type, content } }) };
}
const assistant = () => handler('src/views/ai/assistant/index.vue');
const started = () => {
  const state = assistant();
  state.send('AI_AGENT_THINKING', JSON.stringify({ agentName: 'ErpQueryAgent', message: '开始分析您的问题...' }));
  assert.equal(state.messages.value[0].thinking, '开始分析您的问题...');
  return state;
};
for (const type of ['AI_CONTENT_INCREMENT', 'AI_CONTENT', 'AI_DONE', 'AI_ERROR']) {
  test(`assistant ${type} clears the temporary agent status`, () => {
    const state = started();
    state.messages.value[0].reasoning = '已有推理内容';
    state.send(type, type === 'AI_ERROR' ? '输出截断' : '工单结果');
    assert.equal(state.messages.value[0].thinking, undefined);
    assert.equal(state.messages.value[0].reasoning, '已有推理内容');
    assert.equal(state.thinking.value, false);
  });
}
test('late progress cannot restore an active analysis label after completion', () => {
  const state = started();
  state.send('AI_CONTENT', '完整回答');
  state.send('AI_DONE');
  state.send('AI_AGENT_THINKING', JSON.stringify({ message: '开始分析您的问题...' }));
  assert.equal(state.messages.value[0].thinking, undefined);
  assert.equal(state.messages.value[0].content, '完整回答');
  assert.equal(state.loading.value, false);
});
test('late progress cannot restore an active analysis label after failure', () => {
  const state = started();
  state.send('AI_ERROR', '模型返回失败');
  state.send('AI_AGENT_THINKING', JSON.stringify({ message: '开始分析您的问题...' }));
  assert.ok(state.messages.value.every((message) => !message.thinking));
  assert.match(state.messages.value.at(-1).content, /模型返回失败/);
});
for (const relative of ['src/views/ai/assistant/index.vue', 'src/components/ai/AiChatDrawer.vue']) {
  test(`${relative} appends real increments and waits for the completion event`, () => {
    const state = handler(relative);
    state.messages.value = [{ role: 'user', content: '查询工单' }];
    state.send('AI_THINKING');
    state.send('AI_CONTENT_INCREMENT', '第一部分');
    state.send('AI_CONTENT_INCREMENT', '第二部分');
    assert.equal(state.messages.value.at(-1).content, '第一部分第二部分');
    assert.equal(state.loading.value, true);
    state.send('AI_CONTENT', '第一部分第二部分');
    state.send('AI_DONE');
    assert.equal(state.messages.value.at(-1).content, '第一部分第二部分');
    assert.equal(state.loading.value, false);
  });
}

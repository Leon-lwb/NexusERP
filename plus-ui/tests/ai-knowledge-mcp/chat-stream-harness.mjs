import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
import ts from 'typescript';
import { parse } from '@vue/compiler-sfc';
import { computed, nextTick, ref, watch } from 'vue';

export { test, nextTick };
export const components = ['src/views/ai/assistant/index.vue', 'src/components/ai/AiChatDrawer.vue'];
export const sessionA = '2100000000000000101';
export const sessionB = '2100000000000000102';
export const messageId = '2100000000000000201';
export const serverA = '11111111-1111-4111-8111-111111111111';
export const serverB = '22222222-2222-4222-8222-222222222222';
const uiFile = (relative) => new URL('../../' + relative, import.meta.url);
const transpile = (source) =>
  ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
  }).outputText;

export function loadStreamHelper() {
  const exports = {};
  new Function('exports', transpile(readFileSync(uiFile('src/utils/ai-chat-stream.ts'), 'utf8')))(exports);
  return exports;
}

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

// Execute production state declarations, send/SSE/session functions and disposal callback.
// Only HTTP, DOM work and the independently tested engineering controller are substituted.
export function chat(relative, permissions = ['ai:assistant:send', 'ai:assistant:query']) {
  const { descriptor, errors } = parse(readFileSync(uiFile(relative), 'utf8'));
  assert.deepEqual(errors, []);
  const file = ts.createSourceFile(relative, descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const states = new Set([
    'visible',
    'inputText',
    'messages',
    'thinking',
    'loading',
    'sessionLoading',
    'sessions',
    'currentSessionId',
    'sessionId',
    'chatEpoch',
    'managedRequestId',
    'cancelStatus',
    'chatStream',
    'canSend',
    'canQuery',
    'engineering',
    'chatMode',
    'selectedKnowledgeId',
    'knowledgeOptions',
    'knowledgeLoading',
    'knowledgeLoadError',
    'canListKnowledge',
    'knowledgeRequestVersion',
    'selectedModel',
    'enableThinking',
    'enableWebSearch',
    'selectedToolIds',
    'echartsResizeObservers',
    'echartsInstances',
    'mountedCharts'
  ]);
  const functions = new Set([
    'onAiSseMessage',
    'clearAssistantThinking',
    'sendMessage',
    'onCancelState',
    'switchSession',
    'newSession',
    'newChat',
    'clearMessages',
    'handleClose',
    'open',
    'loadSessions',
    'handleFeedback'
  ]);
  const selected = file.statements.filter(
    (node) =>
      (ts.isVariableStatement(node) && node.declarationList.declarations.some((decl) => states.has(decl.name.getText(file)))) ||
      (ts.isFunctionDeclaration(node) && functions.has(node.name.text)) ||
      (ts.isExpressionStatement(node) &&
        ts.isCallExpression(node.expression) &&
        ['onUnmounted', 'watch'].includes(node.expression.expression.getText(file)) &&
        (node.expression.expression.getText(file) === 'onUnmounted' || node.expression.arguments[0]?.getText(file) === 'visible'))
  );
  assert.ok(selected.some((node) => ts.isFunctionDeclaration(node) && node.name.text === 'sendMessage'));
  const calls = [],
    requests = [],
    histories = [],
    disposals = [],
    feedbacks = [];
  const engineering = { prepare: () => ({}), sent() {} };
  const streamHelper = existsSync(uiFile('src/utils/ai-chat-stream.ts')) ? loadStreamHelper() : {};
  const deps = {
    ref,
    computed,
    nextTick,
    watch,
    createAiChatStream: streamHelper.createAiChatStream,
    normalizeAiChatId: streamHelper.normalizeAiChatId,
    checkPermi: (required) => required.some((permission) => permissions.includes(permission)),
    useEngineeringChat: () => engineering,
    prepareChatKnowledgeRequest: () => ({}),
    sendAiMessage: (data) => {
      calls.push(data);
      const request = deferred();
      requests.push(request);
      return request.promise;
    },
    getChatHistory: () => {
      const request = deferred();
      histories.push(request);
      return request.promise;
    },
    getChatSessions: async () => ({ data: [] }),
    messageFeedback: async (id, feedback) => {
      feedbacks.push({ id, feedback });
    },
    scrollToBottom() {},
    mountEchartsContainers() {},
    onUnmounted: (fn) => disposals.push(fn),
    proxy: { $modal: { msgWarning() {}, msgError() {}, msgInfo() {} } },
    window: { removeEventListener() {} },
    onGlobalClick() {}
  };
  const code = transpile(selected.map((node) => node.getText(file)).join('\n'));
  const api = new Function(
    ...Object.keys(deps),
    code +
      `
    return { inputText, messages, thinking, loading, chatEpoch, sendMessage, onAiSseMessage,
      session: typeof currentSessionId !== 'undefined' ? currentSessionId : sessionId,
      newChat: typeof newSession !== 'undefined' ? newSession : newChat,
      clear: typeof clearMessages !== 'undefined' ? clearMessages : newChat,
      switchSession: typeof switchSession !== 'undefined' ? switchSession : undefined,
      close: typeof handleClose !== 'undefined' ? handleClose : undefined,
      visible: typeof visible !== 'undefined' ? visible : undefined,
      feedback: typeof handleFeedback !== 'undefined' ? handleFeedback : undefined,
      loadSessions: typeof loadSessions !== 'undefined' ? loadSessions : undefined };
  `
  )(...Object.values(deps));
  if (api.visible) api.visible.value = true;
  return Object.assign(api, {
    calls,
    requests,
    histories,
    feedbacks,
    engineering,
    async start(text = '查询工单') {
      api.inputText.value = text;
      const done = api.sendMessage();
      await nextTick();
      return { done, data: calls.at(-1), http: requests.at(-1) };
    },
    event(request, type, content, sequence = 1, extra = {}) {
      api.onAiSseMessage({
        detail: { type, content, requestId: serverA, clientRequestId: request.data?.clientRequestId, sessionId: sessionA, sequence, ...extra }
      });
    },
    unmount() {
      disposals.forEach((fn) => fn());
    }
  });
}

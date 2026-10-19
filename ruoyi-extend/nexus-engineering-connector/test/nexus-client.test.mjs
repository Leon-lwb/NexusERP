import test from 'node:test';
import assert from 'node:assert/strict';
import { NexusClient } from '../dist/nexus-client.js';
/** HTTP错误、R包裹和重定向作为边界，不允许错误正文泄漏到日志。 */
test('请求固定服务地址并验证HTTP及R结果，不跟随重定向', async () => {
  const calls = [];
  const client = new NexusClient('https://nexus.test', async (url, init) => {
    calls.push({ url, init });
    return new Response(JSON.stringify({ code: 200, data: { taskId: '9223372036854775807' } }), { status: 200 });
  });
  client.setAccessToken('synthetic-token');
  const r = await client.request('/connector/claim', {});
  assert.equal(r.taskId, '9223372036854775807');
  assert.equal(calls[0].init.redirect, 'error');
  assert.equal(calls[0].init.headers['X-Nexus-Device-Token'], 'synthetic-token');
  await assert.rejects(client.request('https://evil.test', {}), /路径/);
  const bad = new NexusClient('https://nexus.test', async () => new Response('secret', { status: 401 }));
  await assert.rejects(bad.request('/connector/claim', {}), (e) => e.status === 401 && !e.message.includes('secret'));
});

/** 调用者的取消信号与默认请求超时必须同时生效。 */
test('即使传入运行signal仍保留每次请求的超时', async () => {
  const abort = new AbortController();
  let seen;
  const client = new NexusClient('https://nexus.test', async (url, init) => {
    seen = init.signal;
    return new Response(JSON.stringify({ code: 200, data: null }));
  });
  await client.request('/connector/claim', {}, abort.signal);
  assert.notEqual(seen, abort.signal);
  abort.abort();
  assert.equal(seen.aborted, true);
});
/** 网络请求和响应正文都受同一超时约束，错误码只能来自受限机器字段。 */
test('保留安全工程错误码以便409恢复，不回显错误正文', async () => {
  const client = new NexusClient(
    'https://nexus.test',
    async () =>
      new Response(JSON.stringify({ code: 409, msg: 'private payload', data: { errorCode: 'STALE_REVISION', traceId: 'trace-1' } }), { status: 409 })
  );
  await assert.rejects(
    client.request('/connector/tasks/1/renew', {}),
    (e) => e.status === 409 && e.code === 'STALE_REVISION' && !e.message.includes('private payload')
  );
});

/** 小时限验证真实超时行为，不能只断言signal对象不同。 */
test('外部signal未取消时，挂起请求仍被自身超时中止', async () => {
  const keepAlive = setTimeout(() => {}, 1000);
  try {
    const client = new NexusClient(
      'https://nexus.test',
      async (url, init) =>
        new Promise((resolve, reject) => {
          init.signal.addEventListener('abort', () => reject(init.signal.reason), { once: true });
        }),
      { requestTimeoutMs: 20 }
    );
    await assert.rejects(client.request('/connector/claim', {}, new AbortController().signal), (e) => e.name === 'TimeoutError');
  } finally {
    clearTimeout(keepAlive);
  }
});
/** 响应头已到达不代表请求结束，读取正文也必须受请求时限约束。 */
test('响应正文挂起也会超时，不能阻塞修订号队列', async () => {
  const keepAlive = setTimeout(() => {}, 1000);
  try {
    const client = new NexusClient('https://nexus.test', async () => ({ ok: true, status: 200, json: () => new Promise(() => {}) }), {
      requestTimeoutMs: 20
    });
    await assert.rejects(client.request('/connector/tasks/1', undefined, new AbortController().signal), (e) => e.name === 'TimeoutError');
  } finally {
    clearTimeout(keepAlive);
  }
});

/** 站点根地址可能返回SPA；探测代理前缀时不能携带配对码或设备凭证。 */
test('站点返回HTML时识别prod-api，凭证只发送给确认后的接口', async () => {
  const calls = [];
  const client = new NexusClient('http://nexus.test', async (url, init) => {
    calls.push({url, init});
    if (url === 'http://nexus.test/prod-api/ai/engineering/connector/pair') {
      const data = init.method === 'GET' ? {code:405} : {code:200,data:{paired:true}};
      return new Response(JSON.stringify(data), {headers:{'Content-Type':'application/json'}});
    }
    return new Response('<!doctype html><html>frontend</html>', {headers:{'Content-Type':'text/html'}});
  }, {discoverApi:true});
  client.setAccessToken('synthetic-device-token');
  assert.deepEqual(await client.request('/connector/pair', {pairingCode:'synthetic-test-code'}), {paired:true});
  const probes = calls.filter(call => call.init.method === 'GET');
  assert.equal(probes.length, 2);
  for (const {init} of probes) {
    assert.equal(init.body, undefined);
    assert.equal(new Headers(init.headers).has('X-Nexus-Device-Token'), false);
  }
  assert.equal(calls.at(-1).url, 'http://nexus.test/prod-api/ai/engineering/connector/pair');
  assert.equal(calls.at(-1).init.method, 'POST');
});

/** 前缀探测也须服从父请求取消和超时，不阻塞桌面停止。 */
test('接口探测正文挂起仍会被请求时限中止', async () => {
  const keepAlive = setTimeout(() => {}, 1000);
  try {
    const client = new NexusClient('http://nexus.test', async () => ({
      status:200, headers:new Headers({'Content-Type':'application/json'}), json:()=>new Promise(()=>{})
    }), {discoverApi:true,requestTimeoutMs:20});
    await assert.rejects(client.request('/connector/pair',{pairingCode:'synthetic-only'}), error=>error.name==='TimeoutError');
  } finally {clearTimeout(keepAlive);}
});

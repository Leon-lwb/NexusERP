import test from 'node:test';
import assert from 'node:assert/strict';

const envelope = (code) => new Response(JSON.stringify({code}), {headers:{'Content-Type':'application/json'}});
const html = () => new Response('<html>frontend</html>', {headers:{'Content-Type':'text/html'}});
async function resolve(base, fetcher) {
  const {resolveEngineeringApiBase} = await import('../dist/server-endpoint.js');
  return resolveEngineeringApiBase(base, fetcher, new AbortController().signal);
}

/** 固定只读接口探测不创建配对，也不发送认证头。 */
test('直连后端和显式prod-api不重复添加代理前缀', async () => {
  for (const base of ['http://nexus.test:8080','http://nexus.test/prod-api']) {
    let calls = 0;
    assert.equal(await resolve(base, async (_url, init) => {
      calls++; assert.equal(init.method,'GET'); assert.equal(init.redirect,'manual');
      return envelope(405);
    }), base);
    assert.equal(calls, 1);
  }
});

test('开发前端识别dev-api并保持同一站点', async () => {
  const calls = [];
  assert.equal(await resolve('http://nexus.test', async (url) => {
    calls.push(url); return url.includes('/dev-api/') ? envelope(405) : html();
  }), 'http://nexus.test/dev-api');
  assert.equal(calls.length, 3);
  assert.ok(calls.every(url => new URL(url).origin === 'http://nexus.test'));
});

test('工程设备网页地址归一化为站点根或挂载前缀', async () => {
  const calls = [];
  assert.equal(await resolve('http://nexus.test/app/ai/chat/engineering/device', async (url) => {
    calls.push(url); return url.includes('/app/prod-api/') ? envelope(405) : html();
  }), 'http://nexus.test/app/prod-api');
  assert.equal(calls[0], 'http://nexus.test/app/ai/engineering/connector/pair');
});

test('工程接口缺失与网页地址错误分别返回明确机器码', async () => {
  await assert.rejects(resolve('http://nexus.test:8080', async () => envelope(404)), error => error.code === 'ENGINEERING_DISABLED');
  await assert.rejects(resolve('http://nexus.test', async () => html()), error => error.code === 'API_NOT_FOUND');
});

test('探测不会跟随重定向到其他主机', async () => {
  const calls = [];
  await assert.rejects(resolve('http://nexus.test', async (url, init) => {
    calls.push(url); assert.equal(init.redirect,'manual');
    return new Response(null,{status:302,headers:{Location:'http://other.test/private'}});
  }), error => error.code === 'API_REDIRECTED');
  assert.ok(calls.every(url => new URL(url).hostname === 'nexus.test'));
});

test('根路径被401或403保护时继续识别有效prod-api', async () => {
  for (const code of [401,403]) {
    const calls=[];
    assert.equal(await resolve('http://nexus.test', async url=>{
      calls.push(url); return url.includes('/prod-api/') ? envelope(405) : envelope(code);
    }), 'http://nexus.test/prod-api');
    assert.equal(calls.length,2);
  }
});

test('代理502保留后端不可用原因，不误报网页前缀', async () => {
  await assert.rejects(resolve('http://nexus.test',async url=>url.includes('/prod-api/') ? new Response('<html>bad gateway</html>',{status:502,headers:{'Content-Type':'text/html'}}) : html()), error=>error.code==='API_UNAVAILABLE' && error.status===502);
});

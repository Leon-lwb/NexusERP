import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { createServer as createHttpServer } from 'node:http';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fixture } from './api.mjs';
import { businessMock } from './business-api.mjs';

/** 测试路径由当前文件定位，新检出无需历史忽略夹具。 */
export const uiRoot = fileURLToPath(new URL('../../', import.meta.url));
const harnessRoot = fileURLToPath(new URL('./', import.meta.url));
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64');

/** 对齐真实资源编码和字符串ID规则，拒绝数值强转及精度截断。 */
function validId(value) {
  return typeof value === 'string' && /^[1-9][0-9]{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n;
}

/** 返回与服务端一致的错误信封，不把失败伪装为成功数据。 */
function json(response, status, value) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  response.end(JSON.stringify(value));
}

/** 创建只监听动态回环端口、没有代理和环境文件的Vite及严格Mock。 */
export async function startServer(runDirectory) {
  const failures = [];
  const requests = [];
  const business = businessMock(validId);
  const runs = business.runs;
  const assets = [
    {
      id: fixture.assetId,
      tenant: fixture.tenant,
      code: fixture.assetCode,
      title: '合成测试图片',
      json: '{}',
      revision: 1,
      publishedVersion: fixture.assetVersionId,
      status: 'PUBLISHED',
      owner: fixture.userId
    },
    {
      id: '9223372036854775802',
      tenant: fixture.tenant,
      code: 'SYNTHETIC_DRAFT',
      title: '合成未发布图片',
      json: '{}',
      revision: 1,
      publishedVersion: null,
      status: 'DRAFT',
      owner: fixture.userId
    }
  ];
  for (const asset of assets) {
    assert.match(asset.code, /^[A-Z][A-Z0-9_]{0,63}$/);
    assert.ok(validId(asset.id));
    assert.ok(asset.publishedVersion === null || validId(asset.publishedVersion));
  }

  /** 合成接口仅实现当前场景所需契约，未知接口直接记为回归失败。 */
  async function mock(request, response, next) {
    const path = new URL(request.url, 'http://127.0.0.1').pathname;
    if (!path.startsWith('/__report_mock/')) {
      if (/^\/(?:dev-api|prod-api|report)(?:\/|$)/.test(path)) {
        failures.push('检测到未隔离的后端请求：' + path);
        return json(response, 502, { code: 502, msg: '禁止连接真实后端' });
      }
      return next();
    }
    const entry = { method: request.method, path, status: 0 };
    requests.push(entry);
    /** 为每个实际请求记录响应状态，供驱动核对调用次数。 */
    const reply = (status, body) => {
      entry.status = status;
      json(response, status, body);
    };
    const permissions = String(request.headers['x-report-test-permissions'] || '').split(',');
    if (request.headers['x-report-test-user'] !== fixture.userId || request.headers['x-report-test-tenant'] !== fixture.tenant)
      return reply(403, { code: 403, msg: '合成会话没有资源访问权限', errorCode: 'SCOPE_DENIED' });
    const route = path.slice('/__report_mock'.length);
    if (await business.handle({ request, response, route, permissions, reply, entry })) return;
    if (route === '/report/assets' && request.method === 'GET') {
      if (!permissions.includes('report:asset:list')) return reply(403, { code: 403, msg: '没有图片目录权限', errorCode: 'SCOPE_DENIED' });
      return reply(200, { code: 200, data: assets });
    }
    const image = /^\/report\/assets\/versions\/([^/]+)\/(file|metadata)$/.exec(route);
    if (image && request.method === 'GET') {
      if (!permissions.includes('report:runtime:preview')) return reply(403, { code: 403, msg: '没有图片预览权限', errorCode: 'SCOPE_DENIED' });
      if (!validId(image[1])) return reply(400, { code: 400, msg: '图片版本必须是完整字符串ID', errorCode: 'INVALID_REQUEST' });
      if (image[1] !== fixture.assetVersionId) return reply(404, { code: 404, msg: '图片版本不可访问', errorCode: 'NOT_FOUND' });
      if (image[2] === 'metadata')
        return reply(200, {
          code: 200,
          data: { versionId: fixture.assetVersionId, resourceId: fixture.assetId, name: '合成测试图片', width: 1, height: 1 }
        });
      entry.status = 200;
      response.writeHead(200, { 'Content-Type': 'image/png', 'Cache-Control': 'no-store, private' });
      return response.end(png);
    }
    if (route === '/report/definitions' && request.method === 'POST') {
      const required = 'report:definition:add';
      if (!permissions.includes(required)) return reply(403, { code: 403, msg: '没有报表操作权限', errorCode: 'SCOPE_DENIED' });
      const chunks = [];
      for await (const chunk of request) {
        chunks.push(chunk);
        if (chunks.reduce((size, value) => size + value.length, 0) > 65536)
          return reply(400, { code: 400, msg: '合成请求过大', errorCode: 'INVALID_REQUEST' });
      }
      let body;
      try {
        body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      } catch {
        return reply(400, { code: 400, msg: '请求不是有效JSON', errorCode: 'INVALID_REQUEST' });
      }
      if (!body || typeof body !== 'object' || Array.isArray(body)) return reply(400, { code: 400, msg: '无效请求对象' });
      if (route === '/report/definitions') {
        const dsl = body.dsl;
        if (
          !dsl ||
          typeof dsl.code !== 'string' ||
          !/^[A-Z][A-Z0-9_]{0,63}$/.test(dsl.code) ||
          !validId(dsl.datasetVersionId) ||
          dsl.datasetVersionId !== fixture.datasetVersionId
        )
          return reply(400, { code: 400, msg: '资源code或数据集版本无效', errorCode: 'INVALID_REQUEST' });
        return reply(200, { code: 200, data: { id: fixture.reportId, code: dsl.code, publishedVersion: null, status: 'DRAFT' } });
      }
    }
    failures.push('未实现的合成接口：' + request.method + ' ' + route);
    return reply(404, { code: 404, msg: '隔离Mock未实现此接口' });
  }

  const httpServer = createHttpServer();
  const server = await createServer({
    configFile: false,
    envFile: false,
    root: harnessRoot,
    publicDir: false,
    cacheDir: resolve(runDirectory, 'vite-cache'),
    logLevel: 'error',
    plugins: [
      vue(),
      {
        name: 'isolated-report-mock',
        configureServer(vite) {
          vite.middlewares.use((request, response, next) => {
            mock(request, response, next).catch((error) => {
              failures.push(error.message);
              if (!response.headersSent) json(response, 500, { code: 500, msg: '合成接口内部异常' });
              else response.end();
            });
          });
        }
      }
    ],
    resolve: {
      alias: [
        { find: '@/utils/request', replacement: resolve(harnessRoot, 'api.mjs') },
        { find: '@/store/modules/user', replacement: resolve(harnessRoot, 'user.mjs') },
        { find: '@', replacement: resolve(uiRoot, 'src') }
      ]
    },
    server: { middlewareMode: true, host: '127.0.0.1', hmr: { server: httpServer }, watch: null, open: false, fs: { allow: [uiRoot] } }
  });
  /** Vite使用中间件模式，HTTP端口由操作系统分配，绝不占用默认5173或24678。 */
  const owned = {
    httpServer,
    async close() {
      await server.close();
      httpServer.closeAllConnections();
      if (httpServer.listening) await new Promise((resolve, reject) => httpServer.close((error) => (error ? reject(error) : resolve())));
    }
  };
  try {
    httpServer.on('request', server.middlewares);
    await new Promise((resolve, reject) => {
      httpServer.once('error', reject);
      httpServer.listen(0, '127.0.0.1', resolve);
    });
    const address = httpServer.address();
    assert.ok(address && typeof address !== 'string' && address.address === '127.0.0.1');
    return { server: owned, origin: 'http://127.0.0.1:' + address.port, failures, requests, runs, business };
  } catch (error) {
    await owned.close();
    throw error;
  }
}

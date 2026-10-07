import { createServer } from 'node:http';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { parseArgs } from 'node:util';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { z } from 'zod';
import { createDocumentStore, DocumentError, DOCUMENT_NAMES } from './document-store.mjs';

const SERVICE_NAME = '制造运营资料服务';
const SERVICE_VERSION = '1.0.0';
const HTTP_HOST = '127.0.0.1';
const READ_ONLY = Object.freeze({
  readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false,
});
const log = (message) => process.stderr.write(`[${SERVICE_NAME}] ${message}\n`);

function failedTool(error) {
  const failure = error instanceof DocumentError
    ? { code: error.code, message: error.message }
    : { code: 'DOCUMENT_READ_FAILED', message: '无法读取正式资料，请检查文件权限及状态。' };
  return {
    isError: true,
    content: [{ type: 'text', text: failure.message }],
    structuredContent: { error: failure },
  };
}

// 两种传输完全共享工具注册及资料层，没有按模式扩展权限的分支。
export function createMcpServer({ documentStore = createDocumentStore() } = {}) {
  const server = new McpServer({ name: SERVICE_NAME, title: SERVICE_NAME, version: SERVICE_VERSION }, {
    instructions: '只读访问本仓库三份正式制造运营资料。资料缺失必须明确报错；不查询实时业务数据、不写文件、不执行 SQL、不代理网络。',
  });
  server.server.onerror = () => log('MCP 请求处理失败。');
  server.registerTool('list_documents', {
    title: '列出正式业务资料',
    description: '列出三份正式资料的文件名、标题和可用状态；缺文件不生成正文。',
    inputSchema: z.strictObject({}),
    annotations: READ_ONLY,
  }, async () => {
    try {
      const listing = { documents: await documentStore.listDocuments() };
      return { isError: false, content: [{ type: 'text', text: JSON.stringify(listing, null, 2) }], structuredContent: listing };
    } catch (error) {
      return failedTool(error);
    }
  });
  server.registerTool('read_document', {
    title: '读取正式业务资料',
    description: '按完整正式文件名原样读取 UTF-8 Markdown；只接受清单内名称，不接受路径或网址。',
    inputSchema: z.strictObject({
      document_name: z.string().describe(`正式文件名，只允许：${DOCUMENT_NAMES.join('、')}`),
    }),
    annotations: READ_ONLY,
  }, async ({ document_name }) => {
    try {
      const document = await documentStore.readDocument(document_name);
      return { isError: false, content: [{ type: 'text', text: document.content }], structuredContent: document };
    } catch (error) {
      return failedTool(error);
    }
  });
  return server;
}

// Node 只负责 HTTP 端点路由，JSON-RPC、握手和 stateless 语义均交给官方 SDK。
export function createHttpServer({ documentStore = createDocumentStore() } = {}) {
  const httpServer = createServer(async (request, response) => {
    // URL 只能取本服务实际监听地址，不信任 Host、查询参数或任何转发头。
    const binding = httpServer.address();
    if (!binding || typeof binding === 'string' || binding.address !== HTTP_HOST) {
      response.writeHead(503, { 'content-type': 'application/json; charset=utf-8' });
      response.end(JSON.stringify({ error: '本机资料服务尚未就绪。' }));
      return;
    }
    const origin = `http://${binding.address}:${binding.port}`;
    const allowedHosts = [`${HTTP_HOST}:${binding.port}`, HTTP_HOST];
    // HTTP 安全检查先于端点/方法路由，目录和 GET/DELETE 也不能绕过 Origin 校验。
    // 返回固定提示，不反射客户端提交的 Host/Origin 字符串。
    if (!allowedHosts.includes(request.headers.host)
      || (request.headers.origin !== undefined && request.headers.origin !== origin)) {
      response.writeHead(403, { 'content-type': 'application/json; charset=utf-8' });
      response.end(JSON.stringify({ error: '请求来源不受信任，仅允许本机资料服务。' }));
      return;
    }
    // 市场目录是普通只读 JSON，不是 MCP 协议，也不把两个工具拆成市场服务条目。
    if (request.url === '/catalog') {
      if (request.method !== 'GET') {
        response.writeHead(405, { 'content-type': 'application/json; charset=utf-8', allow: 'GET' });
        response.end(JSON.stringify({ error: '目录仅支持 GET /catalog。' }));
        return;
      }
      response.writeHead(200, {
        'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store',
      });
      response.end(JSON.stringify({ servers: [{
        name: 'manufacturing_operations_documents',
        description: '制造运营资料服务：只读提供采购与库存、生产与质量、设备与工位三份正式业务资料。',
        version: SERVICE_VERSION,
        baseUrl: `${origin}/mcp`,
        tools: ['list_documents', 'read_document'],
      }] }));
      return;
    }
    if (request.url !== '/mcp') {
      response.writeHead(404, { 'content-type': 'application/json; charset=utf-8' });
      response.end(JSON.stringify({ error: '未找到端点，仅提供 /mcp 与 /catalog。' }));
      return;
    }
    // 官方 stateless 示例不提供 GET SSE / DELETE 会话接口；这里只做 HTTP 方法限制。
    if (request.method !== 'POST') {
      response.writeHead(405, { 'content-type': 'application/json; charset=utf-8', allow: 'POST' });
      response.end(JSON.stringify({ error: '仅支持 POST /mcp（stateless）。' }));
      return;
    }
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: undefined,
      enableJsonResponse: true,
      enableDnsRebindingProtection: true,
      allowedHosts,
      allowedOrigins: [origin],
    });
    const server = createMcpServer({ documentStore });
    // stateless 每次请求必须新建 transport/server；响应结束即释放，不保留会话或 SSE 流。
    response.once('close', () => {
      server.close().catch(() => log('HTTP 请求资源关闭失败。'));
    });
    try {
      await server.connect(transport);
      await transport.handleRequest(request, response);
    } catch {
      log('HTTP 请求处理失败。');
      if (!response.headersSent) {
        response.writeHead(500, { 'content-type': 'application/json; charset=utf-8' });
        response.end(JSON.stringify({ error: '资料服务请求处理失败。' }));
      } else if (!response.writableEnded) {
        response.end();
      }
    }
  });
  return httpServer;
}

export function parseServerArguments(args) {
  let values;
  try {
    ({ values } = parseArgs({ args, options: {
      http: { type: 'boolean', default: false }, port: { type: 'string' },
    }, strict: true, allowPositionals: false }));
  } catch {
    throw new Error('参数无效。用法：node server.mjs [--http --port <端口>]');
  }
  if (!values.http) {
    if (values.port !== undefined) throw new Error('端口参数仅在 --http 模式中有效。');
    return { mode: 'stdio' };
  }
  const text = values.port ?? '19540';
  const port = Number(text);
  if (!/^\d+$/.test(text) || !Number.isInteger(port) || port < 0 || port > 65535) {
    throw new Error('端口必须为 0 到 65535 的整数；0 仅用于临时随机端口测试。');
  }
  return { mode: 'http', port };
}

async function main() {
  const options = parseServerArguments(process.argv.slice(2));
  if (options.mode === 'stdio') {
    const server = createMcpServer();
    const close = () => server.close().catch(() => log('STDIO 关闭失败。'));
    process.stdin.once('end', close);
    process.once('SIGINT', close);
    process.once('SIGTERM', close);
    await server.connect(new StdioServerTransport());
    log('已启动：STDIO，只读。');
    return;
  }
  const server = createHttpServer();
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    // 监听地址不可通过 CLI、环境变量或工具调用改成外网地址。
    server.listen(options.port, HTTP_HOST, resolve);
  });
  const close = () => {
    server.close();
    server.closeAllConnections();
  };
  process.once('SIGINT', close);
  process.once('SIGTERM', close);
  log(`已启动：HTTP stateless，只读，http://${HTTP_HOST}:${server.address().port}/mcp`);
}

// import 只定义工厂，不启动进程；直接 node server.mjs 才连接传输。
if (process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url) {
  main().catch((error) => {
    if (error?.code === 'EADDRINUSE') log('启动失败：端口已被占用。');
    else log('启动失败，请检查依赖、参数或端口。用法：node server.mjs [--http --port <端口>]');
    process.exitCode = 1;
  });
}

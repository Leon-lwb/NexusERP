# 制造运营资料服务

独立、小型、只读的本地 MCP 服务，供业务资料工具和 MCP 市场展示使用。服务正式名称为 **制造运营资料服务**；没有“演示/测试”服务名。

默认使用官方 SDK 的 STDIO 模式；可显式切换到 **stateless Streamable HTTP**，地址只绑定 `127.0.0.1`，提供 MCP 协议端点 `/mcp` 和独立普通 JSON 市场目录 `/catalog`。
两种模式共享完全相同的两个只读工具与文件白名单，不访问 ERP、数据库或外部业务 API。
HTTP 使用 Node 内置 HTTP server 转交官方 `StreamableHTTPServerTransport`，**没有新增 Express 直接依赖，没有自行实现 MCP 协议**。

> 本服务接入的是资料原文，不是实时采购/库存/生产/质量/设备数据。资料由主线程/维护者提供，本服务不生成、补写、修订正文。

## 固定资料与读取边界

仓库根从 `document-store.mjs` 的 `import.meta.url` 向上计算，**不是当前工作目录 cwd**；资料只来自该仓库的 `doc/ai/knowledge-base/display`。

| 正式文件名 | 展示标题 |
| --- | --- |
| `采购与库存作业规范.md` | 采购与库存作业规范 |
| `生产与质量作业规范.md` | 生产与质量作业规范 |
| `设备与工位作业指引.md` | 设备与工位作业指引 |

- 仅精确匹配上述文件名，不接受路径、URL、目录穿越、绝对路径、盘符/UNC 路径、备用数据流、编码路径或未知文件。
- 资料目录链和文件均检查 `lstat` / `realpath`；软链接与 Windows junction 一律拒绝，即使指向资料目录内部也不放行。
- 只允许普通文件；以只读标志打开。支持 `O_NOFOLLOW` 的平台额外使用该标志，打开后再次复核路径和文件身份，再读取 UTF-8 原文。
- 不缓存正文、不伪造缺失内容、不回退读取其他目录。不存在的文件清晰失败。
- 不提供文件写入、任意 SQL、网络代理或业务 API 工具；不能通过工具/CLI/环境变量变更资料根或 HTTP 监听地址。
- HTTP/工具错误不返回真实磁盘路径、底层异常原文或堆栈；应用日志只写 stderr。STDIO 的 stdout 仅由官方 Transport 输出 JSON-RPC。

这是应用层只读白名单，不是操作系统沙箱。维护者应限制进程账户权限与资料目录写权限，不应允许不可信进程并发替换仓库目录。

## 依赖和安装

要求 Node.js >= 20.19.0。本次实测环境为 Node.js `v24.21.0` / npm `11.19.0`，未单独验证其他 Node 版本或操作系统。

直接依赖为精确版本，不使用 `^`、`~`、`latest` 或无版本 `npx`：

| 包 | 固定版本 | npm 官方发布时间 |
| --- | --- | --- |
| `@modelcontextprotocol/sdk` | `1.32.1` | `2026-10-05T11:47:50.208Z` |
| `zod` | `4.6.5` | `2026-09-13T23:25:14.644Z` |

`package-lock.json` 锁定完整依赖树，SDK/Zod 的 `resolved` 和 integrity 来自 npm 官方 Registry。
`.npmrc` 固定官方 Registry，启用 `ignore-scripts`，把缓存放在本目录 `.npm-cache/`；`node_modules/`、缓存、测试临时文件和验收日志均被本目录 `.gitignore` 忽略。

在当前项目的服务目录执行；以下为本机路径示例，其他环境按实际项目与Node路径替换：

```powershell
Set-Location -LiteralPath 'E:\Nexus\nexus-erp\script\mcp\manufacturing-documents'
npm ci --ignore-scripts --userconfig .npmrc
npm run check --userconfig .npmrc
npm test --userconfig .npmrc
npm run test:protocol --userconfig .npmrc
```

初次生成锁文件使用 `npm install --ignore-scripts`，其后推荐 `npm ci --ignore-scripts`。
`--userconfig .npmrc` 避免用户级其他项目的 npm 配置/镜像干扰，不修改用户全局配置。

## 启动方式（由主线程/维护者按需执行）

默认 STDIO：

```powershell
& 'D:\Program Files\nodejs\node.exe' 'E:\Nexus\nexus-erp\script\mcp\manufacturing-documents\server.mjs'
```

等价入口是 `node server.mjs`。STDIO 由客户端负责启动/关闭；stdin EOF 后正常关闭。
**后端配置不要用 `npm start` 或 `npx`**，避免 npm 自身输出污染 STDIO 的 JSON-RPC 通道。

可选 HTTP（本任务没有启动此正式展示实例）：

```powershell
& 'D:\Program Files\nodejs\node.exe' 'E:\Nexus\nexus-erp\script\mcp\manufacturing-documents\server.mjs' --http --port 19540
```

- `--http` 未指定 `--port` 时默认 `19540`；正式启动前由主线程核对占用，并按既定约定 Hidden 启动。
- `--http --port 0` 让操作系统分配临时端口，仅用于隔离夹具测试。实际 URL 只写 stderr。
- 端口仅接受 `0..65535` 的整数，未知参数报错；没有 `--host` / `--root` 参数。
- 只绑定 `127.0.0.1`。预计正式端点为 `http://127.0.0.1:19540/mcp`，此 URL 是配置建议，不代表目前常驻可用。
- 每次 POST 新建官方 stateless Transport 和 MCP server，不创建 `Mcp-Session-Id`，响应关闭即释放资源。
- MCP 协议仅支持 POST `/mcp`；GET / DELETE 等方法返回 405，不保留 GET SSE 常驻流。市场目录仅支持 GET `/catalog`，其他目录方法返回 405；未知路由和带查询参数的路由返回 404。
- 官方 Transport 启用 DNS rebinding 防护，Host 限制为本机地址，存在 Origin 时须匹配本机服务 Origin。
- 不提供公网监听、TLS、用户认证或 CORS 放行；只供同机后端访问。若后端运行在独立容器/机器，需主线程另核网络边界，不能直接宣称该回环 URL 可用。

## 只读市场目录：GET `/catalog`

`/catalog` 是普通 JSON 目录，**不是 MCP 协议端点**。按主线程确认的市场契约，刷新市场时 HTTP GET 此地址，读取顶层 `servers` 数组；加载条目时使用 `entry.baseUrl` 创建 REMOTE 服务。

该数组固定只有 **1 个实际服务条目**，不把两个协议工具拆成两个市场服务。若本服务实际监听端口为 `19540`，响应为：

```json
{
  "servers": [
    {
      "name": "manufacturing_operations_documents",
      "description": "制造运营资料服务：只读提供采购与库存、生产与质量、设备与工位三份正式业务资料。",
      "version": "1.0.0",
      "baseUrl": "http://127.0.0.1:19540/mcp",
      "tools": ["list_documents", "read_document"]
    }
  ]
}
```

- `baseUrl` 只取当前 HTTP server 的真实 `address()` 绑定地址/端口；随机端口会自动反映在目录中。不会从请求 Host、Origin、查询参数或 Forwarded / X-Forwarded-* 头构造来源。
- 请求必须通过与 `/mcp` 相同的本机 Host / Origin 校验，Host 注入拒绝。目录只允许精确 GET `/catalog`，不接收用户配置的远端 URL，也不提供代理。
- `tools` 仅是两项已实现工具的展示名称；实际工具发现和调用仍经 `baseUrl` 的 MCP initialize / tools/list / tools/call。
- 正式展示的两条确切 URI 建议为：市场目录 `http://127.0.0.1:19540/catalog`，服务协议 `http://127.0.0.1:19540/mcp`。主线程负责显式本地允许名单与正式启动；本服务不修改后端允许名单，不放开其他环回 URI。

## 工具契约

服务只发现以下两个工具，均声明 `readOnlyHint=true`、`destructiveHint=false`、`idempotentHint=true`、`openWorldHint=false`。
请求 Schema 严格拒绝额外参数。

### `list_documents`

```json
{"name":"list_documents","arguments":{}}
```

返回 text JSON 和相同的 `structuredContent`：`{"documents":[...]}`。
目录固定列出三份正式名称，每项含 `name`、`title`、`mimeType: "text/markdown"` 和 `available`。
缺失或不安全时 `available: false`，并给出 `error.code` / `error.message`；不会返回该项正文。
`available` 表示目录/文件安全检查通过，实际读取仍可能因权限或随后文件变更而失败。

### `read_document`

```json
{"name":"read_document","arguments":{"document_name":"采购与库存作业规范.md"}}
```

成功时 `content[0].text` 是磁盘 UTF-8 原文，`structuredContent` 包含 `name`、`title`、`mimeType`、`content`。
失败时 `isError: true`，text 给出中文原因，`structuredContent.error` 给出以下稳定错误码：

| 错误码 | 含义 |
| --- | --- |
| `DOCUMENT_NOT_ALLOWED` | 不在精确文件白名单，包括任意路径/网址 |
| `DOCUMENT_NOT_FOUND` | 正式文件或资料目录缺失，不编造正文 |
| `DOCUMENT_UNSAFE_PATH` | 软链接/越界引用或检查期间文件身份变化 |
| `DOCUMENT_NOT_REGULAR_FILE` | 文件名对应的对象不是普通文件 |
| `DOCUMENT_READ_FAILED` | 文件权限或其他读取失败，错误已脱敏 |

未知工具、非字符串参数或多余参数由官方 SDK 拒绝。

## 后端 LOCAL configJson 建议

以下是 LOCAL/STDIO 配置示例。根据本机安装位置填写 Node 和项目的绝对路径；不要引用开发者的隔离工作树。REMOTE/HTTP 展示接入见下方说明。

```json
{
  "command": "D:\\Program Files\\nodejs\\node.exe",
  "args": [
    "E:\\Nexus\\nexus-erp\\script\\mcp\\manufacturing-documents\\server.mjs"
  ]
}
```

在所用项目的服务目录执行 `npm ci --ignore-scripts --userconfig .npmrc` 后，确认 `args[0]` 指向实际项目入口，例如：

```text
E:\Nexus\nexus-erp\script\mcp\manufacturing-documents\server.mjs
```

不要把 `--http` 填入需要 STDIO 的 LOCAL 客户端配置。HTTP 市场目录 URL 使用 `http://127.0.0.1:19540/catalog`；目录服务条目名为 `manufacturing_operations_documents`，中文说明为“制造运营资料服务”。加载后的 REMOTE 协议连接 URL 来自条目 `baseUrl`，即同一服务实际绑定的 `/mcp`；不能把 `/catalog` 当成 MCP 协议端点。
服务只有在主线程实际启动、三份正文到位、Java 客户端连接验证及市场记录配置完成后，才能宣称正式展示接入完成。

## 测试与验收记录

先写测试、确认失败，再实现。初始 RED：**44 tests / 0 pass / 44 fail / 0 skipped**，失败原因是缺少资料层/服务入口。
首次服务实现 GREEN：**44 tests / 44 pass / 0 fail / 0 skipped**。
市场目录增量 RED：6 个 HTTP 用例中 3 个原有通过、3 个新增失败（原实现 `/catalog` 为 404），0 跳过。目录增量后的全量 GREEN：**47 tests / 47 pass / 0 fail / 0 skipped**。

- 37 个资料层用例：固定清单、三份 UTF-8 文件原样读取、更新后重新读取、文件/目录缺失、严格名称校验、非 cwd 寻根、非普通文件、软链接文件与目录（Windows 下真实创建 junction，文件软链接测试也不跳过）。
- 3 个 STDIO 实际协议用例：官方 `Client` + `StdioClientTransport` 对真实 CLI 进行 initialize / tools/list / tools/call；临时复制真实入口验证三份成功读取；原始 stdout 每行都是 JSON-RPC，日志在 stderr，EOF 后退出。
- 6 个 HTTP 集成用例：临时复制真实入口，`--http --port 0`；官方 `StreamableHTTPClientTransport` 完成 initialize / tools/list / tools/call；验证缺文件脱敏失败、stateless 无会话头、Origin 拒绝、非法 JSON、405/404。新增目录 JSON 验收、真实 TCP peer 端口与 baseUrl 一致性、从目录来源执行协议发现/调用、Host 注入拒绝、转发头不能改写来源及禁止远端 URL 参数。
- 1 个 CLI 参数用例：默认 STDIO、HTTP 显式开启、整数端口限制、不能设置监听地址或资料根。
- 协议单独运行 `npm run test:protocol`：10 个非零用例（含上述 CLI 参数测试）。子进程在 finally 中关闭，夹具再清理；HTTP 测试不使用正式端口 `19540`。

验收日志位于本目录 `.verification/` 并被忽略，不提交依赖/缓存/临时文件。
上述自动化用例使用隔离文件夹具，不等同于正式展示接入验收。

本轮本地展示阶段另外完成：主树三份正文接入、目录刷新、市场服务加载、Java 客户端实际发现两项工具及资料读取。服务仅绑定本机回环，后端通过服务端精确端点白名单允许 `/mcp` 与 `/catalog`；普通 API 配置不能解除内网访问限制。聊天的成功还必须按本次请求实际执行证据核验，不能仅以 Provider 非空或模型返回文本判定成功。详细会话与 Flow 证据见同日 AI 正式展示验证记录。
未验证生产部署、真实现场设备及整个 ERP 业务闭环。

## 仅使用的官方技术来源

- npm Registry：`https://registry.npmjs.org/@modelcontextprotocol%2Fsdk`、`https://registry.npmjs.org/zod`。
- 指定版本元数据：`https://registry.npmjs.org/@modelcontextprotocol%2Fsdk/1.32.1`、`https://registry.npmjs.org/zod/4.6.5`。
- 官方 SDK 仓库：`https://github.com/modelcontextprotocol/typescript-sdk`；实际契约及 stateless 示例核对了安装包内 `dist/esm/server/streamableHttp.js`、`dist/esm/examples/server/simpleStatelessStreamableHttp.js` 和官方 Client 实现。
- 官方 MCP 规范：`https://modelcontextprotocol.io/specification/2025-11-25/basic/transports`（STDIO 的 stdout 限制、Streamable HTTP、本机绑定及 Origin 安全约定）。

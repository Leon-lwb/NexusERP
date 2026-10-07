# 制造运营资料服务实施计划

用户已指定并批准以下固定设计，本计划不再扩展范围，也不请求额外信息。

**目标：** 在指定隔离树实现独立、小型、只读 MCP 资料服务，以正式名称接入真实仓库文件。

**架构：** 官方 `McpServer` + 默认 `StdioServerTransport`；新增显式 `--http --port <端口>`，以官方 stateless `StreamableHTTPServerTransport` 提供 `/mcp`，只绑定 `127.0.0.1`；仅注册 `list_documents` 与 `read_document`。
读文件能力独立于协议层，以模块绝对位置推导仓库根，精确匹配三份白名单，检查目录链和文件的软链接/真实路径，不接受调用方传入的路径。
stdout 只由官方 Transport 写 JSON-RPC，日志只写 stderr。不存在的文件清晰报错，无兜底编造。

**技术栈：** Node.js >= 20.19.0、原生 ESM、`node:test`；SDK `1.32.1`，Zod `4.6.5`，版本已在 npm 官方 Registry 核实。

## 初始隔离实现边界

初始 worker 的写入范围为仓库内 `script/mcp/manufacturing-documents/`；正式使用以当前项目路径为准，不依赖开发者的隔离工作树。
复用隔离树，BASE 为 `bf9d09f666c74ab5ca312fc95c8bd5878fc6ac6d`，不建分支、不 commit、不推送。
不修改 AI 后端、前端、SQL、模型、正式资料正文；不启动 ERP，不调用业务 API，不启动常驻进程。用户补充允许 HTTP 夹具随机端口测试，但不使用预计正式端口 `19540`。

固定资料目录为当前仓库 `doc/ai/knowledge-base/display`，仅允许：

- `采购与库存作业规范.md`
- `生产与质量作业规范.md`
- `设备与工位作业指引.md`

## 文件职责

所有服务实现文件均位于上述仓库相对目录内：

- `package.json` / `package-lock.json`：固定直接依赖版本与完整依赖树。
- `.npmrc` / `.gitignore`：官方 Registry、目录内缓存、禁用安装脚本，忽略依赖和临时文件。
- `document-store.mjs`：三份白名单、只读目录状态/原文读取、安全检查与中文错误。
- `server.mjs`：官方 SDK 工具注册、正式名称、stdio 启停、stderr 日志。
- `test/fixtures.mjs`：仅在授权目录内创建/清理隔离测试资料，不写正式正文。
- `test/document-store.test.mjs`：真实文件、缺文件、输入白名单、绝对路径和软链接拒绝。
- `test/http.test.mjs`：临时复制真实入口，随机端口运行官方 HTTP Client 的握手/发现/调用，finally 关闭；检查 stateless、Origin 和错误无路径/堆栈。
- `test/stdio.test.mjs`：官方 Client 真实子进程握手、发现和调用；校验 stdout 纯 JSON-RPC。
- `README.md`：中文使用说明、工具契约、LOCAL configJson 建议、版本来源和验收边界。

## 执行步骤与验收

- [x] 核对主树与隔离树 status、BASE、模块路径及 Node/npm；主树改动保持不动。
- [x] 查询 `https://registry.npmjs.org/@modelcontextprotocol%2Fsdk` 与 `https://registry.npmjs.org/zod`，选择精确版本；技术资料限官方 SDK/spec/npm。
- [x] 先写测试。目录必须严格列出三份正式文件；读取必须等于 UTF-8 夹具原文；不存在必须返回 `DOCUMENT_NOT_FOUND`。
- [x] 在无服务实现时运行 `npm test`，确认非零测试、预期断言失败并保存 RED 日志。
- [x] 实现只读资料层；未知名/相对路径/绝对路径一律 `DOCUMENT_NOT_ALLOWED`；软链接文件和目录一律拒绝，普通目录不能当文件读取。
- [x] 实现官方 stdio 服务，只有两项工具，声明只读/幂等/封闭范围；错误使用 `isError: true`，不输出编造资料。
- [x] 使用官方 Client 对真实 `node server.mjs` 执行 initialize → tools/list → tools/call，并关闭子进程；在临时复制入口的仓库夹具中验证成功读取和非 cwd 寻根。
- [x] 用 Node 内置 HTTP server 转交官方 stateless transport，不引入 Express，不自行实现 MCP 协议；两项工具和白名单与 STDIO 共享，HTTP 错误不吐路径/堆栈。
- [x] 运行非零 HTTP 官方 Client 集成测试，只对临时夹具随机端口，不占用 19540，finally 关闭。
- [x] 补中文 README，建议 LOCAL `command` 为 Node 绝对 exe，`args` 为服务绝对路径；Java 契约由主线程核实。
- [x] 执行 `npm ci --ignore-scripts`、`npm run check`、`npm test`、`npm run test:protocol`，检查非零用例与零失败。
- [x] 最终检查实际文件/diff/status、正式资料未写入、子进程已退出、无越界改动；记录文件、版本来源、RED/GREEN、真实协议验收与未验证项。

依赖安装均在上述目录执行，采用 `npm install --ignore-scripts` / `npm ci --ignore-scripts`，缓存也在该目录。
所有测试清理只针对本轮自建并核对位于 `test/.tmp/` 内的随机目录。

## 最终核对结果

- RED：44 个用例预期失败（资料层/入口缺失），0 跳过。
- HTTP Origin 回归 RED：3 个 HTTP 用例中 1 个失败，证明 GET 的 405 分支曾绕过 Origin 校验；随后修复为所有请求先校验，3/3 通过。
- 锁文件重新安装 `npm ci --ignore-scripts --userconfig .npmrc` 成功；语法检查成功；全量 44/44、协议复验 7/7，均 0 失败、0 跳过。
- 最终 12 个新增文件全在授权目录，既有受跟踪文件 diff 为空，HEAD 仍为 BASE，任务服务进程数量为 0。
- 未写正式正文，当前隔离树三份正式来源缺失，已验证明确失败；成功原文读取和 HTTP 协议验收来自真实磁盘夹具。
- Java LOCAL/HTTP 契约、正式服务启动、市场登记和正式正文端到端验证由主线程负责。本任务没有 commit 或启动常驻展示服务。

## 主树接入记录（本轮已授权本地环境）

服务实现已汇合到主树，三份正式展示资料位于固定资料目录。主线程在授权本地测试环境完成常驻回环服务启动、市场登记、后端发现及读取；自动化的47个用例仍独立使用夹具，不计为正式正文验收。后端按服务端精确端点白名单接入，不扩为任意内网访问。详细证据以同日主线程验证记录为准，本文初始隔离实现边界不代表后续未执行。

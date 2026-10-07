# AI 知识库检索 / MCP 最小修复交接

## 现场与边界

- 工作树：`E:/Nexus/nexus-erp/.codex/worktrees/ai-demo-ui-20261008`；开始时干净、detached HEAD，基线 `bf9d09f666c74ab5ca312fc95c8bd5878fc6ac6d`。
- Node `v24.21.0`、npm `11.19.0`；主根 `node_modules` 通过 junction 只读复用，未安装依赖或写入其中的 `.tmp`。
- 唯一生产代码修改：`src/views/ai/knowledge/index.vue`。MCP 页面 / API 的现有流程经过回归后未发现本任务所述大 ID 取整或业务 500 吞没，不做无证据修改。
- 事先说明了唯一既有标准入口变更 `package.json` 的 `test:unit`：默认先执行专属回归，再原样执行旧命令；带 `--ai-knowledge-mcp` 时只跑本任务，避开原入口写共享依赖缓存的步骤。
- 未修改 assistant、decision、Flow、锁文件或后端；未调用运行中的 API、外部 MCP、数据库或服务；未提交或推送。

## 根因与最小设计

旧检索请求发出时就把 `retrievalQueried` 设为 true；失败 catch 仅把数组清空，模板因此仍显示“未检索到相关内容”。旧请求也没有序号保护，切库或新查询后能覆盖当前结果 / 加载态。

保留原布局和既有状态变量，只补面板错误与请求序号：

1. 发起检索先清理旧命中、错误和成功标记。
2. 仅合法数组成功返回才进入“已完成”；成功空数组显示原来的无命中文案。
3. 失败 / 格式异常显示“检索失败，请稍后重试”，不显示成功无命中，不残留旧结果。
4. 检索、切库、取消选库、卸载使旧序号失效；只有当前知识库当前请求能回写面板或结束 loading，包括 A→B→A 的情况。

不新增公共状态机、不改全局请求拦截器、不新增接口。不声称取消了底层请求；旧请求的全局拦截器 toast 沿用原行为，隔离的是本页检索面板。

## 回归方式与 RED → GREEN 证据

使用已有 `@vue/compiler-sfc`、esbuild、Vue 自定义 renderer 和 `node:test`。生产 SFC、API、`useCrud` 及请求响应拦截器真实编译 / 执行；Axios 传输、Element Plus 展示 / 表单校验边界采用离线替身。不测试真实网络、真实 Element Plus DOM 交互或后端业务。

- 修改生产代码前：23 个用例，12 通过、11 失败；失败是业务断言，不是编译 / 挂载错误。见本机 `red.log`。
- 最终同一批 31 个用例只读装载 `bf9d09f6` 的原始页面 SFC：19 通过、12 失败，退出 1。见 `red-baseline.log`。
- 最终当前工作树：31 通过、0 失败、0 跳过，退出 0。见 `green.log`。
- 既有标准入口接线回归：8 通过，未改写旧接线测试。

31 个用例覆盖：检索 HTTP 500 / HTTP 200 + 业务 500、正常空 / 非空、旧结果清理、等待态、格式异常、失败后重试、同库乱序、切库乱序、旧 finally、A→B→A、取消选库；MCP 列表、有效表单新增 / 编辑、失败不报成功 / 不关弹窗、非法 JSON、状态 / 连接测试，以及可用工具选择 API 的空 / 非空 / 错误 / 大 ID 字符串边界。可用工具选择 API 已测，不代表 assistant 工具选择器人工验收。

### 可直接复验的命令

在指定工作树的 `plus-ui` 下使用 PowerShell：

```powershell
# 只读基线页面的 RED；预期退出 1，不改写任何生产文件。
try {
  $env:AI_KNOWLEDGE_MCP_BASELINE_REF = 'bf9d09f6'
  npm run test:unit -- --ai-knowledge-mcp
} finally {
  Remove-Item -LiteralPath Env:AI_KNOWLEDGE_MCP_BASELINE_REF -ErrorAction SilentlyContinue
}

# 当前源码 GREEN，预期 31/31。
npm run test:unit -- --ai-knowledge-mcp

# 原有入口仍挂着旧套件，预期 8/8。
node --test --test-concurrency=1 scripts/test-full-flow-test-wiring.mjs scripts/test-warehouse-safety-test-wiring.mjs

npm run lint:eslint -- src/views/ai/knowledge/index.vue src/views/ai/mcp/index.vue src/views/ai/mcp/market.vue src/api/ai/knowledge.ts src/api/ai/mcp.ts src/api/ai/mcpTool.ts scripts/ai-knowledge-mcp-unit-entry.mjs tests/ai-knowledge-mcp/component-harness.mjs tests/ai-knowledge-mcp/knowledge.test.mjs tests/ai-knowledge-mcp/mcp.test.mjs tests/ai-knowledge-mcp/prepare-typecheck.mjs

node node_modules/prettier/bin/prettier.cjs --check src/views/ai/knowledge/index.vue package.json scripts/ai-knowledge-mcp-unit-entry.mjs tests/ai-knowledge-mcp/component-harness.mjs tests/ai-knowledge-mcp/knowledge.test.mjs tests/ai-knowledge-mcp/mcp.test.mjs tests/ai-knowledge-mcp/prepare-typecheck.mjs tests/ai-knowledge-mcp/README.md

node tests/ai-knowledge-mcp/prepare-typecheck.mjs
npm run type-check -- --project tests/ai-knowledge-mcp/.typecheck.tsconfig.json --incremental false
```

类型检查 helper 只读借用真实自动导入声明，并使用 Element Plus 官方组件类型；不以 any 全局声明掩盖错误。临时配置写在专属 tests 目录；声明缺失会明确失败，不自动生成或安装。

**范围限制：** 原始全量 `npm run type-check -- --incremental false` 在修复前就退出 2，当前工作树缺少 Git 忽略的自动导入声明而出现大量 TS2304。定向类型检查通过不代表全仓类型检查通过。未运行默认全量 `test:unit` 或生产构建（其既有步骤会写共享缓存 / 额外生成文件）；未做真实页面、DB、服务或生产验收。npm 的两个旧 mirror 配置警告是本机既有警告，不是跳过用例。

## 给主线程的展示操作清单

以下是真实演示环境获准后的人工作业清单，**本次未执行**。错误和迟到场景优先用离线测试 / 本地拦截替身，不通过停服务、改数据库来造错。

| 展示动作                                                    | 期望检查点                                                                |
| ----------------------------------------------------------- | ------------------------------------------------------------------------- |
| 进入 AI → 知识库，选中已有知识库 → 检索测试，输入可命中文本 | 列出文本 / 片段；不出现空结果或错误                                       |
| 输入确定无命中的文本                                        | 等待中不能先报无命中；成功后才出现“未检索到相关内容”                      |
| 先获得命中，再在离线替身中返回 HTTP 500 / 业务 `code=500`   | 上轮命中消失；面板显示“检索失败，请稍后重试”，不显示“未检索到相关内容”    |
| 失败后重试，返回正常空数组 / 非空数组                       | 错误清除，恢复对应的真实成功展示                                          |
| 让 A 库检索延迟，切 B 库并检索；再释放 A 的成功或失败       | B 库结果 / 等待态不被覆盖；切库时文本、结果、错误、loading 重置           |
| 同库连续查询倒序返回，或 A→B→A                              | 只保留最新请求结果；旧请求不能提前关闭 loading                            |
| 进入 AI → 工具管理，查看列表，打开新增 / 编辑               | 按钮事件不会误当回调；原字符串雪花 ID 回填、保存、状态 / 测试路径原样透传 |
| 在离线替身中使工具列表 / 新增 / 编辑失败                    | 列表不留旧工具；保存不报成功、不关闭弹窗                                  |
| 使用非法配置 JSON                                           | 在 API 之前阻止保存并给出中文提示                                         |
| 可用工具选择 API 返回空 / 非空 / 业务失败                   | 空成功保留空数组；大 ID 不取整；业务错误拒绝，不变成空成功                |

## writeSet

相对指定工作树（实际交付均保留在该工作树内）：

- `plus-ui/src/views/ai/knowledge/index.vue`
- `plus-ui/package.json`（仅标准单测入口一行）
- `plus-ui/scripts/ai-knowledge-mcp-unit-entry.mjs`
- `plus-ui/tests/ai-knowledge-mcp/component-harness.mjs`
- `plus-ui/tests/ai-knowledge-mcp/knowledge.test.mjs`
- `plus-ui/tests/ai-knowledge-mcp/mcp.test.mjs`
- `plus-ui/tests/ai-knowledge-mcp/prepare-typecheck.mjs`
- `plus-ui/tests/ai-knowledge-mcp/README.md`
- `plus-ui/tests/ai-knowledge-mcp/.gitignore`

本机证据 `*.log` 和 `.typecheck.tsconfig.json` 位于专属 tests 目录且已忽略，不应提交。MCP 生产页面和所有 API 文件只读核验，无写入。

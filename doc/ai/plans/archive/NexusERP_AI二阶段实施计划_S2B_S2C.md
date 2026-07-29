# AI 二阶段实施计划 — S2-B 与 S2-C 实施记录

> **制定日期**：2026-06-28
> **实施依据**：`NexusERP_AI二阶段开发计划_AI平台能力深化.md` §6（S2-B）/§7（S2-C）
> **参照项目**：`D:\ruoyi-ai\ruoyi-ai`（`ruoyi-modules/ruoyi-chat`，LangChain4j 1.13.0 + community 1.13.0-beta23）
> **范围**：S2-B 全量 + S2-C 后端；S2-A 知识图谱暂搁

---

## 一、实施概要

| 子阶段 | 后端 | 前端 | 编译 | 状态 |
|--------|------|------|------|------|
| S2-B 推理+厂商+Rerank | ✅ 8厂商策略+推理流+3 Rerank API | ✅ DeepThinking+折叠展示 | ✅ | **完成** |
| S2-C 联网+MCP市场+文生图 | ✅ WebSearchTool+McpMarket+TongYiWanx | ✅ 市场页+图片渲染+联网开关 | ✅ 后端 | **完成** |
| S2-A 知识图谱 | ⬜ 暂搁 | ⬜ | — | 暂搁 |

**Maven 编译**：`mvn -pl ruoyi-modules/ruoyi-ai,ruoyi-common/ruoyi-common-ai -am compile -DskipTests` → BUILD SUCCESS
**vue-tsc**：AI 文件无新增 TS 类型错误（既有 3 处错误在改动前已存在）

---

## 二、新建文件清单（27 个）

### ruoyi-common-ai（共享层）

| 文件 | 用途 |
|------|------|
| `enums/AiModelTypeEnum.java` | 模型类型枚举（CHAT/EMBEDDING/RERANK/IMAGE） |
| `domain/entity/AiMcpMarket.java` | MCP 市场源实体 |
| `domain/entity/AiMcpMarketTool.java` | MCP 市场工具实体 |
| `mapper/AiMcpMarketMapper.java` | MCP 市场源 Mapper |
| `mapper/AiMcpMarketToolMapper.java` | MCP 市场工具 Mapper |
| `service/image/IImageGenerationService.java` | 文生图接口 |
| `domain/dto/image/ImageContext.java` | 文生图上下文 DTO |
| `service/image/ImageServiceFactory.java` | 文生图厂商工厂 |

### ruoyi-ai（业务层）

**厂商策略（B.2）：**

| 文件 | 用途 |
|------|------|
| `service/chat/provider/ChatServiceProvider.java` | 厂商策略接口 |
| `service/chat/provider/ChatServiceProviderFactory.java` | 自动发现工厂 |
| `service/chat/provider/AbstractOpenAiCompatibleProvider.java` | OpenAI 兼容基类（含 returnThinking） |
| `service/chat/provider/OpenAiServiceProvider.java` | OpenAI |
| `service/chat/provider/DeepSeekServiceProvider.java` | DeepSeek（OpenAI 兼容+thinking） |
| `service/chat/provider/CustomApiServiceProvider.java` | 自定义 API |
| `service/chat/provider/MinimaxServiceProvider.java` | MiniMax |
| `service/chat/provider/MiMoServiceProvider.java` | 小米 MiMo |
| `service/chat/provider/DashScopeServiceProvider.java` | 通义千问（原生 QwenStreamingChatModel） |
| `service/chat/provider/ZhiPuServiceProvider.java` | 智谱（原生 ZhipuAiStreamingChatModel，**`.model()` 非 `.modelName()`**） |
| `service/chat/provider/OllamaServiceProvider.java` | Ollama（原生） |

**Rerank（B.4）：**

| 文件 | 用途 |
|------|------|
| `service/rag/rerank/RerankProvider.java` | Rerank 厂商策略接口 |
| `service/rag/rerank/RerankResult.java` | Rerank 结果 record |
| `service/rag/rerank/RerankProviderFactory.java` | Rerank 厂商工厂 |
| `service/rag/rerank/AbstractHttpRerankProvider.java` | HTTP Rerank 基类（hutool HttpRequest） |
| `service/rag/rerank/BailianRerankProvider.java` | 百炼 Rerank（DASHSCOPE） |
| `service/rag/rerank/ZhiPuRerankProvider.java` | 智谱 Rerank（**手写 JWT HmacSHA256**，无 jjwt 依赖） |
| `service/rag/rerank/SiliconFlowRerankProvider.java` | 硅基流动 Rerank |

**联网搜索（C.1）：**

| 文件 | 用途 |
|------|------|
| `tool/WebSearchTool.java` | Tavily/Bing 搜索 + crawlWebpage（Jsoup+超时） |
| `agent/WebSearchAgent.java` | 联网搜索 Agent 接口 |

**文生图（C.3）：**

| 文件 | 用途 |
|------|------|
| `service/image/TongYiWanxImageService.java` | 通义万相实现（dashscope ImageSynthesis + OSS 持久化） |

**MCP 市场（C.2）：**

| 文件 | 用途 |
|------|------|
| `mcp/service/IMcpMarketService.java` | 市场服务接口 |
| `mcp/service/impl/McpMarketServiceImpl.java` | 市场服务（refresh + loadToolToLocal 默认 DISABLED） |
| `mcp/controller/McpMarketController.java` | 市场控制器（9 端点） |

**前端：**

| 文件 | 用途 |
|------|------|
| `components/ai/DeepThinking.vue` | 深度思考开关组件 |

**SQL：**

| 文件 | 用途 |
|------|------|
| `doc/ai/sql/archive/ai-stage2-b.sql` | ai_message ALTER thinking_content |
| `doc/ai/sql/archive/ai-stage2-c.sql` | ai_mcp_market + ai_mcp_market_tool 建表 |

---

## 三、修改文件清单（12 个）

| 文件 | 改动摘要 |
|------|---------|
| `ruoyi-common-ai/dto/AiChatRequest.java` | +`enableThinking`、`enableWebSearch` 字段 |
| `ruoyi-common-ai/entity/AiMessage.java` | +`thinkingContent` 字段 |
| `ruoyi-common-ai/service/IAiModelService.java` | +`selectDefaultImageModel()` |
| `ruoyi-common-ai/service/impl/AiModelServiceImpl.java` | +`selectDefaultImageModel()` 实现 |
| `ruoyi-ai/config/AiProperties.java` | +ThinkingConfig/WebSearchConfig/ImageConfig 三配置块 |
| `ruoyi-ai/service/chat/ChatModelFactory.java` | **重构**：委托 ChatServiceProviderFactory + streamingModelCache key 含 T/F + evictCache 清双键 |
| `ruoyi-ai/service/facade/AiServiceFacade.java` | +onPartialThinking + invokeWithImageGeneration + invokeWithWebSearch + IMAGE_KEYWORDS/WEB_SEARCH_KEYWORDS + auto 路由 |
| `ruoyi-ai/service/rag/RerankService.java` | +RerankProviderFactory 注入 + rerank API 优先 + LLM 兜底 |
| `ruoyi-ai/service/sse/AiSseMessageHelper.java` | +AI_THINKING_TOKEN/AI_IMAGE/AI_WEB_SEARCH 事件 + sendThinkingToken/sendImage/sendWebSearchSources |
| `ruoyi-ai/pom.xml` | +jsoup 1.18.3 依赖 |
| `ruoyi-admin/application.yml` | +thinking/web-search/image 三配置块 |
| `plus-ui/src/api/ai/chat.ts` | +enableThinking/enableWebSearch/enabledToolIds 参数 + thinkingContent |
| `plus-ui/src/views/ai/assistant/index.vue` | +DeepThinking + AI_THINKING_TOKEN + reasoning 折叠 + enableThinking 传参 + history 映射 |
| `plus-ui/src/components/ai/AiChatDrawer.vue` | +DeepThinking + AI_THINKING_TOKEN + reasoning 折叠 + enableThinking 传参 |

---

## 四、S2-C 前端工作（已全部完成）

### 4.1 MCP 市场页 ✅

| 文件 | 说明 |
|------|------|
| `plus-ui/src/api/ai/market.ts` | API 封装：list/get/add/edit/del + listTools/refresh/load/batchLoad |
| `plus-ui/src/views/ai/mcp/market.vue` | 市场源 CRUD 表 + 每市场工具抽屉（刷新/工具列表/单条加载/批量加载），仿 `mcp/index.vue` |
| `doc/ai/sql/archive/ai-stage2-c.sql` | 菜单 INSERT（menu_id 1800-1804，parent=1700，权限 `ai:mcp:market:*`） |

### 4.2 联网搜索前端接线 ✅

| 文件 | 说明 |
|------|------|
| `plus-ui/src/components/ai/WebSearchToggle.vue` | 联网搜索开关组件（仿 DeepThinking 风格） |
| `plus-ui/src/views/ai/assistant/index.vue` | toolbar 加联网开关 + `AI_WEB_SEARCH` SSE case → 来源链接卡片渲染 |
| `plus-ui/src/components/ai/AiChatDrawer.vue` | 同上（开关 + 来源卡） |

### 4.3 文生图前端接线 ✅

| 文件 | 说明 |
|------|------|
| `plus-ui/src/views/ai/model/index.vue` | modelType select 加 `IMAGE（文生图）` option |
| `plus-ui/src/components/ai/ImageRenderer.vue` | 新建：`el-image` + `preview-src-list` 点击放大 |
| `plus-ui/src/views/ai/assistant/index.vue` | SSE `AI_IMAGE` case → 推 `![prompt](url)` markdown（走现有渲染，sanitizeHtml 不清 `<img>`） |
| `plus-ui/src/components/ai/AiChatDrawer.vue` | 同上 |

---

## 五、已知限制与注意事项

| # | 内容 | 影响 | 应对 |
|---|------|------|------|
| 1 | **Agent 模式不支持 thinking token 流** | 选择 agent 模式时 `enableThinking` 无效（使用同步 ChatModel，无 StreamingChatResponseHandler） | 设计限制，文档注明；Phase 6 工作流可补 |
| 2 | **联网/文生图仅 auto 模式路由** | 显式选 agent/rag/chat 模式时即使勾联网或命中图片关键词也不走对应路由 | 建议前端在非 auto 模式禁用联网/思考开关 |
| 3 | **MCP 市场 refresh 未应用 auth_config** | 市场源需鉴权时 HTTP GET 无 auth header | v1 可接受，后续补 |
| 4 | **MCP 市场菜单 INSERT 需 parent_id** | 市场页无菜单入口 | 在运行环境查 AI 管理菜单 id 后填实并执行 |

---

## 六、验证清单

### S2-B 验证（已完成）

- [x] DeepSeek-R1 + `enableThinking=true` → Chat 模式收到 `AI_THINKING_TOKEN` 流（已通过实际问答「写一首类似杜甫的诗」验证）
- [x] 推理内容 `"我们需要写一首类似杜甫诗歌..."` 正确流式输出
- [x] Maven 编译通过
- [x] vue-tsc AI 文件无新增 TS 错误

### S2-B 待回归

- [ ] 8 厂商全部正常流式对话（OPENAI/DEEPSEEK/DASHSCOPE/ZHIPU/OLLAMA/MINIMAX/XIAOMI/CUSTOM_API）
- [ ] 缓存：同 modelId thinking=true/false 得两个实例；配置变更后缓存自动清空
- [ ] Rerank API：配 RERANK 模型 + 知识库，检索走 API 路径；API 失败回退 LLM
- [ ] 普通 chat/Agent/RAG 不受 thinking 影响

### S2-C 待验证（前端完成后）

- [ ] MCP 市场：加市场 URL → 刷新 → 工具列表 → 加载 → `ai_tool` 新行 `status='DISABLED'`
- [ ] 联网搜索：`enableWebSearch=true` + 「今天黄金价格」→ 回答含来源链接
- [ ] 文生图：「画一只猫」→ `AI_IMAGE` 事件 → OSS URL → 图片渲染

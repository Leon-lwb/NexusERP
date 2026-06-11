# NexusERP AI 开发计划 — 第四周（修复补齐 + 重排序集成）

> **前置状态**：第一~三周核心功能已实现（约 92%），编译通过。主要遗留 P0/P1 问题待修复。
>
> **本周目标**：修复 P0 问题，完成 P1 补齐项（重排序集成 + API Key 加密），跑通端到端集成测试。

---

## 现状盘点

### 已完成（第一~三周）

| 模块 | 内容 | 状态 |
|------|------|------|
| 后端 | ruoyi-common-ai（37个文件）+ ruoyi-ai（28个文件） | ✅ 编译通过 |
| 后端 | AiProvider/AiModel/AiSession/AiMessage 完整 CRUD | ✅ |
| 后端 | AiChatController（send/history/sessions/deleteSession/renameSession） | ✅ |
| 后端 | AiServiceFacade 三路智能路由（Agent/RAG/流式） | ✅ |
| 后端 | ChatModelFactory 多厂商路由 + 双缓存 | ✅ |
| 后端 | ErpQueryService + 3个SQL Tool + 4个业务Tool | ✅ |
| 后端 | AiSseMessageHelper（6种消息类型）+ AiThreadPoolConfig + AiRequestContext | ✅ |
| 后端 | PersistentChatMemoryStore（含去重逻辑） | ✅ |
| 后端 | RAG 知识库完整流水线（6个Service） | ✅ |
| 后端 | VectorStoreService（Milvus/InMemory双模式，按knowledgeId隔离） | ✅ |
| 后端 | RetrieverService（向量+MySQL FULLTEXT+RRF融合） | ✅ |
| 后端 | AiKnowledgeController（CRUD+上传+重试+检索测试） | ✅ |
| 前端 | api/ai/chat.ts + model.ts + knowledge.ts | ✅ |
| 前端 | AiChatDrawer.vue + EchartsRenderer.vue | ✅ |
| 前端 | assistant/index.vue + knowledge/index.vue + model/index.vue + provider.vue | ✅ |
| 前端 | aiRenderers.ts（ECharts安全过滤） | ✅ |
| 前端 | Navbar AI入口图标 | ✅ |
| 前端 | SSE 消息处理（支持 AI_ 类型识别） | ✅ |

### 待完成（本周重点）

| 差距 | 影响 | 优先级 |
|------|------|--------|
| 前端 AI 路由未注册到 router/index.ts | AI 页面无法通过 URL 直接访问 | P0 |
| Milvus 精确删除未实现 | 删除文档后向量残留 | P0 |
| 重排序（Rerank）未集成 | 检索精度不够，enableRerank/rerankModel 字段闲置 | P1 |
| API Key 未 Jasypt 加密存储 | 数据库泄露 = API Key 泄露 | P1 |
| 知识库前端高级配置表单不完整 | 10个RAG参数无法通过前端配置 | P1 |
| 前端检索测试面板参数化不足 | 无法调参测试检索效果 | P2 |
| EmbeddingService 未用策略模式 | 扩展新厂商需改 switch 代码 | P2 |
| 可观测性监听器数量不足 | 缺少 ChatModelListener/EmbeddingModelListener 等 | P2 |
| 全链路集成测试 | 端到端流程未验证 | P1 |

---

## Day 1（周一）：P0 问题修复

### 目标
修复前端路由和 Milvus 精确删除两个 P0 问题。

### 任务 1.1：注册 AI 前端路由

**文件**：`plus-ui/src/router/index.ts`

在路由配置中新增 AI 管理页面路由组：

```typescript
{
  path: '/ai',
  component: Layout,
  redirect: '/ai/assistant',
  name: 'Ai',
  meta: { title: 'AI 智能助手', icon: 'cpu' },
  children: [
    {
      path: 'assistant',
      component: () => import('@/views/ai/assistant/index.vue'),
      name: 'AiAssistant',
      meta: { title: '智能助手', icon: 'chat-dot-round' }
    },
    {
      path: 'model',
      component: () => import('@/views/ai/model/index.vue'),
      name: 'AiModel',
      meta: { title: '模型管理', icon: 'cpu' }
    },
    {
      path: 'model/provider',
      component: () => import('@/views/ai/model/provider.vue'),
      name: 'AiProvider',
      meta: { title: '厂商管理', icon: 'connection', activeMenu: '/ai/model' },
      hidden: true
    },
    {
      path: 'knowledge',
      component: () => import('@/views/ai/knowledge/index.vue'),
      name: 'AiKnowledge',
      meta: { title: '知识库管理', icon: 'notebook' }
    }
  ]
}
```

同时，在 `plus-ui/src/router/index.ts` 文件顶部的路由类型声明中，确认 `Cpu`、`ChatDotRound`、`Notebook`、`Connection` 图标已从 `@element-plus/icons-vue` 导入（用于侧边栏图标渲染）。

> **注意**：如果项目使用 `svg-icon` 方式渲染侧边栏图标，则使用 `svg-icon` 的 `icon-class` 属性而非 Element Plus 图标。根据现有路由风格调整。

### 任务 1.2：实现 Milvus 精确删除

**文件**：`ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/rag/VectorStoreService.java`

当前 `deleteByDocumentId()` 仅打印日志。改造方案：

```java
/**
 * 删除指定文档的所有向量数据
 * <p>通过 metadata 过滤实现：查询匹配 documentId 的所有向量ID，然后逐个删除</p>
 *
 * @param documentId  文档ID
 * @param knowledgeId 知识库ID
 */
public void deleteByDocumentId(Long documentId, Long knowledgeId) {
    if ("milvus".equalsIgnoreCase(getStoreType())) {
        // Milvus: 通过 metadata 过滤删除
        EmbeddingStore<TextSegment> store = getStore(knowledgeId);
        if (store instanceof MilvusEmbeddingStore milvusStore) {
            try {
                // Milvus SDK delete by expr: documentId == xxx
                milvusStore.removeAll(
                    Filter.builder()
                        .eq("documentId", documentId.toString())
                        .build()
                );
                log.info("[VectorStore] Milvus 按文档ID删除向量: documentId={}, knowledgeId={}", documentId, knowledgeId);
            } catch (Exception e) {
                log.error("[VectorStore] Milvus 删除向量失败: documentId={}", documentId, e);
            }
        } else {
            log.warn("[VectorStore] 当前 MilvusStore 实例不支持按条件删除, knowledgeId={}", knowledgeId);
        }
    } else {
        // InMemory: 遍历所有向量，删除匹配 documentId 的条目
        InMemoryEmbeddingStore<TextSegment> store = inMemoryStores.get(knowledgeId);
        if (store != null) {
            // InMemoryEmbeddingStore 不支持按条件删除，需要重建
            // 方案：获取所有非目标文档的向量 → 清空 → 重新添加
            // 由于 InMemory 是测试环境，可直接移除整个知识库缓存
            inMemoryStores.remove(knowledgeId);
            log.info("[VectorStore] InMemory 已移除知识库缓存: knowledgeId={}，下次使用时自动重建", knowledgeId);
        }
    }
}
```

> **备选方案**：如果 `MilvusEmbeddingStore.removeAll(Filter)` 在当前 langchain4j 1.13.0-beta23 版本不可用，可采用**知识库级重建**策略——删除知识库时移除 Collection，重新导入时创建新 Collection。此方案在 P0 可接受。

**备选实现（如果 Filter API 不可用）**：

```java
public void deleteByDocumentId(Long documentId, Long knowledgeId) {
    if ("milvus".equalsIgnoreCase(getStoreType())) {
        // 当前 langchain4j-milvus 版本不支持按 Filter 条件删除
        // 降级方案：清除缓存，提示用户重新导入该知识库
        milvusStores.remove(knowledgeId);
        log.warn("[VectorStore] 已清除 Milvus 知识库缓存 (knowledgeId={})，" +
                 "向量删除需通过 Milvus 管理工具或重新导入知识库完成", knowledgeId);
    } else {
        inMemoryStores.remove(knowledgeId);
        log.info("[VectorStore] InMemory 已移除知识库缓存: knowledgeId={}", knowledgeId);
    }
}
```

同时在 `VectorStoreService` 中新增方法注释说明此限制，并在 `AiKnowledgeController.deleteKnowledge()` 中调用 `vectorStoreService.deleteByKnowledgeId()` 时给出前端提示。

### 交付物
- [ ] `plus-ui/src/router/index.ts` — 新增 `/ai/*` 路由组
- [ ] `VectorStoreService.deleteByDocumentId()` — Milvus/InMemory 精确删除实现

---

## Day 2（周二）：API Key Jasypt 加密存储

### 目标
API Key 使用 Jasypt 加密存储，数据库泄露不影响安全。

### 背景
nexus-erp 已集成 `jasypt-spring-boot-starter 3.0.5`（见 `ruoyi-admin/pom.xml`），配置文件 `application-secret.yml` 中已有敏感信息管理机制。但当前 AiProvider 的 apiKey 字段是明文存储的。

### 任务 2.1：Provider 保存时加密 API Key

**文件**：`ruoyi-common/ruoyi-common-ai/src/main/java/com/nexus/common/ai/service/impl/AiProviderServiceImpl.java`

在 `insertByBo()` 方法中加密 apiKey：

```java
import org.jasypt.encryption.StringEncryptor;

@Autowired
private StringEncryptor stringEncryptor;  // Jasypt 自动配置的 Bean

@Override
public int insertByBo(AiProviderBo bo) {
    AiProvider entity = new AiProvider();
    BeanUtils.copyProperties(bo, entity);
    // 加密 API Key
    if (entity.getApiKey() != null && !entity.getApiKey().isEmpty()) {
        entity.setApiKey(stringEncryptor.encrypt(entity.getApiKey()));
    }
    return baseMapper.insert(entity);
}

@Override
public int updateByBo(AiProviderBo bo) {
    AiProvider entity = new AiProvider();
    BeanUtils.copyProperties(bo, entity);
    // 更新时也加密（如果传入了新的 API Key）
    if (entity.getApiKey() != null && !entity.getApiKey().isEmpty()) {
        entity.setApiKey(stringEncryptor.encrypt(entity.getApiKey()));
    }
    return baseMapper.updateById(entity);
}
```

### 任务 2.2：Provider 查询时解密 API Key

**文件**：同上 `AiProviderServiceImpl.java`

```java
@Override
public AiProviderVo selectById(Long id) {
    AiProvider entity = baseMapper.selectById(id);
    if (entity == null) return null;
    // 解密 API Key 供内部使用
    if (entity.getApiKey() != null && !entity.getApiKey().isEmpty()) {
        try {
            entity.setApiKey(stringEncryptor.decrypt(entity.getApiKey()));
        } catch (Exception e) {
            log.warn("API Key 解密失败，可能为明文存储: providerId={}", id);
        }
    }
    AiProviderVo vo = new AiProviderVo();
    BeanUtils.copyProperties(entity, vo);
    // VO 中 apiKey 脱敏显示（仅显示最后4位）
    vo.setApiKey(maskApiKey(entity.getApiKey()));
    return vo;
}

private String maskApiKey(String apiKey) {
    if (apiKey == null || apiKey.length() <= 8) return "****";
    return "****" + apiKey.substring(apiKey.length() - 4);
}
```

### 任务 2.3：ChatModelFactory/EmbeddingService 解密使用

**文件**：`ChatModelFactory.java` / `EmbeddingService.java`

这两个类从 `AiModel` 实体读取 `apiKey` 字段（通过 `AiModelServiceImpl.fillProviderInfo()` 填充），而 `AiModelServiceImpl` 调用 `AiProviderMapper.selectById()` 获取原始数据。需要在 `AiModelServiceImpl.fillProviderInfo()` 中解密：

```java
// AiModelServiceImpl.java
@Autowired
private StringEncryptor stringEncryptor;

private void fillProviderInfo(AiModel model) {
    AiProvider provider = aiProviderMapper.selectById(model.getProviderId());
    if (provider != null) {
        model.setApiHost(provider.getApiHost());
        // 解密 API Key
        String apiKey = provider.getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            try {
                apiKey = stringEncryptor.decrypt(apiKey);
            } catch (Exception e) {
                log.warn("API Key 解密失败，尝试明文使用: providerId={}", provider.getId());
            }
        }
        model.setApiKey(apiKey);
        model.setProviderType(provider.getProviderType());
    }
}
```

### 任务 2.4：配置文件确认

确认 `application-secret.yml` 中已配置 Jasypt 加密密码：

```yaml
jasypt:
  encryptor:
    password: ${JASYPT_ENCRYPTOR_PASSWORD:nexus-erp-secret}
    algorithm: PBEWithMD5AndDES
    iv-generator-classname: org.jasypt.iv.NoIvGenerator
```

> **生产部署**：通过环境变量 `JASYPT_ENCRYPTOR_PASSWORD` 注入真实密码，不要在配置文件中硬编码。

### 交付物
- [ ] `AiProviderServiceImpl` — insertByBo/updateByBo 加密，selectById 解密+脱敏
- [ ] `AiModelServiceImpl.fillProviderInfo()` — 解密 API Key
- [ ] 确认 Jasypt 加密密码配置

---

## Day 3（周三）：重排序（Rerank）集成

### 目标
补齐 RAG 检索流水线的重排序阶段，提升检索精度。知识库表 `enableRerank`/`rerankModel` 字段已预留，`RetrieverService` 中重排序扩大倍数 `RERANK_EXPANSION_FACTOR=3` 已定义但未使用。

### 任务 3.1：创建 RerankService

**新文件**：`ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/rag/RerankService.java`

```java
package com.nexus.ai.service.rag;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.service.IAiModelService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重排序服务
 * <p>对检索结果进行精排，提升相关性。支持智谱/阿里百炼/SiliconFlow Rerank API</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {

    private final IAiModelService aiModelService;

    /** Rerank 模型缓存（key = modelId） */
    private final ConcurrentHashMap<Long, Object> rerankModelCache = new ConcurrentHashMap<>();

    /**
     * 重排序文档片段
     * <p>Phase 1 采用 Cross-Encoder 方案：将 query+chunk 拼接，由 LLM 打分排序</p>
     * <p>后续可升级为专用 Rerank API（智谱/阿里百炼/SiliconFlow）</p>
     *
     * @param query    查询文本
     * @param chunks   候选片段
     * @param topN     返回 Top N
     * @param modelId  Rerank 模型ID（可为 null，使用默认 CHAT 模型）
     * @return 重排序后的片段列表（按相关性降序）
     */
    public List<String> rerank(String query, List<String> chunks, int topN, Long modelId) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        // Phase 1 方案：使用 LLM 进行相关性打分
        // 将 query + chunk 发送给 LLM，让 LLM 返回 0-10 的相关性分数
        return rerankWithLLM(query, chunks, topN, modelId);
    }

    /**
     * 使用 LLM 进行重排序打分
     * <p>将每个片段与查询组合，由 LLM 评估相关性（0-10分）</p>
     */
    private List<String> rerankWithLLM(String query, List<String> chunks, int topN, Long modelId) {
        // 使用默认聊天模型进行打分
        AiModel config;
        if (modelId != null) {
            config = aiModelService.selectModelById(modelId);
        } else {
            config = aiModelService.selectDefaultModel();
        }
        if (config == null) {
            log.warn("[Rerank] 未找到可用模型，跳过重排序");
            return chunks.subList(0, Math.min(topN, chunks.size()));
        }

        String apiHost = config.getApiHost();
        String apiKey = config.getApiKey();
        ChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl(apiHost)
            .apiKey(apiKey)
            .modelName(config.getModelName())
            .temperature(0.0)  // 重排序使用 temperature=0 保证一致性
            .maxTokens(10)
            .build();

        // 构建打分请求
        List<ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            try {
                String prompt = buildRerankPrompt(query, chunk);
                String response = chatModel.chat(prompt);
                double score = parseScore(response);
                scored.add(new ScoredChunk(chunk, score, i));
                log.debug("[Rerank] chunk[{}] score={}", i, score);
            } catch (Exception e) {
                log.warn("[Rerank] chunk[{}] 打分失败: {}", i, e.getMessage());
                scored.add(new ScoredChunk(chunk, 0.0, i));
            }
        }

        // 按分数降序排列，取 Top N
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored.stream()
            .limit(topN)
            .map(s -> s.content)
            .toList();
    }

    /**
     * 构建重排序提示词
     */
    private String buildRerankPrompt(String query, String chunk) {
        // 截断过长文本（限制 token 消耗）
        String truncatedChunk = chunk.length() > 500 ? chunk.substring(0, 500) : chunk;
        return String.format("""
            Query: %s
            Document: %s

            On a scale of 0 to 10, how relevant is the document to the query?
            Reply with only a number (0-10), nothing else.
            Score:""", query, truncatedChunk);
    }

    /**
     * 解析 LLM 返回的分数
     */
    private double parseScore(String response) {
        if (response == null || response.isEmpty()) return 0;
        try {
            // 提取第一个数字
            String cleaned = response.trim().replaceAll("[^0-9.]", " ").trim();
            if (cleaned.isEmpty()) return 0;
            String[] parts = cleaned.split("\\s+");
            double score = Double.parseDouble(parts[0]);
            return Math.max(0, Math.min(10, score)) / 10.0;  // 归一化到 0-1
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 已打分的片段 */
    private record ScoredChunk(String content, double score, int originalIndex) {}
}
```

> **说明**：Phase 1 使用 LLM 打分方案（Cross-Encoder 替代），优点是无需额外 Rerank API 依赖。后续可升级为专用 Rerank API（智谱 `glm-4-rerank`、阿里百炼 `gte-rerank`、SiliconFlow `BAAI/bge-reranker-v2-m3`），此时可创建 `RerankService` 接口 + 多 Provider 实现。

### 任务 3.2：RetrieverService 集成重排序

**文件**：`ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/rag/RetrieverService.java`

修改 `retrieve(Long knowledgeId, String query)` 方法，在混合检索后增加重排序阶段：

```java
private final RerankService rerankService;  // 新增依赖

public List<String> retrieve(Long knowledgeId, String query) {
    AiKnowledge config = getKnowledgeConfig(knowledgeId);
    int maxResults = config.getRetrieveLimit() != null ? config.getRetrieveLimit() : DEFAULT_MAX_RESULTS;
    double minScore = config.getSimilarityThreshold() != null ? config.getSimilarityThreshold() : DEFAULT_MIN_SCORE;

    // 启用重排序时扩大召回量
    int expandFactor = (config.getEnableRerank() != null && config.getEnableRerank() == 1)
        ? RERANK_EXPANSION_FACTOR : 1;

    List<String> results;
    if (config.getEnableHybrid() != null && config.getEnableHybrid() == 1) {
        results = hybridRetrieve(knowledgeId, query, maxResults * expandFactor, minScore, config.getHybridAlpha());
    } else {
        results = vectorRetrieve(knowledgeId, query, maxResults * expandFactor, minScore);
    }

    // === 新增：重排序阶段 ===
    if (config.getEnableRerank() != null && config.getEnableRerank() == 1 && results.size() > 1) {
        Long rerankModelId = null;
        if (config.getRerankModel() != null && !config.getRerankModel().isEmpty()) {
            AiModel rerankModel = aiModelService.selectModelByName(config.getRerankModel());
            if (rerankModel != null) {
                rerankModelId = rerankModel.getId();
            }
        }
        results = rerankService.rerank(query, results, maxResults, rerankModelId);
        log.info("[Retriever] 重排序完成: knowledgeId={}, 结果数={}", knowledgeId, results.size());
    }

    // 截断到最终数量
    return results.subList(0, Math.min(maxResults, results.size()));
}
```

> **注意**：`RetrieverService` 中 `getKnowledgeConfig()` 方法当前只映射了部分字段。需扩展映射以包含 `rerankModel`：

```java
private AiKnowledge getKnowledgeConfig(Long knowledgeId) {
    var vo = aiKnowledgeService.selectById(knowledgeId);
    if (vo == null) {
        throw new IllegalArgumentException("知识库不存在: " + knowledgeId);
    }
    AiKnowledge config = new AiKnowledge();
    config.setRetrieveLimit(vo.getRetrieveLimit());
    config.setSimilarityThreshold(vo.getSimilarityThreshold());
    config.setEnableRerank(vo.getEnableRerank());
    config.setRerankModel(vo.getRerankModel());       // 新增
    config.setEnableHybrid(vo.getEnableHybrid());
    config.setHybridAlpha(vo.getHybridAlpha());
    return config;
}
```

同时需要在 `AiKnowledge` Entity 中确认 `rerankModel` 字段存在（第三周 DDL 和 Entity 均已包含此字段，确认即可）。

### 交付物
- [ ] `RerankService.java` — LLM 打分重排序实现
- [ ] `RetrieverService.retrieve()` — 集成重排序阶段
- [ ] `RetrieverService.getKnowledgeConfig()` — 补充 rerankModel 映射

---

## Day 4（周四）：前端完善 + 集成测试

### 目标
完善知识库前端高级配置表单，执行全链路集成测试。

### 任务 4.1：知识库高级配置表单完善

**文件**：`plus-ui/src/views/ai/knowledge/index.vue`

当前知识库创建/编辑弹窗需增加 10 个 RAG 配置字段的表单项：

```vue
<!-- 在知识库新增/编辑弹窗中，基础信息字段之后添加高级配置折叠面板 -->
<el-collapse>
  <el-collapse-item title="高级 RAG 配置">
    <el-form-item label="分段大小">
      <el-input-number v-model="form.textBlockSize" :min="100" :max="2000" :step="100" placeholder="500" />
      <span class="form-tip">文本分段字符数，默认 500</span>
    </el-form-item>
    <el-form-item label="重叠字符数">
      <el-input-number v-model="form.overlapChar" :min="0" :max="500" :step="10" placeholder="50" />
      <span class="form-tip">相邻片段重叠字符数，默认 50</span>
    </el-form-item>
    <el-form-item label="分隔符">
      <el-input v-model="form.separator" placeholder="\n\n" />
      <span class="form-tip">段落分隔符，默认 \\n\\n（双换行）</span>
    </el-form-item>
    <el-form-item label="检索返回数">
      <el-input-number v-model="form.retrieveLimit" :min="1" :max="20" placeholder="5" />
    </el-form-item>
    <el-form-item label="相似度阈值">
      <el-input-number v-model="form.similarityThreshold" :min="0" :max="1" :step="0.1" :precision="2" placeholder="0.5" />
    </el-form-item>
    <el-divider />
    <el-form-item label="启用混合检索">
      <el-switch v-model="form.enableHybrid" :active-value="1" :inactive-value="0" />
      <span class="form-tip">结合向量检索和关键词全文检索</span>
    </el-form-item>
    <el-form-item label="混合检索权重" v-if="form.enableHybrid === 1">
      <el-slider v-model="form.hybridAlpha" :min="0" :max="1" :step="0.1" :precision="1" show-input />
      <span class="form-tip">0=纯向量，1=纯关键词，默认 0.5</span>
    </el-form-item>
    <el-divider />
    <el-form-item label="启用重排序">
      <el-switch v-model="form.enableRerank" :active-value="1" :inactive-value="0" />
      <span class="form-tip">对召回结果进行精排（需配置重排序模型）</span>
    </el-form-item>
    <el-form-item label="重排序模型" v-if="form.enableRerank === 1">
      <el-select v-model="form.rerankModel" placeholder="使用默认模型" clearable>
        <el-option v-for="m in modelOptions" :key="m.modelName" :label="m.displayName || m.modelName" :value="m.modelName" />
      </el-select>
    </el-form-item>
    <el-divider />
    <el-form-item label="系统提示词">
      <el-input v-model="form.systemPrompt" type="textarea" :rows="4" placeholder="你是一个专业的知识库助手..." />
      <span class="form-tip">RAG 问答时的系统提示词（可选）</span>
    </el-form-item>
  </el-collapse-item>
</el-collapse>
```

### 任务 4.2：前端检索测试面板参数化

**文件**：同上 `knowledge/index.vue`

在检索测试区域增加参数控制：

```vue
<!-- 检索测试面板 -->
<el-card header="检索测试" style="margin-top: 16px">
  <el-form :inline="true">
    <el-form-item label="Top K">
      <el-input-number v-model="testParams.topK" :min="1" :max="20" size="small" />
    </el-form-item>
    <el-form-item label="阈值">
      <el-input-number v-model="testParams.threshold" :min="0" :max="1" :step="0.1" :precision="2" size="small" />
    </el-form-item>
    <el-form-item label="混合检索">
      <el-switch v-model="testParams.enableHybrid" size="small" />
    </el-form-item>
    <el-form-item label="重排序">
      <el-switch v-model="testParams.enableRerank" size="small" />
    </el-form-item>
  </el-form>
  <el-input v-model="testParams.query" placeholder="输入测试查询..." @keydown.enter="doRetrievalTest">
    <template #append>
      <el-button :icon="Search" @click="doRetrievalTest" :loading="testLoading">检索</el-button>
    </template>
  </el-input>
  <div v-if="testResults.length > 0" style="margin-top: 12px">
    <div v-for="(item, idx) in testResults" :key="idx" class="test-result-item">
      <el-tag size="small" type="info">#{{ idx + 1 }}</el-tag>
      <span class="test-content">{{ item }}</span>
    </div>
  </div>
</el-card>
```

### 任务 4.3：全链路集成测试

编写并执行以下测试场景：

#### 基础功能测试

| 序号 | 测试项 | 操作步骤 | 预期结果 |
|------|--------|----------|----------|
| 1 | 前端路由访问 | 直接访问 `/ai/assistant`、`/ai/model`、`/ai/knowledge` | 页面正常渲染 |
| 2 | 厂商 CRUD | 新增厂商 → 编辑 → 查看 API Key 脱敏 → 删除 | API Key 显示 `****xxxx` |
| 3 | 模型 CRUD | 新增模型 → 设默认 → 删除 | 默认模型切换正常 |
| 4 | Jasypt 加密 | 新增厂商后查看数据库 ai_provider.api_key | 数据库存储为 `ENC(...)` 密文 |
| 5 | 模型工厂解密 | 发送 AI 消息 | 正常调用 LLM（说明解密成功） |

#### 聊天功能测试

| 序号 | 测试项 | 操作步骤 | 预期结果 |
|------|--------|----------|----------|
| 6 | 普通流式对话 | 发送"你好" | 逐 Token 流式推送（AI_CONTENT_INCREMENT） |
| 7 | Agent 数据查询 | 发送"查询 ERP 数据库中有哪些表" | SupervisorAgent → queryAllTables → 返回表列表 |
| 8 | Agent 报表生成 | 发送"本月采购订单按供应商分布" | ErpReportAgent → ```echarts 代码块 |
| 9 | ECharts 渲染 | 查看 AI 回复 | ```echarts 自动渲染为图表 |
| 10 | 多轮对话 | 发送"你好"→"我刚才说了什么" | 上下文记忆正常 |
| 11 | 会话管理 | 右键重命名/删除会话 | 操作成功，列表刷新 |
| 12 | 错误处理 | 停用厂商后发送消息 | AI_ERROR 消息提示 |

#### RAG 知识库测试

| 序号 | 测试项 | 操作步骤 | 预期结果 |
|------|--------|----------|----------|
| 13 | 知识库 CRUD | 创建→编辑→删除知识库 | 级联删除文档和片段 |
| 14 | 文档上传 | 上传 PDF/Word/TXT | OSS 持久化 → 异步解析 → 状态变为"已完成" |
| 15 | 文档重试 | 上传不可读文件 → 失败 → 重试 | 删除旧片段 → 重新解析 |
| 16 | RAG 问答 | 在知识库页面上传文档 → 助手页面提问 | AI 基于知识库内容回答 |
| 17 | 混合检索 | 启用混合检索的知识库提问 | 向量+全文+RRF 融合 |
| 18 | 重排序 | 启用重排序的知识库提问 | 检索结果经 RerankService 精排 |
| 19 | 检索测试面板 | 在知识库页面的检索测试中输入查询 | 返回相关片段列表 |

#### 安全测试

| 序号 | 测试项 | 操作步骤 | 预期结果 |
|------|--------|----------|----------|
| 20 | SQL 注入防护 | AI 生成 `DROP TABLE` | ExecuteSqlQueryTool 拦截 |
| 21 | 租户隔离 | 切换租户后查询 | 仅返回当前租户数据 |
| 22 | 会话归属校验 | 尝试访问其他用户的会话 | 返回"无权访问" |

### 交付物
- [ ] `knowledge/index.vue` — 高级配置表单 + 参数化检索测试面板
- [ ] 全链路集成测试报告（20项测试）

---

## Day 5（周五）：优化 + Bug 修复 + 文档

### 目标
根据集成测试结果修复 Bug，优化体验，更新文档。

### 任务 5.1：Bug 修复

根据 Day 4 测试结果，修复发现的问题。

重点关注：
- API Key 加密/解密异常处理（明文降级兼容）
- 重排序 LLM 调用超时处理
- 知识库文档上传大文件 OOM
- 前端 SSE 重连机制

### 任务 5.2：EmbeddingService 策略模式重构（P2）

**重构目标**：将 `EmbeddingService.buildEmbeddingModel()` 中的 switch 分支改为策略模式，便于扩展新厂商。

**新文件**：
- `EmbeddingProvider.java` — 策略接口
- `OpenAiEmbeddingProvider.java` — OpenAI 兼容实现
- `OllamaEmbeddingProvider.java` — Ollama 实现
- `EmbeddingProviderFactory.java` — 工厂类

```java
// 策略接口
public interface EmbeddingProvider {
    String getProviderType();
    EmbeddingModel build(AiModel config);
}

// 工厂类
@Component
public class EmbeddingProviderFactory {
    private final Map<String, EmbeddingProvider> providers;

    public EmbeddingProviderFactory(List<EmbeddingProvider> providerList) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(
                p -> p.getProviderType().toUpperCase(),
                Function.identity()
            ));
    }

    public EmbeddingModel build(AiModel config) {
        String type = config.getProviderType() != null
            ? config.getProviderType().toUpperCase() : "OPENAI";
        EmbeddingProvider provider = providers.getOrDefault(type, providers.get("OPENAI"));
        return provider.build(config);
    }
}
```

> **注意**：此任务为 P2，如 Day 5 时间紧张可延至第五周。

### 任务 5.3：可观测性增强（P2）

参考 ruoyi-ai 的 observability 目录，新增以下 Listener：

| 新文件 | 说明 |
|--------|------|
| `MyChatModelListener.java` | LLM 调用耗时/Token 统计 |
| `MyEmbeddingModelListener.java` | Embedding 调用统计 |
| `MyToolExecutedListener.java` | Tool 调用链追踪 |

通过 `LangChain4jObservabilityConfig` 注册到 LangChain4j 监听器体系（而非当前自研的 `AiAgentListener` 内存统计）。

> **注意**：此任务为 P2，如 Day 5 时间紧张可延至第五周。

### 任务 5.4：更新开发计划文档

更新 `NexusERP_AI开发计划.md`：
- 标记 Phase 0（第 1-3 周）状态为 ✅ 已完成
- 更新 Phase 1（第 4-8 周）进度为 Week 4 完成状态
- 在修订记录中新增 v4 条目

### 交付物
- [ ] Bug 修复记录
- [ ] `EmbeddingProvider` 策略模式重构（P2，可选）
- [ ] `MyChatModelListener` 等可观测性增强（P2，可选）
- [ ] 更新 `NexusERP_AI开发计划.md`

---

## 文件变更总览

### 后端新增文件（5个）

| 文件 | 模块 | 说明 |
|------|------|------|
| `RerankService.java` | ruoyi-ai | 重排序服务（LLM Cross-Encoder 方案） |
| `EmbeddingProvider.java` | ruoyi-ai | 嵌入模型策略接口（P2 可选） |
| `OpenAiEmbeddingProvider.java` | ruoyi-ai | OpenAI 兼容嵌入实现（P2 可选） |
| `OllamaEmbeddingProvider.java` | ruoyi-ai | Ollama 嵌入实现（P2 可选） |
| `EmbeddingProviderFactory.java` | ruoyi-ai | 嵌入模型工厂（P2 可选） |

### 后端修改文件（4个）

| 文件 | 修改内容 |
|------|----------|
| `VectorStoreService.java` | deleteByDocumentId 实现 Milvus/InMemory 精确删除 |
| `AiProviderServiceImpl.java` | insertByBo/updateByBo Jasypt 加密，selectById 解密+脱敏 |
| `AiModelServiceImpl.java` | fillProviderInfo 解密 API Key |
| `RetrieverService.java` | retrieve 集成 RerankService，getKnowledgeConfig 补充 rerankModel |

### 前端修改文件（2个）

| 文件 | 修改内容 |
|------|----------|
| `router/index.ts` | 新增 `/ai/assistant`、`/ai/model`、`/ai/model/provider`、`/ai/knowledge` 路由 |
| `views/ai/knowledge/index.vue` | 高级 RAG 配置表单 + 参数化检索测试面板 |

### 配置文件修改（1个）

| 文件 | 修改内容 |
|------|----------|
| `application-secret.yml` | 确认 Jasypt 加密密码配置 |

---

## 第五周展望

| 任务 | 说明 |
|------|------|
| ERP Schema 注释增强 | 为 AI 准备简化版业务 Schema 说明（bd_material/so_sales_order_header 等核心表） |
| ErpQueryAgent SystemMessage 优化 | 嵌入业务 Schema 说明，提升 Text2SQL 准确率 |
| 知识库预置内容导入 | 将 `NexusERP_业务流转分析.md`、`NexusERP_编码生成规则设计方案.md` 导入知识库 |
| ERP 业务 Tool 真实 SQL 适配 | 根据实际表结构调整 Tool 中的 SQL 查询 |
| EmbeddingService 策略模式 | 如 Day 5 未完成，延至本周 |
| 可观测性增强 | 如 Day 5 未完成，延至本周 |
| Weaviate/Qdrant 向量存储 | 扩展 VectorStoreService 策略模式 |
| MCP 工具市场 | 参考 ruoyi-ai 的 MCP 模块实现 |

---

## 风险与应对

| 风险 | 应对措施 |
|------|----------|
| **Milvus SDK Filter API 不可用** | 降级为知识库级重建策略，在删除文档时提示用户重新导入知识库 |
| **Jasypt 解密失败（存量明文数据）** | try-catch 降级，解密失败时使用明文；同时提供数据迁移脚本将明文 Key 批量加密 |
| **Rerank LLM 调用增加延迟** | 并行打分（CompletableFuture + aiExecutor），设置 3 秒超时 |
| **重排序 Token 消耗大** | 截断 chunk 到 500 字符，限制候选数不超过 15 条 |
| **前端路由权限控制** | AI 页面暂时不设权限（permissions 字段留空），后续接入权限体系 |
| **vue-element-plus-x 兼容性** | 本周不使用 vue-element-plus-x，全部使用 Element Plus 原生组件 |

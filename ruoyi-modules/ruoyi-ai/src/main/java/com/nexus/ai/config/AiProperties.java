package com.nexus.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 配置属性
 * <p>前缀 nexus.ai，避免与 Spring AI 的 spring.ai 冲突</p>
 * <p>包含默认厂商/模型、SQL 安全配置、向量存储（Milvus/InMemory）配置</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "nexus.ai")
public class AiProperties {

    /** 默认厂商 */
    private String defaultProvider = "deepseek";

    /** 默认模型 */
    private String defaultModel = "deepseek-chat";

    /** 允许查询的表名前缀 */
    private List<String> allowedTablePrefixes = List.of("bd_", "so_", "pu_", "prd_", "wm_", "sch_");

    /** SQL 执行超时（毫秒） */
    private int sqlTimeout = 5000;

    /** SQL 最大返回行数 */
    private int sqlMaxRows = 1000;

    /** 向量存储配置 */
    private VectorStoreConfig vectorStore = new VectorStoreConfig();

    /** 推理模式配置 */
    private ThinkingConfig thinking = new ThinkingConfig();

    /** 联网搜索配置 */
    private WebSearchConfig webSearch = new WebSearchConfig();

    /** 文生图配置 */
    private ImageConfig image = new ImageConfig();

    /** 知识图谱配置 */
    private KnowledgeGraphConfig knowledgeGraph = new KnowledgeGraphConfig();

    @Data
    public static class VectorStoreConfig {
        /** 向量存储类型：milvus / inmemory */
        private String type = "inmemory";

        /** Milvus 配置 */
        private MilvusConfig milvus = new MilvusConfig();

        /** InMemory 配置 */
        private InMemoryConfig inmemory = new InMemoryConfig();
    }

    @Data
    public static class MilvusConfig {
        /** Milvus 连接地址 */
        private String url = "http://localhost:19530";

        /** 集合名称前缀 */
        private String collectionName = "AiKnowledge";

        /** 向量维度（根据 Embedding 模型自动推断，此处为默认值） */
        private int dimension = 1024;
    }

    @Data
    public static class InMemoryConfig {
        /** 是否启用 InMemory 模式（开发/测试用） */
        private boolean enabled = true;
    }

    @Data
    public static class ThinkingConfig {
        /** 是否开启推理模式 */
        private boolean enabled = true;
        /** 默认是否开启（用户主动选择时覆盖） */
        private boolean defaultOn = false;
    }

    @Data
    public static class WebSearchConfig {
        /** 是否开启联网搜索 */
        private boolean enabled = true;
        /** 搜索厂商：tavily | bing */
        private String provider = "tavily";
        /** 搜索 API Key */
        private String apiKey = "";
        /** 返回结果数 */
        private int topN = 5;
        /** 请求超时（毫秒） */
        private int timeoutMs = 15000;
    }

    @Data
    public static class ImageConfig {
        /** 是否开启文生图 */
        private boolean enabled = true;
        /** 默认厂商（对应 IImageGenerationService.getProviderName()） */
        private String defaultProvider = "Tongyiwanx";
    }

    /**
     * 知识图谱配置（前缀 nexus.ai.knowledge-graph）
     */
    @Data
    public static class KnowledgeGraphConfig {
        /** 是否开启知识图谱 */
        private boolean enabled = true;
        /** 存储类型：mysql | neo4j（v1 用 mysql，Neo4j 经 GraphStoreStrategy 预留） */
        private String storage = "mysql";
        /** 每批抽取 chunk 数 */
        private int batchSize = 5;
        /** 每个 chunk 最多抽取实体数 */
        private int maxEntitiesPerChunk = 20;
        /** 每个 chunk 最多抽取关系数 */
        private int maxRelationsPerChunk = 30;
        /** 置信度阈值（低于此值的实体/关系丢弃） */
        private double confidenceThreshold = 0.5;
        /** 抽取用 LLM 模型ID（null 时用默认 chat 模型，建议配便宜模型） */
        private Long extractionModelId = null;
        /** 图谱召回在 RRF 中的权重 */
        private double graphWeight = 0.5;
        /** 单次局部召回最大邻居实体数 */
        private int maxNeighbors = 20;
        /** 标签传播迭代上限 */
        private int labelPropagationMaxIter = 8;
        /** 社区数上限（超出按大小合并小社区） */
        private int maxCommunities = 50;
        /** 全局检索参与 map 的社区上限（超出按 entity_count 取 topN） */
        private int maxCommunitiesForGlobal = 50;
        /** v1.5 预留：实体 gleaning 二次抽取 */
        private boolean gleaningEnabled = false;
    }
}

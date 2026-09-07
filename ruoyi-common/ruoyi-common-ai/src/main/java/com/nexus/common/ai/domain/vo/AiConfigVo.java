package com.nexus.common.ai.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * AI 运行参数配置视图对象
 * <p>「AI 参数设置」页回显：从 AiProperties 当前生效值构建（yml 兜底 + ai_config 表覆盖后的合并结果）</p>
 * <p>webSearch 不回显 apiKey 明文，仅返回 apiKeySet 标识是否已配置</p>
 *
 * @author NexusERP
 */
@Data
public class AiConfigVo {

    /** SQL 安全配置 */
    private SqlSafetyVo sqlSafety;

    /** 向量存储配置 */
    private VectorStoreVo vectorStore;

    /** 联网搜索配置 */
    private WebSearchVo webSearch;

    /** 流程编排配置 */
    private FlowVo flow;

    /** 知识图谱配置 */
    private KnowledgeGraphVo knowledgeGraph;

    @Data
    public static class SqlSafetyVo {

        /** 允许查询的表名前缀 */
        private List<String> allowedTablePrefixes;

        /** SQL 执行超时（毫秒） */
        private Integer sqlTimeout;

        /** SQL 最大返回行数 */
        private Integer sqlMaxRows;
    }

    @Data
    public static class VectorStoreVo {

        /** 向量存储类型：milvus / inmemory */
        private String type;

        /** Milvus 连接地址 */
        private String milvusUrl;

        /** Milvus 集合名称前缀 */
        private String milvusCollectionName;

        /** 向量维度 */
        private Integer milvusDimension;
    }

    @Data
    public static class WebSearchVo {

        /** 是否开启联网搜索 */
        private Boolean enabled;

        /** 搜索厂商：tavily | bing */
        private String provider;

        /** 是否已配置 API Key（明文不回显） */
        private Boolean apiKeySet;

        /** 返回结果数 */
        private Integer topN;

        /** 请求超时（毫秒） */
        private Integer timeoutMs;
    }

    @Data
    public static class FlowVo {

        /** 是否启用并行分支执行 */
        private Boolean parallel;
    }

    @Data
    public static class KnowledgeGraphVo {

        /** 是否开启知识图谱 */
        private Boolean enabled;

        /** 存储类型：mysql | neo4j */
        private String storage;

        /** 每批抽取 chunk 数 */
        private Integer batchSize;

        /** 每个 chunk 最多抽取实体数 */
        private Integer maxEntitiesPerChunk;

        /** 每个 chunk 最多抽取关系数 */
        private Integer maxRelationsPerChunk;

        /** 置信度阈值 */
        private Double confidenceThreshold;

        /** 抽取用 LLM 模型ID（null 用默认 chat 模型） */
        private Long extractionModelId;

        /** 图谱召回在 RRF 中的权重 */
        private Double graphWeight;

        /** 单次局部召回最大邻居实体数 */
        private Integer maxNeighbors;

        /** 标签传播迭代上限 */
        private Integer labelPropagationMaxIter;

        /** 社区数上限 */
        private Integer maxCommunities;

        /** 全局检索参与 map 的社区上限 */
        private Integer maxCommunitiesForGlobal;
    }
}

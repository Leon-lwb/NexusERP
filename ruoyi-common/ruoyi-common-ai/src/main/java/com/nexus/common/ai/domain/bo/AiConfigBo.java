package com.nexus.common.ai.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * AI 运行参数配置业务对象
 * <p>对应「AI 参数设置」页整表提交，按分组落 ai_config 表并回填 AiProperties 即时生效</p>
 * <p>webSearch.apiKey 为写专用：传空/null 表示保留原值，后端不回显明文</p>
 *
 * @author NexusERP
 */
@Data
public class AiConfigBo {

    /** SQL 安全配置 */
    @NotNull
    @Valid
    private SqlSafetyBo sqlSafety;

    /** 向量存储配置 */
    @NotNull
    @Valid
    private VectorStoreBo vectorStore;

    /** 联网搜索配置 */
    @NotNull
    @Valid
    private WebSearchBo webSearch;

    /** 流程编排配置 */
    @NotNull
    @Valid
    private FlowBo flow;

    /** 知识图谱配置 */
    @NotNull
    @Valid
    private KnowledgeGraphBo knowledgeGraph;

    /** 工业决策配置（整表提交，防止将未验证的权重持久化） */
    @NotNull
    @Valid
    private DecisionBo decision;

    /**
     * SQL 安全配置（AI 数据查询的白名单前缀/超时/行数上限）
     */
    @Data
    public static class SqlSafetyBo {

        /** 允许查询的表名前缀 */
        @NotEmpty
        private List<String> allowedTablePrefixes;

        /** SQL 执行超时（毫秒） */
        @NotNull
        @Min(value = 100, message = "SQL 超时不能低于 100ms")
        @Max(value = 600000, message = "SQL 超时不能超过 10 分钟")
        private Integer sqlTimeout;

        /** SQL 最大返回行数 */
        @NotNull
        @Min(value = 1, message = "最大返回行数至少为 1")
        @Max(value = 1000000, message = "最大返回行数不能超过 100 万")
        private Integer sqlMaxRows;
    }

    /**
     * 向量存储配置
     * <p>⚠️ 切换 type 或修改 dimension 会导致既有向量不可检索/需重建索引</p>
     */
    @Data
    public static class VectorStoreBo {

        /** 向量存储类型：milvus / inmemory */
        @NotBlank
        @Pattern(regexp = "milvus|inmemory", message = "向量存储类型仅支持 milvus / inmemory")
        private String type;

        /** Milvus 连接地址 */
        @NotBlank
        private String milvusUrl;

        /** Milvus 集合名称前缀 */
        @NotBlank
        private String milvusCollectionName;

        /** 向量维度（legacy 未配置 embedding_dimension 模型的全局兜底） */
        @NotNull
        @Min(value = 1, message = "向量维度至少为 1")
        @Max(value = 65536, message = "向量维度不能超过 65536")
        private Integer milvusDimension;
    }

    /**
     * 联网搜索配置
     */
    @Data
    public static class WebSearchBo {

        /** 是否开启联网搜索 */
        @NotNull
        private Boolean enabled;

        /** 搜索厂商：tavily | bing */
        @NotBlank
        @Pattern(regexp = "tavily|bing", message = "搜索厂商仅支持 tavily / bing")
        private String provider;

        /** 搜索 API Key（写专用：空/null 表示保留原值） */
        private String apiKey;

        /** 返回结果数 */
        @NotNull
        @Min(value = 1, message = "返回结果数至少为 1")
        @Max(value = 20, message = "返回结果数不能超过 20")
        private Integer topN;

        /** 请求超时（毫秒） */
        @NotNull
        @Min(value = 1000, message = "请求超时不能低于 1 秒")
        @Max(value = 120000, message = "请求超时不能超过 2 分钟")
        private Integer timeoutMs;
    }

    /**
     * 流程编排配置
     */
    @Data
    public static class FlowBo {

        /** 是否启用并行分支执行（含循环的图强制串行） */
        @NotNull
        private Boolean parallel;
    }

    /**
     * 知识图谱配置
     */
    @Data
    public static class KnowledgeGraphBo {

        /** 是否开启知识图谱 */
        @NotNull
        private Boolean enabled;

        /** 存储类型：mysql | neo4j（neo4j 经 GraphStoreStrategy 预留） */
        @NotBlank
        @Pattern(regexp = "mysql|neo4j", message = "存储类型仅支持 mysql / neo4j")
        private String storage;

        /** 每批抽取 chunk 数 */
        @NotNull
        @Min(value = 1, message = "批量抽取 chunk 数至少为 1")
        @Max(value = 100, message = "批量抽取 chunk 数不能超过 100")
        private Integer batchSize;

        /** 每个 chunk 最多抽取实体数 */
        @NotNull
        @Min(1)
        @Max(200)
        private Integer maxEntitiesPerChunk;

        /** 每个 chunk 最多抽取关系数 */
        @NotNull
        @Min(1)
        @Max(200)
        private Integer maxRelationsPerChunk;

        /** 置信度阈值（低于此值的实体/关系丢弃） */
        @NotNull
        @DecimalMin(value = "0.0", message = "置信度阈值范围 0-1")
        @DecimalMax(value = "1.0", message = "置信度阈值范围 0-1")
        private Double confidenceThreshold;

        /** 抽取用 LLM 模型ID（空则用默认 chat 模型，建议配便宜模型） */
        private Long extractionModelId;

        /** 图谱召回在 RRF 中的权重 */
        @NotNull
        @DecimalMin(value = "0.0", message = "图谱权重范围 0-1")
        @DecimalMax(value = "1.0", message = "图谱权重范围 0-1")
        private Double graphWeight;

        /** 单次局部召回最大邻居实体数 */
        @NotNull
        @Min(1)
        @Max(200)
        private Integer maxNeighbors;

        /** 标签传播迭代上限 */
        @NotNull
        @Min(1)
        @Max(100)
        private Integer labelPropagationMaxIter;

        /** 社区数上限（超出按大小合并小社区） */
        @NotNull
        @Min(1)
        @Max(1000)
        private Integer maxCommunities;

        /** 全局检索参与 map 的社区上限（超出按 entity_count 取 topN） */
        @NotNull
        @Min(1)
        @Max(1000)
        private Integer maxCommunitiesForGlobal;
    }

    @Data
    public static class DecisionBo {
        @NotNull private Boolean enabled;
        @NotNull @Min(1) @Max(16) private Integer maxConcurrentRuns;
        @NotNull @Min(2) @Max(8) private Integer maxScenarios;
        @NotNull @Min(10) @Max(300) private Integer analysisTimeoutSeconds;
        @NotNull @Min(5) @Max(120) private Integer actionTimeoutSeconds;
        @NotNull @Min(30) @Max(3600) private Integer staleThresholdSeconds;
        @NotEmpty private List<@NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String> requireApprovalLevels;
        @NotNull private Boolean allowAutoLowRiskNotify;
        @NotNull private Boolean benchmarkMode;
        @NotNull @Min(1) @Max(90) private Integer defaultHorizonDays;
        @NotNull @Min(1) @Max(90) private Integer maxHorizonDays;
        @jakarta.validation.constraints.Positive private Long policyKnowledgeId;
        @NotNull @Valid private DecisionRiskBo risk;
        @NotNull @Valid private DecisionRankingBo ranking;
    }

    @Data
    public static class DecisionRiskBo {
        @NotNull @Min(0) @Max(100) private Integer tardinessPoints;
        @NotNull @Min(0) @Max(100) private Integer shortagePoints;
        @NotNull @Min(0) @Max(100) private Integer capacityPoints;
        @NotNull @Min(0) @Max(100) private Integer supplierPoints;
    }

    @Data
    public static class DecisionRankingBo {
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") private Double tardinessWeight;
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") private Double delayedCountWeight;
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") private Double shortageWeight;
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") private Double utilizationWeight;
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") private Double actionCostWeight;
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") private Double planChangeWeight;
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") private Double infeasibleWeight;
    }

}

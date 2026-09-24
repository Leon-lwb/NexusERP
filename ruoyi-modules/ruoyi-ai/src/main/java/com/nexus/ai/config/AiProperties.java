package com.nexus.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 配置属性
 * <p>前缀 nexus.ai，避免与 Spring AI 的 spring.ai 冲突</p>
 * <p>仅承载运行参数类配置（SQL 安全/向量存储/联网搜索/流程编排/知识图谱）；
 * 默认厂商与模型由 ai_provider/ai_model 表管理（"厂商/模型管理"页面），不再走 yml</p>
 * <p>以上配置均可在前端「AI 参数设置」页修改并即时生效（落 ai_config 表后回填本 bean，
 * yml 值仅作首次启动兜底默认），消费方一律调用时读取 getter，勿缓存基本类型快照</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "nexus.ai")
public class AiProperties {

    /** 允许查询的表名前缀 */
    private List<String> allowedTablePrefixes = List.of("bd_", "so_", "pu_", "prd_", "wm_", "sch_");

    /** SQL 执行超时（毫秒） */
    private int sqlTimeout = 5000;

    /** SQL 最大返回行数 */
    private int sqlMaxRows = 1000;

    /** 向量存储配置 */
    private VectorStoreConfig vectorStore = new VectorStoreConfig();

    /** 联网搜索配置 */
    private WebSearchConfig webSearch = new WebSearchConfig();

    /** 流程编排配置 */
    private FlowConfig flow = new FlowConfig();

    /** 知识图谱配置 */
    private KnowledgeGraphConfig knowledgeGraph = new KnowledgeGraphConfig();

    /** 工业决策运行与评分配置 */
    private DecisionConfig decision = new DecisionConfig();

    @Data
    public static class VectorStoreConfig {
        /** 向量存储类型：milvus / inmemory */
        private String type = "inmemory";

        /** Milvus 配置 */
        private MilvusConfig milvus = new MilvusConfig();
    }

    @Data
    public static class MilvusConfig {
        /** Milvus 连接地址 */
        private String url = "http://localhost:19530";

        /** 集合名称前缀 */
        private String collectionName = "AiKnowledge";

        /** 向量维度（legacy 未配置 embedding_dimension 的 Embedding 模型的全局兜底默认值） */
        private int dimension = 1024;
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

    /**
     * 流程编排配置（前缀 nexus.ai.flow）
     */
    @Data
    public static class FlowConfig {
        /** 是否启用并行分支执行（默认 false 串行；含循环的图强制串行） */
        private boolean parallel = false;
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
    }

    /** 工业交付风险决策：yml 初始值，运行时可由 ai_config 决策分组覆盖。 */
    @Data
    public static class DecisionConfig {
        private boolean enabled = true;
        private int maxConcurrentRuns = 4;
        private int maxScenarios = 4;
        private int analysisTimeoutSeconds = 60;
        private int actionTimeoutSeconds = 30;
        private int staleThresholdSeconds = 300;
        private List<String> requireApprovalLevels = List.of("HIGH", "CRITICAL");
        private boolean allowAutoLowRiskNotify = true;
        private boolean benchmarkMode = false;
        private int defaultHorizonDays = 30;
        private int maxHorizonDays = 90;
        private Long policyKnowledgeId;
        private DecisionRiskConfig risk = new DecisionRiskConfig();
        private DecisionRankingConfig ranking = new DecisionRankingConfig();
    }

    @Data
    public static class DecisionRiskConfig {
        private int tardinessPoints = 40;
        private int shortagePoints = 30;
        private int capacityPoints = 20;
        private int supplierPoints = 10;

        public int pointSum() {
            return tardinessPoints + shortagePoints + capacityPoints + supplierPoints;
        }
    }

    @Data
    public static class DecisionRankingConfig {
        private double tardinessWeight = 0.30;
        private double delayedCountWeight = 0.20;
        private double shortageWeight = 0.15;
        private double utilizationWeight = 0.10;
        private double actionCostWeight = 0.10;
        private double planChangeWeight = 0.10;
        private double infeasibleWeight = 0.05;

        public double weightSum() {
            return tardinessWeight + delayedCountWeight + shortageWeight + utilizationWeight
                + actionCostWeight + planChangeWeight + infeasibleWeight;
        }
    }

}

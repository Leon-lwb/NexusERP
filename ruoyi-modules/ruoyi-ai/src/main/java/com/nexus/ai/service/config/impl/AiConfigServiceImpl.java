package com.nexus.ai.service.config.impl;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.config.IAiConfigService;
import com.nexus.ai.service.query.ErpTableSchemaManager;
import com.nexus.ai.service.rag.VectorStoreService;
import com.nexus.common.ai.domain.bo.AiConfigBo;
import com.nexus.common.ai.domain.vo.AiConfigVo;
import com.nexus.common.ai.domain.entity.AiConfig;
import com.nexus.common.ai.mapper.AiConfigMapper;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.redis.utils.RedisUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 运行参数配置服务实现
 * <p>配置模型：ai_config 表按分组存 JSON（分组键见 {@code GROUP_*} 常量），
 * 启动时全量加载回填 {@link AiProperties}（ApplicationRunner，先于 ApplicationReadyEvent 的
 * ERP 表结构缓存加载），无记录的组沿用 yml 兜底默认值；此后以表为准</p>
 * <p>即时生效：所有消费方均调用时读取 AiProperties 的读取方法，回填 bean 字段即生效；
 * 需联动的缓存 —— 向量库连接参数变更逐出 Milvus 客户端缓存（缓存 key 不含连接参数），
 * 表前缀变更刷新 ERP 表结构缓存（按前缀全量加载）</p>
 * <p>多节点：修改后通过 Redis 发布订阅 广播，各节点收到后从库重载回填（ai_config 已加入
 * tenant.excludes，无租户上下文的订阅回调线程亦可查询）</p>
 *
 * @author NexusERP
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AiConfigServiceImpl implements IAiConfigService, ApplicationRunner {

    /** 配置分组：SQL 安全（AI 数据查询白名单前缀/超时/行数上限） */
    public static final String GROUP_SQL_SAFETY = "sql_safety";
    /** 配置分组：向量存储 */
    public static final String GROUP_VECTOR_STORE = "vector_store";
    /** 配置分组：联网搜索 */
    public static final String GROUP_WEB_SEARCH = "web_search";
    /** 配置分组：流程编排 */
    public static final String GROUP_FLOW = "flow";
    /** 配置分组：知识图谱 */
    public static final String GROUP_KNOWLEDGE_GRAPH = "knowledge_graph";

    /** 工业决策参数：风险评分、方案权重与执行护栏。 */
    public static final String GROUP_DECISION = "decision";

    /** 多节点配置刷新广播通道 */
    private static final String CONFIG_REFRESH_CHANNEL = "ai:config:refresh";

    private final AiConfigMapper aiConfigMapper;
    private final AiProperties aiProperties;
    private final VectorStoreService vectorStoreService;
    private final ErpTableSchemaManager erpTableSchemaManager;

    @Override
    public void run(ApplicationArguments args) {
        loadFromDb("启动加载");
        // 多节点同步：其他节点保存配置后广播本通道，本节点从库重载
        RedisUtils.subscribe(CONFIG_REFRESH_CHANNEL, String.class, msg -> {
            log.info("[AiConfig] 收到配置刷新广播: {}", msg);
            loadFromDb("广播重载");
        });
    }

    @Override
    public AiConfigVo getConfig() {
        AiConfigVo vo = new AiConfigVo();

        AiConfigVo.SqlSafetyVo sqlSafety = new AiConfigVo.SqlSafetyVo();
        sqlSafety.setAllowedTablePrefixes(aiProperties.getAllowedTablePrefixes());
        sqlSafety.setSqlTimeout(aiProperties.getSqlTimeout());
        sqlSafety.setSqlMaxRows(aiProperties.getSqlMaxRows());
        vo.setSqlSafety(sqlSafety);

        AiProperties.VectorStoreConfig vectorStore = aiProperties.getVectorStore();
        AiConfigVo.VectorStoreVo vectorVo = new AiConfigVo.VectorStoreVo();
        vectorVo.setType(vectorStore.getType());
        vectorVo.setMilvusUrl(vectorStore.getMilvus().getUrl());
        vectorVo.setMilvusCollectionName(vectorStore.getMilvus().getCollectionName());
        vectorVo.setMilvusDimension(vectorStore.getMilvus().getDimension());
        vo.setVectorStore(vectorVo);

        AiProperties.WebSearchConfig webSearch = aiProperties.getWebSearch();
        AiConfigVo.WebSearchVo webVo = new AiConfigVo.WebSearchVo();
        webVo.setEnabled(webSearch.isEnabled());
        webVo.setProvider(webSearch.getProvider());
        webVo.setApiKeySet(StringUtils.isNotBlank(webSearch.getApiKey()));
        webVo.setTopN(webSearch.getTopN());
        webVo.setTimeoutMs(webSearch.getTimeoutMs());
        vo.setWebSearch(webVo);

        AiConfigVo.FlowVo flowVo = new AiConfigVo.FlowVo();
        flowVo.setParallel(aiProperties.getFlow().isParallel());
        vo.setFlow(flowVo);

        AiProperties.KnowledgeGraphConfig kg = aiProperties.getKnowledgeGraph();
        AiConfigVo.KnowledgeGraphVo kgVo = new AiConfigVo.KnowledgeGraphVo();
        kgVo.setEnabled(kg.isEnabled());
        kgVo.setStorage(kg.getStorage());
        kgVo.setBatchSize(kg.getBatchSize());
        kgVo.setMaxEntitiesPerChunk(kg.getMaxEntitiesPerChunk());
        kgVo.setMaxRelationsPerChunk(kg.getMaxRelationsPerChunk());
        kgVo.setConfidenceThreshold(kg.getConfidenceThreshold());
        kgVo.setExtractionModelId(kg.getExtractionModelId());
        kgVo.setGraphWeight(kg.getGraphWeight());
        kgVo.setMaxNeighbors(kg.getMaxNeighbors());
        kgVo.setLabelPropagationMaxIter(kg.getLabelPropagationMaxIter());
        kgVo.setMaxCommunities(kg.getMaxCommunities());
        kgVo.setMaxCommunitiesForGlobal(kg.getMaxCommunitiesForGlobal());
        vo.setKnowledgeGraph(kgVo);

        AiProperties.DecisionConfig decision = aiProperties.getDecision();
        AiConfigVo.DecisionVo decisionVo = new AiConfigVo.DecisionVo();
        decisionVo.setEnabled(decision.isEnabled());
        decisionVo.setMaxConcurrentRuns(decision.getMaxConcurrentRuns());
        decisionVo.setMaxScenarios(decision.getMaxScenarios());
        decisionVo.setAnalysisTimeoutSeconds(decision.getAnalysisTimeoutSeconds());
        decisionVo.setActionTimeoutSeconds(decision.getActionTimeoutSeconds());
        decisionVo.setStaleThresholdSeconds(decision.getStaleThresholdSeconds());
        decisionVo.setRequireApprovalLevels(new java.util.ArrayList<>(decision.getRequireApprovalLevels()));
        decisionVo.setAllowAutoLowRiskNotify(decision.isAllowAutoLowRiskNotify());
        decisionVo.setBenchmarkMode(decision.isBenchmarkMode());
        decisionVo.setDefaultHorizonDays(decision.getDefaultHorizonDays());
        decisionVo.setMaxHorizonDays(decision.getMaxHorizonDays());
        decisionVo.setPolicyKnowledgeId(decision.getPolicyKnowledgeId());
        AiConfigVo.DecisionRiskVo riskVo = new AiConfigVo.DecisionRiskVo();
        riskVo.setTardinessPoints(decision.getRisk().getTardinessPoints());
        riskVo.setShortagePoints(decision.getRisk().getShortagePoints());
        riskVo.setCapacityPoints(decision.getRisk().getCapacityPoints());
        riskVo.setSupplierPoints(decision.getRisk().getSupplierPoints());
        decisionVo.setRisk(riskVo);
        AiConfigVo.DecisionRankingVo rankingVo = new AiConfigVo.DecisionRankingVo();
        rankingVo.setTardinessWeight(decision.getRanking().getTardinessWeight());
        rankingVo.setDelayedCountWeight(decision.getRanking().getDelayedCountWeight());
        rankingVo.setShortageWeight(decision.getRanking().getShortageWeight());
        rankingVo.setUtilizationWeight(decision.getRanking().getUtilizationWeight());
        rankingVo.setActionCostWeight(decision.getRanking().getActionCostWeight());
        rankingVo.setPlanChangeWeight(decision.getRanking().getPlanChangeWeight());
        rankingVo.setInfeasibleWeight(decision.getRanking().getInfeasibleWeight());
        decisionVo.setRanking(rankingVo);
        vo.setDecision(decisionVo);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(AiConfigBo bo) {
        String preservedApiKey = resolvePreservedApiKey(bo);
        ConfigCandidate candidate = buildCandidate(bo, preservedApiKey);
        validateDecisionWeights(candidate.decision());
        synchronized (this) {
            // 运行态保持旧值，直到数据库事务真正提交。
            persistCandidate(candidate);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                synchronized (AiConfigServiceImpl.this) {
                    Snapshot before = Snapshot.of(aiProperties);
                    applyCandidate(candidate);
                    handleCacheInvalidation(before, Snapshot.of(aiProperties));
                }
                log.info("[AiConfig] AI 参数设置已提交并生效");
                RedisUtils.publish(CONFIG_REFRESH_CHANNEL, String.valueOf(System.currentTimeMillis()));
            }
        });
    }

    /**
     * 解析联网搜索 apiKey 的落库值：前端留空（null/空白）表示保留原值，
     * 优先取 ai_config 表已存值，其次当前 bean 值（首次保存前可能是 yml 种子）
     */
    private String resolvePreservedApiKey(AiConfigBo bo) {
        if (StringUtils.isNotBlank(bo.getWebSearch().getApiKey())) {
            return bo.getWebSearch().getApiKey().trim();
        }
        AiConfig stored = aiConfigMapper.selectById(GROUP_WEB_SEARCH);
        if (stored != null && StringUtils.isNotBlank(stored.getConfigValue())) {
            try {
                AiProperties.WebSearchConfig saved = JsonUtils.parseObject(stored.getConfigValue(), AiProperties.WebSearchConfig.class);
                if (saved != null && StringUtils.isNotBlank(saved.getApiKey())) {
                    return saved.getApiKey();
                }
            } catch (Exception e) {
                log.warn("[AiConfig] 解析已存 web_search 配置失败，apiKey 回退当前值", e);
            }
        }
        return aiProperties.getWebSearch().getApiKey();
    }

    /** 根据完整表单构建与当前运行态隔离的候选配置。 */
    private ConfigCandidate buildCandidate(AiConfigBo bo, String apiKey) {
        SqlSafetyData sqlSafety = new SqlSafetyData();
        sqlSafety.setAllowedTablePrefixes(List.copyOf(bo.getSqlSafety().getAllowedTablePrefixes()));
        sqlSafety.setSqlTimeout(bo.getSqlSafety().getSqlTimeout());
        sqlSafety.setSqlMaxRows(bo.getSqlSafety().getSqlMaxRows());

        AiProperties.VectorStoreConfig vectorStore = new AiProperties.VectorStoreConfig();
        vectorStore.setType(bo.getVectorStore().getType());
        AiProperties.MilvusConfig milvus = new AiProperties.MilvusConfig();
        milvus.setUrl(bo.getVectorStore().getMilvusUrl().trim());
        milvus.setCollectionName(bo.getVectorStore().getMilvusCollectionName().trim());
        milvus.setDimension(bo.getVectorStore().getMilvusDimension());
        vectorStore.setMilvus(milvus);

        AiProperties.WebSearchConfig webSearch = new AiProperties.WebSearchConfig();
        webSearch.setEnabled(bo.getWebSearch().getEnabled());
        webSearch.setProvider(bo.getWebSearch().getProvider());
        webSearch.setApiKey(apiKey);
        webSearch.setTopN(bo.getWebSearch().getTopN());
        webSearch.setTimeoutMs(bo.getWebSearch().getTimeoutMs());

        AiProperties.FlowConfig flow = new AiProperties.FlowConfig();
        flow.setParallel(bo.getFlow().getParallel());

        AiProperties.KnowledgeGraphConfig kg = new AiProperties.KnowledgeGraphConfig();
        kg.setEnabled(bo.getKnowledgeGraph().getEnabled());
        kg.setStorage(bo.getKnowledgeGraph().getStorage());
        kg.setBatchSize(bo.getKnowledgeGraph().getBatchSize());
        kg.setMaxEntitiesPerChunk(bo.getKnowledgeGraph().getMaxEntitiesPerChunk());
        kg.setMaxRelationsPerChunk(bo.getKnowledgeGraph().getMaxRelationsPerChunk());
        kg.setConfidenceThreshold(bo.getKnowledgeGraph().getConfidenceThreshold());
        kg.setExtractionModelId(bo.getKnowledgeGraph().getExtractionModelId());
        kg.setGraphWeight(bo.getKnowledgeGraph().getGraphWeight());
        kg.setMaxNeighbors(bo.getKnowledgeGraph().getMaxNeighbors());
        kg.setLabelPropagationMaxIter(bo.getKnowledgeGraph().getLabelPropagationMaxIter());
        kg.setMaxCommunities(bo.getKnowledgeGraph().getMaxCommunities());
        kg.setMaxCommunitiesForGlobal(bo.getKnowledgeGraph().getMaxCommunitiesForGlobal());

        return new ConfigCandidate(sqlSafety, vectorStore, webSearch, flow, kg,
            toDecisionConfig(bo.getDecision()));
    }

    /** 在事务中只持久化候选值，不提前污染本节点运行态。 */
    private void persistCandidate(ConfigCandidate candidate) {
        upsertGroup(GROUP_SQL_SAFETY, JsonUtils.toJsonString(candidate.sqlSafety()));
        upsertGroup(GROUP_VECTOR_STORE, JsonUtils.toJsonString(candidate.vectorStore()));
        upsertGroup(GROUP_WEB_SEARCH, JsonUtils.toJsonString(candidate.webSearch()));
        upsertGroup(GROUP_FLOW, JsonUtils.toJsonString(candidate.flow()));
        upsertGroup(GROUP_KNOWLEDGE_GRAPH, JsonUtils.toJsonString(candidate.knowledgeGraph()));
        upsertGroup(GROUP_DECISION, JsonUtils.toJsonString(candidate.decision()));
    }

    /** 仅由事务 afterCommit 或可信数据库重载路径发布。 */
    private void applyCandidate(ConfigCandidate candidate) {
        SqlSafetyData sqlSafety = candidate.sqlSafety();
        aiProperties.setAllowedTablePrefixes(List.copyOf(sqlSafety.getAllowedTablePrefixes()));
        aiProperties.setSqlTimeout(sqlSafety.getSqlTimeout());
        aiProperties.setSqlMaxRows(sqlSafety.getSqlMaxRows());
        aiProperties.setVectorStore(candidate.vectorStore());
        aiProperties.setWebSearch(candidate.webSearch());
        aiProperties.setFlow(candidate.flow());
        aiProperties.setKnowledgeGraph(candidate.knowledgeGraph());
        aiProperties.setDecision(candidate.decision());
    }

    /**
     * 从 ai_config 表全量加载并回填（单组解析失败仅告警跳过，保留现值兜底）
     */
    private void loadFromDb(String trigger) {
        List<AiConfig> configs = aiConfigMapper.selectList();
        if (configs.isEmpty()) {
            log.info("[AiConfig] {}：ai_config 无记录，沿用 yml 默认配置", trigger);
            return;
        }
        synchronized (this) {
            Snapshot before = Snapshot.of(aiProperties);
            for (AiConfig config : configs) {
                try {
                    applyGroup(config.getConfigGroup(), config.getConfigValue());
                } catch (Exception e) {
                    log.warn("[AiConfig] {}：分组 {} 配置解析失败，保留现值: {}", trigger, config.getConfigGroup(), e.getMessage());
                }
            }
            handleCacheInvalidation(before, Snapshot.of(aiProperties));
        }
        log.info("[AiConfig] {}：已从 ai_config 加载 {} 组配置", trigger, configs.size());
    }

    /**
     * 按分组反序列化并回填 AiProperties
     */
    private void applyGroup(String group, String json) {
        switch (group) {
            case GROUP_SQL_SAFETY -> {
                SqlSafetyData data = JsonUtils.parseObject(json, SqlSafetyData.class);
                if (data != null) {
                    aiProperties.setAllowedTablePrefixes(data.getAllowedTablePrefixes());
                    aiProperties.setSqlTimeout(data.getSqlTimeout());
                    aiProperties.setSqlMaxRows(data.getSqlMaxRows());
                }
            }
            case GROUP_VECTOR_STORE -> {
                AiProperties.VectorStoreConfig parsed = JsonUtils.parseObject(json, AiProperties.VectorStoreConfig.class);
                if (parsed != null) {
                    aiProperties.setVectorStore(parsed);
                }
            }
            case GROUP_WEB_SEARCH -> {
                AiProperties.WebSearchConfig parsed = JsonUtils.parseObject(json, AiProperties.WebSearchConfig.class);
                if (parsed != null) {
                    aiProperties.setWebSearch(parsed);
                }
            }
            case GROUP_FLOW -> {
                AiProperties.FlowConfig parsed = JsonUtils.parseObject(json, AiProperties.FlowConfig.class);
                if (parsed != null) {
                    aiProperties.setFlow(parsed);
                }
            }
            case GROUP_KNOWLEDGE_GRAPH -> {
                AiProperties.KnowledgeGraphConfig parsed = JsonUtils.parseObject(json, AiProperties.KnowledgeGraphConfig.class);
                if (parsed != null) {
                    aiProperties.setKnowledgeGraph(parsed);
                }
            }
            case GROUP_DECISION -> {
                AiProperties.DecisionConfig parsed = JsonUtils.parseObject(json, AiProperties.DecisionConfig.class);
                if (parsed != null) {
                    validateDecisionWeights(parsed);
                    aiProperties.setDecision(parsed);
                }
            }
            default -> log.warn("[AiConfig] 未知的配置分组: {}", group);
        }
    }

    /** 请求字段映射为运行态配置；未提供决策组的旧客户端需要先更新表单。 */
    private AiProperties.DecisionConfig toDecisionConfig(AiConfigBo.DecisionBo bo) {
        if (bo == null || bo.getRisk() == null || bo.getRanking() == null) {
            throw new com.nexus.common.core.exception.ServiceException("工业决策配置、风险分值及方案权重不能为空");
        }
        AiProperties.DecisionConfig config = JsonUtils.parseObject(
            JsonUtils.toJsonString(bo), AiProperties.DecisionConfig.class);
        if (config == null) {
            throw new com.nexus.common.core.exception.ServiceException("工业决策配置无法解析");
        }
        return config;
    }

    /** 持久化或重载前检查两个评分维度；拒绝无穷和无效比例。 */
    private void validateDecisionWeights(AiProperties.DecisionConfig config) {
        List<String> required = config.getRequireApprovalLevels();
        if (required == null || !required.contains("HIGH") || !required.contains("CRITICAL")
            || required.stream().anyMatch(level -> level == null
                || !java.util.Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(level))) {
            throw new com.nexus.common.core.exception.ServiceException("高风险及严重风险必须保留人工审批");
        }
        var risk = config.getRisk();
        if (risk == null
            || risk.getTardinessPoints() < 0 || risk.getTardinessPoints() > 100
            || risk.getShortagePoints() < 0 || risk.getShortagePoints() > 100
            || risk.getCapacityPoints() < 0 || risk.getCapacityPoints() > 100
            || risk.getSupplierPoints() < 0 || risk.getSupplierPoints() > 100
            || ((long) risk.getTardinessPoints() + risk.getShortagePoints()
                + risk.getCapacityPoints() + risk.getSupplierPoints()) != 100L) {
            throw new com.nexus.common.core.exception.ServiceException("决策风险分值必须在0到100且合计为100");
        }
        var ranking = config.getRanking();
        if (ranking == null || !Double.isFinite(ranking.weightSum())
            || Math.abs(ranking.weightSum() - 1.0) > 0.000001
            || ranking.getTardinessWeight() < 0 || ranking.getDelayedCountWeight() < 0
            || ranking.getShortageWeight() < 0 || ranking.getUtilizationWeight() < 0
            || ranking.getActionCostWeight() < 0 || ranking.getPlanChangeWeight() < 0
            || ranking.getInfeasibleWeight() < 0) {
            throw new com.nexus.common.core.exception.ServiceException("决策方案排名权重必须非负且合计为1");
        }
        if (config.getMaxConcurrentRuns() < 1 || config.getMaxConcurrentRuns() > 16
            || config.getMaxScenarios() < 2 || config.getMaxScenarios() > 8
            || config.getAnalysisTimeoutSeconds() < 10 || config.getAnalysisTimeoutSeconds() > 300
            || config.getActionTimeoutSeconds() < 5 || config.getActionTimeoutSeconds() > 120
            || config.getStaleThresholdSeconds() < 30 || config.getStaleThresholdSeconds() > 3600
            || config.getDefaultHorizonDays() < 1 || config.getMaxHorizonDays() > 90
            || config.getDefaultHorizonDays() > config.getMaxHorizonDays()
            || (config.getPolicyKnowledgeId() != null && config.getPolicyKnowledgeId() <= 0)) {
            throw new com.nexus.common.core.exception.ServiceException("工业决策并发、方案数或推演跨度不合法");
        }
    }

    /**
     * 配置变更的缓存联动：向量库连接参数变更 → 逐出 Milvus 客户端缓存；
     * 表前缀变更 → 全量刷新 ERP 表结构缓存（异步，避免阻塞保存/广播回调线程）
     */
    private void handleCacheInvalidation(Snapshot before, Snapshot after) {
        if (!Objects.equals(before.vectorConn, after.vectorConn)) {
            log.info("[AiConfig] 向量库连接配置变更: {} -> {}", before.vectorConn, after.vectorConn);
            vectorStoreService.invalidateMilvusStores();
        }
        if (!Objects.equals(before.allowedTablePrefixes, after.allowedTablePrefixes)) {
            log.info("[AiConfig] 允许表前缀变更: {} -> {}", before.allowedTablePrefixes, after.allowedTablePrefixes);
            Thread refresher = new Thread(erpTableSchemaManager::refreshAll, "ai-config-schema-refresh");
            refresher.setDaemon(true);
            refresher.start();
        }
    }

    /**
     * 分组 upsert：存在即 update，否则 insert
     */
    private void upsertGroup(String group, String json) {
        AiConfig config = aiConfigMapper.selectById(group);
        if (config != null) {
            config.setConfigValue(json);
            aiConfigMapper.updateById(config);
        } else {
            config = new AiConfig();
            config.setConfigGroup(group);
            config.setConfigValue(json);
            aiConfigMapper.insert(config);
        }
    }

    private record ConfigCandidate(
        SqlSafetyData sqlSafety,
        AiProperties.VectorStoreConfig vectorStore,
        AiProperties.WebSearchConfig webSearch,
        AiProperties.FlowConfig flow,
        AiProperties.KnowledgeGraphConfig knowledgeGraph,
        AiProperties.DecisionConfig decision) {
    }

    /**
     * SQL 安全组持久化结构（AiProperties 顶层三字段的分组视图）
     */
    @Data
    public static class SqlSafetyData {
        private List<String> allowedTablePrefixes;
        private int sqlTimeout;
        private int sqlMaxRows;
    }

    /**
     * 缓存联动对比快照：向量库连接四元组 + 表前缀列表
     */
    private record Snapshot(VectorConn vectorConn, List<String> allowedTablePrefixes) {

        static Snapshot of(AiProperties props) {
            VectorConn conn = new VectorConn(
                props.getVectorStore().getType(),
                props.getVectorStore().getMilvus().getUrl(),
                props.getVectorStore().getMilvus().getCollectionName(),
                props.getVectorStore().getMilvus().getDimension());
            return new Snapshot(conn, List.copyOf(props.getAllowedTablePrefixes()));
        }

        record VectorConn(String type, String url, String collectionName, int dimension) {
        }
    }
}

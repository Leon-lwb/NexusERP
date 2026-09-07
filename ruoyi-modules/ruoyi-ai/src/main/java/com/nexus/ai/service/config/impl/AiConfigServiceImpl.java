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
 * <p>即时生效：所有消费方均调用时读取 AiProperties getter，回填 bean 字段即生效；
 * 需联动的缓存 —— 向量库连接参数变更逐出 Milvus 客户端缓存（缓存 key 不含连接参数），
 * 表前缀变更刷新 ERP 表结构缓存（按前缀全量加载）</p>
 * <p>多节点：修改后通过 Redis Pub/Sub 广播，各节点收到后从库重载回填（ai_config 已加入
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

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(AiConfigBo bo) {
        String preservedApiKey = resolvePreservedApiKey(bo);
        synchronized (this) {
            Snapshot before = Snapshot.of(aiProperties);
            applyBo(bo, preservedApiKey);
            upsertGroup(GROUP_SQL_SAFETY, JsonUtils.toJsonString(toSqlSafetyData()));
            upsertGroup(GROUP_VECTOR_STORE, JsonUtils.toJsonString(aiProperties.getVectorStore()));
            upsertGroup(GROUP_WEB_SEARCH, JsonUtils.toJsonString(aiProperties.getWebSearch()));
            upsertGroup(GROUP_FLOW, JsonUtils.toJsonString(aiProperties.getFlow()));
            upsertGroup(GROUP_KNOWLEDGE_GRAPH, JsonUtils.toJsonString(aiProperties.getKnowledgeGraph()));
            handleCacheInvalidation(before, Snapshot.of(aiProperties));
        }
        log.info("[AiConfig] AI 参数设置已保存并生效");
        // 广播必须在事务提交后：其他节点收到即重载落库值，提交前广播会读到旧数据
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
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

    /**
     * 将业务对象回填到 AiProperties（逐字段拷贝，嵌套组直接替换为新构建实例）
     */
    private void applyBo(AiConfigBo bo, String apiKey) {
        aiProperties.setAllowedTablePrefixes(bo.getSqlSafety().getAllowedTablePrefixes());
        aiProperties.setSqlTimeout(bo.getSqlSafety().getSqlTimeout());
        aiProperties.setSqlMaxRows(bo.getSqlSafety().getSqlMaxRows());

        AiProperties.VectorStoreConfig vectorStore = new AiProperties.VectorStoreConfig();
        vectorStore.setType(bo.getVectorStore().getType());
        AiProperties.MilvusConfig milvus = new AiProperties.MilvusConfig();
        milvus.setUrl(bo.getVectorStore().getMilvusUrl().trim());
        milvus.setCollectionName(bo.getVectorStore().getMilvusCollectionName().trim());
        milvus.setDimension(bo.getVectorStore().getMilvusDimension());
        vectorStore.setMilvus(milvus);
        aiProperties.setVectorStore(vectorStore);

        AiProperties.WebSearchConfig webSearch = new AiProperties.WebSearchConfig();
        webSearch.setEnabled(bo.getWebSearch().getEnabled());
        webSearch.setProvider(bo.getWebSearch().getProvider());
        webSearch.setApiKey(apiKey);
        webSearch.setTopN(bo.getWebSearch().getTopN());
        webSearch.setTimeoutMs(bo.getWebSearch().getTimeoutMs());
        aiProperties.setWebSearch(webSearch);

        AiProperties.FlowConfig flow = new AiProperties.FlowConfig();
        flow.setParallel(bo.getFlow().getParallel());
        aiProperties.setFlow(flow);

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
        aiProperties.setKnowledgeGraph(kg);
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
            default -> log.warn("[AiConfig] 未知的配置分组: {}", group);
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

    private SqlSafetyData toSqlSafetyData() {
        SqlSafetyData data = new SqlSafetyData();
        data.setAllowedTablePrefixes(aiProperties.getAllowedTablePrefixes());
        data.setSqlTimeout(aiProperties.getSqlTimeout());
        data.setSqlMaxRows(aiProperties.getSqlMaxRows());
        return data;
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

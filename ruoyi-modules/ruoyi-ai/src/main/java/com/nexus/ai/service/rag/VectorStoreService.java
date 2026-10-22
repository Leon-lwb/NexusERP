package com.nexus.ai.service.rag;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.domain.entity.AiKnowledge;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.enums.AiModelTypeEnum;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeMapper;
import com.nexus.common.ai.mapper.AiModelMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.HasCollectionParam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 向量存储服务
 * <p>支持 Milvus 和 InMemory 双模式，按知识库ID隔离集合</p>
 * <p>Milvus 模式：每个知识库一个 Collection（命名规则：{collectionName}_{knowledgeId}）</p>
 * <p>InMemory 模式：每个知识库一个独立的 InMemoryEmbeddingStore 实例</p>
 *
 * @author NexusERP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    /** 向量化服务，按模型名或默认构建并缓存 EmbeddingModel */
    private final EmbeddingService embeddingService;
    /** 知识库片段 Mapper，用于回写片段的向量ID */
    private final AiKnowledgeChunkMapper chunkMapper;
    /** 知识库 Mapper，用于读取知识库存储类型与配置 */
    private final AiKnowledgeMapper knowledgeMapper;
    /** 模型 Mapper，用于按知识库所选 Embedding 模型解析向量维度 */
    private final AiModelMapper modelMapper;
    /** AI 配置属性，提供向量库类型、Milvus 连接与默认模型等配置 */
    private final AiProperties aiProperties;

    /**
     * InMemory 模式：按知识库ID隔离的存储实例。
     * <p>注意：此 Map 持有 InMemory 模式的<strong>权威数据</strong>（chunk 向量仅在导入时写入，
     * 缓存未命中不会从 DB 重建），因此<strong>不能加 TTL/大小逐出</strong>——逐出等于数据丢失。
     * InMemory 模式本身常驻堆内存是其已知代价；如需控制内存占用请切换到 milvus 模式。</p>
     */
    private final ConcurrentHashMap<Long, InMemoryEmbeddingStore<TextSegment>> inMemoryStores = new ConcurrentHashMap<>();

    /** Milvus 模式：按知识库ID缓存 EmbeddingStore 客户端实例。MilvusEmbeddingStore 为无状态客户端
     * （数据在 Milvus 服务端），逐出后重建仅重连，故可安全加有界 + TTL 限制客户端实例数。
     * <p>注意：本缓存基于<b>单实例部署</b>假设（实例内状态即全局状态）；多节点部署时
     * 重建/删除触发的 invalidate 仅对本节点生效，需引入缓存失效广播或集中式状态，否则
     * 其他节点会继续持有旧维度客户端。</p> */
    private final Cache<Long, EmbeddingStore<TextSegment>> milvusStores = Caffeine.newBuilder()
        .maximumSize(64)
        .expireAfterAccess(1, TimeUnit.HOURS)
        .build();

    /** 批量向量化批大小（Embedding API 通常支持批量请求，减少网络往返） */
    private static final int EMBEDDING_BATCH_SIZE = 32;

    /**
     * 逐出全部 Milvus EmbeddingStore 客户端缓存
     * <p>「AI 参数设置」页变更向量库连接（url/collectionName/dimension/type）后调用：
     * milvusStores 缓存 key 仅含 knowledgeId，不感知连接参数变化，必须显式逐出，
     * 下次访问按新配置重建客户端。InMemory 权威数据不受影响。</p>
     */
    public void invalidateMilvusStores() {
        milvusStores.invalidateAll();
        log.info("[VectorStore] 向量库连接配置变更，已逐出全部 Milvus 客户端缓存");
    }

    /**
     * 获取全局默认向量存储类型（未指定知识库时使用）
     */
    private String getDefaultStoreType() {
        return aiProperties.getVectorStore().getType();
    }

    /**
     * 获取指定知识库的向量存储类型
     * <p>优先从知识库实体读取，若知识库不存在则回退到全局配置</p>
     *
     * @param knowledgeId 知识库ID
     * @return 向量存储类型（milvus / inmemory）
     */
    private String getStoreType(Long knowledgeId) {
        AiKnowledge knowledge = knowledgeMapper.selectById(knowledgeId);
        if (knowledge != null && knowledge.getVectorStore() != null) {
            return knowledge.getVectorStore();
        }
        return getDefaultStoreType();
    }

    /**
     * 获取 Milvus Collection 名称（按知识库ID隔离）
     */
    private String getCollectionName(Long knowledgeId) {
        return aiProperties.getVectorStore().getMilvus().getCollectionName() + "_" + knowledgeId;
    }

    /**
     * 获取指定知识库的 EmbeddingStore 实例
     *
     * @param knowledgeId 知识库ID
     * @return EmbeddingStore 实例
     */
    private EmbeddingStore<TextSegment> getStore(Long knowledgeId) {
        return getStore(knowledgeId, null);
    }

    /**
     * 获取指定知识库的 EmbeddingStore 实例（Milvus 维度可显式指定）
     * <p>维度解析优先级：调用方显式指定（重建路径）→ 知识库所选 Embedding 模型的
     * embedding_dimension → 全局配置兜底（legacy 模型未配置维度时，唯一来源
     * {@code AiProperties.MilvusConfig#dimension}，可在「AI 参数设置」页修改）。</p>
     *
     * @param knowledgeId       知识库ID
     * @param dimensionOverride 显式维度（null 表示按知识库配置解析）
     * @return EmbeddingStore 实例
     */
    private EmbeddingStore<TextSegment> getStore(Long knowledgeId, Integer dimensionOverride) {
        String storeType = getStoreType(knowledgeId);
        if ("milvus".equalsIgnoreCase(storeType)) {
            return milvusStores.get(
                    knowledgeId,
                    kid -> {
                        String collectionName = getCollectionName(kid);
                        String url = aiProperties.getVectorStore().getMilvus().getUrl();
                        int dimension =
                                (dimensionOverride != null && dimensionOverride > 0)
                                        ? dimensionOverride
                                        : resolveEmbeddingDimension(
                                                loadKnowledgeEmbeddingModel(kid));
                        log.info(
                                "[VectorStore] 创建 Milvus EmbeddingStore: collection={}, dimension={}",
                                collectionName,
                                dimension);
                        return MilvusEmbeddingStore.builder()
                                .uri(url)
                                .collectionName(collectionName)
                                .dimension(dimension)
                                .retrieveEmbeddingsOnSearch(true)
                                .build();
                    });
        } else {
            return inMemoryStores.computeIfAbsent(knowledgeId, k -> new InMemoryEmbeddingStore<>());
        }
    }

    /**
     * 读取知识库当前选择的 Embedding 模型名称
     */
    private String loadKnowledgeEmbeddingModel(Long knowledgeId) {
        AiKnowledge knowledge = knowledgeMapper.selectById(knowledgeId);
        return knowledge != null ? knowledge.getEmbeddingModel() : null;
    }

    /**
     * 解析 Embedding 模型的向量维度
     * <p>仅认 EMBEDDING 类型且状态启用（0，字典 sys_normal_disable，与
     * {@code AiKnowledgeServiceImpl#validateEmbeddingModel} 同口径）的模型配置的 embedding_dimension；
     * 模型缺失/停用/类型不符/legacy 未配置维度（null/非正数）一律回退全局配置
     * {@code nexus.ai.vector-store.milvus.dimension}（唯一来源 AiProperties，
     * 可在「AI 参数设置」页修改；KnowledgeIndexRebuildService 亦委托本方法，不再各持一份解析）。</p>
     *
     * @param embeddingModelName 模型名称（null/空按 legacy 兜底处理）
     * @return 向量维度
     */
    public int resolveEmbeddingDimension(String embeddingModelName) {
        if (StringUtils.isNotBlank(embeddingModelName)) {
            AiModel model = modelMapper.selectOne(Wrappers.<AiModel>lambdaQuery()
                .eq(AiModel::getModelName, embeddingModelName)
                .eq(AiModel::getModelType, AiModelTypeEnum.EMBEDDING.getCode())
                // 0=启用（字典 sys_normal_disable；契约常量在 common-ai 包内不可见，字面量同步）
                .eq(AiModel::getStatus, "0")
                .last("LIMIT 1"));
            // Java 侧同口径兜底判断（与上方 SQL 条件一致）：停用/类型不符的模型维度不可信
            if (model != null && model.getEmbeddingDimension() != null && model.getEmbeddingDimension() > 0
                && AiModelTypeEnum.EMBEDDING.getCode().equals(model.getModelType())
                && "0".equals(model.getStatus())) {
                return model.getEmbeddingDimension();
            }
        }
        return aiProperties.getVectorStore().getMilvus().getDimension();
    }

    /**
     * 解析 Embedding 模型实例（null/空名称回退默认模型）
     */
    private EmbeddingModel resolveEmbeddingModel(String embeddingModelName) {
        return (embeddingModelName != null && !embeddingModelName.isEmpty())
            ? embeddingService.getEmbeddingModel(embeddingModelName)
            : embeddingService.getDefaultEmbeddingModel();
    }

    /**
     * 将文本片段向量化并存储（使用知识库指定的 Embedding 模型）
     *
     * @param knowledgeId       知识库ID
     * @param chunks            文本片段列表
     * @param embeddingModelName 使用的 Embedding 模型名称（为空时使用默认模型）
     * @return 存储的向量ID列表
     */
    public List<String> storeChunks(Long knowledgeId, List<AiKnowledgeChunk> chunks, String embeddingModelName) {
        EmbeddingModel embeddingModel = resolveEmbeddingModel(embeddingModelName);
        EmbeddingStore<TextSegment> store = getStore(knowledgeId);
        List<String> vectorIds = new ArrayList<>();

        // 分批处理：每批 EMBEDDING_BATCH_SIZE 个片段，批量向量化后批量存入（导入路径：宽容模式，跳过失败片段）
        for (int batchStart = 0; batchStart < chunks.size(); batchStart += EMBEDDING_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + EMBEDDING_BATCH_SIZE, chunks.size());
            List<AiKnowledgeChunk> batch = chunks.subList(batchStart, batchEnd);
            vectorIds.addAll(embedAndStoreBatch(store, embeddingModel, knowledgeId, batch, false));
        }

        log.info("知识库[{}]向量化存储完成: 成功 {}/{} 片段, 存储类型={}", knowledgeId, vectorIds.size(), chunks.size(), getStoreType(knowledgeId));
        return vectorIds;
    }

    /**
     * 单批向量化入库：批量 embedAll/addAll 优先，失败降级逐条，并回写各 chunk 的 vectorId
     *
     * @param store          目标向量存储
     * @param embeddingModel Embedding 模型
     * @param knowledgeId    知识库ID
     * @param batch          本批片段（非空）
     * @param strict         严格模式（重建路径）：降级逐条仍失败/结果缺失时向上抛出，
     *                       由调用方转 FAILED 状态；宽容模式（导入路径）仅记录日志跳过失败片段
     * @return 本批成功入库的向量ID列表
     */
    private List<String> embedAndStoreBatch(EmbeddingStore<TextSegment> store, EmbeddingModel embeddingModel,
                                            Long knowledgeId, List<AiKnowledgeChunk> batch, boolean strict) {
        List<String> vectorIds = new ArrayList<>();
        try {
            // 构建 TextSegment 列表
            List<TextSegment> segments = new ArrayList<>();
            for (AiKnowledgeChunk chunk : batch) {
                Metadata metadata = new Metadata();
                metadata.put("knowledgeId", knowledgeId.toString());
                metadata.put("documentId", chunk.getDocumentId().toString());
                metadata.put("chunkId", chunk.getId().toString());
                segments.add(TextSegment.from(chunk.getContent(), metadata));
            }

            // 批量向量化（一次 API 调用替代 N 次）
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

            // 批量存入向量库
            List<String> batchIds = store.addAll(embeddings, segments);
            vectorIds.addAll(batchIds);

            // 更新片段的向量ID
            for (int i = 0; i < batch.size(); i++) {
                AiKnowledgeChunk chunk = batch.get(i);
                chunk.setVectorId(batchIds.get(i));
                chunkMapper.updateById(chunk);
            }
        } catch (Exception e) {
            log.error("批量向量化失败，回退到逐条处理: knowledgeId={}, strict={}, error={}",
                knowledgeId, strict, e.getMessage());
            // 降级：逐条处理（宽容模式跳过失败片段，严格模式任何片段失败立即上抛）
            for (AiKnowledgeChunk chunk : batch) {
                try {
                    Metadata metadata = new Metadata();
                    metadata.put("knowledgeId", knowledgeId.toString());
                    metadata.put("documentId", chunk.getDocumentId().toString());
                    metadata.put("chunkId", chunk.getId().toString());
                    TextSegment segment = TextSegment.from(chunk.getContent(), metadata);
                    Embedding embedding = embeddingModel.embed(segment).content();
                    String vectorId = store.add(embedding, segment);
                    vectorIds.add(vectorId);
                    chunk.setVectorId(vectorId);
                    chunkMapper.updateById(chunk);
                } catch (Exception ex) {
                    if (strict) {
                        throw new IllegalStateException("重嵌入片段失败: chunkId=" + chunk.getId()
                            + ", error=" + ex.getMessage(), ex);
                    }
                    log.error("向量化片段失败: chunkId={}, error={}", chunk.getId(), ex.getMessage());
                }
            }
        }
        if (strict && vectorIds.size() < batch.size()) {
            throw new IllegalStateException("重嵌入批次结果不完整: " + vectorIds.size() + "/" + batch.size());
        }
        return vectorIds;
    }

    /**
     * 重建知识库向量索引（按当前片段文本全量重嵌入，不重新下载/解析源文档）
     * <p>Milvus 模式：失效客户端缓存并 DROP 旧 Collection 后，经 {@link #getStore(Long, Integer)}
     * 按目标维度重建（builder 构造时自动建表/建索引/加载，即项目既有 schema 构建路径）——
     * 维度变更后旧 Collection 的向量维度已不匹配，{@code EmbeddingStore.removeAll()} 只清数据
     * 不改 schema，无法完成维度迁移，必须 DROP + RECREATE。
     * InMemory 模式：移除实例，下次使用时自动重建。</p>
     * <p>每批入库后回写 vector_index_progress = min(99, processed*100/total)
     * （进度列由此处独占回写；状态/错误/完成时间由 {@link KnowledgeIndexRebuildService} 维护）。</p>
     * <p><b>严格模式</b>：任一批量/逐条重嵌入失败或最终入库数不足（重嵌入不完整: x/y）即上抛，
     * 由 {@link KnowledgeIndexRebuildService#rebuild} 转 FAILED——绝不带着空/残索引返回 READY。</p>
     *
     * @param knowledgeId        知识库ID
     * @param embeddingModelName 目标 Embedding 模型名称
     * @param embeddingDimension 目标向量维度（null/非正数时按全局配置兜底）
     * @param chunks             待重嵌入的当前片段列表（可为 null/空）
     */
    public void rebuildKnowledge(Long knowledgeId, String embeddingModelName, Integer embeddingDimension,
                                 List<AiKnowledgeChunk> chunks) {
        List<AiKnowledgeChunk> safeChunks = chunks == null ? List.of() : chunks;
        int total = safeChunks.size();
        int dimension = (embeddingDimension != null && embeddingDimension > 0)
            ? embeddingDimension
            : aiProperties.getVectorStore().getMilvus().getDimension();
        EmbeddingModel embeddingModel = resolveEmbeddingModel(embeddingModelName);

        EmbeddingStore<TextSegment> store;
        if ("milvus".equalsIgnoreCase(getStoreType(knowledgeId))) {
            milvusStores.invalidate(knowledgeId);
            dropMilvusCollection(knowledgeId);
            // 缓存已失效：强制按目标维度重建 Collection（构造时自动建表/建索引/加载）
            store = getStore(knowledgeId, dimension);
        } else {
            inMemoryStores.remove(knowledgeId);
            store = getStore(knowledgeId, dimension);
        }

        List<String> vectorIds = new ArrayList<>();
        int processed = 0;
        for (int batchStart = 0; batchStart < total; batchStart += EMBEDDING_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + EMBEDDING_BATCH_SIZE, total);
            List<AiKnowledgeChunk> batch = safeChunks.subList(batchStart, batchEnd);
            vectorIds.addAll(embedAndStoreBatch(store, embeddingModel, knowledgeId, batch, true));
            processed = batchEnd;
            updateIndexProgress(knowledgeId, Math.min(99, processed * 100 / total));
        }
        if (vectorIds.size() < total) {
            throw new IllegalStateException("重嵌入不完整: " + vectorIds.size() + "/" + total);
        }

        log.info("知识库[{}]向量索引重建完成: 成功 {}/{} 片段, 存储类型={}, dimension={}",
            knowledgeId, vectorIds.size(), total, getStoreType(knowledgeId), dimension);
    }

    /**
     * 回写重建进度（仅重建路径调用；条件写入 WHERE status='REBUILDING'——被取代的任务不再移动进度；
     * 0 行静默跳过；失败仅告警不中断重建）
     */
    private void updateIndexProgress(Long knowledgeId, int progress) {
        try {
            AiKnowledge update = new AiKnowledge();
            update.setVectorIndexProgress(progress);
            knowledgeMapper.update(update, Wrappers.<AiKnowledge>lambdaUpdate()
                .eq(AiKnowledge::getId, knowledgeId)
                .eq(AiKnowledge::getVectorIndexStatus, KnowledgeIndexRebuildService.STATUS_REBUILDING));
        } catch (Exception e) {
            log.warn("[VectorStore] 更新重建进度失败（不中断重建）: knowledgeId={}, error={}", knowledgeId, e.getMessage());
        }
    }

    /**
     * DROP 知识库对应的 Milvus Collection（维度变更后重建的前置步骤）
     * <p>直接使用 Milvus SDK 的 {@code MilvusServiceClient + DropCollectionParam}；
     * Collection 不存在时跳过（首次导入前重建等场景）。删除失败向上抛出，
     * 由 {@link KnowledgeIndexRebuildService#rebuild} 统一转 FAILED 状态。</p>
     *
     * @param knowledgeId 知识库ID
     */
    private void dropMilvusCollection(Long knowledgeId) {
        String collectionName = getCollectionName(knowledgeId);
        String url = aiProperties.getVectorStore().getMilvus().getUrl();
        MilvusServiceClient client =
                new MilvusServiceClient(ConnectParam.newBuilder().withUri(url).build());
        try {
            R<Boolean> has =
                    client.hasCollection(
                            HasCollectionParam.newBuilder()
                                    .withCollectionName(collectionName)
                                    .build());
            // RPC 失败（status 非 Success/data null）必须上抛：与"Collection 不存在"语义不同，
            // 误判为不存在会跳过 DROP，getStore 挂回旧维度 Collection，插入错误被吞后产出空 READY
            if (has == null
                    || !Integer.valueOf(R.Status.Success.getCode()).equals(has.getStatus())) {
                throw new IllegalStateException("Milvus hasCollection 失败: "
                    + (has == null ? "无响应" : has.getStatus() + " " + has.getMessage()));
            }
            if (!Boolean.TRUE.equals(has.getData())) {
                log.info("[VectorStore] Collection 不存在，跳过删除: {}", collectionName);
                return;
            }
            R<RpcStatus> drop =
                    client.dropCollection(
                            DropCollectionParam.newBuilder()
                                    .withCollectionName(collectionName)
                                    .build());
            if (drop == null
                    || !Integer.valueOf(R.Status.Success.getCode()).equals(drop.getStatus())) {
                throw new IllegalStateException("Milvus dropCollection 失败: "
                    + (drop == null ? "无响应" : drop.getStatus() + " " + drop.getMessage()));
            }
            log.info("[VectorStore] 已删除旧 Collection（维度变更后按新 schema 重建）: {}", collectionName);
        } finally {
            try {
                client.close(3000);
            } catch (Exception ignore) {
                // 连接关闭失败仅影响本次短连回收，不影响重建结果
            }
        }
    }

    /**
     * 语义检索相关片段（指定知识库，使用知识库指定的 Embedding 模型）
     *
     * @param knowledgeId       知识库ID
     * @param query             查询文本
     * @param maxResults        最大返回结果数
     * @param minScore          最低相似度阈值（0.0-1.0）
     * @param embeddingModelName 使用的 Embedding 模型名称（为空时使用默认模型）
     * @return 匹配的片段列表
     */
    public List<EmbeddingMatch<TextSegment>> search(Long knowledgeId, String query, int maxResults, double minScore, String embeddingModelName) {
        EmbeddingModel embeddingModel = resolveEmbeddingModel(embeddingModelName);
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingStore<TextSegment> store = getStore(knowledgeId);

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(maxResults)
            .minScore(minScore)
            .build();

        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);
        return result.matches();
    }

    /**
     * 语义检索相关片段（指定知识库，使用默认 Embedding 模型）
     * <p>兼容性重载，向后兼容未指定模型的调用方</p>
     */
    public List<EmbeddingMatch<TextSegment>> search(Long knowledgeId, String query, int maxResults, double minScore) {
        return search(knowledgeId, query, maxResults, minScore, null);
    }

    /**
     * 语义检索（全库搜索，不指定知识库，仅 InMemory 模式支持）
     *
     * @param query      查询文本
     * @param maxResults 最大返回结果数
     * @param minScore   最低相似度阈值（0.0-1.0）
     * @return 匹配的片段列表（按相似度降序）
     */
    public List<EmbeddingMatch<TextSegment>> search(String query, int maxResults, double minScore) {
        EmbeddingModel embeddingModel = embeddingService.getDefaultEmbeddingModel();
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        if (!"milvus".equalsIgnoreCase(getDefaultStoreType())) {
            List<EmbeddingMatch<TextSegment>> allMatches = new ArrayList<>();
            for (var entry : inMemoryStores.entrySet()) {
                EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();
                EmbeddingSearchResult<TextSegment> result = entry.getValue().search(searchRequest);
                allMatches.addAll(result.matches());
            }
            allMatches.sort((a, b) -> Double.compare(b.score(), a.score()));
            return allMatches.subList(0, Math.min(maxResults, allMatches.size()));
        }

        log.warn("[VectorStore] Milvus 模式下不支持跨知识库搜索，请指定 knowledgeId");
        return List.of();
    }

    /**
     * 删除指定知识库的所有向量数据
     *
     * @param knowledgeId 知识库ID
     */
    public void deleteByKnowledgeId(Long knowledgeId) {
        if ("milvus".equalsIgnoreCase(getStoreType(knowledgeId))) {
            // Milvus: 先尝试通过 removeAll 清除全部数据，再移除本地缓存
            EmbeddingStore<TextSegment> store = getStore(knowledgeId);
            try {
                store.removeAll();
                log.info("[VectorStore] Milvus 已删除知识库全部向量: knowledgeId={}", knowledgeId);
            } catch (Exception e) {
                log.warn("[VectorStore] Milvus removeAll 失败，回退到清除缓存: knowledgeId={}, error={}", knowledgeId, e.getMessage());
            }
            milvusStores.invalidate(knowledgeId);
        } else {
            inMemoryStores.remove(knowledgeId);
            log.info("[VectorStore] 已删除 InMemory 存储: knowledgeId={}", knowledgeId);
        }
    }

    /**
     * 按文档身份精确删除向量，不清空同一知识库其他文档的数据。
     *
     * <p>Milvus 与当前 InMemory 存储均使用相同的 metadata 过滤条件。 外部删除失败必须明确拒绝，清除客户端缓存不等于删除实际向量。
     *
     * @param documentId 文档ID，对应向量 metadata 中的 {@code documentId}
     * @param knowledgeId 知识库ID，用于定位已缓存的 InMemory 存储或 Milvus Collection
     * @throws ServiceException 清理未完成，不携带厂商原始错误
     */
    public void deleteByDocumentId(Long documentId, Long knowledgeId) {
        try {
            EmbeddingStore<TextSegment> store = "milvus".equalsIgnoreCase(getStoreType(knowledgeId))
                ? getStore(knowledgeId) : inMemoryStores.get(knowledgeId);
            if (store == null) {
                return;
            }
            Filter filter = MetadataFilterBuilder.metadataKey("documentId").isEqualTo(documentId.toString());
            store.removeAll(filter);
            log.info("[VectorStore] 按文档ID精确删除向量成功: documentId={}, knowledgeId={}", documentId, knowledgeId);
        } catch (Exception failure) {
            log.warn("[VectorStore] 文档向量清理未完成: documentId={}, knowledgeId={}, errorType={}",
                documentId, knowledgeId, failure.getClass().getSimpleName());
            throw new ServiceException("文档向量清理失败，请稍后重试或联系管理员。");
        }
    }
}

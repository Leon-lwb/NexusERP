package com.nexus.ai.service.rag;

import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.domain.entity.AiKnowledge;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeMapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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

    private final EmbeddingService embeddingService;
    private final AiKnowledgeChunkMapper chunkMapper;
    private final AiKnowledgeMapper knowledgeMapper;
    private final AiProperties aiProperties;

    /** InMemory 模式：按知识库ID隔离的存储实例 */
    private final ConcurrentHashMap<Long, InMemoryEmbeddingStore<TextSegment>> inMemoryStores = new ConcurrentHashMap<>();

    /** Milvus 模式：按知识库ID缓存 EmbeddingStore 实例 */
    private final ConcurrentHashMap<Long, EmbeddingStore<TextSegment>> milvusStores = new ConcurrentHashMap<>();

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
        String storeType = getStoreType(knowledgeId);
        if ("milvus".equalsIgnoreCase(storeType)) {
            return milvusStores.computeIfAbsent(knowledgeId, kid -> {
                String collectionName = getCollectionName(kid);
                String url = aiProperties.getVectorStore().getMilvus().getUrl();
                int dimension = aiProperties.getVectorStore().getMilvus().getDimension();
                log.info("[VectorStore] 创建 Milvus EmbeddingStore: collection={}, dimension={}", collectionName, dimension);
                return dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore.builder()
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
     * 将文本片段向量化并存储（使用知识库指定的 Embedding 模型）
     *
     * @param knowledgeId       知识库ID
     * @param chunks            文本片段列表
     * @param embeddingModelName 使用的 Embedding 模型名称（为空时使用默认模型）
     * @return 存储的向量ID列表
     */
    public List<String> storeChunks(Long knowledgeId, List<AiKnowledgeChunk> chunks, String embeddingModelName) {
        EmbeddingModel embeddingModel = (embeddingModelName != null && !embeddingModelName.isEmpty())
            ? embeddingService.getEmbeddingModel(embeddingModelName)
            : embeddingService.getDefaultEmbeddingModel();
        EmbeddingStore<TextSegment> store = getStore(knowledgeId);
        List<String> vectorIds = new ArrayList<>();

        for (AiKnowledgeChunk chunk : chunks) {
            try {
                dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
                metadata.put("knowledgeId", knowledgeId.toString());
                metadata.put("documentId", chunk.getDocumentId().toString());
                metadata.put("chunkId", chunk.getId().toString());

                TextSegment segment = TextSegment.from(chunk.getContent(), metadata);
                Embedding embedding = embeddingModel.embed(segment).content();
                String vectorId = store.add(embedding, segment);
                vectorIds.add(vectorId);

                // 更新片段的向量ID
                chunk.setVectorId(vectorId);
                chunkMapper.updateById(chunk);
            } catch (Exception e) {
                log.error("向量化片段失败: chunkId={}, error={}", chunk.getId(), e.getMessage());
            }
        }

        log.info("知识库[{}]向量化存储完成: 成功 {}/{} 片段, 存储类型={}", knowledgeId, vectorIds.size(), chunks.size(), getStoreType(knowledgeId));
        return vectorIds;
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
        EmbeddingModel embeddingModel = (embeddingModelName != null && !embeddingModelName.isEmpty())
            ? embeddingService.getEmbeddingModel(embeddingModelName)
            : embeddingService.getDefaultEmbeddingModel();
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
            // Milvus: 移除缓存，Collection 在下次创建时会自动重建
            milvusStores.remove(knowledgeId);
            log.info("[VectorStore] 已移除 Milvus 知识库缓存: knowledgeId={}", knowledgeId);
            // 注意：Milvus Collection 的实际删除需要通过 Milvus 管理工具或 SDK 操作
            // 此处仅清除本地缓存，避免使用已删除的知识库数据
        } else {
            inMemoryStores.remove(knowledgeId);
            log.info("[VectorStore] 已删除 InMemory 存储: knowledgeId={}", knowledgeId);
        }
    }

    /**
     * 删除指定文档的所有向量数据
     * <p>Milvus 模式：通过 {@link MetadataFilterBuilder} 构建 metadata 过滤条件，按 documentId 精确删除</p>
     * <p>  参考 ruoyi-ai 的 MilvusVectorStoreStrategy.removeByDocId()，已验证 {@code EmbeddingStore.removeAll(Filter)} 在 langchain4j 1.13.0 可用</p>
     * <p>InMemory 模式：移除知识库缓存，下次使用时自动重建（InMemoryEmbeddingStore 不支持按条件删除）</p>
     *
     * @param documentId  文档ID，对应存储向量时 metadata 中的 "documentId" 字段
     * @param knowledgeId 知识库ID，用于定位 Milvus Collection 或 InMemory 实例
     */
    public void deleteByDocumentId(Long documentId, Long knowledgeId) {
        if ("milvus".equalsIgnoreCase(getStoreType(knowledgeId))) {
            // Milvus: 通过 MetadataFilterBuilder 构建过滤条件，按 documentId 精确删除
            EmbeddingStore<TextSegment> store = getStore(knowledgeId);
            try {
                Filter filter = MetadataFilterBuilder.metadataKey("documentId").isEqualTo(documentId.toString());
                store.removeAll(filter);
                log.info("[VectorStore] Milvus 按文档ID删除向量成功: documentId={}, knowledgeId={}", documentId, knowledgeId);
            } catch (Exception e) {
                log.error("[VectorStore] Milvus 按文档ID删除向量失败: documentId={}, knowledgeId={}, error={}",
                    documentId, knowledgeId, e.getMessage());
                // 降级：清除缓存，提示重新导入
                milvusStores.remove(knowledgeId);
                log.warn("[VectorStore] 已清除 Milvus 知识库缓存 (knowledgeId={})，建议重新导入该知识库", knowledgeId);
            }
        } else {
            // InMemory: 不支持按条件删除，移除整个知识库缓存
            inMemoryStores.remove(knowledgeId);
            log.info("[VectorStore] InMemory 已移除知识库缓存: knowledgeId={}，下次使用时自动重建", knowledgeId);
        }
    }
}

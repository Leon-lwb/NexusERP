package com.nexus.ai.service.rag;

import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.domain.entity.AiKnowledge;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeMapper;
import com.nexus.common.ai.mapper.AiModelMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 向量存储服务重建路径单元测试（InMemory 模式 + Mockito 桩，无 Milvus 无网络）
 * <p>覆盖：重建严格模式（重嵌入全失败必须上抛而非静默空 READY）、成功路径进度回写与
 * vectorId 回写、维度解析口径（EMBEDDING+启用取模型维度，legacy/停用回退全局配置）。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class VectorStoreServiceTest {

    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final AiKnowledgeChunkMapper chunkMapper = mock(AiKnowledgeChunkMapper.class);
    private final AiKnowledgeMapper knowledgeMapper = mock(AiKnowledgeMapper.class);
    private final AiModelMapper modelMapper = mock(AiModelMapper.class);

    private VectorStoreService newService() {
        // 默认 vectorStore.type=inmemory：整个测试类不触碰 Milvus SDK
        return new VectorStoreService(embeddingService, chunkMapper, knowledgeMapper, modelMapper,
            new AiProperties());
    }

    @Test
    void rebuildReembedsAllChunksAndReportsProgress() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingService.getEmbeddingModel("text-embedding")).thenReturn(embeddingModel);
        Embedding vector = Embedding.from(new float[]{0.1f, 0.2f});
        when(embeddingModel.embedAll(any()))
            .thenReturn(new Response<>(List.of(vector, vector)));

        VectorStoreService service = newService();
        service.rebuildKnowledge(1L, "text-embedding", 64, List.of(chunk(11L), chunk(12L)));

        // 两片段 vectorId 均回写
        verify(chunkMapper).updateById(org.mockito.ArgumentMatchers.<AiKnowledgeChunk>argThat(c ->
            c.getId().equals(11L) && c.getVectorId() != null));
        verify(chunkMapper).updateById(org.mockito.ArgumentMatchers.<AiKnowledgeChunk>argThat(c ->
            c.getId().equals(12L) && c.getVectorId() != null));
        // 单批 2/2 → min(99, 200/2)=99；进度为条件写入（WHERE REBUILDING）
        verify(knowledgeMapper).update(org.mockito.ArgumentMatchers.<AiKnowledge>argThat(k ->
            Integer.valueOf(99).equals(k.getVectorIndexProgress())), any());
    }

    @Test
    void strictReembedRethrowsWhenEmbeddingTotallyFails() {
        // Embedding 端点完全不可用：批量与逐条兜底全部失败，严格模式必须上抛，
        // 由 KnowledgeIndexRebuildService 转 FAILED——绝不静默返回产出空索引 + READY/100
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingService.getEmbeddingModel("text-embedding")).thenReturn(embeddingModel);
        when(embeddingModel.embedAll(any())).thenThrow(new RuntimeException("embedding endpoint down"));
        when(embeddingModel.embed(any(TextSegment.class))).thenThrow(new RuntimeException("embedding endpoint down"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> newService().rebuildKnowledge(1L, "text-embedding", 64, List.of(chunk(11L), chunk(12L))));
        assertTrue(e.getMessage().contains("重嵌入片段失败"), e.getMessage());
    }

    @Test
    void resolveDimensionPrefersActiveEmbeddingModelConfiguration() {
        AiModel model = new AiModel();
        model.setModelName("text-embedding");
        model.setModelType("EMBEDDING");
        model.setStatus("0");
        model.setEmbeddingDimension(1536);
        when(modelMapper.selectOne(any())).thenReturn(model);

        assertEquals(1536, newService().resolveEmbeddingDimension("text-embedding"));
    }

    @Test
    void resolveDimensionFallsBackForInactiveModelOrLegacyMissingDimension() {
        AiModel inactive = new AiModel();
        inactive.setModelName("text-embedding");
        inactive.setModelType("EMBEDDING");
        inactive.setStatus("1");
        inactive.setEmbeddingDimension(1536);
        when(modelMapper.selectOne(any())).thenReturn(inactive);

        VectorStoreService service = newService();
        // 停用模型 / 名称空白（legacy 未配置维度）→ 全局配置兜底（AiProperties 默认 1024）
        assertEquals(1024, service.resolveEmbeddingDimension("text-embedding"));
        assertEquals(1024, service.resolveEmbeddingDimension(null));
    }

    private AiKnowledgeChunk chunk(Long id) {
        AiKnowledgeChunk chunk = new AiKnowledgeChunk();
        chunk.setId(id);
        chunk.setKnowledgeId(1L);
        chunk.setDocumentId(7L);
        chunk.setChunkIndex(0);
        chunk.setContent("测试片段内容 " + id);
        return chunk;
    }
}

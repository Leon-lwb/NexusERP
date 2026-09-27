package com.nexus.ai.service.rag;

import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.domain.vo.AiKnowledgeVo;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 检索入口索引状态门单元测试：向量索引非 READY 的知识库必须拒绝检索
 * <p>真实入口 {@code retrieve(knowledgeId, query, overrides)} 同时承接对话 RAG
 * （AiServiceFacade.augmentPrompt）与检索测试面板（AiKnowledgeController.retrieval），
 * 门在读取知识库配置后、任何向量/全文检索前触发。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class RetrieverServiceIndexGateTest {

    private final VectorStoreService vectorStoreService = mock(VectorStoreService.class);
    private final IAiKnowledgeService aiKnowledgeService = mock(IAiKnowledgeService.class);

    private RetrieverService newService() {
        return new RetrieverService(vectorStoreService, aiKnowledgeService,
            mock(com.nexus.common.ai.mapper.AiKnowledgeChunkMapper.class),
            mock(JdbcTemplate.class), mock(RerankService.class), mock(IAiModelService.class),
            mock(com.nexus.ai.service.kg.GraphStoreStrategy.class), new AiProperties(),
            mock(com.nexus.common.ai.mapper.AiKnowledgeDocumentMapper.class));
    }

    @Test
    void retrievalRejectedWhileIndexRebuilding() {
        AiKnowledgeVo vo = knowledgeVo("REBUILDING");
        when(aiKnowledgeService.selectById(1L)).thenReturn(vo);
        ServiceException e = assertThrows(ServiceException.class,
            () -> newService().retrieve(1L, "查询", Map.of()));
        assertTrue(e.getMessage().contains("知识库索引正在重建或重建失败"), e.getMessage());
    }

    @Test
    void retrievalRejectedWhenIndexFailed() {
        AiKnowledgeVo vo = knowledgeVo("FAILED");
        when(aiKnowledgeService.selectById(1L)).thenReturn(vo);
        ServiceException e = assertThrows(ServiceException.class,
            () -> newService().retrieve(1L, "查询", null));
        assertTrue(e.getMessage().contains("知识库索引正在重建或重建失败"), e.getMessage());
    }

    @Test
    void retrievalToleratesNullIndexStatusOnLegacyRows() {
        // 列默认值 READY 之前/迁移宽容处理：status=null 的存量行按 READY 放行（空结果，不抛异常）
        AiKnowledgeVo vo = knowledgeVo(null);
        when(aiKnowledgeService.selectById(1L)).thenReturn(vo);

        List<String> results = newService().retrieve(1L, "查询", null);

        assertTrue(results.isEmpty());
    }

    private AiKnowledgeVo knowledgeVo(String indexStatus) {
        AiKnowledgeVo vo = new AiKnowledgeVo();
        vo.setId(1L);
        vo.setKnowledgeName("测试知识库");
        vo.setEmbeddingModel("text-embedding");
        vo.setVectorIndexStatus(indexStatus);
        return vo;
    }
}

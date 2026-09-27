package com.nexus.ai.service.rag;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.kg.GraphStoreStrategy;
import com.nexus.common.ai.decision.dto.RetrievalHit;
import com.nexus.common.ai.domain.entity.AiKnowledgeDocument;
import com.nexus.common.ai.domain.vo.AiKnowledgeVo;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeDocumentMapper;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.tenant.helper.TenantHelper;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.RowMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class RetrieverServiceProvenanceTest {
    private final VectorStoreService vectors = mock(VectorStoreService.class);
    private final IAiKnowledgeService knowledge = mock(IAiKnowledgeService.class);
    private final AiKnowledgeDocumentMapper documents = mock(AiKnowledgeDocumentMapper.class);
    private final AiKnowledgeChunkMapper chunks = mock(AiKnowledgeChunkMapper.class);
    private final RerankService rerank = mock(RerankService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RetrieverService service = new RetrieverService(vectors, knowledge,
        chunks, jdbc, rerank,
        mock(IAiModelService.class), mock(GraphStoreStrategy.class), new AiProperties(), documents);

    @Test
    void sameTextInTwoChunksKeepsDistinctProvenanceAndLegacyContent() {
        var config = config(false);
        when(knowledge.selectById(7L)).thenReturn(config);
        var first = match(1001L, 11L, "相同内容", .91);
        var second = match(1002L, 12L, "相同内容", .82);
        when(vectors.search(eq(7L), eq("问答"), eq(5), eq(0.5), eq("embed")))
            .thenReturn(List.of(first, second));
        when(documents.selectList(any())).thenReturn(List.of(document(11L, "制度A"), document(12L, "制度B")));
        when(chunks.selectList(any())).thenReturn(List.of(stored(1001L, 11L, "相同内容"),
            stored(1002L, 12L, "相同内容")));

        List<RetrievalHit> hits = service.retrieveHits(7L, "问答");

        assertEquals(List.of(1001L, 1002L), hits.stream().map(RetrievalHit::chunkId).toList());
        assertEquals(List.of(11L, 12L), hits.stream().map(RetrievalHit::documentId).toList());
        assertEquals(List.of("制度A", "制度B"), hits.stream().map(RetrievalHit::documentName).toList());
        assertEquals(List.of("VECTOR", "VECTOR"), hits.stream().map(RetrievalHit::sourceType).toList());
        assertEquals(List.of("相同内容", "相同内容"), service.retrieve(7L, "问答"));
        verify(documents, times(2)).selectList(any()); // 每次检索仅执行一批查询，避免逐项查询。
    }

    @Test
    void rerankReordersChunkObjectsWithoutLosingIdsOrNames() {
        when(knowledge.selectById(7L)).thenReturn(config(true));
        var first = match(1001L, 11L, "A", .91);
        var second = match(1002L, 12L, "B", .82);
        when(vectors.search(eq(7L), eq("问答"), eq(15), eq(0.5), eq("embed")))
            .thenReturn(List.of(first, second));
        when(documents.selectList(any())).thenReturn(List.of(document(11L, "制度A"), document(12L, "制度B")));
        when(rerank.rerank(eq("问答"), eq(List.of("A", "B")), eq(5), isNull()))
            .thenReturn(List.of("B", "A"));
        var hits = service.retrieveHits(7L, "问答");
        assertEquals(List.of(1002L, 1001L), hits.stream().map(RetrievalHit::chunkId).toList());
        assertEquals(List.of("制度B", "制度A"), hits.stream().map(RetrievalHit::documentName).toList());
    }

    @Test
    void legacySearchKeepsTextWhenDocumentWasRemovedButDecisionHitIsMarkedUnverified() {
        when(knowledge.selectById(7L)).thenReturn(config(false));
        var missingDocument = match(1001L, 11L, "历史片段", .85);
        when(vectors.search(eq(7L), eq("问答"), eq(5), eq(0.5), eq("embed")))
            .thenReturn(List.of(missingDocument));
        when(documents.selectList(any())).thenReturn(List.of());
        assertEquals(List.of("历史片段"), service.retrieve(7L, "问答"));
        var hit = service.retrieveHits(7L, "问答").get(0);
        assertNull(hit.documentName());
        assertEquals("UNVERIFIED", hit.sourceType());
    }

    @Test
    void mysqlFulltextQueryIsBoundToTenantRatherThanOnlyKnowledgeId() {
        var hybrid = config(false);
        hybrid.setEnableHybrid(1);
        when(knowledge.selectById(7L)).thenReturn(hybrid);
        when(vectors.search(eq(7L), eq("问答"), eq(5), eq(0.5), eq("embed")))
            .thenReturn(List.of());
        try (MockedStatic<TenantHelper> tenants = mockStatic(TenantHelper.class)) {
            tenants.when(TenantHelper::getTenantId).thenReturn("TENANT-A");
            service.retrieveHits(7L, "问答");
            var sql = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(jdbc).query(sql.capture(), any(RowMapper.class),
                eq(7L), eq("TENANT-A"), eq("问答"), eq("问答"), eq(5));
            assertTrue(sql.getValue().contains("tenant_id = ?"));
        }
    }

    @Test
    void hybridMergeUsesCurrentDatabaseTextAndItsMatchingDocumentId() {
        var hybrid = config(false);
        hybrid.setEnableHybrid(1);
        when(knowledge.selectById(7L)).thenReturn(hybrid);
        var oldVector = match(1001L, 11L, "旧索引文本", .91);
        when(vectors.search(eq(7L), eq("问答"), eq(5), eq(0.5), eq("embed")))
            .thenReturn(List.of(oldVector));
        var latest = new com.nexus.common.ai.domain.entity.AiKnowledgeChunk();
        latest.setId(1001L); latest.setKnowledgeId(7L); latest.setDocumentId(12L);
        latest.setContent("当前文档文本");
        try (MockedStatic<TenantHelper> tenants = mockStatic(TenantHelper.class)) {
            tenants.when(TenantHelper::getTenantId).thenReturn("TENANT-A");
            doReturn(List.of(latest)).when(jdbc).query(anyString(), any(RowMapper.class),
                eq(7L), eq("TENANT-A"), eq("问答"), eq("问答"), eq(5));
            when(documents.selectList(any())).thenReturn(List.of(document(12L, "新版制度")));
            var hit = service.retrieveHits(7L, "问答").get(0);
            assertEquals(12L, hit.documentId());
            assertEquals("当前文档文本", hit.content());
            assertEquals("新版制度", hit.documentName());
        }
    }

    @Test
    void duplicateTextDoesNotAllowTextOnlyRerankerToMisattributeChunkIds() {
        when(knowledge.selectById(7L)).thenReturn(config(true));
        var first = match(1001L, 11L, "相同文本", .91);
        var second = match(1002L, 12L, "相同文本", .82);
        when(vectors.search(eq(7L), eq("问答"), eq(15), eq(0.5), eq("embed")))
            .thenReturn(List.of(first, second));
        when(documents.selectList(any())).thenReturn(List.of(document(11L, "制度A"), document(12L, "制度B")));
        var hits = service.retrieveHits(7L, "问答");
        assertEquals(List.of(1001L, 1002L), hits.stream().map(RetrievalHit::chunkId).toList());
        verifyNoInteractions(rerank);
    }

    @Test
    void staleVectorTextNeverBecomesVerifiedNormativeEvidence() {
        when(knowledge.selectById(7L)).thenReturn(config(false));
        var staleVector = match(1001L, 11L, "旧索引文本", .91);
        when(vectors.search(eq(7L), eq("问答"), eq(5), eq(0.5), eq("embed")))
            .thenReturn(List.of(staleVector));
        when(documents.selectList(any())).thenReturn(List.of(document(11L, "正式制度")));
        var stored = new com.nexus.common.ai.domain.entity.AiKnowledgeChunk();
        stored.setId(1001L); stored.setDocumentId(11L); stored.setKnowledgeId(7L);
        stored.setContent("新版制度正文");
        when(chunks.selectList(any())).thenReturn(List.of(stored));
        var hit = service.retrieveHits(7L, "问答").get(0);
        assertEquals("UNVERIFIED", hit.sourceType());
        assertThrows(IllegalArgumentException.class, () ->
            new com.nexus.ai.service.decision.DecisionEvidenceService().fromRetrievalHits(List.of(hit)));
    }

    @Test
    void documentWithoutAnyTimestampHasNoVerifiedVersion() {
        when(knowledge.selectById(7L)).thenReturn(config(false));
        var vector = match(1001L, 11L, "正文", .9);
        when(vectors.search(eq(7L), eq("问答"), eq(5), eq(0.5), eq("embed")))
            .thenReturn(List.of(vector));
        when(chunks.selectList(any())).thenReturn(List.of(stored(1001L, 11L, "正文")));
        var document = document(11L, "制度");
        document.setUpdateTime(null); document.setCreateTime(null);
        when(documents.selectList(any())).thenReturn(List.of(document));
        var hit = service.retrieveHits(7L, "问答").get(0);
        assertEquals("UNVERIFIED", hit.sourceType());
        assertNull(hit.version());
    }

    private static com.nexus.common.ai.domain.entity.AiKnowledgeChunk stored(long id, long document, String content) {
        var chunk = new com.nexus.common.ai.domain.entity.AiKnowledgeChunk();
        chunk.setId(id); chunk.setKnowledgeId(7L); chunk.setDocumentId(document); chunk.setContent(content);
        return chunk;
    }

    private static AiKnowledgeVo config(boolean useRerank) {
        AiKnowledgeVo config = new AiKnowledgeVo();
        config.setId(7L);
        config.setEmbeddingModel("embed");
        config.setRetrieveLimit(5);
        config.setSimilarityThreshold(0.5);
        config.setVectorIndexStatus("READY");
        config.setEnableRerank(useRerank ? 1 : 0);
        return config;
    }

    @SuppressWarnings("unchecked")
    private static EmbeddingMatch<TextSegment> match(long chunk, long document, String text, double score) {
        EmbeddingMatch<TextSegment> match = mock(EmbeddingMatch.class);
        when(match.embedded()).thenReturn(TextSegment.from(text, new Metadata()
            .put("knowledgeId", "7").put("documentId", String.valueOf(document))
            .put("chunkId", String.valueOf(chunk))));
        when(match.score()).thenReturn(score);
        return match;
    }

    private static AiKnowledgeDocument document(long id, String filename) {
        AiKnowledgeDocument doc = new AiKnowledgeDocument();
        doc.setId(id); doc.setKnowledgeId(7L); doc.setFileName(filename); doc.setStatus("2");
        doc.setUpdateTime(new Date(1790164800000L));
        return doc;
    }
}

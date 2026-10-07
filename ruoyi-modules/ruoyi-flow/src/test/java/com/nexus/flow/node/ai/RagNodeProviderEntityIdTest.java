package com.nexus.flow.node.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.service.rag.RetrieverService;
import com.nexus.common.ai.decision.dto.RetrievalHit;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.state.FlowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 RAG 节点的实体 ID 回归；仅替换检索服务，不连接向量库或外部模型。 */
class RagNodeProviderEntityIdTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long KNOWLEDGE_ID = 2103871286244564993L;
    private final RetrieverService retriever = mock(RetrieverService.class);
    private final RagNodeProvider provider = new RagNodeProvider(retriever);

    @Test
    @DisplayName("控件保存的字符串知识库 ID 可验证且精确传入检索")
    void savedTextIdReachesExactKnowledge() throws Exception {
        ObjectNode config = (ObjectNode) MAPPER.readTree("{\"knowledgeId\":\"2103871286244564993\",\"queryTemplate\":\"查询 {{input.subject}}\"}");
        var hit = new RetrievalHit(KNOWLEDGE_ID, 2103871286244564995L, 2103871286244564997L,
            "工艺处置指引", "合格材料按指定工艺加工", 0.9, null, 0.8, null, "v1", LocalDate.of(2026, 10, 7), "DOCUMENT");
        when(retriever.retrieveHits(KNOWLEDGE_ID, "查询 装配", null)).thenReturn(List.of(hit));
        assertDoesNotThrow(() -> provider.validateConfig(config));
        JsonNode output = provider.execute(context(config));
        assertEquals("2103871286244564993", output.at("/hits/0/knowledgeId").textValue());
        assertEquals("2103871286244564995", output.at("/hits/0/documentId").textValue());
        assertEquals("2103871286244564997", output.at("/hits/0/chunkId").textValue());
        assertEquals("工艺处置指引", output.at("/hits/0/documentName").textValue());
        assertEquals("合格材料按指定工艺加工", output.at("/chunks/0").textValue());
        assertTrue(output.get("count").isIntegralNumber());
        assertEquals(1, output.get("count").intValue());
        assertTrue(output.get("augmentedPrompt").textValue().contains("查询 装配"));
        assertEquals("2103871286244564993", MAPPER.readTree(MAPPER.writeValueAsString(output)).at("/hits/0/knowledgeId").textValue());
        verify(retriever).retrieveHits(KNOWLEDGE_ID, "查询 装配", null);
        verifyNoMoreInteractions(retriever);
    }

    @Test
    @DisplayName("存量整型知识库 ID 仍支持，不重写历史配置")
    void legacyIntegralIdStillWorks() {
        ObjectNode config = config().put("knowledgeId", KNOWLEDGE_ID);
        when(retriever.retrieveHits(KNOWLEDGE_ID, "查询 装配", null)).thenReturn(List.of());
        assertDoesNotThrow(() -> provider.validateConfig(config));
        provider.execute(context(config));
        assertTrue(config.get("knowledgeId").isIntegralNumber());
        verify(retriever).retrieveHits(KNOWLEDGE_ID, "查询 装配", null);
    }

    @Test
    @DisplayName("第二雪花 ID 与 Long 上界均精确到达检索边界")
    void otherSnowflakeAndUpperBoundStayExact() {
        for (String id : new String[]{"9007199254740993", "9223372036854775807"}) {
            ObjectNode config = config().put("knowledgeId", id);
            long expected = Long.parseLong(id);
            when(retriever.retrieveHits(expected, "查询 装配", null)).thenReturn(List.of());
            assertDoesNotThrow(() -> provider.validateConfig(config));
            provider.execute(context(config));
            verify(retriever).retrieveHits(expected, "查询 装配", null);
        }
    }

    @Test
    @DisplayName("零值保持原配置契约，不选择其他知识库")
    void defaultZeroKeepsContract() {
        when(retriever.retrieveHits(0L, "查询 装配", null)).thenReturn(List.of());
        for (JsonNode id : new JsonNode[]{MAPPER.getNodeFactory().numberNode(0), MAPPER.getNodeFactory().textNode("0")}) {
            ObjectNode config = config();
            config.set("knowledgeId", id);
            assertDoesNotThrow(() -> provider.validateConfig(config));
            provider.execute(context(config));
        }
        verify(retriever, times(2)).retrieveHits(0L, "查询 装配", null);
        verifyNoMoreInteractions(retriever);
    }

    @Test
    @DisplayName("非法文本不得在执行时被默认为知识库零")
    void invalidTextCannotFallBackToZero() {
        for (String id : new String[]{"", " ", "abc", "+1", "-1", "01", " 1", "1.5", "1e3", "9223372036854775808"}) {
            ObjectNode config = config().put("knowledgeId", id);
            assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(config), id);
            assertThrows(IllegalArgumentException.class, () -> provider.execute(context(config)), id);
        }
        verifyNoInteractions(retriever);
    }

    @Test
    @DisplayName("缺失与非整数来源在检索之前失败")
    void invalidNodeTypesNeverRetrieve() {
        assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(null));
        assertThrows(IllegalArgumentException.class, () -> provider.execute(context(null)));
        ObjectNode missing = config();
        assertEquals("RAG 节点缺少 knowledgeId 配置", assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(missing)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> provider.execute(context(missing)));
        for (JsonNode id : new JsonNode[]{MAPPER.nullNode(), MAPPER.getNodeFactory().booleanNode(true),
            MAPPER.getNodeFactory().numberNode(-1), MAPPER.getNodeFactory().numberNode(1.5),
            MAPPER.getNodeFactory().numberNode(1.0), MAPPER.getNodeFactory().numberNode(new BigInteger("9223372036854775808")),
            MAPPER.createObjectNode(), MAPPER.createArrayNode()}) {
            ObjectNode config = config();
            config.set("knowledgeId", id);
            assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(config));
            assertThrows(IllegalArgumentException.class, () -> provider.execute(context(config)));
        }
        verifyNoInteractions(retriever);
    }

    @Test
    @DisplayName("查询模板仍必填，不因 ID 修复绕过原校验")
    void queryTemplateRemainsRequired() {
        ObjectNode config = MAPPER.createObjectNode().put("knowledgeId", 1);
        assertEquals("RAG 节点缺少 queryTemplate 配置", assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(config)).getMessage());
        config.put("queryTemplate", " ");
        assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(config));
        verifyNoInteractions(retriever);
    }

    @Test
    @DisplayName("条数和相似度仍作为数字覆盖，不改变检索规则")
    void ordinaryNumericOverridesRemainNumbers() {
        ObjectNode config = config().put("knowledgeId", "2103871286244564993").put("topK", 3).put("minScore", 0.6);
        Map<String, Object> overrides = Map.of("topK", 3, "minScore", 0.6);
        when(retriever.retrieveHits(KNOWLEDGE_ID, "查询 装配", overrides)).thenReturn(List.of());
        assertDoesNotThrow(() -> provider.validateConfig(config));
        provider.execute(context(config));
        verify(retriever).retrieveHits(KNOWLEDGE_ID, "查询 装配", overrides);
    }

    @Test
    @DisplayName("空检索仍明确返回零片段，不伪造命中")
    void emptyRetrievalKeepsExistingResult() {
        ObjectNode config = config().put("knowledgeId", 42);
        when(retriever.retrieveHits(42L, "查询 装配", null)).thenReturn(List.of());
        JsonNode result = provider.execute(context(config));
        assertEquals(0, result.get("count").intValue());
        assertTrue(result.get("hits").isEmpty());
        assertTrue(result.get("chunks").isEmpty());
        assertTrue(result.get("augmentedPrompt").textValue().contains("查询 装配"));
        verify(retriever).retrieveHits(42L, "查询 装配", null);
    }

    @Test
    @DisplayName("实体选项默认值与普通参数结构保持兼容")
    void schemaContractIsUnchanged() {
        var schemas = provider.getParamSchemas();
        assertEquals("aiKnowledge", schemas.get(0).getOptionsSource());
        assertEquals("number", schemas.get(0).getType());
        assertEquals("0", schemas.get(0).getDefaultValue());
        assertEquals("number", schemas.get(2).getType());
        assertEquals("number", schemas.get(3).getType());
        verifyNoInteractions(retriever);
    }

    private ObjectNode config() {
        return MAPPER.createObjectNode().put("queryTemplate", "查询 {{input.subject}}");
    }

    private FlowExecutionContext context(ObjectNode config) {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setNodeName("资料检索");
        ctx.setConfig(config);
        ctx.setState(new FlowState(MAPPER.createObjectNode().put("subject", "装配")));
        return ctx;
    }
}

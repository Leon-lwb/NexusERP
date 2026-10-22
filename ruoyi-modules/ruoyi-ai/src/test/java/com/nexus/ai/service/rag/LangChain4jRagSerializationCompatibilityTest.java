package com.nexus.ai.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.chat.PersistentChatMemoryStore;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeMapper;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.mapper.AiModelMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证升级后向量批次对应关系、聊天记忆及应用 Jackson 数值契约；仅使用合成数据。 */
class LangChain4jRagSerializationCompatibilityTest {
    /** 真实向量存储必须维持片段顺序、维度、元数据和向量标识的对应关系。 */
    @Test
    void realInMemoryStoreRetainsBatchOrderDimensionsAndProvenance() {
        var embeddings = mock(EmbeddingService.class);
        var chunks = mock(AiKnowledgeChunkMapper.class);
        var inputs = new ArrayList<String>();
        EmbeddingModel model = new EmbeddingModel() {
            @Override public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                inputs.addAll(segments.stream().map(TextSegment::text).toList());
                return Response.from(List.of(Embedding.from(new float[]{1, 0, 0}), Embedding.from(new float[]{0, 1, 0})));
            }
        };
        when(embeddings.getEmbeddingModel("offline-embedding")).thenReturn(model);
        var service = new VectorStoreService(embeddings, chunks, mock(AiKnowledgeMapper.class), mock(AiModelMapper.class), new AiProperties());
        var first = chunk(9007199254740993L, "库存凭证");
        var second = chunk(9007199254740995L, "采购凭证");
        var ids = service.storeChunks(19L, List.of(first, second), "offline-embedding");
        assertEquals(List.of("库存凭证", "采购凭证"), inputs);
        assertEquals(2, ids.size());
        assertEquals(ids.get(0), first.getVectorId());
        assertEquals(ids.get(1), second.getVectorId());
        EmbeddingStore<TextSegment> store = ReflectionTestUtils.invokeMethod(service, "getStore", 19L);
        var result = store.search(EmbeddingSearchRequest.builder().queryEmbedding(Embedding.from(new float[]{0, 1, 0})).maxResults(1).build()).matches().get(0);
        assertEquals(ids.get(1), result.embeddingId());
        assertEquals("采购凭证", result.embedded().text());
        assertEquals("9007199254740995", result.embedded().metadata().getString("chunkId"));
        assertEquals("19", result.embedded().metadata().getString("knowledgeId"));
        verify(chunks, times(2)).updateById(any(AiKnowledgeChunk.class));
    }

    /** SDK 聊天消息编码不能截断工具参数中的大整数与小数。 */
    @Test
    void sdkChatHistoryRoundTripPreservesToolArgumentsAndChinese() {
        String arguments = "{\"id\":9007199254740993,\"amount\":12345.67890123456789}";
        var tool = ToolExecutionRequest.builder().id("offline-tool").name("readStock").arguments(arguments).build();
        List<ChatMessage> messages = List.of(UserMessage.from("库存凭证"), dev.langchain4j.data.message.AiMessage.from(tool), ToolExecutionResultMessage.from(tool, "库存正常"), dev.langchain4j.data.message.AiMessage.from("已核对"));
        var restored = ChatMessageDeserializer.messagesFromJson(ChatMessageSerializer.messagesToJson(messages));
        assertEquals(messages, restored);
        assertEquals(arguments, ((dev.langchain4j.data.message.AiMessage) restored.get(1)).toolExecutionRequests().get(0).arguments());
    }

    /** 生产记忆适配器继续恢复完整的文本角色，不改变持久化消息标识。 */
    @Test
    void persistentMemoryRestoresRolesAndLargeSessionId() {
        var mapper = mock(AiMessageMapper.class);
        var system = message("SYSTEM", "只读查询");
        var user = message("USER", "库存情况");
        var answer = message("ASSISTANT", "库存正常");
        when(mapper.selectList(any())).thenReturn(List.of(system, user, answer));
        assertEquals(List.of(dev.langchain4j.data.message.SystemMessage.from("只读查询"), UserMessage.from("库存情况"), dev.langchain4j.data.message.AiMessage.from("库存正常")),
            new PersistentChatMemoryStore(mapper).getMessages("9007199254740993"));
    }

    /** 使用两个真实应用模块注册器，验证大整数、金额与时间的兼容性。 */
    @Test
    void applicationJacksonModulesPreserveSnowflakeDecimalAndTime() throws Exception {
        var mapper = new ObjectMapper()
            .registerModule(new com.nexus.common.json.config.JacksonConfig().registerJavaTimeModule())
            .registerModule(new com.nexus.common.web.config.JacksonConfig().bigNumberModule());
        var original = new NumericPayload(9007199254740993L, new BigDecimal("12345.67890123456789"), LocalDateTime.of(2026, 10, 22, 12, 30, 0));
        String serialized = mapper.writeValueAsString(original);
        var tree = mapper.readTree(serialized);
        assertTrue(tree.get("id").isTextual());
        assertEquals("9007199254740993", tree.get("id").textValue());
        assertEquals("12345.67890123456789", tree.get("amount").textValue());
        assertEquals(original, mapper.readValue(serialized, NumericPayload.class));
    }

    private static AiKnowledgeChunk chunk(long id, String content) {
        var chunk = new AiKnowledgeChunk(); chunk.setId(id); chunk.setKnowledgeId(19L); chunk.setDocumentId(23L); chunk.setContent(content); return chunk;
    }
    private static com.nexus.common.ai.domain.entity.AiMessage message(String role, String content) {
        var message = new com.nexus.common.ai.domain.entity.AiMessage(); message.setId(9007199254740993L); message.setRole(role); message.setContent(content); return message;
    }
    private record NumericPayload(long id, BigDecimal amount, LocalDateTime occurredAt) { }
}

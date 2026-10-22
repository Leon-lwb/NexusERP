package com.nexus.ai.service.governance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.service.IAiModelService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.*;

class AiAbRunServiceTest {
    AiExecutionRepositoryTest f;
    AiAbRunService runs;
    AiModel model;
    AtomicInteger calls;
    ChatModelFactory factory;
    IAiModelService models;

    @BeforeEach
    void setup() throws Exception {
        f = new AiExecutionRepositoryTest();
        f.setup();
        f.jdbc.execute(
                "CREATE TABLE ai_ab_test(id bigint PRIMARY KEY,tenant_id varchar(20),test_name"
                        + " varchar(128),model_a_id bigint,model_b_id bigint,test_questions"
                        + " longtext,status varchar(32))");
        f.jdbc.update(
                "INSERT INTO ai_ab_test VALUES"
                        + " (11,'000001','fixture',21,21,'[\"same\",\"same\"]','PENDING')");
        model = new AiModel();
        model.setId(21L);
        model.setModelName("fixture");
        model.setModelType("TEXT");
        model.setInputCapabilities("TEXT");
        model.setOutputCapabilities("TEXT");
        model.setApiProtocol("OPENAI_CHAT_COMPLETIONS");
        model.setMaxTokens(100);
        model.setInputPricePerMillion(BigDecimal.ONE);
        model.setOutputPricePerMillion(BigDecimal.ONE);
        model.setCurrency("CNY");
        models = mock(IAiModelService.class);
        when(models.selectModelById(21L)).thenReturn(model);
        factory = mock(ChatModelFactory.class);
        calls = new AtomicInteger();
        when(factory.getChatModel(eq(21L), any()))
                .thenReturn(
                        new ChatModel() {
                            public ChatResponse doChat(ChatRequest r) {
                                calls.incrementAndGet();
                                return ChatResponse.builder()
                                        .aiMessage(AiMessage.from("answer"))
                                        .build();
                            }
                        });
        var p = new AiGovernanceProperties();
        p.setEnabled(true);
        p.setAbEnabled(true);
        runs =
                new AiAbRunService(
                        f.jdbc.getDataSource(), f.repo, models, factory, Runnable::run, p);
    }

    @Test
    void duplicateTextAndSameModelRemainDistinctAndStartIsIdempotent() {
        String id = runs.start("000001", 7L, 11L, "same-start-key-123");
        String again = runs.start("000001", 7L, 11L, "same-start-key-123");
        assertThat(again).isEqualTo(id);
        assertThat(calls).hasValue(4);
        assertThat(runs.items("000001", 7L, id)).hasSize(4);
        assertThat(runs.info("000001", 7L, id).get("status")).isEqualTo("SUCCEEDED");
        assertThatThrownBy(() -> runs.items("000002", 7L, id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partialFailureIsNotCompletedAndResumeKeepsSuccessfulItems() {
        var sequence = new AtomicInteger();
        when(factory.getChatModel(eq(21L), any()))
                .thenReturn(
                        new ChatModel() {
                            public ChatResponse doChat(ChatRequest r) {
                                if (sequence.getAndIncrement() == 0)
                                    throw new IllegalStateException("fixture");
                                return ChatResponse.builder()
                                        .aiMessage(AiMessage.from("answer"))
                                        .build();
                            }
                        });
        String id = runs.start("000001", 7L, 11L, "partial-key-12345");
        assertThat(runs.info("000001", 7L, id).get("status")).isEqualTo("PARTIAL_SUCCESS");
        runs.resume("000001", 7L, id);
        assertThat(sequence).hasValue(5);
        assertThat(runs.info("000001", 7L, id).get("status")).isEqualTo("SUCCEEDED");
    }

    @Test
    void queuedRunRefusesChangedModelConfiguration() {
        var queued = new ArrayList<Runnable>();
        var p = new AiGovernanceProperties();
        p.setEnabled(true);
        p.setAbEnabled(true);
        runs = new AiAbRunService(f.jdbc.getDataSource(), f.repo, models, factory, queued::add, p);
        String id = runs.start("000001", 7L, 11L, "frozen-key-123456");
        model.setModelName("changed");
        queued.remove(0).run();
        assertThat(calls).hasValue(0);
        assertThat(runs.info("000001", 7L, id).get("status")).isEqualTo("FAILED");
    }
}

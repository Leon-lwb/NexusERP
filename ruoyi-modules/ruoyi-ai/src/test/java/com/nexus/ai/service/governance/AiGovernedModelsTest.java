package com.nexus.ai.service.governance;

import static org.assertj.core.api.Assertions.*;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.governance.*;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.*;
import dev.langchain4j.model.chat.request.*;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.output.TokenUsage;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class AiGovernedModelsTest {
    AiCallLedgerServiceTest fixture;
    AiGovernedModels models;
    AiModel config;
    AiGovernanceProperties props;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AiCallLedgerServiceTest();
        fixture.setup();
        props = new AiGovernanceProperties();
        props.setEnabled(true);
        models = new AiGovernedModels(fixture.ledger, props);
        config = new AiModel();
        config.setId(11L);
        config.setModelName("fixture");
        config.setCurrency("CNY");
        config.setInputPricePerMillion(BigDecimal.ONE);
        config.setOutputPricePerMillion(BigDecimal.ONE);
        config.setMaxTokens(100);
        config.setApiProtocol("OPENAI_CHAT_COMPLETIONS");
    }

    static ChatResponse response() {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("answer"))
                .tokenUsage(new TokenUsage(10, 5))
                .build();
    }

    @Test
    void verifiedTextBudgetAcceptsPlainAssistantHistory() {
        props.getBudgetVerifiedModelIds().add(11L);
        config.setModelName("gpt-4o");
        config.setContextWindow(1000);
        fixture.ledger.configureBudget(
                "000001", "month", "CNY", "TENANT_MONTH", "", BigDecimal.ONE, true, 7L);
        ChatModel raw =
                new ChatModel() {
                    public ChatResponse doChat(ChatRequest request) {
                        return response();
                    }
                };
        try (var scope = AiInvocationScope.open(fixture.context, () -> {})) {
            assertThat(
                            models.chat(raw, config)
                                    .chat(
                                            ChatRequest.builder()
                                                    .messages(
                                                            UserMessage.from("first"),
                                                            AiMessage.from("answer"),
                                                            UserMessage.from("second"))
                                                    .build())
                                    .aiMessage()
                                    .text())
                    .isEqualTo("answer");
        }
        assertThat(fixture.ledger.calls("000001", 7L, "req", 100)).hasSize(1);
    }

    @Test
    void eachActualInvocationIsRecordedWithCapturedModel() {
        var raw =
                new ChatModel() {
                    public ChatResponse doChat(ChatRequest r) {
                        return response();
                    }
                };
        var model = models.chat(raw, config);
        try (var scope = AiInvocationScope.open(fixture.context, () -> {})) {
            model.chat("one");
            model.chat("two");
        }
        assertThat(fixture.ledger.calls("000001", 7L, "req", 100)).hasSize(2);
        assertThat(
                        fixture.jdbc.queryForObject(
                                "SELECT SUM(input_tokens) FROM ai_model_call_daily", Long.class))
                .isEqualTo(20);
    }

    @Test
    void streamingKeepsIdentityAcrossCallbacksAndChargesLateCompletion() throws Exception {
        var callback = new AtomicReference<StreamingChatResponseHandler>();
        var raw =
                new StreamingChatModel() {
                    public void doChat(ChatRequest r, StreamingChatResponseHandler h) {
                        callback.set(h);
                    }
                };
        var cancelled = new AtomicBoolean(false);
        var content = new StringBuilder();
        var terminal = new AtomicReference<Throwable>();
        try (var scope =
                AiInvocationScope.open(
                        fixture.context,
                        () -> {
                            if (cancelled.get()) throw new CancellationException();
                        })) {
            models.streaming(raw, config)
                    .chat(
                            "question",
                            new StreamingChatResponseHandler() {
                                public void onPartialResponse(String s) {
                                    content.append(s);
                                }

                                public void onCompleteResponse(ChatResponse r) {}

                                public void onError(Throwable t) {
                                    terminal.set(t);
                                }
                            });
        }
        callback.get().onPartialResponse("accepted");
        cancelled.set(true);
        var pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(
                            () -> {
                                callback.get().onPartialResponse("late");
                                callback.get().onCompleteResponse(response());
                            })
                    .get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(content).hasToString("accepted");
        assertThat(terminal.get()).isInstanceOf(CancellationException.class);
        assertThat(fixture.ledger.calls("000001", 7L, "req", 100)).hasSize(1);
        assertThat(
                        fixture.jdbc.queryForObject(
                                "SELECT SUM(known_cost) FROM ai_model_call_daily",
                                BigDecimal.class))
                .isEqualByComparingTo("0.000015");
    }
}

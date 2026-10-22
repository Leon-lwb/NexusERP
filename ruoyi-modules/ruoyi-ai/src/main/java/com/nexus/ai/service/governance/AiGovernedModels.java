package com.nexus.ai.service.governance;

import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.sse.AiChatSseLifecycle;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.governance.*;
import com.nexus.common.satoken.utils.LoginHelper;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.*;
import dev.langchain4j.model.chat.request.*;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.openai.*;
import dev.langchain4j.model.output.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/** 在实际SDK调用边界捕获模型快照与身份，不依赖SDK回调线程的登录ThreadLocal。 */
@Component
public class AiGovernedModels {
    @Autowired(required = false)
    private AiChatExecutionService executions;

    private final AiCallLedgerService ledger;
    private final AiGovernanceProperties properties;

    public AiGovernedModels(AiCallLedgerService ledger, AiGovernanceProperties properties) {
        this.ledger = ledger;
        this.properties = properties;
    }

    private record Call(String id, AiInvocationScope.Snapshot scope) {}

    private AiInvocationScope.Snapshot capture(String source) {
        var snapshot = AiInvocationScope.capture();
        if (snapshot != null) return snapshot;
        String tenant = AiRequestContext.getTenantId();
        Long user = AiRequestContext.getUserId();
        if (tenant == null || user == null) {
            tenant = LoginHelper.getTenantId();
            user = LoginHelper.getUserId();
        }
        return new AiInvocationScope.Snapshot(
                new AiCallContext(
                        tenant,
                        user,
                        AiRequestContext.getUserName(),
                        AiChatSseLifecycle.currentRequestId(),
                        AiRequestContext.getSessionId(),
                        null,
                        null,
                        null,
                        source),
                () -> {});
    }

    private AiPriceSnapshot price(AiModel c, Integer output, String source) {
        return new AiPriceSnapshot(
                c.getId(),
                c.getModelName(),
                c.getApiProtocol(),
                c.getCurrency(),
                c.getInputPricePerMillion(),
                "EMBEDDING".equals(source) ? BigDecimal.ZERO : c.getOutputPricePerMillion(),
                c.getContextWindow(),
                output);
    }

    private Call begin(AiModel config, ChatRequest request, String source) {
        var snapshot = capture(source);
        snapshot.checkActive();
        Integer output = request == null ? config.getMaxTokens() : request.maxOutputTokens();
        if (output == null) output = config.getMaxTokens();
        boolean bounded = false;
        if (request != null
                && properties.getBudgetVerifiedModelIds().contains(config.getId())
                && "OPENAI_CHAT_COMPLETIONS".equals(config.getApiProtocol())
                && config.getContextWindow() != null
                && output != null
                && output > 0
                && (request.toolSpecifications() == null
                        || request.toolSpecifications().isEmpty())) {
            // 只有部署方核实过的文本模型可进入严格额度；工具和多模态需独立认证，不猜测费用。
            boolean textOnly =
                    request.messages().stream()
                            .allMatch(
                                    m ->
                                            m instanceof SystemMessage
                                                    || (m instanceof AiMessage assistant
                                                            && !assistant.hasToolExecutionRequests()
                                                            && assistant.text() != null)
                                                    || (m instanceof UserMessage u
                                                            && u.contents().stream()
                                                                    .allMatch(
                                                                            c ->
                                                                                    c
                                                                                            instanceof
                                                                                            TextContent)));
            if (textOnly)
                try {
                    int input =
                            new OpenAiTokenCountEstimator(config.getModelName())
                                    .estimateTokenCountInMessages(request.messages());
                    bounded = input <= config.getContextWindow();
                } catch (RuntimeException ignored) {
                    bounded = false;
                }
        }
        return new Call(
                ledger.begin(snapshot.context(), price(config, output, source), bounded), snapshot);
    }

    private void finish(
            Call call, String status, TokenUsage usage, Throwable error, boolean embedding) {
        ledger.finish(
                call.scope.context().tenantId(),
                call.id,
                status,
                usage(usage, embedding),
                error == null
                        ? null
                        : error instanceof CancellationException ? "CANCELLED" : "MODEL_ERROR");
    }

    static AiCallUsage usage(TokenUsage usage, boolean embedding) {
        if (usage == null) return AiCallUsage.unknown();
        Long cached = null, reasoning = null;
        if (usage instanceof OpenAiTokenUsage open) {
            if (open.inputTokensDetails() != null)
                cached = number(open.inputTokensDetails().cachedTokens());
            if (open.outputTokensDetails() != null)
                reasoning = number(open.outputTokensDetails().reasoningTokens());
        }
        return new AiCallUsage(
                number(usage.inputTokenCount()),
                embedding ? 0L : number(usage.outputTokenCount()),
                cached,
                reasoning,
                "REPORTED");
    }

    private static Long number(Integer n) {
        return n == null ? null : n.longValue();
    }

    /** 同步Agent、普通模型、Flow和Rerank复用同一装饰器。 */
    public ChatModel chat(ChatModel raw, AiModel config) {
        return new ChatModel() {
            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return raw.defaultRequestParameters();
            }

            @Override
            public Set<Capability> supportedCapabilities() {
                return raw.supportedCapabilities();
            }

            @Override
            public ModelProvider provider() {
                return raw.provider();
            }

            @Override
            public ChatResponse doChat(ChatRequest request) {
                if (!properties.isEnabled()) return raw.chat(request);
                var call = begin(config, request, "MODEL");
                try (var scope = AiInvocationScope.open(call.scope)) {
                    ChatResponse response;
                    try {
                        response = raw.chat(request);
                    } catch (Throwable e) {
                        finish(call, "FAILED", null, e, false);
                        throw e;
                    }
                    finish(call, "SUCCEEDED", response.tokenUsage(), null, false);
                    if (response.tokenUsage() != null
                            && response.tokenUsage().inputTokenCount() != null
                            && response.tokenUsage().outputTokenCount() != null)
                        AiRequestContext.setLastTokenUsage(
                                response.tokenUsage().inputTokenCount(),
                                response.tokenUsage().outputTokenCount(),
                                config.getId());
                    call.scope.checkActive();
                    return response;
                }
            }
        };
    }

    /** SDK可能在任意线程回调，所有回调使用捕获的scope并过滤取消后的正文。 */
    public StreamingChatModel streaming(StreamingChatModel raw, AiModel config) {
        return new StreamingChatModel() {
            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return raw.defaultRequestParameters();
            }

            @Override
            public Set<Capability> supportedCapabilities() {
                return raw.supportedCapabilities();
            }

            @Override
            public ModelProvider provider() {
                return raw.provider();
            }

            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                if (!properties.isEnabled()) {
                    raw.chat(request, handler);
                    return;
                }
                var call = begin(config, request, "CHAT");
                var terminal = new AtomicBoolean();
                var wrapped =
                        new StreamingChatResponseHandler() {
                            private void progress(Runnable event, StreamingHandle handle) {
                                if (terminal.get()) return;
                                if (handle != null
                                        && executions != null
                                        && call.scope.context().requestId() != null)
                                    executions.transport(
                                            call.scope.context().tenantId(),
                                            call.scope.context().requestId(),
                                            call.id,
                                            handle::cancel);
                                try (var scope = AiInvocationScope.open(call.scope)) {
                                    try {
                                        call.scope.checkActive();
                                        event.run();
                                    } catch (CancellationException cancelled) {
                                        if (handle != null) handle.cancel();
                                    }
                                }
                            }

                            @Override
                            public void onPartialResponse(String text) {
                                progress(() -> handler.onPartialResponse(text), null);
                            }

                            @Override
                            public void onPartialResponse(
                                    PartialResponse response, PartialResponseContext context) {
                                progress(
                                        () -> handler.onPartialResponse(response, context),
                                        context.streamingHandle());
                            }

                            @Override
                            public void onPartialThinking(PartialThinking thinking) {
                                progress(() -> handler.onPartialThinking(thinking), null);
                            }

                            @Override
                            public void onPartialThinking(
                                    PartialThinking thinking, PartialThinkingContext context) {
                                progress(
                                        () -> handler.onPartialThinking(thinking, context),
                                        context.streamingHandle());
                            }

                            @Override
                            public void onPartialToolCall(PartialToolCall tool) {
                                progress(() -> handler.onPartialToolCall(tool), null);
                            }

                            @Override
                            public void onPartialToolCall(
                                    PartialToolCall tool, PartialToolCallContext context) {
                                progress(
                                        () -> handler.onPartialToolCall(tool, context),
                                        context.streamingHandle());
                            }

                            @Override
                            public void onCompleteToolCall(CompleteToolCall tool) {
                                progress(() -> handler.onCompleteToolCall(tool), null);
                            }

                            @Override
                            public void onUnmappedRawEvent(Object event) {
                                progress(() -> handler.onUnmappedRawEvent(event), null);
                            }

                            @Override
                            public void onCompleteResponse(ChatResponse response) {
                                if (!terminal.compareAndSet(false, true)) return;
                                if (executions != null && call.scope.context().requestId() != null)
                                    executions.untransport(
                                            call.scope.context().requestId(), call.id);
                                try (var scope = AiInvocationScope.open(call.scope)) {
                                    try {
                                        finish(
                                                call,
                                                "SUCCEEDED",
                                                response.tokenUsage(),
                                                null,
                                                false);
                                        call.scope.checkActive();
                                        handler.onCompleteResponse(response);
                                    } catch (Throwable e) {
                                        handler.onError(e);
                                    }
                                }
                            }

                            @Override
                            public void onError(Throwable error) {
                                if (!terminal.compareAndSet(false, true)) return;
                                try (var scope = AiInvocationScope.open(call.scope)) {
                                    try {
                                        finish(call, "FAILED", null, error, false);
                                    } catch (Throwable accounting) {
                                        error.addSuppressed(accounting);
                                    }
                                    handler.onError(error);
                                }
                            }
                        };
                try (var scope = AiInvocationScope.open(call.scope)) {
                    try {
                        raw.chat(request, wrapped);
                    } catch (Throwable e) {
                        wrapped.onError(e);
                    }
                }
            }
        };
    }

    /** 嵌入批次只按实际SDK调用计一次，不按向量条数伪造请求数。 */
    public EmbeddingModel embedding(EmbeddingModel raw, AiModel config) {
        return new EmbeddingModel() {
            private <T> T invoke(Supplier<T> action, Function<T, TokenUsage> usage) {
                if (!properties.isEnabled()) return action.get();
                var call = begin(config, null, "EMBEDDING");
                try (var scope = AiInvocationScope.open(call.scope)) {
                    T result;
                    try {
                        result = action.get();
                    } catch (Throwable e) {
                        finish(call, "FAILED", null, e, true);
                        throw e;
                    }
                    finish(call, "SUCCEEDED", usage.apply(result), null, true);
                    call.scope.checkActive();
                    return result;
                }
            }

            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                return invoke(() -> raw.embedAll(segments), Response::tokenUsage);
            }

            @Override
            public EmbeddingResponse doEmbed(EmbeddingRequest request) {
                return invoke(() -> raw.embed(request), EmbeddingResponse::tokenUsage);
            }

            @Override
            public int dimension() {
                return config.getEmbeddingDimension() != null
                        ? config.getEmbeddingDimension()
                        : EmbeddingModel.super.dimension();
            }

            @Override
            public String modelName() {
                return raw.modelName();
            }
        };
    }
}

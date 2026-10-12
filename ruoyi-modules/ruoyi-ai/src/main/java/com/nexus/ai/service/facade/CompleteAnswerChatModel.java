package com.nexus.ai.service.facade;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** 查询 Agent 保持同步工具编排，模型正文由真实流式回调推送；残缺回答不得正常完成。 */
final class CompleteAnswerChatModel implements ChatModel {
    private final ChatModel delegate;
    private final StreamingChatModel streamingDelegate;
    private final Consumer<String> content;
    private final Consumer<String> thinking;

    CompleteAnswerChatModel(ChatModel delegate) {
        this(delegate, null, ignored -> {}, ignored -> {});
    }

    CompleteAnswerChatModel(ChatModel delegate, StreamingChatModel streamingDelegate,
                            Consumer<String> content, Consumer<String> thinking) {
        this.delegate = Objects.requireNonNull(delegate);
        this.streamingDelegate = streamingDelegate;
        this.content = Objects.requireNonNull(content);
        this.thinking = Objects.requireNonNull(thinking);
    }

    @Override
    public ChatResponse chat(ChatRequest request, ChatRequestOptions options) {
        ChatResponse response = ChatModel.super.chat(request, options);
        if (response == null || response.aiMessage() == null) {
            throw new IllegalStateException("模型未返回有效回答，请重试。");
        }
        if (response.finishReason() == FinishReason.LENGTH) {
            throw new IllegalStateException("本次回答达到模型输出上限，结果不完整，未保存为完成答案。请分批继续查询。");
        }
        return response;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        return streamingDelegate == null ? delegate.chat(request) : awaitStreamingResponse(request);
    }

    private ChatResponse awaitStreamingResponse(ChatRequest request) {
        CompletableFuture<ChatResponse> completed = new CompletableFuture<>();
        AtomicBoolean accepting = new AtomicBoolean(true);
        try {
            // 外层 ChatModel.chat 已合并原 Agent 参数并在调用线程触发监听器。
            // 使用 doChat 避免在 SDK 的流式回调线程重复统计或依赖其租户 ThreadLocal。
            streamingDelegate.doChat(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    if (accepting.get() && token != null && !token.isEmpty()) content.accept(token);
                }

                @Override
                public void onPartialThinking(PartialThinking token) {
                    if (accepting.get() && token != null && token.text() != null) thinking.accept(token.text());
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    if (accepting.compareAndSet(true, false)) completed.complete(response);
                }

                @Override
                public void onError(Throwable error) {
                    if (accepting.compareAndSet(true, false)) completed.completeExceptionally(error);
                }
            });
            return completed.get(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("数据查询已中断，未保存为完成答案。", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("数据查询模型响应超时，未保存为完成答案。", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("数据查询模型返回失败，未保存为完成答案。", cause);
        } finally {
            accepting.set(false);
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        // 流式适配后的响应回到原执行线程，工具、租户和 Token 统计沿用原 Agent 上下文。
        return streamingDelegate == null ? List.of() : delegate.listeners();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }
}

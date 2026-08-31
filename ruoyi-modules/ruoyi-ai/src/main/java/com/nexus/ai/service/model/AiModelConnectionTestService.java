package com.nexus.ai.service.model;

import com.nexus.ai.domain.dto.AiModelTestRequest;
import com.nexus.ai.domain.dto.AiModelTestResult;
import com.nexus.ai.service.chat.provider.ChatServiceProviderFactory;
import com.nexus.ai.service.rag.embedding.EmbeddingProviderFactory;
import com.nexus.ai.service.rag.rerank.RerankProvider;
import com.nexus.ai.service.rag.rerank.RerankProviderFactory;
import com.nexus.ai.service.rag.rerank.RerankResult;
import com.nexus.common.ai.domain.bo.AiModelBo;
import com.nexus.common.ai.domain.dto.image.ImageContext;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.domain.entity.AiProvider;
import com.nexus.common.ai.mapper.AiProviderMapper;
import com.nexus.common.ai.service.AiApiKeyDecryptor;
import com.nexus.common.ai.service.image.IImageGenerationService;
import com.nexus.common.ai.service.image.ImageServiceFactory;
import com.nexus.common.core.exception.ServiceException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * AI 模型连接测试服务（草稿测试，无需先保存模型）
 * <p>对未落库的模型草稿做一次最小计费的真实上游调用，验证厂商配置（apiHost/apiKey/协议）可用。
 * 结构分两层：</p>
 * <ul>
 *   <li><b>公共入口 {@link #test(AiModelTestRequest)}</b>：DB 边界——按 providerId 加载租户内厂商
 *       （{@code AiProviderMapper.selectById}，与 {@code AiModelServiceImpl.fillProviderInfo} 同一
 *       租户拦截路径），用共享的 {@link AiApiKeyDecryptor} 解密 Key，跑
 *       {@link AiModelDraftValidator#resolveAndValidate} 协议/能力策略校验后进入分发</li>
 *   <li><b>内部入口 {@link #testWithDraft(AiModel, boolean)}</b>：纯分发无 DB，
 *       按模型类型走 Chat/Embedding/Rerank/Image 四个<b>可注入操作接口</b>——
 *       单元测试用桩替换实现（无网络无 Spring），生产构造器用真实协议工厂装配默认实现</li>
 * </ul>
 * <p>安全约定：错误信息脱敏（剥离 API Key 与 Bearer 令牌、截断 300 字符）；
 * 日志只记模型名/协议/耗时/结论，绝不记 Key、Authorization 头、上游完整响应与测试图片；
 * 测试通过不落库、不自动保存模型。AUDIO 方向暂无已注册适配器，返回明确的不支持结果（不抛异常）。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
@Slf4j
@Service
public class AiModelConnectionTestService {

    /** 对话连接测试的输出 Token 封顶（只为验证连通，最小化计费） */
    static final int TEST_MAX_TOKENS = 16;

    /** 单次上游调用的最长等待（秒）：连接测试要快速反馈，不等提供商默认的 180s 读超时 */
    static final long TEST_TIMEOUT_SECONDS = 25;

    /** 失败信息最大长度（脱敏后截断） */
    static final int MAX_ERROR_LENGTH = 300;

    /** 向量连接测试的固定文本 */
    static final String EMBEDDING_TEST_TEXT = "Nexus connectivity check";

    /**
     * 视觉连接测试内联的 32x32 纯色 PNG（最小计费的图片输入）。
     * <p>上游会校验图片字节本身：base64 必须解码为结构合法的 PNG（块 CRC/zlib 完整），
     * 否则报 "unsupported image"；单元测试 {@code visionTestImageIsStructurallyValidPng} 防回归。
     * 不用 1x1 是为避开个别厂商的最小尺寸校验。</p>
     */
    static final String VISION_TEST_IMAGE_DATA_URI =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAJ0lEQVR42u3NsQkAAAjAsP7/tF7h"
            + "IASyp6lTCQQCgUAgEAgEgi/BAjLD/C5w/SM9AAAAAElFTkSuQmCC";

    /** 厂商 Mapper（租户拦截器自动附加 tenant_id 条件） */
    private final AiProviderMapper aiProviderMapper;
    /** Jasypt 加密器（解密厂商 API Key，仅内存使用） */
    private final StringEncryptor stringEncryptor;
    /** 草稿校验器（协议/能力策略，公共入口使用） */
    private final AiModelDraftValidator draftValidator;
    /** 厂商协议策略注册表（结果回显协议、默认协议推断） */
    private final AiProviderPolicyRegistry policyRegistry;
    /** 对话操作（TEXT/VISION） */
    private final ChatTestOperation chatOperation;
    /** 向量操作（EMBEDDING） */
    private final EmbeddingTestOperation embeddingOperation;
    /** 重排操作（RERANK） */
    private final RerankTestOperation rerankOperation;
    /** 图片生成操作（IMAGE，计费需二次确认） */
    private final ImageTestOperation imageOperation;
    /** 连接测试专用线程池（与 aiExecutor 隔离，见 AiThreadPoolConfig#aiConnectionTestExecutor） */
    private final Executor testExecutor;
    /**
     * 上游调用等待上限（毫秒）。
     * <p>非 final 仅因单元测试需调短以验证超时分支（生产恒为 {@link #TEST_TIMEOUT_SECONDS} 秒）。</p>
     */
    long timeoutMillis = TEST_TIMEOUT_SECONDS * 1000;

    /**
     * Spring 装配构造器：注入协议策略注册表 Bean，用真实协议工厂装配四个默认操作实现，
     * 走连接测试专用线程池（与 aiExecutor 隔离，防止慢上游占满生产线程）
     * <p>类中并存包级可见的测试构造器，多构造器场景需 @Autowired 显式指定装配入口。</p>
     */
    @Autowired
    public AiModelConnectionTestService(AiProviderMapper aiProviderMapper,
                                        StringEncryptor stringEncryptor,
                                        AiProviderPolicyRegistry policyRegistry,
                                        ChatServiceProviderFactory chatProviderFactory,
                                        EmbeddingProviderFactory embeddingProviderFactory,
                                        RerankProviderFactory rerankProviderFactory,
                                        ImageServiceFactory imageServiceFactory,
                                        @Qualifier("aiConnectionTestExecutor") Executor executor) {
        this(aiProviderMapper, stringEncryptor,
            new AiModelDraftValidator(policyRegistry), policyRegistry,
            defaultChatOperation(chatProviderFactory),
            defaultEmbeddingOperation(embeddingProviderFactory),
            defaultRerankOperation(rerankProviderFactory),
            defaultImageOperation(imageServiceFactory),
            executor);
    }

    /**
     * 测试构造器（包级可见）：直接注入操作桩与执行器，绕过 DB 与网络
     */
    AiModelConnectionTestService(AiProviderMapper aiProviderMapper,
                                 StringEncryptor stringEncryptor,
                                 AiModelDraftValidator draftValidator,
                                 AiProviderPolicyRegistry policyRegistry,
                                 ChatTestOperation chatOperation,
                                 EmbeddingTestOperation embeddingOperation,
                                 RerankTestOperation rerankOperation,
                                 ImageTestOperation imageOperation,
                                 Executor testExecutor) {
        this.aiProviderMapper = aiProviderMapper;
        this.stringEncryptor = stringEncryptor;
        this.draftValidator = draftValidator;
        this.policyRegistry = policyRegistry;
        this.chatOperation = chatOperation;
        this.embeddingOperation = embeddingOperation;
        this.rerankOperation = rerankOperation;
        this.imageOperation = imageOperation;
        this.testExecutor = testExecutor;
    }

    /**
     * 对模型草稿执行连接测试（公共入口：厂商加载 + Key 解密 + 策略校验 + 分发）
     *
     * @param request 测试请求（不含 API Key 字段）
     * @return 测试结果（失败信息已脱敏）
     * @throws ServiceException 厂商不存在 / 草稿违反协议策略（中文提示，走全局异常处理）
     */
    public AiModelTestResult test(AiModelTestRequest request) {
        AiProvider provider = aiProviderMapper.selectById(request.getProviderId());
        if (provider == null) {
            throw new ServiceException("厂商不存在或无权访问: " + request.getProviderId());
        }
        AiModelBo draftBo = toDraftBo(request);
        // 协议/能力策略校验（会补全草稿默认协议与 EMBEDDING/RERANK 锁定能力）
        String protocol = draftValidator.resolveAndValidate(draftBo, provider.getProviderType());

        AiModel draft = new AiModel();
        draft.setModelName(request.getModelName());
        draft.setDisplayName(request.getDisplayName());
        draft.setModelType(request.getModelType());
        draft.setContextWindow(request.getContextWindow());
        draft.setMaxTokens(request.getMaxTokens());
        draft.setTemperature(request.getTemperature());
        draft.setEmbeddingDimension(request.getEmbeddingDimension());
        draft.setInputCapabilities(draftBo.getInputCapabilities());
        draft.setOutputCapabilities(draftBo.getOutputCapabilities());
        draft.setApiProtocol(protocol);
        // 厂商瞬态字段：与 AiModelServiceImpl.fillProviderInfo 同一解密路径
        draft.setApiHost(provider.getApiHost());
        draft.setApiKey(AiApiKeyDecryptor.decrypt(stringEncryptor, provider.getApiKey()));
        draft.setProviderType(provider.getProviderType());
        return testWithDraft(draft, request.isConfirmBillableImageTest());
    }

    /**
     * 对已填充厂商信息的草稿执行连接测试（无二次确认，图片测试将被拒绝）
     */
    AiModelTestResult testWithDraft(AiModel draft) {
        return testWithDraft(draft, false);
    }

    /**
     * 分发执行连接测试（纯逻辑无 DB；包级可见供单元测试注入操作桩直测）
     *
     * @param draft                    模型草稿（apiHost/apiKey/providerType 瞬态字段已填充）
     * @param confirmBillableImageTest 图片生成测试的计费二次确认
     * @return 测试结果（上游异常一律转为脱敏的失败结果，不向调用方透传原文）
     * @throws ServiceException 图片测试未二次确认（计费防护；用户提示需直达前端，
     *                          IllegalArgumentException 会被全局处理器吞成"发生未知异常"）
     */
    AiModelTestResult testWithDraft(AiModel draft, boolean confirmBillableImageTest) {
        String modelType = draft.getModelType() == null ? "" : draft.getModelType().trim().toUpperCase(Locale.ROOT);
        // 计费护栏在 try 之外：未确认的图片测试直接拒绝，属于调用方错误而非上游失败
        if ("IMAGE".equals(modelType) && !confirmBillableImageTest) {
            throw new ServiceException(
                "图片生成测试会产生实际计费，请勾选“我已知晓将产生计费”后重试");
        }
        long startNanos = System.nanoTime();
        String protocol = echoProtocol(draft);
        try {
            AiModelTestResult result = switch (modelType) {
                case "TEXT" -> chatTest(draft, protocol, startNanos,
                    List.of(UserMessage.from("请只回复四个字：连接正常")));
                case "VISION" -> chatTest(draft, protocol, startNanos, List.of(UserMessage.from(
                    // 显式构造 TextContent + ImageContent（UserMessage.from(String, Content...) 的
                    // 首参是消息 name 而非文本内容，不能依赖它携带提示词）
                    TextContent.from("请描述这张图片的颜色，限十个字以内"),
                    ImageContent.from(VISION_TEST_IMAGE_DATA_URI))));
                case "AUDIO" -> AiModelTestResult.failure(draft.getProviderType(), protocol,
                    elapsedMs(startNanos), "暂不支持该音频组合的连接测试", null);
                case "EMBEDDING" -> embeddingTest(draft, protocol, startNanos);
                case "RERANK" -> rerankTest(draft, protocol, startNanos);
                case "IMAGE" -> imageTest(draft, protocol, startNanos);
                default -> AiModelTestResult.failure(draft.getProviderType(), protocol,
                    elapsedMs(startNanos), "暂不支持该模型类型的连接测试: " + modelType, null);
            };
            log.info("[模型连接测试] model={}, type={}, protocol={}, latencyMs={}, success={}",
                draft.getModelName(), modelType, protocol, result.latencyMs(), result.success());
            return result;
        } catch (Exception e) {
            long latency = elapsedMs(startNanos);
            log.warn("[模型连接测试] model={}, type={}, protocol={}, latencyMs={}, 失败: {}",
                draft.getModelName(), modelType, protocol, latency, e.getClass().getSimpleName());
            return AiModelTestResult.failure(draft.getProviderType(), protocol, latency,
                sanitize(e.getMessage(), draft.getApiKey()), null);
        }
    }

    /**
     * TEXT/VISION：一次极短的非流式对话（输出封顶 {@link #TEST_MAX_TOKENS} Token）
     */
    private AiModelTestResult chatTest(AiModel draft, String protocol, long startNanos,
                                       List<ChatMessage> messages) {
        // 测试请求输出封顶 16 token（上下文窗口更小时取窗口值），最小化计费
        int capped = TEST_MAX_TOKENS;
        if (draft.getContextWindow() != null && draft.getContextWindow() < capped) {
            capped = draft.getContextWindow();
        }
        draft.setMaxTokens(capped);
        callWithTimeout(() -> {
            chatOperation.chat(draft, messages);
            return null;
        }, "对话连接测试");
        return AiModelTestResult.success(draft.getProviderType(), protocol,
            elapsedMs(startNanos), null);
    }

    /**
     * EMBEDDING：固定文本向量化，实测维度与声明维度比对
     */
    private AiModelTestResult embeddingTest(AiModel draft, String protocol, long startNanos) {
        Embedding embedding = callWithTimeout(
            () -> embeddingOperation.embed(draft, EMBEDDING_TEST_TEXT), "向量连接测试");
        Integer detected = embedding == null ? null : embedding.dimension();
        if (detected == null || detected <= 0) {
            return AiModelTestResult.failure(draft.getProviderType(), protocol,
                elapsedMs(startNanos), "上游未返回有效向量", null);
        }
        Integer declared = draft.getEmbeddingDimension();
        if (declared != null && declared > 0 && !declared.equals(detected)) {
            return AiModelTestResult.failure(draft.getProviderType(), protocol, elapsedMs(startNanos),
                "向量维度不匹配：实际检测 " + detected + " 维，模型配置声明 " + declared
                    + " 维，请修正向量维度后重试", detected);
        }
        return AiModelTestResult.success(draft.getProviderType(), protocol,
            elapsedMs(startNanos), detected);
    }

    /**
     * RERANK：固定一查两候选走真实重排链路
     */
    private AiModelTestResult rerankTest(AiModel draft, String protocol, long startNanos) {
        callWithTimeout(() -> rerankOperation.rerank(draft, "Nexus 连接测试查询",
            List.of("连接测试候选文档一：学校简介", "连接测试候选文档二：课程安排"), 2), "重排连接测试");
        return AiModelTestResult.success(draft.getProviderType(), protocol,
            elapsedMs(startNanos), null);
    }

    /**
     * IMAGE：调用图片厂商连接测试（计费已二次确认），产物全部丢弃
     */
    private AiModelTestResult imageTest(AiModel draft, String protocol, long startNanos) {
        callWithTimeout(() -> {
            imageOperation.testImage(draft);
            return null;
        }, "图片生成连接测试");
        return AiModelTestResult.success(draft.getProviderType(), protocol,
            elapsedMs(startNanos), null);
    }

    /**
     * 结果回显协议：草稿已填 → 规范化回显；未填 → 厂商默认协议
     */
    private String echoProtocol(AiModel draft) {
        String raw = draft.getApiProtocol();
        if (raw != null && !raw.isBlank()) {
            return raw.trim().toUpperCase(Locale.ROOT);
        }
        return policyRegistry.isRegistered(draft.getProviderType())
            ? policyRegistry.defaultProtocol(draft.getProviderType()) : "";
    }

    /**
     * 请求 → 草稿 BO（校验注解已保证必填项，此处仅搬运）
     */
    private AiModelBo toDraftBo(AiModelTestRequest request) {
        AiModelBo bo = new AiModelBo();
        bo.setProviderId(request.getProviderId());
        bo.setModelName(request.getModelName());
        bo.setDisplayName(request.getDisplayName());
        bo.setModelType(request.getModelType());
        bo.setInputCapabilities(request.getInputCapabilities());
        bo.setOutputCapabilities(request.getOutputCapabilities());
        bo.setApiProtocol(request.getApiProtocol());
        bo.setContextWindow(request.getContextWindow());
        bo.setMaxTokens(request.getMaxTokens());
        bo.setTemperature(request.getTemperature());
        bo.setEmbeddingDimension(request.getEmbeddingDimension());
        return bo;
    }

    /**
     * 错误信息脱敏：剥离 API Key 与 Bearer 令牌，截断至 {@link #MAX_ERROR_LENGTH} 字符
     * <p>公开静态供图片厂商实现（如 TongYiWanxImageService）的日志复用同一脱敏纪律。</p>
     *
     * @param message 上游/框架异常信息（可能含敏感凭据）
     * @param apiKey  本次测试使用的厂商 Key（全文替换为 ***）
     * @return 脱敏后的安全文案
     */
    public static String sanitize(String message, String apiKey) {
        if (message == null || message.isBlank()) {
            return "连接测试失败（上游未返回错误信息）";
        }
        String cleaned = message;
        if (apiKey != null && !apiKey.isBlank()) {
            cleaned = cleaned.replace(apiKey, "***");
        }
        cleaned = cleaned.replaceAll("(?i)bearer\\s+[^\\s,;\\\"']+", "Bearer ***");
        if (cleaned.length() > MAX_ERROR_LENGTH) {
            cleaned = cleaned.substring(0, MAX_ERROR_LENGTH);
        }
        return cleaned;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 上游调用超时护栏：在<b>连接测试专用线程池</b>上执行并限时等待
     * <p>与 {@code aiExecutor} 隔离（见 {@code AiThreadPoolConfig#aiConnectionTestExecutor}）：
     * {@code cancel(true)} 无法中断底层 HTTP 调用，线程会占用到上游自身读超时，
     * 复用生产线程池会让慢上游挤占对话/Agent 流量。超时后取消等待转失败结果
     * （底层 HTTP 由其自身读超时兜底回收）；线程池耗尽被拒绝时转"繁忙"失败。</p>
     *
     * @param call   上游调用
     * @param action 动作描述（超时提示前缀，如"对话连接测试"）
     * @return 上游返回值
     * @throws IllegalStateException 超时 / 被拒绝 / 被中断（由分发层 catch 转失败结果）
     */
    private <T> T callWithTimeout(Supplier<T> call, String action) {
        CompletableFuture<T> future;
        try {
            future = CompletableFuture.supplyAsync(call, testExecutor);
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("连接测试繁忙，请稍后再试");
        }
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException(action + "超时（超过 " + TEST_TIMEOUT_SECONDS + " 秒），请检查 API 地址可达性");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause.getMessage() != null ? cause.getMessage() : cause.toString(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(action + "被中断");
        }
    }

    // ------------------------------------------------------------------
    // 可注入操作接口（测试桩替换点）与 Spring 默认实现
    // ------------------------------------------------------------------

    /**
     * 对话连接测试操作（TEXT/VISION 共用；经协议工厂构建 ChatModel 走真实运行时链路）
     */
    public interface ChatTestOperation {
        /**
         * @param draft    模型草稿（maxTokens 已封顶，apiHost/apiKey/providerType/apiProtocol 已填充）
         * @param messages 测试消息（VISION 含内联 32x32 纯色 PNG）
         */
        void chat(AiModel draft, List<ChatMessage> messages);
    }

    /** 向量连接测试操作 */
    public interface EmbeddingTestOperation {
        /**
         * @param draft 模型草稿
         * @param text  固定测试文本
         * @return 上游返回的向量
         */
        Embedding embed(AiModel draft, String text);
    }

    /** 重排连接测试操作 */
    public interface RerankTestOperation {
        /**
         * @param draft      模型草稿
         * @param query      固定查询
         * @param candidates 固定候选（两条）
         * @param topN       返回条数
         * @return 重排结果
         */
        List<RerankResult> rerank(AiModel draft, String query, List<String> candidates, int topN);
    }

    /** 图片生成连接测试操作（计费） */
    public interface ImageTestOperation {
        /**
         * @param draft 模型草稿（图片厂商连接测试，产物丢弃）
         */
        void testImage(AiModel draft);
    }

    /** 默认对话操作：协议工厂路由 + 同步 ChatModel（与运行时同一批适配器；超时由分发层统一护栏） */
    private static ChatTestOperation defaultChatOperation(ChatServiceProviderFactory factory) {
        return (draft, messages) -> {
            double temperature = draft.getTemperature() != null ? draft.getTemperature().doubleValue() : 0.1;
            ChatModel chatModel = factory.get(draft).buildChatModel(draft, temperature);
            chatModel.chat(messages);
        };
    }

    /** 默认向量操作：Embedding 工厂构建（含监听器链路） */
    private static EmbeddingTestOperation defaultEmbeddingOperation(EmbeddingProviderFactory factory) {
        return (draft, text) -> {
            EmbeddingModel embeddingModel = factory.build(draft);
            return embeddingModel.embed(text).content();
        };
    }

    /** 默认重排操作：按厂商类型路由专用重排实现 */
    private static RerankTestOperation defaultRerankOperation(RerankProviderFactory factory) {
        return (draft, query, candidates, topN) -> {
            RerankProvider provider = factory.get(draft.getProviderType());
            if (provider == null) {
                throw new IllegalStateException("厂商 " + draft.getProviderType() + " 暂无重排实现，无法执行连接测试");
            }
            return provider.rerank(query, candidates, topN, draft);
        };
    }

    /** 默认图片操作：按厂商类型经文生图工厂路由（DASHSCOPE→通义万相等别名映射统一在工厂内）并调其连接测试 */
    private static ImageTestOperation defaultImageOperation(ImageServiceFactory factory) {
        return draft -> {
            String providerType = draft.getProviderType();
            IImageGenerationService imageService = factory.get(providerType);
            if (imageService == null) {
                throw new IllegalStateException("暂不支持厂商 " + providerType + " 的图片生成连接测试");
            }
            imageService.testConnection(ImageContext.builder()
                .modelConfig(draft)
                .prompt("连接测试")
                .size("512*512")
                .build());
        };
    }
}

package com.nexus.ai.service.model;

import com.nexus.ai.domain.dto.AiModelTestResult;
import com.nexus.ai.service.rag.rerank.RerankResult;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.core.exception.ServiceException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型连接测试服务单元测试（纯 Java 构造服务，操作桩注入，无网络无 Spring 上下文）
 * <p>测试走 {@code testWithDraft} 内部入口绕过 DB（厂商加载/解密在公共入口 {@code test(request)}，
 * 属于集成边界）；各模态上游调用经四个操作接口（Chat/Embedding/Rerank/Image）注入桩替换。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AiModelConnectionTestServiceTest {

    private static final String API_KEY = "sk-test-secret-key-123456";

    // ------------------------------------------------------------------
    // 计划内三条测试
    // ------------------------------------------------------------------

    @Test
    void embeddingTestReturnsDetectedDimension() {
        AiModelConnectionTestService service = serviceWith(new FakeEmbeddingOperation(1024));
        AiModelTestResult result = service.testWithDraft(draft("EMBEDDING", "TEXT", "VECTOR", 1024));
        assertTrue(result.success());
        assertEquals(1024, result.detectedEmbeddingDimension());
    }

    @Test
    void embeddingTestRejectsDeclaredDimensionMismatch() {
        AiModelConnectionTestService service = serviceWith(new FakeEmbeddingOperation(1024));
        AiModelTestResult result = service.testWithDraft(draft("EMBEDDING", "TEXT", "VECTOR", 1536));
        assertFalse(result.success());
        assertTrue(result.message().contains("向量维度"));
        assertTrue(result.message().contains("1024"));
        assertTrue(result.message().contains("1536"));
        // 失败结果仍带回检测出的维度，便于前端提示修正
        assertEquals(1024, result.detectedEmbeddingDimension());
    }

    @Test
    void imageTestRequiresExplicitBillableConfirmation() {
        AiModelConnectionTestService service = serviceWith(new FakeImageOperation());
        // 复审修正：ServiceException 才能经全局处理器把中文提示直达用户
        // （IllegalArgumentException 会被吞成"发生未知异常"）
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.testWithDraft(draft("IMAGE", "TEXT", "IMAGE", null)));
        assertTrue(e.getMessage().contains("计费"), "异常应说明计费原因: " + e.getMessage());
    }

    // ------------------------------------------------------------------
    // 补充测试：TEXT 成功路径 / 清洗 / AUDIO 不支持 / RERANK / VISION / maxTokens 封顶
    // ------------------------------------------------------------------

    @Test
    void textTestSuccessReturnsLatencyAndProtocolEcho() {
        FakeChatOperation chat = new FakeChatOperation(null);
        AiModelConnectionTestService service = serviceWith(chat);
        AiModelTestResult result = service.testWithDraft(draft("TEXT", "TEXT", "TEXT", null));
        assertTrue(result.success(), "成功结果: " + result.message());
        assertTrue(result.latencyMs() >= 0);
        // OPENCODE_GO 未填协议 → 回显厂商默认协议 OPENAI_CHAT_COMPLETIONS
        assertEquals("OPENAI_CHAT_COMPLETIONS", result.apiProtocol());
        assertEquals("OPENCODE_GO", result.providerType());
        assertNotNull(chat.messagesSeen);
    }

    @Test
    void textTestCapsMaxTokensAt16() {
        FakeChatOperation chat = new FakeChatOperation(null);
        AiModelConnectionTestService service = serviceWith(chat);
        AiModel draft = draft("TEXT", "TEXT", "TEXT", null);
        draft.setMaxTokens(4096);
        service.testWithDraft(draft);
        assertEquals(16, chat.draftSeen.getMaxTokens(), "连接测试的对话请求应把输出封顶在 16 token");
    }

    @Test
    void visionTestSendsTextAndBuiltInImage() {
        FakeChatOperation chat = new FakeChatOperation(null);
        AiModelConnectionTestService service = serviceWith(chat);
        AiModelTestResult result = service.testWithDraft(draft("VISION", "TEXT,IMAGE", "TEXT", null));
        assertTrue(result.success(), "成功结果: " + result.message());
        assertNotNull(chat.messagesSeen);
        assertEquals(1, chat.messagesSeen.size());
        UserMessage userMessage = (UserMessage) chat.messagesSeen.get(0);
        List<dev.langchain4j.data.message.Content> contents = userMessage.contents();
        assertTrue(contents.stream().anyMatch(TextContent.class::isInstance),
            "视觉连接测试消息应包含文本内容");
        assertTrue(contents.stream().anyMatch(ImageContent.class::isInstance),
            "视觉连接测试应内联内置 PNG 图片内容");
    }

    @Test
    void visionTestImageIsStructurallyValidPng() throws DataFormatException {
        // 回归防护：内联常量曾被写坏（IDAT 块 CRC / zlib adler32 错误），
        // 上游校验图片字节直接判 "unsupported image"，此处逐块校验兜底
        String dataUri = AiModelConnectionTestService.VISION_TEST_IMAGE_DATA_URI;
        byte[] png = Base64.getDecoder().decode(
            dataUri.substring(dataUri.indexOf(',') + 1));
        assertArrayEquals(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
            Arrays.copyOfRange(png, 0, 8), "应以 PNG 签名开头");

        byte[] idat = null;
        int width = 0;
        int height = 0;
        int bitDepth = 0;
        int colorType = 0;
        ByteBuffer buf = ByteBuffer.wrap(png).position(8);
        while (buf.hasRemaining()) {
            int length = buf.getInt();
            byte[] type = new byte[4];
            buf.get(type);
            byte[] body = new byte[length];
            buf.get(body);
            long storedCrc = buf.getInt() & 0xFFFFFFFFL;
            CRC32 crc32 = new CRC32();
            crc32.update(type);
            crc32.update(body);
            assertEquals(storedCrc, crc32.getValue(),
                "PNG 块 " + new String(type, StandardCharsets.US_ASCII) + " CRC 应完整");
            if (Arrays.equals(type, "IHDR".getBytes(StandardCharsets.US_ASCII))) {
                width = (body[0] & 0xFF) << 24 | (body[1] & 0xFF) << 16 | (body[2] & 0xFF) << 8 | (body[3] & 0xFF);
                height = (body[4] & 0xFF) << 24 | (body[5] & 0xFF) << 16 | (body[6] & 0xFF) << 8 | (body[7] & 0xFF);
                bitDepth = body[8] & 0xFF;
                colorType = body[9] & 0xFF;
            } else if (Arrays.equals(type, "IDAT".getBytes(StandardCharsets.US_ASCII))) {
                idat = body;
            }
        }
        assertNotNull(idat, "应存在 IDAT 块");
        // zlib 完整解压（含 adler32 校验），并按 IHDR 尺寸断言像素数据齐全（字节深 8 假设）
        Inflater inflater = new Inflater();
        inflater.setInput(idat);
        byte[] raw = new byte[height * (1 + width * channelsOf(colorType))];
        assertEquals(raw.length, inflater.inflate(raw), "IDAT 应解压出完整像素数据");
        assertTrue(inflater.finished(), "zlib 流应完整消费（adler32 校验通过）");
        inflater.end();
        assertEquals(8, bitDepth, "连接测试图片应使用 8 位深");
    }

    /** PNG 颜色类型 → 每像素字节数（0 灰度 / 2 RGB / 3 调色 / 4 灰度+A / 6 RGBA） */
    private static int channelsOf(int colorType) {
        return switch (colorType) {
            case 0, 3 -> 1;
            case 4 -> 2;
            case 2 -> 3;
            case 6 -> 4;
            default -> throw new IllegalArgumentException("不支持的颜色类型: " + colorType);
        };
    }

    @Test
    void chatFailureIsSanitizedAndTruncated() {
        String longJunk = "x".repeat(600);
        RuntimeException upstream = new RuntimeException(
            "401 Unauthorized: Authorization Bearer " + API_KEY + " invalid, key=" + API_KEY + longJunk);
        AiModelConnectionTestService service = serviceWith(new FakeChatOperation(upstream));
        AiModel draft = draft("TEXT", "TEXT", "TEXT", null);
        AiModelTestResult result = service.testWithDraft(draft);
        assertFalse(result.success());
        String message = result.message();
        assertFalse(message.contains(API_KEY), "错误信息不得泄露 API Key: " + message);
        assertFalse(message.contains("Bearer " + API_KEY), "错误信息不得泄露 Bearer 令牌: " + message);
        assertTrue(message.contains("Bearer ***"), "Bearer 令牌应被脱敏占位替换: " + message);
        assertTrue(message.length() <= 300, "错误信息应截断至 300 字符，实际: " + message.length());
    }

    @Test
    void audioUnsupportedReturnsFailureInsteadOfThrowing() {
        AiModelConnectionTestService service = serviceWith(new FakeChatOperation(null));
        AiModelTestResult result = service.testWithDraft(draft("AUDIO", "TEXT", "AUDIO", null));
        assertFalse(result.success());
        assertTrue(result.message().contains("暂不支持"), "应返回明确的不支持提示: " + result.message());
    }

    @Test
    void rerankTestUsesFixedQueryAndTwoCandidates() {
        FakeRerankOperation rerank = new FakeRerankOperation();
        AiModelConnectionTestService service = serviceWith(rerank);
        AiModelTestResult result = service.testWithDraft(draft("RERANK", "TEXT", "RANKING", null));
        assertTrue(result.success(), "成功结果: " + result.message());
        assertNotNull(rerank.querySeen);
        assertEquals(2, rerank.candidatesSeen.size(), "重排连接测试应固定一查两候选");
        assertEquals(2, rerank.topNSeen);
    }

    @Test
    void imageTestWithConfirmationInvokesImageAdapter() {
        FakeImageOperation image = new FakeImageOperation();
        AiModelConnectionTestService service = serviceWith(image);
        AiModelTestResult result =
            service.testWithDraft(draft("IMAGE", "TEXT", "IMAGE", null), true);
        assertTrue(result.success(), "成功结果: " + result.message());
        assertTrue(image.invoked, "确认计费后应实际调用图片厂商连接测试");
    }

    @Test
    void unknownModelTypeReturnsFailure() {
        AiModelConnectionTestService service = serviceWith(new FakeChatOperation(null));
        AiModelTestResult result = service.testWithDraft(draft("HOLOGRAM", "TEXT", "TEXT", null));
        assertFalse(result.success());
        assertTrue(result.message().contains("暂不支持"));
    }

    @Test
    void slowUpstreamReturnsTimeoutFailureResult() {
        // 真实异步执行器 + 沉睡操作：50ms 等待上限对 300ms 调用必超时
        ExecutorService single = Executors.newSingleThreadExecutor();
        try {
            AiModelConnectionTestService.ChatTestOperation sleepy = (draft, messages) -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            AiModelConnectionTestService service = serviceWith(single, sleepy);
            service.timeoutMillis = 50;
            AiModelTestResult result = service.testWithDraft(draft("TEXT", "TEXT", "TEXT", null));
            assertFalse(result.success());
            assertTrue(result.message().contains("超时"), "应提示超时: " + result.message());
        } finally {
            single.shutdownNow();
        }
    }

    @Test
    void rejectedExecutionReturnsBusyFailureResult() {
        // 线程池耗尽（AbortPolicy 拒绝）应转"繁忙"失败结果，而非向调用方抛异常
        Executor rejecting = task -> {
            throw new RejectedExecutionException("pool exhausted");
        };
        AiModelConnectionTestService service = serviceWith(rejecting, new FakeChatOperation(null));
        AiModelTestResult result = service.testWithDraft(draft("TEXT", "TEXT", "TEXT", null));
        assertFalse(result.success());
        assertTrue(result.message().contains("繁忙"), "应提示繁忙: " + result.message());
    }

    // ------------------------------------------------------------------
    // 构造辅助：操作桩注入（默认直排执行器同步执行，测试确定无并发）
    // ------------------------------------------------------------------

    private AiModelConnectionTestService serviceWith(AiModelConnectionTestService.EmbeddingTestOperation embedding) {
        return serviceWith(new FakeChatOperation(null), embedding, new FakeRerankOperation(), new FakeImageOperation());
    }

    private AiModelConnectionTestService serviceWith(AiModelConnectionTestService.ImageTestOperation image) {
        return serviceWith(new FakeChatOperation(null), new FakeEmbeddingOperation(8), new FakeRerankOperation(), image);
    }

    private AiModelConnectionTestService serviceWith(AiModelConnectionTestService.RerankTestOperation rerank) {
        return serviceWith(new FakeChatOperation(null), new FakeEmbeddingOperation(8), rerank, new FakeImageOperation());
    }

    private AiModelConnectionTestService serviceWith(AiModelConnectionTestService.ChatTestOperation chat) {
        return serviceWith(Runnable::run, chat);
    }

    private AiModelConnectionTestService serviceWith(Executor executor,
                                                     AiModelConnectionTestService.ChatTestOperation chat) {
        return new AiModelConnectionTestService(null, null,
            new AiModelDraftValidator(new AiProviderPolicyRegistry()),
            new AiProviderPolicyRegistry(), chat, new FakeEmbeddingOperation(8),
            new FakeRerankOperation(), new FakeImageOperation(), executor);
    }

    private AiModelConnectionTestService serviceWith(AiModelConnectionTestService.ChatTestOperation chat,
                                                     AiModelConnectionTestService.EmbeddingTestOperation embedding,
                                                     AiModelConnectionTestService.RerankTestOperation rerank,
                                                     AiModelConnectionTestService.ImageTestOperation image) {
        return new AiModelConnectionTestService(null, null,
            new AiModelDraftValidator(new AiProviderPolicyRegistry()),
            new AiProviderPolicyRegistry(), chat, embedding, rerank, image, Runnable::run);
    }

    private AiModel draft(String modelType, String inputCapabilities, String outputCapabilities,
                          Integer embeddingDimension) {
        AiModel draft = new AiModel();
        draft.setModelName("test-model");
        draft.setDisplayName("连接测试模型");
        draft.setModelType(modelType);
        draft.setInputCapabilities(inputCapabilities);
        draft.setOutputCapabilities(outputCapabilities);
        draft.setEmbeddingDimension(embeddingDimension);
        draft.setProviderType("OPENCODE_GO");
        draft.setApiHost("http://localhost:19999");
        draft.setApiKey(API_KEY);
        draft.setContextWindow(8192);
        draft.setMaxTokens(512);
        return draft;
    }

    /** 对话操作桩：可注入失败异常，记录收到的草稿与消息 */
    private static final class FakeChatOperation implements AiModelConnectionTestService.ChatTestOperation {
        private final RuntimeException failure;
        private AiModel draftSeen;
        private List<ChatMessage> messagesSeen;

        private FakeChatOperation(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void chat(AiModel draft, List<ChatMessage> messages) {
            this.draftSeen = draft;
            this.messagesSeen = messages;
            if (failure != null) {
                throw failure;
            }
        }
    }

    /** 向量操作桩：固定返回指定维度 */
    private static final class FakeEmbeddingOperation implements AiModelConnectionTestService.EmbeddingTestOperation {
        private final int dimension;

        private FakeEmbeddingOperation(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public Embedding embed(AiModel draft, String text) {
            return Embedding.from(new float[dimension]);
        }
    }

    /** 重排操作桩：记录入参并返回两条结果 */
    private static final class FakeRerankOperation implements AiModelConnectionTestService.RerankTestOperation {
        private String querySeen;
        private List<String> candidatesSeen;
        private int topNSeen;

        @Override
        public List<RerankResult> rerank(AiModel draft, String query, List<String> candidates, int topN) {
            this.querySeen = query;
            this.candidatesSeen = candidates;
            this.topNSeen = topN;
            return List.of(new RerankResult(0, 0.9, candidates.get(0)),
                new RerankResult(1, 0.1, candidates.get(1)));
        }
    }

    /** 图片操作桩：记录是否被调用 */
    private static final class FakeImageOperation implements AiModelConnectionTestService.ImageTestOperation {
        private boolean invoked;

        @Override
        public void testImage(AiModel draft) {
            this.invoked = true;
        }
    }
}

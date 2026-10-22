package com.nexus.ai.service.facade;

import com.nexus.ai.agent.ErpQueryAgent;
import com.nexus.ai.context.AiRequestContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 假模型驱动真实 Supervisor 与 ERP 子 Agent，验证多轮工具及复用线程的身份清理。 */
@Timeout(20)
class LangChain4jAgenticCompatibilityTest {
    /** 金额遵循应用的十进制字符串契约；数字或字符串形式的大整数均须精确到达工具。 */
    @ParameterizedTest
    @ValueSource(strings = {"9007199254740993", "\"9007199254740993\""})
    void supervisorRunsMultipleToolRoundsWithExactArgumentsAndTenant(String materialIdJson) throws Exception {
        var plans = new AtomicInteger();
        var modelCalls = new AtomicInteger();
        var tools = new ReadOnlyTool();
        ChatModel planner = new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) {
                assertIdentity();
                return text(plans.getAndIncrement() == 0
                    ? "{\"agentName\":\"query\",\"arguments\":{\"query\":\"核对库存\"}}"
                    : "{\"agentName\":\"done\",\"arguments\":{\"response\":\"已核对\"}}");
            }
        };
        ChatModel worker = new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) {
                assertIdentity();
                int call = modelCalls.getAndIncrement();
                if (call < 2) return ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                    .id("offline-" + call).name("readStock")
                    .arguments("{\"materialId\":" + materialIdJson + ",\"amount\":\"12345.67890123456789\"}").build())).build();
                return text("已核对两次库存");
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            String result = executor.submit(() -> {
                AiRequestContext.set("000007", 7L);
                try {
                    ErpQueryAgent agent = AgenticServices.agentBuilder(ErpQueryAgent.class).chatModel(worker).tools(tools).build();
                    return AgenticServices.supervisorBuilder().chatModel(planner).subAgents(agent)
                        .responseStrategy(SupervisorResponseStrategy.LAST).maxAgentsInvocations(4).build().invoke("核对库存");
                } finally { AiRequestContext.clear(); }
            }).get(10, TimeUnit.SECONDS);
            assertEquals("已核对两次库存", result);
            assertEquals(2, tools.calls.get());
            assertEquals(3, modelCalls.get());
            assertEquals(2, plans.get());
            assertThreadCleared(executor);
        } finally { executor.shutdownNow(); }
    }

    /** 子 Agent 失败向上传播，线程复用时也不能残留前一请求身份。 */
    @Test
    void childModelFailurePropagatesAndWorkerIdentityIsCleared() throws Exception {
        ChatModel planner = new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) {
                return text("{\"agentName\":\"query\",\"arguments\":{\"query\":\"核对库存\"}}");
            }
        };
        ChatModel failed = new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) {
                assertIdentity();
                throw new IllegalStateException("offline-model-failure");
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(() -> {
                AiRequestContext.set("000007", 7L);
                try {
                    var agent = AgenticServices.agentBuilder(ErpQueryAgent.class).chatModel(failed).build();
                    return AgenticServices.supervisorBuilder().chatModel(planner).subAgents(agent)
                        .responseStrategy(SupervisorResponseStrategy.LAST).maxAgentsInvocations(2).build().invoke("核对库存");
                } finally { AiRequestContext.clear(); }
            });
            var error = assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
            assertTrue(causeContains(error, "offline-model-failure"));
            assertThreadCleared(executor);
        } finally { executor.shutdownNow(); }
    }

    /** 只读合成工具，不连接业务数据库。 */
    public static final class ReadOnlyTool {
        private final AtomicInteger calls = new AtomicInteger();
        @Tool("读取离线库存，只读")
        public String readStock(@P("物料标识") long materialId, @P("高精度金额") BigDecimal amount) {
            assertIdentity();
            assertEquals(9007199254740993L, materialId);
            assertEquals(new BigDecimal("12345.67890123456789"), amount);
            calls.incrementAndGet();
            return "库存正常";
        }
    }

    private static ChatResponse text(String value) { return ChatResponse.builder().aiMessage(AiMessage.from(value)).build(); }
    private static void assertIdentity() { assertEquals("000007", AiRequestContext.getTenantId()); assertEquals(7L, AiRequestContext.getUserId()); }
    private static void assertThreadCleared(ExecutorService executor) throws Exception {
        executor.submit(() -> { assertNull(AiRequestContext.getTenantId()); assertNull(AiRequestContext.getUserId()); }).get(3, TimeUnit.SECONDS);
    }
    private static boolean causeContains(Throwable error, String message) {
        for (Throwable current = error; current != null; current = current.getCause()) if (message.equals(current.getMessage())) return true;
        return false;
    }
}

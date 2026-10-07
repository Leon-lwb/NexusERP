package com.nexus.flow.node.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.tool.BuiltinToolProvider;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.flow.node.FlowExecutionContext;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** ERP工具把失败转换成文本时，Flow不能将其包装为成功输出。 */
class ErpToolNodeResultGuardTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "查询失败：数据库不可用", "查询失败:数据源不可用", "参数错误：物料编码为空", "错误：表名不在白名单", "错误:表不存在"})
    @DisplayName("空结果与工具明确错误不能成为节点成功")
    void rejectedResultsCannotProduceSuccessfulNode(String text) throws Exception {
        ErpToolNodeProvider provider = provider(text);
        Exception failure = assertThrows(IllegalStateException.class, () -> provider.execute(context()));
        assertFalse(failure.getMessage().contains("数据库不可用"), "前端错误不透传工具底层明文");
        assertFalse(failure.getMessage().contains("数据源不可用"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"查询成功，但未返回数据。", "| 物料编码 | 单位 |\n| M001 | 件 |", "库存结构已读取"})
    @DisplayName("真实非空返回与明确零行成功仍可输出")
    void validResultAndSuccessfulZeroRowsRemain(String text) throws Exception {
        var output = provider(text).execute(context());
        assertEquals("business_lookup", output.get("toolName").asText());
        assertEquals(text, output.get("result").asText());
    }

    private ErpToolNodeProvider provider(String text) {
        BuiltinToolRegistry registry = mock(BuiltinToolRegistry.class);
        BuiltinToolProvider builtin = mock(BuiltinToolProvider.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(registry.getProvider("business_lookup")).thenReturn(builtin);
        doReturn(TextTool.class).when(builtin).getToolClass();
        when(applicationContext.getBean(TextTool.class)).thenReturn(new TextTool(text));
        return new ErpToolNodeProvider(registry, applicationContext);
    }

    private FlowExecutionContext context() {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setNodeName("业务查询");
        ctx.setTenantId("000000");
        ctx.setConfig(MAPPER.createObjectNode().put("toolName", "business_lookup"));
        return ctx;
    }

    public static class TextTool {
        private final String text;
        TextTool(String text) { this.text = text; }
        @Tool("返回业务读取结果")
        public String query() { return text; }
    }
}

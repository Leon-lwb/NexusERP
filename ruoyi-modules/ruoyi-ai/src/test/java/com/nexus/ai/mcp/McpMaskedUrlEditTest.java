package com.nexus.ai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.controller.McpMarketController;
import com.nexus.ai.mcp.service.IMcpMarketService;
import com.nexus.ai.mcp.service.McpClientManager;
import com.nexus.ai.mcp.service.McpToolServiceImpl;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.common.ai.domain.entity.AiMcpMarket;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiMcpMarketMapper;
import com.nexus.common.ai.mapper.AiMcpMarketToolMapper;
import com.nexus.common.ai.mapper.AiToolMapper;
import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 掩码地址的编辑语义回归；哨兵完全虚构，不读写真实数据库或凭据。 */
class McpMaskedUrlEditTest {
    private static final String SECRET = "REVIEW_ONLY_R5_SECRET_SENTINEL";
    private static final String STORED = "https://93.184.216.34/v1?token=" + SECRET;
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest(name = "不能静默丢弃掩码地址编辑：{0}")
    @ValueSource(strings = {"https://93.184.216.34/v2?token=****", "https://93.184.216.35/v1?token=****", "https://93.184.216.34/v1?renamed=****"})
    void 无法无歧义恢复的地址编辑应明确拒绝(String edited) throws Exception {
        String input=json.writeValueAsString(java.util.Map.of("baseUrl",edited));
        String old=json.writeValueAsString(java.util.Map.of("baseUrl",STORED));
        ServiceException error=assertThrows(ServiceException.class,()->McpConfigSanitizer.restoreMasks(input,old));
        assertTrue(error.getMessage().contains("重新填写"));
        assertFalse(error.toString().contains(SECRET));
        assertNull(error.getCause());
    }

    @Test void 未修改的脱敏地址可以恢复且其他明确编辑保留() throws Exception {
        String restored=McpConfigSanitizer.restoreMasks("{\"baseUrl\":\""+McpConfigSanitizer.maskUrl(STORED)+"\",\"label\":\"新名称\"}","{\"baseUrl\":\""+STORED+"\",\"label\":\"旧名称\"}");
        assertEquals(STORED,json.readTree(restored).path("baseUrl").asText());
        assertEquals("新名称",json.readTree(restored).path("label").asText());
        assertFalse(McpConfigSanitizer.maskJson(restored).contains(SECRET));
    }

    @Test void 完整新地址和显式新认证叶子不能被旧值覆盖() throws Exception {
        String input="{\"baseUrl\":\"https://93.184.216.34/v2?token=REVIEW_ONLY_NEW\",\"headers\":{\"Authorization\":\"REVIEW_ONLY_NEW\"}}";
        String restored=McpConfigSanitizer.restoreMasks(input,"{\"baseUrl\":\""+STORED+"\"}");
        assertEquals(json.readTree(input),json.readTree(restored));
    }

    @Test void 市场编辑不能声称成功却回退旧路径() {
        AiMcpMarketMapper mapper=mock(AiMcpMarketMapper.class);
        McpMarketController controller=new McpMarketController(mapper,mock(AiMcpMarketToolMapper.class),mock(IMcpMarketService.class));
        AiMcpMarket old=new AiMcpMarket();old.setId(1L);old.setUrl(STORED);
        when(mapper.selectById(1L)).thenReturn(old);
        AiMcpMarket edited=new AiMcpMarket();edited.setId(1L);edited.setUrl("https://93.184.216.34/v2?token=****");
        assertThrows(ServiceException.class,()->controller.edit(edited));
        verify(mapper,never()).updateById(any(AiMcpMarket.class));
    }

    @Test void 工具编辑拒绝歧义后不能落库或失效当前客户端() {
        AiToolMapper mapper=mock(AiToolMapper.class);McpClientManager manager=mock(McpClientManager.class);
        McpToolServiceImpl service=new McpToolServiceImpl(mapper,mock(BuiltinToolRegistry.class),manager);
        AiTool old=new AiTool();old.setId(1L);old.setToolType("REMOTE");old.setConfigJson("{\"baseUrl\":\""+STORED+"\"}");
        when(mapper.selectById(1L)).thenReturn(old);
        AiTool edited=new AiTool();edited.setId(1L);edited.setToolType("REMOTE");edited.setConfigJson("{\"baseUrl\":\"https://93.184.216.34/v2?token=****\"}");
        assertThrows(ServiceException.class,()->service.updateTool(edited));
        verify(mapper,never()).updateById(any(AiTool.class));verifyNoInteractions(manager);
    }
}

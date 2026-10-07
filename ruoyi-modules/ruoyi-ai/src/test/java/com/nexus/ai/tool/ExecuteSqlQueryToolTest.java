package com.nexus.ai.tool;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.query.ErpQueryService;
import com.nexus.ai.service.query.SqlSafetyValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** SQL 工具只验证委托与展示边界；SQL 安全核心另有工作线程负责，本测试不执行数据库查询。 */
class ExecuteSqlQueryToolTest {

    private final ErpQueryService query = mock(ErpQueryService.class);
    private final SqlSafetyValidator validator = mock(SqlSafetyValidator.class);
    private final AiProperties properties = new AiProperties();
    private final ExecuteSqlQueryTool tool = new ExecuteSqlQueryTool(query, properties, validator);

    @Test
    void 只执行校验后的SQL并保留雪花ID与未知数据() {
        String input = "SELECT material_id, material_name FROM bd_material";
        String sanitized = "SELECT material_id, material_name FROM bd_material WHERE tenant_id = '000000' LIMIT 100";
        when(validator.validateAndSanitize(input, properties.getSqlMaxRows())).thenReturn(sanitized);
        var row = new LinkedHashMap<String, Object>();
        row.put("物料ID", 1888888888888888888L);
        row.put("名称", "精密电机壳体");
        row.put("单位", null);
        when(query.executeQuery(sanitized, properties.getSqlMaxRows(), properties.getSqlTimeout() / 1000))
            .thenReturn(List.of(row));
        String result = tool.executeSqlQuery(input);
        assertTrue(result.contains("1888888888888888888"));
        assertTrue(result.contains("精密电机壳体 | -"));
        verify(query).executeQuery(sanitized, properties.getSqlMaxRows(), properties.getSqlTimeout() / 1000);
        verify(query, never()).executeQuery(eq(input), anyInt(), anyInt());
    }

    @Test
    void SELECT无结果不等于有库存或查询失败() {
        when(validator.validateAndSanitize("SELECT * FROM bd_material", properties.getSqlMaxRows()))
            .thenReturn("SELECT * FROM bd_material LIMIT 100");
        assertEquals("Query executed successfully, but no results returned.", tool.executeSqlQuery("SELECT * FROM bd_material"));
    }

    @ParameterizedTest(name = "校验器拒绝后不得调用数据库：{0}")
    @NullAndEmptySource
    @ValueSource(strings = {"DELETE FROM bd_material", "SELECT * FROM sys_user", "SELECT * FROM bd_material WHERE material_id = 非法ID"})
    void 非法SQL和标识符拒绝仍委托公共安全核心(String input) {
        when(validator.validateAndSanitize(input, properties.getSqlMaxRows()))
            .thenThrow(new IllegalArgumentException("测试安全核心拒绝此查询"));
        assertEquals("Error: 测试安全核心拒绝此查询", tool.executeSqlQuery(input));
        verifyNoInteractions(query);
    }

    @Test
    void 执行失败不能伪装成空结果() {
        when(validator.validateAndSanitize("SELECT * FROM bd_material", properties.getSqlMaxRows()))
            .thenReturn("SELECT * FROM bd_material LIMIT 100");
        when(query.executeQuery(anyString(), anyInt(), anyInt())).thenThrow(new IllegalStateException("测试查询服务不可用"));
        assertTrue(tool.executeSqlQuery("SELECT * FROM bd_material").startsWith("Error: Query execution failed"));
    }
}

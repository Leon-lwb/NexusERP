package com.nexus.ai.tool;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.query.ErpQueryService;
import com.nexus.ai.service.query.ErpTableSchemaManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 表结构工具必须在缓存读取、刷新和 DDL 降级前统一执行同一配置白名单。 */
@ExtendWith(MockitoExtension.class)
class QueryTableSchemaToolTest {

    @Mock private ErpTableSchemaManager schemas;
    @Mock private ErpQueryService query;
    @Spy private AiProperties properties = new AiProperties();
    @InjectMocks private QueryTableSchemaTool tool;

    @Test
    void 命中缓存时返回真实结构摘要() {
        var schema = mock(ErpTableSchemaManager.TableStructure.class);
        when(schemas.getTableSchema("bd_material")).thenReturn(schema);
        when(schemas.formatEnhancedTableSummary(schema)).thenReturn("物料表：material_id、material_code");
        assertEquals("物料表：material_id、material_code", tool.queryTableSchema("bd_material"));
        verifyNoInteractions(query);
    }

    @Test
    void 不存在的白名单表应如实提示先查表列表() {
        String result = tool.queryTableSchema("bd_missing");
        assertTrue(result.contains("不存在"));
        assertTrue(result.contains("queryAllTables"));
        verify(schemas, never()).refreshTableSchema(anyString());
    }

    @Test
    void 非白名单表不得进入缓存未命中后的存在性和DDL查询() {
        assertTrue(tool.queryTableSchema("sys_user").contains("不允许"));
        verifyNoInteractions(schemas, query);
    }

    @Test
    void 即使错误缓存了非白名单表也必须拒绝() {
        var schema = mock(ErpTableSchemaManager.TableStructure.class);
        // 旧实现会读取这份缓存；修复后应在读取前拒绝，故这两处替身允许不被调用。
        lenient().when(schemas.getTableSchema("sys_user")).thenReturn(schema);
        lenient().when(schemas.formatEnhancedTableSummary(schema)).thenReturn("系统用户表结构");
        assertTrue(tool.queryTableSchema("sys_user").contains("不允许"));
        verifyNoInteractions(schemas, query);
    }

    @Test
    void 使用配置白名单而不是另写一份硬编码列表() {
        properties.setAllowedTablePrefixes(List.of("custom_"));
        var schema = mock(ErpTableSchemaManager.TableStructure.class);
        when(schemas.getTableSchema("custom_material")).thenReturn(schema);
        when(schemas.formatEnhancedTableSummary(schema)).thenReturn("自定义物料表结构");
        assertEquals("自定义物料表结构", tool.queryTableSchema("custom_material"));
        assertTrue(tool.queryTableSchema("bd_material").contains("不允许"));
        verify(schemas, never()).getTableSchema("bd_material");
    }

    @Test
    void 空白名单应拒绝所有表而非默认放行() {
        properties.setAllowedTablePrefixes(List.of());
        assertTrue(tool.queryTableSchema("bd_material").contains("不允许"));
        verifyNoInteractions(schemas, query);
    }

    @ParameterizedTest(name = "非法表标识符不能查询：{0}")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "bd_material;DROP TABLE bd_material", "bd_material OR 1=1", "123table"})
    void 非法表标识符应直接拒绝(String tableName) {
        assertTrue(tool.queryTableSchema(tableName).startsWith("错误："));
        verifyNoInteractions(schemas, query);
    }

    @Test
    void 刷新仍无结构时允许白名单表降级DDL并限制大小() {
        when(query.tableExists("bd_material")).thenReturn(true);
        when(query.queryTableSchema("bd_material")).thenReturn("物料字段".repeat(1000));
        String result = tool.queryTableSchema("bd_material");
        assertTrue(result.startsWith("物料字段"));
        assertTrue(result.endsWith("... (truncated)"));
        verify(schemas).refreshTableSchema("bd_material");
    }
}

package com.nexus.ai.tool;

import com.nexus.ai.service.query.ErpTableSchemaManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 表目录是结构信息，不伪造实体数据，也不借单元测试连接真实库。 */
class QueryAllTablesToolTest {

    private final ErpTableSchemaManager schemas = mock(ErpTableSchemaManager.class);
    private final QueryAllTablesTool tool = new QueryAllTablesTool(schemas);

    @Test
    void 查询成功返回真实表名与数量() {
        when(schemas.getAllTablesSummary()).thenReturn("bd_material：物料\nwm_material_stock：库存");
        when(schemas.getTableCount()).thenReturn(2);
        assertEquals("Found 2 ERP business tables:\nbd_material：物料\nwm_material_stock：库存", tool.queryAllTables());
    }

    @Test
    void 无业务表时不能编造演示目录() {
        when(schemas.getAllTablesSummary()).thenReturn("");
        assertEquals("No ERP business tables found.", tool.queryAllTables());
        verify(schemas, never()).getTableCount();
    }

    @Test
    void 缓存查询失败应如实返回失败() {
        when(schemas.getAllTablesSummary()).thenThrow(new IllegalStateException("测试表目录不可用"));
        assertEquals("Error: 测试表目录不可用", tool.queryAllTables());
    }
}

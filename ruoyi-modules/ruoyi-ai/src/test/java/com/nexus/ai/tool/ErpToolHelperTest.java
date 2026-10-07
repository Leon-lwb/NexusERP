package com.nexus.ai.tool;

import com.nexus.ai.service.query.ErpQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 内置 ERP 工具的租户字符串边界回归，不连接数据库。
 * 反射调用用于让旧 Long 基线也能执行 RED，而不是因新 String 签名尚未存在而编译失败。
 */
class ErpToolHelperTest {

    private final ErpToolHelper helper = new ErpToolHelper(mock(ErpQueryService.class)) { };

    @ParameterizedTest(name = "租户 {0} 必须作为精确字符串参与比较")
    @ValueSource(strings = {"000000", "000042", "TENANT-A", "0"})
    void 租户字符串不得数字化或补零(String tenantId) {
        String sql = assertDoesNotThrow(() -> inject("SELECT * FROM wm_material_stock s ORDER BY s.stock_id", tenantId));
        assertTrue(sql.contains("s.tenant_id = '" + tenantId + "'"), sql);
        assertFalse(sql.contains("s.tenant_id = " + tenantId + " "), sql);
    }

    @ParameterizedTest(name = "非法或缺失租户不能生成查询：{0}")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "x' OR '1'='1", "TENANT\\A", "A;SELECT", "A\nB", "123456789012345678901"})
    void 非法或缺失租户应拒绝而非查询全库(String tenantId) {
        assertThrows(IllegalArgumentException.class,
            () -> inject("SELECT * FROM wm_material_stock s", tenantId));
    }

    @Test
    void 预置参数化租户条件也不能绕过缺失上下文检查() {
        assertThrows(IllegalArgumentException.class,
            () -> inject("SELECT * FROM wm_material_stock s WHERE s.tenant_id = ?", null));
    }

    @Test
    void 已有参数化租户条件不应重复注入() {
        String sql = "SELECT * FROM wm_material_stock s WHERE s.tenant_id = ?";
        assertEquals(sql, assertDoesNotThrow(() -> inject(sql, "000000")));
    }

    @Test
    void 无别名的表名不能被WHERE关键字替代() {
        String sql = assertDoesNotThrow(() -> inject("SELECT * FROM wm_material_stock WHERE stock_quantity > 0", "TENANT-A"));
        assertTrue(sql.contains("wm_material_stock.tenant_id = 'TENANT-A'"), sql);
        assertFalse(sql.contains("WHERE.tenant_id"), sql);
    }

    @Test
    void 表间租户相等不能替代当前请求的租户隔离() {
        String sql = inject("SELECT s.material_id FROM wm_material_stock s "
            + "JOIN bd_material m ON m.material_id = s.material_id AND m.tenant_id = s.tenant_id", "000000");
        assertTrue(sql.contains("s.tenant_id = '000000'"), sql);
    }

    private String inject(String sql, String tenantId) {
        return ReflectionTestUtils.invokeMethod(helper, "injectTenantCondition", sql, tenantId);
    }
}

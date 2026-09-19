package com.nexus.erp.so;

import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.so.report.service.SoSalesReportServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售报表 Service 单元测试（T1.6）。
 * <p>报表用 JdbcTemplate 原生 SQL，MyBatis-Plus 的租户插件与列名映射都帮不上忙，
 * 因此这里直接断言生成的 SQL 文本与绑定参数 —— 这正是原实现出问题的地方：
 * 列名 {@code so.order_id}/{@code so.status} 在 so_sales_order_header 中不存在，
 * 且完全没有 tenant_id 条件。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class SoSalesReportServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private SoSalesReportServiceImpl reportService;

    private static MockedStatic<TenantHelper> tenantHelperMock;

    private String capturedSql;
    private List<Object> capturedParams;

    @BeforeAll
    static void initStatics() {
        tenantHelperMock = mockStatic(TenantHelper.class);
    }

    @AfterAll
    static void closeStatics() {
        if (tenantHelperMock != null) {
            tenantHelperMock.close();
        }
    }

    /** 执行并抓取 SQL 与绑定参数（同一测试内多次调用时取最后一次） */
    private void runAndCapture(Runnable invocation) {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        invocation.run();
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForList(sqlCaptor.capture(), argsCaptor.capture());
        List<String> sqls = sqlCaptor.getAllValues();
        List<Object[]> argLists = argsCaptor.getAllValues();
        capturedSql = sqls.get(sqls.size() - 1);
        capturedParams = Arrays.asList(argLists.get(argLists.size() - 1));
    }

    /** 销售执行率：必须用 order_status 而不是不存在的 status */
    @Test
    void salesExecution_usesOrderStatusColumn() {
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(null);

        runAndCapture(() -> reportService.getSalesExecution(6));

        assertTrue(capturedSql.contains("so.order_status"), "应使用 order_status 列: " + capturedSql);
        assertFalse(capturedSql.contains("so.status"), "不应出现不存在的 so.status 列: " + capturedSql);
        assertTrue(capturedSql.contains("GROUP BY month"), "应按月分组: " + capturedSql);
    }

    /** 销售执行率：排除已取消订单（金额与完成率分母都不含取消单） */
    @Test
    void salesExecution_excludesCancelledOrders() {
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(null);

        runAndCapture(() -> reportService.getSalesExecution(6));

        assertTrue(capturedSql.contains("<> 'CANCELLED'"), "应排除取消单: " + capturedSql);
        assertTrue(capturedSql.contains("so.del_flag = '0'"), "应排除逻辑删除: " + capturedSql);
    }

    /** 销售执行率：有租户上下文时必须带 tenant_id 条件（原生 SQL 绕过租户插件） */
    @Test
    void salesExecution_addsTenantFilterWhenTenantPresent() {
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn("000000");

        runAndCapture(() -> reportService.getSalesExecution(6));

        assertTrue(capturedSql.contains("so.tenant_id = ?"), "应带租户条件: " + capturedSql);
        assertTrue(capturedParams.contains("000000"), "租户ID应作为绑定参数: " + capturedParams);
    }

    /** 统计周期收敛到 [1, 60]，防超大范围全表扫描 */
    @Test
    void salesExecution_clampsMonths() {
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(null);

        runAndCapture(() -> reportService.getSalesExecution(0));
        assertEquals(1, capturedParams.get(0), "下限应为 1");

        runAndCapture(() -> reportService.getSalesExecution(9999));
        assertEquals(60, capturedParams.get(0), "上限应为 60");

        runAndCapture(() -> reportService.getSalesExecution(null));
        assertEquals(6, capturedParams.get(0), "默认应为 6");
    }

    /** 客户排行：必须用 so_id 而不是不存在的 order_id，且 topN 参数化 */
    @Test
    void customerRanking_usesSoIdColumnAndParameterizedLimit() {
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(null);

        runAndCapture(() -> reportService.getCustomerRanking(3, 5));

        assertTrue(capturedSql.contains("COUNT(so.so_id)"), "应使用 so_id 列: " + capturedSql);
        assertFalse(capturedSql.contains("so.order_id"), "不应出现不存在的 so.order_id 列: " + capturedSql);
        assertTrue(capturedSql.contains("LIMIT ?"), "LIMIT 应参数化: " + capturedSql);
        assertTrue(capturedParams.contains(5), "topN 应作为绑定参数: " + capturedParams);
        assertTrue(capturedParams.contains(3), "months 应作为绑定参数: " + capturedParams);
    }

    /** 客户排行：topN 非法时回落默认 10，并收敛到 [1, 100] */
    @Test
    void customerRanking_clampsTopN() {
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(null);

        runAndCapture(() -> reportService.getCustomerRanking(6, null));
        assertTrue(capturedParams.contains(10), "默认 topN 应为 10");

        runAndCapture(() -> reportService.getCustomerRanking(6, -5));
        assertTrue(capturedParams.contains(10), "非法 topN 应回落 10");

        runAndCapture(() -> reportService.getCustomerRanking(6, 9999));
        assertTrue(capturedParams.contains(100), "topN 上限应为 100");
    }
}

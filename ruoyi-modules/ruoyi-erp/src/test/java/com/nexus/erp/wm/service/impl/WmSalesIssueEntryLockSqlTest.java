package com.nexus.erp.wm.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.erp.wm.domain.WmSalesIssue;
import com.nexus.erp.wm.mapper.SalesIssueSourceGuardMapper;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.service.SalesIssueSourceGuard;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 只解析固定映射 SQL 和事务注解，不获取连接、不执行 SQL，不代表物理 MySQL 验证。 */
class WmSalesIssueEntryLockSqlTest {
    @Test
    void fixedSqlBindsTenantOrderAndEverySourceAndUsesOrderedRowLocks() {
        assertEquals("erp", SalesIssueSourceGuardMapper.class.getAnnotation(DS.class).value());
        Configuration config = new Configuration();
        config.addMapper(SalesIssueSourceGuardMapper.class);
        String prefix = SalesIssueSourceGuardMapper.class.getName() + ".";
        var header = config.getMappedStatement(prefix + "lockHeader").getBoundSql(Map.of("tenantId", "000000", "soId", 7L));
        String headerSql = normalized(header.getSql());
        assertTrue(headerSql.contains("from so_sales_order_header"));
        assertTrue(headerSql.contains("tenant_id = ? and so_id = ? and del_flag = '0'"));
        assertTrue(headerSql.endsWith("for update"));
        assertEquals(List.of("tenantId", "soId"), header.getParameterMappings().stream().map(m -> m.getProperty()).toList());
        var lines = config.getMappedStatement(prefix + "lockLines").getBoundSql(
            Map.of("tenantId", "000000", "soId", 7L, "lineIds", List.of(21L, 22L)));
        String lineSql = normalized(lines.getSql());
        assertTrue(lineSql.contains("from so_sales_order_line"));
        assertTrue(lineSql.contains("tenant_id = ? and so_id = ? and del_flag = '0' and line_id in"));
        assertTrue(lineSql.contains("ordered_qty as orderedqty"));
        assertTrue(lineSql.contains("delivered_qty as deliveredqty"));
        assertTrue(lineSql.contains("reserved_qty as reservedqty"));
        assertTrue(lineSql.contains("oqc_status as oqcstatus"));
        assertTrue(lineSql.endsWith("order by line_id for update"));
        assertEquals(4, lines.getParameterMappings().size());
        assertEquals(21L, lines.getAdditionalParameter(lines.getParameterMappings().get(2).getProperty()));
        assertEquals(22L, lines.getAdditionalParameter(lines.getParameterMappings().get(3).getProperty()));
        for (var method : SalesIssueSourceGuardMapper.class.getMethods()) {
            assertFalse(String.join(" ", method.getAnnotation(Select.class).value()).contains("${"), "不得接受动态 SQL 拼接");
        }
    }

    @Test
    void locksAndAllAccountingStayInsideOriginalErpRollbackBoundary() throws Exception {
        assertEquals("erp", WmSalesIssueServiceImpl.class.getAnnotation(DS.class).value());
        assertEquals("erp", SalesIssueSourceGuard.class.getAnnotation(DS.class).value());
        var entry = WmSalesIssueServiceImpl.class.getMethod("completeSalesIssue", Long.class).getAnnotation(Transactional.class);
        var guard = SalesIssueSourceGuard.class.getMethod("validateAndLock", WmSalesIssue.class, List.class).getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRED, entry.propagation());
        assertEquals(Propagation.MANDATORY, guard.propagation());
        assertTrue(List.of(entry.rollbackFor()).contains(Exception.class));
        assertTrue(List.of(guard.rollbackFor()).contains(Exception.class));
        String batchSql = normalized(String.join(" ", WmBatchMapper.class.getMethod("increaseIssuedQty", Long.class,
            Long.class, String.class, BigDecimal.class).getAnnotation(Update.class).value()));
        assertTrue(batchSql.contains("issued_qty = ifnull(issued_qty, 0) + #{issueqty}"));
        assertTrue(batchSql.contains("batch_id = #{batchid} and material_id = #{materialid} and tenant_id = #{tenantid}"));
        assertTrue(batchSql.contains("del_flag = '0' and #{issueqty} > 0"));
        assertTrue(batchSql.contains("ifnull(issued_qty, 0) + #{issueqty} <= ifnull(received_qty, 0)"));
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}

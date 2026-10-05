package com.nexus.erp.wm.strategy.impl;

import com.nexus.erp.wm.mapper.WmBatchMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** 生产领料批次原子更新的静态 SQL 契约；不连接任何数据库。 */
class ProductionIssueBatchAtomicSqlTest {

    @Test
    void issuedQuantityIsRelativeAtomicUpdateWithReceiveCap() {
        String sql = atomicSql();
        assertTrue(sql.startsWith("update wm_batch set issued_qty = ifnull(issued_qty, 0) + #{issueqty}"), sql);
        String where = sql.substring(sql.indexOf(" where "));
        assertTrue(where.contains("#{issueqty} > 0"), sql);
        assertTrue(where.contains("ifnull(issued_qty, 0) + #{issueqty} <= ifnull(received_qty, 0)"), sql);
        assertFalse(sql.contains("status ="), "不能扩展批次状态策略");
        assertFalse(sql.contains("received_qty ="), "领料不能改写收量");
        // 同一原子语句必须绑定实际库存所属批次、物料和租户。
        assertTrue(where.contains("batch_id = #{batchid}"), sql);
        assertTrue(where.contains("material_id = #{materialid}"), sql);
        assertTrue(where.contains("tenant_id = #{tenantid}"), sql);
        assertTrue(where.contains("del_flag = '0'"), sql);
        assertEquals(Arrays.asList("batchId", "materialId", "tenantId", "issueQty"),
            Arrays.stream(atomicMethod().getParameters()).map(parameter -> parameter.getAnnotation(Param.class).value()).toList());
    }

    private static String atomicSql() {
        Update annotation = atomicMethod().getAnnotation(Update.class);
        assertNotNull(annotation, "必须使用原生原子更新，不能先读后改累计值");
        return String.join(" ", annotation.value()).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static Method atomicMethod() {
        Method method = Arrays.stream(WmBatchMapper.class.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals("increaseIssuedQty")).findFirst().orElse(null);
        assertNotNull(method, "生产领料缺少 wm_batch.issued_qty 原生原子累计方法");
        return method;
    }
}

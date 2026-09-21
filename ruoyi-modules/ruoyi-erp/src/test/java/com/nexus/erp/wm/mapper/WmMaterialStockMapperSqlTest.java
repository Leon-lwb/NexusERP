package com.nexus.erp.wm.mapper;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存原子 SQL 守卫 单元测试（审计 ERP-WM-01 / ERP-WM-02 / ERP-WM-09 回归）
 * <p>守卫必须落在原子 SQL 的 WHERE 里，否则「读库存行 → 冻结/预留 → 扣减」的 TOCTOU 窗口依旧存在。</p>
 */
@Tag("dev")
class WmMaterialStockMapperSqlTest {

    /** ERP-WM-09：冻结/解冻后旧版本的扣减必须在 SQL 层被 frozen_flag 挡住 */
    @Test
    void deductStock_guardsFrozenFlag() throws Exception {
        String sql = updateSqlOf("deductStock", Long.class, BigDecimal.class, Integer.class);
        assertTrue(sql.contains("frozen_flag = 0"),
            "ERP-WM-09: deductStock 必须带 frozen_flag = 0 守卫, 实际: " + sql);
        assertTrue(sql.contains("stock_quantity >= #{deductQty}"), "不得超扣");
        assertTrue(sql.contains("version = #{version}"), "必须保留乐观锁条件");
    }

    /** ERP-WM-02：非预留消费类出库必须按「可用量」守卫，而非账面量 */
    @Test
    void deductStockAvailable_guardsAvailableQuantityAndFrozenFlag() throws Exception {
        String sql = updateSqlOf("deductStockAvailable", Long.class, BigDecimal.class, Integer.class);
        assertTrue(sql.contains("reserved_quantity"),
            "ERP-WM-02: 必须按 (stock_quantity - reserved_quantity) 守卫, 实际: " + sql);
        assertTrue(sql.contains("frozen_flag = 0"), "ERP-WM-09: 同样必须带冻结守卫");
        assertTrue(sql.contains("version = #{version}"), "必须保留乐观锁条件");
    }

    /** ERP-WM-02：销售出库消费预留的入口同样不得扣减冻结品 */
    @Test
    void deductStockWithReserved_guardsFrozenFlag() throws Exception {
        String sql = updateSqlOf("deductStockWithReserved", Long.class, BigDecimal.class, BigDecimal.class, Integer.class);
        assertTrue(sql.contains("reserved_quantity >= #{consumeReservedQty}"), "不得超额消费预留");
        assertTrue(sql.contains("frozen_flag = 0"),
            "ERP-WM-09: deductStockWithReserved 必须带 frozen_flag = 0 守卫, 实际: " + sql);
    }

    /** 盘点盘亏需要能调整冻结行，故该入口刻意不带冻结守卫（与 deductStock 区分） */
    @Test
    void deductStockIgnoreFrozen_hasNoFrozenGuard() throws Exception {
        String sql = updateSqlOf("deductStockIgnoreFrozen", Long.class, BigDecimal.class, Integer.class);
        assertFalse(sql.contains("frozen_flag"), "盘点盘亏入口不应带冻结守卫, 实际: " + sql);
        assertTrue(sql.contains("stock_quantity >= #{deductQty}"), "仍不得超扣");
    }

    /**
     * ERP-WM-01：出库成本结转必须是原子相对扣减，且不得再包含「出库量 vs 余量」的前置校验。
     * <p>修复前该逻辑在 Java 里比较「扣减后的余量」，整批出库必然抛「出库数量超过库存数量」。</p>
     */
    @Test
    void issueStockCost_isAtomicRelativeDeduction_withoutQuantityPrecondition() throws Exception {
        String sql = updateSqlOf("issueStockCost", Long.class, BigDecimal.class);
        assertTrue(sql.contains("total_cost"), "必须扣减 total_cost, 实际: " + sql);
        assertTrue(sql.contains("GREATEST"), "total_cost 不得被扣成负数");
        assertFalse(sql.contains("stock_quantity >= #{outQty}"),
            "ERP-WM-01: 成本结转不得再自行校验出库量, 数量充足性由 deductStock* 负责");
    }

    private String updateSqlOf(String method, Class<?>... paramTypes) throws Exception {
        Method m = WmMaterialStockMapper.class.getMethod(method, paramTypes);
        Update update = m.getAnnotation(Update.class);
        assertNotNull(update, method + " 必须使用 @Update 原子 SQL（禁止 Java 读-改-写）");
        return String.join(" ", update.value());
    }
}

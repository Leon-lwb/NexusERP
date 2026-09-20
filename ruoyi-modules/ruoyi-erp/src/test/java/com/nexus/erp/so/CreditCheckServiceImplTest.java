package com.nexus.erp.so;

import com.nexus.erp.bd.domain.BdClientCredit;
import com.nexus.erp.bd.mapper.BdClientCreditMapper;
import com.nexus.erp.so.domain.SoCreditUsage;
import com.nexus.erp.so.mapper.SoCreditUsageMapper;
import com.nexus.erp.so.service.impl.CreditCheckServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 信用检查 Service 单元测试（收尾批次：ERP-BD-09 + ERP-SO-08）。
 * <p>断言"修复前会失败"的核心行为：</p>
 * <ul>
 *   <li>ERP-BD-09：客户存在多条信用记录时<b>不得</b>再用 {@code selectOne}（多行抛
 *       TooManyResultsException → 销售订单确认 500），改为取最新一行；</li>
 *   <li>ERP-SO-08：已占用额度改为数据库端 {@code SUM} 聚合，不再把全部占用记录载入内存累加。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
@Tag("local")
class CreditCheckServiceImplTest {

    @Mock
    private SoCreditUsageMapper creditUsageMapper;
    @Mock
    private BdClientCreditMapper bdClientCreditMapper;

    @InjectMocks
    private CreditCheckServiceImpl service;

    /** ERP-BD-09：同客户多行信用记录时取最新一行并正常冻结，不再 selectOne 抛异常。 */
    @Test
    void checkAndFreezeTakesLatestRowWhenCreditDuplicated() {
        BdClientCredit latest = credit(9L, 100_000);
        BdClientCredit stale = credit(1L, 10);
        when(bdClientCreditMapper.selectList(any())).thenReturn(List.of(latest, stale));
        // 已占用 0（selectMaps 聚合返回空集）
        when(creditUsageMapper.selectMaps(any())).thenReturn(Collections.emptyList());
        when(creditUsageMapper.selectCount(any())).thenReturn(0L);

        assertDoesNotThrow(() -> service.checkAndFreeze(7L, 100L, new BigDecimal("50")));

        // 修复前走 selectOne（多行即抛 TooManyResultsException），且不会走到 insert
        verify(bdClientCreditMapper, never()).selectOne(any());
        verify(creditUsageMapper).insert(any(SoCreditUsage.class));
    }

    /** ERP-BD-09：唯一一条信用记录且额度充足时同样正常冻结。 */
    @Test
    void checkAndFreezeUsesSingleRow() {
        when(bdClientCreditMapper.selectList(any())).thenReturn(List.of(credit(3L, 1_000)));
        when(creditUsageMapper.selectMaps(any())).thenReturn(Collections.emptyList());
        when(creditUsageMapper.selectCount(any())).thenReturn(0L);

        assertDoesNotThrow(() -> service.checkAndFreeze(7L, 101L, new BigDecimal("200")));
        verify(creditUsageMapper).insert(any(SoCreditUsage.class));
    }

    /** ERP-SO-08：已占用额度由数据库 SUM 返回（修复前 selectList 全量载入内存）。 */
    @Test
    void getUsedAmountReadsDatabaseSum() {
        Map<String, Object> row = new HashMap<>();
        row.put("total", new BigDecimal("150.50"));
        when(creditUsageMapper.selectMaps(any())).thenReturn(List.of(row));

        BigDecimal used = service.getUsedAmount(7L);

        assertEquals(0, used.compareTo(new BigDecimal("150.50")));
        verify(creditUsageMapper, never()).selectList(any());
    }

    /** ERP-SO-08：无占用记录时 SUM 为 0（兼容驱动返回 Integer/String 的情况）。 */
    @Test
    void getUsedAmountReturnsZeroWhenNoRows() {
        when(creditUsageMapper.selectMaps(any())).thenReturn(Collections.emptyList());
        assertEquals(0, service.getUsedAmount(7L).compareTo(BigDecimal.ZERO));

        Map<String, Object> zero = new HashMap<>();
        zero.put("total", 0);
        when(creditUsageMapper.selectMaps(any())).thenReturn(List.of(zero));
        assertEquals(0, service.getUsedAmount(7L).compareTo(BigDecimal.ZERO));
    }

    private BdClientCredit credit(Long creditId, int amount) {
        BdClientCredit c = new BdClientCredit();
        c.setCreditId(creditId);
        c.setClientId(7L);
        c.setClientCredit(amount);
        return c;
    }
}

package com.nexus.erp.so;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.impl.SoSalesOrderLineServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售订单行 Service 单元测试（收尾批次：ERP-SO-02）。
 * <p>核心断言：{@code updateOqcStatus} 不再按 {@code (so_id, material_id)} 批量改写同物料多行，
 * 而是先定位唯一目标行、再按 {@code line_id} 精确更新（修复前 SQL 里带 material_id 且会命中全部行）。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
@Tag("local")
class SoSalesOrderLineServiceImplTest {

    @Mock
    private SoSalesOrderLineMapper baseMapper;

    @InjectMocks
    private SoSalesOrderLineServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 真实列缓存：LambdaUpdateWrapper.getSqlSegment() 需要解析 lambda → 列名
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            SoSalesOrderLine.class);
    }

    /** 同订单同物料两行时，只按 line_id 更新首行，不再按 material_id 批量误伤。 */
    @Test
    void updateOqcStatusTargetsSingleLineByLineId() {
        when(baseMapper.selectList(any())).thenReturn(List.of(line(11L), line(12L)));
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        int rows = service.updateOqcStatus(100L, 5L, "UNQUALIFIED", 900L, "OQC001", new Date());

        assertEquals(1, rows);
        ArgumentCaptor<Wrapper<SoSalesOrderLine>> captor = wrapperCaptor();
        String where = captor.getValue().getSqlSegment();
        assertTrue(where.contains("line_id"), "应按 line_id 精确更新, 实际: " + where);
        assertFalse(where.contains("material_id"), "不得再按 material_id 批量更新, 实际: " + where);
    }

    /** 无未取消行时返回 0 且不执行更新。 */
    @Test
    void updateOqcStatusReturnsZeroWhenNoCandidateLine() {
        when(baseMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertEquals(0, service.updateOqcStatus(100L, 5L, "QUALIFIED", 901L, "OQC002", new Date()));
        verify(baseMapper, never()).update(isNull(), any());
    }

    /** 按行ID精确回写：WHERE 只含 line_id 条件。 */
    @Test
    void updateOqcStatusByLineIdUsesLineIdOnly() {
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        int rows = service.updateOqcStatusByLineId(12L, "QUALIFIED", 902L, "OQC003", new Date());

        assertEquals(1, rows);
        ArgumentCaptor<Wrapper<SoSalesOrderLine>> captor = wrapperCaptor();
        String where = captor.getValue().getSqlSegment();
        assertTrue(where.contains("line_id"));
        assertFalse(where.contains("material_id"));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Wrapper<SoSalesOrderLine>> wrapperCaptor() {
        ArgumentCaptor<Wrapper<SoSalesOrderLine>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        return captor;
    }

    private SoSalesOrderLine line(Long lineId) {
        SoSalesOrderLine l = new SoSalesOrderLine();
        l.setLineId(lineId);
        l.setSoId(100L);
        l.setMaterialId(5L);
        l.setOrderedQty(BigDecimal.TEN);
        l.setLineStatus("CONFIRMED");
        return l;
    }
}

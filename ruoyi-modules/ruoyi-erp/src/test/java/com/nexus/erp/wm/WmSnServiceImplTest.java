package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmSn;
import com.nexus.erp.wm.mapper.WmSnMapper;
import com.nexus.erp.wm.service.impl.WmSnServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 序列号管理Service单元测试
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class WmSnServiceImplTest {

    @Mock
    private WmSnMapper baseMapper;

    @InjectMocks
    private WmSnServiceImpl wmSnService;

    private WmSn existingSn;

    @BeforeEach
    void setUp() {
        existingSn = new WmSn();
        existingSn.setSnId(1L);
        existingSn.setSnCode("SN202606010001");
        existingSn.setMaterialId(100L);
        existingSn.setStatus("IN_STOCK");
    }

    /**
     * 批量生成序列号 - 验证生成数量和编码格式
     */
    @Test
    void testBatchGenerate() {
        when(baseMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(baseMapper.insert(any(WmSn.class))).thenReturn(1);

        wmSnService.batchGenerate(100L, 3, 1L, 200L, "PO", "PO20260601001");

        // 验证插入了3条记录
        verify(baseMapper, times(3)).insert(any(WmSn.class));
    }

    /**
     * 批量生成序列号 - 第二批生成时序号递增
     */
    @Test
    void testBatchGenerate_SequenceIncrement() {
        // 模拟当天已有最大序号的序列号
        WmSn lastSn = new WmSn();
        lastSn.setSnCode("SN202606010005");

        when(baseMapper.selectList(any())).thenReturn(List.of(lastSn));
        when(baseMapper.insert(any(WmSn.class))).thenReturn(1);

        wmSnService.batchGenerate(100L, 2, 1L, 200L, "PO", "PO20260601001");

        // 验证插入了2条记录
        verify(baseMapper, times(2)).insert(any(WmSn.class));

        // 捕获插入的SN对象，验证序号递增
        ArgumentCaptor<WmSn> captor = ArgumentCaptor.forClass(WmSn.class);
        verify(baseMapper, times(2)).insert(captor.capture());

        List<WmSn> insertedSns = captor.getAllValues();
        // 由于已有SN202606010005，下一个序号应从6开始
        assertTrue(insertedSns.get(0).getSnCode().endsWith("0006"));
        assertTrue(insertedSns.get(1).getSnCode().endsWith("0007"));
    }

    /**
     * 绑定库存 - SN状态应变为IN_STOCK
     */
    @Test
    void testBindStock() {
        WmSn unboundSn = new WmSn();
        unboundSn.setSnId(1L);
        unboundSn.setSnCode("SN202606010001");
        unboundSn.setMaterialId(100L);
        unboundSn.setStatus("OUT");
        unboundSn.setStockId(null);

        when(baseMapper.selectById(1L)).thenReturn(unboundSn);
        when(baseMapper.updateById(any(WmSn.class))).thenReturn(1);

        wmSnService.bindStock(1L, 300L, 400L, 500L);

        // 捕获updateById参数，验证状态和字段
        ArgumentCaptor<WmSn> captor = ArgumentCaptor.forClass(WmSn.class);
        verify(baseMapper).updateById(captor.capture());

        WmSn updated = captor.getValue();
        assertEquals("IN_STOCK", updated.getStatus());
        assertEquals(300L, updated.getStockId());
        assertEquals(400L, updated.getWarehouseId());
        assertEquals(500L, updated.getLocationId());
    }

    /**
     * 绑定库存 - SN已绑定时应抛出异常
     */
    @Test
    void testBindStock_AlreadyBound() {
        WmSn boundSn = new WmSn();
        boundSn.setSnId(1L);
        boundSn.setStockId(300L);

        when(baseMapper.selectById(1L)).thenReturn(boundSn);

        ServiceException exception = assertThrows(ServiceException.class, () ->
            wmSnService.bindStock(1L, 301L, 400L, 500L)
        );
        assertTrue(exception.getMessage().contains("已绑定"));
    }

    /**
     * 解绑库存 - SN状态应变为OUT
     */
    @Test
    void testUnbindStock() {
        WmSn boundSn = new WmSn();
        boundSn.setSnId(1L);
        boundSn.setSnCode("SN202606010001");
        boundSn.setStatus("IN_STOCK");
        boundSn.setStockId(300L);
        boundSn.setWarehouseId(400L);
        boundSn.setLocationId(500L);

        when(baseMapper.selectById(1L)).thenReturn(boundSn);
        when(baseMapper.updateById(any(WmSn.class))).thenReturn(1);

        wmSnService.unbindStock(1L);

        // 捕获updateById参数，验证状态和字段清空
        ArgumentCaptor<WmSn> captor = ArgumentCaptor.forClass(WmSn.class);
        verify(baseMapper).updateById(captor.capture());

        WmSn updated = captor.getValue();
        assertEquals("OUT", updated.getStatus());
        assertNull(updated.getStockId());
        assertNull(updated.getWarehouseId());
        assertNull(updated.getLocationId());
    }

    /**
     * 解绑库存 - SN未绑定时应抛出异常
     */
    @Test
    void testUnbindStock_NotBound() {
        WmSn unboundSn = new WmSn();
        unboundSn.setSnId(1L);
        unboundSn.setStockId(null);

        when(baseMapper.selectById(1L)).thenReturn(unboundSn);

        ServiceException exception = assertThrows(ServiceException.class, () ->
            wmSnService.unbindStock(1L)
        );
        assertTrue(exception.getMessage().contains("未绑定"));
    }

    /**
     * 变更序列号状态
     */
    @Test
    void testChangeStatus() {
        when(baseMapper.selectById(1L)).thenReturn(existingSn);
        when(baseMapper.updateById(any(WmSn.class))).thenReturn(1);

        wmSnService.changeStatus(1L, "SCRAP");

        ArgumentCaptor<WmSn> captor = ArgumentCaptor.forClass(WmSn.class);
        verify(baseMapper).updateById(captor.capture());

        assertEquals("SCRAP", captor.getValue().getStatus());
    }

    /**
     * 变更状态 - 序列号不存在时应抛出异常
     */
    @Test
    void testChangeStatus_SnNotFound() {
        when(baseMapper.selectById(anyLong())).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class, () ->
            wmSnService.changeStatus(999L, "SCRAP")
        );
        assertTrue(exception.getMessage().contains("不存在"));
    }

    /**
     * 批量生成 - 数量小于等于0应抛出异常
     */
    @Test
    void testBatchGenerate_InvalidQuantity() {
        ServiceException exception = assertThrows(ServiceException.class, () ->
            wmSnService.batchGenerate(100L, 0, 1L, 200L, "PO", "PO001")
        );
        assertTrue(exception.getMessage().contains("大于0"));

        assertThrows(ServiceException.class, () ->
            wmSnService.batchGenerate(100L, -1, 1L, 200L, "PO", "PO001")
        );
    }
}

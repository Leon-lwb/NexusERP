package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存冻结/解冻 单元测试（审计 MES-X-01）
 * <p>核心断言: 解冻必须把 frozen_flag 条件更新为 0; 无批次号时冻结回退按批次ID定位。</p>
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WmMaterialStockServiceImplTest {

    @Mock
    private WmMaterialStockMapper baseMapper;
    @Mock
    private BdMaterialMapper bdMaterialMapper;
    @Mock
    private WmMaterialReceiveLineMapper materialReceiveLineMapper;
    @InjectMocks
    private WmMaterialStockServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 真实 TableInfo/列缓存, 让 MyBatis-Plus lambda 在纯单测中正常解析列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialReceiveLine.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(WmMaterialStock.class);
        TableInfoHelper.remove(WmMaterialReceiveLine.class);
    }

    @BeforeEach
    void injectBaseMapper() throws Exception {
        // @InjectMocks 不注入 ServiceImpl 继承来的 baseMapper(不同 MyBatis-Plus 版本字段声明位置不同),
        // 这里按类型沿类层次查找并反射赋值
        Class<?> type = service.getClass();
        while (type != null && type != Object.class) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (field.getType().isAssignableFrom(WmMaterialStockMapper.class)) {
                    field.setAccessible(true);
                    field.set(service, baseMapper);
                    return;
                }
            }
            type = type.getSuperclass();
        }
        throw new IllegalStateException("未找到 baseMapper 字段, 无法注入 mock");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void unfreezeByBatch_writesFrozenFlagBackToZero() {
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        int rows = service.unfreezeByBatch(100L, "B-001", "IQC合格解冻");

        assertEquals(1, rows);
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) captor.getValue();
        assertTrue(String.valueOf(wrapper.getSqlSet()).contains("frozen_flag"), "解冻必须更新 frozen_flag 列");
        assertTrue(wrapper.getParamNameValuePairs().containsValue(0L), "MES-X-01: 解冻必须把 frozen_flag 写回 0");
        assertTrue(String.valueOf(wrapper.getSqlSegment()).contains("frozen_flag"),
            "解冻必须带 frozen_flag=1 的幂等守卫(仅解冻当前冻结行)");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void unfreezeByReceive_usesBatchCodesFromReceiveLines() {
        WmMaterialReceiveLine line = new WmMaterialReceiveLine();
        line.setReceiveId(9L);
        line.setMaterialId(100L);
        line.setBatchCode("B-001");
        when(materialReceiveLineMapper.selectList(any())).thenReturn(List.of(line));
        when(baseMapper.update(isNull(), any())).thenReturn(2);

        int rows = service.unfreezeByReceive(9L, 100L, "让步接收解冻");

        assertEquals(2, rows);
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) captor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(0L));
        assertTrue(String.valueOf(wrapper.getSqlSegment()).contains("batch_code"), "应按收货行批次号定位库存行");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void freezeStockByReceiveBatches_incrementsVersion() {
        WmMaterialReceiveLine line = new WmMaterialReceiveLine();
        line.setReceiveId(9L);
        line.setMaterialId(100L);
        line.setBatchCode("B-001");
        when(materialReceiveLineMapper.selectList(any())).thenReturn(List.of(line));
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        int rows = service.freezeStockByReceiveBatches(9L, 100L);

        assertEquals(1, rows);
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) captor.getValue();
        assertTrue(String.valueOf(wrapper.getSqlSet()).contains("version = version + 1"),
            "ERP-WM-09: 冻结必须递增 version, 否则乐观锁失效——在途出库仍能用旧 version 扣减被冻结库存");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void unfreezeByBatch_incrementsVersion() {
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        service.unfreezeByBatch(100L, "B-001", "IQC合格解冻");

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) captor.getValue();
        assertTrue(String.valueOf(wrapper.getSqlSet()).contains("version = version + 1"),
            "ERP-WM-09: 解冻同样必须递增 version, 使在途的旧版本扣减立即失效");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void freezeStockByReceiveBatches_fallsBackToBatchIdWhenNoBatchCode() {
        WmMaterialReceiveLine line = new WmMaterialReceiveLine();
        line.setReceiveId(9L);
        line.setMaterialId(100L);
        line.setBatchId(77L);
        when(materialReceiveLineMapper.selectList(any())).thenReturn(List.of(line));
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        int rows = service.freezeStockByReceiveBatches(9L, 100L);

        assertEquals(1, rows, "MES-X-01: 无批次号时应按批次ID定位库存行冻结, 不再直接返回 0");
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) captor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L), "冻结必须写 frozen_flag=1");
        assertTrue(String.valueOf(wrapper.getSqlSegment()).contains("batch_id"), "应按批次ID定位库存行");
    }
}

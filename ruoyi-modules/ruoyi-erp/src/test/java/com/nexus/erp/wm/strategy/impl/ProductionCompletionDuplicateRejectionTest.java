package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 工单已有库存流水时，策略不能静默返回并让另一入库单标成执行成功。 */
class ProductionCompletionDuplicateRejectionTest {
    @Test
    void priorStockEffectCannotBecomeAnotherReceiptSuccess() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WmMaterialStockTx.class);
        WmMaterialStockTxMapper tx = mock(WmMaterialStockTxMapper.class);
        when(tx.selectCount(any())).thenReturn(1L);
        Constructor<?> constructor = ProductionCompletionStrategy.class.getConstructors()[0];
        Class<?>[] types = constructor.getParameterTypes();
        Object[] dependencies = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            dependencies[i] = types[i] == WmMaterialStockTxMapper.class ? tx : mock(types[i]);
        }
        ProductionCompletionStrategy strategy = (ProductionCompletionStrategy) constructor.newInstance(dependencies);
        assertThrows(ServiceException.class, () -> strategy.execute(201L, "SCGD202610070099", 301L,
            "IF20261007099", "连接组件", new BigDecimal("6"), 3L, 28L, 401L, "B202610070099", new Date()),
            "存在旧流水时必须拒绝；原回执恢复应由完整上层凭据守卫处理");
        verify(tx, never()).insert(any(WmMaterialStockTx.class));
    }
}
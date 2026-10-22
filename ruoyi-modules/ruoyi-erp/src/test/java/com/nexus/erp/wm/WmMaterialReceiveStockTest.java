package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.impl.WmMaterialReceiveExecutorServiceImpl;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 入库执行器：库存唯一键 / 库存查询 的 SN 维度单元测试（问题③回归）
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class WmMaterialReceiveStockTest {

    @Mock
    private WmMaterialStockMapper materialStockMapper;
    @Mock
    private WmMaterialStockTxMapper materialStockTxMapper;

    @InjectMocks
    private WmMaterialReceiveExecutorServiceImpl executor;

    /**
     * 预热 MyBatis-Plus Lambda 缓存。
     * <p>单一 Mockito 单元测试无 Spring 上下文，实体 mapper 是 mock，不会自动注册 TableInfo；
     * 校验 selectOneStock 查询条件需调用 {@code getSqlSegment()}，必须手动 initTableInfo 才能解析 lambda 列名。</p>
     */
    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
    }

    /** 同批次同库位、不同 SN 的库存必须落成不同的行（唯一键含 snId） */
    @Test
    void buildStockKey_differsBySnId() throws Exception {
        WmMaterialStock s1 = stockWithSn(100L, 9L, 2L, 77L);
        WmMaterialStock s2 = stockWithSn(100L, 9L, 2L, 78L);
        WmMaterialStock s3 = stockWithSn(100L, 9L, 2L, null);

        String k1 = buildStockKey(s1);
        String k2 = buildStockKey(s2);
        String k3 = buildStockKey(s3);

        assertNotEquals(k1, k2, "不同SN必须生成不同库存唯一键");
        assertNotEquals(k1, k3, "有SN与无SN必须生成不同库存唯一键");
        assertNotNull(k1);
    }

    /** selectOneStock 必须按 snId 过滤，避免命中其他 SN 的库存行 */
    @Test
    void selectOneStock_filtersBySnId() throws Exception {
        WmMaterialStock condition = stockWithSn(100L, 9L, 2L, 77L);
        when(materialStockMapper.selectOne(any())).thenReturn(new WmMaterialStock());

        Method m = WmMaterialReceiveExecutorServiceImpl.class
            .getDeclaredMethod("selectOneStock", WmMaterialStock.class);
        m.setAccessible(true);
        m.invoke(executor, condition);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<WmMaterialStock>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(materialStockMapper).selectOne(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("sn_id"), "查询条件必须包含 sn_id，实际: " + sql);
    }

    private WmMaterialStock stockWithSn(Long materialId, Long batchId, Long warehouseId, Long snId) {
        WmMaterialStock s = new WmMaterialStock();
        s.setMaterialId(materialId);
        s.setBatchId(batchId);
        s.setWarehouseId(warehouseId);
        s.setSnId(snId);
        s.setStockQuantity(new BigDecimal("1"));
        return s;
    }

    private String buildStockKey(WmMaterialStock stock) throws Exception {
        Method m = WmMaterialReceiveExecutorServiceImpl.class.getDeclaredMethod("buildStockKey", WmMaterialStock.class);
        m.setAccessible(true);
        return (String) m.invoke(executor, stock);
    }

    /** 库存事务必须记录 SN（问题⑥回归） */
    @Test
    void recordStockTransaction_persistsSn() throws Exception {
        WmMaterialReceiveVo receive = new WmMaterialReceiveVo();
        receive.setReceiveId(1L);
        receive.setReceiveCode("CGRK20260816001");
        receive.setReceiveDate(new Date());

        WmMaterialStock stock = new WmMaterialStock();
        stock.setMaterialId(100L);
        stock.setMaterialCode("MAT001");
        stock.setBatchId(9L);
        stock.setBatchCode("B202608160001");
        stock.setSnId(77L);
        stock.setSnCode("SN202608160001");
        stock.setWarehouseId(2L);
        stock.setLocationId(6L);
        stock.setRecptDate(new Date());

        Method m =
                WmMaterialReceiveExecutorServiceImpl.class.getDeclaredMethod(
                        "recordStockTransaction",
                        WmMaterialReceiveVo.class,
                        WmMaterialStock.class,
                        int.class,
                        BigDecimal.class,
                        BigDecimal.class,
                        BigDecimal.class,
                        Long.class,
                        String.class);
        m.setAccessible(true);
        m.invoke(executor, receive, stock, 1, new BigDecimal("1"),
            BigDecimal.ZERO, new BigDecimal("1"), 5L, "TARGET_IN");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WmMaterialStockTx> captor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(materialStockTxMapper).insert(captor.capture());
        WmMaterialStockTx tx = captor.getValue();
        assertEquals(77L, tx.getSnId(), "库存事务必须落 sn_id");
        assertEquals("SN202608160001", tx.getSnCode(), "库存事务必须落 sn_code");
    }
}
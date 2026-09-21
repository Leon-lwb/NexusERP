package com.nexus.erp.pu;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.service.impl.PuPurchaseReceiveWritebackServiceImpl;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购收货回写单元测试（ERP-PU-01 超收守卫 / ERP-PU-02 幂等键可重试）。
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PuPurchaseReceiveWritebackServiceImplTest {

    private static final String IDEMPOTENT_KEY = "erp:writeback:receive:1";

    @Mock
    private PuPurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    @Mock
    private WmMaterialReceiveLineMapper receiveLineMapper;

    @InjectMocks
    private PuPurchaseReceiveWritebackServiceImpl writebackService;

    private static MockedStatic<RedisUtils> redisUtilsMock;

    @BeforeAll
    static void initStatics() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PuPurchaseOrderLine.class);
        TableInfoHelper.initTableInfo(assistant, PuPurchaseOrder.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialReceiveLine.class);
    }

    /**
     * RedisUtils 静态桩必须按用例开启/关闭。
     * <p>若在 {@code @BeforeAll} 里建立，桩与调用记录会跨用例泄漏：
     * {@code writeBack_alreadyWritten_skips} 的 {@code getCacheObject -> "1"} 会让后续用例
     * 被误判为「已回写过」而直接跳过，导致零交互。</p>
     */
    @BeforeEach
    void openRedisMock() {
        redisUtilsMock = mockStatic(RedisUtils.class);
        // 默认：未回写过
        lenient().when(RedisUtils.getCacheObject(anyString())).thenReturn(null);
    }

    @AfterEach
    void closeRedisMock() {
        if (redisUtilsMock != null) {
            redisUtilsMock.close();
        }
    }

    /** ERP-PU-01：已收 50 + 本次 60 > 订单量 100（容差 0）→ 拒绝回写且不落库 */
    @Test
    void writeBack_overReceive_rejected() {
        WmMaterialReceiveLine receiveLine = receiveLine(11L, new BigDecimal("60"));
        when(receiveLineMapper.selectList(any())).thenReturn(List.of(receiveLine));

        PuPurchaseOrderLine poLine = orderLine(11L, new BigDecimal("100"), new BigDecimal("50"));
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(poLine));
        when(purchaseOrderLineMapper.selectOne(any())).thenReturn(poLine);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> writebackService.writeBackReceivedQty(1L, 100L));
        assertTrue(ex.getMessage().contains("超过"), ex.getMessage());
        verify(purchaseOrderLineMapper, never()).update(any(), any());
        verify(purchaseOrderMapper, never()).update(any(), any());
    }

    /** ERP-PU-02：回写失败后幂等键不得落库（否则 24h 内永久跳过回写，无法重试） */
    @Test
    void writeBack_failed_doesNotOccupyIdempotentKey() {
        WmMaterialReceiveLine receiveLine = receiveLine(11L, new BigDecimal("60"));
        when(receiveLineMapper.selectList(any())).thenReturn(List.of(receiveLine));

        PuPurchaseOrderLine poLine = orderLine(11L, new BigDecimal("100"), new BigDecimal("50"));
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(poLine));
        when(purchaseOrderLineMapper.selectOne(any())).thenReturn(poLine);

        assertThrows(ServiceException.class, () -> writebackService.writeBackReceivedQty(1L, 100L));
        redisUtilsMock.verify(() -> RedisUtils.setObjectIfAbsent(anyString(), any(), any()), never());
    }

    /** ERP-PU-01/02：已收 50 + 本次 50 = 订单量 100 → 允许回写，且成功后才写幂等键 */
    @Test
    void writeBack_exactQuantity_succeedsAndWritesIdempotentKey() {
        WmMaterialReceiveLine receiveLine = receiveLine(11L, new BigDecimal("50"));
        when(receiveLineMapper.selectList(any())).thenReturn(List.of(receiveLine));

        PuPurchaseOrderLine poLine = orderLine(11L, new BigDecimal("100"), new BigDecimal("50"));
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(poLine));
        when(purchaseOrderLineMapper.selectOne(any())).thenReturn(poLine);
        when(purchaseOrderLineMapper.update(any(), any())).thenReturn(1);
        when(purchaseOrderMapper.update(any(), any())).thenReturn(1);

        writebackService.writeBackReceivedQty(1L, 100L);

        verify(purchaseOrderLineMapper, times(1)).update(any(), any());
        redisUtilsMock.verify(
            () -> RedisUtils.setObjectIfAbsent(anyString(), any(), any()), times(1));
    }

    /** ERP-PU-02：purId 为空必须抛异常让 MQ 重投，不能静默 return 并占位 */
    @Test
    void writeBack_blankPurId_throwsInsteadOfSilentSkip() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> writebackService.writeBackReceivedQty(1L, null));
        assertTrue(ex.getMessage().contains("采购订单ID为空"), ex.getMessage());
        redisUtilsMock.verify(() -> RedisUtils.setObjectIfAbsent(anyString(), any(), any()), never());
    }

    /** ERP-PU-06：同一物料在订单中有多行时给出可读错误，而不是 TooManyResultsException */
    @Test
    void writeBack_ambiguousMaterialLine_throwsReadableError() {
        WmMaterialReceiveLine receiveLine = receiveLine(null, new BigDecimal("10"));
        when(receiveLineMapper.selectList(any())).thenReturn(List.of(receiveLine));
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(
            orderLine(11L, new BigDecimal("100"), BigDecimal.ZERO),
            orderLine(12L, new BigDecimal("100"), BigDecimal.ZERO)));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> writebackService.writeBackReceivedQty(1L, 100L));
        assertTrue(ex.getMessage().contains("多行"), ex.getMessage());
    }

    /** 幂等：已回写过的收货单直接跳过 */
    @Test
    void writeBack_alreadyWritten_skips() {
        when(RedisUtils.getCacheObject(IDEMPOTENT_KEY)).thenReturn("1");
        writebackService.writeBackReceivedQty(1L, 100L);
        verify(receiveLineMapper, never()).selectList(any());
    }

    private WmMaterialReceiveLine receiveLine(Long purchaseLineId, BigDecimal quantityReceived) {
        WmMaterialReceiveLine line = new WmMaterialReceiveLine();
        line.setLineId(1L);
        line.setReceiveId(1L);
        line.setPurchaseLineId(purchaseLineId);
        line.setMaterialId(500L);
        line.setQuantityReceived(quantityReceived);
        return line;
    }

    private PuPurchaseOrderLine orderLine(Long lineId, BigDecimal orderQty, BigDecimal receivedQty) {
        PuPurchaseOrderLine line = new PuPurchaseOrderLine();
        line.setLineId(lineId);
        line.setOrderId(100L);
        line.setMaterialId(500L);
        line.setOrderQuantity(orderQty);
        line.setReceivedQuantity(receivedQty);
        return line;
    }

    /** 占位：确保 LambdaQueryWrapper 泛型可用（避免 IDE 未使用告警误删 import） */
    @SuppressWarnings("unused")
    private LambdaQueryWrapper<PuPurchaseOrderLine> unusedWrapper() {
        return new LambdaQueryWrapper<>();
    }

    /** 断言工具：BigDecimal 等值比较（供后续扩展用例使用） */
    @SuppressWarnings("unused")
    private void assertDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}

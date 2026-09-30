package com.nexus.erp.wm.service.mq;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.service.IPuPurchaseRequisitionService;
import com.nexus.erp.wm.service.IWmSpareStockPostingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 2026-09-30 P1：采购消息必须在 Redis 占位/业务写入前明确暂停。
 * 静态依赖全部隔离；不连接数据库、Redis、RabbitMQ，也不启动 Spring 服务。
 */
@Tag("dev")
class MesSpareErpConsumerTest {

    private static final String TENANT_ID = "000123";
    private static final String BIZ_KEY = "EQSP:000123:42";
    private static final String DEDUP_KEY = "mes:spare:pr:consumed:" + BIZ_KEY;
    private static final String RETRY_KEY = "mes:spare:erp:retry:" + BIZ_KEY;

    private final Map<String, Object> redisState = new HashMap<>();
    private IWmSpareStockPostingService stockPostingService;
    private IPuPurchaseRequisitionService purchaseRequisitionService;
    private MesSpareErpConsumer consumer;
    private MockedStatic<SpringUtil> spring;
    private MockedStatic<RedisUtils> redis;
    private MockedStatic<TenantHelper> tenant;

    @BeforeEach
    void setUp() {
        stockPostingService = mock(IWmSpareStockPostingService.class);
        purchaseRequisitionService = mock(IPuPurchaseRequisitionService.class);
        consumer = new MesSpareErpConsumer(stockPostingService, purchaseRequisitionService);

        /** RedisUtils 的静态 CLIENT 必须取到 mock，避免初始化时依赖真实 Spring/Redis。 */
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(mock(RedissonClient.class));
        redis = mockStatic(RedisUtils.class);
        tenant = mockStatic(TenantHelper.class);
        tenant.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        });

        /** 保留旧实现会产生的 Redis 副作用，令 RED 真正暴露先占位后失败的漏洞。 */
        redis.when(() -> RedisUtils.setObjectIfAbsent(anyString(), any(), any(Duration.class)))
            .thenAnswer(invocation -> redisState.putIfAbsent(invocation.getArgument(0), invocation.getArgument(1)) == null);
        redis.when(() -> RedisUtils.incrAtomicValue(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = ((Number) redisState.getOrDefault(key, 0L)).longValue() + 1;
            redisState.put(key, next);
            return next;
        });
        redis.when(() -> RedisUtils.deleteObject(anyString()))
            .thenAnswer(invocation -> redisState.remove(invocation.getArgument(0)) != null);
    }

    @AfterEach
    void closeStatics() {
        if (tenant != null) {
            tenant.close();
        }
        if (redis != null) {
            redis.close();
        }
        if (spring != null) {
            spring.close();
        }
    }

    @Test
    void purchaseIsExplicitlyPausedBeforeTenantRedisOrDraftCreation() {
        when(purchaseRequisitionService.insertByBo(any(PuPurchaseRequisitionBo.class))).thenReturn(1);

        assertPaused(purchasePayload());

        assertPurchaseHasNoSideEffects();
    }

    @Test
    void serverPolicyRejectionCannotLeaveASuccessDedupKey() {
        when(purchaseRequisitionService.insertByBo(any(PuPurchaseRequisitionBo.class)))
            .thenThrow(new ServiceException("未接入来源账，拒绝采购申请"));

        assertThrows(RuntimeException.class, () -> consumer.onMessage(purchasePayload()));

        assertFalse(redisState.containsKey(DEDUP_KEY), "服务端拒绝不得留下已成功去重键");
        assertPurchaseHasNoSideEffects();
    }

    @Test
    void draftWriteFailureCannotLeaveASuccessDedupKey() {
        when(purchaseRequisitionService.insertByBo(any(PuPurchaseRequisitionBo.class)))
            .thenThrow(new IllegalStateException("draft write failed"));

        assertThrows(RuntimeException.class, () -> consumer.onMessage(purchasePayload()));

        assertFalse(redisState.containsKey(DEDUP_KEY), "写异常不得留下已成功去重键");
        assertPurchaseHasNoSideEffects();
    }

    @Test
    void redeliveriesAfterARejectionNeverReturnAsIfADraftExists() {
        when(purchaseRequisitionService.insertByBo(any(PuPurchaseRequisitionBo.class)))
            .thenThrow(new ServiceException("未接入来源账，拒绝采购申请"));
        Map<String, Object> payload = purchasePayload();

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(RuntimeException.class, () -> consumer.onMessage(payload),
                "每次重投都必须失败，不能因 Redis 占位或重试上限正常返回");
        }

        assertFalse(redisState.containsKey(DEDUP_KEY));
        assertPurchaseHasNoSideEffects();
    }

    @Test
    void legacyDedupPlaceholderCannotSilentlyAcknowledgeAPausedPurchase() {
        redisState.put(DEDUP_KEY, "1");

        assertPaused(purchasePayload());

        assertEquals("1", redisState.get(DEDUP_KEY), "不自动清理线上历史键，只禁止据此确认成功");
        assertPurchaseHasNoSideEffects();
    }

    @Test
    void exhaustedStockRetryCounterCannotTurnAPausedPurchaseIntoSuccess() {
        redisState.put(RETRY_KEY, 3L);
        when(purchaseRequisitionService.insertByBo(any(PuPurchaseRequisitionBo.class)))
            .thenThrow(new ServiceException("未接入来源账，拒绝采购申请"));

        assertPaused(purchasePayload());

        assertEquals(3L, redisState.get(RETRY_KEY));
        assertPurchaseHasNoSideEffects();
    }

    @Test
    void zeroRowsFromDraftWriterCannotBeAcknowledgedDuringPause() {
        when(purchaseRequisitionService.insertByBo(any(PuPurchaseRequisitionBo.class))).thenReturn(0);

        assertPaused(purchasePayload());

        assertPurchaseHasNoSideEffects();
    }

    @Test
    void purchaseWithNoLinesStillFailsBeforeAnyDedupReservation() {
        Map<String, Object> payload = purchasePayload();
        payload.put("lines", List.of());

        assertPaused(payload);

        assertPurchaseHasNoSideEffects();
    }

    @Test
    void purchaseWithNoBizKeyStillFailsRatherThanBeingSilentlyDiscarded() {
        Map<String, Object> payload = purchasePayload();
        payload.remove("bizKey");

        assertPaused(payload);

        assertPurchaseHasNoSideEffects();
    }

    @Test
    void stockPostingStillPropagatesTenantAndClearsRetryOnSuccess() {
        redisState.put(RETRY_KEY, 2L);
        when(stockPostingService.postSpareStock(any(MesSpareErpMessage.class)))
            .thenReturn(new IWmSpareStockPostingService.WmSpareStockPostingResult(false, new BigDecimal("7")));

        assertDoesNotThrow(() -> consumer.onMessage(stockPayload()));

        ArgumentCaptor<MesSpareErpMessage> captor = ArgumentCaptor.forClass(MesSpareErpMessage.class);
        verify(stockPostingService).postSpareStock(captor.capture());
        MesSpareErpMessage message = captor.getValue();
        assertEquals(MesSpareErpMessage.BIZ_STOCK, message.getBizType());
        assertEquals(TENANT_ID, message.getTenantId());
        assertEquals(BIZ_KEY, message.getBizKey());
        assertEquals(11L, message.getMaterialId());
        assertEquals(22L, message.getWarehouseId());
        assertEquals("OUT", message.getTransactionType());
        assertEquals(new BigDecimal("2"), message.getQuantity());
        tenant.verify(() -> TenantHelper.dynamic(eq(TENANT_ID), any(Runnable.class)));
        redis.verify(() -> RedisUtils.deleteObject(RETRY_KEY));
        redis.verifyNoMoreInteractions();
        assertFalse(redisState.containsKey(RETRY_KEY));
        verifyNoInteractions(purchaseRequisitionService);
    }

    @Test
    void duplicateStockDeliveryStillDelegatesIdempotencyToStockPosting() {
        when(stockPostingService.postSpareStock(any(MesSpareErpMessage.class)))
            .thenReturn(new IWmSpareStockPostingService.WmSpareStockPostingResult(false, new BigDecimal("7")),
                new IWmSpareStockPostingService.WmSpareStockPostingResult(true, new BigDecimal("7")));

        assertDoesNotThrow(() -> consumer.onMessage(stockPayload()));
        assertDoesNotThrow(() -> consumer.onMessage(stockPayload()));

        verify(stockPostingService, times(2)).postSpareStock(any(MesSpareErpMessage.class));
        redis.verify(() -> RedisUtils.deleteObject(RETRY_KEY), times(2));
        redis.verifyNoMoreInteractions();
        verifyNoInteractions(purchaseRequisitionService);
    }

    @Test
    void stockFailureStillThrowsBelowTheOriginalRetryLimit() {
        RuntimeException failure = new ServiceException("库存过账失败");
        when(stockPostingService.postSpareStock(any(MesSpareErpMessage.class))).thenThrow(failure);

        RuntimeException actual = assertThrows(RuntimeException.class, () -> consumer.onMessage(stockPayload()));

        assertSame(failure, actual);
        assertEquals(1L, redisState.get(RETRY_KEY));
        redis.verify(() -> RedisUtils.incrAtomicValue(RETRY_KEY));
        redis.verify(() -> RedisUtils.expire(RETRY_KEY, Duration.ofHours(24)));
        redis.verifyNoMoreInteractions();
        verifyNoInteractions(purchaseRequisitionService);
    }

    @Test
    void stockFailureStillReturnsAtTheOriginalRetryLimit() {
        redisState.put(RETRY_KEY, 2L);
        when(stockPostingService.postSpareStock(any(MesSpareErpMessage.class)))
            .thenThrow(new ServiceException("库存过账失败"));

        assertDoesNotThrow(() -> consumer.onMessage(stockPayload()));

        assertEquals(3L, redisState.get(RETRY_KEY));
        redis.verify(() -> RedisUtils.incrAtomicValue(RETRY_KEY));
        redis.verify(() -> RedisUtils.expire(RETRY_KEY, Duration.ofHours(24)));
        redis.verifyNoMoreInteractions();
        verifyNoInteractions(purchaseRequisitionService);
    }

    @Test
    void stockWithoutTenantStillUsesTheCurrentContext() {
        Map<String, Object> payload = stockPayload();
        payload.remove("tenantId");
        when(stockPostingService.postSpareStock(any(MesSpareErpMessage.class)))
            .thenReturn(new IWmSpareStockPostingService.WmSpareStockPostingResult(false, BigDecimal.ONE));

        assertDoesNotThrow(() -> consumer.onMessage(payload));

        verify(stockPostingService).postSpareStock(any(MesSpareErpMessage.class));
        tenant.verifyNoInteractions();
        redis.verify(() -> RedisUtils.deleteObject(RETRY_KEY));
        redis.verifyNoMoreInteractions();
        verifyNoInteractions(purchaseRequisitionService);
    }

    @Test
    void invalidStockPayloadsStillReturnWithoutSideEffects() {
        assertDoesNotThrow(() -> consumer.onMessage(null));
        assertDoesNotThrow(() -> consumer.onMessage(Map.of("bizType", MesSpareErpMessage.BIZ_STOCK)));

        redis.verifyNoInteractions();
        tenant.verifyNoInteractions();
        verifyNoInteractions(stockPostingService, purchaseRequisitionService);
    }

    private void assertPaused(Map<String, Object> payload) {
        ServiceException failure = assertThrows(ServiceException.class, () -> consumer.onMessage(payload));
        assertTrue(failure.getMessage().contains("暂停"), failure.getMessage());
        assertTrue(failure.getMessage().contains("来源账"), failure.getMessage());
    }

    private void assertPurchaseHasNoSideEffects() {
        redis.verifyNoInteractions();
        tenant.verifyNoInteractions();
        verifyNoInteractions(stockPostingService, purchaseRequisitionService);
    }

    private Map<String, Object> purchasePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bizType", MesSpareErpMessage.BIZ_PURCHASE);
        payload.put("tenantId", TENANT_ID);
        payload.put("bizKey", BIZ_KEY);
        payload.put("lines", List.of(Map.of("materialId", "11", "applyQuantity", "2", "spareId", "42")));
        return payload;
    }

    private Map<String, Object> stockPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bizType", MesSpareErpMessage.BIZ_STOCK);
        payload.put("tenantId", TENANT_ID);
        payload.put("bizKey", BIZ_KEY);
        payload.put("materialId", "11");
        payload.put("warehouseId", "22");
        payload.put("transactionType", "OUT");
        payload.put("quantity", "2");
        return payload;
    }
}

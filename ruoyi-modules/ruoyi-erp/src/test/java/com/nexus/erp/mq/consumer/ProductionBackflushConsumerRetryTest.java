package com.nexus.erp.mq.consumer;

import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.service.ProductionCompletionPostingService;
import com.nexus.erp.mq.message.ProductionCompleteMessage;
import com.nexus.erp.prd.service.impl.PrdBackflushServiceImpl;
import com.nexus.erp.wm.domain.vo.PutawayLocation;
import com.nexus.erp.wm.service.IPutawayStrategyService;
import com.nexus.erp.wm.service.IWmProductionRecptService;
import com.rabbitmq.client.Channel;
import cn.hutool.extra.spring.SpringUtil;
import org.redisson.api.RedissonClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 完工失败重投不能被提前占用的缓存键冒充成功；仅使用内存缓存语义桩。 */
class ProductionBackflushConsumerRetryTest {
    private PrdBackflushServiceImpl backflush;
    private IWmProductionRecptService receipt;
    private IPutawayStrategyService putaway;
    private ProductionBackflushConsumer consumer;
    private ProductionCompleteMessage message;
    private final Map<String, Object> cache = new HashMap<>();

    /** 仅让静态工具类获得模拟客户端完成初始化，不创建真实 Redis 客户端。 */
    @BeforeAll
    static void initializeRedisWithoutConnection() throws Exception {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(mock(RedissonClient.class));
            Class.forName(RedisUtils.class.getName());
        }
    }

    @BeforeEach
    void setUp() {
        backflush = mock(PrdBackflushServiceImpl.class);
        receipt = mock(IWmProductionRecptService.class);
        putaway = mock(IPutawayStrategyService.class);
        ProductionCompletionPostingService posting = mock(ProductionCompletionPostingService.class);
        consumer = new ProductionBackflushConsumer(posting);
        // 此类仅验消费模板重试/ACK边界；持久验账与实际代理事务在 prd 专门测试。
        doAnswer(inv -> {
            ProductionCompleteMessage m = inv.getArgument(0);
            backflush.backflushOnCompletion(m.getWoId(), m.getCompletionQty());
            PutawayLocation target = putaway.resolvePutaway(m.getMaterialId(), m.getCompletionQty());
            receipt.createAndExecute(m.getWoId(), m.getWoNo(), m.getMaterialId(), m.getMaterialCode(), m.getMaterialName(),
                null, m.getCompletionQty(), target.getWarehouseId(), target.getWarehouseCode(), target.getWarehouseName(),
                target.getLocationId(), target.getLocationCode(), target.getLocationName(), new java.util.Date());
            return null;
        }).when(posting).postCompletion(any());
        message = new ProductionCompleteMessage();
        message.setMessageId("completion-retry");
        message.setTenantId(7L);
        message.setWoId(100L);
        message.setWoNo("WO-100");
        message.setMaterialId(200L);
        message.setMaterialCode("FG-200");
        message.setMaterialName("测试成品");
        message.setCompletionQty(new BigDecimal("10"));
        message.setMaxRetry(3);
        PutawayLocation target = new PutawayLocation();
        target.setWarehouseId(300L);
        target.setLocationId(400L);
        when(putaway.resolvePutaway(200L, message.getCompletionQty())).thenReturn(target);
    }

    /** SETNX 首次为真、占用后为假；失败不清键时第二次必须被测试抓住。 */
    private MockedStatic<RedisUtils> memoryRedis() {
        MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class);
        redis.when(() -> RedisUtils.setObjectIfAbsent(anyString(), any(), any(Duration.class)))
            .thenAnswer(inv -> cache.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
        redis.when(() -> RedisUtils.getCacheObject(anyString())).thenAnswer(inv -> cache.get(inv.getArgument(0)));
        redis.when(() -> RedisUtils.deleteObject(anyString())).thenAnswer(inv -> cache.remove(inv.getArgument(0)) != null);
        redis.when(() -> RedisUtils.setCacheObject(anyString(), any(), any(Duration.class)))
            .thenAnswer(inv -> { cache.put(inv.getArgument(0), inv.getArgument(1)); return null; });
        redis.when(() -> RedisUtils.incrAtomicValue(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            long next = ((Number) cache.getOrDefault(key, 0L)).longValue() + 1;
            cache.put(key, next);
            return next;
        });
        return redis;
    }

    private void failReceiptOnce(AtomicInteger attempts, AtomicInteger posted) {
        when(receipt.createAndExecute(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("生产入库失败");
            posted.incrementAndGet();
            return 900L;
        });
    }

    @Test
    void receiptFailureMustNotPoisonRetryForNormalWorkOrder() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger posted = new AtomicInteger();
        failReceiptOnce(attempts, posted);
        try (MockedStatic<RedisUtils> ignored = memoryRedis()) {
            assertThrows(IllegalStateException.class, () -> consumer.doProcess(message));
            assertDoesNotThrow(() -> consumer.doProcess(message));
            assertEquals(2, attempts.get(), "失败后的同载荷重投必须再次尝试入库，而不是直接返回成功");
            assertEquals(1, posted.get());
        }
    }

    @Test
    void putawayFailureMustRemainRetryable() {
        AtomicInteger posted = new AtomicInteger();
        when(putaway.resolvePutaway(200L, message.getCompletionQty()))
            .thenThrow(new IllegalStateException("成品上架规则缺失"))
            .thenReturn(new PutawayLocation());
        when(receipt.createAndExecute(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> { posted.incrementAndGet(); return 900L; });
        try (MockedStatic<RedisUtils> ignored = memoryRedis()) {
            assertThrows(IllegalStateException.class, () -> consumer.doProcess(message));
            assertDoesNotThrow(() -> consumer.doProcess(message));
            assertEquals(1, posted.get(), "上架失败后的重投必须真实调用生产入库");
        }
    }

    @Test
    void baseMustNotAckUnpostedProductionOnRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger posted = new AtomicInteger();
        failReceiptOnce(attempts, posted);
        Channel channel = mock(Channel.class);
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(11L);
        Message amqp = new Message(new byte[0], props);
        try (MockedStatic<RedisUtils> ignored = memoryRedis(); MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
            tenant.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class)))
                .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return null; });
            consumer.onMessage(message, channel, amqp);
            verify(channel).basicNack(11L, false, true);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
            assertFalse(cache.containsValue("DONE"));
            consumer.onMessage(message, channel, amqp);
            assertEquals(1, posted.get(), "第二次 ACK 必须以真实入库成功为前提");
            verify(channel).basicAck(11L, false);
            assertTrue(cache.containsValue("DONE"));
        }
    }
}

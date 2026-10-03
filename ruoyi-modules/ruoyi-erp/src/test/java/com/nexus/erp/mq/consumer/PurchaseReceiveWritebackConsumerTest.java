package com.nexus.erp.mq.consumer;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.mq.message.PurchaseReceiveWritebackMessage;
import com.nexus.erp.pu.service.IPuPurchaseReceiveWritebackService;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 真实强类型投递边界；Redis 传输状态绝不等于认证回执。 */
class PurchaseReceiveWritebackConsumerTest {
    @org.junit.jupiter.api.BeforeAll static void initializeRedisFacadeWithoutAServer() throws Exception {
        try(var spring=mockStatic(cn.hutool.extra.spring.SpringUtil.class)) {
            spring.when(()->cn.hutool.extra.spring.SpringUtil.getBean(org.redisson.api.RedissonClient.class))
                .thenReturn(mock(org.redisson.api.RedissonClient.class));
            Class.forName(RedisUtils.class.getName(),true,RedisUtils.class.getClassLoader());
        }
    }

    private final IPuPurchaseReceiveWritebackService writer=mock(IPuPurchaseReceiveWritebackService.class);
    private final IWmMaterialReceiveService warehouse=mock(IWmMaterialReceiveService.class);
    private final PurchaseReceiveWritebackConsumer consumer=consumer();

    // 通过发现构造方法，使此回归用例在新旧调度器装配方式下均可执行。
    private PurchaseReceiveWritebackConsumer consumer() {
        try {
            for(var ctor:PurchaseReceiveWritebackConsumer.class.getConstructors()) {
                if(ctor.getParameterCount()==1) return (PurchaseReceiveWritebackConsumer)ctor.newInstance(writer);
                if(ctor.getParameterCount()==2) return (PurchaseReceiveWritebackConsumer)ctor.newInstance(writer,warehouse);
            }
            throw new AssertionError("Consumer constructor missing");
        } catch(ReflectiveOperationException failure) {throw new AssertionError(failure);}
    }
    private PurchaseReceiveWritebackMessage message() {
        var m=new PurchaseReceiveWritebackMessage(); m.setReceiveId(77L);m.setMessageId("receipt-delivery-77");
        m.setTenantIdStr("000001");m.setTenantId(1L);return m;
    }
    private Message delivery() {
        var p=new MessageProperties();p.setDeliveryTag(17L);return new Message(new byte[0],p);
    }
    @Test void missingTenantMustNotFallBackToSystemTenant() {
        var m=message();m.setTenantIdStr(null);m.setTenantId(null);
        assertThrows(ServiceException.class,()->consumer.resolveTenantId(m));
    }
    @Test void numericTenantFallbackPreservesZeroPadding() {
        var m=message();m.setTenantIdStr(null);m.setTenantId(123L);
        assertEquals("000123",consumer.resolveTenantId(m));
        m.setTenantId(0L);assertEquals("000000",consumer.resolveTenantId(m));
    }
    @Test void typedStringAndNumericTenantMustAgree() {
        var m=message();m.setTenantId(2L);
        assertThrows(ServiceException.class,()->consumer.resolveTenantId(m));
    }
    @ParameterizedTest @ValueSource(strings={"", " ", " 000001", "000001 ", "000001\n"})
    void doesNotTrimOrReplaceMalformedTenantLabels(String tenant) {
        var m=message();m.setTenantIdStr(tenant);
        assertThrows(ServiceException.class,()->consumer.resolveTenantId(m));
    }
    @Test void numericTenantCannotBeNegative() {
        var m=message();m.setTenantIdStr(null);m.setTenantId(-1L);
        assertThrows(ServiceException.class,()->consumer.resolveTenantId(m));
    }
    @Test void dispatchesIdOnlyWithoutReadingWarehouseOutsideOwnedTransaction() {
        try(var tenant=mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true);
            tenant.when(TenantHelper::getTenantId).thenReturn("000001");
            consumer.doProcess(message());
            verify(writer).writeBackReceivedQty(77L,null);verifyNoInteractions(warehouse);
        }
    }
    @Test void contextMismatchIsNotAValidDelivery() {
        try(var tenant=mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true);
            tenant.when(TenantHelper::getTenantId).thenReturn("000002");
            assertThrows(ServiceException.class,()->consumer.doProcess(message()));
            verifyNoInteractions(writer,warehouse);
        }
    }
    @ParameterizedTest @ValueSource(strings={"DONE", "PENDING"})
    void redisTransportMarkersCannotSkipPermanentReceiptValidation(String transportMarker) throws IOException {
        Channel channel=mock(Channel.class);
        try(var redis=mockStatic(RedisUtils.class);var login=mockStatic(LoginHelper.class);
            var tenant=mockStatic(TenantHelper.class,CALLS_REAL_METHODS)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true);
            login.when(LoginHelper::isLogin).thenReturn(false);
            redis.when(()->RedisUtils.getCacheObject(anyString())).thenReturn(transportMarker);
            AtomicReference<String> inside=new AtomicReference<>();
            doAnswer(call->{inside.set(TenantHelper.getTenantId());return null;}).when(writer).writeBackReceivedQty(77L,null);
            TenantHelper.dynamic("outer-tenant",()->{
                try {consumer.processMessage(message(),channel,delivery());}catch(IOException e){throw new AssertionError(e);}
                assertEquals("outer-tenant",TenantHelper.getTenantId());
            });
            assertEquals("000001",inside.get());verify(channel).basicAck(17L,false);
            assertNull(TenantHelper.getDynamic());verifyNoInteractions(warehouse);
        }
    }
    @Test void unknownCommitResultIsNackedWithoutPublishingOrReleasingBusinessSources() throws IOException {
        Channel channel=mock(Channel.class);
        try(var redis=mockStatic(RedisUtils.class);var login=mockStatic(LoginHelper.class);
            var tenant=mockStatic(TenantHelper.class,CALLS_REAL_METHODS)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true);login.when(LoginHelper::isLogin).thenReturn(false);
            redis.when(()->RedisUtils.setObjectIfAbsent(anyString(),any(),any())).thenReturn(true);
            redis.when(()->RedisUtils.incrAtomicValue(anyString())).thenReturn(1L);
            doThrow(new ServiceException("结果无法确认")).when(writer).writeBackReceivedQty(77L,null);
            consumer.processMessage(message(),channel,delivery());
            verify(channel,never()).basicAck(anyLong(),anyBoolean());verify(channel).basicNack(17L,false,true);
            assertNull(TenantHelper.getDynamic());verifyNoInteractions(warehouse);
        }
    }
}

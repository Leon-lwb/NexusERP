package com.nexus.erp.mq.consumer;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.message.IqcCompletedMessage;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.service.*;
import com.nexus.erp.wm.service.WmReceiptIqcEvidenceService.Completion;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ErpIqcCompletedConsumerTest {
    final IWmMaterialReceiveService receiveService = mock(IWmMaterialReceiveService.class);
    final IWmArrivalNoticeLineService noticeService = mock(IWmArrivalNoticeLineService.class);
    final IWmBatchService batchService = mock(IWmBatchService.class);
    final IWmMaterialStockService stockService = mock(IWmMaterialStockService.class);
    final WmReceiptIqcEvidenceService evidenceService = mock(WmReceiptIqcEvidenceService.class);
    final ErpIqcCompletedConsumer consumer = new ErpIqcCompletedConsumer(receiveService, noticeService,
        batchService, stockService, evidenceService);
    IqcCompletedMessage message() {
        IqcCompletedMessage message = new IqcCompletedMessage();
        message.setTenantId(7L); message.setMessageId("event-501"); message.setIqcId(501L);
        message.setReceiveId(101L); message.setMaterialId(201L);
        message.setIqcCode("client-forged-code"); message.setInspectResult("QUALIFIED");
        message.setQuantity(new BigDecimal("999")); return message;
    }
    WmMaterialReceive receive() {
        WmMaterialReceive receive = new WmMaterialReceive();
        receive.setReceiveId(101L); receive.setTenantId("000007"); receive.setNoticeId(401L);
        when(receiveService.getById(101L)).thenReturn(receive);
        when(receiveService.updateById(any())).thenReturn(1); return receive;
    }
    void actualUnqualified() {
        doAnswer(i -> {
            i.<Consumer<Completion>>getArgument(1).accept(new Completion(501L, "IQC-501", 101L, 201L,
                "UNQUALIFIED", new BigDecimal("12.0000"))); return null;
        }).when(evidenceService).consumeCompleted(any(), any());
    }

    @Test void sourceDerivedCompletionDrivesEveryErpProjectionNotEnvelopeFields() {
        WmMaterialReceive receive = receive(); actualUnqualified();
        consumer.doProcess(message());
        assertThat(receive.getIqcCode()).isNull(); assertThat(receive.getIqcStatus()).isNull();
        verify(receiveService, never()).updateById(any());
        verify(noticeService).updateIqcResult(401L, 201L, 501L, "IQC-501", "UNQUALIFIED");
        verifyNoInteractions(batchService, stockService);
        verify(evidenceService).consumeCompleted(any(), any());
    }
    @Test void sourceFailureCannotWriteAnyErpBusinessProjection() {
        doThrow(new ServiceException("IQC_SOURCE_UNAVAILABLE")).when(evidenceService).consumeCompleted(any(), any());
        assertThatThrownBy(() -> consumer.doProcess(message())).isInstanceOf(ServiceException.class);
        verifyNoInteractions(receiveService, noticeService, batchService, stockService);
    }
    @Test void missingReceiptProjectionFailsRatherThanAcknowledgeCertificationAsSuccess() {
        actualUnqualified();
        assertThatThrownBy(() -> consumer.doProcess(message())).isInstanceOf(ServiceException.class);
        verifyNoInteractions(noticeService, batchService, stockService);
    }
    @Test void bodyConflictIsRejectedBeforeRedisDoneShortcutCanAcknowledgeIt() throws Exception {
        Channel channel = mock(Channel.class);
        MessageProperties properties = new MessageProperties(); properties.setDeliveryTag(33L);
        Message amqp = new Message(new byte[0], properties);
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(mock(RedissonClient.class));
            try (var redis = mockStatic(RedisUtils.class); var tenant = mockStatic(TenantHelper.class)) {
                redis.when(() -> RedisUtils.getCacheObject(anyString())).thenReturn("DONE");
                tenant.when(() -> TenantHelper.dynamic(eq("000007"), any(Runnable.class))).thenAnswer(i -> {
                    i.<Runnable>getArgument(1).run(); return null;
                });
                doThrow(new ServiceException("IQC_EVIDENCE_IMMUTABLE_CONFLICT"))
                    .when(evidenceService).consumeCompleted(any(), any());
                consumer.onMessage(message(), channel, amqp);
                verify(channel).basicNack(33L, false, false);
                verify(channel, never()).basicAck(anyLong(), anyBoolean());
                verifyNoInteractions(receiveService, noticeService, batchService, stockService);
                redis.verifyNoInteractions();
            }
        }
    }
    @Test void validDoneNegativeReplayReappliesSafetyBeforeBaseConsumerAcknowledges() throws Exception {
        receive(); actualUnqualified();
        Channel channel = mock(Channel.class);
        MessageProperties properties = new MessageProperties(); properties.setDeliveryTag(34L);
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(mock(RedissonClient.class));
            try (var redis = mockStatic(RedisUtils.class); var tenant = mockStatic(TenantHelper.class)) {
                redis.when(() -> RedisUtils.getCacheObject(anyString())).thenReturn("DONE");
                tenant.when(() -> TenantHelper.dynamic(eq("000007"), any(Runnable.class))).thenAnswer(i -> {
                    i.<Runnable>getArgument(1).run(); return null;
                });
                consumer.onMessage(message(), channel, new Message(new byte[0], properties));
                verify(evidenceService).consumeCompleted(any(), any());
                verify(channel).basicAck(34L, false);
                verifyNoInteractions(stockService, batchService);
                verify(receiveService, never()).updateById(any());
                verify(stockService, never()).unfreezeByReceive(anyLong(), anyLong(), anyString());
            }
        }
    }

    @Test void actualUnqualifiedOnFinishedReceiptNeverTouchesOldHeadCrudBeforeStockFreeze() {
        WmMaterialReceive receive = receive(); receive.setStatus("FINISHED"); actualUnqualified();
        when(receiveService.updateById(any())).thenThrow(new ServiceException("posted head mutation forbidden"));
        consumer.doProcess(message());
        verify(receiveService, never()).updateById(any());
        verifyNoInteractions(stockService, batchService);
        verify(stockService, never()).unfreezeByReceive(anyLong(), anyLong(), anyString());
    }
}

package com.nexus.erp.wm;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.domain.BaseMessage;
import com.nexus.common.mq.domain.MessageRecord;
import com.nexus.common.mq.domain.MessageRecordMapper;
import com.nexus.common.mq.producer.MessageRecordService;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.mq.message.PurchaseReceiveWritebackMessage;

import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class WmCertifiedReceiptNotificationTest {
    RabbitTemplate rabbit;MessageRecordService records;ReliableMessageProducer producer;ConnectionFactory connection;
    @BeforeEach void setup(){rabbit=mock(RabbitTemplate.class);records=mock(MessageRecordService.class);connection=mock(ConnectionFactory.class);
        when(rabbit.getConnectionFactory()).thenReturn(connection);when(connection.isPublisherConfirms()).thenReturn(true);when(connection.isPublisherReturns()).thenReturn(true);
        when(rabbit.isMandatoryFor(any())).thenReturn(true);producer=new ReliableMessageProducer(rabbit,records,new ObjectMapper());
        doAnswer(i->{MessagePostProcessor processor=i.getArgument(3);processor.postProcessMessage(new Message(new byte[0],new MessageProperties()));
            CorrelationData correlation=i.getArgument(4);correlation.getFuture().complete(new CorrelationData.Confirm(true,null));return null;})
            .when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));}
    PurchaseReceiveWritebackMessage message(){var m=new PurchaseReceiveWritebackMessage();m.setReceiveId(101L);m.setTenantIdStr("000000");m.setSourceModule("wm");return m;}
    @Test void durableAcceptedRequiresPersistedRecordCorrelatedAckAndStrictStatus(){var m=message();assertDoesNotThrow(()->producer.sendDurable("exchange","route",m));
        var ordered=inOrder(records,rabbit);ordered.verify(records).saveSendingStrict(any());ordered.verify(rabbit).convertAndSend(eq("exchange"),eq("route"),eq(m),any(MessagePostProcessor.class),any(CorrelationData.class));ordered.verify(records).updateStatusStrict(m.getMessageId(),"SENT");}
    @Test void persistenceFailureIsNotSwallowedAndNeverPublishes(){doThrow(new IllegalStateException("private jdbc password")).when(records).saveSendingStrict(any());
        var failure=assertThrows(ServiceException.class,()->producer.sendDurable("exchange","route",message()));assertFalse(failure.getMessage().contains("password"));assertNull(failure.getCause());
        verify(rabbit,never()).convertAndSend(anyString(),anyString(),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));}
    @Test void brokerNackNeverMarksDurablyAccepted(){doAnswer(i->{CorrelationData c=i.getArgument(4);c.getFuture().complete(new CorrelationData.Confirm(false,"private broker info"));return null;}).when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));
        assertThrows(ServiceException.class,()->producer.sendDurable("exchange","route",message()));verify(records,never()).updateStatusStrict(anyString(),anyString());}
    @Test void returnedMessageIsNotSuccessfulEvenIfBrokerAcked(){doAnswer(i->{CorrelationData c=i.getArgument(4);c.setReturned(new ReturnedMessage(new Message(new byte[0],new MessageProperties()),312,"NO_ROUTE","exchange","route"));c.getFuture().complete(new CorrelationData.Confirm(true,null));return null;}).when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));
        assertThrows(ServiceException.class,()->producer.sendDurable("exchange","route",message()));verify(records,never()).updateStatusStrict(anyString(),anyString());}
    @Test void missingPublisherReturnsCannotPretendToProveRouting(){when(connection.isPublisherReturns()).thenReturn(false);assertThrows(ServiceException.class,()->producer.sendDurable("exchange","route",message()));verifyNoInteractions(records);}
    @Test void mandatoryExpressionCannotDisableActualMessageRouteProof(){when(rabbit.isMandatoryFor(any())).thenReturn(false);assertThrows(ServiceException.class,()->producer.sendDurable("exchange","route",message()));verify(records,never()).updateStatusStrict(anyString(),anyString());}

    @Test
    void zeroAffectedRecordCannotBecomeAcceptance() {
        MessageRecordMapper mapper = mock(MessageRecordMapper.class);
        MessageRecordService service = new MessageRecordService(mapper);
        when(mapper.insert(any(MessageRecord.class))).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.saveSendingStrict(new MessageRecord()));
    }

    @Test void certifiedNotificationMustUseFailurePropagatingDurableAcceptanceNotLegacySwallowingSend() throws Exception {
        assertDoesNotThrow(()->ReliableMessageProducer.class.getMethod("sendDurable",String.class,String.class,BaseMessage.class));
    }
}

package com.nexus.mes.common.mq.consumer;

import com.nexus.common.mq.message.SubcontractSnGenerateMessage;
import com.nexus.mes.pro.service.IProSnService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubcontractSerialCommandTest {
    @ParameterizedTest
    @ValueSource(strings = {"1.25", "0", "-1", "2147483648"})
    void malformedSerialQuantityCannotBeTruncatedOrAcknowledgedAsSuccess(String quantity) {
        var sn = mock(IProSnService.class);
        var consumer = new SubcontractSnGenerateConsumer(sn);
        var message = message(quantity);
        assertThrows(RuntimeException.class, () -> consumer.doProcess(message));
        verifyNoInteractions(sn);
    }

    @Test
    void retryUsesSameDurableMessageCommand() {
        var sn = mock(IProSnService.class);
        var consumer = new SubcontractSnGenerateConsumer(sn);
        var message = message("2.0000");
        consumer.doProcess(message);
        consumer.doProcess(message);
        verify(sn, times(2))
                .generateSnForSubcontract(
                        "MES:SUBCONTRACT:77:message-1", 77L, "SC77", 100L, "PCBA", "Board", 2);
    }

    SubcontractSnGenerateMessage message(String qty) {
        var message = new SubcontractSnGenerateMessage();
        message.setMessageId("message-1");
        message.setSubcontractId(77L);
        message.setSubcontractNo("SC77");
        message.setMaterialId(100L);
        message.setMaterialCode("PCBA");
        message.setMaterialName("Board");
        message.setQuantity(new BigDecimal(qty));
        return message;
    }
}

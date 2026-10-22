package com.nexus.erp.wm.service.mq;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.domain.DeadLetterRecord;
import com.nexus.common.mq.domain.DeadLetterRecordMapper;
import com.nexus.common.web.core.BigNumberSerializer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag("dev")
@ExtendWith(MockitoExtension.class)
class MesSparePurchasePauseAuditServiceTest {
    @Mock
    private DeadLetterRecordMapper mapper;

    @Test
    void retainsOriginalDecodedPayloadInPendingAuditBeforeConsumerAcknowledgement() throws Exception {
        when(mapper.insert(any(DeadLetterRecord.class))).thenAnswer(invocation -> {
            invocation.<DeadLetterRecord>getArgument(0).setId(123L);
            return 1;
        });
        Map<String, Object> payload = payload();
        ObjectMapper json = new ObjectMapper();
        assertEquals(123L, new MesSparePurchasePauseAuditService(mapper, json).persist(payload));
        ArgumentCaptor<DeadLetterRecord> row = ArgumentCaptor.forClass(DeadLetterRecord.class);
        verify(mapper).insert(row.capture());
        assertEquals("PENDING", row.getValue().getStatus());
        assertEquals(QueueConstants.MES_SPARE_ERP_QUEUE, row.getValue().getRoutingKey());
        assertEquals("", row.getValue().getExchange());
        assertEquals(LinkedHashMap.class.getName(), row.getValue().getMessageType());
        assertEquals(json.readTree(json.writeValueAsString(payload)), json.readTree(row.getValue().getMessageBody()));
        assertEquals(64, row.getValue().getMessageId().length());
        assertTrue(row.getValue().getErrorMessage().startsWith("PAUSED_SOURCE_CONTRACT:"));
        assertNotNull(row.getValue().getCreateTime());
    }

    @Test
    void callerTransactionMustBeSuspendedAndAuditCommittedInMasterIndependently() throws Exception {
        var method = MesSparePurchasePauseAuditService.class.getMethod("persist", Map.class);
        DS ds = method.getAnnotation(DS.class);
        assertNotNull(ds);
        assertEquals("master", ds.value());
        Transactional tx = method.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation());
        assertTrue(List.of(tx.rollbackFor()).contains(Exception.class));
    }

    @Test
    void zeroRowsOrMissingGeneratedIdNeverReportsDurableSuccess() {
        when(mapper.insert(any(DeadLetterRecord.class))).thenReturn(0);
        var service = new MesSparePurchasePauseAuditService(mapper, new ObjectMapper());
        assertThrows(ServiceException.class, () -> service.persist(payload()));
        verify(mapper).insert(any(DeadLetterRecord.class));
    }

    @Test
    void insertedRowWithoutGeneratedIdDoesNotAuthorizeAcknowledgement() {
        when(mapper.insert(any(DeadLetterRecord.class))).thenReturn(1);
        var service = new MesSparePurchasePauseAuditService(mapper, new ObjectMapper());
        assertThrows(ServiceException.class, () -> service.persist(payload()));
        verify(mapper).insert(any(DeadLetterRecord.class));
    }

    @Test
    void injectedGlobalSnowflakeSerializerIsPreservedInsteadOfReplacedByLocalMapper() throws Exception {
        when(mapper.insert(any(DeadLetterRecord.class))).thenAnswer(invocation -> {
            invocation.<DeadLetterRecord>getArgument(0).setId(123L);
            return 1;
        });
        ObjectMapper global = new ObjectMapper();
        var module = new SimpleModule();
        module.addSerializer(Long.class, BigNumberSerializer.instance);
        global.registerModule(module);
        var original = new LinkedHashMap<>(payload());
        original.put("materialId", 2100000000000000123L);
        new MesSparePurchasePauseAuditService(mapper, global).persist(original);
        ArgumentCaptor<DeadLetterRecord> row = ArgumentCaptor.forClass(DeadLetterRecord.class);
        verify(mapper).insert(row.capture());
        var value = global.readTree(row.getValue().getMessageBody()).path("materialId");
        assertTrue(value.isTextual());
        assertEquals("2100000000000000123", value.textValue());
    }
    @Test
    void databaseFailureIsNotSwallowedIntoAcknowledgementPermission() {
        when(mapper.insert(any(DeadLetterRecord.class))).thenThrow(new IllegalStateException("database unavailable"));
        var service = new MesSparePurchasePauseAuditService(mapper, new ObjectMapper());
        assertThrows(IllegalStateException.class, () -> service.persist(payload()));
    }

    @Test
    void oversizedOrNonPurchasePayloadIsNotTruncatedAndNotRecordedAsSuccessful() {
        var service = new MesSparePurchasePauseAuditService(mapper, new ObjectMapper());
        assertThrows(ServiceException.class, () -> service.persist(Map.of("bizType", "SPARE_STOCK")));
        var oversized = new LinkedHashMap<>(payload());
        oversized.put("remark", "a".repeat(65536));
        assertThrows(ServiceException.class, () -> service.persist(oversized));
        verifyNoInteractions(mapper);
    }

    private Map<String, Object> payload() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bizType", "SPARE_PURCHASE");
        row.put("tenantId", "000123");
        row.put("bizKey", "EQSP-PR:000123:42");
        row.put("lines", List.of(Map.of("materialId", "2100000000000000123", "applyQuantity", "5.4292")));
        return row;
    }
}
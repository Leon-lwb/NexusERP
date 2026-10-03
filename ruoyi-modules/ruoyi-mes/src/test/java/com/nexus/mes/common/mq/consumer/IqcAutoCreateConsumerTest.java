package com.nexus.mes.common.mq.consumer;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.message.ReceiveApprovedMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.qc.controller.QcIqcController;
import com.nexus.mes.qc.domain.bo.QcIqcBo;
import com.nexus.mes.qc.domain.vo.QcIqcVo;
import com.nexus.mes.qc.service.IQcIqcService;
import com.nexus.mes.qc.service.QcReceiptIqcBirthService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 直接执行实际入口方法，无数据库、Redis 或 RabbitMQ 连接。 */
class IqcAutoCreateConsumerTest {
    @Test void messageQuantityAndNamesNeverBecomeNativePersistenceInput() {
        var writer = mock(QcReceiptIqcBirthService.class);
        var message = new ReceiveApprovedMessage(); message.setReceiveId(101L); message.setMaterialId(201L);
        message.setQuantity(new BigDecimal("-999999999999.123456789")); message.setMaterialCode("伪造物料");
        message.setMaterialName("伪造名称"); message.setVendorId(999L); message.setVendorName("伪造供应商");
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000007");
            new IqcAutoCreateConsumer(writer).doProcess(message);
            verify(writer).create("000007", 101L, 201L); verifyNoMoreInteractions(writer);
        }
    }
    @Test void legacyUnitFailurePropagatesForMqRetryRatherThanAcknowledgingFalseSuccess() {
        var writer = mock(QcReceiptIqcBirthService.class);
        when(writer.create("000007", 101L, 201L)).thenThrow(new ServiceException("UNIT_ORIGIN_UNPROVEN"));
        var message = new ReceiveApprovedMessage(); message.setReceiveId(101L); message.setMaterialId(201L);
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000007");
            assertThatThrownBy(() -> new IqcAutoCreateConsumer(writer).doProcess(message)).hasMessage("UNIT_ORIGIN_UNPROVEN");
            verify(writer).create("000007", 101L, 201L);
        }
    }
    @Test void manualReceiptCreationRunsNativeDispatcherAndIgnoresUntrustedBoQuantity() {
        var legacy = mock(IQcIqcService.class); var writer = mock(QcReceiptIqcBirthService.class);
        var view = new QcIqcVo(); view.setIqcId(501L); when(writer.create("000007", 101L, 201L)).thenReturn(view);
        var bo = new QcIqcBo(); bo.setReceiveId(101L); bo.setMaterialId(201L); bo.setQuantity(new BigDecimal("999999"));
        bo.setInspectResult("QUALIFIED"); bo.setStatus("COMPLETED"); bo.setMaterialName("伪造名称");
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000007");
            assertThat(new QcIqcController(legacy, writer).add(bo).getData()).isSameAs(view);
            verify(writer).create("000007", 101L, 201L); verifyNoInteractions(legacy);
        }
    }
    @Test void unlinkedManualInspectionStillUsesLegacyCreationWithoutUnitCertification() {
        var legacy = mock(IQcIqcService.class); var writer = mock(QcReceiptIqcBirthService.class);
        var bo = new QcIqcBo(); bo.setMaterialId(201L); var view = new QcIqcVo();
        when(legacy.insertByBo(bo)).thenReturn(view);
        assertThat(new QcIqcController(legacy, writer).add(bo).getData()).isSameAs(view);
        verify(legacy).insertByBo(bo); verifyNoInteractions(writer);
    }
}

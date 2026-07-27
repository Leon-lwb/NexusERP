package com.nexus.mes.common.mq.consumer;

import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.pro.service.IProWorkOrderService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderReleaseConsumerTest {

    @Mock
    private IProWorkOrderService proWorkOrderService;
    @Mock
    private Channel channel;
    @InjectMocks
    private WorkOrderReleaseConsumer consumer;

    private MockedStatic<TenantHelper> tenantHelperMock;

    @BeforeEach
    void setUp() {
        tenantHelperMock = mockStatic(TenantHelper.class);
    }

    @AfterEach
    void tearDown() {
        if (tenantHelperMock != null) tenantHelperMock.close();
    }

    @Test
    void doProcessRestoresTenantContextAndCallsCreateFromErpMessage() {
        WorkOrderReleaseMessage message = new WorkOrderReleaseMessage();
        message.setMessageId("msg-001");
        message.setTenantId(1L);
        message.setWoId(100L);
        message.setWoNo("WO-20250727-001");
        message.setMaterialCode("MAT-001");
        message.setPlannedQty(java.math.BigDecimal.TEN);

        // Mock TenantHelper.dynamic to actually execute the runnable
        tenantHelperMock.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class)))
            .thenAnswer(invocation -> {
                String tenantId = invocation.getArgument(0);
                Runnable runnable = invocation.getArgument(1);
                assertEquals("000001", tenantId);
                runnable.run();
                return null;
            });

        consumer.doProcess(message);

        verify(proWorkOrderService).createFromErpMessage(message);
    }

    @Test
    void doProcessUsesDefaultTenantWhenTenantIdIsNull() {
        WorkOrderReleaseMessage message = new WorkOrderReleaseMessage();
        message.setMessageId("msg-002");
        message.setTenantId(null);
        message.setWoId(100L);

        tenantHelperMock.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class)))
            .thenAnswer(invocation -> {
                String tenantId = invocation.getArgument(0);
                assertEquals("000000", tenantId);
                Runnable runnable = invocation.getArgument(1);
                runnable.run();
                return null;
            });

        consumer.doProcess(message);

        verify(proWorkOrderService).createFromErpMessage(message);
    }

    @Test
    void doProcessUsesDefaultTenantWhenTenantIdIsZero() {
        WorkOrderReleaseMessage message = new WorkOrderReleaseMessage();
        message.setMessageId("msg-003");
        message.setTenantId(0L);
        message.setWoId(100L);

        tenantHelperMock.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class)))
            .thenAnswer(invocation -> {
                String tenantId = invocation.getArgument(0);
                assertEquals("000000", tenantId);
                Runnable runnable = invocation.getArgument(1);
                runnable.run();
                return null;
            });

        consumer.doProcess(message);

        verify(proWorkOrderService).createFromErpMessage(message);
    }

    @Test
    void doProcessFormatsTenantIdCorrectly() {
        // Test with tenantId = 1 -> "000001"
        WorkOrderReleaseMessage message = new WorkOrderReleaseMessage();
        message.setMessageId("msg-005");
        message.setTenantId(1L);
        message.setWoId(100L);

        tenantHelperMock.when(() -> TenantHelper.dynamic(eq("000001"), any(Runnable.class)))
            .thenAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(1);
                runnable.run();
                return null;
            });

        consumer.doProcess(message);

        verify(proWorkOrderService).createFromErpMessage(message);
    }

    @Test
    void doProcessHandlesNonZeroTenantId() {
        // Test with tenantId = 123L -> "000123"
        WorkOrderReleaseMessage message = new WorkOrderReleaseMessage();
        message.setMessageId("msg-006");
        message.setTenantId(123L);
        message.setWoId(100L);

        tenantHelperMock.when(() -> TenantHelper.dynamic(eq("000123"), any(Runnable.class)))
            .thenAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(1);
                runnable.run();
                return null;
            });

        consumer.doProcess(message);

        verify(proWorkOrderService).createFromErpMessage(message);
    }

    @Test
    void doProcessCallsServiceWithSameMessage() {
        WorkOrderReleaseMessage message = new WorkOrderReleaseMessage();
        message.setMessageId("msg-007");
        message.setTenantId(5L);
        message.setWoId(200L);
        message.setWoNo("WO-002");

        tenantHelperMock.when(() -> TenantHelper.dynamic(eq("000005"), any(Runnable.class)))
            .thenAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(1);
                runnable.run();
                return null;
            });

        consumer.doProcess(message);

        verify(proWorkOrderService).createFromErpMessage(message);
    }
}
package com.nexus.erp.mq.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.mq.message.IpqcCompletedMessage;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.service.IPrdWorkOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** ERP IPQC 消费源时间回归，直接执行业务入口，不连接数据库、Redis 或 MQ。 */
@ExtendWith(MockitoExtension.class)
class ErpIpqcCompletedConsumerTest {
    private static final long SOURCE_TIME = 1_790_000_000_000L;
    @Mock private IPrdWorkOrderService workOrderService;
    @InjectMocks private ErpIpqcCompletedConsumer consumer;
    private final ObjectMapper json = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PENDING", "QUALIFIED"})
    void qualifiedInspectionUsesSourceTimeInsteadOfConsumptionOrMessageTimestamp(String currentStatus) throws Exception {
        prepare(currentStatus);
        IpqcCompletedMessage message = message("QUALIFIED", SOURCE_TIME);
        message.setTimestamp(SOURCE_TIME + 5_050_000L);

        consumer.doProcess(message);

        assertWriteUsesSourceTime("QUALIFIED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "UNQUALIFIED"})
    void unqualifiedInspectionKeepsStickyStatusAndUsesSourceTime(String currentStatus) throws Exception {
        prepare(currentStatus);

        consumer.doProcess(message("UNQUALIFIED", SOURCE_TIME));

        assertWriteUsesSourceTime("UNQUALIFIED");
    }

    @Test
    void qualifiedInspectionCannotOverwriteExistingUnqualifiedStatus() throws Exception {
        prepare("UNQUALIFIED");

        consumer.doProcess(message("QUALIFIED", SOURCE_TIME));

        verify(workOrderService).selectPrdWorkOrderByWoId(33L);
        verifyNoMoreInteractions(workOrderService);
    }

    @Test
    void legacyMessageWithoutSourceTimeIsRejectedEvenWithValidTimestamp() throws Exception {
        prepareLenient();
        IpqcCompletedMessage message = message("QUALIFIED", null);
        message.setTimestamp(System.currentTimeMillis());

        assertThrows(IllegalArgumentException.class, () -> consumer.doProcess(message),
            "旧消息必须明确失败，不能用发送时间或消费时间兜底");

        verifyNoInteractions(workOrderService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MAX_VALUE})
    void invalidSourceTimeIsRejectedBeforeAnyWorkOrderReadOrWrite(long sourceTime) throws Exception {
        prepareLenient();
        IpqcCompletedMessage message = message("QUALIFIED", sourceTime);

        assertThrows(IllegalArgumentException.class, () -> consumer.doProcess(message),
            "无效或未来的源时间必须沿原异常路径进入重试");

        verifyNoInteractions(workOrderService);
    }

    @Test
    void missingWorkOrderRetainsOriginalNoWriteBehaviorForValidMessage() throws Exception {
        consumer.doProcess(message("QUALIFIED", SOURCE_TIME));

        verify(workOrderService).selectPrdWorkOrderByWoId(33L);
        verifyNoMoreInteractions(workOrderService);
    }

    private void prepare(String currentStatus) {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(33L);
        wo.setWoNo("原工单");
        wo.setQualityStatus(currentStatus);
        when(workOrderService.selectPrdWorkOrderByWoId(33L)).thenReturn(wo);
    }

    private void prepareLenient() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setQualityStatus("PENDING");
        lenient().when(workOrderService.selectPrdWorkOrderByWoId(33L)).thenReturn(wo);
    }

    /** JSON 注入源字段使缺少新 getter 的基线也可编译，RED 由实际消费行为产生。 */
    private IpqcCompletedMessage message(String result, Long sourceTime) throws Exception {
        String source = sourceTime == null ? "" : ",\"inspectTime\":" + sourceTime;
        return json.readValue("{\"ipqcId\":11,\"ipqcNo\":\"原IPQC\",\"erpWoId\":33,"
            + "\"inspectResult\":\"" + result + "\",\"inspectorId\":7,"
            + "\"inspectorName\":\"原检验员\",\"tenantId\":123" + source + "}", IpqcCompletedMessage.class);
    }

    private void assertWriteUsesSourceTime(String result) {
        var time = ArgumentCaptor.forClass(Date.class);
        verify(workOrderService).updateIpqcStatus(eq(33L), eq(result), eq(11L), eq("原IPQC"),
            eq(7L), eq("原检验员"), time.capture());
        assertEquals(SOURCE_TIME, time.getValue().getTime(), "ERP 必须记录原检验时间");
    }
}

package com.nexus.mes.common.mq.consumer;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.mes.pro.service.IProWorkOrderService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消费端租户解析 fail-closed 测试(MES-MQ-01)
 * <p>
 * 核心断言: 消息缺失 tenantId 时<b>不会</b>退化为系统租户 000000 落库, 而是显式失败
 * (抛 {@link ServiceException} -> processMessage 捕获后重投/进死信)。
 * <p>
 * 说明: processMessage 级别的用例需要静态桩 RedisUtils, 而 RedisUtils 的静态初始化依赖真实
 * Redis 客户端(单测环境无法 instrument), 故此处直接断言租户解析这一 fail-closed 关键路径。
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class BaseMessageConsumerTenantTest {

    @Mock
    private IProWorkOrderService proWorkOrderService;

    private ProbeConsumer consumer() {
        return new ProbeConsumer(proWorkOrderService);
    }

    private WorkOrderReleaseMessage message(String messageId, Long tenantId) {
        WorkOrderReleaseMessage message = new WorkOrderReleaseMessage();
        message.setMessageId(messageId);
        message.setWoId(100L);
        message.setWoNo("WO-20260920-001");
        message.setTenantId(tenantId);
        return message;
    }

    @Test
    void resolveTenantIdFormatsRealTenant() {
        assertEquals("000001", consumer().resolveTenantId(message("msg-1", 1L)));
        assertEquals("000123", consumer().resolveTenantId(message("msg-2", 123L)));
    }

    @Test
    void resolveTenantIdThrowsWhenTenantIdIsNull() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> consumer().resolveTenantId(message("msg-null", null)));
        assertTrue(ex.getMessage().contains("缺少租户信息"), ex.getMessage());
        assertTrue(ex.getMessage().contains("拒绝以系统租户"), ex.getMessage());
        assertFalse(ex.getMessage().contains("tenantId=000000"), "不得把缺租户解析为 000000: " + ex.getMessage());
    }

    @Test
    void resolveTenantIdMapsExplicitZeroToSystemTenant() {
        // 生产端显式 setTenantId(0) 表示默认租户 '000000'(Long 无法保留前导零), 不得被误判为缺失而进死信
        assertEquals("000000", consumer().resolveTenantId(message("msg-zero", 0L)));
    }

    @Test
    void resolveTenantIdUsesSubclassFallbackBeforeFailingClosed() {
        // 子类可覆盖 resolveMissingTenantId 提供回退解析(如按 ERP 工单号反查真实租户)
        ProbeConsumer consumer = new ProbeConsumer(proWorkOrderService, "000123");
        assertEquals("000123", consumer.resolveTenantId(message("msg-fallback", null)));
    }

    /**
     * 探针子类: 把基类 protected 的 resolveTenantId 提升为 public 便于断言, 并支持注入回退解析结果
     */
    private static class ProbeConsumer extends WorkOrderReleaseConsumer {

        private final String fallbackTenantId;

        ProbeConsumer(IProWorkOrderService proWorkOrderService) {
            this(proWorkOrderService, null);
        }

        ProbeConsumer(IProWorkOrderService proWorkOrderService, String fallbackTenantId) {
            super(proWorkOrderService);
            this.fallbackTenantId = fallbackTenantId;
        }

        @Override
        public String resolveTenantId(WorkOrderReleaseMessage message) {
            return super.resolveTenantId(message);
        }

        @Override
        protected String resolveMissingTenantId(WorkOrderReleaseMessage message) {
            return fallbackTenantId;
        }
    }
}

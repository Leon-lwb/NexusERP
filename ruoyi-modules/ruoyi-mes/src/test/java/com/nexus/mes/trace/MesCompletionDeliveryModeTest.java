package com.nexus.mes.trace;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.mes.pro.domain.ProWorkOrder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证真实属性注入与持久化组件集合，不启动业务数据库或消息传输任务。
 */
class MesCompletionDeliveryModeTest {
    MesTraceBusinessTest b;
    AnnotationConfigApplicationContext app;
    final TrackingPolicy quantity = new TrackingPolicy(false, false, true, 1L, TrackingStage.AUDIT);
    final TrackingPolicy serial = new TrackingPolicy(false, true, true, 1L, TrackingStage.STRICT);

    @BeforeEach
    void setup() {
        b = new MesTraceBusinessTest();
        b.setup();
        when(b.policies.policy("000001", 100L)).thenReturn(quantity);
        when(b.policies.policy("000001", 200L)).thenReturn(quantity);
    }

    @AfterEach
    void cleanup() {
        if (app != null) {
            app.close();
        }
        b.cleanup();
    }

    MesCompletionManifestService configured(String enabled) {
        app = new AnnotationConfigApplicationContext();
        if (enabled != null) {
            app.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "case", Map.of("nexus.trace.relay.enabled", enabled)));
        }
        app.registerBean(MesCompletionManifestService.class, () -> b.manifests);
        app.refresh();
        return app.getBean(MesCompletionManifestService.class);
    }

    ProWorkOrder wo() {
        var wo = new ProWorkOrder();
        wo.setTenantId("000001");
        wo.setMesWoId(10L);
        wo.setErpWoId(20L);
        wo.setMaterialId(100L);
        return wo;
    }

    boolean mode(String enabled) {
        var service = configured(enabled);
        return b.inTx(() -> service.usesOutbox(wo()));
    }

    @Test
    void missingPropertyKeepsPlainQuantityBusinessOnLegacy() {
        assertFalse(mode(null));
        assertEquals(
                0, b.jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox", Integer.class));
    }

    @Test
    void explicitFalseKeepsPlainQuantityProductAndBomOnLegacy() {
        assertFalse(mode("false"));
    }

    @Test
    void trueUsesOutboxForQuantityBusinessAsWell() {
        assertTrue(mode("true"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SERIAL", "BATCH", "BATCH_SERIAL"})
    void disabledRelayNeverFallsBackForAnyTrackedProduct(String mode) {
        when(b.policies.policy("000001", 100L))
                .thenReturn(
                        new TrackingPolicy(
                                mode.contains("BATCH"),
                                mode.contains("SERIAL"),
                                true,
                                1L,
                                TrackingStage.AUDIT));
        var error = assertThrows(ServiceException.class, () -> mode("false"));
        assertTrue(error.getMessage().contains("nexus.trace.relay.enabled"));
    }

    @Test
    void trackedBomComponentCannotTakeQuantityFallback() {
        when(b.policies.policy("000001", 200L)).thenReturn(serial);
        assertThrows(ServiceException.class, () -> mode("false"));
    }

    @Test
    void trackedActualComponentMissingFromBomCannotTakeQuantityFallback() {
        b.jdbc.update(
                "INSERT INTO"
                    + " lt_wo_consumption(consumption_id,tenant_id,wo_id,material_id,consumed_qty)"
                    + " VALUES(1,'000001',10,201,1)");
        when(b.policies.policy("000001", 201L)).thenReturn(serial);
        assertThrows(ServiceException.class, () -> mode("false"));
    }

    @Test
    void deletedActualComponentDoesNotEraseTheTrackingGate() {
        b.jdbc.update(
                "INSERT INTO"
                    + " lt_wo_consumption(consumption_id,tenant_id,wo_id,material_id,consumed_qty,del_flag)"
                    + " VALUES(1,'000001',10,201,1,'1')");
        when(b.policies.policy("000001", 201L)).thenReturn(serial);
        assertThrows(ServiceException.class, () -> mode("false"));
    }

    @Test
    void realHistoricalSnComponentBindingsAlsoPreventQuantityFallback() {
        b.sn(1, "IN_PRODUCTION");
        b.jdbc.update("INSERT INTO lt_sn_binding VALUES(1,'000001',1,201,8)");
        when(b.policies.policy("000001", 201L)).thenReturn(serial);
        assertThrows(ServiceException.class, () -> mode("false"));
    }

    @Test
    void anotherTenantsSameNumericWorkOrderDoesNotSupplyComponents() {
        b.jdbc.update(
                "INSERT INTO"
                    + " lt_wo_consumption(consumption_id,tenant_id,wo_id,material_id,consumed_qty)"
                    + " VALUES(1,'000002',10,201,1)");
        b.jdbc.update("INSERT INTO pro_work_order_bom_line VALUES(2,'000002',10,201,1,30,0,'0')");
        assertFalse(mode("false"));
        verify(b.policies, never()).policy("000001", 201L);
    }

    @Test
    void missingPolicyIsNotEvidenceOfQuantityOnly() {
        when(b.policies.policy("000001", 100L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> mode("false"));
    }

    @Test
    void crossTenantWorkOrderIsRejected() {
        var service = configured("false");
        var wo = wo();
        wo.setTenantId("000002");
        assertThrows(ServiceException.class, () -> b.inTx(() -> service.usesOutbox(wo)));
    }

    @Test
    void invalidProductIdentityCannotDefaultToQuantityOnly() {
        var service = configured("false");
        var wo = wo();
        wo.setMaterialId(null);
        assertThrows(ServiceException.class, () -> b.inTx(() -> service.usesOutbox(wo)));
    }
}

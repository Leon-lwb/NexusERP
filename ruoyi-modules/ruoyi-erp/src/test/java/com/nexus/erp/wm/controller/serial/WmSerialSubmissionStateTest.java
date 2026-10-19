package com.nexus.erp.wm.controller.serial;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 提交凭据只取原过账组及ERP 所属业务库发件箱，首次提交和原命令重放保持一致。
 */
class WmSerialSubmissionStateTest {
    static Map<String, Object> event(long group, String status, String type) {
        var row = new HashMap<String, Object>();
        row.put("tenant_id", "000001");
        row.put("source_module", "ERP");
        row.put("aggregate_type", "STOCK_POSTING");
        row.put("aggregate_id", group);
        row.put("event_id", "ERP-" + type + "-" + group);
        row.put("status", status);
        row.put("last_error", "");
        return row;
    }

    @Test
    void firstPdaSuccessWithMissingEventIsServerCommittedAndRetainsOriginalPdaFields()
            throws Exception {
        var s = new WmSerialOfflineCommandReplayTest.Scenario();
        s.mvc.perform(
                        post(WmSerialOfflineCommandReplayTest.RECEIVE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(WmSerialOfflineCommandReplayTest.PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionState").value("SERVER_COMMITTED"))
                .andExpect(jsonPath("$.data.commandId").value("offline-receive-ACK-lost"))
                .andExpect(jsonPath("$.data.postingGroupIds[0]").value("99"))
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.nextAction").value("FINISHED"))
                .andExpect(jsonPath("$.data.submissionWarnings[0]").isNotEmpty());
    }

    @Test
    void firstVoidSuccessAndReplayUseOriginalGroupsAfterLaterTransfer() throws Exception {
        var s = new WmSerialOfflineCommandReplayTest.Scenario();
        s.commitLoseAckThenMove();
        s.f.outbox.add(event(99L, "APPLIED", "STOCK"));
        s.f.outbox.add(event(100L, "NEW", "STOCK"));
        s.mvc.perform(
                        post(WmSerialOfflineCommandReplayTest.RECEIVE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(WmSerialOfflineCommandReplayTest.PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionState").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.postingGroupIds[0]").value("99"))
                .andExpect(jsonPath("$.data.commandId").value("offline-receive-ACK-lost"));
        assertEquals(2, s.physicalPosts.get());
    }

    @Test
    void conflictAndBrokerSentStayPendingAndPreserveFailureReason() throws Exception {
        for (String state : List.of("NEW", "BROKER_SENT", "CONFLICT")) {
            var s = new WmSerialOfflineCommandReplayTest.Scenario();
            var row = event(99L, state, "SHIP");
            row.put("last_error", "原消费证明发生冲突，待核查");
            s.f.outbox.add(row);
            s.mvc.perform(
                            post(WmSerialOfflineCommandReplayTest.RECEIVE)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(WmSerialOfflineCommandReplayTest.PAYLOAD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.submissionState").value("CROSS_MODULE_PENDING"))
                    .andExpect(
                            jsonPath("$.data.submissionWarnings[0]")
                                    .value(org.hamcrest.Matchers.containsString("原消费证明发生冲突")));
        }
    }

    @Test
    void everyMaterialSourceEventMustBeAppliedBeforeConfirmed() throws Exception {
        var s = new WmSerialOfflineCommandReplayTest.Scenario();
        s.f.outbox.add(event(99L, "APPLIED", "MATERIAL-1"));
        s.f.outbox.add(event(99L, "BROKER_SENT", "MATERIAL-2"));
        s.mvc.perform(
                        post(WmSerialOfflineCommandReplayTest.RECEIVE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(WmSerialOfflineCommandReplayTest.PAYLOAD))
                .andExpect(jsonPath("$.data.submissionState").value("CROSS_MODULE_PENDING"));
        s.f.outbox.get(1).put("status", "APPLIED");
        s.mvc.perform(
                        post(WmSerialOfflineCommandReplayTest.RECEIVE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(WmSerialOfflineCommandReplayTest.PAYLOAD))
                .andExpect(jsonPath("$.data.submissionState").value("CONFIRMED"));
        verify(s.receive, times(1)).finish(10L);
        assertEquals(1, s.physicalPosts.get());
        verify(s.f.jdbc, atLeastOnce())
                .queryForList(
                        argThat(
                                sql ->
                                        sql.contains("FROM wm_trace_outbox")
                                                && sql.contains("tenant_id=?")
                                                && sql.contains("source_module='ERP'")
                                                && sql.contains("aggregate_type='STOCK_POSTING'")
                                                && !sql.contains("event_type=")),
                        any(Object[].class));
    }

    @Test
    void otherTenantOrModuleCannotProvideConfirmation() throws Exception {
        var s = new WmSerialOfflineCommandReplayTest.Scenario();
        var foreign = event(99L, "APPLIED", "STOCK");
        foreign.put("tenant_id", "000002");
        s.f.outbox.add(foreign);
        var mes = event(99L, "APPLIED", "STOCK");
        mes.put("source_module", "MES");
        s.f.outbox.add(mes);
        s.mvc.perform(
                        post(WmSerialOfflineCommandReplayTest.RECEIVE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(WmSerialOfflineCommandReplayTest.PAYLOAD))
                .andExpect(jsonPath("$.data.submissionState").value("SERVER_COMMITTED"));
    }

    @Test
    void firstVoidSuccessCarriesMetadataAndNoStockLookup() throws Throwable {
        var s = new WmSerialOfflineCommandReplayTest.Scenario();
        var f = s.f;
        Object result =
                f.adapter.invoke(
                        SerialBusinessRoute.ofType("WM_MATERIAL_RECEIVE"),
                        WmSerialOfflineCommandReplayTest.RECEIVE,
                        f.body(WmSerialOfflineCommandReplayTest.PAYLOAD),
                        "finish",
                        () -> {
                            s.receive.finish(10L);
                            return com.nexus.common.core.domain.R.ok();
                        });
        var data = (Map<?, ?>) ((com.nexus.common.core.domain.R<?>) result).getData();
        assertEquals("SERVER_COMMITTED", data.get("submissionState"));
        assertEquals(List.of("99"), data.get("postingGroupIds"));
        assertEquals("offline-receive-ACK-lost", data.get("commandId"));
        verify(f.repo, never()).stocks(anyLong());
    }

    @Test
    void oneMissingGroupCannotConfirmWholeCommandAndDetailsExposeSnapshot() throws Exception {
        var s = new WmSerialOfflineCommandReplayTest.Scenario();
        s.commitLoseAckThenMove();
        s.f.outbox.add(event(99L, "APPLIED", "STOCK"));
        var state = s.f.store.submission("root", List.of(99L, 100L));
        assertEquals("CROSS_MODULE_PENDING", state.get("submissionState"));
        assertEquals(List.of("99", "100"), state.get("postingGroupIds"));
        s.f.outbox.add(event(100L, "APPLIED", "STOCK"));
        assertEquals(
                "CONFIRMED",
                s.f.store.submission("root", List.of(99L, 100L)).get("submissionState"));
        assertEquals(
                "CONFIRMED",
                s.f.store
                        .detail("WM_MATERIAL_RECEIVE", 10L, 11L)
                        .submissionSnapshot()
                        .get("submissionState"));
    }

    @Test
    void ordinaryQuantityUrlKeepsVoidResponseWithoutInventedSubmissionMetadata() throws Throwable {
        var f = new SerialAdapterFixture();
        f.policy =
                new com.nexus.common.trace.TrackingPolicy(
                        false, false, false, 7L, com.nexus.common.trace.TrackingStage.AUDIT);
        var result =
                (com.nexus.common.core.domain.R<?>)
                        f.adapter.invoke(
                                SerialBusinessRoute.ofType("WM_TRANSFER"),
                                "/erp/wm/transfer/execute/10",
                                f.body("{}"),
                                "execute",
                                () -> com.nexus.common.core.domain.R.ok());
        assertNull(result.getData());
        verify(f.jdbc, never())
                .queryForList(
                        argThat(sql -> sql.contains("FROM wm_trace_outbox")), any(Object[].class));
    }
}

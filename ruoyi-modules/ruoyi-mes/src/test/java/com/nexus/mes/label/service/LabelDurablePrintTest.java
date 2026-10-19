package com.nexus.mes.label.service;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelPrintTask;
import com.nexus.mes.pro.service.MesProgressTransactionGuard;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 使用独立内存 H2 与真实 Spring 事务验证提交、回滚、租约及一次发送。
 */
class LabelDurablePrintTest extends LabelDurableTestSupport {
    @Test
    void committedBusinessTaskSurvivesBeforeSendAndAnotherWorkerDispatchesOnce() {
        com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder.push("mes");
        business.executeWithoutResult(
                tx -> {
                    service.submit(record("AUTO"), "FROZEN", StandardCharsets.UTF_8);
                    assertEquals(1, count("mes_label_print_task"));
                    assertEquals(0, sends.get());
                });
        com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder.poll();
        assertEquals("PENDING", onlyTask().getStatus());
        new LabelPrintDispatcher(store, transport).pollTenant();
        dispatcher.pollTenant();
        assertEquals(1, sends.get());
        assertEquals("SENT", onlyTask().getStatus());
        assertEquals(1, count("mes_label_print_log"));
    }

    @Test
    void rollbackRemovesTaskAndNeverSends() {
        com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder.push("mes");
        business.executeWithoutResult(
                tx -> {
                    service.submit(record("AUTO"), "FROZEN", StandardCharsets.UTF_8);
                    tx.setRollbackOnly();
                });
        assertEquals(0, count("mes_label_print_task"));
        dispatcher.pollTenant();
        assertEquals(0, sends.get());
    }

    @Test
    void duplicateAutomaticSubmissionKeepsFirstFrozenTaskAndOneSend() {
        com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder.push("mes");
        business.executeWithoutResult(
                tx -> {
                    service.submit(record("AUTO"), "ORIGINAL", StandardCharsets.UTF_8);
                    service.submit(record("AUTO"), "CHANGED", StandardCharsets.UTF_8);
                });
        com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder.poll();
        assertEquals(1, count("mes_label_print_task"));
        dispatcher.pollTenant();
        assertEquals("ORIGINAL", store.record(onlyTask()).getRenderedPayload());
        assertEquals(1, sends.get());
    }

    @Test
    void expiredSendingLeaseBecomesUnknownAndIsNeverAutomaticallyRetried() {
        MesLabelPrintTask pending =
                store.create(recordWithPayload("TEST", "FROZEN"), "crash-command", false);
        MesLabelPrintTask claimed = store.claim(pending.getTaskId(), "dead-worker", 120);
        assertNotNull(claimed);
        jdbc.update(
                "UPDATE mes_label_print_task SET lease_until=DATEADD('SECOND',-5,CURRENT_TIMESTAMP)"
                        + " WHERE task_id=?",
                pending.getTaskId());
        dispatcher.pollTenant();
        dispatcher.pollTenant();
        assertEquals("UNKNOWN", onlyTask().getStatus());
        assertEquals(1, onlyTask().getAttemptCount());
        assertEquals(0, sends.get());
        assertNull(store.claim(pending.getTaskId(), "another-worker", 120));
        assertEquals(
                "UNKNOWN",
                jdbc.queryForObject("SELECT result FROM mes_label_print_log", String.class));
        store.finish(
                pending.getTaskId(), "dead-worker", new LabelPrinterTransport.Result("SENT", null));
        assertEquals("UNKNOWN", onlyTask().getStatus(), "过期旧进程不得覆盖 UNKNOWN");
    }

    @Test
    void manualSubmissionIsCommittedBeforeNetworkAndDuplicateCommandDoesNotResend() {
        assertTrue(
                service.send(record("TEST"), "FROZEN", StandardCharsets.UTF_8, "manual-command"));
        assertTrue(
                service.send(record("TEST"), "FROZEN", StandardCharsets.UTF_8, "manual-command"));
        assertEquals(1, count("mes_label_print_task"));
        assertEquals(1, sends.get());
        assertEquals(1, onlyTask().getAttemptCount());
    }

    @Test
    void missingTaskStoragePreventsNetwork() {
        jdbc.execute("DROP TABLE mes_label_print_task");
        assertThrows(
                Exception.class,
                () -> service.send(record("TEST"), "FROZEN", StandardCharsets.UTF_8));
        assertEquals(0, sends.get());
    }

    @Test
    void automaticSubmissionRequiresBusinessMesTransaction() {
        assertThrows(
                Exception.class,
                () -> service.submit(record("AUTO"), "FROZEN", StandardCharsets.UTF_8));
        assertEquals(0, sends.get());
    }

    @Test
    void unknownRequiresNewManualTaskWithReasonAndOriginalReference() {
        MesLabelPrintTask original =
                store.create(recordWithPayload("TEST", "FROZEN"), "original-command", false);
        store.claim(original.getTaskId(), "dead", 120);
        jdbc.update(
                "UPDATE mes_label_print_task SET"
                        + " lease_until=DATEADD('SECOND',-5,CURRENT_TIMESTAMP)");
        store.recoverExpired();
        MesLabelPrintLog reprint =
                service.newReprintLog(
                        store.record(store.find(original.getTaskId())), "核实后补打", false);
        assertTrue(service.send(reprint, "FROZEN", StandardCharsets.UTF_8, "explicit-reprint"));
        assertEquals("UNKNOWN", store.find(original.getTaskId()).getStatus());
        assertEquals(2, count("mes_label_print_task"));
        assertEquals(1, sends.get());
    }

    @Test
    void rawReprintCannotBypassOriginalRecordCheck() {
        var reprint = record("REPRINT");
        reprint.setOriginalPrintLogId(999L);
        reprint.setReprintReason("污损");
        assertThrows(
                Exception.class, () -> service.send(reprint, "FROZEN", StandardCharsets.UTF_8));
        assertEquals(0, sends.get());
        assertEquals(0, count("mes_label_print_task"));
    }

    @Test
    void rawAutomaticSendCannotEscapeBusinessTransactionRequirement() {
        assertThrows(
                Exception.class,
                () -> service.send(record("AUTO"), "FROZEN", StandardCharsets.UTF_8));
        assertEquals(0, sends.get());
    }

    @Test
    void reprintCannotRunAlongsideItsPendingOriginal() {
        var original = store.create(recordWithPayload("TEST", "FROZEN"), "pending-original", false);
        var reprint = service.newReprintLog(store.record(original), "污损", false);
        assertThrows(
                Exception.class, () -> service.send(reprint, "FROZEN", StandardCharsets.UTF_8));
        assertEquals(0, sends.get());
    }

    @Test
    void finishStorageFailureDoesNotResendAndRecoversAsUnknown() throws Exception {
        var original = store.create(recordWithPayload("TEST", "FROZEN"), "finish-failure", false);
        jdbc.execute("DROP TABLE mes_label_print_log");
        assertThrows(Exception.class, () -> dispatcher.dispatch(original.getTaskId()));
        assertEquals(1, sends.get());
        assertEquals("SENDING", store.find(original.getTaskId()).getStatus());
        createTable("mes_label_print_log");
        jdbc.update(
                "UPDATE mes_label_print_task SET"
                        + " lease_until=DATEADD('SECOND',-5,CURRENT_TIMESTAMP)");
        dispatcher.pollTenant();
        dispatcher.pollTenant();
        assertEquals("UNKNOWN", store.find(original.getTaskId()).getStatus());
        assertEquals(1, sends.get());
        assertEquals(1, count("mes_label_print_log"));
    }

    @Test
    void sameManualRequestCannotChangeBusinessIntent() {
        service.send(record("TEST"), "FROZEN", StandardCharsets.UTF_8, "same-request");
        var changed = record("TEST");
        changed.setSnId(2L);
        changed.setSnCode("SN-2");
        assertThrows(
                Exception.class,
                () -> service.send(changed, "FROZEN", StandardCharsets.UTF_8, "same-request"));
        assertEquals(1, sends.get());
        assertEquals(1, count("mes_label_print_task"));
    }

    @Test
    void expiredLeaseBeforeNetworkPreventsLateWorkerFromSending() {
        var task = store.create(recordWithPayload("TEST", "FROZEN"), "pause-before-send", false);
        var paused = spy(store);
        doAnswer(
                        inv -> {
                            var record = (MesLabelPrintLog) inv.callRealMethod();
                            jdbc.update(
                                    "UPDATE mes_label_print_task SET"
                                            + " lease_until=DATEADD('SECOND',-5,CURRENT_TIMESTAMP)"
                                            + " WHERE task_id=?",
                                    task.getTaskId());
                            return record;
                        })
                .when(paused)
                .record(any());
        new LabelPrintDispatcher(paused, transport).dispatch(task.getTaskId());
        assertEquals(0, sends.get());
        assertEquals("UNKNOWN", store.find(task.getTaskId()).getStatus());
    }
}

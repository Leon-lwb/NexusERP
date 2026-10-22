package com.nexus.erp.wm.trace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.trace.SerialReservation;
import com.nexus.common.trace.SerialReservationCommand;
import com.nexus.erp.wm.controller.SerialIdentityController;
import com.nexus.erp.wm.controller.WmSnController;
import com.nexus.erp.wm.domain.bo.WmSnBo;
import com.nexus.erp.wm.mapper.WmSnMapper;
import com.nexus.erp.wm.service.impl.WmSnServiceImpl;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

/**
 * 手工身份退休必须受来源、权限、状态锁和事务审计约束；不连接环境数据库。
 */
class ErpManualSerialLifecycleTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    ErpSerialIdentityService service;
    ErpTraceContext context;
    MockedStatic<StpUtil> permissions;
    MockedStatic<LoginHelper> login;

    @BeforeEach
    void setup() {
        var fixture = new ErpSerialIdentityServiceTest();
        fixture.setup();
        jdbc = fixture.jdbc;
        tx = fixture.tx;
        context = mock(ErpTraceContext.class);
        when(context.tenant()).thenReturn("T1");
        when(context.today()).thenReturn(LocalDate.of(2026, 10, 18));
        service =
                new ErpSerialIdentityService(
                        jdbc, new ObjectMapper(), context, tx.getTransactionManager());
        for (String column :
                List.of(
                        "warehouse_id BIGINT",
                        "location_id BIGINT",
                        "update_by BIGINT",
                        "update_time TIMESTAMP")) {
            jdbc.execute("ALTER TABLE wm_sn ADD " + column);
        }
        jdbc.execute(
                "CREATE TABLE wm_material_stock(stock_id BIGINT PRIMARY KEY,tenant_id"
                    + " VARCHAR(20),sn_id BIGINT,stock_quantity DECIMAL(18,4),del_flag CHAR(1))");
        jdbc.execute(
                "CREATE TABLE wm_material_stock_tx(tx_id BIGINT PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),sn_id BIGINT,del_flag CHAR(1))");
        jdbc.execute(
                "CREATE TABLE wm_document_sn(document_sn_id BIGINT PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),identity_id BIGINT)");
        jdbc.execute(
                "CREATE TABLE wm_trace_material_reservation_line(reservation_line_id BIGINT PRIMARY"
                        + " KEY,tenant_id VARCHAR(20),identity_id BIGINT)");
        jdbc.execute(
                "CREATE TABLE wm_trace_material_consumption(consumption_id BIGINT PRIMARY"
                        + " KEY,tenant_id VARCHAR(20),identity_id BIGINT)");
        jdbc.execute(
                "CREATE TABLE wm_serial_lifecycle_command(operation_id BIGINT PRIMARY KEY,tenant_id"
                    + " VARCHAR(20),command_id VARCHAR(128),fingerprint CHAR(64),action"
                    + " VARCHAR(32),identity_id BIGINT,reservation_id BIGINT,actor_id BIGINT,reason"
                    + " VARCHAR(500),before_version BIGINT,after_version BIGINT,before_status"
                    + " VARCHAR(16),after_status VARCHAR(16),reservation_status"
                    + " VARCHAR(24),source_type VARCHAR(32),source_id BIGINT,before_json"
                    + " CLOB,after_json CLOB,result_json CLOB,created_at TIMESTAMP DEFAULT"
                    + " CURRENT_TIMESTAMP,UNIQUE(tenant_id,command_id))");
        permissions = mockStatic(StpUtil.class);
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::getUserId).thenReturn(501L);
    }

    @AfterEach
    void closeSecurity() {
        if (login != null) {
            login.close();
        }
        if (permissions != null) {
            permissions.close();
        }
    }

    SerialReservation manual(String command, int quantity) {
        return service.reserve(
                "T1",
                new SerialReservationCommand(command, 1L, quantity, null, "ERP_MANUAL", null));
    }

    Object retire(String tenant, Long identity, String command, Long version, String reason) {
        try {
            return ErpSerialIdentityService.class
                    .getMethod(
                            "retireUnusedManual",
                            String.class,
                            Long.class,
                            String.class,
                            Long.class,
                            String.class)
                    .invoke(service, tenant, identity, command, version, reason);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("缺少手工身份审计退休入口", e);
        }
    }

    Object retire(Long identity, String command) {
        return retire("T1", identity, command, 1L, "unused labels destroyed");
    }

    /**
     * PC注册入口不能借来源字符串冒用MES握手或任意业务单据。
     */
    @ParameterizedTest
    @ValueSource(strings = {"MES_FLOW_CARD", "MES_MANUAL", "SUBCONTRACT", "PO"})
    void pcReserveRejectsNonManualSources(String source) {
        var request = new SerialReservationCommand("spoof-" + source, 1L, 1, null, source, 20L);
        assertThrows(
                ServiceException.class,
                () -> new SerialIdentityController(service, context).reserve(request));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wm_sn", Integer.class));
    }

    /**
     * 旧新增和批量也必须经过相同的手工来源边界。
     */
    @Test
    void legacyUrlsRejectForgedMesSource() {
        var legacy =
                new WmSnController(new WmSnServiceImpl(mock(WmSnMapper.class), service, context));
        var one = new WmSnBo();
        one.setCommandId("legacy-spoof");
        one.setMaterialId(1L);
        one.setSourceDocType("MES_FLOW_CARD");
        one.setSourceDocId(20L);
        assertThrows(ServiceException.class, () -> legacy.add(one));
        one.setQuantity(2);
        assertThrows(ServiceException.class, () -> legacy.batchGenerate(one));
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM wm_serial_reservation", Integer.class));
    }

    /**
     * 保留未消费预留，使主线程仅RESERVED且有REGISTERED的guard继续保护未使用身份。
     */
    @Test
    void manualPcReservationRemainsPendingAndNeverDeclaresInventory() {
        var result =
                new SerialIdentityController(service, context)
                        .reserve(
                                new SerialReservationCommand(
                                        "pc", 1L, 1, null, "ERP_MANUAL", null));
        assertEquals("RESERVED", result.getData().status());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_sn WHERE status='REGISTERED' AND stock_id IS NULL",
                        Integer.class));
        permissions.verify(() -> StpUtil.checkPermission("wm:sn:add"));
    }

    /**
     * 唯一剩余手工身份退休后取消预留，审计和号码永久保留且原命令不能补号。
     */
    @Test
    void unusedManualRetiresWithAuditAndNeverRecyclesItsCode() {
        var reserved = manual("manual-original", 1);
        var sn = reserved.identities().get(0);
        var result = retire(sn.identityId(), "retire-once");
        assertEquals(
                "RETIRED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_sn WHERE sn_id=?", String.class, sn.identityId()));
        assertEquals(
                2L,
                jdbc.queryForObject(
                        "SELECT identity_version FROM wm_sn WHERE sn_id=?",
                        Long.class,
                        sn.identityId()));
        assertEquals(
                "CANCELLED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_serial_reservation WHERE"
                                + " command_id='manual-original'",
                        String.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_sn WHERE sn_id=? AND sn_code=? AND del_flag='0'",
                        Integer.class,
                        sn.identityId(),
                        sn.snCode()));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_serial_lifecycle_command WHERE actor_id=501 AND"
                                + " before_status='REGISTERED' AND after_status='RETIRED' AND"
                                + " before_version=1 AND after_version=2",
                        Integer.class));
        assertEquals(result, retire(sn.identityId(), "retire-once"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_serial_lifecycle_command", Integer.class));
        assertThrows(ServiceException.class, () -> manual("manual-original", 1));
        assertNotEquals(sn.snCode(), manual("manual-next", 1).identities().get(0).snCode());
        assertThrows(
                ServiceException.class, () -> retire(sn.identityId(), "new-command-cannot-reopen"));
    }

    /**
     * 支持部分批次退休；只在整批都退休时取消，不把已使用批次伪装成取消。
     */
    @Test
    void partialRetirementKeepsOtherUnitsAndOriginalRegistrationCannotReopenThem() {
        var reserved = manual("partial", 2);
        var first = reserved.identities().get(0);
        var second = reserved.identities().get(1);
        retire(first.identityId(), "partial-first");
        assertEquals(
                "RESERVED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_serial_reservation WHERE command_id='partial'",
                        String.class));
        assertEquals(
                "REGISTERED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_sn WHERE sn_id=?",
                        String.class,
                        second.identityId()));
        assertThrows(ServiceException.class, () -> manual("partial", 2));
        retire(second.identityId(), "partial-second");
        assertEquals(
                "CANCELLED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_serial_reservation WHERE command_id='partial'",
                        String.class));
    }

    @Test
    void usedSiblingDoesNotPreventRetiringOnlyTheUnusedUnit() {
        var reserved = manual("partly-used", 2);
        var unused = reserved.identities().get(0);
        var used = reserved.identities().get(1);
        jdbc.update(
                "UPDATE wm_sn SET status='OUT',identity_version=2 WHERE sn_id=?",
                used.identityId());
        jdbc.update("INSERT INTO wm_material_stock_tx VALUES(7,'T1',?,'0')", used.identityId());
        retire(unused.identityId(), "retire-unused-sibling");
        assertEquals(
                "RESERVED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_serial_reservation WHERE command_id='partly-used'",
                        String.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_sn WHERE status='REGISTERED'", Integer.class));
        assertEquals(
                "OUT",
                jdbc.queryForObject(
                        "SELECT status FROM wm_sn WHERE sn_id=?", String.class, used.identityId()));
    }

    /**
     * 已确认MES身份也不是PC手工来源，来源权限不能用wm:sn:remove冒充。
     */
    @ParameterizedTest
    @ValueSource(strings = {"MES_FLOW_CARD", "MES_MANUAL", "SUBCONTRACT", "PO"})
    void nonManualReservationsCannotBeRetired(String source) {
        var reserved =
                service.reserve(
                        "T1",
                        new SerialReservationCommand("foreign-source", 1L, 1, null, source, 20L));
        assertThrows(
                ServiceException.class,
                () -> retire(reserved.identities().get(0).identityId(), "denied-source"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_serial_lifecycle_command", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_STOCK", "OUT", "SCRAPPED", "RETIRED", "CREATED", "IN_PRODUCTION"})
    void onlyUnusedRegisteredStateCanRetire(String status) {
        var sn = manual("state", 1).identities().get(0);
        jdbc.update("UPDATE wm_sn SET status=? WHERE sn_id=?", status, sn.identityId());
        assertThrows(ServiceException.class, () -> retire(sn.identityId(), "denied-state"));
        assertEquals(
                status,
                jdbc.queryForObject(
                        "SELECT status FROM wm_sn WHERE sn_id=?", String.class, sn.identityId()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "wm_material_stock",
                "wm_material_stock_tx",
                "wm_document_sn",
                "wm_trace_material_reservation_line",
                "wm_trace_material_consumption"
            })
    void anyHistoricalUseIncludingDeletedOrZeroStockPreventsRetirement(String table) {
        var sn = manual("history", 1).identities().get(0);
        switch (table) {
            case "wm_material_stock" ->
                    jdbc.update(
                            "INSERT INTO wm_material_stock VALUES(7,'T1',?,0,'2')",
                            sn.identityId());
            case "wm_material_stock_tx" ->
                    jdbc.update(
                            "INSERT INTO wm_material_stock_tx VALUES(7,'T1',?,'2')",
                            sn.identityId());
            default -> jdbc.update("INSERT INTO " + table + " VALUES(7,'T1',?)", sn.identityId());
        }
        assertThrows(ServiceException.class, () -> retire(sn.identityId(), "denied-history"));
        assertEquals(
                "REGISTERED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_sn WHERE sn_id=?", String.class, sn.identityId()));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_serial_lifecycle_command", Integer.class));
    }

    @Test
    void sourcePermissionTenantVersionAndPayloadAreCheckedBeforeMutation() {
        var sn = manual("validation", 1).identities().get(0);
        permissions
                .when(() -> StpUtil.checkPermission("wm:sn:remove"))
                .thenThrow(new ServiceException("denied"));
        assertThrows(ServiceException.class, () -> retire(sn.identityId(), "no-permission"));
        permissions.when(() -> StpUtil.checkPermission("wm:sn:remove")).thenAnswer(call -> null);
        assertThrows(
                ServiceException.class,
                () -> retire("T2", sn.identityId(), "wrong-tenant", 1L, "reason"));
        assertThrows(
                ServiceException.class,
                () -> retire("T1", sn.identityId(), "wrong-version", 2L, "reason"));
        assertThrows(
                ServiceException.class, () -> retire("T1", sn.identityId(), "bad-reason", 1L, " "));
        assertThrows(
                ServiceException.class,
                () -> retire("T1", sn.identityId(), "x".repeat(129), 1L, "reason"));
        jdbc.update("UPDATE wm_sn SET stock_id=8 WHERE sn_id=?", sn.identityId());
        assertThrows(ServiceException.class, () -> retire(sn.identityId(), "bound-stock"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_serial_lifecycle_command", Integer.class));
    }

    @Test
    void changedRetirementPayloadOrActorCannotReplayAnExistingCommand() {
        var sn = manual("idempotency", 1).identities().get(0);
        retire(sn.identityId(), "same-retirement");
        assertThrows(
                ServiceException.class,
                () -> retire("T1", sn.identityId(), "same-retirement", 1L, "changed reason"));
        login.when(LoginHelper::getUserId).thenReturn(502L);
        assertThrows(ServiceException.class, () -> retire(sn.identityId(), "same-retirement"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_serial_lifecycle_command", Integer.class));
    }

    /**
     * 审计写入失败必须回滚身份与预留，不能只留下状态变更。
     */
    @Test
    void auditFailureRollsBackIdentityAndReservationTogether() {
        var sn = manual("atomic-audit", 1).identities().get(0);
        var recording = spy(jdbc);
        doAnswer(
                        call -> {
                            if (((String) call.getArgument(0))
                                    .startsWith("INSERT INTO wm_serial_lifecycle_command")) {
                                throw new DataAccessResourceFailureException(
                                        "synthetic audit failure");
                            }
                            return call.callRealMethod();
                        })
                .when(recording)
                .update(anyString(), any(Object[].class));
        service =
                new ErpSerialIdentityService(
                        recording, new ObjectMapper(), context, tx.getTransactionManager());
        assertThrows(RuntimeException.class, () -> retire(sn.identityId(), "audit-failed"));
        assertEquals(
                "REGISTERED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_sn WHERE sn_id=?", String.class, sn.identityId()));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT identity_version FROM wm_sn WHERE sn_id=?",
                        Long.class,
                        sn.identityId()));
        assertEquals(
                "RESERVED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_serial_reservation WHERE command_id='atomic-audit'",
                        String.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_serial_lifecycle_command", Integer.class));
    }

    /**
     * 实际Controller入口、权限与稳定结果契约必须可调用，不能只存在未接线工具方法。
     */
    @Test
    void retirementControllerIsWiredAndRequiresBothSourceAndRetirementPermissions()
            throws Exception {
        var sn = manual("controller", 1).identities().get(0);
        var controller = new SerialIdentityController(service, context);
        var response =
                controller.retire(
                        sn.identityId(),
                        new SerialIdentityController.RetireManualCommand(
                                "controller-retire", 1L, "unused"));
        assertEquals(sn.identityId(), response.getData().identityId());
        assertEquals("RETIRED", response.getData().status());
        assertEquals(2L, response.getData().version());
        assertNotNull(response.getData().operationId());
        var method =
                SerialIdentityController.class.getMethod(
                        "retire", Long.class, SerialIdentityController.RetireManualCommand.class);
        assertEquals(
                List.of("wm:sn:add", "wm:sn:remove"),
                List.of(method.getAnnotation(SaCheckPermission.class).value()));
        assertEquals(
                List.of("/{identityId}/retire"),
                List.of(method.getAnnotation(PostMapping.class).value()));
    }

    /**
     * 损坏审计结果不能被当作成功重放，更不能再次执行退休。
     */
    @Test
    void damagedRetirementAuditResultFailsClosed() {
        var sn = manual("damaged-audit", 1).identities().get(0);
        retire(sn.identityId(), "damaged-command");
        jdbc.update(
                "UPDATE wm_serial_lifecycle_command SET result_json='{}' WHERE"
                        + " command_id='damaged-command'");
        assertThrows(ServiceException.class, () -> retire(sn.identityId(), "damaged-command"));
        assertEquals(
                2L,
                jdbc.queryForObject(
                        "SELECT identity_version FROM wm_sn WHERE sn_id=?",
                        Long.class,
                        sn.identityId()));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_serial_lifecycle_command", Integer.class));
    }

    /**
     * 缺少审计结构时失败关闭，不改变身份、号码或原预留。
     */
    @Test
    void missingAuditTableDoesNotPerformUnauditedRetirement() {
        var sn = manual("missing-table", 1).identities().get(0);
        jdbc.execute("DROP TABLE wm_serial_lifecycle_command");
        assertThrows(ServiceException.class, () -> retire(sn.identityId(), "missing-audit"));
        assertEquals(
                "REGISTERED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_sn WHERE sn_id=?", String.class, sn.identityId()));
        assertEquals(
                "RESERVED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_serial_reservation WHERE command_id='missing-table'",
                        String.class));
    }

    /**
     * 并发重放同一退休命令只提交一次审计和一次版本推进。
     */
    @Test
    void concurrentIdenticalRetirementCommandsReturnOneFrozenResult() throws Exception {
        var sn = manual("concurrent-command", 1).identities().get(0);
        var workers = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Object> action =
                    () -> {
                        try (var p = mockStatic(StpUtil.class);
                                var l = mockStatic(LoginHelper.class)) {
                            l.when(LoginHelper::getUserId).thenReturn(501L);
                            ready.countDown();
                            assertTrue(start.await(5, TimeUnit.SECONDS));
                            return retire(sn.identityId(), "concurrent-retire");
                        }
                    };
            var first = workers.submit(action);
            var second = workers.submit(action);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM wm_serial_lifecycle_command", Integer.class));
            assertEquals(
                    2L,
                    jdbc.queryForObject(
                            "SELECT identity_version FROM wm_sn WHERE sn_id=?",
                            Long.class,
                            sn.identityId()));
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    /**
     * 行锁竞争保证退休和首次入库不能都成功；库存侧模拟相同SN锁与版本CAS。
     */
    @Test
    void competingIntakeAndRetirementCannotBothCommit() throws Exception {
        var sn = manual("race", 1).identities().get(0);
        var workers = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var retired =
                    workers.submit(
                            () -> {
                                try (var p = mockStatic(StpUtil.class);
                                        var l = mockStatic(LoginHelper.class)) {
                                    l.when(LoginHelper::getUserId).thenReturn(501L);
                                    ready.countDown();
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    try {
                                        retire(sn.identityId(), "race-retire");
                                        return true;
                                    } catch (ServiceException rejected) {
                                        return false;
                                    }
                                }
                            });
            var received =
                    workers.submit(
                            () -> {
                                ready.countDown();
                                assertTrue(start.await(5, TimeUnit.SECONDS));
                                return tx.execute(
                                        s -> {
                                            jdbc.queryForList(
                                                    "SELECT sn_id FROM wm_sn WHERE tenant_id='T1'"
                                                            + " AND sn_id=? FOR UPDATE",
                                                    sn.identityId());
                                            if (jdbc.update(
                                                            "UPDATE wm_sn SET"
                                                                + " status='IN_STOCK',stock_id=9,identity_version=2"
                                                                + " WHERE tenant_id='T1' AND"
                                                                + " sn_id=? AND status='REGISTERED'"
                                                                + " AND identity_version=1",
                                                            sn.identityId())
                                                    != 1) {
                                                return false;
                                            }
                                            jdbc.update(
                                                    "INSERT INTO wm_material_stock"
                                                            + " VALUES(9,'T1',?,1,'0')",
                                                    sn.identityId());
                                            return true;
                                        });
                            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(retired.get(10, TimeUnit.SECONDS), received.get(10, TimeUnit.SECONDS));
            String status =
                    jdbc.queryForObject(
                            "SELECT status FROM wm_sn WHERE sn_id=?",
                            String.class,
                            sn.identityId());
            assertEquals(
                    "RETIRED".equals(status) ? 1 : 0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM wm_serial_lifecycle_command", Integer.class));
            assertEquals(
                    "IN_STOCK".equals(status) ? 1 : 0,
                    jdbc.queryForObject("SELECT COUNT(*) FROM wm_material_stock", Integer.class));
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}

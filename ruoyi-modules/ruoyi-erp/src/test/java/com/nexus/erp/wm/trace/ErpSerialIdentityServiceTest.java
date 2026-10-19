package com.nexus.erp.wm.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ErpSerialIdentityServiceTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    ErpSerialIdentityService service;

    @BeforeEach
    void setup() {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:registry"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute(
                "CREATE TABLE bd_material(material_id BIGINT PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),material_code VARCHAR(64),status INT,del_flag"
                        + " CHAR(1),is_batch_manage INT,is_serial_manage INT,allow_mixed_lot"
                        + " INT,tracking_version BIGINT,tracking_stage VARCHAR(16))");
        jdbc.execute(
                "CREATE TABLE bd_serial_number_rule(rule_id BIGINT,tenant_id VARCHAR(20),rule_type"
                        + " VARCHAR(16),rule_code VARCHAR(64),prefix VARCHAR(32),date_format"
                        + " VARCHAR(32),seq_length INT,seq_cycle VARCHAR(16),check_digit_rule"
                        + " VARCHAR(16),status VARCHAR(16),del_flag CHAR(1),rule_version BIGINT)");
        jdbc.execute(
                "CREATE TABLE wm_serial_reservation(reservation_id BIGINT PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),command_id VARCHAR(128),fingerprint VARCHAR(64),material_id"
                        + " BIGINT,quantity INT,rule_id BIGINT,rule_version BIGINT,rule_snapshot"
                        + " CLOB,source_type VARCHAR(32),source_id BIGINT,cycle_key"
                        + " VARCHAR(32),biz_date DATE,status VARCHAR(16),created_at"
                        + " TIMESTAMP,updated_at TIMESTAMP,UNIQUE(tenant_id,command_id))");
        jdbc.execute(
                "CREATE TABLE wm_serial_sequence(sequence_id BIGINT PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),namespace VARCHAR(32),rule_key VARCHAR(64),cycle_key"
                        + " VARCHAR(32),current_value BIGINT,version BIGINT,created_at"
                        + " TIMESTAMP,updated_at"
                        + " TIMESTAMP,UNIQUE(tenant_id,namespace,rule_key,cycle_key))");
        jdbc.execute(
                "CREATE TABLE wm_batch(batch_id BIGINT PRIMARY KEY,tenant_id"
                    + " VARCHAR(20),material_id BIGINT,batch_code VARCHAR(64),del_flag CHAR(1))");
        jdbc.execute(
                "CREATE TABLE wm_sn(sn_id BIGINT PRIMARY KEY,tenant_id VARCHAR(20),sn_code"
                    + " VARCHAR(128),material_id BIGINT,material_code VARCHAR(64),status"
                    + " VARCHAR(16),source_doc_type VARCHAR(32),source_doc_id"
                    + " BIGINT,registration_command VARCHAR(128),identity_version BIGINT,rule_id"
                    + " BIGINT,batch_id BIGINT,batch_code VARCHAR(64),stock_id BIGINT,del_flag"
                    + " CHAR(1),create_time TIMESTAMP,UNIQUE(tenant_id,sn_code))");
        jdbc.update("INSERT INTO bd_material VALUES(1,'T1','M1',1,'0',0,1,1,1,'STRICT')");
        var context = mock(ErpTraceContext.class);
        when(context.today()).thenReturn(LocalDate.of(2026, 10, 18));
        service =
                new ErpSerialIdentityService(
                        jdbc, new ObjectMapper(), context, tx.getTransactionManager());
    }

    SerialReservation reserve(String command, int quantity) {
        return service.reserve(
                "T1", new SerialReservationCommand(command, 1L, quantity, null, "MES_CARD", 20L));
    }

    @Test
    void consecutiveBatchesReserveWholeRangesAndNeverDeclareInventory() {
        var a = reserve("first", 3);
        var b = reserve("second", 2);
        var single = reserve("single", 1);
        var all = new HashSet<String>();
        a.identities().forEach(i -> assertTrue(all.add(i.snCode())));
        b.identities().forEach(i -> assertTrue(all.add(i.snCode())));
        single.identities().forEach(i -> assertTrue(all.add(i.snCode())));
        assertEquals(6, all.size());
        assertEquals(
                6L,
                jdbc.queryForObject("SELECT current_value FROM wm_serial_sequence", Long.class));
        assertEquals(
                6,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_sn WHERE status='REGISTERED' AND stock_id IS NULL",
                        Integer.class));
    }

    @Test
    void replayReturnsOriginalIdentitiesButChangedPayloadFails() {
        var a = reserve("same", 2);
        var b = reserve("same", 2);
        assertEquals(a.identities(), b.identities());
        assertThrows(RuntimeException.class, () -> reserve("same", 3));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wm_sn", Integer.class));
    }

    @Test
    void explicitRuleIsAppliedAndFrozen() {
        jdbc.update(
                "INSERT INTO bd_serial_number_rule"
                    + " VALUES(9,'T1','PRODUCT_SN','CUSTOM','PC','yyyyMM',4,'MONTH','NONE','ACTIVE','0',3)");
        var a =
                service.reserve(
                        "T1", new SerialReservationCommand("custom", 1L, 2, 9L, "MES_CARD", 20L));
        assertEquals("PC2026100001", a.identities().get(0).snCode());
        jdbc.update("UPDATE bd_serial_number_rule SET prefix='OTHER',rule_version=4");
        var b =
                service.reserve(
                        "T1", new SerialReservationCommand("custom", 1L, 2, 9L, "MES_CARD", 20L));
        assertEquals(a.identities(), b.identities());
    }

    @Test
    void inactiveMaterialCannotCreateReservationOrSerials() {
        jdbc.update("UPDATE bd_material SET status=0");
        assertThrows(RuntimeException.class, () -> reserve("inactive", 2));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wm_sn", Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM wm_serial_reservation", Integer.class));
    }

    @Test
    void confirmationIsIdempotentAndCannotChangeFingerprint() {
        var a = reserve("confirm", 1);
        tx.executeWithoutResult(s -> service.confirm("T1", "confirm", a.fingerprint()));
        tx.executeWithoutResult(s -> service.confirm("T1", "confirm", a.fingerprint()));
        assertThrows(
                RuntimeException.class,
                () -> tx.executeWithoutResult(s -> service.confirm("T1", "confirm", "different")));
        assertEquals(
                "CONFIRMED",
                jdbc.queryForObject("SELECT status FROM wm_serial_reservation", String.class));
    }

    /**
     * 批次属于当前租户及物料时，正式身份冻结该批次；重放不随主数据变化。
     */
    @Test
    void batchIdentityIsValidatedAndFrozenIntoRegisteredSn() {
        jdbc.update("INSERT INTO wm_batch VALUES(9,'T1',1,'B-ORIGINAL','0')");
        var command = new SerialReservationCommand("batch", 1L, 2, null, "ERP_MANUAL", null, 9L);
        var first = service.reserve("T1", command);
        assertTrue(
                first.identities().stream()
                        .allMatch(
                                i ->
                                        Objects.equals(9L, i.batchId())
                                                && "B-ORIGINAL".equals(i.batchCode())));
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_sn WHERE batch_id=9 AND batch_code='B-ORIGINAL'"
                                + " AND status='REGISTERED' AND stock_id IS NULL",
                        Integer.class));
        jdbc.update("UPDATE wm_batch SET batch_code='RENAMED' WHERE batch_id=9");
        assertEquals(first.identities(), service.reserve("T1", command).identities());
    }

    /**
     * 同一命令换批次必须冲突，不允许复用旧号或再发号。
     */
    @Test
    void fingerprintIncludesBatchAndKeepsSixArgumentMesCompatibility() {
        var legacy = new SerialReservationCommand("legacy", 1L, 1, null, "MES_CARD", 20L);
        assertNull(legacy.batchId());
        assertEquals(TraceHashes.fingerprint(1L, 1, null, "MES_CARD", 20L), legacy.fingerprint());
        var a = new SerialReservationCommand("batch", 1L, 1, null, "MES_CARD", 20L, 9L);
        var b = new SerialReservationCommand("batch", 1L, 1, null, "MES_CARD", 20L, 10L);
        assertNotEquals(legacy.fingerprint(), a.fingerprint());
        assertNotEquals(a.fingerprint(), b.fingerprint());
        jdbc.update("INSERT INTO wm_batch VALUES(9,'T1',1,'B9','0'),(10,'T1',1,'B10','0')");
        service.reserve("T1", a);
        assertThrows(RuntimeException.class, () -> service.reserve("T1", b));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wm_sn", Integer.class));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SerialReservationCommand("bad", 1L, 1, null, "MES_CARD", 20L, 0L));
    }

    /**
     * 跨租户、跨物料、已删除及不存在批次均不能消耗号码。
     */
    @Test
    void foreignOrDeletedBatchCannotCreateAnyReservationOrNumber() {
        jdbc.update(
                "INSERT INTO wm_batch"
                    + " VALUES(9,'T2',1,'FOREIGN','0'),(10,'T1',2,'OTHER-MATERIAL','0'),(11,'T1',1,'DELETED','2')");
        for (long batchId : List.of(9L, 10L, 11L, 12L)) {
            var command =
                    new SerialReservationCommand(
                            "invalid-" + batchId, 1L, 1, null, "ERP_MANUAL", null, batchId);
            assertThrows(RuntimeException.class, () -> service.reserve("T1", command));
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wm_sn", Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM wm_serial_reservation", Integer.class));
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM wm_serial_sequence", Integer.class));
    }

    /**
     * 新正式号码限制64位，历史128位身份读取保持原文。
     */
    @Test
    void newCodeLengthLimitDoesNotTruncateHistoricalIdentity() {
        String historical = "LEGACY-" + "X".repeat(121);
        jdbc.update(
                "INSERT INTO wm_sn(sn_id,tenant_id,sn_code,material_id,status,del_flag)"
                        + " VALUES(77,'T1',?,1,'REGISTERED','0')",
                historical);
        assertEquals(historical, service.find("T1", 77L).snCode());
        String prefix = "P".repeat(32);
        jdbc.update(
                "INSERT INTO bd_serial_number_rule"
                    + " VALUES(9,'T1','PRODUCT_SN','BOUNDARY',?,?,18,'NONE','NONE','ACTIVE','0',1)",
                prefix,
                "'" + "X".repeat(14) + "'");
        // 日期模板用单引号包裹常量，确保只测号码长度而不引入时钟格式差异。
        jdbc.update(
                "UPDATE bd_serial_number_rule SET date_format=? WHERE rule_id=9",
                "'" + "X".repeat(14) + "'");
        var valid =
                service.reserve(
                        "T1",
                        new SerialReservationCommand("length64", 1L, 1, 9L, "ERP_MANUAL", null));
        assertEquals(64, valid.identities().get(0).snCode().length());
        jdbc.update(
                "UPDATE bd_serial_number_rule SET date_format=? WHERE rule_id=9",
                "'" + "X".repeat(15) + "'");
        assertThrows(
                RuntimeException.class,
                () ->
                        service.reserve(
                                "T1",
                                new SerialReservationCommand(
                                        "length65", 1L, 1, 9L, "ERP_MANUAL", null)));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_serial_reservation WHERE command_id='length65'",
                        Integer.class));
        assertEquals(historical, service.find("T1", 77L).snCode());
    }

    /**
     * Inbox确认必须携带原预留身份，并保留主线程的强制事务边界。
     */
    @Test
    void inboxConfirmationRequiresOriginalReservationAndMandatoryTransaction() throws Exception {
        var reservation = reserve("inbox", 1);
        var method =
                ErpSerialIdentityService.class.getMethod(
                        "confirmWithinInbox", String.class, Long.class, String.class, String.class);
        assertEquals(
                org.springframework.transaction.annotation.Propagation.MANDATORY,
                method.getAnnotation(org.springframework.transaction.annotation.Transactional.class)
                        .propagation());
        assertThrows(
                RuntimeException.class,
                () ->
                        tx.executeWithoutResult(
                                s ->
                                        service.confirmWithinInbox(
                                                "T1",
                                                reservation.reservationId() + 1,
                                                "inbox",
                                                reservation.fingerprint())));
        assertEquals(
                "RESERVED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_serial_reservation WHERE command_id='inbox'",
                        String.class));
        tx.executeWithoutResult(
                s ->
                        service.confirmWithinInbox(
                                "T1",
                                reservation.reservationId(),
                                "inbox",
                                reservation.fingerprint()));
        assertEquals(
                "CONFIRMED",
                jdbc.queryForObject(
                        "SELECT status FROM wm_serial_reservation WHERE command_id='inbox'",
                        String.class));
    }

    /**
     * 既有序列表命中后不再扫描历史SN，只在首次初始化时读取历史最大值。
     */
    @Test
    void existingSequenceDoesNotRescanHistoricalNumbers() {
        JdbcTemplate recording = spy(jdbc);
        var context = mock(ErpTraceContext.class);
        when(context.today()).thenReturn(LocalDate.of(2026, 10, 18));
        service =
                new ErpSerialIdentityService(
                        recording, new ObjectMapper(), context, tx.getTransactionManager());
        reserve("initial", 1);
        assertTrue(
                mockingDetails(recording).getInvocations().stream()
                        .anyMatch(
                                call ->
                                        call.getMethod().getName().equals("queryForList")
                                                && call.getArgument(0) instanceof String sql
                                                && sql.startsWith("SELECT sn_code FROM wm_sn")
                                                && sql.contains(" LIKE ")));
        clearInvocations(recording);
        reserve("next", 1);
        assertFalse(
                mockingDetails(recording).getInvocations().stream()
                        .anyMatch(
                                call ->
                                        call.getMethod().getName().equals("queryForList")
                                                && call.getArgument(0) instanceof String sql
                                                && sql.startsWith("SELECT sn_code FROM wm_sn")
                                                && sql.contains(" LIKE ")));
    }

    private void rule(long id, String prefix, int length) {
        jdbc.update(
                "INSERT INTO bd_serial_number_rule VALUES(?,'T1','PRODUCT_SN',?,?,"
                        + " '',?,'NONE','NONE','ACTIVE','0',1)",
                id,
                "RULE-" + id,
                prefix,
                length);
    }

    /**
     * 已初始化规则改成已有前缀，也必须继续发未出现的正式码，而不是永久撞号。
     */
    @Test
    void editedPrefixSkipsOtherRuleExistingCodesWithoutLosingCounterProgress() {
        rule(9, "A", 4);
        rule(10, "B", 4);
        service.reserve("T1", new SerialReservationCommand("a", 1L, 1, 9L, "ERP_MANUAL", null));
        var previous =
                service.reserve(
                        "T1", new SerialReservationCommand("b", 1L, 3, 10L, "ERP_MANUAL", null));
        jdbc.update("UPDATE bd_serial_number_rule SET prefix='B',rule_version=2 WHERE rule_id=9");
        var changed =
                service.reserve(
                        "T1",
                        new SerialReservationCommand("edited", 1L, 2, 9L, "ERP_MANUAL", null));
        assertEquals(
                List.of("B0004", "B0005"),
                changed.identities().stream().map(SerialIdentity::snCode).toList());
        assertTrue(
                Collections.disjoint(
                        previous.identities().stream().map(SerialIdentity::snCode).toList(),
                        changed.identities().stream().map(SerialIdentity::snCode).toList()));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM wm_sn", Integer.class));
    }

    /**
     * 两物料两规则同格式共享输出序列，跨规则仍然是完整无交叠号段。
     */
    @Test
    void identicalOutputRulesAcrossMaterialsShareOneSequence() {
        rule(9, "SAME", 4);
        rule(10, "SAME", 4);
        jdbc.update("INSERT INTO bd_material VALUES(2,'T1','M2',1,'0',0,1,1,1,'STRICT')");
        var first =
                service.reserve(
                        "T1",
                        new SerialReservationCommand("rule-a", 1L, 2, 9L, "ERP_MANUAL", null));
        var second =
                service.reserve(
                        "T1",
                        new SerialReservationCommand("rule-b", 2L, 2, 10L, "ERP_MANUAL", null));
        assertEquals(
                List.of("SAME0001", "SAME0002"),
                first.identities().stream().map(SerialIdentity::snCode).toList());
        assertEquals(
                List.of("SAME0003", "SAME0004"),
                second.identities().stream().map(SerialIdentity::snCode).toList());
        assertEquals(
                1, jdbc.queryForObject("SELECT COUNT(*) FROM wm_serial_sequence", Integer.class));
    }

    /**
     * 已删除号码仍永久占用；碰到候选号段时整段留空，成功结果保持连续完整。
     */
    @Test
    void historicalDeletedNumberBurnsConflictingWindowAndNeverGetsRecycled() {
        rule(9, "R", 4);
        service.reserve(
                "T1", new SerialReservationCommand("initial-r", 1L, 1, 9L, "ERP_MANUAL", null));
        jdbc.update(
                "INSERT INTO wm_sn(sn_id,tenant_id,sn_code,material_id,status,del_flag)"
                        + " VALUES(700,'T1','R0003',1,'OUT','2')");
        var result =
                service.reserve(
                        "T1",
                        new SerialReservationCommand(
                                "after-deleted", 1L, 3, 9L, "ERP_MANUAL", null));
        assertEquals(
                List.of("R0005", "R0006", "R0007"),
                result.identities().stream().map(SerialIdentity::snCode).toList());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_sn WHERE sn_id=700 AND sn_code='R0003' AND"
                                + " del_flag='2'",
                        Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_sn WHERE sn_code IN ('R0002','R0004')",
                        Integer.class));
        assertEquals(
                7L,
                jdbc.queryForObject(
                        "SELECT MAX(current_value) FROM wm_serial_sequence", Long.class));
    }

    /**
     * 预查后唯一键碰撞也要撤销半批；外部已提交历史保留，整段留空且原命令精确重放。
     */
    @Test
    void lateUniqueCollisionBurnsWholeWindowAndReplaysOnlyCompleteIdentities() {
        rule(9, "LATE", 4);
        service.reserve(
                "T1", new SerialReservationCommand("late-initial", 1L, 1, 9L, "ERP_MANUAL", null));
        JdbcTemplate recording = recordingService();
        var injected = new java.util.concurrent.atomic.AtomicBoolean();
        var competing = new TransactionTemplate(tx.getTransactionManager());
        competing.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        doAnswer(
                        call -> {
                            String sql = call.getArgument(0);
                            Object[] parameters = (Object[]) call.getRawArguments()[1];
                            if (sql.startsWith("INSERT INTO wm_sn(")
                                    && "LATE0003".equals(parameters[2])
                                    && injected.compareAndSet(false, true)) {
                                competing.executeWithoutResult(
                                        s ->
                                                jdbc.update(
                                                        "INSERT INTO"
                                                            + " wm_sn(sn_id,tenant_id,sn_code,material_id,status,del_flag)"
                                                            + " VALUES(703,'T1','LATE0003',1,'OUT','2')"));
                            }
                            return call.callRealMethod();
                        })
                .when(recording)
                .update(anyString(), any(Object[].class));
        var command = new SerialReservationCommand("late-batch", 1L, 3, 9L, "ERP_MANUAL", null);
        var result = service.reserve("T1", command);
        assertTrue(injected.get());
        assertEquals(
                List.of("LATE0005", "LATE0006", "LATE0007"),
                result.identities().stream().map(SerialIdentity::snCode).toList());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_sn WHERE sn_code IN ('LATE0002','LATE0004')",
                        Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_sn WHERE sn_id=703 AND sn_code='LATE0003' AND"
                                + " status='OUT' AND del_flag='2'",
                        Integer.class));
        assertEquals(
                3,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_sn WHERE registration_command='late-batch'",
                        Integer.class));
        assertEquals(
                7L,
                jdbc.queryForObject("SELECT current_value FROM wm_serial_sequence", Long.class));
        assertEquals(result, service.reserve("T1", command));
        assertEquals(
                7L,
                jdbc.queryForObject("SELECT current_value FROM wm_serial_sequence", Long.class));
        var next =
                service.reserve(
                        "T1",
                        new SerialReservationCommand("late-next", 1L, 1, 9L, "ERP_MANUAL", null));
        assertEquals("LATE0008", next.identities().get(0).snCode());
    }

    /**
     * 跨物料同格式首次并发注册必须共享号段，每个原命令都得到完整且可重放的身份集合。
     */
    @Test
    void concurrentCrossMaterialRulesReserveDisjointCompleteBatches() throws Exception {
        rule(9, "PAR", 4);
        rule(10, "PAR", 4);
        jdbc.update("INSERT INTO bd_material VALUES(2,'T1','M2',1,'0',0,1,1,1,'STRICT')");
        var commands =
                List.of(
                        new SerialReservationCommand("parallel-a", 1L, 3, 9L, "ERP_MANUAL", null),
                        new SerialReservationCommand("parallel-b", 2L, 3, 10L, "ERP_MANUAL", null));
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var results = new ArrayList<java.util.concurrent.Future<SerialReservation>>();
            for (var command : commands) {
                results.add(
                        workers.submit(
                                () -> {
                                    ready.countDown();
                                    assertTrue(
                                            start.await(5, java.util.concurrent.TimeUnit.SECONDS));
                                    return service.reserve("T1", command);
                                }));
            }
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            var codes = new HashSet<String>();
            for (int i = 0; i < results.size(); i++) {
                var result = results.get(i).get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(3, result.identities().size());
                assertEquals(commands.get(i).materialId(), result.identities().get(0).materialId());
                assertTrue(
                        result.identities().stream()
                                .allMatch(
                                        identity ->
                                                identity.materialId()
                                                        .equals(
                                                                result.identities()
                                                                        .get(0)
                                                                        .materialId())));
                result.identities().forEach(identity -> assertTrue(codes.add(identity.snCode())));
                assertEquals(result, service.reserve("T1", commands.get(i)));
            }
            assertEquals(
                    Set.of("PAR0001", "PAR0002", "PAR0003", "PAR0004", "PAR0005", "PAR0006"),
                    codes);
            assertEquals(
                    1,
                    jdbc.queryForObject("SELECT COUNT(*) FROM wm_serial_sequence", Integer.class));
            assertEquals(
                    6L,
                    jdbc.queryForObject(
                            "SELECT current_value FROM wm_serial_sequence", Long.class));
            assertEquals(
                    2,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM wm_serial_reservation", Integer.class));
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    /**
     * 流水位数不能靠String.format自动扩宽，批量越界不得留下半批身份或消耗有效尾号。
     */
    @Test
    void configuredSequenceWidthIsAnAtomicBatchBoundary() {
        rule(9, "LIMIT", 2);
        jdbc.update(
                "INSERT INTO wm_sn(sn_id,tenant_id,sn_code,material_id,status,del_flag)"
                        + " VALUES(701,'T1','LIMIT98',1,'REGISTERED','0')");
        assertThrows(
                RuntimeException.class,
                () ->
                        service.reserve(
                                "T1",
                                new SerialReservationCommand(
                                        "too-wide", 1L, 2, 9L, "ERP_MANUAL", null)));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wm_sn WHERE registration_command='too-wide'",
                        Integer.class));
        var last =
                service.reserve(
                        "T1", new SerialReservationCommand("last", 1L, 1, 9L, "ERP_MANUAL", null));
        assertEquals("LIMIT99", last.identities().get(0).snCode());
        assertThrows(
                RuntimeException.class,
                () ->
                        service.reserve(
                                "T1",
                                new SerialReservationCommand(
                                        "exhausted", 1L, 1, 9L, "ERP_MANUAL", null)));
    }

    /**
     * 单次请求上限不等于工单总量上限，501和10000均有效，超过10000须由编排分批。
     */
    @Test
    void perRequestQuantityLimitDoesNotIntroduceA500UnitOrderCap() {
        assertDoesNotThrow(
                () -> new SerialReservationCommand("501", 1L, 501, null, "MES_CARD", 20L));
        assertDoesNotThrow(
                () -> new SerialReservationCommand("10000", 1L, 10000, null, "MES_CARD", 20L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SerialReservationCommand("10001", 1L, 10001, null, "MES_CARD", 20L));
    }

    private JdbcTemplate recordingService() {
        JdbcTemplate recording = spy(jdbc);
        var context = mock(ErpTraceContext.class);
        when(context.today()).thenReturn(LocalDate.of(2026, 10, 18));
        service =
                new ErpSerialIdentityService(
                        recording, new ObjectMapper(), context, tx.getTransactionManager());
        return recording;
    }

    /**
     * MySQL间隙死锁必须先结束失败事务，再以新连接重试同一原命令。
     */
    @Test
    void sequenceInitializationDeadlockRetriesOnlyInFreshErpTransactions() throws Exception {
        JdbcTemplate recording = recordingService();
        var connections = new ArrayList<java.sql.Connection>();
        doAnswer(
                        call -> {
                            String sql = call.getArgument(0);
                            if (sql.contains("FROM wm_serial_sequence")
                                    && sql.endsWith("FOR UPDATE")) {
                                var holder =
                                        (org.springframework.jdbc.datasource.ConnectionHolder)
                                                org.springframework.transaction.support
                                                        .TransactionSynchronizationManager
                                                        .getResource(jdbc.getDataSource());
                                connections.add(holder.getConnection());
                                if (connections.size() == 1) {
                                    throw new org.springframework.dao.CannotAcquireLockException(
                                            "synthetic MySQL deadlock",
                                            new java.sql.SQLTransactionRollbackException(
                                                    "deadlock", "40001", 1213));
                                }
                            }
                            return call.callRealMethod();
                        })
                .when(recording)
                .queryForList(anyString(), any(Object[].class));
        var result = assertDoesNotThrow(() -> reserve("retry-original", 2));
        assertEquals(2, result.identities().size());
        assertEquals(2, connections.size());
        assertNotSame(connections.get(0), connections.get(1));
        assertTrue(connections.get(0).isClosed());
        assertEquals(
                1,
                jdbc.queryForObject("SELECT COUNT(*) FROM wm_serial_reservation", Integer.class));
        assertEquals(
                1, jdbc.queryForObject("SELECT COUNT(*) FROM wm_serial_sequence", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wm_sn", Integer.class));
    }

    /**
     * 锁错误重试必须有上限，失败请求不留下半批身份或半条预留。
     */
    @Test
    void persistentLockFailureStopsAfterThreeIndependentAttempts() {
        JdbcTemplate recording = recordingService();
        var connections = new ArrayList<java.sql.Connection>();
        doAnswer(
                        call -> {
                            String sql = call.getArgument(0);
                            if (sql.contains("FROM wm_serial_sequence")
                                    && sql.endsWith("FOR UPDATE")) {
                                var holder =
                                        (org.springframework.jdbc.datasource.ConnectionHolder)
                                                org.springframework.transaction.support
                                                        .TransactionSynchronizationManager
                                                        .getResource(jdbc.getDataSource());
                                connections.add(holder.getConnection());
                                throw new org.springframework.dao.CannotAcquireLockException(
                                        "synthetic timeout",
                                        new java.sql.SQLException("timeout", "HY000", 1205));
                            }
                            return call.callRealMethod();
                        })
                .when(recording)
                .queryForList(anyString(), any(Object[].class));
        assertThrows(
                com.nexus.common.core.exception.ServiceException.class,
                () -> reserve("retry-limit", 2));
        assertEquals(3, connections.size());
        assertEquals(3, new HashSet<>(connections).size());
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM wm_serial_reservation", Integer.class));
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM wm_serial_sequence", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wm_sn", Integer.class));
    }

    /**
     * 发号编排不允许携带外层业务行锁，禁止靠挂起外层事务隐藏锁依赖。
     */
    @Test
    void reservationRejectsOuterBusinessTransactionBeforeAnyWrite() {
        assertThrows(
                RuntimeException.class,
                () ->
                        tx.execute(
                                s ->
                                        service.reserve(
                                                "T1",
                                                new SerialReservationCommand(
                                                        "outer",
                                                        1L,
                                                        1,
                                                        null,
                                                        "ERP_MANUAL",
                                                        null))));
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM wm_serial_reservation", Integer.class));
    }

    /**
     * 业务拒绝不属于可重试锁冲突。
     */
    @Test
    void inactiveMaterialBusinessFailureIsNotRetried() {
        JdbcTemplate recording = recordingService();
        jdbc.update("UPDATE bd_material SET status=0");
        assertThrows(
                com.nexus.common.core.exception.ServiceException.class,
                () -> reserve("business-failure", 1));
        long reads =
                mockingDetails(recording).getInvocations().stream()
                        .filter(
                                call ->
                                        call.getMethod().getName().equals("queryForList")
                                                && call.getArgument(0) instanceof String sql
                                                && sql.contains("FROM bd_material"))
                        .count();
        assertEquals(1, reads);
    }
}

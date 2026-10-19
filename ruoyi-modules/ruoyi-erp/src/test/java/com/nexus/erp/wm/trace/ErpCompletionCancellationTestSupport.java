package com.nexus.erp.wm.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.jdbc.*;
import com.nexus.common.trace.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.PrintWriter;
import java.sql.Clob;
import java.util.*;
import static org.mockito.Mockito.*;

/**
 * 只使用独立 H2 内存库和合成凭据的取消握手测试基座。
 */
public class ErpCompletionCancellationTestSupport {
    static final String TENANT = "000001", HASH = "a".repeat(64), MATERIAL_HASH = "b".repeat(64);
    static final long MANIFEST = 9007199254741993L,
            ERP_WO = 1001L,
            MES_WO = 2001L,
            MATERIAL = 3001L;
    ObjectMapper json;
    JdbcTemplate erp, mes;
    TransactionTemplate et, mt;
    JdbcTraceJournal ej, mj;
    ErpTraceContext context;
    ErpCompletionCancellationService service;
    ErpCompletionCancellationHandler handler;
    TraceSourceVerificationPort verifier;
    MaterialTraceEvent original, cancel;
    CompletionCancellationRequest request;

    @BeforeEach
    void setupCancellation() throws Exception {
        json = new ObjectMapper();
        var ed =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:cancel_e_"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2000",
                        "sa",
                        "");
        var md =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:cancel_m_"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        erp = new JdbcTemplate(ed);
        mes = new JdbcTemplate(md);
        et = new TransactionTemplate(new DataSourceTransactionManager(ed));
        mt = new TransactionTemplate(new DataSourceTransactionManager(md));
        for (String table : List.of("wm_trace_inbox", "wm_trace_outbox")) {
            journalTable(erp, table);
        }
        for (String table : List.of("mes_trace_inbox", "mes_trace_outbox")) {
            journalTable(mes, table);
        }
        erp.execute(
                "CREATE TABLE prd_work_order(wo_id BIGINT PRIMARY KEY,tenant_id"
                    + " VARCHAR(20),material_id BIGINT,wo_no VARCHAR(64),unit_id BIGINT,del_flag"
                    + " CHAR(1),wo_status VARCHAR(32),completed_qty DECIMAL(18,4),scrap_qty"
                    + " DECIMAL(18,4))");
        erp.execute(
                "CREATE TABLE wm_production_recpt(recpt_id BIGINT PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),wo_id BIGINT,status VARCHAR(32),del_flag CHAR(1),batch_id"
                        + " BIGINT)");
        erp.execute(
                "CREATE TABLE wm_material_stock_tx(tx_id BIGINT PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),source_doc_type VARCHAR(64),source_doc_id BIGINT)");
        erp.execute(
                "CREATE TABLE wm_trace_posting_group(posting_group_id BIGINT PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),source_doc_type VARCHAR(64),source_doc_id BIGINT,status"
                        + " VARCHAR(32))");
        erp.execute(
                "CREATE TABLE wm_trace_material_consumption(consumption_id BIGINT PRIMARY"
                        + " KEY,tenant_id VARCHAR(20),manifest_id BIGINT,manifest_revision INT)");
        erp.update(
                "INSERT INTO prd_work_order VALUES(?,?,?,?,?,?,'IN_PROGRESS',0,0)",
                ERP_WO,
                TENANT,
                MATERIAL,
                "SYNTH-WO",
                1L,
                "0");
        ej = new JdbcTraceJournal(erp, json, "ERP");
        mj = new JdbcTraceJournal(mes, json, "MES");
        context = mock(ErpTraceContext.class);
        service = new ErpCompletionCancellationService(erp, json, context);
        verifier =
                new TraceSourceVerificationPort() {
                    public String sourceModule() {
                        return "MES";
                    }

                    public void verify(MaterialTraceEvent event) {
                        JdbcTraceSourceVerifier.verify(mes, "MES", event);
                    }
                };
        handler = new ErpCompletionCancellationHandler(json, service, List.of(verifier));
        original = original("ORIGINAL", MANIFEST, ERP_WO, HASH);
        request = new CompletionCancellationRequest(MANIFEST, 1, HASH, original);
        cancel = cancellation("CANCEL", request, 1);
        mt.executeWithoutResult(
                s -> {
                    mj.append(original);
                    mj.append(cancel);
                });
    }

    static void journalTable(JdbcTemplate jdbc, String name) {
        jdbc.execute(
                "CREATE TABLE "
                        + name
                        + "(event_id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(20),source_module"
                        + " VARCHAR(16),event_type VARCHAR(64),aggregate_type"
                        + " VARCHAR(64),aggregate_id BIGINT,aggregate_version BIGINT,schema_version"
                        + " INT,payload CLOB,content_hash CHAR(64),status VARCHAR(24),attempts"
                        + " INT,lease_owner VARCHAR(64),lease_until TIMESTAMP,next_attempt_at"
                        + " TIMESTAMP,last_error VARCHAR(512),result_payload CLOB,created_at"
                        + " TIMESTAMP,updated_at TIMESTAMP,applied_at"
                        + " TIMESTAMP,UNIQUE(tenant_id,source_module,aggregate_type,aggregate_id,aggregate_version))");
    }

    MaterialTraceEvent original(String id, long manifest, long wo, String hash) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("manifestId", Long.toString(manifest));
        payload.put("revision", 1);
        payload.put("manifestHash", hash);
        payload.put("erpWoId", Long.toString(wo));
        payload.put("mesWoId", Long.toString(MES_WO));
        payload.put("materialId", Long.toString(MATERIAL));
        payload.put("lineCount", 1);
        payload.put("materialHash", MATERIAL_HASH);
        payload.put("materialLineCount", 0);
        return event(
                id,
                "MES",
                "COMPLETION_MANIFEST_FROZEN",
                "COMPLETION_MANIFEST",
                manifest,
                1,
                json.writeValueAsString(payload));
    }

    MaterialTraceEvent cancellation(String id, CompletionCancellationRequest command, long version)
            throws Exception {
        return event(
                id,
                "MES",
                "COMPLETION_MANIFEST_CANCEL_REQUESTED",
                "COMPLETION_MANIFEST_CANCELLATION",
                command.manifestId(),
                version,
                json.writeValueAsString(command));
    }

    static MaterialTraceEvent event(
            String id,
            String source,
            String type,
            String aggregate,
            long aggregateId,
            long version,
            String payload) {
        return new MaterialTraceEvent(
                id,
                TENANT,
                source,
                type,
                aggregate,
                aggregateId,
                version,
                1,
                payload,
                TraceHashes.sha256(payload));
    }

    CompletionCancellationProof proof() {
        return (CompletionCancellationProof) handler.prepare(cancel);
    }

    TraceDeliveryResult deliver() {
        Object prepared = handler.prepare(cancel);
        return et.execute(s -> ej.accept(cancel, () -> handler.apply(cancel, prepared)));
    }

    CompletionCancellationResult result(TraceDeliveryResult delivery) throws Exception {
        return json.readValue(delivery.resultPayload(), CompletionCancellationResult.class);
    }

    String scalar(String sql, Object... args) {
        return erp.queryForObject(sql, String.class, args);
    }

    static String text(Object value) {
        try {
            return value instanceof Clob c
                    ? c.getSubString(1, (int) c.length())
                    : Objects.toString(value, null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    String originalResult() {
        return text(
                erp.queryForObject(
                        "SELECT result_payload FROM wm_trace_inbox WHERE event_id=?",
                        Object.class,
                        original.eventId()));
    }

    String receiptResult(String outcome) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceEventId", original.eventId());
        payload.put("outcome", outcome);
        payload.put("manifestId", Long.toString(MANIFEST));
        payload.put("revision", 1);
        payload.put("manifestHash", HASH);
        payload.put("receiptId", "POSTED".equals(outcome) ? "51" : null);
        payload.put("postingGroupId", "POSTED".equals(outcome) ? "61" : null);
        return json.writeValueAsString(payload);
    }

    void noGoodProof() throws Exception {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("outcome", "NO_GOOD_OUTPUT");
        payload.put("manifestId", Long.toString(MANIFEST));
        payload.put("revision", 1);
        payload.put("manifestHash", HASH);
        payload.put("erpWoId", Long.toString(ERP_WO));
        payload.put("mesWoId", Long.toString(MES_WO));
        payload.put("materialId", Long.toString(MATERIAL));
        String encoded = json.writeValueAsString(payload);
        et.executeWithoutResult(
                s ->
                        ej.append(
                                event(
                                        "ERP-NO-GOOD-" + MANIFEST + "-1",
                                        "ERP",
                                        "ERP_INVENTORY_POSTED",
                                        "COMPLETION_RESULT",
                                        MANIFEST,
                                        1,
                                        encoded)));
    }

    /**
     * 独立 JUnit launcher；不启动 Maven reactor 或 Spring/外部服务。
     */
    public static void main(String[] args) {
        var request =
                LauncherDiscoveryRequestBuilder.request()
                        .selectors(
                                DiscoverySelectors.selectClass(
                                        ErpCompletionCancellationContractTest.class),
                                DiscoverySelectors.selectClass(
                                        ErpCompletionCancellationHandlerTest.class),
                                DiscoverySelectors.selectClass(
                                        ErpCompletionCancellationServiceTest.class),
                                DiscoverySelectors.selectClass(
                                        ErpCompletionCancellationReplayTest.class),
                                DiscoverySelectors.selectClass(
                                        "com.nexus.mes.trace.MesTraceJournalReplayTest"),
                                DiscoverySelectors.selectClass(
                                        "com.nexus.common.trace.JdbcTraceJournalTest"))
                        .configurationParameter(
                                "junit.jupiter.extensions.autodetection.enabled", "false")
                        .build();
        var listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        var summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out));
        summary.printFailuresTo(new PrintWriter(System.out));
        if (summary.getTestsFoundCount() == 0
                || summary.getTestsFailedCount() > 0
                || summary.getTestsSkippedCount() > 0) {
            System.exit(1);
        }
    }
}

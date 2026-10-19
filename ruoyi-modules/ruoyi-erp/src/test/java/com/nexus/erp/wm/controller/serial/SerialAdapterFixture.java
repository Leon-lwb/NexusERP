package com.nexus.erp.wm.controller.serial;

import com.fasterxml.jackson.databind.*;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.domain.WmSerialPostingRequest;
import com.nexus.erp.wm.service.*;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import javax.sql.DataSource;
import java.sql.Connection;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;

/**
 * JDBC仅模拟存储，事务使用真实Spring代理和连接管理，不连接外部数据库。
 */
class SerialAdapterFixture {
    final ObjectMapper json = new ObjectMapper();
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final WmSerialInventoryRepository repo = mock(WmSerialInventoryRepository.class);
    final WmSerialSelectionService selections = mock(WmSerialSelectionService.class);
    final Connection connection = mock(Connection.class);
    final List<Map<String, Object>> drafts = new ArrayList<>();
    final List<String> events = new ArrayList<>();
    final List<Map<String, Object>> outbox = new ArrayList<>();
    final Map<String, Map<String, Object>> commandRows = new LinkedHashMap<>();
    final Map<Long, List<SnItem>> frozenHistory = new HashMap<>();
    final WmSerialBusinessStore store;
    final WmSerialBusinessAdapter adapter;
    final WmSerialBusinessAspect aspect;
    final WmSerialInventoryService inventory;
    String version = "opaque:事实/一";
    List<Long> lines = new ArrayList<>(List.of(11L));
    TrackingPolicy policy = new TrackingPolicy(true, true, false, 7L, TrackingStage.STRICT);
    WmSerialInventoryRepository.Group group;
    List<SnItem> frozen = List.of();

    SerialAdapterFixture() throws Exception {
        when(repo.tenant()).thenReturn("000001");
        when(repo.row(anyString(), any(Object[].class)))
                .thenAnswer(
                        call -> {
                            String sql = call.getArgument(0);
                            var row = new HashMap<String, Object>();
                            row.put("recpt_id", 10L);
                            row.put("wo_id", 70L);
                            row.put("material_id", 20L);
                            row.put("batch_id", 50L);
                            row.put("sn_code", "SN-真实");
                            row.put("batch_code", "LOT-A");
                            return row;
                        });
        when(selections.snapshot(anyString(), anyLong(), anyLong()))
                .thenAnswer(
                        call ->
                                new WmSerialInventoryRepository.Snapshot(
                                        version,
                                        policy,
                                        group != null && "POSTED".equals(group.status()) ? 2 : 1));
        when(repo.request(anyString(), anyLong(), anyLong()))
                .thenAnswer(
                        call ->
                                request(
                                        call.getArgument(0),
                                        call.getArgument(1),
                                        call.getArgument(2)));
        when(repo.group(any()))
                .thenAnswer(
                        call -> {
                            var request = (WmSerialPostingRequest) call.getArgument(0);
                            if (group == null) {
                                return null;
                            }
                            var row = commandRows.get(group.commandId());
                            return row != null
                                            && (!request.sourceType()
                                                            .equals(row.get("source_doc_type"))
                                                    || !request.sourceId()
                                                            .equals(row.get("source_doc_id"))
                                                    || request.lineKey()
                                                            != WmSerialBusinessStore.number(
                                                                    row.get("source_doc_line_id")))
                                    ? null
                                    : group;
                        });
        when(repo.items(anyLong()))
                .thenAnswer(call -> frozenHistory.getOrDefault(call.getArgument(0), frozen));
        when(repo.sourceHash(any())).thenAnswer(call -> version);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(call -> new ArrayList<>(lines));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(call -> group == null ? 0L : 1L);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenAnswer(
                        call -> {
                            String sql = call.getArgument(0);
                            Object[] args = params(call);
                            if (sql.contains("FROM wm_trace_outbox")) {
                                return outbox.stream()
                                        .filter(
                                                row ->
                                                        Objects.equals(
                                                                        row.get("tenant_id"),
                                                                        args[0])
                                                                && "ERP"
                                                                        .equals(
                                                                                row.get(
                                                                                        "source_module"))
                                                                && "STOCK_POSTING"
                                                                        .equals(
                                                                                row.get(
                                                                                        "aggregate_type"))
                                                                && Objects.equals(
                                                                        row.get("aggregate_id"),
                                                                        args[1]))
                                        .map(HashMap::new)
                                        .toList();
                            }
                            if (sql.contains("FROM wm_document_sn")) {
                                return drafts.stream()
                                        .filter(
                                                r ->
                                                        r.get("source_doc_type").equals(args[1])
                                                                && r.get("source_doc_id")
                                                                        .equals(args[2])
                                                                && r.get("source_doc_line_id")
                                                                        .equals(args[3]))
                                        .map(HashMap::new)
                                        .toList();
                            }
                            if (sql.contains("FROM wm_trace_posting_group")
                                    && sql.contains("command_id")) {
                                var keys =
                                        Arrays.stream(args)
                                                .filter(
                                                        o ->
                                                                o instanceof String v
                                                                        && v.startsWith("BUS/"))
                                                .map(Object::toString)
                                                .toList();
                                if (!keys.isEmpty()) {
                                    return commandRows.values().stream()
                                            .filter(
                                                    row ->
                                                            keys.stream()
                                                                    .anyMatch(
                                                                            key ->
                                                                                    key.endsWith(
                                                                                                    "%")
                                                                                            ? String
                                                                                                    .valueOf(
                                                                                                            row
                                                                                                                    .get(
                                                                                                                            "command_id"))
                                                                                                    .startsWith(
                                                                                                            key
                                                                                                                    .substring(
                                                                                                                            0,
                                                                                                                            key
                                                                                                                                            .length()
                                                                                                                                    - 1))
                                                                                            : key
                                                                                                    .equals(
                                                                                                            row
                                                                                                                    .get(
                                                                                                                            "command_id"))))
                                            .map(HashMap::new)
                                            .toList();
                                }
                            }
                            if (sql.contains("FROM wm_trace_posting_group")
                                    && sql.contains("posting_group_id=?")
                                    && !commandRows.isEmpty()) {
                                return commandRows.values().stream()
                                        .filter(
                                                row ->
                                                        Arrays.stream(args)
                                                                .anyMatch(
                                                                        arg ->
                                                                                Objects.equals(
                                                                                        arg,
                                                                                        row.get(
                                                                                                "posting_group_id"))))
                                        .map(HashMap::new)
                                        .toList();
                            }
                            if (sql.contains("FROM wm_trace_posting_group")
                                    && sql.contains("source_doc_type=?")
                                    && args.length >= 4
                                    && !commandRows.isEmpty()) {
                                return commandRows.values().stream()
                                        .filter(
                                                row ->
                                                        Objects.equals(
                                                                        args[1],
                                                                        row.get("source_doc_type"))
                                                                && Objects.equals(
                                                                        args[2],
                                                                        row.get("source_doc_id"))
                                                                && Objects.equals(
                                                                        args[3],
                                                                        row.get(
                                                                                "source_doc_line_id")))
                                        .map(HashMap::new)
                                        .toList();
                            }
                            if (group == null) {
                                return List.of();
                            }
                            return List.of(
                                    commandRows.containsKey(group.commandId())
                                            ? new HashMap<>(commandRows.get(group.commandId()))
                                            : Map.of(
                                                    "posting_group_id",
                                                    group.id(),
                                                    "status",
                                                    group.status(),
                                                    "command_id",
                                                    group.commandId()));
                        });
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenAnswer(
                        call -> {
                            String sql = call.getArgument(0);
                            Object[] args = params(call);
                            if (sql.startsWith("DELETE FROM wm_document_sn")) {
                                drafts.removeIf(
                                        r ->
                                                r.get("source_doc_type").equals(args[1])
                                                        && r.get("source_doc_id").equals(args[2])
                                                        && (args.length < 4
                                                                || r.get("source_doc_line_id")
                                                                        .equals(args[3])));
                            }
                            if (sql.startsWith("INSERT INTO wm_document_sn")) {
                                Map<String, Object> row = new HashMap<>();
                                String[] keys = {
                                    "document_sn_id",
                                    "tenant_id",
                                    "command_id",
                                    "line_no",
                                    "source_doc_type",
                                    "source_doc_id",
                                    "source_doc_line_id",
                                    "identity_id",
                                    "material_id",
                                    "batch_id",
                                    "source_stock_id",
                                    "target_location_id",
                                    "original_tx_id"
                                };
                                for (int i = 0; i < keys.length; i++) {
                                    row.put(keys[i], args[i]);
                                }
                                drafts.add(row);
                                events.add("draft");
                            }
                            return 1;
                        });
        when(selections.freeze(any()))
                .thenAnswer(
                        call -> {
                            var bo =
                                    (com.nexus.erp.wm.domain.bo.WmSerialSelectionBo)
                                            call.getArgument(0);
                            var prior = commandRows.get(bo.getCommandId());
                            if (prior != null) {
                                Long id =
                                        WmSerialBusinessStore.number(prior.get("posting_group_id"));
                                if (!Objects.equals(prior.get("source_hash"), bo.getDetailVersion())
                                        || !WmSerialBusinessStore.sameItems(
                                                bo.getSnItems(), frozenHistory.get(id))) {
                                    throw new com.nexus.common.core.exception.ServiceException(
                                            "同一命令内容不一致");
                                }
                                events.add("replay-proof");
                                return id;
                            }
                            events.add("freeze");
                            frozen = bo.getSnItems();
                            long id = 99L + commandRows.size();
                            var algorithms = new WmSerialInventoryService(repo);
                            String content = algorithms.selectionHash(frozen);
                            String fingerprint =
                                    algorithms.fingerprint(
                                            request(
                                                    bo.getSourceDocType(),
                                                    bo.getSourceDocId(),
                                                    bo.getLineId()),
                                            bo.getPolicyVersion(),
                                            content);
                            group =
                                    new WmSerialInventoryRepository.Group(
                                            id,
                                            bo.getCommandId(),
                                            bo.getRevision(),
                                            fingerprint,
                                            content,
                                            BigDecimal.valueOf(frozen.size()),
                                            BigDecimal.ZERO,
                                            "PREPARED",
                                            null);
                            Map<String, Object> row = new HashMap<>();
                            row.put("posting_group_id", id);
                            row.put("command_id", bo.getCommandId());
                            row.put("tenant_id", "000001");
                            row.put("source_doc_type", bo.getSourceDocType());
                            row.put("source_doc_id", bo.getSourceDocId());
                            row.put("source_doc_line_id", bo.getLineId());
                            row.put("detail_version", bo.getRevision());
                            row.put("source_hash", bo.getDetailVersion());
                            row.put("policy_version", bo.getPolicyVersion());
                            row.put("material_id", 20L);
                            row.put("quantity", BigDecimal.valueOf(frozen.size()));
                            row.put("amount", BigDecimal.ZERO);
                            row.put("fingerprint", group.fingerprint());
                            row.put("content_hash", group.contentHash());
                            row.put(
                                    "tx_type",
                                    request(
                                                    bo.getSourceDocType(),
                                                    bo.getSourceDocId(),
                                                    bo.getLineId())
                                            .txType());
                            row.put("status", "PREPARED");
                            commandRows.put(bo.getCommandId(), row);
                            frozenHistory.put(id, List.copyOf(frozen));
                            return id;
                        });
        store = new WmSerialBusinessStore(jdbc, repo, selections);
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        ProxyFactory tx = new ProxyFactory(new WmSerialBusinessAdapter(store));
        tx.setProxyTargetClass(true);
        tx.addAdvice(
                new TransactionInterceptor(
                        new DataSourceTransactionManager(ds),
                        new AnnotationTransactionAttributeSource()));
        adapter = (WmSerialBusinessAdapter) tx.getProxy();
        aspect = new WmSerialBusinessAspect(adapter, json);
        var target = mock(WmSerialInventoryService.class);
        when(target.post(any()))
                .thenAnswer(
                        call -> {
                            events.add("post");
                            return true;
                        });
        inventory = proxy(target);
    }

    @SuppressWarnings("unchecked")
    <T> T proxy(T value) {
        AspectJProxyFactory factory = new AspectJProxyFactory(value);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return (T) factory.getProxy();
    }

    WmSerialPostingRequest request(String type, Long source, Long line) {
        String tx =
                switch (type) {
                    case "WM_MATERIAL_RECEIVE" -> "RECEIVE_IN";
                    case "WM_TRANSFER" -> "TRANSFER";
                    case "WM_PRODUCTION_ISSUE" -> "PRODUCTION_ISSUE";
                    case "WM_SALES_ISSUE" -> "SALES_OUT";
                    case "WM_MISC_ISSUE" -> "MISC_ISSUE";
                    case "WM_MATERIAL_RETURN", "SO_RETURN" -> "RETURN_IN";
                    case "PU_PURCHASE_RETURN" -> "PUR_RETURN_OUT";
                    case "WM_STOCK_TAKING" -> "COUNT_ADJUST";
                    case "PRD_WORK_ORDER" -> "PRODUCTION_IN";
                    default -> "MISC_RECPT";
                };
        return WmSerialPostingRequest.in(
                type, source, line, tx, 20L, BigDecimal.ONE, 30L, 40L, 50L, null);
    }

    JsonNode body(String text) throws Exception {
        return json.readTree(text);
    }

    SnItem item(long id) {
        return new SnItem(id, null, null, null, 50L);
    }

    static Object[] params(InvocationOnMock call) {
        Object[] raw = call.getRawArguments();
        return raw.length == 2 && raw[1] instanceof Object[] a
                ? a
                : Arrays.copyOfRange(raw, 1, raw.length);
    }
}

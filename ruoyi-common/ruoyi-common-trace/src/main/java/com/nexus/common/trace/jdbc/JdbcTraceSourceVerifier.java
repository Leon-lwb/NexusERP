package com.nexus.common.trace.jdbc;

import com.nexus.common.trace.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Clob;
import java.util.*;

/**
 * 固定来源表并核对完整事件头；仅做只读验证，不写入业务数据或持有行锁。
 */
public final class JdbcTraceSourceVerifier {
    private JdbcTraceSourceVerifier() {}

    public static void verify(JdbcTemplate jdbc, String owner, MaterialTraceEvent event) {
        if (!Set.of("ERP", "MES").contains(owner) || !owner.equals(event.sourceModule())) {
            throw new IllegalArgumentException("事件来源模块不一致");
        }
        String table = owner.equals("ERP") ? "wm_trace_outbox" : "mes_trace_outbox";
        var rows =
                jdbc.queryForList(
                        "SELECT * FROM " + table + " WHERE tenant_id=? AND event_id=?",
                        event.tenantId(),
                        event.eventId());
        if (rows.size() != 1) {
            throw new IllegalStateException("缺少来源业务库内的原始事件凭据");
        }
        var row = rows.get(0);
        String payload = text(row.get("payload"));
        boolean equal =
                owner.equals(text(row.get("source_module")))
                        && event.eventType().equals(text(row.get("event_type")))
                        && event.aggregateType().equals(text(row.get("aggregate_type")))
                        && event.aggregateId() == number(row.get("aggregate_id"))
                        && event.aggregateVersion() == number(row.get("aggregate_version"))
                        && event.schemaVersion() == number(row.get("schema_version"))
                        && event.payload().equals(payload)
                        && event.contentHash().equals(text(row.get("content_hash")))
                        && event.contentHash().equals(TraceHashes.sha256(payload));
        if (!equal) {
            throw new IllegalStateException("事件与来源业务事务内的不可变凭据不一致");
        }
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static String text(Object value) {
        if (value instanceof Clob c) {
            try {
                return c.getSubString(1, Math.toIntExact(c.length()));
            } catch (Exception e) {
                throw new IllegalStateException("来源凭据不可读取");
            }
        }
        return value == null ? null : value.toString();
    }
}

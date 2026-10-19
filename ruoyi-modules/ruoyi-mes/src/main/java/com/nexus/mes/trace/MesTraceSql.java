package com.nexus.mes.trace;

import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.sql.Clob;
import java.util.Map;
import static com.nexus.mes.trace.MesTraceContext.need;

/**
 * 集中处理数据库字段类型与业务参数校验，保留长整型身份及精确数量的语义。
 */
final class MesTraceSql {
    private MesTraceSql() {}

    static Map<String, Object> one(JdbcTemplate jdbc, String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        need(rows.size() <= 1, "追溯来源不唯一");
        return rows.isEmpty() ? null : rows.get(0);
    }

    static String text(Object value) {
        try {
            return value == null
                    ? null
                    : value instanceof Clob c
                            ? c.getSubString(1, Math.toIntExact(c.length()))
                            : value.toString();
        } catch (Exception e) {
            throw new IllegalStateException("追溯载荷读取失败");
        }
    }

    static Long id(Object value) {
        return value == null ? null : new BigDecimal(text(value)).longValueExact();
    }

    static BigDecimal qty(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(text(value));
    }

    static boolean positive(Long id) {
        return id != null && id > 0;
    }

    static void quantity(BigDecimal q) {
        need(q != null && q.signum() > 0 && q.stripTrailingZeros().scale() <= 4, "数量必须是最多四位小数的正数");
    }

    static void command(String command) {
        need(command != null && !command.isBlank() && command.length() <= 128, "必须提供稳定业务命令号");
    }
}

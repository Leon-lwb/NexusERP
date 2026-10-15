package com.nexus.report.query;

import com.nexus.report.domain.ReportFault;

import java.sql.*;
import java.util.*;

/** 核验连接账户的实际授权；JDBC只读标记不能替代数据库权限。 */
public final class ReadOnlyQueryAccount {
    /** 禁止构造无状态校验器。 */
    private ReadOnlyQueryAccount() {}

    /**
     * 核验只允许当前业务库代码目录中的表级SELECT授权。
     *
     * @param connection 当前契约的connection配置或状态
     * @param approvedTables 当前契约的approvedTables配置或状态
     */
    public static void verify(Connection connection, Set<String> approvedTables)
            throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (product.contains("mysql")) {
            String database = connection.getCatalog();
            if (database == null || database.isBlank()) throw denied();
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER")) {
                boolean readable = false;
                while (rows.next()) {
                    String grant = rows.getString(1).toUpperCase(Locale.ROOT);
                    int on = grant.indexOf(" ON "), to = grant.indexOf(" TO ", on);
                    if (!grant.startsWith("GRANT ")
                            || on < 0
                            || to < 0
                            || grant.contains("WITH GRANT OPTION")) throw denied();
                    String privilege = grant.substring(6, on).trim();
                    if (!Set.of("USAGE", "SELECT").contains(privilege)) throw denied();
                    if (privilege.equals("USAGE")) continue;
                    String[] resource = grant.substring(on + 4, to).replace("`", "").split("\\.");
                    if (resource.length != 2
                            || !resource[0].equalsIgnoreCase(database)
                            || approvedTables.stream()
                                    .noneMatch(table -> table.equalsIgnoreCase(resource[1])))
                        throw denied();
                    readable = true;
                }
                if (!readable) throw denied();
            }
        } else if (product.equals("h2")) {
            try (var statement =
                            connection.prepareStatement(
                                    "SELECT IS_ADMIN FROM INFORMATION_SCHEMA.USERS WHERE"
                                            + " USER_NAME=CURRENT_USER");
                    var rows = statement.executeQuery()) {
                if (!rows.next() || rows.getBoolean(1)) throw denied();
            }
            try (var grants = connection.getMetaData().getTablePrivileges(null, null, "%")) {
                boolean readable = false;
                while (grants.next()) {
                    if (!"SELECT".equals(grants.getString("PRIVILEGE"))) throw denied();
                    String table = grants.getString("TABLE_NAME");
                    if (approvedTables.stream()
                            .noneMatch(allowed -> allowed.equalsIgnoreCase(table))) throw denied();
                    readable = true;
                }
                if (!readable) throw denied();
            }
        } else throw new ReportFault("QUERY_SOURCE_UNSUPPORTED", "当前只读查询仅支持已验证的MySQL数据源", 409);
    }

    /**
     * 返回稳定错误，不暴露授权文本和账户信息。
     *
     * @return 当前受控处理的固定结果
     */
    private static ReportFault denied() {
        return new ReportFault("QUERY_ACCOUNT_UNSAFE", "查询账户必须只有批准业务表的SELECT权限", 409);
    }
}

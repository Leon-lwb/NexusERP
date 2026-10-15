package com.nexus.erp.report.adapter;

import com.nexus.common.report.*;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** 从仓库安装结构构造隔离H2，保留真实字段与非空约束，不导入任何业务数据。 */
final class FoundationReportFixture {
    /** 隔离数据库访问入口。 */
    final JdbcTemplate jdbc;

    /**
     * 创建仅含指定真实表结构的测试数据库。
     *
     * @param tables 本模块需要验证的表名
     * @throws Exception 安装结构缺失或建表失败
     */
    FoundationReportFixture(String... tables) throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL(
                "jdbc:h2:mem:"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(ds);
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.exists(directory.resolve("script/sql/ry-erp.sql")))
            directory = directory.getParent();
        if (directory == null) throw new IllegalStateException("找不到仓库安装结构");
        String installation = Files.readString(directory.resolve("script/sql/ry-erp.sql"));
        for (String table : tables) {
            int start = installation.indexOf("CREATE TABLE `" + table + "`");
            if (start < 0) throw new IllegalArgumentException("安装表不存在: " + table);
            String block = installation.substring(start, installation.indexOf("\n) ENGINE", start));
            var columns =
                    block.lines()
                            .map(String::trim)
                            .filter(line -> line.startsWith("`"))
                            .map(
                                    line ->
                                            line.replaceAll(" COLLATE [a-zA-Z0-9_]+", "")
                                                    .replaceAll(" COMMENT '[^']*'", "")
                                                    .replace("AUTO_INCREMENT", "")
                                                    .replace("ON UPDATE CURRENT_TIMESTAMP", "")
                                                    .replaceAll(",$", ""))
                            .toList();
            jdbc.execute("CREATE TABLE " + table + "(" + String.join(",", columns) + ")");
        }
    }

    /**
     * 插入纯合成的测试记录，默认属于A租户10部门。
     *
     * @param table 固定业务表名
     * @param pairs 字段和值交替出现
     */
    void insert(String table, Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tenant_id", "A");
        values.put("create_dept", 10L);
        values.put("create_by", 7L);
        values.put("del_flag", "0");
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        jdbc.update(
                "INSERT INTO "
                        + table
                        + "("
                        + String.join(",", values.keySet())
                        + ") VALUES("
                        + values.keySet().stream().map(k -> "?").collect(Collectors.joining(","))
                        + ")",
                values.values().toArray());
    }

    /**
     * 生成具备指定权限且只可读10部门的身份。
     *
     * @param permissions 业务或字段权限
     * @return 服务端权限上下文
     */
    static ReportExecutionContext context(String... permissions) {
        var actor =
                new ReportActor(
                        "A",
                        7,
                        10,
                        false,
                        List.of(
                                new ReportRole(
                                        2,
                                        Set.of(permissions),
                                        new ReportScope(false, Set.of(10L), false, 7))));
        return new ReportExecutionContext(actor, List.of(ReportScope.all(7)));
    }

    /**
     * 创建默认投影且日期范围明确的查询。
     *
     * @param provider 被验证的业务目录
     * @param parameters 显式参数
     * @return 有界测试查询
     */
    static DatasetQuery query(ReportDatasetProvider provider, Map<String, Object> parameters) {
        return new DatasetQuery(
                parameters,
                new LinkedHashSet<>(provider.descriptor().defaultColumns()),
                1,
                50,
                false,
                null,
                false);
    }

    /**
     * 反射加载待实现的数据集，缺失时提供明确失败证据。
     *
     * @param name 数据集类名
     * @return 当前隔离数据库上的提供者
     * @throws Exception 数据集未实现或构造失败
     */
    ReportDatasetProvider provider(String name) throws Exception {
        return (ReportDatasetProvider)
                Class.forName("com.nexus.erp.report.adapter." + name)
                        .getConstructor(JdbcTemplate.class)
                        .newInstance(jdbc);
    }
}

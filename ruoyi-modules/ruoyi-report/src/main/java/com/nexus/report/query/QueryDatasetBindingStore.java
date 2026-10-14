package com.nexus.report.query;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/** 将数据集版本与不可变查询、目录及校验和建立唯一关联。 */
public final class QueryDatasetBindingStore {
    /**
     * 固定关联不包含连接串、账户或业务行。
     *
     * @param datasetVersion 当前契约的datasetVersion配置或状态
     * @param queryVersion 当前契约的queryVersion配置或状态
     * @param catalogVersion 当前契约的catalogVersion配置或状态
     * @param queryChecksum 当前契约的queryChecksum配置或状态
     * @param catalogChecksum 当前契约的catalogChecksum配置或状态
     */
    public record Link(
            String datasetVersion,
            String queryVersion,
            int catalogVersion,
            String queryChecksum,
            String catalogChecksum) {}

    /** 保存当前jdbc状态或配置。 */
    private final JdbcTemplate jdbc;

    /**
     * 复用系统主库的报表元数据连接。
     *
     * @param jdbc 当前契约的jdbc配置或状态
     */
    public QueryDatasetBindingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 读取当前租户的准确固定关联。
     *
     * @param tenant 租户隔离标识
     * @param datasetVersion 当前契约的datasetVersion配置或状态
     * @return 当前受控处理的固定结果
     */
    public Optional<Link> read(String tenant, String datasetVersion) {
        return jdbc
                .query(
                        "SELECT * FROM rpt_query_dataset_binding WHERE tenant_id=? AND"
                                + " dataset_version_id=?",
                        (r, i) ->
                                new Link(
                                        Long.toString(r.getLong("dataset_version_id")),
                                        Long.toString(r.getLong("query_version_id")),
                                        r.getInt("catalog_version"),
                                        r.getString("query_checksum"),
                                        r.getString("catalog_checksum")),
                        tenant,
                        Long.parseLong(datasetVersion))
                .stream()
                .findFirst();
    }

    /**
     * 仅首次发布登记关联，不允许重写已有版本。
     *
     * @param tenant 租户隔离标识
     * @param datasetVersion 当前契约的datasetVersion配置或状态
     * @param queryVersion 当前契约的queryVersion配置或状态
     * @param catalogVersion 当前契约的catalogVersion配置或状态
     * @param queryChecksum 当前契约的queryChecksum配置或状态
     * @param catalogChecksum 当前契约的catalogChecksum配置或状态
     */
    public void register(
            String tenant,
            String datasetVersion,
            String queryVersion,
            int catalogVersion,
            String queryChecksum,
            String catalogChecksum) {
        jdbc.update(
                "INSERT INTO"
                    + " rpt_query_dataset_binding(dataset_version_id,tenant_id,query_version_id,catalog_version,query_checksum,catalog_checksum)"
                    + " VALUES(?,?,?,?,?,?)",
                Long.parseLong(datasetVersion),
                tenant,
                Long.parseLong(queryVersion),
                catalogVersion,
                queryChecksum,
                catalogChecksum);
    }
}

package com.nexus.system.runtime;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.p6spy.engine.spy.P6DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 查询已绑定的现有连接池；绝不根据配置的 URL 新建连接。 */
public final class BoundDatasourceIdentityReader {
    private static final Map<String, String> SCHEMAS;
    static {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("master", "ry-vue"); values.put("erp", "ry-erp");
        values.put("mes", "ry-mes"); values.put("iot", "ry-iot");
        SCHEMAS = java.util.Collections.unmodifiableMap(values);
    }
    private final DataSource source;

    public BoundDatasourceIdentityReader(DataSource source) { this.source = source; }

    public record Observation(Map<String, RuntimeAttestationReport.DatasourceIdentity> identities,
                              String diagnosticCategory) {
        public Observation { identities = Map.copyOf(identities); }
        public String status() { return diagnosticCategory == null ? "OBSERVED" : "UNPROVEN"; }
    }

    public Observation observe() {
        Map<String, RuntimeAttestationReport.DatasourceIdentity> identities = new LinkedHashMap<>();
        if (TransactionSynchronizationManager.isActualTransactionActive()
            || TransactionSynchronizationManager.isSynchronizationActive()) {
            return new Observation(identities, "ATTESTATION_AMBIENT_TRANSACTION_UNSUPPORTED");
        }
        if (!(source instanceof DynamicRoutingDataSource routing)) {
            return new Observation(identities, "ATTESTATION_DATASOURCE_BINDING_UNPROVEN");
        }
        Map<String, DataSource> bound = new LinkedHashMap<>();
        Map<String, HikariDataSource> physical = new LinkedHashMap<>();
        try {
            var unique = new IdentityHashMap<DataSource, Boolean>();
            for (String name : SCHEMAS.keySet()) {
                DataSource registered = routing.getDataSources().get(name);
                if (registered == null || routing.getGroupDataSources().containsKey(name)
                    || routing.getDataSource(name) != registered) {
                    return new Observation(identities, "ATTESTATION_DATASOURCE_BINDING_UNPROVEN");
                }
                HikariDataSource pool = physicalPool(registered);
                if (pool == null || unique.put(pool, Boolean.TRUE) != null) {
                    return new Observation(identities, "ATTESTATION_DATASOURCE_BINDING_UNPROVEN");
                }
                bound.put(name, registered); physical.put(name, pool);
            }
            for (var entry : SCHEMAS.entrySet()) {
                if (!stillBound(routing, bound, physical)) {
                    return new Observation(identities, "ATTESTATION_DATASOURCE_BINDING_UNPROVEN");
                }
                ReadResult read = read(bound.get(entry.getKey()), entry.getValue());
                if (read.diagnosticCategory != null) return new Observation(identities, read.diagnosticCategory);
                identities.put(entry.getKey(), read.identity);
            }
            if (!stillBound(routing, bound, physical)) {
                return new Observation(identities, "ATTESTATION_DATASOURCE_BINDING_UNPROVEN");
            }
            return new Observation(identities, null);
        } catch (Exception ignored) {
            return new Observation(identities, "ATTESTATION_DATASOURCE_BINDING_UNPROVEN");
        }
    }

    private static HikariDataSource physicalPool(DataSource registered) throws ReflectiveOperationException {
        DataSource physical = registered;
        if (registered.getClass() == ItemDataSource.class) {
            ItemDataSource item = (ItemDataSource) registered;
            if (!Boolean.FALSE.equals(item.getSeata())) return null;
            physical = item.getRealDataSource();
            DataSource decorated = item.getDataSource();
            if (Boolean.FALSE.equals(item.getP6spy())) {
                if (decorated != physical) return null;
            } else if (Boolean.TRUE.equals(item.getP6spy())) {
                if (decorated == null || decorated.getClass() != P6DataSource.class) return null;
                // P6 的 getRealDataSource() 返回 JNDI 名称；绝不信任 unwrap 或回退路径。
                Field delegate = P6DataSource.class.getDeclaredField("realDataSource");
                if (!delegate.trySetAccessible() || delegate.get(decorated) != physical) return null;
            } else return null;
        }
        if (physical == null || physical.getClass() != HikariDataSource.class) return null;
        HikariDataSource hikari = (HikariDataSource) physical;
        // 借出或校验连接时，可能在本处 READ ONLY 事务开始前执行连接池回调。
        // 不要执行任意配置的 SQL，包括调用带副作用存储函数的 SELECT。
        return hikari.getDataSource() == null && harmlessProbe(hikari.getConnectionInitSql())
            && harmlessProbe(hikari.getConnectionTestQuery()) ? hikari : null;
    }

    private static boolean harmlessProbe(String sql) {
        return sql == null || sql.isBlank() || "SELECT 1".equalsIgnoreCase(sql.strip());
    }

    private static boolean stillBound(DynamicRoutingDataSource routing, Map<String, DataSource> bound,
                                      Map<String, HikariDataSource> physical) throws ReflectiveOperationException {
        for (String name : SCHEMAS.keySet()) {
            if (routing.getDataSources().get(name) != bound.get(name)
                || routing.getGroupDataSources().containsKey(name)
                || routing.getDataSource(name) != bound.get(name)
                || physicalPool(bound.get(name)) != physical.get(name)) return false;
        }
        return true;
    }

    private record ReadResult(RuntimeAttestationReport.DatasourceIdentity identity, String diagnosticCategory) { }

    private static ReadResult read(DataSource source, String expectedSchema) {
        Connection connection = null;
        RuntimeAttestationReport.DatasourceIdentity identity = null;
        String category = null;
        boolean originalReadOnly = false;
        int originalIsolation = Connection.TRANSACTION_READ_COMMITTED;
        boolean clean = false;
        boolean began = false;
        try {
            connection = source.getConnection();
            if (!connection.getAutoCommit()) {
                // 绝不重置或回滚可能已属于其他工作单元的连接。
                category = "ATTESTATION_AMBIENT_TRANSACTION_UNSUPPORTED";
            } else {
                clean = true;
                originalReadOnly = connection.isReadOnly();
                originalIsolation = connection.getTransactionIsolation();
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                // 驱动可能在抛出异常前改变状态；清理时仍须尝试回滚。
                began = true;
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.setQueryTimeout(3);
                    statement.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY");
                    try (ResultSet result = statement.executeQuery("SELECT @@port,@@server_uuid,DATABASE()")) {
                        if (!result.next()) category = "ATTESTATION_DATASOURCE_TARGET_UNPROVEN";
                        else {
                            int port = result.getInt(1);
                            boolean missingPort = result.wasNull();
                            String uuid = result.getString(2);
                            String schema = result.getString(3);
                            if (missingPort || port != 3306 || !expectedSchema.equals(schema)
                                || uuid == null || !uuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                                || result.next()) category = "ATTESTATION_DATASOURCE_TARGET_UNPROVEN";
                            else identity = new RuntimeAttestationReport.DatasourceIdentity(port, schema,
                                RuntimeAttestationProvider.sha256(uuid), "LIVE_JDBC_METADATA_SELECT",
                                OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            category = "ATTESTATION_DATASOURCE_READ_FAILED";
        } finally {
            if (connection != null) {
                boolean rolledBack = false;
                if (clean && began) {
                    try { connection.rollback(); rolledBack = true; }
                    catch (Exception ignored) { category = "ATTESTATION_READONLY_CLEANUP_UNPROVEN"; }
                }
                if (rolledBack) {
                    try {
                        connection.setReadOnly(originalReadOnly);
                        connection.setTransactionIsolation(originalIsolation);
                        connection.setAutoCommit(true);
                    } catch (Exception ignored) { category = "ATTESTATION_READONLY_CLEANUP_UNPROVEN"; }
                }
                try { connection.close(); }
                catch (Exception ignored) { category = "ATTESTATION_READONLY_CLEANUP_UNPROVEN"; }
            }
        }
        return new ReadResult(category == null ? identity : null, category);
    }
}

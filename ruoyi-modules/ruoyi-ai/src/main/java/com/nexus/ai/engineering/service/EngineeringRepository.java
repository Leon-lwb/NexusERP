package com.nexus.ai.engineering.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.ai.domain.entity.*;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.function.Supplier;

import javax.sql.DataSource;

/** 工程 master 仓储；SQL 必须显式限定 owner，写竞争使用受影响行数，不等待外部 IO。 */
@Repository
@DS("master")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    /** 使用应用数据源构建短事务仓储；测试注入独立 H2。 */
    public EngineeringRepository(DataSource source) {
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    /** 在 master 单连接短事务中执行有界元数据操作。 */
    public <T> T transaction(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    /** 条件更新并返回真实受影响行数；参数均使用绑定变量。 */
    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    /** 查询内部实体，调用者负责固定 SQL 与完整 owner 条件。 */
    public <T> List<T> query(Class<T> type, String sql, Object... args) {
        return jdbc.query(sql, BeanPropertyRowMapper.newInstance(type), args);
    }

    /** 按不可猜凭证摘要定位设备，后续必须复核当前所有者权限。 */
    public AiEngineeringDevice deviceByHash(String hash, boolean refresh) {
        return one(
                query(
                        AiEngineeringDevice.class,
                        "select * from ai_engineering_device where "
                                + (refresh ? "refresh_hash" : "access_hash")
                                + "=?",
                        hash));
    }

    /** 查询精确所有者设备，不按编号授权。 */
    public AiEngineeringDevice device(String tenant, Long user, Long id) {
        return one(
                query(
                        AiEngineeringDevice.class,
                        "select * from ai_engineering_device where tenant_id=? and user_id=? and"
                            + " device_id=?",
                        tenant,
                        user,
                        id));
    }

    /** 查询精确所有者任务。 */
    public AiEngineeringTask task(String tenant, Long user, Long id) {
        return one(
                query(
                        AiEngineeringTask.class,
                        "select * from ai_engineering_task where tenant_id=? and user_id=? and"
                            + " task_id=?",
                        tenant,
                        user,
                        id));
    }

    /** 有界列表计数，固定 SQL 显式限定 owner。 */
    public long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    /** 查询单行结果，空集返回空值。 */
    public static <T> T one(List<T> rows) {
        return rows.isEmpty() ? null : rows.get(0);
    }
}

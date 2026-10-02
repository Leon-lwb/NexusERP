package com.nexus.erp.pu.service;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.baomidou.dynamic.datasource.tx.TransactionContext;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import com.p6spy.engine.spy.P6DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.SimpleConnectionHandle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.CommonDataSource;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 竞争池当前读的最小 ERP 同连接资格校验，不开启事务、不取得新连接、不补绑资源。
 *
 * 使用现有单一 MyBatis 工厂的真实配置；仅接受显式注册的 ERP 物理数据源与该工厂的
 * 路由数据源共用同一个已同步的 Spring 连接持有者及 JDBC 连接。注册项是
 * ItemDataSource 时只接受无 Seata 的直接精确 Hikari 池，或已核标准 P6 包装指向同一池；
 * 所有注册项都须有限
 * 解析为同类已核池，拒绝未知委托和覆写子类，不以名称、路由上下文或 catalog 猜归属。
 * 普通路由事务只绑定路由资源键，没有物理归属证据，即使实际选中 ERP 也一律拒绝。
 *
 * 仅支持进入当前读前未绑定 MyBatis 会话的入口；预绑定会话无法在不取得执行器连接的
 * 条件下复核其归属，因此拒绝复用。线程绑定资源只允许明确 ERP 物理数据源与 MyBatis
 * 路由数据源两个键，且值必须为同一个 ERP 连接持有者；所有其他资源绑定，包括普通
 * 对象键和值，一律拒绝，不按资源类型默认放行。延迟连接、事务感知代理和动态多库事务
 * 同样失败关闭。本类不改变全局数据源或事务拓扑；专属入口另由内部事务执行器建立，
 * 不在资格校验中借连接、补绑或授予业务资格。
 * 资格仅对当前线程、当前事务有效，不授予采购权限、可信额度或任何正向业务写入资格。
 */
@Component
public class PuRequirementErpTransactionQualification {

    private final SqlSessionFactory sqlSessionFactory;

    public PuRequirementErpTransactionQualification(@Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 在任何物料 guard 或来源 Mapper 调用前要求可写、单库且已证明归属的实际 ERP 事务。
     * 无法证明时仅抛出固定业务错误，不向调用方暴露连接、元数据或底层异常中的敏感信息。
     */
    public void requireWritableErpTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()
            || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            || TransactionContext.getXID() != null) {
            throw new ServiceException("物料竞争池当前读必须加入可写且已同步的单库 ERP 实际事务");
        }

        ErpTransactionConfiguration sources = resolveConfiguration(sqlSessionFactory);
        DynamicRoutingDataSource routing = sources.routing();
        DataSource physical = sources.physical();
        if (TransactionSynchronizationManager.getResource(sqlSessionFactory) != null) {
            throw new ServiceException("物料竞争池拒绝复用无法核验执行器连接的既有 MyBatis 会话资源");
        }

        ConnectionHolder erpHolder = requiredHolder(physical);
        ConnectionHolder mapperHolder = requiredHolder(routing);
        if (erpHolder != mapperHolder) {
            throw new ServiceException("物料竞争池 ERP 与 MyBatis 未加入同一个实际连接持有者");
        }
        for (var binding : TransactionSynchronizationManager.getResourceMap().entrySet()) {
            if ((binding.getKey() != physical && binding.getKey() != routing) || binding.getValue() != erpHolder) {
                throw new ServiceException("物料竞争池只允许 ERP 物理与 MyBatis 路由的同一事务资源绑定，拒绝其他资源");
            }
        }

        boolean writable;
        try {
            Connection connection = erpHolder.getConnection();
            writable = connection == mapperHolder.getConnection() && !connection.isClosed()
                && !connection.getAutoCommit() && !connection.isReadOnly();
        } catch (SQLException | RuntimeException failure) {
            throw new ServiceException("物料竞争池 ERP 事务连接不可用或状态无法核验");
        }
        if (!writable) {
            throw new ServiceException("物料竞争池 ERP 事务连接必须有效、非自动提交且可写");
        }
    }

    /** 连续认证读取器仅可复核专属入口拥有的会话；外部既有会话仍按原规则拒绝。 */
    public void requireParticipatingWritableErpTransaction() {
        if (!PuRequirementErpTransactionExecutor.requireOwnedRepeatableReadParticipation(sqlSessionFactory)) {
            requireWritableErpTransaction();
        }
    }

    /** 完整采购认证链仅支持专属入口明示的可重复读事务，不接受外部布尔或路由猜测。 */
    public void requireSourceChainTransaction() {
        if (!PuRequirementErpTransactionExecutor.requireOwnedRepeatableReadParticipation(sqlSessionFactory)) {
            throw new ServiceException("完整采购认证链缺少专属可重复读事务归属");
        }
    }

    /** 仅读取真实工厂与显式注册身份；与专属入口共享解析，不借连接、补绑或放宽事务门禁。 */
    static ErpTransactionConfiguration resolveConfiguration(SqlSessionFactory sqlSessionFactory) {
        Configuration configuration = sqlSessionFactory.getConfiguration();
        Environment environment = configuration == null ? null : configuration.getEnvironment();
        if (environment == null || environment.getTransactionFactory() == null
            || environment.getTransactionFactory().getClass() != SpringManagedTransactionFactory.class
            || !(environment.getDataSource() instanceof DynamicRoutingDataSource routing)
            || !configuration.hasMapper(PuRequirementCompetitionPoolMapper.class)
            || !configuration.hasMapper(PuRequirementMaterialGuardMapper.class)) {
            throw new ServiceException("物料竞争池 MyBatis 工厂、数据源或 Mapper 配置无法证明同库事务");
        }

        Map<String, DataSource> registeredSources = Map.copyOf(routing.getDataSources());
        Map<String, ?> registeredGroups = Map.copyOf(routing.getGroupDataSources());
        DataSource registered = registeredSources.get("erp");
        if (registered == null || registeredGroups.containsKey("erp")) {
            throw new ServiceException("物料竞争池缺少唯一显式注册的 ERP 数据源");
        }
        Map<String, DataSource> physicalSources = new HashMap<>();
        Map<String, DataSource> decoratedSources = new HashMap<>();
        for (var entry : registeredSources.entrySet()) {
            DataSource registeredSource = entry.getValue();
            physicalSources.put(entry.getKey(), requiredPhysicalPool(registeredSource));
            decoratedSources.put(entry.getKey(), registeredSource.getClass() == ItemDataSource.class
                ? ((ItemDataSource) registeredSource).getDataSource() : registeredSource);
        }
        DataSource physical = physicalSources.get("erp");
        for (var entry : physicalSources.entrySet()) {
            if (!"erp".equals(entry.getKey()) && entry.getValue() == physical) {
                throw new ServiceException("物料竞争池 ERP 物理数据源与其他路由共用身份，拒绝归属猜测");
            }
        }
        return new ErpTransactionConfiguration(configuration, environment, routing, registered, physical,
            registeredSources, registeredGroups, Map.copyOf(physicalSources), Map.copyOf(decoratedSources));
    }

    /**
     * 仅接受精确 Hikari 物理池或精确 Item 注册包装，拒绝覆写子类、未知委托和嵌套包装。
     * Item 只支持无 Seata 的直接真实池，或 p6spy=true 的精确 P6DataSource 直接指向同一池。
     * Hikari 显式 DataSource 委托不在支持范围，防止已知池外形再隐藏共享池。
     * 全程只读类型和对象身份，不调用未知 unwrap、JNDI、getConnection 或 catalog。
     */
    private static DataSource requiredPhysicalPool(DataSource registered) {
        DataSource physical = registered;
        if (registered != null && registered.getClass() == ItemDataSource.class) {
            ItemDataSource item = (ItemDataSource) registered;
            physical = item.getRealDataSource();
            if (!Boolean.FALSE.equals(item.getSeata())) {
                throw new ServiceException("物料竞争池拒绝数据源跨库事务代理");
            }
            DataSource decorated = item.getDataSource();
            if (Boolean.FALSE.equals(item.getP6spy())) {
                if (decorated != physical) {
                    throw new ServiceException("物料竞争池注册包装未直接委托同一个已核物理池");
                }
            } else if (Boolean.TRUE.equals(item.getP6spy())) {
                requireP6PhysicalDelegate(decorated, physical);
            } else {
                throw new ServiceException("物料竞争池数据源包装状态无法核验");
            }
        }
        if (physical == null || physical.getClass() != HikariDataSource.class
            || ((HikariDataSource) physical).getDataSource() != null) {
            throw new ServiceException("物料竞争池仅支持明确的 Hikari 物理池，拒绝未知包装或覆写子类");
        }
        return physical;
    }

    /**
     * P6Spy 的公开 getRealDataSource 返回 JNDI 名，unwrap 又会委托内层，均不能证明池身份。
     * 仅对已核精确类型只读既有 delegate 字段，不写字段、不触发绑定或借连接；布局不符拒绝。
     */
    private static void requireP6PhysicalDelegate(DataSource decorated, DataSource physical) {
        if (decorated == null || decorated.getClass() != P6DataSource.class) {
            throw new ServiceException("物料竞争池拒绝未知或覆写的 P6 数据源包装");
        }
        try {
            Field delegate = P6DataSource.class.getDeclaredField("realDataSource");
            if (delegate.getType() != CommonDataSource.class || !delegate.trySetAccessible()) {
                throw new ServiceException("物料竞争池 P6 包装布局无法核验");
            }
            Object actual = delegate.get(decorated);
            if (actual == null || actual != physical) {
                throw new ServiceException("物料竞争池 P6 包装没有直接指向同一个已核真实池");
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new ServiceException("物料竞争池 P6 包装身份无法明确核验");
        }
    }

    /** 当前调用解析的身份快照；注册对象按引用比较，不凭名称、equals 或 catalog 猜同库。 */
    record ErpTransactionConfiguration(Configuration configuration, Environment environment,
                                      DynamicRoutingDataSource routing, DataSource registered, DataSource physical,
                                      Map<String, DataSource> registeredSources, Map<String, ?> registeredGroups,
                                      Map<String, DataSource> physicalSources, Map<String, DataSource> decoratedSources) {

        boolean sameIdentityAs(ErpTransactionConfiguration current) {
            return configuration == current.configuration && environment == current.environment
                && routing == current.routing && registered == current.registered && physical == current.physical
                && sameMappings(registeredSources, current.registeredSources)
                && sameMappings(registeredGroups, current.registeredGroups)
                && sameMappings(physicalSources, current.physicalSources)
                && sameMappings(decoratedSources, current.decoratedSources);
        }

        private static boolean sameMappings(Map<String, ?> expected, Map<String, ?> current) {
            if (expected.size() != current.size()) {
                return false;
            }
            for (var entry : expected.entrySet()) {
                if (current.get(entry.getKey()) != entry.getValue()) {
                    return false;
                }
            }
            return true;
        }
    }

    /** 仅读取已绑定的固定连接持有者，拒绝未同步、待回滚或可能按需借连接的句柄。 */
    private static ConnectionHolder requiredHolder(DataSource source) {
        Object resource = TransactionSynchronizationManager.getResource(source);
        if (!(resource instanceof ConnectionHolder holder) || !holder.isSynchronizedWithTransaction()
            || holder.isRollbackOnly() || !(holder.getConnectionHandle() instanceof SimpleConnectionHandle)) {
            throw new ServiceException("物料竞争池未绑定可核验的 ERP 同库事务连接");
        }
        return holder;
    }
}

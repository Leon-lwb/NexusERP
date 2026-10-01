package com.nexus.erp.pu.service;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.baomidou.dynamic.datasource.tx.TransactionContext;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.SimpleConnectionHandle;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 竞争池当前读的最小 ERP 同连接资格校验，不开启事务、不取得新连接、不补绑资源。
 *
 * 使用现有单一 MyBatis 工厂的真实配置；仅接受显式注册的 ERP 物理数据源与该工厂的
 * 路由数据源共用同一个已同步的 Spring 连接持有者及 JDBC 连接。注册项是
 * ItemDataSource 时，物理身份取其真实数据源，不以名称、路由上下文或 catalog 猜归属。
 * 普通路由事务只绑定路由资源键，没有物理归属证据，即使实际选中 ERP 也一律拒绝。
 *
 * 仅支持进入当前读前未绑定 MyBatis 会话的入口；预绑定会话无法在不取得执行器连接的
 * 条件下复核其归属，因此拒绝复用。线程绑定资源只允许明确 ERP 物理数据源与 MyBatis
 * 路由数据源两个键，且值必须为同一个 ERP 连接持有者；所有其他资源绑定，包括普通
 * 对象键和值，一律拒绝，不按资源类型默认放行。延迟连接、事务感知代理和动态多库事务
 * 同样失败关闭。本类不改变全局数据源或事务拓扑，不证明现有应用已有可靠绑定入口。
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

        Configuration configuration = sqlSessionFactory.getConfiguration();
        Environment environment = configuration == null ? null : configuration.getEnvironment();
        if (environment == null || environment.getTransactionFactory() == null
            || environment.getTransactionFactory().getClass() != SpringManagedTransactionFactory.class
            || !(environment.getDataSource() instanceof DynamicRoutingDataSource routing)
            || !configuration.hasMapper(PuRequirementCompetitionPoolMapper.class)
            || !configuration.hasMapper(PuRequirementMaterialGuardMapper.class)) {
            throw new ServiceException("物料竞争池 MyBatis 工厂、数据源或 Mapper 配置无法证明同库事务");
        }

        if (TransactionSynchronizationManager.getResource(sqlSessionFactory) != null) {
            throw new ServiceException("物料竞争池拒绝复用无法核验执行器连接的既有 MyBatis 会话资源");
        }

        DataSource registered = routing.getDataSources().get("erp");
        if (registered == null || routing.getGroupDataSources().containsKey("erp")) {
            throw new ServiceException("物料竞争池缺少唯一显式注册的 ERP 数据源");
        }
        DataSource physical = registered;
        if (registered instanceof ItemDataSource item) {
            if (!Boolean.FALSE.equals(item.getSeata())) {
                throw new ServiceException("物料竞争池不接受无法证明单库边界的数据源事务代理");
            }
            physical = item.getRealDataSource();
        }
        if (physical == null || physical instanceof DynamicRoutingDataSource || physical instanceof ItemDataSource
            || physical instanceof LazyConnectionDataSourceProxy || physical instanceof TransactionAwareDataSourceProxy
            || physical instanceof org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource) {
            throw new ServiceException("物料竞争池 ERP 物理数据源身份无法明确核验");
        }
        for (var entry : routing.getDataSources().entrySet()) {
            DataSource other = entry.getValue() instanceof ItemDataSource item ? item.getRealDataSource() : entry.getValue();
            if (!"erp".equals(entry.getKey()) && other == physical) {
                throw new ServiceException("物料竞争池 ERP 物理数据源与其他路由共用身份，拒绝归属猜测");
            }
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

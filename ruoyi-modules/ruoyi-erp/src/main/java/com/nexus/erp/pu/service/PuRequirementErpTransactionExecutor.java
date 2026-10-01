package com.nexus.erp.pu.service;

import com.baomidou.dynamic.datasource.tx.TransactionContext;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification.ErpTransactionConfiguration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSession;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.mybatis.spring.SqlSessionHolder;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.JdbcTransactionObjectSupport;
import org.springframework.jdbc.datasource.SimpleConnectionHandle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Supplier;

/**
 * 内部采购需求基础件的专属 ERP 物理事务入口，不接控制器或现有采购业务门禁。
 *
 * 从命名 MyBatis 工厂的真实环境解析唯一显式 ERP 物理数据源，以调用私有的 Spring
 * 本地事务管理器开启可写事务，只有该管理器实际取得的 holder 才能提供路由别名。
 * 不加入、挂起或修补外部事务，不更改全局 TM Bean，不证明 AI master 与 ERP 跨库 ACID。
 *
 * 仅支持当前框架使用的 DefaultSqlSessionFactory/DefaultSqlSession 正常 Spring 参与。
 * 末端只读核验 MyBatis 同步对象的 holder、factory、顺序及解绑状态；未知实现或框架布局变化
 * 失败关闭，不借额外连接、不修改框架内部字段，也不放宽原资格对已绑定会话的拒绝。
 * 回调异常或已核 late 漂移中的外来 factory 绑定只保留或恢复最终状态；原生 MyBatis
 * 收尾可能临时解绑，
 * 已核观察点还在原生清理前记录正常参与后的 late 漂移，并由 JDBC 提交前检查拒绝。
 * 本入口在 Spring 收尾末端仅向空键恢复同一引用，已有不同引用不覆盖；该路径仍然失败，
 * 不将恢复资源当作业务授权或正常会话。入口之前任何资源仍先拒绝且不触碰。
 * 支持已核标准 Item/P6/Hikari 注册链，但本专属路径直接在真实 Hikari 上借连接，
 * 不调用注册 P6DataSource.getConnection，故不产生本连接级 Spy 日志；全局注册、Spy
 * 开关与安全审计保持原样，不能声称该路径仍执行了 Spy。
 * 本类只建立事务归属；调用方将来仍须负责权限、来源、单位、额度及幂等校验。
 */
@Component
public class PuRequirementErpTransactionExecutor {

    private static final int MYBATIS_SYNCHRONIZATION_ORDER = DataSourceUtils.CONNECTION_SYNCHRONIZATION_ORDER - 1;

    private final SqlSessionFactory sqlSessionFactory;

    /** 构造仅保存已有命名工厂，不解析 catalog、不借连接或启动事务。 */
    public PuRequirementErpTransactionExecutor(@Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        if (sqlSessionFactory == null) {
            throw new ServiceException("ERP 专属事务入口缺少 MyBatis 工厂");
        }
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 无任何已有事务、同步、资源或 XID 时，在显式 ERP 物理连接上执行内部回调。
     * 回调不是采购授权或通用跨库执行许可；不生成草稿、申请行、占用或其他业务事实。
     * 异常或 rollback-only 必须失败，只有真实提交及自有资源清理均完成才返回回调值。
     * 回调中的 VirtualMachineError/ThreadDeath 先交由 Spring 回滚、清理，再按原对象传播；
     * 回滚或收尾故障不得覆盖已捕获的原对象，但这不证明回滚已成功。
     * 提交异常可能意味着结果无法确认，不将其描述为已成功提交或必然回滚。
     *
     * @param tenantId 必须与当前已启用租户一致的标识
     * @param callback 将来由已授权 ERP 内部编排提供的回调，不接受外部事务或资格布尔
     * @return 实际提交及清理完成后的回调值
     */
    public <T> T execute(String tenantId, Supplier<T> callback) {
        requireFreshThread(tenantId);
        if (callback == null) {
            throw new ServiceException("ERP 专属事务需要内部回调");
        }
        Error[] callbackFatal = new Error[1];
        try {
            if (sqlSessionFactory.getClass() != DefaultSqlSessionFactory.class) {
                throw failed();
            }
            OwnedTransaction owned = new OwnedTransaction(tenantId,
                PuRequirementErpTransactionQualification.resolveConfiguration(sqlSessionFactory));
            ErpTransactionManager manager = new ErpTransactionManager(owned);
            TransactionTemplate transaction = new TransactionTemplate(manager);
            transaction.setReadOnly(false);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            T result = transaction.execute(status -> {
                try {
                    if (!status.isNewTransaction()) {
                        throw failed();
                    }
                    owned.bindRoutingAlias();
                    T value = callback.get();
                    if (status.isRollbackOnly()) {
                        throw failed();
                    }
                    owned.requireResources(Phase.CALLBACK_RETURN);
                    return value;
                } catch (VirtualMachineError | ThreadDeath fatal) {
                    /** 先保存原对象；即使框架回滚或收尾再失败，也不能用新诊断替换它。 */
                    callbackFatal[0] = fatal;
                    owned.prepareRollbackSessionCleanup();
                    owned.rememberForeignFactoryBinding();
                    throw fatal;
                } catch (RuntimeException | Error failure) {
                    /** 先移除底层诊断再交给 Spring 回滚，不将 SQL/凭证放入 message/cause/suppressed。 */
                    owned.prepareRollbackSessionCleanup();
                    owned.rememberForeignFactoryBinding();
                    throw failed();
                }
            });
            owned.requireSuccessfulCompletion();
            return result;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            /** 回调原对象优先于收尾再抛出的致命错误；不认定回滚已成功。 */
            rethrowCallbackFatal(callbackFatal[0]);
            throw fatal;
        } catch (RuntimeException | Error failure) {
            rethrowCallbackFatal(callbackFatal[0]);
            throw failed();
        }
    }

    /** 原始回调致命错误按同一对象传播，不附加回滚诊断、异常链或成功状态。 */
    private static void rethrowCallbackFatal(Error fatal) {
        if (fatal instanceof VirtualMachineError machineFailure) {
            throw machineFailure;
        }
        if (fatal instanceof ThreadDeath threadFailure) {
            throw threadFailure;
        }
    }

    /** 入口及管理器起点重复前置拒绝，不以路由标签或已有资源取得资格。 */
    private static void requireFreshThread(String tenantId) {
        requireTenant(tenantId);
        if (TransactionSynchronizationManager.isActualTransactionActive()
            || TransactionSynchronizationManager.isSynchronizationActive()
            || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            || !TransactionSynchronizationManager.getResourceMap().isEmpty()
            || TransactionContext.getXID() != null) {
            throw new ServiceException("ERP 专属事务拒绝已有事务、同步、资源或跨库事务上下文");
        }
    }

    private static void requireTenant(String tenantId) {
        if (!TenantHelper.isEnable() || tenantId == null || tenantId.isBlank()
            || !tenantId.equals(TenantHelper.getTenantId())) {
            throw new ServiceException("ERP 专属事务租户未启用或与当前租户不匹配");
        }
    }

    private static ServiceException failed() {
        return new ServiceException("ERP 专属物理事务未成功完成，不能确认提交结果");
    }

    private enum Phase { CALLBACK_RETURN, BEFORE_FLUSH, BEFORE_JDBC }

    /** 单次调用的真实管理器持有者与生命周期，不提供可被调用方设置的资格标记。 */
    private final class OwnedTransaction {
        private final String tenantId;
        private final ErpTransactionConfiguration sources;
        private ConnectionHolder holder;
        private Connection connection;
        private SqlSessionHolder sessionHolder;
        private TransactionSynchronization sessionSynchronization;
        private OwnedSqlSessionSynchronization guardedSessionSynchronization;
        private Object foreignFactoryBinding;
        private int completionStatus = TransactionSynchronization.STATUS_UNKNOWN;
        private boolean jdbcCommitted;
        private boolean cleaned;
        private boolean factoryBindingViolation;

        private OwnedTransaction(String tenantId, ErpTransactionConfiguration sources) {
            this.tenantId = tenantId;
            this.sources = sources;
        }

        private void requireIdentity() {
            if (!sources.sameIdentityAs(PuRequirementErpTransactionQualification.resolveConfiguration(sqlSessionFactory))) {
                throw failed();
            }
        }

        /** 只将本管理器已实际取得的物理 holder 提供给既有路由键，不接受外部补绑。 */
        private void bindRoutingAlias() {
            requireContext();
            requireIdentity();
            if (TransactionSynchronizationManager.getResourceMap().size() != 1
                || TransactionSynchronizationManager.getResourceMap().get(sources.physical()) != holder) {
                throw failed();
            }
            requireConnection();
            TransactionSynchronizationManager.bindResource(sources.routing(), holder);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public int getOrder() {
                    return MYBATIS_SYNCHRONIZATION_ORDER - 1;
                }

                @Override
                public void beforeCompletion() {
                    /** 全部 beforeCommit 已结束，在已核 MyBatis 原生清理前记住漂移，不靠抛错阻止 commit。 */
                    rememberLateFactoryViolation();
                }

                @Override
                public void afterCompletion(int status) {
                    completionStatus = status;
                }
            });
        }

        private void requireContext() {
            requireTenant(tenantId);
            if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || TransactionContext.getXID() != null) {
                throw failed();
            }
        }

        /** 只读取管理器已经取得的固定连接，不调用任何 DataSource.getConnection。 */
        private void requireConnection() {
            if (holder == null || !(holder.getConnectionHandle() instanceof SimpleConnectionHandle)
                || !holder.isSynchronizedWithTransaction() || holder.isRollbackOnly()
                || TransactionSynchronizationManager.getResource(sources.physical()) != holder) {
                throw failed();
            }
            try {
                if (holder.getConnection() != connection || connection.isClosed()
                    || connection.getAutoCommit() || connection.isReadOnly()) {
                    throw failed();
                }
            } catch (SQLException failure) {
                throw failed();
            }
        }

        private void requireResources(Phase phase) {
            requireContext();
            requireIdentity();
            requireConnection();
            if (TransactionSynchronizationManager.getResource(sources.routing()) != holder) {
                throw failed();
            }
            Object currentSession = TransactionSynchronizationManager.getResource(sqlSessionFactory);
            if (phase == Phase.CALLBACK_RETURN) {
                if (currentSession != null) {
                    if (currentSession.getClass() != SqlSessionHolder.class) {
                        throw failed();
                    }
                    SqlSessionHolder candidate = (SqlSessionHolder) currentSession;
                    requireConcreteSession(candidate);
                    sessionSynchronization = findSessionSynchronization(candidate);
                    sessionHolder = candidate;
                } else {
                    findSessionSynchronization(null);
                }
            } else if (currentSession != sessionHolder) {
                /** SqlSessionTemplate 正常 released 后，MyBatis 会在 beforeCompletion 解绑并关闭。 */
                if (phase != Phase.BEFORE_JDBC || currentSession != null || sessionHolder == null
                    || sessionSynchronization != findSessionSynchronization(sessionHolder)
                    || !Boolean.FALSE.equals(participationField(sessionSynchronization, "holderActive", boolean.class))) {
                    throw failed();
                }
            } else if (sessionHolder != null) {
                if (sessionSynchronization != findSessionSynchronization(sessionHolder)) {
                    throw failed();
                }
                requireConcreteSession(sessionHolder);
            } else {
                findSessionSynchronization(null);
            }

            for (var binding : TransactionSynchronizationManager.getResourceMap().entrySet()) {
                if (binding.getKey() == sources.physical() || binding.getKey() == sources.routing()) {
                    if (binding.getValue() != holder) {
                        throw failed();
                    }
                } else if (binding.getKey() != sqlSessionFactory || sessionHolder == null
                    || binding.getValue() != sessionHolder) {
                    throw failed();
                }
            }
        }

        /** 正常已注册的真实会话才可读取连接；路由 holder 已核验，读取只能复用本次物理连接。 */
        private void requireConcreteSession(SqlSessionHolder candidate) {
            SqlSession session = candidate.getSqlSession();
            if (!candidate.isSynchronizedWithTransaction() || candidate.isRollbackOnly()
                || session == null || session.getClass() != DefaultSqlSession.class
                || session.getConfiguration() != sources.configuration()) {
                throw failed();
            }
            findSessionSynchronization(candidate);
            if (session.getConnection() != connection) {
                throw failed();
            }
        }

        /** 同步凭据必须属于本工厂与同一 holder；孤立/替换/其他工厂会话均失败关闭。 */
        private TransactionSynchronization findSessionSynchronization(SqlSessionHolder expected) {
            TransactionSynchronization found = null;
            boolean guardPresent = guardedSessionSynchronization == null;
            for (TransactionSynchronization registered : TransactionSynchronizationManager.getSynchronizations()) {
                TransactionSynchronization synchronization = registered;
                if (registered == guardedSessionSynchronization) {
                    guardPresent = true;
                    synchronization = guardedSessionSynchronization.delegate;
                }
                Class<?> type = synchronization.getClass();
                if (type.getEnclosingClass() != SqlSessionUtils.class
                    || !"SqlSessionSynchronization".equals(type.getSimpleName())) {
                    continue;
                }
                if (synchronization.getOrder() != MYBATIS_SYNCHRONIZATION_ORDER
                    || expected == null || found != null
                    || participationField(synchronization, "sessionFactory", SqlSessionFactory.class) != sqlSessionFactory
                    || participationField(synchronization, "holder", SqlSessionHolder.class) != expected) {
                    throw failed();
                }
                found = synchronization;
            }
            if (!guardPresent || (expected != null && found == null)) {
                throw failed();
            }
            return found;
        }

        /**
         * 回调异常或待回滚时也保护唯一已核原生同步，不能等待只在正常提交执行的准备阶段。
         * 鉴别只用于清理观察：只读原生工厂、持有者及明确会话配置，不读取或借用其连接，
         * 不要求当前工厂键仍是原引用，也不为回调资源、权限或业务来源授予资格。
         */
        private void prepareRollbackSessionCleanup() {
            if (guardedSessionSynchronization != null) {
                return;
            }
            TransactionSynchronization found = null;
            SqlSessionHolder candidate = null;
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                Class<?> type = synchronization.getClass();
                if (type.getEnclosingClass() != SqlSessionUtils.class
                    || !"SqlSessionSynchronization".equals(type.getSimpleName())) {
                    continue;
                }
                if (participationField(synchronization, "sessionFactory", SqlSessionFactory.class) != sqlSessionFactory) {
                    continue;
                }
                if (found != null || synchronization.getOrder() != MYBATIS_SYNCHRONIZATION_ORDER) {
                    return;
                }
                SqlSessionHolder current = (SqlSessionHolder) participationField(synchronization, "holder", SqlSessionHolder.class);
                SqlSession session = current.getSqlSession();
                if (current.getClass() != SqlSessionHolder.class || session == null
                    || session.getClass() != DefaultSqlSession.class
                    || session.getConfiguration() != sources.configuration()) {
                    return;
                }
                found = synchronization;
                candidate = current;
            }
            if (found != null) {
                sessionHolder = candidate;
                sessionSynchronization = found;
                protectNativeSessionCleanup();
            }
        }

        /**
         * 只替换本次已核 MyBatis 同步的注册引用，其他对象及同序相对顺序保持不变。
         * 公共同步注册 API 不改工厂、事务元数据或框架字段；不能用于外部事务补绑。
         * 保护紧邻原生清理，避免较早观察之后的同序回调替换被原生解绑抹去。
         */
        private void protectNativeSessionCleanup() {
            if (sessionHolder == null) {
                return;
            }
            if (guardedSessionSynchronization != null) {
                throw failed();
            }
            List<TransactionSynchronization> original = TransactionSynchronizationManager.getSynchronizations();
            if (original.stream().filter(sync -> sync == sessionSynchronization).count() != 1) {
                throw failed();
            }
            OwnedSqlSessionSynchronization guard = new OwnedSqlSessionSynchronization(sessionSynchronization);
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.initSynchronization();
            guardedSessionSynchronization = guard;
            for (TransactionSynchronization synchronization : original) {
                TransactionSynchronizationManager.registerSynchronization(
                    synchronization == sessionSynchronization ? guard : synchronization);
            }
            if (findSessionSynchronization(sessionHolder) != sessionSynchronization) {
                throw failed();
            }
        }

        /**
         * 本次同步的透明有界委托；保留全部公共生命周期，仅在原生清理之前记录归属漂移。
         * 不修改被委托对象；错误标记仍由 JDBC 提交前检查传播，而非依赖清理回调抛异常。
         */
        private final class OwnedSqlSessionSynchronization implements TransactionSynchronization {
            private final TransactionSynchronization delegate;

            private OwnedSqlSessionSynchronization(TransactionSynchronization delegate) {
                this.delegate = delegate;
            }

            @Override
            public int getOrder() {
                return delegate.getOrder();
            }

            @Override
            public void suspend() {
                delegate.suspend();
            }

            @Override
            public void resume() {
                delegate.resume();
            }

            @Override
            public void flush() {
                delegate.flush();
            }

            @Override
            public void savepoint(Object savepoint) {
                delegate.savepoint(savepoint);
            }

            @Override
            public void savepointRollback(Object savepoint) {
                delegate.savepointRollback(savepoint);
            }

            @Override
            public void beforeCommit(boolean readOnly) {
                delegate.beforeCommit(readOnly);
            }

            @Override
            public void beforeCompletion() {
                rememberLateFactoryViolation();
                delegate.beforeCompletion();
            }

            @Override
            public void afterCommit() {
                delegate.afterCommit();
            }

            @Override
            public void afterCompletion(int status) {
                delegate.afterCompletion(status);
            }
        }

        /**
         * 正常参与的 holder 在原生清理开始前必须仍归本次事务；缺失或替换均记为失败。
         * 本方法不以 beforeCompletion 抛错充当保护，真正可传播的拒绝位于管理器 doCommit。
         * 仅观察本次已经核验的会话，不改其他同步或框架字段；外来引用只用于失败收尾。
         */
        private void rememberLateFactoryViolation() {
            if (sessionHolder == null) {
                return;
            }
            Object current = TransactionSynchronizationManager.getResource(sqlSessionFactory);
            if (current != sessionHolder) {
                factoryBindingViolation = true;
                if (current != null) {
                    foreignFactoryBinding = current;
                }
            }
        }

        /**
         * 在回调异常出口记录外来 factory 引用，不读取外来会话连接或授权业务。
         * 与本工厂真实 MyBatis 同步持有者同一引用的正常资源不记录、不恢复。
         */
        private void rememberForeignFactoryBinding() {
            Object binding = TransactionSynchronizationManager.getResource(sqlSessionFactory);
            if (binding == null) {
                return;
            }
            for (TransactionSynchronization registered : TransactionSynchronizationManager.getSynchronizations()) {
                TransactionSynchronization synchronization = registered == guardedSessionSynchronization
                    ? guardedSessionSynchronization.delegate : registered;
                Class<?> type = synchronization.getClass();
                if (type.getEnclosingClass() == SqlSessionUtils.class
                    && "SqlSessionSynchronization".equals(type.getSimpleName())
                    && participationField(synchronization, "sessionFactory", SqlSessionFactory.class) == sqlSessionFactory
                    && participationField(synchronization, "holder", SqlSessionHolder.class) == binding) {
                    return;
                }
            }
            foreignFactoryBinding = binding;
        }

        /**
         * 异常 Spring 收尾后保留或恢复最终状态，不宣称框架从未临时解绑。
         * 当前已有不同引用时绝不覆盖；本路径已经异常，不能转成成功返回。
         */
        private void restoreForeignFactoryBinding() {
            if (foreignFactoryBinding != null
                && TransactionSynchronizationManager.getResource(sqlSessionFactory) == null) {
                TransactionSynchronizationManager.bindResource(sqlSessionFactory, foreignFactoryBinding);
            }
        }

        private void requireSuccessfulCompletion() {
            requireTenant(tenantId);
            if (!jdbcCommitted || !cleaned || completionStatus != TransactionSynchronization.STATUS_COMMITTED
                || TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.getResourceMap().isEmpty() || TransactionContext.getXID() != null) {
                throw failed();
            }
            try {
                if (!connection.isClosed()) {
                    throw failed();
                }
            } catch (SQLException failure) {
                throw failed();
            }
        }
    }

    /**
     * 只读当前 MyBatis 私有同步凭据的三个已核对字段，禁止写入或按未知会话默认放行。
     * 私有布局不兼容或不可访问时失败关闭；不反射 Spring 事务内部字段。
     */
    private static Object participationField(TransactionSynchronization synchronization, String name, Class<?> type) {
        try {
            Field field = synchronization.getClass().getDeclaredField(name);
            if (field.getType() != type || !field.trySetAccessible()) {
                throw failed();
            }
            return field.get(synchronization);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw failed();
        }
    }

    /** 每次调用私有的 Spring 物理管理器，不注册全局 Bean，也不接收外部事务管理器。 */
    private final class ErpTransactionManager extends DataSourceTransactionManager {
        private static final long serialVersionUID = 1L;
        private final OwnedTransaction owned;

        private ErpTransactionManager(OwnedTransaction owned) {
            super(owned.sources.physical());
            this.owned = owned;
            setNestedTransactionAllowed(false);
            setValidateExistingTransaction(true);
        }

        @Override
        protected Object doGetTransaction() {
            requireFreshThread(owned.tenantId);
            owned.requireIdentity();
            return super.doGetTransaction();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            requireFreshThread(owned.tenantId);
            owned.requireIdentity();
            super.doBegin(transaction, definition);
            owned.holder = ((JdbcTransactionObjectSupport) transaction).getConnectionHolder();
            owned.connection = owned.holder.getConnection();
        }

        @Override
        protected void prepareForCommit(DefaultTransactionStatus status) {
            owned.requireResources(Phase.BEFORE_FLUSH);
            owned.protectNativeSessionCleanup();
            super.prepareForCommit(status);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            /** beforeCompletion 异常会被 Spring 吞掉；必须在可传播失败点拒绝已记录的 factory 漂移。 */
            if (owned.factoryBindingViolation) {
                throw failed();
            }
            /** 位于全部 beforeCommit/正常 MyBatis beforeCompletion 之后，紧邻真实物理提交。 */
            owned.requireResources(Phase.BEFORE_JDBC);
            super.doCommit(status);
            owned.jdbcCommitted = true;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            try {
                /** commit/rollback 与 MyBatis 同步已经结束；只解绑本次仍拥有的别名。 */
                if (TransactionSynchronizationManager.getResource(owned.sources.routing()) == owned.holder) {
                    TransactionSynchronizationManager.unbindResource(owned.sources.routing());
                }
                if (TransactionSynchronizationManager.getResource(owned.sources.physical()) == owned.holder) {
                    super.doCleanupAfterCompletion(transaction);
                } else {
                    /** 物理键已被替换/移除时，不让父类误删调用方资源；仅释放管理器已取得的连接。 */
                    DataSourceUtils.releaseConnection(owned.connection, null);
                    owned.holder.clear();
                }
                owned.cleaned = true;
            } finally {
                /** 只收尾已拒绝事务的外来 factory 最终绑定，不改 MyBatis/Spring 私有字段。 */
                owned.restoreForeignFactoryBinding();
            }
        }
    }
}

package com.nexus.mes.qc.service;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.dynamic.datasource.tx.TransactionContext;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.iqc.IqcNativeSchema;
import com.nexus.common.mq.iqc.ReceiptIqcSource;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.qc.mapper.QcReceiptIqcEvidenceMapper;
import com.nexus.mes.qc.mapper.QcReceiptIqcEvidenceMapper.DetailBody;
import com.nexus.mes.qc.mapper.QcReceiptIqcEvidenceMapper.InspectionRow;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 跨模块接口的可信只读 MES 实现。显式原生 MES 路由只能在新线程中、借用连接之前
 * 建立。绝不加入 ERP 或 master 数据源的
 * 事务，也绝不写入 MES、ERP 或 master。单头、版本和明细使用同一个只读
 * 可重复读快照；此观测不承诺分布式锁或跨数据库 ACID。
 */
@Service
public class QcReceiptIqcEvidenceService implements ReceiptIqcSource {
    private final QcReceiptIqcEvidenceMapper mapper;
    private final SqlSessionFactory factory;

    public QcReceiptIqcEvidenceService(QcReceiptIqcEvidenceMapper mapper,
        @Qualifier("sqlSessionFactory") SqlSessionFactory factory) {
        this.mapper = Objects.requireNonNull(mapper);
        this.factory = Objects.requireNonNull(factory);
    }

    @Override
    public Inspection readCompleted(String tenantId, Long inspectionId) {
        return readSnapshot(tenantId, inspectionId, false);
    }

    @Override
    public Inspection readCompletedIdentity(String tenantId, Long inspectionId) {
        return readSnapshot(tenantId, inspectionId, true);
    }

    private Inspection readSnapshot(String tenantId, Long inspectionId, boolean identityOnly) {
        requireTenant(tenantId);
        if (inspectionId == null || inspectionId <= 0) throw fail("IQC_MES_IDENTITY_INVALID");
        requireFreshThread();
        Environment environment = factory.getConfiguration().getEnvironment();
        if (factory.getClass() != DefaultSqlSessionFactory.class || environment == null
            || environment.getTransactionFactory() == null
            || environment.getTransactionFactory().getClass() != SpringManagedTransactionFactory.class
            || !(environment.getDataSource() instanceof DynamicRoutingDataSource routing)
            || !factory.getConfiguration().hasMapper(QcReceiptIqcEvidenceMapper.class)) {
            throw fail("IQC_MES_SOURCE_TOPOLOGY_UNSUPPORTED");
        }
        DataSource mes = routing.getDataSources().get("mes");
        if (mes == null || mes == routing || routing.getGroupDataSources().containsKey("mes")) {
            throw fail("IQC_MES_SOURCE_UNAVAILABLE");
        }
        DynamicDataSourceContextHolder.push("mes");
        try {
            if (routing.getDataSource("mes") != mes) throw fail("IQC_MES_SOURCE_TOPOLOGY_UNSUPPORTED");
            TransactionTemplate snapshot = new TransactionTemplate(new DataSourceTransactionManager(routing));
            snapshot.setReadOnly(true);
            snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            Inspection actual = snapshot.execute(status -> {
                if (!status.isNewTransaction()) throw fail("IQC_MES_SOURCE_REQUIRES_FRESH_THREAD");
                requireSnapshot(tenantId, routing, mes);
                requireNativeDefinition(identityOnly);
                InspectionRow row = identityOnly ? mapper.selectInspectionIdentity(tenantId, inspectionId)
                    : mapper.selectInspection(tenantId, inspectionId);
                requireCurrent(row, tenantId, inspectionId, identityOnly);
                List<Long> versions = identityOnly ? List.of(inspectionId) : mapper.selectInspectionVersions(tenantId, row.getReceiveId(), row.getMaterialId());
                if (versions == null || versions.size() != 1 || !inspectionId.equals(versions.get(0))) {
                    throw fail("IQC_MES_HISTORICAL_VERSION_AMBIGUOUS");
                }
                List<DetailBody> details = identityOnly ? List.of() : mapper.selectInspectionBodies(tenantId, inspectionId);
                if (details == null || details.stream().anyMatch(Objects::isNull)) throw fail("IQC_MES_BODY_UNAVAILABLE");
                List<DetailBody> ordered = new ArrayList<>(details);
                Set<Long> detailIds = new HashSet<>();
                for (DetailBody detail : ordered) {
                    if (detail.getDetailId() == null || detail.getDetailId() <= 0 || !detailIds.add(detail.getDetailId())
                        || !tenantId.equals(detail.getTenantId()) || !inspectionId.equals(detail.getCheckId())
                        || !"IQC".equals(detail.getCheckType()) || blank(detail.getActualBody())) {
                        throw fail("IQC_MES_DETAIL_SCOPE_INVALID");
                    }
                }
                ordered.sort(Comparator.comparing(DetailBody::getDetailId));
                String bodySha = actualBodySha(row, ordered);
                requireSnapshot(tenantId, routing, mes);
                requireNativeDefinition(identityOnly);
                long observedAt = System.currentTimeMillis();
                if (row.getInspectTime().getTime() > observedAt || (row.getUpdateTime() != null
                    && row.getUpdateTime().getTime() > observedAt)) throw fail("IQC_MES_TIME_INVALID");
                return new Inspection(tenantId, inspectionId, row.getIqcNo(), row.getReceiveId(), row.getMaterialId(),
                    identityOnly ? null : row.getQuantity(), row.getStatus(), row.getInspectResult(), row.getInspectorId(),
                    row.getCreateTime().getTime(), row.getInspectTime().getTime(),
                    row.getUpdateTime() == null ? null : row.getUpdateTime().getTime(), observedAt,
                    bodySha, "QC_IQC_CURRENT_V1:" + bodySha);
            });
            requireFreshThread();
            return actual;
        } catch (ServiceException invalidSource) {
            throw invalidSource;
        } catch (RuntimeException unavailable) {
            // 不得在 MQ 日志中暴露原始来源数据主体、SQL 异常、连接地址、密钥或数据库目录。
            throw fail("IQC_MES_SOURCE_UNAVAILABLE");
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    private void requireNativeDefinition(boolean identityOnly) {
        var columns = mapper.selectNativeColumns(); var indexes = mapper.selectNativeIndexes();
        if (identityOnly) IqcNativeSchema.requireMesIdentityDefinition(columns, indexes);
        else IqcNativeSchema.requireMesDefinition(columns, indexes);
    }

    private void requireSnapshot(String tenantId, DynamicRoutingDataSource routing, DataSource mes) {
        requireTenant(tenantId);
        if (routing.getDataSources().get("mes") != mes || routing.getDataSource("mes") != mes
            || !"mes".equals(DynamicDataSourceContextHolder.peek())
            || !TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            || !Integer.valueOf(Connection.TRANSACTION_REPEATABLE_READ).equals(
                TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
            || TransactionContext.getXID() != null
            || !(TransactionSynchronizationManager.getResource(routing) instanceof ConnectionHolder holder)
            || !holder.isSynchronizedWithTransaction()) throw fail("IQC_MES_SNAPSHOT_INVALID");
        try {
            Connection connection = holder.getConnection();
            if (connection.isClosed() || connection.getAutoCommit() || !connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_REPEATABLE_READ) {
                throw fail("IQC_MES_SNAPSHOT_INVALID");
            }
        } catch (SQLException unavailable) { throw fail("IQC_MES_SNAPSHOT_INVALID"); }
        for (Object key : TransactionSynchronizationManager.getResourceMap().keySet()) {
            if (key != routing && key != factory) throw fail("IQC_MES_SNAPSHOT_FOREIGN_RESOURCE");
        }
    }

    private static void requireCurrent(InspectionRow row, String tenantId, Long inspectionId, boolean identityOnly) {
        if (row == null || !tenantId.equals(row.getTenantId()) || !inspectionId.equals(row.getIqcId())
            || !"0".equals(row.getDelFlag()) || !"COMPLETED".equals(row.getStatus())
            || !("QUALIFIED".equals(row.getInspectResult()) || "UNQUALIFIED".equals(row.getInspectResult()))
            || row.getReceiveId() == null || row.getReceiveId() <= 0 || row.getMaterialId() == null || row.getMaterialId() <= 0
            || blank(row.getIqcNo()) || row.getIqcNo().length() > 64 || blank(row.getActualBody())
            || row.getInspectorId() == null || row.getInspectorId() <= 0
            || row.getInspectTime() == null || row.getCreateTime() == null || row.getCreateTime().getTime() <= 0
            || row.getInspectTime().before(row.getCreateTime())
            || (row.getUpdateTime() != null && row.getUpdateTime().before(row.getCreateTime()))
            || (!identityOnly && (row.getQuantity() == null || row.getQuantity().signum() <= 0
                || row.getQuantity().stripTrailingZeros().scale() > 4
                || row.getQuantity().compareTo(BigDecimal.TEN.pow(10)) >= 0))) {
            throw fail("IQC_MES_CURRENT_COMPLETION_REQUIRED");
        }
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || !tenantId.matches("[0-9]{6,20}") || !TenantHelper.isEnable()
            || !tenantId.equals(TenantHelper.getTenantId())) throw fail("IQC_MES_TENANT_MISMATCH");
    }
    private static void requireFreshThread() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            || TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.getResourceMap().isEmpty()
            || TransactionSynchronizationManager.isCurrentTransactionReadOnly() || TransactionContext.getXID() != null) {
            throw fail("IQC_MES_SOURCE_REQUIRES_FRESH_THREAD");
        }
    }
    private static String actualBodySha(InspectionRow row, List<DetailBody> details) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "QC_IQC_CURRENT_V1"); update(digest, row.getActualBody());
            for (DetailBody detail : details) {
                update(digest, detail.getDetailId().toString()); update(digest, detail.getActualBody());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) { throw fail("IQC_MES_DIGEST_UNAVAILABLE"); }
    }
    private static void update(MessageDigest digest, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':'); digest.update(bytes);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static ServiceException fail(String code) { return new ServiceException(code); }
}

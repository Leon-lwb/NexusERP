package com.nexus.mes.qc.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.dynamic.datasource.tx.TransactionContext;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.iqc.IqcNativeSchema;
import com.nexus.common.mq.iqc.ReceiptIqcBirthPort;
import com.nexus.common.mq.iqc.ReceiptIqcBirthPort.Birth;
import com.nexus.common.mq.iqc.ReceiptIqcBirthSupport;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.qc.domain.QcIqc;
import com.nexus.mes.qc.domain.vo.QcIqcVo;
import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper;
import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper.Binding;
import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper.NativeColumn;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * IQC 原生出生调度入口，不加入调用方数据源或事务。先在独立 MES 只读 RR 中探测：
 * 既有完整出生只读 ERP 永久登记并核验返回；无 IQC 才准备 ERP 出生，再独占 MES
 * 物理 RR 同事务写新单、出生绑定及模板。两库不具跨库 ACID，终态不能借恢复创建新单，
 * 旧 IQC 不补认证；探测为无记录后若准备失败，必须在下次调用重新观察，不猜测跨库提交。
 */
@Service
public class QcReceiptIqcBirthService {
    private final ReceiptIqcBirthPort port;
    private final QcReceiptIqcBirthMapper mapper;
    private final IQcInspectDetailService details;
    private final SqlSessionFactory factory;

    public QcReceiptIqcBirthService(ReceiptIqcBirthPort port, QcReceiptIqcBirthMapper mapper,
        IQcInspectDetailService details, @Qualifier("sqlSessionFactory") SqlSessionFactory factory) {
        this.port = Objects.requireNonNull(port); this.mapper = Objects.requireNonNull(mapper);
        this.details = Objects.requireNonNull(details); this.factory = Objects.requireNonNull(factory);
    }

    /** 只接受租户、真实收货和物料身份；消息或 BO 的数量、单位、名称和认证标志不进入此契约。 */
    public QcIqcVo create(String tenantId, Long receiveId, Long materialId) {
        requireTenant(tenantId); requireFreshDispatch();
        if (receiveId == null || receiveId <= 0 || materialId == null || materialId <= 0) {
            throw fail("IQC_BIRTH_IDENTITY_INVALID");
        }
        ExistingBirth existing = probeExisting(tenantId, receiveId, materialId);
        requireFreshDispatch();
        // 恢复只读永久登记；不把终态订单重新当成首次接收，也不借永久登记新建 IQC。
        Birth birth = existing == null ? port.prepare(tenantId, receiveId, materialId)
            : port.readPermanent(tenantId, receiveId, materialId);
        requireFreshDispatch(); ReceiptIqcBirthSupport.validate(birth);
        if (!tenantId.equals(birth.tenantId()) || !receiveId.equals(birth.receiveId())
            || !materialId.equals(birth.materialId())) throw fail("IQC_BIRTH_PREPARED_SCOPE_INVALID");
        if (existing != null) {
            if (!existing.birth().birthId().equals(birth.birthId())
                || !existing.birth().birthSha256().equals(birth.birthSha256())) throw fail("IQC_BIRTH_BINDING_INVALID");
            return writeMes(birth, false, existing);
        }
        try {
            return writeMes(birth, true);
        } catch (DuplicateKeyException concurrentInsert) {
            // 冲突事务先完整回滚，再借新连接核验已提交的同 Birth 完整绑定；不能吞冲突盲认成功。
            requireFreshDispatch();
            return writeMes(birth, false);
        }
    }

    private record MesTopology(DynamicRoutingDataSource routing, DataSource mes) { }
    private record ExistingBirth(Long inspectionId, Birth birth, String bindingSha256) { }

    private MesTopology mesTopology() {
        Environment environment = factory.getConfiguration().getEnvironment();
        if (factory.getClass() != DefaultSqlSessionFactory.class || environment == null
            || environment.getTransactionFactory() == null
            || environment.getTransactionFactory().getClass() != SpringManagedTransactionFactory.class
            || !(environment.getDataSource() instanceof DynamicRoutingDataSource routing)
            || !factory.getConfiguration().hasMapper(QcReceiptIqcBirthMapper.class)) {
            throw fail("IQC_BIRTH_MES_TOPOLOGY_UNSUPPORTED");
        }
        DataSource mes = routing.getDataSources().get("mes");
        if (mes == null || mes == routing || routing.getGroupDataSources().containsKey("mes")) {
            throw fail("IQC_BIRTH_MES_TOPOLOGY_UNSUPPORTED");
        }
        return new MesTopology(routing, mes);
    }

    /** 仅探测原生既有身份：独立只读 RR，不加行锁，不给旧单补录，也不猜测跨库是否提交。 */
    private ExistingBirth probeExisting(String tenantId, Long receiveId, Long materialId) {
        requireFreshDispatch(); requireTenant(tenantId);
        MesTopology topology = mesTopology(); DynamicRoutingDataSource routing = topology.routing(); DataSource mes = topology.mes();
        DynamicDataSourceContextHolder.push("mes");
        try {
            if (routing.getDataSource("mes") != mes) throw fail("IQC_BIRTH_MES_TOPOLOGY_UNSUPPORTED");
            TransactionTemplate snapshot = new TransactionTemplate(new DataSourceTransactionManager(routing));
            snapshot.setReadOnly(true); snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            return snapshot.execute(status -> {
                if (!status.isNewTransaction()) throw fail("IQC_BIRTH_REQUIRES_FRESH_DISPATCH");
                ConnectionHolder holder = requireMesTransaction(tenantId, routing, mes, true);
                List<NativeColumn> columns = mapper.selectNativeColumns(); var indexes = mapper.selectNativeIndexes();
                requireProbeIdentityDefinition(columns, indexes);
                Long inspectionId = readScopeIdentity(holder, tenantId, receiveId, materialId);
                ExistingBirth existing = null; int precision = -1;
                if (inspectionId != null) {
                    // 出生表尚未部署或旧单没有绑定都不能走 ERP 准备，尤其不能借终态恢复补出生。
                    if (columns.stream().noneMatch(c -> c != null && "qc_receipt_iqc_birth".equals(c.getTableName()))) {
                        throw fail("UNIT_ORIGIN_UNPROVEN");
                    }
                    Binding binding = mapper.selectBinding(tenantId, inspectionId);
                    if (binding == null) throw fail("UNIT_ORIGIN_UNPROVEN");
                    precision = requireBirthDefinition(columns, indexes);
                    QcIqc row = mapper.selectInspection(tenantId, inspectionId);
                    if (row == null || !inspectionId.equals(row.getIqcId()) || !tenantId.equals(row.getTenantId())
                        || !receiveId.equals(row.getReceiveId()) || !materialId.equals(row.getMaterialId())) {
                        throw fail("IQC_BIRTH_BINDING_INVALID");
                    }
                    requireDocument(row, binding, binding.birth(), precision);
                    existing = new ExistingBirth(inspectionId, binding.birth(), binding.getBindingSha256());
                }
                var checkedColumns = mapper.selectNativeColumns(); var checkedIndexes = mapper.selectNativeIndexes();
                requireProbeIdentityDefinition(checkedColumns, checkedIndexes);
                if (requireMesTransaction(tenantId, routing, mes, true) != holder
                    || (existing != null && requireBirthDefinition(checkedColumns, checkedIndexes) != precision)) {
                    throw fail("IQC_BIRTH_MES_TRANSACTION_INVALID");
                }
                return existing;
            });
        } catch (ServiceException rejected) {
            throw rejected;
        } catch (RuntimeException unavailable) {
            throw fail("IQC_BIRTH_MES_PROBE_FAILED");
        } finally {
            DynamicDataSourceContextHolder.clear();
        }
    }

    private static void requireProbeIdentityDefinition(List<NativeColumn> columns, List<IqcNativeSchema.IndexPart> indexes) {
        IqcNativeSchema.requireMesIdentityDefinition(columns == null ? null : columns.stream()
            .map(c -> (IqcNativeSchema.Column)c).toList(), indexes);
    }

    /** 复用已掌控的真实连接作无锁身份查询；原 Mapper 的范围方法有 FOR UPDATE，不能用于只读探测。 */
    private static Long readScopeIdentity(ConnectionHolder holder, String tenantId, Long receiveId, Long materialId) {
        try (PreparedStatement query = holder.getConnection().prepareStatement("""
            SELECT iqc_id FROM qc_iqc WHERE tenant_id = ? AND receive_id = ? AND material_id = ? ORDER BY iqc_id
            """)) {
            query.setString(1, tenantId); query.setLong(2, receiveId); query.setLong(3, materialId);
            try (ResultSet rows = query.executeQuery()) {
                Long inspectionId = null;
                while (rows.next()) {
                    long actual = rows.getLong("iqc_id");
                    if (rows.wasNull() || actual <= 0 || inspectionId != null) throw fail("IQC_BIRTH_BINDING_INVALID");
                    inspectionId = actual;
                }
                return inspectionId;
            }
        } catch (SQLException unavailable) { throw fail("IQC_BIRTH_MES_PROBE_FAILED"); }
    }

    private QcIqcVo writeMes(Birth birth, boolean mayCreate) {
        return writeMes(birth, mayCreate, null);
    }

    private QcIqcVo writeMes(Birth birth, boolean mayCreate, ExistingBirth expected) {
        requireFreshDispatch(); requireTenant(birth.tenantId());
        MesTopology topology = mesTopology(); DynamicRoutingDataSource routing = topology.routing(); DataSource mes = topology.mes();
        DynamicDataSourceContextHolder.push("mes");
        try {
            if (routing.getDataSource("mes") != mes) throw fail("IQC_BIRTH_MES_TOPOLOGY_UNSUPPORTED");
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(routing));
            transaction.setReadOnly(false); transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            QcIqcVo result = transaction.execute(status -> {
                if (!status.isNewTransaction()) throw fail("IQC_BIRTH_REQUIRES_FRESH_DISPATCH");
                ConnectionHolder holder = requireWriter(birth.tenantId(), routing, mes);
                int precision = requireBirthDefinition(mapper.selectNativeColumns(), mapper.selectNativeIndexes());
                List<QcIqc> old = mapper.selectScopeInspections(birth.tenantId(), birth.receiveId(), birth.materialId());
                List<Binding> bindings = mapper.selectScopeBindings(birth.tenantId(), birth.receiveId(), birth.materialId());
                if (old == null || bindings == null || old.stream().anyMatch(Objects::isNull)
                    || bindings.stream().anyMatch(Objects::isNull)) throw fail("IQC_BIRTH_BINDING_INVALID");
                Long inspectionId;
                if (!old.isEmpty() || !bindings.isEmpty()) {
                    if (old.size() == 1 && bindings.isEmpty()) throw fail("UNIT_ORIGIN_UNPROVEN");
                    if (old.size() != 1 || bindings.size() != 1) throw fail("IQC_BIRTH_BINDING_INVALID");
                    QcIqc row = old.get(0); Binding binding = bindings.get(0);
                    if (expected != null && (!expected.inspectionId().equals(row.getIqcId())
                        || !expected.bindingSha256().equals(binding.getBindingSha256()))) throw fail("IQC_BIRTH_BINDING_INVALID");
                    requireDocument(row, binding, birth, precision); inspectionId = row.getIqcId();
                } else {
                    if (!mayCreate) throw fail("IQC_BIRTH_DUPLICATE_UNPROVEN");
                    Date boundAt = mapper.selectWriteTime();
                    if (boundAt == null || boundAt.getTime() < birth.capturedAt()
                        || boundAt.getTime() > System.currentTimeMillis()) throw fail("IQC_BIRTH_TIME_INVALID");
                    QcIqc row = new QcIqc(); row.setIqcId(IdUtil.getSnowflakeNextId());
                    row.setTenantId(birth.tenantId()); row.setReceiveId(birth.receiveId()); row.setMaterialId(birth.materialId());
                    row.setIqcNo("IQC-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + row.getIqcId());
                    row.setMaterialCode(birth.materialCode()); row.setMaterialName(birth.materialName());
                    row.setVendorId(birth.vendorId()); row.setVendorName(birth.vendorName()); row.setQuantity(birth.quantity());
                    row.setStatus("PENDING"); row.setInspectResult("PENDING"); row.setDelFlag("0");
                    // 新单时间取自同连接原生当前时间，先按实际列精度向下对齐，避免 MySQL 隐式舍入。
                    // 毫秒出生绑定独立保存；此操作绝不回填或重写任何既有单据的 create_time。
                    row.setCreateTime(new Date(alignedCreationTime(boundAt.getTime(), precision)));
                    Binding binding = Binding.from(row.getIqcId(), birth, boundAt,
                        ReceiptIqcBirthSupport.bindingHash(row.getIqcId(), birth, boundAt.getTime()));
                    if (mapper.insertInspection(row) != 1 || mapper.insertBinding(binding) != 1) {
                        throw fail("IQC_BIRTH_MES_WRITE_FAILED");
                    }
                    details.generateDetails("IQC", row.getIqcId(), birth.materialId(), birth.quantity());
                    if (requireWriter(birth.tenantId(), routing, mes) != holder) {
                        throw fail("IQC_BIRTH_MES_TRANSACTION_INVALID");
                    }
                    // 核验新 INSERT 的实际持久值，拒绝非严格 SQL 模式造成的截断或静默舍入。
                    requireDocument(mapper.selectInspection(birth.tenantId(), row.getIqcId()),
                        mapper.selectBinding(birth.tenantId(), row.getIqcId()), birth, precision);
                    inspectionId = row.getIqcId();
                }
                QcIqcVo view = mapper.selectInspectionView(birth.tenantId(), inspectionId);
                if (view == null || !inspectionId.equals(view.getIqcId())) throw fail("IQC_BIRTH_MES_WRITE_FAILED");
                if (requireWriter(birth.tenantId(), routing, mes) != holder
                    || requireBirthDefinition(mapper.selectNativeColumns(), mapper.selectNativeIndexes()) != precision) {
                    throw fail("IQC_BIRTH_MES_TRANSACTION_INVALID");
                }
                return view;
            });
            if (result == null) throw fail("IQC_BIRTH_MES_WRITE_FAILED");
            return result;
        } catch (DuplicateKeyException conflict) {
            if (!mayCreate) throw fail("IQC_BIRTH_DUPLICATE_UNPROVEN");
            throw conflict;
        } catch (ServiceException rejected) {
            throw rejected;
        } catch (RuntimeException unavailable) {
            // 不把原生 SQL、连接地址、数据主体或密钥带到 MQ 的异常日志。
            throw fail("IQC_BIRTH_MES_WRITE_FAILED");
        } finally {
            // 调度入口要求初始路由为空；独占阶段结束时清空本阶段的栈，避免异常协作者残留路由。
            DynamicDataSourceContextHolder.clear();
        }
    }

    private static void requireDocument(QcIqc row, Binding binding, Birth prepared, int precision) {
        if (row == null || !"0".equals(row.getDelFlag()) || row.getIqcNo() == null || row.getIqcNo().isBlank()
            || !("PENDING".equals(row.getStatus()) || "INSPECTING".equals(row.getStatus()) || "COMPLETED".equals(row.getStatus()))
            || !Objects.equals(row.getMaterialCode(), prepared.materialCode())
            || !Objects.equals(row.getMaterialName(), prepared.materialName())
            || !Objects.equals(row.getVendorId(), prepared.vendorId()) || !Objects.equals(row.getVendorName(), prepared.vendorName())) {
            throw fail("IQC_BIRTH_BINDING_INVALID");
        }
        Birth actual = requireBinding(binding, row.getTenantId(), row.getIqcId(), row.getReceiveId(), row.getMaterialId(),
            row.getQuantity(), row.getCreateTime(), precision);
        if (!prepared.birthId().equals(actual.birthId()) || !prepared.birthSha256().equals(actual.birthSha256())) {
            throw fail("IQC_BIRTH_BINDING_INVALID");
        }
    }

    /** 绑定摘要是完整性核验而非授权；ERP 仍须重读其永久出生登记和冻结来源。 */
    static Birth requireBinding(Binding binding, String tenantId, Long inspectionId, Long receiveId,
        Long materialId, BigDecimal quantity, Date createdAt, int precision) {
        if (binding == null) throw fail("UNIT_ORIGIN_UNPROVEN");
        Birth birth = binding.birth();
        try {
            ReceiptIqcBirthSupport.validate(birth);
            if (!Objects.equals(tenantId, binding.getTenantId()) || !Objects.equals(inspectionId, binding.getIqcId())
                || !Objects.equals(receiveId, birth.receiveId()) || !Objects.equals(materialId, birth.materialId())
                || quantity == null || quantity.compareTo(birth.quantity()) != 0
                || quantity.stripTrailingZeros().scale() > birth.quantityScale()
                || binding.getBoundAt() == null || binding.getBoundAt().getTime() > System.currentTimeMillis()
                || createdAt == null || createdAt.getTime() != alignedCreationTime(binding.getBoundAt().getTime(), precision)
                || !ReceiptIqcBirthSupport.bindingHash(inspectionId, birth, binding.getBoundAt().getTime())
                    .equals(binding.getBindingSha256())) throw fail("IQC_BIRTH_BINDING_INVALID");
            return birth;
        } catch (ServiceException invalid) {
            throw fail("IQC_BIRTH_BINDING_INVALID");
        }
    }

    static long alignedCreationTime(long boundAt, int precision) {
        if (precision < 0 || precision > 3 || boundAt <= 0) throw fail("IQC_BIRTH_BINDING_INVALID");
        long quantum = switch (precision) { case 0 -> 1000; case 1 -> 100; case 2 -> 10; default -> 1; };
        return boundAt / quantum * quantum;
    }

    /** 必须在实际 MES 事务持有的连接中读原生目录，不能用配置文件或 DTO 声称有唯一性。 */
    static int requireBirthDefinition(List<NativeColumn> columns, List<IqcNativeSchema.IndexPart> indexes) {
        if (columns == null || indexes == null || columns.stream().anyMatch(Objects::isNull)
            || indexes.stream().anyMatch(Objects::isNull)) throw fail("IQC_BIRTH_NATIVE_SCHEMA_UNSUPPORTED");
        var iqcColumns = columns.stream().filter(c -> !"qc_receipt_iqc_birth".equals(c.getTableName()))
            .map(c -> (IqcNativeSchema.Column)c).toList();
        var iqcIndexes = indexes.stream().filter(i -> !"qc_receipt_iqc_birth".equals(i.getTableName())).toList();
        IqcNativeSchema.requireMesDefinition(iqcColumns, iqcIndexes);
        Map<String, NativeColumn> birth = new HashMap<>(); NativeColumn created = null;
        for (NativeColumn c : columns) {
            if ("qc_iqc".equals(c.getTableName()) && "create_time".equals(c.getColumnName())) {
                if (created != null) throw fail("IQC_BIRTH_NATIVE_SCHEMA_UNSUPPORTED"); created = c;
            }
            if (!"qc_receipt_iqc_birth".equals(c.getTableName())) continue;
            if (!"InnoDB".equalsIgnoreCase(c.getEngine()) || !"NO".equals(c.getIsNullable())
                || birth.put(c.getColumnName(), c) != null) throw fail("IQC_BIRTH_NATIVE_SCHEMA_UNSUPPORTED");
        }
        if (created == null || !"InnoDB".equalsIgnoreCase(created.getEngine()) || !"datetime".equals(created.getDataType())
            || created.getDatetimePrecision() == null || created.getDatetimePrecision() < 0 || created.getDatetimePrecision() > 3) {
            throw fail("IQC_BIRTH_NATIVE_SCHEMA_UNSUPPORTED");
        }
        for (String name : List.of("iqc_id", "birth_id", "receive_id", "material_id", "base_uom_id", "vendor_id", "captured_at")) {
            requireType(birth, name, "bigint");
        }
        for (String name : List.of("tenant_id", "receipt_scope_sha256", "birth_sha256", "binding_sha256",
            "receive_code", "material_code", "material_name", "vendor_name")) requireType(birth, name, "varchar");
        requireType(birth, "quantity_scale", "int"); requireType(birth, "quantity", "decimal"); requireType(birth, "bound_at", "datetime");
        if (!Integer.valueOf(14).equals(birth.get("quantity").getNumericPrecision())
            || !Integer.valueOf(4).equals(birth.get("quantity").getNumericScale())
            || !Integer.valueOf(3).equals(birth.get("bound_at").getDatetimePrecision())) throw fail("IQC_BIRTH_NATIVE_SCHEMA_UNSUPPORTED");
        requireUnique(indexes, "PRIMARY", List.of("iqc_id"));
        requireUnique(indexes, null, List.of("tenant_id", "iqc_id"));
        requireUnique(indexes, null, List.of("tenant_id", "birth_id"));
        requireUnique(indexes, null, List.of("tenant_id", "receive_id", "material_id"));
        return created.getDatetimePrecision();
    }
    private static void requireType(Map<String, NativeColumn> columns, String name, String type) {
        if (!columns.containsKey(name) || !type.equals(columns.get(name).getDataType())) throw fail("IQC_BIRTH_NATIVE_SCHEMA_UNSUPPORTED");
    }
    private static void requireUnique(List<IqcNativeSchema.IndexPart> indexes, String name, List<String> expected) {
        Map<String, List<IqcNativeSchema.IndexPart>> grouped = new HashMap<>();
        for (var index : indexes) if ("qc_receipt_iqc_birth".equals(index.getTableName())) {
            grouped.computeIfAbsent(index.getIndexName(), ignored -> new java.util.ArrayList<>()).add(index);
        }
        for (var entry : grouped.entrySet()) {
            if (name != null && !name.equals(entry.getKey())) continue;
            var parts = entry.getValue(); parts.sort(Comparator.comparing(IqcNativeSchema.IndexPart::getSeqInIndex,
                Comparator.nullsLast(Comparator.naturalOrder())));
            if (parts.size() != expected.size()) continue;
            boolean exact = true;
            for (int i = 0; i < parts.size(); i++) {
                var part = parts.get(i);
                if (!Integer.valueOf(0).equals(part.getNonUnique()) || part.getSubPart() != null
                    || !Integer.valueOf(i+1).equals(part.getSeqInIndex()) || !"BTREE".equalsIgnoreCase(part.getIndexType())
                    || !expected.get(i).equals(part.getColumnName())) { exact = false; break; }
            }
            if (exact) return;
        }
        throw fail("IQC_BIRTH_NATIVE_SCHEMA_UNSUPPORTED");
    }

    private ConnectionHolder requireWriter(String tenantId, DynamicRoutingDataSource routing, DataSource mes) {
        return requireMesTransaction(tenantId, routing, mes, false);
    }

    private ConnectionHolder requireMesTransaction(String tenantId, DynamicRoutingDataSource routing, DataSource mes, boolean readOnly) {
        requireTenant(tenantId);
        if (routing.getDataSources().get("mes") != mes || routing.getDataSource("mes") != mes
            || !"mes".equals(DynamicDataSourceContextHolder.peek()) || TransactionContext.getXID() != null
            || !TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()
            || TransactionSynchronizationManager.isCurrentTransactionReadOnly() != readOnly
            || !Integer.valueOf(Connection.TRANSACTION_REPEATABLE_READ).equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
            || !(TransactionSynchronizationManager.getResource(routing) instanceof ConnectionHolder holder)
            || !holder.isSynchronizedWithTransaction()) throw fail("IQC_BIRTH_MES_TRANSACTION_INVALID");
        try {
            Connection connection = holder.getConnection();
            if (connection.isClosed() || connection.getAutoCommit() || connection.isReadOnly() != readOnly
                || connection.getTransactionIsolation() != Connection.TRANSACTION_REPEATABLE_READ) throw fail("IQC_BIRTH_MES_TRANSACTION_INVALID");
        } catch (SQLException unavailable) { throw fail("IQC_BIRTH_MES_TRANSACTION_INVALID"); }
        for (Object key : TransactionSynchronizationManager.getResourceMap().keySet()) {
            if (key != routing && key != factory) throw fail("IQC_BIRTH_MES_TRANSACTION_INVALID");
        }
        return holder;
    }
    private static void requireFreshDispatch() {
        if (TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.getResourceMap().isEmpty() || TransactionContext.getXID() != null
            || TransactionSynchronizationManager.isCurrentTransactionReadOnly() || DynamicDataSourceContextHolder.peek() != null) {
            throw fail("IQC_BIRTH_REQUIRES_FRESH_DISPATCH");
        }
    }
    private static void requireTenant(String tenantId) {
        if (tenantId == null || !tenantId.matches("[0-9]{6,20}") || !TenantHelper.isEnable()
            || !tenantId.equals(TenantHelper.getTenantId())) throw fail("IQC_MES_TENANT_MISMATCH");
    }
    private static ServiceException fail(String code) { return new ServiceException(code); }
}

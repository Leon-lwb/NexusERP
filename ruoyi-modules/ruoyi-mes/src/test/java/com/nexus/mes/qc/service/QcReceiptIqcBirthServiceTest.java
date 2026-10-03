package com.nexus.mes.qc.service;

import com.nexus.mes.common.mq.consumer.IqcAutoCreateConsumer;
import com.nexus.mes.qc.controller.QcIqcController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.nexus.common.core.exception.ServiceException;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.mq.iqc.ReceiptIqcBirthPort;
import com.nexus.common.mq.iqc.ReceiptIqcBirthPort.Birth;
import com.nexus.common.mq.iqc.ReceiptIqcBirthSupport;
import com.nexus.common.mq.iqc.IqcNativeSchema;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.qc.domain.QcIqc;
import com.nexus.mes.qc.domain.vo.QcIqcVo;
import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper;
import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper.Binding;
import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper.NativeColumn;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.mockito.MockedStatic;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

/** 原生出生服务必须接入真实创建入口，而不只是提供一个未调用的读取器。 */
class QcReceiptIqcBirthServiceTest {
    final String tenantId = "000007";
    final long now = System.currentTimeMillis();
    final ReceiptIqcBirthPort port = mock(ReceiptIqcBirthPort.class);
    final QcReceiptIqcBirthMapper mapper = mock(QcReceiptIqcBirthMapper.class);
    final IQcInspectDetailService details = mock(IQcInspectDetailService.class);
    final DynamicRoutingDataSource routing = new DynamicRoutingDataSource(List.of());
    final DataSource mes = mock(DataSource.class);
    final DataSource master = mock(DataSource.class);
    final Connection connection = mock(Connection.class);
    final AtomicBoolean autoCommit = new AtomicBoolean(true);
    final AtomicBoolean readOnly = new AtomicBoolean();
    final AtomicInteger readOnlySnapshots = new AtomicInteger();
    final AtomicInteger readOnlyCommits = new AtomicInteger();
    final AtomicInteger writeCommits = new AtomicInteger();
    final AtomicInteger isolation = new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED);
    final AtomicReference<QcIqc> committedInspection = new AtomicReference<>();
    final AtomicReference<Binding> committedBinding = new AtomicReference<>();
    final AtomicReference<QcIqc> pendingInspection = new AtomicReference<>();
    final AtomicReference<Binding> pendingBinding = new AtomicReference<>();
    Birth birth;
    QcReceiptIqcBirthService service;
    MockedStatic<TenantHelper> tenant;

    static Birth birth(String tenantId, long capturedAt) {
        Birth raw = new Birth(tenantId, 701L, 101L, 201L, 301L, 2, new BigDecimal("12.34"),
            "a".repeat(64), null, capturedAt, "RECV-101", "MAT-201", "原始物料", 401L, "原始供应商");
        return new Birth(raw.tenantId(), raw.birthId(), raw.receiveId(), raw.materialId(), raw.baseUomId(),
            raw.quantityScale(), raw.quantity(), raw.receiptScopeSha256(), ReceiptIqcBirthSupport.birthHash(raw),
            raw.capturedAt(), raw.receiveCode(), raw.materialCode(), raw.materialName(), raw.vendorId(), raw.vendorName());
    }

    static List<NativeColumn> nativeColumns() {
        List<NativeColumn> rows = new ArrayList<>();
        for (var old : QcReceiptIqcEvidenceServiceTest.nativeColumns()) {
            var row = new NativeColumn(); row.setTableName(old.getTableName()); row.setColumnName(old.getColumnName());
            row.setDataType(old.getDataType()); row.setEngine(old.getEngine()); row.setIsNullable(old.getIsNullable());
            row.setNumericPrecision(old.getNumericPrecision()); row.setNumericScale(old.getNumericScale()); rows.add(row);
        }
        NativeColumn created = new NativeColumn(); created.setTableName("qc_iqc"); created.setColumnName("create_time");
        created.setDataType("datetime"); created.setEngine("InnoDB"); created.setIsNullable("YES");
        created.setDatetimePrecision(0); rows.add(created);
        for (String name : List.of("iqc_id", "tenant_id", "birth_id", "receive_id", "material_id", "base_uom_id",
            "quantity_scale", "quantity", "receipt_scope_sha256", "birth_sha256", "captured_at", "receive_code",
            "material_code", "material_name", "vendor_id", "vendor_name", "bound_at", "binding_sha256")) {
            NativeColumn column = new NativeColumn(); column.setTableName("qc_receipt_iqc_birth"); column.setColumnName(name);
            column.setEngine("InnoDB"); column.setIsNullable("NO");
            if (!"tenant_id".equals(name) && (name.endsWith("_id") || "captured_at".equals(name))) column.setDataType("bigint");
            else if ("quantity_scale".equals(name)) column.setDataType("int");
            else if ("quantity".equals(name)) { column.setDataType("decimal"); column.setNumericPrecision(14); column.setNumericScale(4); }
            else if ("bound_at".equals(name)) { column.setDataType("datetime"); column.setDatetimePrecision(3); }
            else column.setDataType("varchar");
            rows.add(column);
        }
        return rows;
    }

    static List<IqcNativeSchema.IndexPart> nativeIndexes() {
        var rows = new ArrayList<>(QcReceiptIqcEvidenceServiceTest.nativeIndexes());
        String[][] specs = {{"PRIMARY", "iqc_id"}, {"uk_native_iqc", "tenant_id", "iqc_id"},
            {"uk_native_birth", "tenant_id", "birth_id"}, {"uk_native_scope", "tenant_id", "receive_id", "material_id"}};
        for (String[] spec : specs) for (int i=1; i<spec.length; i++) {
            var row = new IqcNativeSchema.IndexPart(); row.setTableName("qc_receipt_iqc_birth"); row.setIndexName(spec[0]);
            row.setColumnName(spec[i]); row.setSeqInIndex(i); row.setNonUnique(0); row.setIndexType("BTREE"); rows.add(row);
        }
        return rows;
    }

    @BeforeEach void arrangeNativeConnectionAndTransactionAwareStore() throws Exception {
        birth = birth(tenantId, now - 2000);
        tenant = mockStatic(TenantHelper.class); tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
        routing.addDataSource("mes", mes); routing.addDataSource("master", master);
        when(mes.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenAnswer(i -> autoCommit.get());
        doAnswer(i -> { autoCommit.set(i.getArgument(0)); return null; }).when(connection).setAutoCommit(anyBoolean());
        when(connection.isReadOnly()).thenAnswer(i -> readOnly.get());
        doAnswer(i -> { readOnly.set(i.getArgument(0)); return null; }).when(connection).setReadOnly(anyBoolean());
        when(connection.getTransactionIsolation()).thenAnswer(i -> isolation.get());
        doAnswer(i -> { isolation.set(i.getArgument(0)); return null; }).when(connection).setTransactionIsolation(anyInt());
        doAnswer(i -> { if (readOnly.get()) readOnlyCommits.incrementAndGet(); else writeCommits.incrementAndGet();
            if (pendingInspection.get() != null) committedInspection.set(pendingInspection.getAndSet(null));
            if (pendingBinding.get() != null) committedBinding.set(pendingBinding.getAndSet(null)); return null; }).when(connection).commit();
        doAnswer(i -> { pendingInspection.set(null); pendingBinding.set(null); return null; }).when(connection).rollback();
        when(port.prepare(tenantId, 101L, 201L)).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
            assertThat(DynamicDataSourceContextHolder.peek()).isNull(); assertThat(autoCommit.get()).isTrue(); return birth;
        });
        when(port.readPermanent(tenantId, 101L, 201L)).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
            assertThat(DynamicDataSourceContextHolder.peek()).isNull(); assertThat(autoCommit.get()).isTrue(); return birth;
        });
        when(connection.prepareStatement(anyString())).thenAnswer(i -> {
            assertNativeReader(); String sql = i.getArgument(0);
            assertThat(sql).contains("SELECT iqc_id FROM qc_iqc", "tenant_id = ?", "receive_id = ?", "material_id = ?");
            assertThat(sql).doesNotContain("FOR UPDATE", "INSERT", "UPDATE", "DELETE");
            PreparedStatement query = mock(PreparedStatement.class); ResultSet rows = mock(ResultSet.class);
            Long id = committedInspection.get() == null ? null : committedInspection.get().getIqcId();
            AtomicInteger cursor = new AtomicInteger(); when(rows.next()).thenAnswer(call -> cursor.getAndIncrement() == 0 && id != null);
            when(rows.getLong("iqc_id")).thenAnswer(call -> id == null ? 0 : id);
            when(query.executeQuery()).thenAnswer(call -> {
                assertNativeReader(); readOnlySnapshots.incrementAndGet();
                verify(query).setString(1, tenantId); verify(query).setLong(2, 101L); verify(query).setLong(3, 201L); return rows;
            });
            return query;
        });
        when(mapper.selectNativeColumns()).thenAnswer(i -> nativeColumns());
        when(mapper.selectNativeIndexes()).thenAnswer(i -> nativeIndexes());
        when(mapper.selectWriteTime()).thenReturn(new Date(now));
        when(mapper.selectScopeInspections(tenantId, 101L, 201L)).thenAnswer(i -> committedInspection.get() == null ? List.of() : List.of(committedInspection.get()));
        when(mapper.selectScopeBindings(tenantId, 101L, 201L)).thenAnswer(i -> committedBinding.get() == null ? List.of() : List.of(committedBinding.get()));
        when(mapper.insertInspection(any())).thenAnswer(i -> { assertNativeWriter(); pendingInspection.set(i.getArgument(0)); return 1; });
        when(mapper.insertBinding(any())).thenAnswer(i -> { assertNativeWriter(); pendingBinding.set(i.getArgument(0)); return 1; });
        when(mapper.selectInspection(eq(tenantId), anyLong())).thenAnswer(i -> pendingInspection.get() == null ? committedInspection.get() : pendingInspection.get());
        when(mapper.selectBinding(eq(tenantId), anyLong())).thenAnswer(i -> pendingBinding.get() == null ? committedBinding.get() : pendingBinding.get());
        when(mapper.selectInspectionView(eq(tenantId), anyLong())).thenAnswer(i -> {
            QcIqcVo view = new QcIqcVo(); view.setIqcId(i.getArgument(1)); return view;
        });
        var config = new Configuration(new Environment("private-native-mes", new SpringManagedTransactionFactory(), routing));
        config.setMapUnderscoreToCamelCase(true); config.addMapper(QcReceiptIqcBirthMapper.class);
        service = new QcReceiptIqcBirthService(port, mapper, details, new DefaultSqlSessionFactory(config));
    }

    void assertNativeReader() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
        assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
        assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("mes");
        assertThat(TransactionSynchronizationManager.getResource(routing)).isInstanceOf(ConnectionHolder.class);
        assertThat(autoCommit.get()).isFalse(); assertThat(readOnly.get()).isTrue();
    }

    void assertNativeWriter() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
        assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
        assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("mes");
        assertThat(TransactionSynchronizationManager.getResource(routing)).isInstanceOf(ConnectionHolder.class);
        assertThat(autoCommit.get()).isFalse(); assertThat(isolation.get()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
    }

    void existingNativeDocument() {
        QcIqc row = new QcIqc(); row.setIqcId(501L); row.setTenantId(tenantId); row.setIqcNo("IQC-501");
        row.setReceiveId(101L); row.setMaterialId(201L); row.setQuantity(birth.quantity()); row.setDelFlag("0");
        row.setStatus("PENDING"); row.setInspectResult("PENDING"); row.setCreateTime(new Date(now / 1000 * 1000));
        row.setMaterialCode(birth.materialCode()); row.setMaterialName(birth.materialName());
        row.setVendorId(birth.vendorId()); row.setVendorName(birth.vendorName()); committedInspection.set(row);
        committedBinding.set(Binding.from(501L, birth, new Date(now), ReceiptIqcBirthSupport.bindingHash(501L, birth, now)));
    }

    @AfterEach void cleanOnlyThreadState() {
        tenant.close(); TransactionSynchronizationManager.clear(); DynamicDataSourceContextHolder.clear();
    }

    @Test void actualCreationUsesPreparedFactsAndCommitsBodyBirthAndTemplateTogether() throws Exception {
        var actual = service.create(tenantId, 101L, 201L);
        var row = committedInspection.get(); var binding = committedBinding.get();
        assertThat(actual.getIqcId()).isEqualTo(row.getIqcId());
        assertThat(row.getQuantity()).isEqualByComparingTo("12.34"); assertThat(binding.birth()).isEqualTo(birth);
        assertThat(row.getCreateTime().getTime()).isEqualTo(now / 1000 * 1000);
        assertThat(binding.getBoundAt().getTime()).isEqualTo(now);
        assertThat(binding.getBindingSha256()).isEqualTo(ReceiptIqcBirthSupport.bindingHash(row.getIqcId(), birth, now));
        verify(details).generateDetails("IQC", row.getIqcId(), 201L, birth.quantity());
        assertThat(writeCommits.get()).isEqualTo(1); verify(connection, never()).rollback(); verify(master, never()).getConnection();
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty(); assertThat(DynamicDataSourceContextHolder.peek()).isNull();
    }

    @Test void templateFailureRollsBackBothInsertsRatherThanLeavingANativeCertificate() throws Exception {
        when(details.generateDetails(eq("IQC"), anyLong(), eq(201L), any())).thenThrow(new IllegalStateException("模板写失败"));
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_MES_WRITE_FAILED");
        assertThat(committedInspection.get()).isNull(); assertThat(committedBinding.get()).isNull();
        verify(mapper).insertInspection(any()); verify(mapper).insertBinding(any()); verify(connection).rollback(); assertThat(writeCommits.get()).isZero();
    }
    @Test void repeatRequiresSameCompleteBirthAndNeverRegeneratesTemplates() throws Exception {
        existingNativeDocument(); var actual = service.create(tenantId, 101L, 201L);
        assertThat(actual.getIqcId()).isEqualTo(501L); verify(mapper, never()).insertInspection(any());
        verify(mapper, never()).insertBinding(any()); verifyNoInteractions(details); assertThat(writeCommits.get()).isEqualTo(1);
    }
    @Test void legacyInspectionCannotAcquireBirthRetroactively() throws Exception {
        existingNativeDocument(); committedBinding.set(null);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("UNIT_ORIGIN_UNPROVEN");
        verify(mapper, never()).insertBinding(any()); verify(connection).rollback();
    }
    @Test void preparedErpBirthWithoutCommittedMesDocumentCanRetryNormally() throws Exception {
        when(details.generateDetails(eq("IQC"), anyLong(), eq(201L), any())).thenThrow(new IllegalStateException("中断"));
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_MES_WRITE_FAILED");
        doReturn(0).when(details).generateDetails(eq("IQC"), anyLong(), eq(201L), any());
        // 重试已发生过连接借用，改用仍核验外部事务清空的来源准备。
        doAnswer(i -> {
            assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty(); return birth;
        }).when(port).prepare(tenantId, 101L, 201L);
        service.create(tenantId, 101L, 201L); assertThat(committedBinding.get()).isNotNull();
        verify(connection).rollback(); assertThat(writeCommits.get()).isEqualTo(1); verify(port, times(2)).prepare(tenantId, 101L, 201L);
    }
    @Test void duplicateKeyIsNotBlindSuccessWhenNoCommittedBindingExists() throws Exception {
        doThrow(new DuplicateKeyException("冲突")).when(mapper).insertInspection(any());
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_DUPLICATE_UNPROVEN");
        verify(connection, atLeastOnce()).rollback(); assertThat(writeCommits.get()).isZero();
    }
    @Test void duplicateRaceMayReturnOnlyARecheckedCommittedSameBirth() throws Exception {
        doAnswer(i -> { existingNativeDocument(); throw new DuplicateKeyException("并发" ); }).when(mapper).insertInspection(any());
        var actual = service.create(tenantId, 101L, 201L); assertThat(actual.getIqcId()).isEqualTo(501L);
        verify(connection).rollback(); assertThat(writeCommits.get()).isEqualTo(1); verifyNoInteractions(details);
    }
    @Test void alteredBindingHashFailsClosedOnRepeat() throws Exception {
        existingNativeDocument(); committedBinding.get().setBindingSha256("0".repeat(64));
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_BINDING_INVALID");
        verify(mapper, never()).insertBinding(any()); verify(connection).rollback();
    }
    @Test void changedQuantityOrOldCreationTimeDoesNotGetRewritten() throws Exception {
        existingNativeDocument(); committedInspection.get().setCreateTime(new Date(now - 86400000));
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_BINDING_INVALID");
        verify(mapper, never()).insertInspection(any()); verify(connection).rollback();
    }
    @Test void foreignTenantFailsBeforeErpPrepare() {
        assertThatThrownBy(() -> service.create("000008", 101L, 201L)).hasMessage("IQC_MES_TENANT_MISMATCH");
        verifyNoInteractions(port, mapper);
    }
    @Test void ambientMesTransactionCannotWrapErpPrepare() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_REQUIRES_FRESH_DISPATCH");
        verifyNoInteractions(port, mapper);
    }
    @Test void routingOnlyMesCallerAlsoCannotPrepareErpInsideMesAdvice() {
        DynamicDataSourceContextHolder.push("mes");
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_REQUIRES_FRESH_DISPATCH");
        verifyNoInteractions(port, mapper);
    }
    @Test void missingPermanentBirthUniqueRejectsBeforeAnyInsert() throws Exception {
        var indexes = nativeIndexes(); indexes.removeIf(i -> "uk_native_birth".equals(i.getIndexName()));
        when(mapper.selectNativeIndexes()).thenReturn(indexes);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_NATIVE_SCHEMA_UNSUPPORTED");
        verify(mapper, never()).insertInspection(any()); verify(connection).rollback();
    }
    @Test void nonTransactionalBirthTableOrWrongBoundTimePrecisionRejectsWrites() throws Exception {
        var columns = nativeColumns(); columns.stream().filter(c -> "bound_at".equals(c.getColumnName())).forEach(c -> c.setDatetimePrecision(0));
        when(mapper.selectNativeColumns()).thenReturn(columns);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_NATIVE_SCHEMA_UNSUPPORTED");
        verify(mapper, never()).insertInspection(any()); verify(connection).rollback();
    }
    @Test void sourceScopeMustMatchEvenAValidlyHashedDto() {
        birth = birth("000008", now - 2000);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_PREPARED_SCOPE_INVALID");
        verify(mapper, never()).insertInspection(any()); verify(mapper, never()).insertBinding(any());
    }
    @Test void boundTimeBeforeErpCaptureFailsWithoutNewDocument() throws Exception {
        when(mapper.selectWriteTime()).thenReturn(new Date(birth.capturedAt()-1));
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_TIME_INVALID");
        verify(mapper, never()).insertInspection(any()); verify(connection).rollback();
    }


    @Test void changedPermanentFactsCannotReuseAnExistingSameScopeBirth() throws Exception {
        existingNativeDocument(); birth = birth(tenantId, now - 2500);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_BINDING_INVALID");
        verify(mapper, never()).insertBinding(any()); verify(port).readPermanent(tenantId, 101L, 201L); assertThat(writeCommits.get()).isZero();
    }
    @Test void quantityChangeInExistingBodyCannotReturnAsIdempotentSuccess() throws Exception {
        existingNativeDocument(); committedInspection.get().setQuantity(new BigDecimal("12.35"));
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_BINDING_INVALID");
        verify(mapper, never()).insertInspection(any()); verify(connection).rollback();
    }
    @Test void missingNativeMesDatasourceNeverFallsBackToMaster() throws Exception {
        routing.getDataSources().remove("mes");
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_MES_TOPOLOGY_UNSUPPORTED");
        verify(mes, never()).getConnection(); verify(master, never()).getConnection();
    }
    @Test void ambientForeignConnectionHolderCannotReachErpPrepare() {
        TransactionSynchronizationManager.bindResource(master, new ConnectionHolder(connection));
        try {
            assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_REQUIRES_FRESH_DISPATCH");
            verifyNoInteractions(port, mapper);
        } finally { TransactionSynchronizationManager.unbindResource(master); }
    }
    @Test void originalCreateTimePrecisionThreeKeepsNativeMillisecondCreation() throws Exception {
        var columns = nativeColumns(); columns.stream().filter(c -> "create_time".equals(c.getColumnName())).forEach(c -> c.setDatetimePrecision(3));
        when(mapper.selectNativeColumns()).thenReturn(columns);
        service.create(tenantId, 101L, 201L);
        assertThat(committedInspection.get().getCreateTime().getTime()).isEqualTo(now); assertThat(writeCommits.get()).isEqualTo(1);
    }
    @Test void nonTransactionalBirthEngineCannotCommitANativeCertificate() throws Exception {
        var columns = nativeColumns(); columns.stream().filter(c -> "qc_receipt_iqc_birth".equals(c.getTableName())).forEach(c -> c.setEngine("MyISAM"));
        when(mapper.selectNativeColumns()).thenReturn(columns);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_NATIVE_SCHEMA_UNSUPPORTED");
        verify(mapper, never()).insertInspection(any()); verify(connection).rollback();
    }
    @Test void allPermanentBirthUniquesMustBeFullColumnsNotPrefixKeys() throws Exception {
        var indexes = nativeIndexes(); indexes.stream().filter(i -> "uk_native_scope".equals(i.getIndexName())).forEach(i -> i.setSubPart(4));
        when(mapper.selectNativeIndexes()).thenReturn(indexes);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_NATIVE_SCHEMA_UNSUPPORTED");
        verify(mapper, never()).insertInspection(any()); verify(connection).rollback();
    }
    @Test void unexpectedTemplateRoutingIsRejectedAndExclusiveRouteIsCleaned() throws Exception {
        doAnswer(i -> { DynamicDataSourceContextHolder.push("master"); return 0; }).when(details)
            .generateDetails(eq("IQC"), anyLong(), eq(201L), any());
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_MES_TRANSACTION_INVALID");
        assertThat(committedBinding.get()).isNull(); assertThat(DynamicDataSourceContextHolder.peek()).isNull();
        verify(connection).rollback(); verify(master, never()).getConnection();
    }

    @Test void nativeDecimalFourScaleDoesNotInventNewOriginalUnitPrecision() throws Exception {
        existingNativeDocument(); committedBinding.get().setQuantity(birth.quantity().setScale(4));
        committedInspection.get().setQuantity(birth.quantity().setScale(4));
        var actual = service.create(tenantId, 101L, 201L);
        assertThat(actual.getIqcId()).isEqualTo(501L); assertThat(committedBinding.get().getQuantityScale()).isEqualTo(2);
        verify(mapper, never()).insertBinding(any()); assertThat(writeCommits.get()).isEqualTo(1);
    }

    @Test void finishedReceiptOrClosedOrderWithNativeIqcRestoresOnlyPermanentBirth() throws Exception {
        existingNativeDocument(); committedInspection.get().setStatus("COMPLETED");
        doThrow(new ServiceException("IQC_BIRTH_RECEIPT_NOT_APPROVED")).when(port).prepare(tenantId, 101L, 201L);
        when(port.readPermanent(tenantId, 101L, 201L)).thenReturn(birth);
        var actual = service.create(tenantId, 101L, 201L);
        assertThat(actual.getIqcId()).isEqualTo(501L); assertThat(readOnlySnapshots.get()).isEqualTo(1);
        verify(port).readPermanent(tenantId, 101L, 201L); verify(port, never()).prepare(anyString(), anyLong(), anyLong());
        verify(mapper, never()).insertInspection(any()); verify(mapper, never()).insertBinding(any()); verifyNoInteractions(details);
    }
    @Test void terminalSourceWithoutMesIqcStillRequiresPrepareAndCannotCreateFromPermanentRead() {
        doThrow(new ServiceException("订单不在实际接收状态")).when(port).prepare(tenantId, 101L, 201L);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("订单不在实际接收状态");
        assertThat(readOnlySnapshots.get()).isEqualTo(1);
        verify(port).prepare(tenantId, 101L, 201L); verify(port, never()).readPermanent(anyString(), anyLong(), anyLong());
        verify(mapper, never()).insertInspection(any()); verify(mapper, never()).insertBinding(any()); verifyNoInteractions(details);
    }
    @Test void legacyIqcWithoutBindingFailsBeforeAnyErpLookupEvenWhenSourceIsTerminal() {
        existingNativeDocument(); committedBinding.set(null);
        doThrow(new ServiceException("IQC_BIRTH_RECEIPT_NOT_APPROVED")).when(port).prepare(tenantId, 101L, 201L);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("UNIT_ORIGIN_UNPROVEN");
        verifyNoInteractions(port); verify(mapper, never()).insertBinding(any());
    }
    @Test void damagedNativeBindingRejectsBeforePrepareOrPermanentLookup() {
        existingNativeDocument(); committedBinding.get().setBindingSha256("0".repeat(64));
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_BINDING_INVALID");
        verifyNoInteractions(port); verify(mapper, never()).insertInspection(any()); verify(mapper, never()).insertBinding(any());
    }


    @Test void noneProbeThenTerminalPrepareFailureMustRetryFromANewMesSnapshot() {
        doAnswer(i -> { existingNativeDocument(); throw new ServiceException("IQC_BIRTH_RECEIPT_NOT_APPROVED"); })
            .when(port).prepare(tenantId, 101L, 201L);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_RECEIPT_NOT_APPROVED");
        verify(port, never()).readPermanent(anyString(), anyLong(), anyLong());
        assertThat(readOnlySnapshots.get()).isEqualTo(1); assertThat(writeCommits.get()).isZero();
        var restored = service.create(tenantId, 101L, 201L);
        assertThat(restored.getIqcId()).isEqualTo(501L); assertThat(readOnlySnapshots.get()).isEqualTo(2);
        verify(port, times(1)).prepare(tenantId, 101L, 201L); verify(port).readPermanent(tenantId, 101L, 201L);
        verify(mapper, never()).insertInspection(any()); verify(mapper, never()).insertBinding(any()); verifyNoInteractions(details);
    }
    @Test void validBindingChangedBetweenProbeAndPermanentReadCannotReplaceTheObservedBinding() throws Exception {
        existingNativeDocument();
        doAnswer(i -> {
            long changedAt = now % 1000 == 0 ? now + 1 : now - 1;
            committedBinding.set(Binding.from(501L, birth, new Date(changedAt), ReceiptIqcBirthSupport.bindingHash(501L, birth, changedAt)));
            return birth;
        }).when(port).readPermanent(tenantId, 101L, 201L);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_BINDING_INVALID");
        verify(port, never()).prepare(anyString(), anyLong(), anyLong()); verify(connection).rollback();
        verify(mapper, never()).insertInspection(any()); verify(mapper, never()).insertBinding(any()); assertThat(writeCommits.get()).isZero();
    }
    @Test void vanishedNativeDocumentCannotBeRecreatedByThePermanentRecoveryBranch() {
        existingNativeDocument();
        doAnswer(i -> { committedInspection.set(null); committedBinding.set(null); return birth; })
            .when(port).readPermanent(tenantId, 101L, 201L);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("IQC_BIRTH_DUPLICATE_UNPROVEN");
        verify(port, never()).prepare(anyString(), anyLong(), anyLong());
        verify(mapper, never()).insertInspection(any()); verify(mapper, never()).insertBinding(any()); assertThat(writeCommits.get()).isZero();
    }
    @Test void legacyIqcWithoutBirthTableNeedsNoDecimalOriginBeforeRejectingRecovery() {
        existingNativeDocument(); committedBinding.set(null);
        var columns = nativeColumns(); columns.removeIf(c -> "qc_receipt_iqc_birth".equals(c.getTableName()));
        columns.stream().filter(c -> "quantity".equals(c.getColumnName())).forEach(c -> c.setDataType("float"));
        when(mapper.selectNativeColumns()).thenReturn(columns);
        assertThatThrownBy(() -> service.create(tenantId, 101L, 201L)).hasMessage("UNIT_ORIGIN_UNPROVEN");
        verifyNoInteractions(port); verify(mapper, never()).selectBinding(anyString(), anyLong());
        verify(mapper, never()).insertInspection(any()); verify(mapper, never()).insertBinding(any());
    }

    @Test void nativeDispatcherExistsOutsideAutomaticMesRoutingAndTransactions() throws Exception {
        Class<?> type = Class.forName("com.nexus.mes.qc.service.QcReceiptIqcBirthService");
        assertThat(type.getAnnotation(com.baomidou.dynamic.datasource.annotation.DS.class)).isNull();
        assertThat(type.getAnnotation(org.springframework.transaction.annotation.Transactional.class)).isNull();
        var create = type.getMethod("create", String.class, Long.class, Long.class);
        assertThat(create.getAnnotation(org.springframework.transaction.annotation.Transactional.class)).isNull();
    }
    @Test void bothRealEntrypointsDependOnTheNativeDispatcher() {
        for (Class<?> entry : new Class<?>[]{IqcAutoCreateConsumer.class, QcIqcController.class}) {
            assertThat(java.util.Arrays.stream(entry.getDeclaredFields()).map(f -> f.getType().getSimpleName()))
                .contains("QcReceiptIqcBirthService");
        }
    }
}

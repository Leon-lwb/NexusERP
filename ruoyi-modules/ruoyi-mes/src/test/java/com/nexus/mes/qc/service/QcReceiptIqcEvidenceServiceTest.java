package com.nexus.mes.qc.service;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.iqc.IqcNativeSchema;
import com.nexus.common.mq.iqc.ReceiptIqcBirthPort.Birth;
import com.nexus.common.mq.iqc.ReceiptIqcBirthSupport;
import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper;
import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper.Binding;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.qc.mapper.QcReceiptIqcEvidenceMapper;
import com.nexus.mes.qc.mapper.QcReceiptIqcEvidenceMapper.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QcReceiptIqcEvidenceServiceTest {
    final String tenantId = "000007";
    final long now = System.currentTimeMillis();
    final QcReceiptIqcEvidenceMapper mapper = mock(QcReceiptIqcEvidenceMapper.class);
    final QcReceiptIqcBirthMapper birthMapper = mock(QcReceiptIqcBirthMapper.class);
    final DynamicRoutingDataSource routing = new DynamicRoutingDataSource(List.of());
    final DataSource mes = mock(DataSource.class);
    final DataSource master = mock(DataSource.class);
    final Connection connection = mock(Connection.class);
    final AtomicBoolean readOnly = new AtomicBoolean();
    final AtomicBoolean autoCommit = new AtomicBoolean(true);
    final AtomicInteger isolation = new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED);
    final InspectionRow row = new InspectionRow();
    QcReceiptIqcEvidenceService service;

    static java.util.List<IqcNativeSchema.Column> nativeColumns() {
        java.util.List<IqcNativeSchema.Column> rows = new java.util.ArrayList<>();
        for (String[] spec : new String[][] {{"qc_iqc", "iqc_id", "bigint"}, {"qc_iqc", "tenant_id", "varchar"},
            {"qc_iqc", "receive_id", "bigint"}, {"qc_iqc", "material_id", "bigint"},
            {"qc_iqc", "quantity", "decimal"}, {"qc_inspect_detail", "detail_id", "bigint"}}) {
            var row = new IqcNativeSchema.Column(); row.setTableName(spec[0]); row.setColumnName(spec[1]);
            row.setDataType(spec[2]); row.setEngine("InnoDB"); row.setIsNullable("receive_id".equals(spec[1]) ? "YES" : "NO");
            if ("decimal".equals(spec[2])) { row.setNumericPrecision(14); row.setNumericScale(4); } rows.add(row);
        }
        return rows;
    }
    static java.util.List<IqcNativeSchema.IndexPart> nativeIndexes() {
        java.util.List<IqcNativeSchema.IndexPart> rows = new java.util.ArrayList<>();
        String[][] specs = {{"qc_iqc", "PRIMARY", "iqc_id"}, {"qc_inspect_detail", "PRIMARY", "detail_id"},
            {"qc_iqc", "uk_actual_scope", "tenant_id", "receive_id", "material_id"}};
        for (String[] spec : specs) for (int i = 2; i < spec.length; i++) {
            var row = new IqcNativeSchema.IndexPart(); row.setTableName(spec[0]); row.setIndexName(spec[1]);
            row.setColumnName(spec[i]); row.setSeqInIndex(i - 1); row.setNonUnique(0); row.setIndexType("BTREE"); rows.add(row);
        }
        return rows;
    }

    void arrange() throws Exception {
        when(mapper.selectNativeColumns()).thenAnswer(i -> nativeColumns());
        when(mapper.selectNativeIndexes()).thenAnswer(i -> nativeIndexes());
        routing.addDataSource("mes", mes); routing.addDataSource("master", master);
        when(mes.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenAnswer(i -> autoCommit.get());
        doAnswer(i -> { autoCommit.set(i.getArgument(0)); return null; }).when(connection).setAutoCommit(anyBoolean());
        when(connection.isReadOnly()).thenAnswer(i -> readOnly.get());
        doAnswer(i -> { readOnly.set(i.getArgument(0)); return null; }).when(connection).setReadOnly(anyBoolean());
        when(connection.getTransactionIsolation()).thenAnswer(i -> isolation.get());
        doAnswer(i -> { isolation.set(i.getArgument(0)); return null; }).when(connection).setTransactionIsolation(anyInt());
        Configuration config = new Configuration(new Environment("private-mocked-mes",
            new SpringManagedTransactionFactory(), routing));
        config.setMapUnderscoreToCamelCase(true); config.addMapper(QcReceiptIqcEvidenceMapper.class);
        config.addMapper(QcReceiptIqcBirthMapper.class);
        service = new QcReceiptIqcEvidenceService(mapper, birthMapper, new DefaultSqlSessionFactory(config));
        row.setIqcId(501L); row.setTenantId(tenantId); row.setIqcNo("IQC-501"); row.setReceiveId(101L);
        row.setMaterialId(201L); row.setQuantity(new BigDecimal("12.0000")); row.setStatus("COMPLETED");
        row.setInspectResult("QUALIFIED"); row.setInspectorId(801L); row.setDelFlag("0");
        row.setInspectTime(new Date(now - 2000)); row.setCreateTime(new Date(now - 10000));
        row.setUpdateTime(new Date(now - 2000)); row.setActualBody("{\"actualNativeRow\":501}");
        when(mapper.selectInspection(tenantId, 501L)).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(((ConnectionHolder) TransactionSynchronizationManager.getResource(routing)).getConnection())
                .isSameAs(connection);
            assertThat(isolation.get()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("mes");
            return row;
        });
        when(mapper.selectInspectionVersions(tenantId, 101L, 201L)).thenReturn(List.of(501L));
        when(mapper.selectInspectionBodies(tenantId, 501L)).thenReturn(List.of());
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clear(); DynamicDataSourceContextHolder.clear(); }

    @Test void readsCurrentNativeMesInIndependentReadOnlyRrAndRestoresRoute() throws Exception {
        arrange(); DynamicDataSourceContextHolder.push("erp");
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true);
            tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            var actual = service.readCompleted(tenantId, 501L);
            assertThat(actual.actualBodySha256()).matches("[0-9a-f]{64}");
            assertThat(actual.quantity()).isEqualByComparingTo("12");
            assertThat(actual.sourceVersion()).isEqualTo("QC_IQC_CURRENT_V1:" + actual.actualBodySha256());
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("erp");
            assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
            verify(connection).commit(); verifyNoInteractions(master);
        }
    }
    @Test void deletedHistoricalSourceCannotBeCertified() throws Exception {
        arrange(); row.setDelFlag("2");
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompleted(tenantId, 501L)).isInstanceOf(ServiceException.class);
            verify(connection).rollback(); verify(connection, never()).commit();
        }
    }
    @Test void multipleHistoricalVersionsAreAmbiguousEvenIfLatestLooksQualified() throws Exception {
        arrange(); when(mapper.selectInspectionVersions(tenantId, 101L, 201L)).thenReturn(List.of(400L, 501L));
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompleted(tenantId, 501L)).isInstanceOf(ServiceException.class);
            verify(connection).rollback();
        }
    }

    @Test void unknownOrMissingActualMesDatasourceFailsWithoutBorrowingMaster() throws Exception {
        arrange(); routing.getDataSources().remove("mes");
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompleted(tenantId, 501L)).hasMessage("IQC_MES_SOURCE_UNAVAILABLE");
            verifyNoInteractions(master, mes, mapper);
        }
    }
    @Test void pendingInspectionDoesNotBecomeCompletedBecauseMessageSaysQualified() throws Exception {
        arrange(); row.setStatus("INSPECTING");
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompleted(tenantId, 501L)).hasMessage("IQC_MES_CURRENT_COMPLETION_REQUIRED");
            verify(connection).rollback();
        }
    }
    @Test void completionWithNoHistoricalTimeOrInspectorIsNotAuthenticEvidence() throws Exception {
        arrange(); row.setInspectTime(null); row.setInspectorId(null);
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompleted(tenantId, 501L)).hasMessage("IQC_MES_CURRENT_COMPLETION_REQUIRED");
            verify(connection).rollback();
        }
    }
    @Test void completionFromAnotherTenantFailsEvenIfMapperOrPluginReturnsIt() throws Exception {
        arrange(); row.setTenantId("000008");
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompleted(tenantId, 501L)).hasMessage("IQC_MES_CURRENT_COMPLETION_REQUIRED");
        }
    }
    @Test void inheritedErpTransactionIsRejectedBeforeAnyMesRead() throws Exception {
        arrange(); TransactionSynchronizationManager.setActualTransactionActive(true);
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompleted(tenantId, 501L)).hasMessage("IQC_MES_SOURCE_REQUIRES_FRESH_THREAD");
            verifyNoInteractions(mes, master, mapper);
        }
    }
    DetailBody detail(Long id, String body) {
        DetailBody d = new DetailBody(); d.setDetailId(id); d.setTenantId(tenantId); d.setCheckId(501L);
        d.setCheckType("IQC"); d.setActualBody(body); return d;
    }
    @Test void everyActualDetailBodyAffectsHashAndOrderingIsDeterministic() throws Exception {
        arrange(); DetailBody first = detail(1L, "{\"measured_value\":\"OK\"}");
        DetailBody second = detail(2L, "{\"measured_value\":\"10.000001\"}");
        when(mapper.selectInspectionBodies(tenantId, 501L)).thenReturn(List.of(first, second));
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            String original = service.readCompleted(tenantId, 501L).actualBodySha256();
            when(mapper.selectInspectionBodies(tenantId, 501L)).thenReturn(List.of(second, first));
            assertThat(service.readCompleted(tenantId, 501L).actualBodySha256()).isEqualTo(original);
            second.setActualBody("{\"measured_value\":\"10.000002\"}");
            assertThat(service.readCompleted(tenantId, 501L).actualBodySha256()).isNotEqualTo(original);
        }
    }
    @Test void nativeHeaderBodyChangesWithoutUpdateVersionStillChangeFingerprint() throws Exception {
        arrange();
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            String original = service.readCompleted(tenantId, 501L).actualBodySha256();
            row.setActualBody("{\"actualNativeRow\":501,\"remark\":\"changed\"}");
            assertThat(service.readCompleted(tenantId, 501L).actualBodySha256()).isNotEqualTo(original);
        }
    }
    @Test void failedSourceReadRestoresOriginalRouteAndSanitizesJdbcErrors() throws Exception {
        arrange(); DynamicDataSourceContextHolder.push("erp");
        doThrow(new IllegalStateException("secret jdbc detail")).when(mapper).selectInspection(tenantId, 501L);
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompleted(tenantId, 501L)).hasMessage("IQC_MES_SOURCE_UNAVAILABLE").hasNoCause();
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("erp");
            assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
            verify(connection).rollback();
        }
    }

    @Test void actualMesFloatQuantityFailsBeforeReadingInspectionEvenWhenBigDecimalWouldLookExact() throws Exception {
        arrange(); var columns = nativeColumns(); columns.stream().filter(c -> "quantity".equals(c.getColumnName()))
            .forEach(c -> c.setDataType("float")); doReturn(columns).when(mapper).selectNativeColumns();
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompleted(tenantId, 501L)).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
            verify(mapper, never()).selectInspection(anyString(), anyLong()); verify(connection).rollback();
        }
    }
    @Test void myisamMesDetailCannotSupplyRepeatableTransactionalBodySnapshot() throws Exception {
        arrange(); var columns = nativeColumns(); columns.stream().filter(c -> "qc_inspect_detail".equals(c.getTableName()))
            .forEach(c -> c.setEngine("MyISAM")); doReturn(columns).when(mapper).selectNativeColumns();
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompleted(tenantId, 501L)).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
            verify(mapper, never()).selectInspection(anyString(), anyLong());
        }
    }
    @Test void absentPermanentMesReceiveMaterialUniqueDoesNotBecomeHistoricalVersionAuthority() throws Exception {
        arrange(); var keys = nativeIndexes(); keys.removeIf(k -> "uk_actual_scope".equals(k.getIndexName()));
        doReturn(keys).when(mapper).selectNativeIndexes();
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompleted(tenantId, 501L)).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
            verify(mapper, never()).selectInspection(anyString(), anyLong());
        }
    }
    @Test void definitionsAreRecheckedBeforeReturningNativeObservation() throws Exception {
        arrange(); var valid = nativeColumns(); var invalid = nativeColumns();
        invalid.stream().filter(c -> "quantity".equals(c.getColumnName())).forEach(c -> c.setNumericScale(3));
        doReturn(valid, invalid).when(mapper).selectNativeColumns();
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompleted(tenantId, 501L)).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
            verify(mapper, times(2)).selectNativeColumns(); verify(connection).rollback();
        }
    }

    @Test void negativeIdentityReadNeedsNoQuantityUnitNumericValueDetailTableOrScopeUnique() throws Exception {
        arrange(); row.setInspectResult("UNQUALIFIED"); row.setQuantity(null);
        doReturn(row).when(mapper).selectInspectionIdentity(tenantId, 501L);
        var columns = nativeColumns(); columns.stream().filter(c -> "quantity".equals(c.getColumnName())).forEach(c -> c.setDataType("float"));
        columns.stream().filter(c -> "qc_inspect_detail".equals(c.getTableName())).forEach(c -> c.setEngine("MyISAM"));
        var keys = nativeIndexes(); keys.removeIf(k -> "uk_actual_scope".equals(k.getIndexName()));
        doReturn(columns).when(mapper).selectNativeColumns(); doReturn(keys).when(mapper).selectNativeIndexes();
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            var actual = service.readCompletedIdentity(tenantId, 501L);
            assertThat(actual.result()).isEqualTo("UNQUALIFIED"); assertThat(actual.quantity()).isNull();
            assertThat(actual.receiveId()).isEqualTo(101L); assertThat(actual.materialId()).isEqualTo(201L);
            verify(mapper, never()).selectInspection(anyString(), anyLong());
            verify(mapper, never()).selectInspectionBodies(anyString(), anyLong());
            verify(mapper, never()).selectInspectionVersions(anyString(), anyLong(), anyLong());
            verify(connection).commit(); verifyNoInteractions(birthMapper);
        }
    }
    @Test void negativeIdentityReaderStillRejectsOtherTenantOrPendingState() throws Exception {
        arrange(); row.setTenantId("000008"); row.setQuantity(null);
        doReturn(row).when(mapper).selectInspectionIdentity(tenantId, 501L);
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompletedIdentity(tenantId, 501L)).hasMessage("IQC_MES_CURRENT_COMPLETION_REQUIRED");
            verify(connection).rollback();
        }
    }

    Binding arrangeBirth() {
        long boundAt = now - 10000;
        Birth birth = QcReceiptIqcBirthServiceTest.birth(tenantId, now - 11000);
        row.setQuantity(birth.quantity()); row.setCreateTime(new Date(boundAt / 1000 * 1000));
        Binding binding = Binding.from(501L, birth, new Date(boundAt), ReceiptIqcBirthSupport.bindingHash(501L, birth, boundAt));
        when(birthMapper.selectNativeColumns()).thenAnswer(i -> QcReceiptIqcBirthServiceTest.nativeColumns());
        when(birthMapper.selectNativeIndexes()).thenAnswer(i -> QcReceiptIqcBirthServiceTest.nativeIndexes());
        when(birthMapper.selectBinding(tenantId, 501L)).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
            return binding;
        });
        return binding;
    }
    @Test void nativeBirthBodyAndDetailsUseOneIndependentReadOnlySnapshot() throws Exception {
        arrange(); Binding binding = arrangeBirth();
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            var actual = service.readCompletedBorn(tenantId, 501L);
            assertThat(actual.inspection().sourceVersion()).startsWith("QC_IQC_CURRENT_V1:");
            assertThat(actual.birth()).isEqualTo(binding.birth()); assertThat(actual.boundAt()).isEqualTo(binding.getBoundAt().getTime());
            assertThat(actual.bindingSha256()).isEqualTo(binding.getBindingSha256());
            verify(mes, times(1)).getConnection(); verify(connection).commit(); verify(connection, never()).rollback();
            verify(mapper).selectInspection(tenantId, 501L); verify(mapper).selectInspectionBodies(tenantId, 501L);
            verify(birthMapper).selectBinding(tenantId, 501L); verify(birthMapper, times(2)).selectNativeColumns();
            verify(master, never()).getConnection();
        }
    }
    @Test void existingCompletedIqcWithoutBindingKeepsUnitOriginUnproven() throws Exception {
        arrange(); arrangeBirth(); doReturn(null).when(birthMapper).selectBinding(tenantId, 501L);
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompletedBorn(tenantId, 501L)).hasMessage("UNIT_ORIGIN_UNPROVEN");
            verify(connection).rollback();
        }
    }
    @Test void birthCannotBeReconstructedFromInspectionMeasurementUnit() throws Exception {
        arrange(); Binding binding = arrangeBirth(); binding.setBaseUomId(999L);
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompletedBorn(tenantId, 501L)).hasMessage("IQC_BIRTH_BINDING_INVALID");
            verify(connection).rollback();
        }
    }
    @Test void alteredBodyQuantityCannotReuseOriginalUnitBinding() throws Exception {
        arrange(); arrangeBirth(); row.setQuantity(new BigDecimal("12.35"));
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompletedBorn(tenantId, 501L)).hasMessage("IQC_BIRTH_BINDING_INVALID");
            verify(connection).rollback();
        }
    }
    @Test void correctlyHashedBindingCannotCertifyAnOldCreationTime() throws Exception {
        arrange(); arrangeBirth(); row.setCreateTime(new Date(now - 86400000));
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompletedBorn(tenantId, 501L)).hasMessage("IQC_BIRTH_BINDING_INVALID");
            verify(connection).rollback();
        }
    }
    @Test void birthMetadataRecheckCannotWeakenNativeBoundPrecision() throws Exception {
        arrange(); arrangeBirth(); var changed = QcReceiptIqcBirthServiceTest.nativeColumns();
        changed.stream().filter(c -> "bound_at".equals(c.getColumnName())).forEach(c -> c.setDatetimePrecision(0));
        doReturn(QcReceiptIqcBirthServiceTest.nativeColumns(), changed).when(birthMapper).selectNativeColumns();
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertThatThrownBy(() -> service.readCompletedBorn(tenantId, 501L)).hasMessage("IQC_BIRTH_NATIVE_SCHEMA_UNSUPPORTED");
            verify(connection).rollback();
        }
    }
}

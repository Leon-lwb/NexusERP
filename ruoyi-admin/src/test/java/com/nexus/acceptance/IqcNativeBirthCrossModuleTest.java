package com.nexus.acceptance;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.mq.iqc.IqcNativeSchema.Column;
import com.nexus.common.mq.iqc.IqcNativeSchema.IndexPart;
import com.nexus.common.mq.iqc.ReceiptIqcBirthPort.Birth;
import com.nexus.common.mq.iqc.ReceiptIqcBirthSupport;
import com.nexus.common.mq.message.IqcCompletedMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.wm.mapper.WmReceiptIqcBirthMapper;
import com.nexus.erp.wm.mapper.WmReceiptIqcBirthMapper.BirthRow;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper.EvidenceRow;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper.MaterialUnit;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper.NativeUnit;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper.ReceiptHeader;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper.ReceiptLine;
import com.nexus.erp.wm.service.WmReceiptIqcEvidenceService;
import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper;
import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper.Binding;
import com.nexus.mes.qc.mapper.QcReceiptIqcBirthMapper.NativeColumn;
import com.nexus.mes.qc.mapper.QcReceiptIqcEvidenceMapper;
import com.nexus.mes.qc.mapper.QcReceiptIqcEvidenceMapper.DetailBody;
import com.nexus.mes.qc.mapper.QcReceiptIqcEvidenceMapper.InspectionRow;
import com.nexus.mes.qc.service.QcReceiptIqcEvidenceService;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 真正跨模块调用的单测：真实 MES reader 读取非空明细并计算摘要，再由真实 ERP 服务认证。
 * 仅 JDBC、Mapper 和 ERP 事务边界使用替身，不 stub 来源接口或 Inspection/BornInspection 返回值。
 * 不启动应用、不访问数据库或消息服务；此结果不替代实际 MySQL 跨库与部署验收。
 */
class IqcNativeBirthCrossModuleTest {
    private static final String TENANT = "000007";
    private final long createdAt = System.currentTimeMillis() / 1000 * 1000 - 90000;
    private final DynamicRoutingDataSource routing = new DynamicRoutingDataSource(List.of());
    private final DataSource mes = mock(DataSource.class);
    private final DataSource master = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final AtomicBoolean autoCommit = new AtomicBoolean(true);
    private final AtomicBoolean readOnly = new AtomicBoolean();
    private final AtomicInteger isolation = new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED);
    private final AtomicBoolean inErp = new AtomicBoolean();
    private final AtomicInteger mesCommits = new AtomicInteger();
    private final AtomicReference<EvidenceRow> stored = new AtomicReference<>();
    private final QcReceiptIqcEvidenceMapper mesMapper = mock(QcReceiptIqcEvidenceMapper.class);
    private final QcReceiptIqcBirthMapper mesBirths = mock(QcReceiptIqcBirthMapper.class);
    private final WmReceiptIqcEvidenceMapper erpMapper = mock(WmReceiptIqcEvidenceMapper.class);
    private final WmReceiptIqcBirthMapper erpBirths = mock(WmReceiptIqcBirthMapper.class);
    private final PuRequirementErpTransactionExecutor transactions = mock(PuRequirementErpTransactionExecutor.class);
    private final PuRequirementErpTransactionQualification qualification = mock(PuRequirementErpTransactionQualification.class);
    private final InspectionRow header = new InspectionRow();
    private final DetailBody detail = new DetailBody();
    private QcReceiptIqcEvidenceService reader;
    private WmReceiptIqcEvidenceService evidence;
    private Birth birth;
    private MockedStatic<TenantHelper> tenant;

    @BeforeEach
    void arrangeRealServicesWithNativeMesAndFakeErpStorage() throws Exception {
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        arrangeMesConnectionAndReader();
        arrangeErpStorageAndTransactions();
        evidence = new WmReceiptIqcEvidenceService(erpMapper, reader, transactions, qualification, erpBirths);
        // 冻结范围摘要由真实 ERP 范围逻辑计算，禁止用临时 Source DTO 或任意常量冒充来源范围。
        var scope = transactions.executeRepeatableRead(TENANT,
            () -> evidence.captureBirthScopeLocked(TENANT, 101L, 201L));
        Birth raw = new Birth(TENANT, 701L, 101L, 201L, 301L, 4, new BigDecimal("12.0000"), scope.sha256(),
            null, createdAt + 200, "RECV-101", "MAT-201", "原始物料", 901L, "原始供应商");
        birth = new Birth(raw.tenantId(), raw.birthId(), raw.receiveId(), raw.materialId(), raw.baseUomId(),
            raw.quantityScale(), raw.quantity(), raw.receiptScopeSha256(), ReceiptIqcBirthSupport.birthHash(raw),
            raw.capturedAt(), raw.receiveCode(), raw.materialCode(), raw.materialName(), raw.vendorId(), raw.vendorName());
        long boundAt = createdAt + 371;
        Binding binding = Binding.from(501L, birth, new Date(boundAt), ReceiptIqcBirthSupport.bindingHash(501L, birth, boundAt));
        when(mesBirths.selectBinding(TENANT, 501L)).thenAnswer(call -> { requireMesSnapshot(); return binding; });
        when(erpBirths.lockId(TENANT, 701L)).thenAnswer(call -> { requireErpFrame(); return registryRow(birth); });
        clearInvocations(erpMapper, erpBirths, transactions, qualification);
    }

    private void arrangeMesConnectionAndReader() throws Exception {
        routing.addDataSource("mes", mes); routing.addDataSource("master", master);
        when(mes.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenAnswer(call -> autoCommit.get());
        doAnswer(call -> { autoCommit.set(call.getArgument(0)); return null; }).when(connection).setAutoCommit(anyBoolean());
        when(connection.isReadOnly()).thenAnswer(call -> readOnly.get());
        doAnswer(call -> { readOnly.set(call.getArgument(0)); return null; }).when(connection).setReadOnly(anyBoolean());
        when(connection.getTransactionIsolation()).thenAnswer(call -> isolation.get());
        doAnswer(call -> { isolation.set(call.getArgument(0)); return null; }).when(connection).setTransactionIsolation(anyInt());
        doAnswer(call -> { requireMesSnapshot(); mesCommits.incrementAndGet(); return null; }).when(connection).commit();
        Configuration config = new Configuration(new Environment("isolated-native-mes-test",
            new SpringManagedTransactionFactory(), routing));
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(QcReceiptIqcEvidenceMapper.class); config.addMapper(QcReceiptIqcBirthMapper.class);
        reader = new QcReceiptIqcEvidenceService(mesMapper, mesBirths, new DefaultSqlSessionFactory(config));
        header.setIqcId(501L); header.setTenantId(TENANT); header.setIqcNo("IQC-501"); header.setReceiveId(101L);
        header.setMaterialId(201L); header.setQuantity(new BigDecimal("12.0000")); header.setStatus("COMPLETED");
        header.setInspectResult("QUALIFIED"); header.setInspectorId(801L); header.setDelFlag("0");
        header.setCreateTime(new Date(createdAt)); header.setInspectTime(new Date(createdAt + 30000));
        header.setUpdateTime(new Date(createdAt + 30000));
        header.setActualBody("{\"iqc_id\":501,\"tenant_id\":\"000007\",\"receive_id\":101,\"material_id\":201,"
            + "\"quantity\":12.0000,\"status\":\"COMPLETED\",\"inspect_result\":\"QUALIFIED\",\"inspector_id\":801}");
        detail.setDetailId(601L); detail.setTenantId(TENANT); detail.setCheckId(501L); detail.setCheckType("IQC");
        detail.setActualBody(detailBody("12.0"));
        when(mesMapper.selectInspectionIdentity(TENANT, 501L)).thenAnswer(call -> { requireMesSnapshot(); return header; });
        when(mesMapper.selectInspection(TENANT, 501L)).thenAnswer(call -> { requireMesSnapshot(); return header; });
        when(mesMapper.selectInspectionVersions(TENANT, 101L, 201L)).thenReturn(List.of(501L));
        when(mesMapper.selectInspectionBodies(TENANT, 501L)).thenAnswer(call -> { requireMesSnapshot(); return List.of(detail); });
        when(mesMapper.selectNativeColumns()).thenAnswer(call -> {
            requireMesSnapshot(); return mesColumns().stream().filter(c -> !"qc_receipt_iqc_birth".equals(c.getTableName()))
                .map(c -> (Column)c).toList();
        });
        when(mesMapper.selectNativeIndexes()).thenAnswer(call -> {
            requireMesSnapshot(); return mesIndexes().stream().filter(i -> !"qc_receipt_iqc_birth".equals(i.getTableName())).toList();
        });
        when(mesBirths.selectNativeColumns()).thenAnswer(call -> { requireMesSnapshot(); return mesColumns(); });
        when(mesBirths.selectNativeIndexes()).thenAnswer(call -> { requireMesSnapshot(); return mesIndexes(); });
    }

    private void arrangeErpStorageAndTransactions() {
        when(transactions.executeRepeatableRead(eq(TENANT), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
            assertThat(DynamicDataSourceContextHolder.peek()).isNull(); assertThat(inErp.getAndSet(true)).isFalse();
            EvidenceRow before = stored.get();
            try { return ((Supplier<?>)call.getArgument(1)).get(); }
            catch (RuntimeException | Error failure) { stored.set(before); throw failure; }
            finally { inErp.set(false); }
        });
        doAnswer(call -> { requireErpFrame(); return null; }).when(qualification).requireSourceChainTransaction();
        ReceiptHeader receipt = new ReceiptHeader(); receipt.setTenantId(TENANT); receipt.setReceiveId(101L);
        receipt.setReceiveCode("RECV-101"); receipt.setStatus("APPROVED"); receipt.setDelFlag("0");
        receipt.setCreateTime(new Date(createdAt - 30000)); receipt.setPurId(401L); receipt.setVendorId(901L);
        when(erpMapper.selectReceiveForUpdate(TENANT, 101L)).thenReturn(receipt);
        when(erpMapper.selectMaterialLinesForUpdate(TENANT, 101L, 201L)).thenReturn(List.of(receiptLine(11L, "5.0000"), receiptLine(12L, "7.0000")));
        MaterialUnit material = new MaterialUnit(); material.setTenantId(TENANT); material.setMaterialId(201L);
        material.setBaseUomId(301L); material.setDelFlag("0"); when(erpMapper.selectMaterialForUpdate(TENANT, 201L)).thenReturn(material);
        NativeUnit unit = new NativeUnit(); unit.setCategory("COUNT"); unit.setIsBase(1L); unit.setPrimaryId(301L); unit.setConversionRate(BigDecimal.ONE);
        unit.setConversionVersion("root-v1"); unit.setConversionVerifiedAt(new Date(System.currentTimeMillis()-1000000));
        unit.setTenantId(TENANT); unit.setUomId(301L); unit.setQuantityScale(4);
        unit.setStatus(1); unit.setDelFlag("0"); when(erpMapper.selectUnitForUpdate(TENANT, 301L)).thenReturn(unit);
        when(erpMapper.selectNativeColumns()).thenAnswer(call -> { requireErpFrame(); return erpColumns(); });
        when(erpMapper.selectNativeIndexes()).thenAnswer(call -> { requireErpFrame(); return erpIndexes(); });
        when(erpBirths.columns()).thenAnswer(call -> { requireErpFrame(); return erpBirthColumns(); });
        when(erpBirths.indexes()).thenAnswer(call -> {
            requireErpFrame(); List<IndexPart> indexes = new ArrayList<>();
            key(indexes, "wm_receipt_iqc_birth", "PRIMARY", "birth_id");
            key(indexes, "wm_receipt_iqc_birth", "uk_birth_scope", "tenant_id", "receive_id", "material_id"); return indexes;
        });
        when(erpMapper.selectMessageForUpdate(TENANT, "event-501")).thenAnswer(call -> { requireErpFrame(); return stored.get(); });
        when(erpMapper.selectInspectionForUpdate(TENANT, 501L)).thenAnswer(call -> { requireErpFrame(); return stored.get(); });
        when(erpMapper.selectReceiptMaterialForUpdate(TENANT, 101L, 201L)).thenAnswer(call -> { requireErpFrame(); return stored.get(); });
        when(erpMapper.insertEvidence(any())).thenAnswer(call -> {
            requireErpFrame(); assertThat(stored.get()).isNull(); stored.set(call.getArgument(0)); return 1;
        });
    }

    @AfterEach
    void releaseOnlyTestThreadState() {
        if (tenant != null) tenant.close();
        TransactionSynchronizationManager.clear(); DynamicDataSourceContextHolder.clear();
    }

    @Test
    void differentIdentityAndFullDetailDigestsStillPersistTheRealBornSnapshotAsV2() throws Exception {
        assertThat(mockingDetails(reader).isMock()).isFalse();
        var identity = reader.readCompletedIdentity(TENANT, 501L);
        var born = reader.readCompletedBorn(TENANT, 501L);
        assertThat(identity.actualBodySha256()).isNotEqualTo(born.inspection().actualBodySha256());
        assertThat(identity.sourceVersion()).isNotEqualTo(born.inspection().sourceVersion()); assertThat(identity.quantity()).isNull();
        assertThat(born.boundAt() - born.inspection().createdAt()).isEqualTo(371);
        assertThat(born.birth().capturedAt()).isGreaterThan(born.inspection().createdAt());
        assertThat(born.birth().baseUomId()).isEqualTo(301L);
        evidence.consumeCompleted(message(), completion -> {
            requireErpFrame(); assertThat(completion.result()).isEqualTo("QUALIFIED");
            assertThat(completion.quantity()).isEqualByComparingTo("12.0000");
        });
        EvidenceRow row = stored.get(); assertThat(row).isNotNull();
        assertThat(row.getSourceKind()).isEqualTo("MES_QC_IQC_RECEIVE_MATERIAL_V2");
        assertThat(row.getActualBodySha256()).isEqualTo(born.inspection().actualBodySha256());
        assertThat(row.getSourceVersion()).isEqualTo(born.inspection().sourceVersion());
        assertThat(row.getBirthId()).isEqualTo(701L); assertThat(row.getBirthSha256()).isEqualTo(birth.birthSha256());
        verify(mesMapper, times(2)).selectInspectionBodies(TENANT, 501L); verify(erpBirths).lockId(TENANT, 701L);
        verify(erpMapper).insertEvidence(any()); assertThat(mesCommits.get()).isEqualTo(4);
        verify(connection, never()).rollback(); verify(master, never()).getConnection();
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
    }

    @Test
    void changedFullDetailBodyRejectsReplayEvenThoughHeadAndNativeBirthAreUnchanged() {
        String identityBefore = reader.readCompletedIdentity(TENANT, 501L).actualBodySha256();
        evidence.consumeCompleted(message(), completion -> { });
        EvidenceRow original = stored.get(); String fullBefore = original.getActualBodySha256();
        detail.setActualBody(detailBody("12.1"));
        assertThat(reader.readCompletedIdentity(TENANT, 501L).actualBodySha256()).isEqualTo(identityBefore);
        assertThat(reader.readCompletedBorn(TENANT, 501L).inspection().actualBodySha256()).isNotEqualTo(fullBefore);
        assertThatThrownBy(() -> evidence.consumeCompleted(message(), completion -> fail("变动后的检验明细不能重复认证")))
            .hasMessage("IQC_EVIDENCE_IMMUTABLE_CONFLICT");
        assertThat(stored.get()).isSameAs(original); assertThat(stored.get().getActualBodySha256()).isEqualTo(fullBefore);
        verify(erpMapper, times(1)).insertEvidence(any()); verify(mesMapper, times(3)).selectInspectionBodies(TENANT, 501L);
    }

    @Test
    void legacySameNumericQuantityAndExistingErpRegistrationNeverFillMissingMesBirth() throws Exception {
        doReturn(null).when(mesBirths).selectBinding(TENANT, 501L);
        assertThat(birth.quantity()).isEqualByComparingTo(header.getQuantity());
        assertThatThrownBy(() -> evidence.consumeCompleted(message(), completion -> fail("旧检验不能补出生")))
            .hasMessage("UNIT_ORIGIN_UNPROVEN");
        assertThat(stored.get()).isNull(); verify(erpMapper, never()).insertEvidence(any());
        verify(erpBirths, never()).lockId(anyString(), anyLong()); verifyNoInteractions(transactions);
        verify(mesMapper).selectInspectionIdentity(TENANT, 501L); verify(mesMapper).selectInspectionBodies(TENANT, 501L);
        verify(connection).rollback(); verify(master, never()).getConnection();
    }

    private void requireMesSnapshot() {
        assertThat(inErp.get()).isFalse(); assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
        assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
        assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("mes");
        assertThat(TransactionSynchronizationManager.getResource(routing)).isInstanceOf(ConnectionHolder.class);
        assertThat(((ConnectionHolder)TransactionSynchronizationManager.getResource(routing)).getConnection()).isSameAs(connection);
        assertThat(autoCommit.get()).isFalse(); assertThat(readOnly.get()).isTrue();
    }
    private void requireErpFrame() {
        assertThat(inErp.get()).isTrue(); assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
    }
    private static String detailBody(String measured) {
        return "{\"detail_id\":601,\"tenant_id\":\"000007\",\"check_id\":501,\"check_type\":\"IQC\","
            + "\"unit\":\"mm\",\"measured_value\":\"" + measured + "\",\"judge_result\":\"PASS\"}";
    }
    private static IqcCompletedMessage message() {
        IqcCompletedMessage message = new IqcCompletedMessage(); message.setTenantId(7L); message.setMessageId("event-501");
        message.setIqcId(501L); message.setReceiveId(101L); message.setMaterialId(201L);
        message.setInspectResult("UNQUALIFIED"); message.setQuantity(new BigDecimal("999")); return message;
    }
    private static ReceiptLine receiptLine(Long id, String quantity) {
        ReceiptLine line = new ReceiptLine(); line.setTenantId(TENANT); line.setLineId(id); line.setReceiveId(101L);
        line.setMaterialId(201L); line.setBaseUomId("301"); line.setQuantityReceived(new BigDecimal(quantity)); line.setDelFlag("0"); return line;
    }
    private static BirthRow registryRow(Birth b) {
        BirthRow row = new BirthRow(); row.setTenantId(b.tenantId()); row.setBirthId(b.birthId()); row.setReceiveId(b.receiveId());
        row.setMaterialId(b.materialId()); row.setBaseUomId(b.baseUomId()); row.setQuantityScale(b.quantityScale()); row.setQuantity(b.quantity());
        row.setReceiptScopeSha256(b.receiptScopeSha256()); row.setBirthSha256(b.birthSha256()); row.setCapturedAt(b.capturedAt());
        row.setReceiveCode(b.receiveCode()); row.setMaterialCode(b.materialCode()); row.setMaterialName(b.materialName());
        row.setVendorId(b.vendorId()); row.setVendorName(b.vendorName()); return row;
    }
    private static NativeColumn column(String table, String name, String type) {
        NativeColumn c = new NativeColumn(); c.setTableName(table); c.setColumnName(name); c.setDataType(type);
        c.setEngine("InnoDB"); c.setIsNullable("NO");
        if ("decimal".equals(type)) { c.setNumericPrecision(14); c.setNumericScale(4); } return c;
    }
    private static void key(List<IndexPart> result, String table, String name, String... columns) {
        for (int i = 0; i < columns.length; i++) {
            IndexPart part = new IndexPart(); part.setTableName(table); part.setIndexName(name); part.setColumnName(columns[i]);
            part.setNonUnique(0); part.setSeqInIndex(i + 1); part.setIndexType("BTREE"); result.add(part);
        }
    }
    private static List<NativeColumn> mesColumns() {
        List<NativeColumn> result = new ArrayList<>();
        for (String name : List.of("iqc_id", "receive_id", "material_id")) result.add(column("qc_iqc", name, "bigint"));
        result.add(column("qc_iqc", "tenant_id", "varchar")); result.add(column("qc_iqc", "quantity", "decimal"));
        var created = column("qc_iqc", "create_time", "datetime"); created.setDatetimePrecision(0); created.setIsNullable("YES"); result.add(created);
        result.add(column("qc_inspect_detail", "detail_id", "bigint"));
        for (String name : List.of("iqc_id", "birth_id", "receive_id", "material_id", "base_uom_id", "vendor_id", "captured_at"))
            result.add(column("qc_receipt_iqc_birth", name, "bigint"));
        for (String name : List.of("tenant_id", "receipt_scope_sha256", "birth_sha256", "binding_sha256", "receive_code", "material_code", "material_name", "vendor_name"))
            result.add(column("qc_receipt_iqc_birth", name, "varchar"));
        result.add(column("qc_receipt_iqc_birth", "quantity_scale", "int")); result.add(column("qc_receipt_iqc_birth", "quantity", "decimal"));
        var bound = column("qc_receipt_iqc_birth", "bound_at", "datetime"); bound.setDatetimePrecision(3); result.add(bound); return result;
    }
    private static List<IndexPart> mesIndexes() {
        List<IndexPart> result = new ArrayList<>(); key(result, "qc_iqc", "PRIMARY", "iqc_id");
        key(result, "qc_iqc", "uk_scope", "tenant_id", "receive_id", "material_id"); key(result, "qc_inspect_detail", "PRIMARY", "detail_id");
        key(result, "qc_receipt_iqc_birth", "PRIMARY", "iqc_id"); key(result, "qc_receipt_iqc_birth", "uk_iqc", "tenant_id", "iqc_id");
        key(result, "qc_receipt_iqc_birth", "uk_birth", "tenant_id", "birth_id");
        key(result, "qc_receipt_iqc_birth", "uk_scope", "tenant_id", "receive_id", "material_id"); return result;
    }
    private static List<Column> erpColumns() {
        List<Column> result = new ArrayList<>(); result.add(column("wm_material_receive", "receive_id", "bigint"));
        result.add(column("wm_material_receive_line", "line_id", "bigint"));
        var quantity = column("wm_material_receive_line", "quantity_received", "decimal"); quantity.setNumericPrecision(18); result.add(quantity);
        result.add(column("bd_material", "material_id", "bigint")); result.add(column("bd_unit_of_measure", "uom_id", "bigint"));
        for (String name : List.of("evidence_id", "inspection_id", "receive_id", "material_id", "base_uom_id"))
            result.add(column("wm_certified_iqc_evidence", name, "bigint"));
        result.add(column("wm_certified_iqc_evidence", "tenant_id", "varchar"));
        result.add(column("wm_certified_iqc_evidence", "first_message_id", "varchar"));
        result.add(column("wm_certified_iqc_evidence", "inspected_quantity", "decimal")); return result;
    }
    private static List<IndexPart> erpIndexes() {
        List<IndexPart> result = new ArrayList<>(); key(result, "wm_material_receive", "PRIMARY", "receive_id");
        key(result, "wm_material_receive_line", "PRIMARY", "line_id"); key(result, "bd_material", "PRIMARY", "material_id");
        key(result, "bd_unit_of_measure", "PRIMARY", "uom_id"); key(result, "wm_certified_iqc_evidence", "PRIMARY", "evidence_id");
        key(result, "wm_certified_iqc_evidence", "uk_inspection", "tenant_id", "inspection_id");
        key(result, "wm_certified_iqc_evidence", "uk_scope", "tenant_id", "receive_id", "material_id");
        key(result, "wm_certified_iqc_evidence", "uk_message", "tenant_id", "first_message_id"); return result;
    }
    private static List<Column> erpBirthColumns() {
        List<Column> result = new ArrayList<>();
        for (String name : List.of("birth_id", "receive_id", "material_id", "base_uom_id", "vendor_id", "captured_at"))
            result.add(column("wm_receipt_iqc_birth", name, "bigint"));
        result.add(column("wm_receipt_iqc_birth", "tenant_id", "varchar")); result.add(column("wm_receipt_iqc_birth", "quantity_scale", "tinyint"));
        result.add(column("wm_receipt_iqc_birth", "receipt_scope_sha256", "char")); result.add(column("wm_receipt_iqc_birth", "birth_sha256", "char"));
        result.add(column("wm_receipt_iqc_birth", "quantity", "decimal")); return result;
    }
}

package com.nexus.erp.wm;

import com.nexus.common.mq.iqc.IqcNativeSchema;
import com.nexus.common.mq.iqc.IqcNativeSchema.Column;
import com.nexus.common.mq.iqc.IqcNativeSchema.IndexPart;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WmReceiptIqcEvidenceSchemaTest {
    static Column col(String table, String name, String type, Integer precision, Integer scale, String nullable) {
        Column c = new Column(); c.setTableName(table); c.setEngine("InnoDB"); c.setColumnName(name);
        c.setDataType(type); c.setNumericPrecision(precision); c.setNumericScale(scale); c.setIsNullable(nullable); return c;
    }
    static void key(List<IndexPart> out, String table, String name, String... cols) {
        for (int i = 0; i < cols.length; i++) {
            IndexPart p = new IndexPart(); p.setTableName(table); p.setIndexName(name); p.setColumnName(cols[i]);
            p.setNonUnique(0); p.setSeqInIndex(i + 1); p.setIndexType("BTREE"); out.add(p);
        }
    }
    static List<Column> erpColumns() {
        List<Column> out = new ArrayList<>();
        out.add(col("wm_material_receive", "receive_id", "bigint", null, null, "NO"));
        out.add(col("wm_material_receive_line", "line_id", "bigint", null, null, "NO"));
        out.add(col("wm_material_receive_line", "quantity_received", "decimal", 18, 4, "NO"));
        out.add(col("bd_material", "material_id", "bigint", null, null, "NO"));
        out.add(col("bd_unit_of_measure", "uom_id", "bigint", null, null, "NO"));
        for (String name : List.of("evidence_id", "inspection_id", "receive_id", "material_id", "base_uom_id"))
            out.add(col("wm_certified_iqc_evidence", name, "bigint", null, null, "NO"));
        out.add(col("wm_certified_iqc_evidence", "tenant_id", "varchar", null, null, "NO"));
        out.add(col("wm_certified_iqc_evidence", "first_message_id", "varchar", null, null, "NO"));
        out.add(col("wm_certified_iqc_evidence", "inspected_quantity", "decimal", 14, 4, "NO"));
        return out;
    }
    static List<IndexPart> erpIndexes() {
        List<IndexPart> out = new ArrayList<>();
        key(out, "wm_material_receive", "PRIMARY", "receive_id");
        key(out, "wm_material_receive_line", "PRIMARY", "line_id");
        key(out, "bd_material", "PRIMARY", "material_id"); key(out, "bd_unit_of_measure", "PRIMARY", "uom_id");
        key(out, "wm_certified_iqc_evidence", "PRIMARY", "evidence_id");
        key(out, "wm_certified_iqc_evidence", "inspection_unique", "tenant_id", "inspection_id");
        key(out, "wm_certified_iqc_evidence", "scope_unique", "tenant_id", "receive_id", "material_id");
        key(out, "wm_certified_iqc_evidence", "message_unique", "tenant_id", "first_message_id");
        return out;
    }
    static List<Column> isolationColumns() {
        List<Column> out = new ArrayList<>();
        for (String[] id : new String[][] {{"wm_material_receive", "receive_id"}, {"wm_material_receive_line", "line_id"},
            {"wm_batch", "batch_id"}, {"wm_material_stock", "stock_id"}, {"wm_arrival_notice_line", "line_id"},
            {"wm_material_receive_detail", "detail_id"}, {"wm_material_stock_tx", "tx_id"}, {"wm_warehouse", "warehouse_id"},
            {"wm_storage_area", "area_id"}, {"wm_storage_location", "location_id"}}) {
            out.add(col(id[0], id[1], "bigint", null, null, "NO"));
        }
        return out;
    }
    static List<IndexPart> isolationIndexes() {
        List<IndexPart> out = new ArrayList<>();
        for (String[] id : new String[][] {{"wm_material_receive", "receive_id"}, {"wm_material_receive_line", "line_id"},
            {"wm_batch", "batch_id"}, {"wm_material_stock", "stock_id"}, {"wm_arrival_notice_line", "line_id"},
            {"wm_material_receive_detail", "detail_id"}, {"wm_material_stock_tx", "tx_id"}, {"wm_warehouse", "warehouse_id"},
            {"wm_storage_area", "area_id"}, {"wm_storage_location", "location_id"}}) {
            key(out, id[0], "PRIMARY", id[1]);
        }
        return out;
    }
    static Column field(List<Column> rows, String table, String col) {
        return rows.stream().filter(r -> table.equals(r.getTableName()) && col.equals(r.getColumnName())).findFirst().orElseThrow();
    }
    @Test void exactNativeEngineDecimalAndPermanentFullUniqueDefinitionsAreAcceptedAsSchemaOnly() {
        assertThatCode(() -> IqcNativeSchema.requireErpDefinition(erpColumns(), erpIndexes())).doesNotThrowAnyException();
    }
    @Test void floatColumnCannotBeCertifiedMerelyBecauseJdbcReturnsBigDecimalScale4() {
        var c = erpColumns(); field(c, "wm_material_receive_line", "quantity_received").setDataType("float");
        assertThatThrownBy(() -> IqcNativeSchema.requireErpDefinition(c, erpIndexes())).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
    }
    @Test void evidenceDecimal18DoesNotReplaceActualNativeInspectionDecimal14() {
        var c = erpColumns(); field(c, "wm_certified_iqc_evidence", "inspected_quantity").setNumericPrecision(18);
        assertThatThrownBy(() -> IqcNativeSchema.requireErpDefinition(c, erpIndexes())).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
    }
    @Test void sourceDecimalScale3CannotBeRoundedIntoExpectedScale4() {
        var c = erpColumns(); field(c, "wm_material_receive_line", "quantity_received").setNumericScale(3);
        assertThatThrownBy(() -> IqcNativeSchema.requireErpDefinition(c, erpIndexes())).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
    }
    @Test void myisamEvidenceCannotSupplyRollbackOrPermanentOwnedTransactionAssumptions() {
        var c = erpColumns(); field(c, "wm_certified_iqc_evidence", "evidence_id").setEngine("MyISAM");
        assertThatThrownBy(() -> IqcNativeSchema.requireErpDefinition(c, erpIndexes())).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
    }
    @Test void missingPermanentScopeUniqueCannotBeReplacedByCurrentlyOneRow() {
        var keys = erpIndexes(); keys.removeIf(k -> "scope_unique".equals(k.getIndexName()));
        assertThatThrownBy(() -> IqcNativeSchema.requireErpDefinition(erpColumns(), keys)).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
    }
    @Test void nonUniqueIndexDoesNotProvePermanentInspectionUniqueness() {
        var keys = erpIndexes(); keys.stream().filter(k -> "inspection_unique".equals(k.getIndexName())).forEach(k -> k.setNonUnique(1));
        assertThatThrownBy(() -> IqcNativeSchema.requireErpDefinition(erpColumns(), keys)).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
    }
    @Test void prefixTenantIndexDoesNotProveFullIdentityUniqueness() {
        var keys = erpIndexes(); keys.stream().filter(k -> "scope_unique".equals(k.getIndexName()) && "tenant_id".equals(k.getColumnName()))
            .forEach(k -> k.setSubPart(6));
        assertThatThrownBy(() -> IqcNativeSchema.requireErpDefinition(erpColumns(), keys)).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
    }
    @Test void largerUniqueWithDelFlagIsNotImmutableReceiptMaterialUnique() {
        var keys = erpIndexes(); key(keys, "wm_certified_iqc_evidence", "scope_unique", "del_flag");
        assertThatThrownBy(() -> IqcNativeSchema.requireErpDefinition(erpColumns(), keys)).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
    }
    @Test void nullableIdentityColumnsCannotSupplyPermanentIqcEvidenceUnique() {
        var c = erpColumns(); field(c, "wm_certified_iqc_evidence", "material_id").setIsNullable("YES");
        assertThatThrownBy(() -> IqcNativeSchema.requireErpDefinition(c, erpIndexes())).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
    }
    @Test void missingNativeCatalogObservationsFailClosed() {
        assertThatThrownBy(() -> IqcNativeSchema.requireErpDefinition(List.of(), List.of())).hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
    }

    @Test void lockedServiceReadsActualCatalogAndRefusesFloatBeforeBusinessEvidenceRead() {
        var f = new WmReceiptIqcEvidenceServiceTest(); f.arrange("QUALIFIED"); var columns = erpColumns();
        field(columns, "wm_material_receive_line", "quantity_received").setDataType("double");
        doReturn(columns).when(f.mapper).selectNativeColumns();
        try (var tenant = f.tenant()) {
            assertThatThrownBy(() -> f.service.requireLocked("000007", 101L, 201L, 301L, new java.math.BigDecimal("12")))
                .hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
            verify(f.mapper).selectNativeColumns(); verify(f.mapper).selectNativeIndexes();
            verify(f.mapper, never()).selectReceiptMaterialForUpdate(anyString(), anyLong(), anyLong());
        }
    }
    @Test void lockedServiceRefusesMissingPermanentUniqueBeforeLegacyEvidenceOrUnitGuessing() {
        var f = new WmReceiptIqcEvidenceServiceTest(); f.arrange("QUALIFIED"); var keys = erpIndexes();
        keys.removeIf(k -> "scope_unique".equals(k.getIndexName())); doReturn(keys).when(f.mapper).selectNativeIndexes();
        try (var tenant = f.tenant()) {
            assertThatThrownBy(() -> f.service.requireLocked("000007", 101L, 201L, 301L, new java.math.BigDecimal("12")))
                .hasMessage("IQC_NATIVE_SCHEMA_UNSUPPORTED");
            verify(f.mapper, never()).selectMaterialForUpdate(anyString(), anyLong());
        }
    }
}

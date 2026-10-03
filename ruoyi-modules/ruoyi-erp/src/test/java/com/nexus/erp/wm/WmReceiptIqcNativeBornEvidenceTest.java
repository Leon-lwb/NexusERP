package com.nexus.erp.wm;

import com.nexus.common.mq.iqc.*;
import com.nexus.common.mq.iqc.ReceiptIqcBirthPort.Birth;
import com.nexus.erp.wm.mapper.WmReceiptIqcBirthMapper;
import com.nexus.erp.wm.mapper.WmReceiptIqcBirthMapper.BirthRow;
import com.nexus.erp.wm.service.WmReceiptIqcEvidenceService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 正向放行必须重读实际ERP登记；数量相等、消息结果或临时DTO不能补造历史单位。 */
class WmReceiptIqcNativeBornEvidenceTest {
    final WmReceiptIqcEvidenceServiceTest f = new WmReceiptIqcEvidenceServiceTest();
    final WmReceiptIqcBirthMapper births = mock(WmReceiptIqcBirthMapper.class);
    final WmReceiptIqcEvidenceService service = new WmReceiptIqcEvidenceService(f.mapper,f.source,f.transactions,f.qualification,births);
    Birth born;
    void arrange() {
        f.arrange("QUALIFIED");
        try(var tenant=f.tenant()) {
            var scope=(WmReceiptIqcEvidenceService.Scope)f.readCurrentScope();
            Birth raw=new Birth(f.TENANT,71L,101L,201L,301L,4,new BigDecimal("12.0000"),scope.sha256(),null,
                f.now-95000,"RECV-101","MAT-201","原生来源物料",901L,"原生来源供应商");
            born=new Birth(raw.tenantId(),raw.birthId(),raw.receiveId(),raw.materialId(),raw.baseUomId(),raw.quantityScale(),
                raw.quantity(),raw.receiptScopeSha256(),ReceiptIqcBirthSupport.birthHash(raw),raw.capturedAt(),raw.receiveCode(),
                raw.materialCode(),raw.materialName(),raw.vendorId(),raw.vendorName());
            var actual=f.observation("QUALIFIED",new BigDecimal("12.0000"),"a".repeat(64));
            long bound=f.now-90000;
            when(f.source.readCompletedBorn(f.TENANT,501L)).thenReturn(new ReceiptIqcSource.BornInspection(actual,born,bound,
                ReceiptIqcBirthSupport.bindingHash(501L,born,bound)));
            when(births.lockId(f.TENANT,71L)).thenReturn(row(born));
            when(births.columns()).thenReturn(columns());when(births.indexes()).thenReturn(indexes());
        }
    }
    @Test void realNativeBirthPersistsV2AndReplaysWithoutSecondEvidenceWrite() {
        arrange();var projected=new AtomicBoolean();
        try(var tenant=f.tenant()) {
            service.consumeCompleted(f.message(), x -> projected.set(true));
            assertTrue(projected.get());assertNotNull(f.stored);
            assertEquals("MES_QC_IQC_RECEIVE_MATERIAL_V2",f.stored.getSourceKind());
            service.consumeCompleted(f.message(), x -> {});
            verify(f.mapper,times(1)).insertEvidence(any());
            assertDoesNotThrow(() -> service.requireLockedEvidence(f.TENANT,101L,201L,301L,new BigDecimal("12")));
            verify(births,atLeastOnce()).lockId(f.TENANT,71L);
        }
    }
    @Test void fullNativeInspectionBodyWithDetailsMustNotBeComparedToIdentityOnlyDigest() {
        arrange();
        var full=f.observation("QUALIFIED",new BigDecimal("12.0000"),"b".repeat(64));
        long bound=f.now-90000;
        when(f.source.readCompletedBorn(f.TENANT,501L)).thenReturn(new ReceiptIqcSource.BornInspection(full,born,bound,
            ReceiptIqcBirthSupport.bindingHash(501L,born,bound)));
        try(var tenant=f.tenant()) {
            assertDoesNotThrow(()->service.consumeCompleted(f.message(),x->{}));
            assertEquals("b".repeat(64),f.stored.getActualBodySha256());
            assertEquals("QC_IQC_CURRENT_V1:"+"b".repeat(64),f.stored.getSourceVersion());
        }
    }
    @Test void originalUnitDimensionDriftCannotKeepSameBornAuthority() {
        arrange();f.mapper.selectUnitForUpdate(f.TENANT,301L).setCategory("LENGTH");
        try(var tenant=f.tenant()) {
            var failure=assertThrows(RuntimeException.class,()->service.consumeCompleted(f.message(),x->fail("不得投影")));
            assertEquals("IQC_BIRTH_SOURCE_CHANGED",failure.getMessage());verify(f.mapper,never()).insertEvidence(any());
        }
    }
    @Test void originalUnitVersionDriftCannotBeAcceptedAsSameBornRange() {
        arrange();f.mapper.selectUnitForUpdate(f.TENANT,301L).setConversionVersion("root-v2");
        try(var tenant=f.tenant()) {
            var failure=assertThrows(RuntimeException.class,()->service.consumeCompleted(f.message(),x->fail("不得投影")));
            assertEquals("IQC_BIRTH_SOURCE_CHANGED",failure.getMessage());verify(f.mapper,never()).insertEvidence(any());
        }
    }
    @Test void missingVerifiedOriginalUnitRefusesPositiveEvidenceBeforeAnyInsert() {
        arrange();f.mapper.selectUnitForUpdate(f.TENANT,301L).setConversionVerifiedAt(null);
        try(var tenant=f.tenant()) {
            var failure=assertThrows(RuntimeException.class,()->service.consumeCompleted(f.message(),x->fail("不得投影")));
            assertEquals("IQC_NATIVE_UNIT_UNKNOWN",failure.getMessage());verify(f.mapper,never()).insertEvidence(any());
        }
    }
    @Test void typedBornDtoWithoutActualErpRegistrationNeverGrantsPositiveEvidence() {
        arrange();when(births.lockId(f.TENANT,71L)).thenReturn(null);
        try(var tenant=f.tenant()) {
            var failure=assertThrows(RuntimeException.class, () -> service.consumeCompleted(f.message(), x -> fail("不得投影")));
            assertEquals("IQC_BIRTH_REGISTRY_MISSING",failure.getMessage());verify(f.mapper,never()).insertEvidence(any());
        }
    }
    @Test void changedReceiptRangeDoesNotReuseOriginalUnitBinding() {
        arrange();f.mapper.selectMaterialLinesForUpdate(f.TENANT,101L,201L).get(0).setQuantityReceived(new BigDecimal("4"));
        try(var tenant=f.tenant()) {
            var failure=assertThrows(RuntimeException.class, () -> service.consumeCompleted(f.message(), x -> fail("不得投影")));
            assertEquals("IQC_BIRTH_SOURCE_CHANGED",failure.getMessage());verify(f.mapper,never()).insertEvidence(any());
        }
    }
    @Test void legacyCompletedInspectionStillCannotAcquireBirthFromSameNumericTotal() {
        f.arrange("QUALIFIED");
        try(var tenant=f.tenant()) {
            var failure=assertThrows(RuntimeException.class, () -> service.consumeCompleted(f.message(), x -> fail("不得投影")));
            assertEquals("UNIT_ORIGIN_UNPROVEN",failure.getMessage());verify(f.mapper,never()).insertEvidence(any());
        }
    }
    static BirthRow row(Birth b) {
        var r=new BirthRow();r.setTenantId(b.tenantId());r.setBirthId(b.birthId());r.setReceiveId(b.receiveId());
        r.setMaterialId(b.materialId());r.setBaseUomId(b.baseUomId());r.setQuantityScale(b.quantityScale());r.setQuantity(b.quantity());
        r.setReceiptScopeSha256(b.receiptScopeSha256());r.setBirthSha256(b.birthSha256());r.setCapturedAt(b.capturedAt());
        r.setReceiveCode(b.receiveCode());r.setMaterialCode(b.materialCode());r.setMaterialName(b.materialName());
        r.setVendorId(b.vendorId());r.setVendorName(b.vendorName());return r;
    }
    static List<IqcNativeSchema.Column> columns() {
        var result=new ArrayList<IqcNativeSchema.Column>();
        for(String key:List.of("birth_id","receive_id","material_id","base_uom_id","vendor_id","captured_at")) result.add(column(key,"bigint"));
        result.add(column("tenant_id","varchar"));result.add(column("quantity_scale","tinyint"));
        result.add(column("receipt_scope_sha256","char"));result.add(column("birth_sha256","char"));
        var qty=column("quantity","decimal");qty.setNumericPrecision(14);qty.setNumericScale(4);result.add(qty);return result;
    }
    static IqcNativeSchema.Column column(String key,String type) {
        var c=new IqcNativeSchema.Column();c.setTableName("wm_receipt_iqc_birth");c.setColumnName(key);c.setDataType(type);
        c.setEngine("InnoDB");c.setIsNullable("NO");return c;
    }
    static List<IqcNativeSchema.IndexPart> indexes() {
        var rows=new ArrayList<IqcNativeSchema.IndexPart>();rows.add(index("PRIMARY",1,"birth_id"));
        int pos=0;for(String key:List.of("tenant_id","receive_id","material_id")) rows.add(index("uk_scope",++pos,key));return rows;
    }
    static IqcNativeSchema.IndexPart index(String name,int position,String key) {
        var i=new IqcNativeSchema.IndexPart();i.setTableName("wm_receipt_iqc_birth");i.setIndexName(name);i.setColumnName(key);
        i.setSeqInIndex(position);i.setNonUnique(0);i.setIndexType("BTREE");return i;
    }
}

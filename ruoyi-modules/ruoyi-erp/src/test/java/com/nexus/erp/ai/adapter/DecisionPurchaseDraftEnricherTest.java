package com.nexus.erp.ai.adapter;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@Tag("dev") class DecisionPurchaseDraftEnricherTest {
    private final BdMaterialMapper materials=mock(BdMaterialMapper.class);
    private final BdUnitOfMeasureMapper units=mock(BdUnitOfMeasureMapper.class);
    private final DecisionOperatorProfileResolver profiles=mock(DecisionOperatorProfileResolver.class);
    private final DecisionActor actor=new DecisionActor("000000",1L,"admin",Set.of());
    private final DecisionPurchaseDraftEnricher enricher=new DecisionPurchaseDraftEnricher(materials,units,profiles);
    private BdMaterial material() {var m=new BdMaterial();m.setMaterialId(101L);m.setTenantId("000000");m.setMaterialCode("M-101");m.setMaterialName("组件");m.setMaterialSpec("SPEC");m.setStatus(1L);m.setBaseUomId(21L);m.setPurchaseUomId(22L);return m;}
    private PuPurchaseRequisitionBo draft() {var b=new PuPurchaseRequisitionBo();var l=new PuPurchaseRequisitionLine();l.setMaterialId(101L);l.setApplyQuantity(new BigDecimal("19"));l.setShortageQuantity(new BigDecimal("19"));b.setPuPurchaseRequisitionLineList(List.of(l));return b;}
    private void profile() {when(profiles.resolve(actor)).thenReturn(new DecisionOperatorProfileResolver.OperatorProfile(1L,"admin",103L,"研发部门"));}
    @Test void fillsTrustedSnapshotsAuditAndBaseUnitWithoutQuantityConversionOrApproval() {
        profile();when(materials.selectById(101L)).thenReturn(material());
        var u=new BdUnitOfMeasure();u.setUomId(21L);u.setTenantId("000000");u.setStatus(1L);u.setUomName("件");when(units.selectById(21L)).thenReturn(u);
        var b=draft();enricher.enrich(b,actor);var l=b.getPuPurchaseRequisitionLineList().get(0);
        assertEquals(103L,b.getDeptId());assertEquals("研发部门",b.getDeptName());assertEquals(103L,b.getCreateDept());assertEquals(1L,b.getCreateBy());
        assertEquals("M-101",l.getMaterialCode());assertEquals("组件",l.getMaterialName());assertEquals("SPEC",l.getMaterialSpec());assertEquals("件",l.getUnit());
        assertEquals("000000",l.getTenantId());assertEquals(1L,l.getCreateBy());assertEquals(1L,l.getUpdateBy());assertEquals(103L,l.getCreateDept());
        assertEquals(new BigDecimal("19"),l.getApplyQuantity());assertNull(l.getApprovedQuantity());assertNull(b.getSelectFlowId());verify(units,never()).selectById(22L);
    }
    @Test void trulyMissingSpecificationAndBaseUnitAreNotInventedAndHaveAnExplicitNote() {
        profile();var m=material();m.setMaterialSpec(null);m.setBaseUomId(null);when(materials.selectById(101L)).thenReturn(m);
        var b=draft();enricher.enrich(b,actor);var l=b.getPuPurchaseRequisitionLineList().get(0);
        assertNull(l.getMaterialSpec());assertNull(l.getUnit());assertTrue(l.getRemark().contains("未维护"));verifyNoInteractions(units);
    }
    @Test void crossTenantDisabledMissingMaterialOrConflictingFrozenCodeIsRejected() {
        profile();var m=material();when(materials.selectById(101L)).thenReturn(m);
        m.setTenantId("910000");assertThrows(ServiceException.class,()->enricher.enrich(draft(),actor));
        m.setTenantId("000000");m.setStatus(0L);assertThrows(ServiceException.class,()->enricher.enrich(draft(),actor));
        m.setStatus(1L);var b=draft();b.getPuPurchaseRequisitionLineList().get(0).setMaterialCode("FORGED");assertThrows(ServiceException.class,()->enricher.enrich(b,actor));
        when(materials.selectById(101L)).thenReturn(null);assertThrows(ServiceException.class,()->enricher.enrich(draft(),actor));
    }
    @Test void aConfiguredButCrossTenantOrMissingUnitDoesNotBecomeAnUnverifiedUnitLabel() {
        profile();when(materials.selectById(101L)).thenReturn(material());
        when(units.selectById(21L)).thenReturn(null);assertThrows(ServiceException.class,()->enricher.enrich(draft(),actor));
        var u=new BdUnitOfMeasure();u.setTenantId("910000");u.setStatus(1L);u.setUomName("件");when(units.selectById(21L)).thenReturn(u);
        assertThrows(ServiceException.class,()->enricher.enrich(draft(),actor));
    }
    @Test void supplementalVerificationRejectsTamperedAuditOrMaterialSnapshot() {
        profile();var m=material();m.setBaseUomId(null);when(materials.selectById(101L)).thenReturn(m);
        var b=draft();enricher.enrich(b,actor);
        var v=new com.nexus.erp.pu.domain.vo.PuPurchaseRequisitionVo();
        v.setDeptId(b.getDeptId());v.setDeptName(b.getDeptName());v.setCreateDept(b.getCreateDept());v.setCreateBy(b.getCreateBy());
        v.setPuPurchaseRequisitionLineList(b.getPuPurchaseRequisitionLineList());
        assertTrue(enricher.matchesPersisted(v,actor));
        var l=v.getPuPurchaseRequisitionLineList().get(0);l.setCreateBy(-1L);assertFalse(enricher.matchesPersisted(v,actor));
        l.setCreateBy(1L);l.setMaterialCode("FORGED");assertFalse(enricher.matchesPersisted(v,actor));
        l.setMaterialCode("M-101");l.setUnit("箱");assertFalse(enricher.matchesPersisted(v,actor));
    }

}

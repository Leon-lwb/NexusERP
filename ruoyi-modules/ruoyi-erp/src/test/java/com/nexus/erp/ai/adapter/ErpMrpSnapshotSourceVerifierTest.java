package com.nexus.erp.ai.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class ErpMrpSnapshotSourceVerifierTest {

    private static final String TENANT = "000000";
    private static final LocalDate DAY = LocalDate.of(2026, 9, 30);

    @Mock
    private PrdMaterialRequirementDetailMapper detailMapper;
    @Mock
    private PrdMaterialRequirementMapper requirementMapper;
    @Mock
    private PrdWorkOrderMapper workOrderMapper;
    @Mock
    private BdMaterialMapper materialMapper;
    @Mock
    private BdUnitOfMeasureMapper uomMapper;

    private ErpMrpSnapshotSourceVerifier verifier;
    private PrdMaterialRequirementDetail first;
    private PrdMaterialRequirementDetail second;
    private PrdMaterialRequirement header;
    private PrdWorkOrder workOrder;
    private BdMaterial material;
    private BdUnitOfMeasure uom;
    private List<PrdMaterialRequirementDetail> dbDetails;

    @BeforeAll
    static void initMybatis() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(PrdMaterialRequirementDetail.class, PrdMaterialRequirement.class,
            PrdWorkOrder.class, BdMaterial.class, BdUnitOfMeasure.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @BeforeEach
    void setUp() {
        verifier = new ErpMrpSnapshotSourceVerifier(detailMapper, requirementMapper,
            workOrderMapper, materialMapper, uomMapper);
        first = detail(901L, "27", "10", "2");
        second = detail(902L, "27", "5", "2");
        dbDetails = List.of(first);
        header = new PrdMaterialRequirement();
        header.setRequirementId(301L);
        header.setPlanId(201L);
        header.setTenantId(TENANT);
        workOrder = new PrdWorkOrder();
        workOrder.setWoId(11L);
        workOrder.setPlanId(201L);
        workOrder.setTenantId(TENANT);
        workOrder.setSourceType("MATERIAL_REQUIREMENT");
        workOrder.setSourceId(301L);
        material = new BdMaterial();
        material.setMaterialId(501L);
        material.setTenantId(TENANT);
        material.setBaseUomId(27L);
        material.setStatus(1L);
        material.setDelFlag("0");
        uom = new BdUnitOfMeasure();
        uom.setUomId(27L);
        uom.setTenantId(TENANT);
        uom.setStatus(1L);
        uom.setDelFlag("0");
        lenient().when(detailMapper.selectList(any())).thenAnswer(inv -> dbDetails);
        lenient().when(requirementMapper.selectBatchIds(anyCollection())).thenAnswer(inv -> List.of(header));
        lenient().when(workOrderMapper.selectBatchIds(anyCollection())).thenAnswer(inv -> List.of(workOrder));
        lenient().when(materialMapper.selectBatchIds(anyCollection())).thenAnswer(inv -> List.of(material));
        lenient().when(uomMapper.selectBatchIds(anyCollection())).thenAnswer(inv -> List.of(uom));
    }

    @Test
    void twoDistinctDetailIdsForSameWorkOrderMaterialAndDateStaySeparate() {
        dbDetails = List.of(first, second);
        List<ErpMrpSnapshotSourceVerifier.VerifiedDetail> rows = verifier.verify(
            snapshot(fact(901L, "8"), fact(902L, "3")), actor());
        assertEquals(List.of(901L, 902L), rows.stream().map(ErpMrpSnapshotSourceVerifier.VerifiedDetail::detailId).toList());
        assertEquals(List.of(new BigDecimal("8"), new BigDecimal("3")),
            rows.stream().map(ErpMrpSnapshotSourceVerifier.VerifiedDetail::unissuedDemandBase).toList());
        assertTrue(rows.stream().allMatch(row -> row.baseUomId() == 27L && row.workOrderId() == 11L));
    }

    @Test
    void unitMustBeCanonicalDecimalIdOfMaterialBaseUom() {
        assertTrustedBaseline();
        for (String invalid : new String[] {null, "", "kg", "28", "027", "9223372036854775808"}) {
            first.setUnit(invalid);
            assertThrows(ServiceException.class, () -> verifier.verify(snapshot(fact(901L, "8")), actor()),
                "unit=" + invalid);
        }
    }

    @Test
    void missingInactiveDeletedOrForeignTenantUnitFailsClosed() {
        assertTrustedBaseline();
        uom.setStatus(0L);
        assertRejected();
        uom.setStatus(1L);
        uom.setDelFlag("1");
        assertRejected();
        uom.setDelFlag("0");
        uom.setTenantId("other");
        assertRejected();
        uom.setTenantId(TENANT);
        uom.setUomId(28L);
        assertRejected();
    }

    @Test
    void missingInactiveDeletedOrForeignTenantMaterialFailsClosed() {
        assertTrustedBaseline();
        material.setBaseUomId(null);
        assertRejected();
        material.setBaseUomId(27L);
        material.setStatus(0L);
        assertRejected();
        material.setStatus(1L);
        material.setDelFlag("1");
        assertRejected();
        material.setDelFlag("0");
        material.setTenantId("other");
        assertRejected();
        material.setTenantId(TENANT);
        material.setMaterialId(502L);
        assertRejected();
    }

    @Test
    void missingOrWrongQuantitiesNeverBecomeZeroOrVerified() {
        assertTrustedBaseline();
        first.setIssuedQuantity(null);
        assertRejected();
        first.setIssuedQuantity(new BigDecimal("2"));
        first.setRequiredQuantity(null);
        assertRejected();
        first.setRequiredQuantity(new BigDecimal("10"));
        first.setIssuedQuantity(new BigDecimal("11"));
        assertRejected();
        first.setIssuedQuantity(new BigDecimal("2"));
        assertThrows(ServiceException.class, () -> verifier.verify(snapshot(fact(901L, "0")), actor()));
    }

    @Test
    void missingAndDuplicatedDetailIdsAreRejectedBeforeAnyVerifiedResult() {
        assertTrustedBaseline();
        assertThrows(ServiceException.class, () -> verifier.verify(snapshot(fact(null, "8")), actor()));
        assertThrows(ServiceException.class, () -> verifier.verify(snapshot(fact(901L, "8"), fact(901L, "8")), actor()));
        assertThrows(ServiceException.class, () -> verifier.verify(snapshot(fact(999L, "8")), actor()));
        dbDetails = List.of(first, first);
        assertRejected();
    }

    @Test
    void foreignTenantOrForgedDetailAndOwnershipFailsClosed() {
        assertTrustedBaseline();
        first.setTenantId("other");
        assertRejected();
        first.setTenantId(TENANT);
        first.setMaterialId(502L);
        assertRejected();
        first.setMaterialId(501L);
        first.setRequiredDate(Date.valueOf(DAY.plusDays(1)));
        assertRejected();
        first.setRequiredDate(Date.valueOf(DAY));
        header.setTenantId("other");
        assertRejected();
        header.setTenantId(TENANT);
        workOrder.setTenantId("other");
        assertRejected();
        workOrder.setTenantId(TENANT);
        workOrder.setSourceId(302L);
        assertRejected();
    }

    @Test
    void detailReadExplicitlyExcludesDeletedRowsAndOtherTenants() {
        verifier.verify(snapshot(fact(901L, "8")), actor());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PrdMaterialRequirementDetail>> captor =
                ArgumentCaptor.forClass(Wrapper.class);
        Mockito.verify(detailMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("detail_id") && sql.contains("tenant_id") && sql.contains("del_flag"), sql);
        assertTrue(
                ((AbstractWrapper<?, ?, ?>) captor.getValue())
                        .getParamNameValuePairs()
                        .containsValue(TENANT));
    }

    @Test
    void emptySnapshotCannotClaimVerifiedMrpSources() {
        assertThrows(ServiceException.class, () -> verifier.verify(snapshot(), actor()));
    }

    private void assertTrustedBaseline() {
        assertEquals(1, verifier.verify(snapshot(fact(901L, "8")), actor()).size());
    }

    private void assertRejected() {
        assertThrows(ServiceException.class, () -> verifier.verify(snapshot(fact(901L, "8")), actor()));
    }

    private DecisionActor actor() {
        return new DecisionActor(TENANT, 7L, "operator", Set.of("ai:decision:run"));
    }

    private PrdMaterialRequirementDetail detail(long id, String unit, String required, String issued) {
        PrdMaterialRequirementDetail detail = new PrdMaterialRequirementDetail();
        detail.setDetailId(id);
        detail.setTenantId(TENANT);
        detail.setRequirementId(301L);
        detail.setMaterialId(501L);
        detail.setRequiredDate(Date.valueOf(DAY));
        detail.setRequiredQuantity(new BigDecimal(required));
        detail.setIssuedQuantity(new BigDecimal(issued));
        detail.setUnit(unit);
        return detail;
    }

    private DeliveryContextSnapshot.RequirementFact fact(Long id, String net) {
        return new DeliveryContextSnapshot.RequirementFact(id, 501L, "RM-501", "物料", DAY,
            new BigDecimal(net), 11L, "WO-11");
    }

    private DeliveryContextSnapshot snapshot(DeliveryContextSnapshot.RequirementFact... facts) {
        return new DeliveryContextSnapshot(
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, null), List.of(),
            List.of(new DeliveryContextSnapshot.WorkOrderFact(11L, "WO-11", 888L,
                BigDecimal.ONE, DAY, 1L, 3L)), Map.of(), List.of(facts),
            List.of(), List.of(), Map.of(), List.of(), Map.of(), List.of(),
            "hash", LocalDateTime.of(2026, 9, 30, 12, 0), List.of());
    }
}

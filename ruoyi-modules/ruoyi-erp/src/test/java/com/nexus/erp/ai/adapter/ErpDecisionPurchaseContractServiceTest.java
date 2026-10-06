package com.nexus.erp.ai.adapter;

import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.RequirementFact;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedPurchaseScope;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedLine;
import com.nexus.erp.pu.service.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 合成完整认证结果只验证接线；数据库、条款及物理RR仍须主线程另验。 */
class ErpDecisionPurchaseContractServiceTest {
    private static final String TENANT="000000", HASH="a".repeat(64);
    private static final LocalDate DAY=LocalDate.of(2026,10,6);
    private static final DecisionBusinessRef ROOT=new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER,11L,null);
    private static final DecisionActor ACTOR=new DecisionActor(TENANT,7L,"合成分析员",Set.of("ai:decision:run","prd:work:query"));
    private PuRequirementErpTransactionExecutor executor;
    private PuRequirementErpTransactionQualification qualification;
    private PuRequirementMaterialLockService locks;
    private ErpPurchaseSourceCertificationService certification;
    private ErpDecisionPurchaseScopeGuard scope;
    private ErpDecisionShortageSimulation shortage;
    private ErpDecisionPurchaseContractService service;
    private MockedStatic<TenantHelper> tenants;
    @BeforeEach void setup() {
        tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);
        tenants.when(TenantHelper::getTenantId).thenReturn(TENANT);
        executor=mock(PuRequirementErpTransactionExecutor.class);qualification=mock(PuRequirementErpTransactionQualification.class);
        locks=mock(PuRequirementMaterialLockService.class);certification=mock(ErpPurchaseSourceCertificationService.class);
        scope=mock(ErpDecisionPurchaseScopeGuard.class);shortage=mock(ErpDecisionShortageSimulation.class);
        service=new ErpDecisionPurchaseContractService(executor,qualification,locks,certification,scope,shortage);
        when(executor.executeRepeatableRead(eq(TENANT),any())).thenAnswer(i->((Supplier<?>)i.getArgument(1)).get());
        when(scope.selectVendorLocked(TENANT,139L)).thenReturn(9L);
    }
    @AfterEach void close() {tenants.close();}
    private RequirementFact demand(Long detail,Long material,BigDecimal qty) {
        return new RequirementFact(detail,material,"合成物料","合成名称",DAY,qty,11L,"仅展示",4L);
    }
    private DeliveryContextSnapshot snapshot(List<RequirementFact> details) {
        return new DeliveryContextSnapshot(ROOT,List.of(),List.of(),Map.of(),details,List.of(),List.of(),Map.of(),List.of(),
            Map.of(),List.of(),HASH,DAY.atTime(10,0),List.of());
    }
    private void risk(DeliveryContextSnapshot snapshot,Long... ids) {
        when(shortage.simulate(snapshot,7,Map.of())).thenReturn(new ShortageSimulationResult(true,ids.length,DAY,new BigDecimal("99"),
            Arrays.stream(ids).map(id->new ShortageSimulationResult.Item(id,"合成","合成",DAY,new BigDecimal("99"),BigDecimal.ZERO,
                List.of(11L),List.of(),"PROCURE",List.of())).toList(),null,List.of(),List.of()));
    }
    private CertifiedLine line(Long id,String qty) {return new CertifiedLine(id,DAY,new BigDecimal(qty),new BigDecimal(qty),4L,4L);}
    private CertifiedPurchaseScope certified(Long material,Long vendor,List<CertifiedLine> lines,String fingerprint) {
        var at=DAY.atStartOfDay().toInstant(ZoneOffset.ofHours(8));
        var unit=new ErpPurchaseQuantityPolicy.TrustedUnit(TENANT,"4","4","MASS",BigDecimal.ONE,"单位版本",4,at,true);
        var terms=new ErpPurchaseQuantityPolicy.SupplierTerms(TENANT,material.toString(),"700","4","4",new BigDecimal("0.0001"),
            new BigDecimal("0.0001"),true,BigDecimal.ONE,"条款版本",at.minusSeconds(1),at.plusSeconds(31536000),at,true);
        var catalog=new ErpPurchaseCatalogSourceReader.CatalogSnapshot(TENANT,vendor.toString(),"700",at,"+08:00",
            new ErpPurchaseQuantityPolicy.Material(TENANT,material.toString(),"4",true),terms,unit,unit,unit,
            Map.of("material:"+material,"物料内容版本","vendor:"+vendor,"供应商内容版本"));
        return new CertifiedPurchaseScope(TENANT,material,vendor,DAY,DAY.plusDays(6),catalog,null,lines,
            Map.of("catalog:material:"+material,"不能误取的前缀版本"),fingerprint);
    }
    private DecisionActionProposal action(DecisionPurchaseContractV2 contract) {
        return new DecisionActionProposal(200L,DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,"合成采购","合成理由",ROOT,
            DecisionPurchaseContractCodec.toPayload(100L,contract),DecisionRiskLevel.HIGH,true,"pu:requisition:add",
            DecisionPurchaseContractCodec.idempotencyKey(TENANT,100L,ROOT,contract),"purchaseRequisitionDraft",0,List.of());
    }
    @Test void freezesOnlyOwnedPositiveMrpLinesUsingCertifiedQuantityNotRiskGap() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN)));risk(saved,139L);
        when(certification.previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L))).thenReturn(certified(139L,9L,List.of(line(21L,"3.25")),HASH));
        var contracts=service.freeze(saved,7,ACTOR);
        assertEquals(1,contracts.size(),"正认证额度才生成合同");
        var contract=contracts.get(0);assertEquals(new BigDecimal("3.25"),contract.frozenLines().get(0).requestedOrderQty(),"绝不能把风险gap99当采购量");
        assertEquals("物料内容版本",contract.materialVersion(),"版本须取目录原键，不取scope带前缀的版本map");
        assertEquals(DAY.plusDays(6),contract.windowEnd(),"窗口末日必须来自原快照业务日");
        var order=inOrder(locks,scope,certification);
        order.verify(locks).lockExisting(TENANT,List.of(139L));
        order.verify(scope).requireRootSourcesLocked(TENANT,ROOT,139L,Set.of(21L),DAY.plusDays(6));
        order.verify(scope).selectVendorLocked(TENANT,139L);
        order.verify(scope).requireLocked(TENANT,ROOT,139L,9L,Set.of(21L),DAY.plusDays(6));
        order.verify(certification).previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L));
        verify(locks,never()).lock(any(),any());verify(certification,never()).prepareLocked(any(),any(),any(),any(),any());
    }
    @Test void recertifiesExactPositiveSubsetBeforeFingerprinting() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN),demand(22L,139L,BigDecimal.ONE)));risk(saved,139L);
        when(certification.previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L,22L))).thenReturn(certified(139L,9L,List.of(line(21L,"3.25")),HASH));
        when(certification.previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L))).thenReturn(certified(139L,9L,List.of(line(21L,"3.25")),"b".repeat(64)));
        var result=service.freeze(saved,7,ACTOR).get(0);
        assertEquals(List.of(21L),result.requirementDetailIds(),"零额度明细必须从合同的来源集合移除");
        assertEquals("b".repeat(64),result.expectedSourceFingerprint(),"指纹必须属于重新认证的确切正子集");
        verify(certification).previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L));
    }
    @Test void zeroAllocationCreatesNoPurchaseContractAndNoBusinessWrites() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN)));risk(saved,139L);
        when(certification.previewLocked(any(),any(),any(),any(),any())).thenReturn(certified(139L,9L,List.of(),HASH));
        assertTrue(service.freeze(saved,7,ACTOR).isEmpty(),"全零认证额度不产生采购动作");
        verify(certification).previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L));
        verify(locks,never()).lock(any(),any());verify(certification,never()).prepareLocked(any(),any(),any(),any(),any());
    }
    @Test void freezeLocksRootAndRequirementBeforeSharedVendorConsistentlyWithWriter() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN)));risk(saved,139L);
        when(certification.previewLocked(any(),any(),any(),any(),any())).thenReturn(certified(139L,9L,List.of(line(21L,"2")),HASH));
        service.freeze(saved,7,ACTOR);
        var calls=new ArrayList<>(mockingDetails(scope).getInvocations());
        assertFalse(calls.isEmpty(),"冻结必须调用根来源守卫");
        assertEquals("requireRootSourcesLocked",calls.get(0).getMethod().getName(),
            "冻结必须先锁工单与需求再锁共享供应商，避免与最终writer反向锁序");
    }
    @Test void threeRiskMaterialsUseThreeIndependentShortOwnedRrContractsInNumericOrder() {
        var saved=snapshot(List.of(demand(31L,141L,BigDecimal.TEN),demand(21L,139L,BigDecimal.TEN),demand(41L,140L,BigDecimal.TEN)));
        risk(saved,141L,139L,140L);
        for(Long id:List.of(139L,140L,141L)) {
            Long detail=id==139L?21L:id==140L?41L:31L;
            when(scope.selectVendorLocked(TENANT,id)).thenReturn(9L);
            when(certification.previewLocked(TENANT,id,9L,DAY.plusDays(6),Set.of(detail))).thenReturn(certified(id,9L,List.of(line(detail,"2")),HASH));
        }
        assertEquals(List.of(139L,140L,141L),service.freeze(saved,7,ACTOR).stream().map(DecisionPurchaseContractV2::materialId).toList(),
            "三物料必须生成三个单物料合同并按数值顺序处理");
        verify(executor,times(3)).executeRepeatableRead(eq(TENANT),any());
    }
    @Test void unresolvedMoqOrPackagingIsNotRoundedOrSilentlyDropped() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN)));risk(saved,139L);
        when(certification.previewLocked(any(),any(),any(),any(),any())).thenThrow(new ServiceException("MOQ与包装条款不能满足当前额度"));
        assertThrows(ServiceException.class,()->service.freeze(saved,7,ACTOR),"认证拒绝必须保留，不能伪造取整或隐藏失败");
        verify(certification,never()).prepareLocked(any(),any(),any(),any(),any());
    }
    @Test void subsetRecertificationCannotChangePositiveQuantityOrLoseAnotherLine() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN),demand(22L,139L,BigDecimal.ONE)));risk(saved,139L);
        when(certification.previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L,22L))).thenReturn(certified(139L,9L,List.of(line(21L,"3")),HASH));
        when(certification.previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L))).thenReturn(certified(139L,9L,List.of(line(21L,"4")),HASH));
        assertThrows(ServiceException.class,()->service.freeze(saved,7,ACTOR),"同RR重新认证必须保留确切逐行数量");
    }
    @Test void freezeComparesOriginalCandidateUnitAndBusinessDateAfterFullCertification() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN)));risk(saved,139L);
        var changed=new CertifiedLine(21L,DAY.plusDays(1),BigDecimal.ONE,BigDecimal.ONE,4L,4L);
        when(certification.previewLocked(any(),any(),any(),any(),any())).thenReturn(certified(139L,9L,List.of(changed),HASH));
        var error=assertThrows(RuntimeException.class,()->service.freeze(saved,7,ACTOR),"认证后原候选日期已变化不得冻结新意图");
        assertEquals("com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec$SourceChangedException",error.getClass().getName(),
            "正认证的候选差异应使用公共变化类型");
    }
    @Test void crossTenantAndMissingReadPermissionNeverStartErpRr() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN)));
        var wrong=new DecisionActor("111111",7L,"其他租户",ACTOR.permissions());
        assertThrows(ServiceException.class,()->service.freeze(saved,7,wrong),"跨租户不能开始来源事务");
        var noRead=new DecisionActor(TENANT,7L,"无业务读取权",Set.of("ai:decision:run"));
        assertThrows(ServiceException.class,()->service.freeze(saved,7,noRead),"没有原业务读取权不得借分析权限读来源");
        verifyNoInteractions(executor,certification,scope,shortage);
    }
    @Test void invalidFrozenWindowAndUnknownCandidateAreNotSilentlyDefaulted() {
        var saved=snapshot(List.of(new RequirementFact(null,139L,"合成","合成",DAY,BigDecimal.ONE,11L,"展示",4L)));risk(saved,139L);
        assertThrows(ServiceException.class,()->service.freeze(saved,7,ACTOR),"原快照缺detailId不能补造来源");
        assertThrows(ServiceException.class,()->service.freeze(saved,0,ACTOR),"窗口不能默认为当天或自动延长");
        verifyNoInteractions(executor,certification,scope,locks);
    }
    @Test void subsetRecertificationCannotCrossOriginalSnapshotBusinessDay() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN),demand(22L,139L,BigDecimal.ONE)));risk(saved,139L);
        var first=certified(139L,9L,List.of(line(21L,"3")),HASH);
        when(certification.previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L,22L))).thenReturn(first);
        var later=new CertifiedPurchaseScope(TENANT,139L,9L,DAY.plusDays(1),DAY.plusDays(6),first.catalog(),null,first.lines(),first.sourceVersions(),"b".repeat(64));
        when(certification.previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L))).thenReturn(later);
        assertThrows(ServiceException.class,()->service.freeze(saved,7,ACTOR),"正子集认证跨过原快照业务日必须安全拒绝而不是变更窗口");
    }
    @Test void thirtyDayWindowComesFromPersistedOctoberSixthSnapshotNotPreflightPreview() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN)));
        var end=LocalDate.of(2026,11,4);
        when(shortage.simulate(saved,30,Map.of())).thenReturn(new ShortageSimulationResult(true,1,DAY,BigDecimal.ONE,
            List.of(new ShortageSimulationResult.Item(139L,"合成","合成",DAY,BigDecimal.ONE,BigDecimal.ZERO,List.of(11L),List.of(),"PROCURE",List.of())),
            null,List.of(),List.of()));
        var original=certified(139L,9L,List.of(line(21L,"3")),"c".repeat(64));
        var realWindow=new CertifiedPurchaseScope(TENANT,139L,9L,DAY,end,original.catalog(),null,original.lines(),original.sourceVersions(),"c".repeat(64));
        when(certification.previewLocked(TENANT,139L,9L,end,Set.of(21L))).thenReturn(realWindow);
        var result=service.freeze(saved,30,ACTOR).get(0);
        assertEquals(end,result.windowEnd(),"持久化10月6日的30日窗口必须是11月4日");
        assertEquals("c".repeat(64),result.expectedSourceFingerprint(),"必须冻结本次实际窗口认证的摘要而非预览旧指纹");
        verify(certification,never()).previewLocked(TENANT,139L,9L,LocalDate.of(2026,11,5),Set.of(21L));
        assertDoesNotThrow(()->service.requireFresh(action(result),saved,30,ACTOR),"复核必须保持同一原冻结窗口");
        verify(certification,times(2)).previewLocked(TENANT,139L,9L,end,Set.of(21L));
    }
    @Test void certificationReadFailureIsOrdinarySafeErrorNotSourceChanged() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN)));risk(saved,139L);
        when(certification.previewLocked(any(),any(),any(),any(),any())).thenThrow(new IllegalStateException("底层连接与SQL错误"));
        var failure=assertThrows(ServiceException.class,()->service.freeze(saved,7,ACTOR),"未知认证读取故障应失败关闭");
        assertFalse(failure.getMessage().contains("SQL"),"冻结不能暴露底层来源异常内容");
    }
    @Test void nonWorkOrderRootProducesNoPurchaseAndNeverTouchesErpSources() {
        var saved=new DeliveryContextSnapshot(new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER,5L,null),List.of(),List.of(),Map.of(),
            List.of(),List.of(),List.of(),Map.of(),List.of(),Map.of(),List.of(),HASH,DAY.atTime(10,0),List.of());
        assertTrue(service.freeze(saved,7,ACTOR).isEmpty(),"首包不扩展非工单根采购");
        verifyNoInteractions(executor,certification,scope,locks);
    }
    @Test void freshContractUsesOnlyFrozenMaterialSubsetNotGlobalRiskRecomputation() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN),demand(22L,139L,BigDecimal.ONE)));risk(saved,139L);
        when(certification.previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L,22L))).thenReturn(certified(139L,9L,List.of(line(21L,"3")),HASH));
        when(certification.previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L))).thenReturn(certified(139L,9L,List.of(line(21L,"3")),"b".repeat(64)));
        var contract=service.freeze(saved,7,ACTOR).get(0);clearInvocations(executor,qualification,scope,locks,certification,shortage);
        var approver=new DecisionActor(TENANT,8L,"合成审批员",Set.of("ai:decision:approve","prd:work:query"));
        assertDoesNotThrow(()->service.requireFresh(action(contract),saved,7,approver),"审批只复读原合同物料与原窗口");
        verifyNoInteractions(shortage);
        verify(certification).previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L));
        verify(certification,never()).previewLocked(TENANT,139L,9L,DAY.plusDays(6),Set.of(21L,22L));
    }
    @Test void changedFingerprintIsDistinctFromSourceReadFailure() {
        var saved=snapshot(List.of(demand(21L,139L,BigDecimal.TEN)));risk(saved,139L);
        when(certification.previewLocked(any(),any(),any(),any(),any())).thenReturn(certified(139L,9L,List.of(line(21L,"3")),HASH));
        var contract=service.freeze(saved,7,ACTOR).get(0);
        when(certification.previewLocked(any(),any(),any(),any(),any())).thenReturn(certified(139L,9L,List.of(line(21L,"3")),"b".repeat(64)));
        var changed=assertThrows(RuntimeException.class,()->service.requireFresh(action(contract),saved,7,ACTOR),"已认证的来源差异须单独分类");
        assertEquals("com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec$SourceChangedException",changed.getClass().getName(),
            "不能用普通安全异常冒充公共来源变化类型");
        when(certification.previewLocked(any(),any(),any(),any(),any())).thenThrow(new ServiceException("无法读取完整来源"));
        var unavailable=assertThrows(ServiceException.class,()->service.requireFresh(action(contract),saved,7,ACTOR),"读取失败应普通安全拒绝");
        assertEquals(ServiceException.class,unavailable.getClass(),"无法证明变化时不得把动作标记STALE");
    }
}

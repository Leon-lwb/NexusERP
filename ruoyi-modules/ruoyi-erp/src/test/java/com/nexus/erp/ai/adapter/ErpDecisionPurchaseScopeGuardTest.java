package com.nexus.erp.ai.adapter;

import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec.SourceChangedException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper;
import com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.pu.service.PuRequirementMaterialLockService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 全部合成来源，只证明拒绝规则与调用顺序，不冒充数据库物理验收。 */
class ErpDecisionPurchaseScopeGuardTest {
    private static final String TENANT="000000";
    private static final LocalDate DAY=LocalDate.of(2026,10,6);
    private final DecisionBusinessRef root=new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER,11L,null);
    private ErpDecisionPurchaseScopeMapper mapper;
    private PuRequirementErpTransactionQualification qualification;
    private PuRequirementMaterialLockService locks;
    private ErpDecisionPurchaseScopeGuard guard;
    private MockedStatic<TenantHelper> tenants;
    private WorkSource work(Long id,String source,Long sourceId) { return new WorkSource(id,TENANT,31L,source,sourceId,"RELEASED","0"); }
    private VendorChoice vendor(Long relation,Long id,int mark) { return new VendorChoice(relation,TENANT,139L,id,mark,1,"0","ACTIVE","0"); }
    @BeforeEach void setup() {
        tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);
        tenants.when(TenantHelper::getTenantId).thenReturn(TENANT);
        mapper=mock(ErpDecisionPurchaseScopeMapper.class);qualification=mock(PuRequirementErpTransactionQualification.class);
        locks=mock(PuRequirementMaterialLockService.class);guard=new ErpDecisionPurchaseScopeGuard(mapper,qualification,locks);
        when(mapper.pinScopeDefinition()).thenReturn(List.of());when(mapper.scopeTableEngine()).thenReturn("InnoDB");
        var work=work(11L,"MATERIAL_REQUIREMENT",51L);
        when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(work);
        when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(work));
        when(mapper.lockDirectWorkOrders(TENANT,List.of(51L))).thenReturn(List.of(work));
        when(mapper.lockRequirementSources(TENANT,139L,List.of(21L),DAY)).thenReturn(List.of(
            new RequirementSource(21L,TENANT,51L,139L,"4",DAY,1L,"0",31L,"CONFIRMED","0")));
        when(mapper.lockVendorChoices(TENANT,139L)).thenReturn(List.of(vendor(71L,9L,0)));
    }
    @AfterEach void close() {tenants.close();}
    private void check() {guard.requireLocked(TENANT,root,139L,9L,Set.of(21L),DAY);}
    @Test void acceptsUniqueDirectMrpOnlyAfterExistingMaterialGuard() {
        assertDoesNotThrow(this::check,"唯一直接MRP必须能通过真实归属规则");
        var order=inOrder(qualification,locks,mapper);
        order.verify(qualification).requireSourceChainTransaction();
        order.verify(locks).lockExisting(TENANT,List.of(139L));
        order.verify(mapper).pinScopeDefinition();
        verify(mapper).lockDirectWorkOrders(TENANT,List.of(51L));
    }
    @Test void acceptsOnlyUniqueExplicitPlanFallback() {
        var work=work(11L,"PLAN",31L);when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(work);
        when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(work));
        when(mapper.lockDirectWorkOrders(TENANT,List.of(51L))).thenReturn(List.of());
        assertDoesNotThrow(this::check,"唯一且明确PLAN来源才允许计划后备归属");
    }
    @Test void rejectsAmbiguousPlanAndDuplicateDirectSources() {
        when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(work(11L,"PLAN",31L),work(12L,"PLAN",31L)));
        assertThrows(SourceChangedException.class,this::check,"同计划多工单不得借快照缩小候选");
        when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(work(11L,"MATERIAL_REQUIREMENT",51L)));
        when(mapper.lockDirectWorkOrders(TENANT,List.of(51L))).thenReturn(List.of(work(11L,"MATERIAL_REQUIREMENT",51L),work(12L,"MATERIAL_REQUIREMENT",51L)));
        assertThrows(SourceChangedException.class,this::check,"重复直接需求关联不能按最小ID挑选");
    }
    @Test void crossTenantOrTerminalRootIsDenied() {
        assertThrows(ServiceException.class,()->guard.requireLocked("111111",root,139L,9L,Set.of(21L),DAY),"跨租户须在查询前拒绝");
        var done=new WorkSource(11L,TENANT,31L,"MATERIAL_REQUIREMENT",51L,"COMPLETED","0");
        when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(done);when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(done));
        assertThrows(SourceChangedException.class,this::check,"新建时已完工根工单不可采购");
    }
    @Test void unknownEngineAndIncompleteRowsFailClosed() {
        when(mapper.scopeTableEngine()).thenReturn("MyISAM");
        assertThrows(ServiceException.class,this::check,"新增根工单来源必须是认证事务引擎");
        when(mapper.scopeTableEngine()).thenReturn("InnoDB");when(mapper.lockRequirementSources(any(),any(),any(),any())).thenReturn(null);
        assertThrows(ServiceException.class,this::check,"读取失败不能解释为来源变化或零额度");
    }
    @Test void choosesOnlyUniqueActiveOrUniqueRealDefaultVendor() {
        assertEquals(9L,guard.selectVendorLocked(TENANT,139L),"唯一活跃关系可直接选择");
        when(mapper.lockVendorChoices(TENANT,139L)).thenReturn(List.of(vendor(71L,9L,0),vendor(72L,10L,1)));
        assertEquals(10L,guard.selectVendorLocked(TENANT,139L),"多活跃关系仅能选择唯一真实默认供应商");
        assertThrows(SourceChangedException.class,this::check,"默认供应商变化后旧合同不得继续采购原供应商");
    }
    @Test void ambiguousOrDuplicateVendorRelationshipsNeverPickFirst() {
        when(mapper.lockVendorChoices(TENANT,139L)).thenReturn(List.of(vendor(71L,9L,0),vendor(72L,10L,0)));
        assertThrows(ServiceException.class,()->guard.selectVendorLocked(TENANT,139L),"没有唯一默认供应商应拒绝");
        when(mapper.lockVendorChoices(TENANT,139L)).thenReturn(List.of(vendor(71L,9L,1),vendor(72L,9L,0)));
        assertThrows(ServiceException.class,()->guard.selectVendorLocked(TENANT,139L),"重复关系不得伪装唯一供应商");
    }
    @Test void currentDraftMrpHeadDoesNotRequireInventedConfirmationStage() {
        when(mapper.lockRequirementSources(TENANT,139L,List.of(21L),DAY)).thenReturn(List.of(
            new RequirementSource(21L,TENANT,51L,139L,"4",DAY,1L,"0",31L,"DRAFT","0")));
        assertDoesNotThrow(this::check,"真实认证支持的DRAFT需求头不必凭空升级为CONFIRMED");
    }
    @Test void rootAndRequirementLocksPrecedeVendorInAllCompleteChecks() {
        check();
        var order=inOrder(mapper);
        order.verify(mapper).lockPlanWorkOrders(TENANT,31L);
        order.verify(mapper).lockRequirementSources(TENANT,139L,List.of(21L),DAY);
        order.verify(mapper).lockDirectWorkOrders(TENANT,List.of(51L));
        order.verify(mapper).lockVendorChoices(TENANT,139L);
    }
    @Test void unknownSourceLinkIsUnavailableNotStale() {
        var unknown=new WorkSource(11L,TENANT,31L,null,null,"RELEASED","0");
        when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(unknown);when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(unknown));
        when(mapper.lockDirectWorkOrders(TENANT,List.of(51L))).thenReturn(List.of());
        assertThrows(ServiceException.class,this::check,"未知根来源类型不是已证明来源变化");
    }
    @Test void unknownRootStatusAndWrongTenantRowsStayOrdinarySafetyErrors() {
        var unknown=new WorkSource(11L,TENANT,31L,"MATERIAL_REQUIREMENT",51L,null,"0");
        when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(unknown);
        assertThrows(ServiceException.class,this::check,"未知根状态不得自动记STALE");
        when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(work(11L,"MATERIAL_REQUIREMENT",51L));
        when(mapper.lockRequirementSources(TENANT,139L,List.of(21L),DAY)).thenReturn(List.of(
            new RequirementSource(21L,"111111",51L,139L,"4",DAY,1L,"0",31L,"CONFIRMED","0")));
        assertThrows(ServiceException.class,this::check,"跨租户错误保持权限安全异常");
    }
    @Test void positiveReadOfClosedRequirementIsSourceChanged() {
        when(mapper.lockRequirementSources(TENANT,139L,List.of(21L),DAY)).thenReturn(List.of(
            new RequirementSource(21L,TENANT,51L,139L,"4",DAY,1L,"0",31L,"CLOSED","0")));
        assertThrows(SourceChangedException.class,this::check,"可信当前读确认MRP已闭合才可判来源变化");
    }
    @Test void duplicateDirectLinkOutsideRootPlanIsStillDenied() {
        when(mapper.lockDirectWorkOrders(TENANT,List.of(51L))).thenReturn(List.of(work(11L,"MATERIAL_REQUIREMENT",51L),
            new WorkSource(12L,TENANT,99L,"MATERIAL_REQUIREMENT",51L,"RELEASED","0")));
        assertThrows(SourceChangedException.class,this::check,"完整租户的其他计划重复直链不能被根计划过滤隐藏");
    }
    @Test void unknownDeletionAndStateFlagsAreSafetyErrorsNotProofOfChange() {
        var unknown=new WorkSource(11L,TENANT,31L,"MATERIAL_REQUIREMENT",51L,"RELEASED","未知");
        when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(unknown);when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(unknown));
        assertThrows(ServiceException.class,this::check,"未知工单删除标志不能解释为已删除或STALE");
        var valid=work(11L,"MATERIAL_REQUIREMENT",51L);when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(valid);
        when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(valid));
        when(mapper.lockRequirementSources(TENANT,139L,List.of(21L),DAY)).thenReturn(List.of(
            new RequirementSource(21L,TENANT,51L,139L,"4",DAY,99L,"0",31L,"CONFIRMED","0")));
        assertThrows(ServiceException.class,this::check,"未知MRP行状态不得冒充明确闭合变化");
    }
    @Test void unknownActiveVendorStatusCannotSilentlyLeaveAUniqueAlternative() {
        when(mapper.lockVendorChoices(TENANT,139L)).thenReturn(List.of(vendor(71L,9L,0),
            new VendorChoice(72L,TENANT,139L,10L,1,1,"0","未知","0")));
        assertThrows(ServiceException.class,()->guard.selectVendorLocked(TENANT,139L),"未知活跃供应商状态不能被删除后猜唯一剩余关系");
    }
    @Test void unreadableMapperFactsAreSanitizedAsUnavailableNotStale() {
        when(mapper.findRootWorkOrder(TENANT,11L)).thenThrow(new IllegalStateException("底层连接与SQL错误"));
        var failure=assertThrows(ServiceException.class,this::check,"无法读取来源必须普通安全拒绝而非来源变化");
        assertFalse(failure.getMessage().contains("SQL"),"不能暴露底层来源查询错误内容");
    }
    @Test void planLocatorRaceIsNotFollowedToAnotherPlan() {
        when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(new WorkSource(11L,TENANT,32L,"MATERIAL_REQUIREMENT",51L,"RELEASED","0")));
        assertThrows(SourceChangedException.class,this::check,"定位后的计划漂移应拒绝而不是追逐新计划");
        verify(mapper,never()).lockPlanWorkOrders(TENANT,32L);
    }
}

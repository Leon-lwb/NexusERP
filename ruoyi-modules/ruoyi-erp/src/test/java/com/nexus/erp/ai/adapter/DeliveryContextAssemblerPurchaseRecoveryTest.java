package com.nexus.erp.ai.adapter;

import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.pu.domain.vo.PuCertifiedPurchaseResult;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实Port和装配器存在性、真实采购适配器组成恢复链；只隔离来源Mapper和最终writer。 */
class DeliveryContextAssemblerPurchaseRecoveryTest {
    private static final String TENANT="000000",HASH="a".repeat(64);
    private static final DecisionBusinessRef ROOT=new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER,11L,null);
    private static final DecisionActor ACTOR=new DecisionActor(TENANT,7L,"合成执行员",
        Set.of("ai:decision:run","ai:decision:execute","prd:work:query","pu:requisition:add"));
    private PrdWorkOrderMapper work;
    private DeliveryContextAssembler assembler;
    private PuCertifiedPurchaseWriteService writer;
    private ErpIndustrialDecisionDataAdapter port;
    private MockedStatic<TenantHelper> tenants;
    @SuppressWarnings("unchecked")
    @BeforeEach void setup() throws Exception {
        tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);
        tenants.when(TenantHelper::getTenantId).thenReturn(TENANT);
        tenants.when(()->TenantHelper.dynamic(eq(TENANT),any(Supplier.class))).thenAnswer(i->((Supplier<?>)i.getArgument(1)).get());
        work=mock(PrdWorkOrderMapper.class);
        var constructor=DeliveryContextAssembler.class.getConstructors()[0];
        Object[] arguments=Arrays.stream(constructor.getParameterTypes()).map(type->type==PrdWorkOrderMapper.class?work:null).toArray();
        assembler=(DeliveryContextAssembler)constructor.newInstance(arguments);
        writer=mock(PuCertifiedPurchaseWriteService.class);
        port=new ErpIndustrialDecisionDataAdapter(assembler,mock(ErpDecisionShortageSimulation.class),mock(ErpDecisionApsSimulation.class),
            mock(ErpDecisionFreshnessChecker.class),mock(ErpDecisionActionExecutor.class),new ErpCertifiedPurchaseDecisionAdapter(writer),
            mock(ErpDecisionPurchaseContractService.class));
    }
    @AfterEach void close() {tenants.close();}
    private PrdWorkOrder root(String status,String tenant,String deleted) {
        var row=new PrdWorkOrder();row.setWoId(11L);row.setTenantId(tenant);row.setDelFlag(deleted);row.setWoStatus(status);return row;
    }
    private DecisionActionProposal action() {
        var date=LocalDate.of(2026,10,13);
        var contract=new DecisionPurchaseContractV2(139L,2L,LocalDate.of(2026,11,4),"物料版本","供应商版本",700L,"T-合成",
            26L,"Q-合成",26L,"Q-合成","Q-合成",List.of(21L),
            List.of(new DecisionPurchaseContractV2.Line(21L,26L,new BigDecimal("6"),26L,new BigDecimal("6"),date)),HASH);
        return new DecisionActionProposal(200L,DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,"合成采购","合成理由",ROOT,
            DecisionPurchaseContractCodec.toPayload(100L,contract),DecisionRiskLevel.HIGH,true,"pu:requisition:add",
            DecisionPurchaseContractCodec.idempotencyKey(TENANT,100L,ROOT,contract),"purchaseRequisitionDraft",0,List.of());
    }
    private PuCertifiedPurchaseResult receipt() {
        return new PuCertifiedPurchaseResult(99L,"PR-SYNTHETIC","DRAFT",0,true,HASH,
            List.of(new PuCertifiedPurchaseResult.Line(98L,21L,139L,26L,new BigDecimal("6"),LocalDate.of(2026,10,13))));
    }
    private boolean recoveryVisible(DecisionBusinessRef ref,DecisionActor actor) {
        return assembler.existsForPurchaseRecovery(ref,actor);
    }
    @ParameterizedTest @ValueSource(strings={"COMPLETED","CLOSED"})
    void committedReceiptRecoversAndVerifiesThroughRealPortAfterRootBecomesTerminal(String status) {
        when(work.selectById(11L)).thenReturn(root(status,TENANT,"0"));
        when(writer.recoverForDecision(any(),eq(ACTOR),eq(100L),eq(200L),anyList(),any(PuCertifiedPurchaseWriteService.FrozenContract.class)))
            .thenReturn(receipt());
        assertFalse(assembler.exists(ROOT,ACTOR),"普通分析存在性仍应排除终态根");
        var result=assertDoesNotThrow(()->port.recoverPurchaseAction(action(),ACTOR),"终态根不得在永久查询前抹去可恢复receipt");
        assertTrue(result.success(),"真实恢复信封必须认领原草稿");
        assertSame(result,port.verifyBusinessAction(action(),result,ACTOR),"Port核验也不能被终态根再次截断");
        verify(writer,times(2)).recoverForDecision(any(),eq(ACTOR),eq(100L),eq(200L),anyList(),any(PuCertifiedPurchaseWriteService.FrozenContract.class));
        verify(writer,never()).createForDecision(any(),any(),any(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"COMPLETED","CLOSED"})
    void terminalRootStillCannotStartNewPurchaseOrAnalysis(String status) {
        when(work.selectById(11L)).thenReturn(root(status,TENANT,"0"));
        assertThrows(ServiceException.class,()->port.executeBusinessAction(action(),ACTOR),"恢复例外不能放开终态根的新建采购");
        assertThrows(ServiceException.class,()->port.loadDeliveryContext(ROOT,30,ACTOR),"恢复例外不能改变普通快照分析门禁");
        verifyNoInteractions(writer);
    }
    @Test void recoveryVisibilityRequiresExactTenantUndeletedObjectAndKnownRootState() throws Exception {
        when(work.selectById(11L)).thenReturn(root("COMPLETED",TENANT,"0"));
        assertTrue(recoveryVisible(ROOT,ACTOR),"本租户可见的已完工根允许只恢复");
        when(work.selectById(11L)).thenReturn(root("COMPLETED","111111","0"));
        assertFalse(recoveryVisible(ROOT,ACTOR),"其他租户同ID不可认领原永久提交");
        when(work.selectById(11L)).thenReturn(root("COMPLETED",TENANT,"1"));
        assertFalse(recoveryVisible(ROOT,ACTOR),"删除根不能以终态例外绕过对象可见性");
        when(work.selectById(11L)).thenReturn(root(null,TENANT,"0"));
        assertFalse(recoveryVisible(ROOT,ACTOR),"未知状态不是可恢复对象的事实证明");
        when(work.selectById(11L)).thenReturn(null);
        assertFalse(recoveryVisible(ROOT,ACTOR),"缺根不等于终态根");
    }
    @Test void missingOrForeignRootAndMissingRealtimePermissionsNeverReachReceiptWriter() {
        when(work.selectById(11L)).thenReturn(null);
        assertThrows(ServiceException.class,()->port.recoverPurchaseAction(action(),ACTOR),"缺根须安全拒绝");
        when(work.selectById(11L)).thenReturn(root("COMPLETED","111111","0"));
        assertThrows(ServiceException.class,()->port.recoverPurchaseAction(action(),ACTOR),"跨租户根不可恢复");
        var noPurchase=new DecisionActor(TENANT,7L,"无采购权限",Set.of("ai:decision:execute","prd:work:query"));
        assertThrows(ServiceException.class,()->port.recoverPurchaseAction(action(),noPurchase),"恢复仍需实时采购业务权限");
        verifyNoInteractions(writer);
    }
    @Test void recoveryDoesNotTurnDifferentActorConflictIntoMissingReceipt() {
        when(work.selectById(11L)).thenReturn(root("COMPLETED",TENANT,"0"));
        when(writer.recoverForDecision(any(),eq(ACTOR),any(),any(),any(),any())).thenThrow(new ServiceException("原永久提交属于另一执行人"));
        var error=assertThrows(ServiceException.class,()->port.recoverPurchaseAction(action(),ACTOR),"实际writer执行人冲突必须传播");
        assertEquals("原永久提交属于另一执行人",error.getMessage(),"不可在对象可见性检查时误判为不存在");
    }
}

package com.nexus.erp.pu;

import static com.nexus.erp.pu.PuCertifiedPurchaseLifecycleServiceTest.hash;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.ai.adapter.*;
import com.nexus.erp.pu.domain.*;
import com.nexus.erp.pu.domain.bo.PuCertifiedPurchaseCommand;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseWriteMapper.*;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** 使用合成永久创建事实和真实领域推进；不访问实际数据库、登录服务或业务接口。 */
class PuCertifiedPurchaseOriginalReceiptTest {
    private static final String T="000001";
    private static final Date NOW=new Date(1790900000000L);
    private static final DecisionActor ACTOR=new DecisionActor(T,901L,"离线核验员",Set.of("ai:decision:execute","pu:requisition:add"));
    private static final DecisionBusinessRef TARGET=new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER,11L,"离线工单");

    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6})
    void originalEnvelopeSurvivesEveryProvenLifecycleStage(int stage) throws Exception {
        try(var f=new Fixture()) {
            f.advance(stage);f.clearWrites();
            var before=new LinkedHashMap<>(f.original.result());
            assertSame(f.original,f.adapter.verify(f.action,f.original,ACTOR),"独立核验必须原样返回历史结果，不能换成当前结论");
            assertEquals(before,f.original.result(),"原信封必须保持所有字段不变");
            var restored=f.adapter.recover(f.action,ACTOR);
            assertTrue(restored.success(),"完整合法生命周期必须允许恢复原创建回执");
            assertEquals("DRAFT",restored.result().get("status"),"恢复的是原始创建状态，不是当前物理状态");
            assertEquals("v0",restored.result().get("versionMarker"),"恢复必须保留原始创建版本");
            assertEquals("COMMITTED",restored.result().get("beforeState"),"只恢复保留原有永久提交语义");
            assertEquals(true,restored.result().get("replayed"),"只恢复不得伪称本次新建");
            assertEquals(f.original.resultBizId(),restored.resultBizId(),"恢复不得创建新单据");
            f.assertNoWrites();
        }
    }

    @ParameterizedTest @ValueSource(strings={"CLOSE_REQUISITION","CANCEL_REQUISITION"})
    void legitimateRequisitionTerminalRetainsCreationReceipt(String operation) throws Exception {
        for(boolean approved:List.of(false,true))try(var f=new Fixture()) {
            if(approved)f.approve();f.endRequisition(Operation.valueOf(operation));f.clearWrites();
            assertSame(f.original,f.adapter.verify(f.action,f.original,ACTOR),"未转单合法释放后仍必须核验历史创建");
            assertTrue(f.adapter.recover(f.action,ACTOR).success(),"合法关闭取消必须恢复原创建而非重建");f.assertNoWrites();
        }
    }

    @ParameterizedTest @ValueSource(strings={"CLOSE_ORDER","CANCEL_ORDER"})
    void terminalDraftOrderAlsoHasAnIndependentlyProvenTransfer(String operation) throws Exception {
        try(var f=new Fixture()) {
            f.advance(2);f.endOrder(Operation.valueOf(operation));f.clearWrites();
            assertSame(f.original,f.adapter.verify(f.action,f.original,ACTOR),"草稿订单合法终态不要求虚构订单批准或报价");f.assertNoWrites();
        }
    }

    @ParameterizedTest @ValueSource(ints={1,2,3,4,6})
    void executeStillRejectsCurrentNonDraft(int stage) throws Exception {
        try(var f=new Fixture()) {
            f.advance(stage);f.clearWrites();var result=f.adapter.execute(f.action,ACTOR);
            assertFalse(result.success(),"执行入口不得把当前非草稿变成新建成功");
            assertEquals("VERIFY_STATE_MISMATCH",result.errorCode(),"执行应显式拒绝非草稿");f.assertNoWrites();
        }
    }

    @ParameterizedTest @ValueSource(strings={"missingApprove","missingTransfer","missingOrderApprove","wrongEventKey","wrongCreateActor","wrongTenant","missingQuote","wrongQuote","duplicateOrder","wrongOrderParent","changedCounter","missingPhysicalReceipt","missingStockEffect","wrongReleaseReason","changedHeadVersion","extraAllocation","unknownRead"})
    void incompleteOrTamperedLifecycleCannotRecoverOrVerify(String corruption) throws Exception {
        try(var f=new Fixture()) {
            f.advance(corruption.contains("Receipt")||corruption.contains("Stock")?4:corruption.contains("Release")?6:3);
            f.corrupt(corruption);f.clearWrites();
            assertThrows(ServiceException.class,()->f.adapter.recover(f.action,ACTOR),"缺少完整可信证据必须失败关闭："+corruption);
            assertThrows(ServiceException.class,()->f.adapter.verify(f.action,f.original,ACTOR),"历史成功不能代替真实来源认证："+corruption);
            f.assertNoWrites();
        }
    }

    @ParameterizedTest @ValueSource(strings={"versionMarker","coveredDemandBase","sourceFingerprint","payloadFingerprint","purchase_payload_version"})
    void originalResultCannotBeReinterpretedAfterProgress(String field) throws Exception {
        try(var f=new Fixture()) {
            f.advance(3);var changed=new LinkedHashMap<>(f.original.result());
            if("coveredDemandBase".equals(field)) {
                var line=new LinkedHashMap<>((Map<String,Object>)((List<?>)changed.get("lines")).get(0));line.put(field,"9");changed.put("lines",List.of(line));
            } else changed.put(field,"purchase_payload_version".equals(field)?1:"versionMarker".equals(field)?"v2":"f".repeat(64));
            var forged=new DecisionActionExecutionResult(true,false,f.original.resultBizType(),f.original.resultBizId(),f.original.resultBizCode(),changed,null,null);
            assertThrows(ServiceException.class,()->f.adapter.verify(f.action,forged,ACTOR),"原始结果版本、数量和摘要不能改写："+field);
        }
    }

    @ParameterizedTest @ValueSource(strings={"missingNativeApproval","wrongNativeApprovalActor","wrongNativeVersion","wrongNativeQuoteReference","changedNativeMoney","missingNativeTerminal","wrongReceiveStatus"})
    void nativeOrderReceiptMustRemainBoundToItsOriginalActorQuoteAndVersion(String corruption) throws Exception {
        try(var f=new Fixture()) {
            f.advance("missingNativeTerminal".equals(corruption)?6:"wrongReceiveStatus".equals(corruption)?4:3);
            switch(corruption) {
                case "missingNativeApproval" -> f.nativeApproval=null;
                case "wrongNativeApprovalActor" -> {
                    var a=f.nativeApproval;f.nativeApproval=new PuCertifiedPurchaseOrderWorkflowMapper.OperationRecord(a.commandId(),a.tenantId(),a.orderId(),a.requestKey(),a.requestHash(),902L,a.operation(),a.expectedVersion(),a.resultVersion(),a.resultStatus(),a.quoteId(),a.quoteHash(),a.expectedArrivalDate(),a.totalAmount(),a.amountExcludingTax(),a.taxAmount(),a.resultFingerprint(),a.occurredAt());
                }
                case "wrongNativeVersion" -> f.nativeVersion=9;
                case "wrongNativeQuoteReference" -> f.nativeQuoteReference=1901L;
                case "changedNativeMoney" -> f.nativeMoneyTax=new BigDecimal("14");
                case "missingNativeTerminal" -> f.nativeTerminal=null;
                case "wrongReceiveStatus" -> {
                    var line=f.physical.orderLine;f.physical.orderLine=new OrderLine(line.tenantId(),line.lineId(),line.orderId(),line.requisitionLineId(),line.materialId(),line.unit(),line.orderQuantity(),line.receivedQuantity(),line.returnedQuantity(),"PENDING",line.delFlag());
                }
                default -> throw new IllegalArgumentException("未知原生回执破坏项");
            }
            f.clearWrites();
            assertThrows(ServiceException.class,()->f.adapter.recover(f.action,ACTOR),"原生订单永久回执必须完整绑定："+corruption);
            assertThrows(ServiceException.class,()->f.adapter.verify(f.action,f.original,ACTOR),"原生回执变化不能以创建成功信封绕过："+corruption);f.assertNoWrites();
        }
    }

    @Test void unknownNativeDraftReadMustStayAnUnconfirmedDomainFailure() throws Exception {
        try(var f=new Fixture()) {
            f.advance(2);f.clearWrites();when(f.nativeMapper.lockHead(T,801L)).thenThrow(new IllegalStateException("离线原生读取不可确认"));
            assertThrows(ServiceException.class,()->f.adapter.recover(f.action,ACTOR),"未批准订单未知读取必须保持可识别的失败关闭，不得当作未命中");f.assertNoWrites();
        }
    }

    @Test void fullyReceivedAndClosedOrderRetainsOriginalZeroVersion() throws Exception {
        for(boolean close:List.of(false,true))try(var f=new Fixture()) {
            f.advance(3);f.physical.receipt();
            var line=new ReceiveLine(T,1101L,1001L,701L,201L,"501",BigDecimal.TEN,"0");
            when(f.physical.mapper.lockReceiveLine(T,1101L)).thenReturn(line);when(f.physical.mapper.lockReceiveLines(T,1001L)).thenReturn(List.of(line));
            when(f.physical.mapper.lockReceiptSources(T,701L)).thenReturn(List.of(new MovementSource(T,1101L,1001L,"FINISHED","0","0")));
            when(f.physical.mapper.lockStockEffects(T,"WM_MATERIAL_RECEIVE",1001L)).thenReturn(List.of(new StockEffect(T,2102L,"RECEIVE_IN",201L,"501","WM_MATERIAL_RECEIVE",1001L,1101L,1,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN,3002L,901L,"0")));
            f.apply(Operation.RECEIPT_POSTED,1101L);if(close)f.endOrder(Operation.CLOSE_ORDER);f.clearWrites();
            assertSame(f.original,f.adapter.verify(f.action,f.original,ACTOR),"全部履约及零余量关闭仍核验原创建回执");
            assertEquals("v0",f.adapter.recover(f.action,ACTOR).result().get("versionMarker"),"全部履约后不得更新历史创建版本");f.assertNoWrites();
        }
    }

    @Test void partialFulfillmentThenOrderCancellationPreservesReceipt() throws Exception {
        try(var f=new Fixture()) {
            f.advance(4);f.endOrder(Operation.CANCEL_ORDER);f.clearWrites();
            assertSame(f.original,f.adapter.verify(f.action,f.original,ACTOR),"部分履约取消释放未履约余量后仍保留原回执");
            assertTrue(f.adapter.recover(f.action,ACTOR).success(),"合法部分履约取消可以只恢复原创建");f.assertNoWrites();
        }
    }

    @Test void composedVerificationUsesExactlyOneOwnedRepeatableReadCallback() throws Exception {
        try(var f=new Fixture()) {
            f.advance(3);f.clearWrites();clearInvocations(f.physical.executor);boolean[] owned={false};
            when(f.physical.executor.executeRepeatableRead(eq(T), any()))
                    .thenAnswer(
                            a -> {
                                owned[0] = true;
                                try {
                                    return ((Supplier<?>) a.getArgument(1)).get();
                                } finally {
                                    owned[0] = false;
                                }
                            });
            doAnswer(a->{assertTrue(owned[0],"全部生命周期和原生订单来源核验必须处于同一个专属 ERP RR 回调");return null;}).when(f.physical.qualification).requireSourceChainTransaction();
            assertSame(f.original,f.adapter.verify(f.action,f.original,ACTOR),"单个专属事务完成核验且不改信封");
            verify(f.physical.executor,times(1)).executeRepeatableRead(eq(T),any());f.assertNoWrites();
        }
    }

    @ParameterizedTest @ValueSource(strings={"SUBMITTED","APPROVING","REJECTED"})
    void unsupportedLaterHeadCannotBeCertifiedFromOnlyCreation(String status) throws Exception {
        try(var f=new Fixture()) {
            f.reqStatus=status;f.reqVersion=1;f.clearWrites();
            assertThrows(ServiceException.class,()->f.adapter.recover(f.action,ACTOR),"仅创建事件不能认证无完整推进来源的后态："+status);f.assertNoWrites();
        }
    }

    @ParameterizedTest @ValueSource(ints={1,6})
    void executeCannotAcceptAHeadForgedBackToDraftAfterRealHistory(int stage) throws Exception {
        try(var f=new Fixture()) {
            f.advance(stage);f.reqStatus="DRAFT";f.reqVersion=0;f.clearWrites();
            assertThrows(ServiceException.class,()->f.adapter.execute(f.action,ACTOR),"历史已推进的头被篡回草稿零版本也不能声称新建成功");f.assertNoWrites();
        }
    }

    @Test void missAndUnknownNeverAllocateOrRebuild() throws Exception {
        try(var f=new Fixture()) {
            when(f.write.lockCommand(T,f.key)).thenReturn(null);f.clearWrites();
            assertNull(f.adapter.recover(f.action,ACTOR),"确认无提交只能返回只读未命中");
            assertEquals("VERIFY_NOT_FOUND",f.adapter.verify(f.action,f.original,ACTOR).errorCode(),"核验未命中不得创建");f.assertNoWrites();
            when(f.write.lockCommand(T,f.key)).thenThrow(new ServiceException("离线未知读取"));
            assertThrows(ServiceException.class,()->f.adapter.recover(f.action,ACTOR),"未知读取不得伪装成未命中");f.assertNoWrites();
        }
    }

    @Test void originalActorAndTenantRemainMandatoryAfterProgress() throws Exception {
        try(var f=new Fixture()) {
            f.advance(3);f.clearWrites();
            var other=new DecisionActor(T,902L,"其他核验员",ACTOR.permissions());
            assertThrows(ServiceException.class,()->f.adapter.recover(f.action,other),"不同创建人不得认领永久提交");
            var otherTenant=new DecisionActor("000002",901L,"其他租户",ACTOR.permissions());
            assertThrows(ServiceException.class,()->f.adapter.recover(f.action,otherTenant),"跨租户恢复必须拒绝");f.assertNoWrites();
        }
    }

    static final class Fixture implements AutoCloseable {
        final PuCertifiedPurchaseLifecycleServiceTest.Fixture physical=new PuCertifiedPurchaseLifecycleServiceTest.Fixture();
        final PuCertifiedPurchaseWriteMapper write=mock(PuCertifiedPurchaseWriteMapper.class);
        final PuCertifiedPurchaseNumberReservation numbers=mock(PuCertifiedPurchaseNumberReservation.class);
        final ErpPurchaseSourceCertificationService certification=mock(ErpPurchaseSourceCertificationService.class);
        final ErpDecisionPurchaseScopeGuard scope=mock(ErpDecisionPurchaseScopeGuard.class);
        final PuCertifiedPurchaseOrderWorkflowMapper nativeMapper=mock(PuCertifiedPurchaseOrderWorkflowMapper.class);
        final PuCertifiedPurchaseOrderWorkflowService nativeWorkflow=new PuCertifiedPurchaseOrderWorkflowService(physical.executor,physical.qualification,physical.locks,nativeMapper,physical.mapper,physical.service,certification);
        PuCertifiedPurchaseOrderWorkflowMapper.Quote nativeQuote;
        PuCertifiedPurchaseOrderWorkflowMapper.OperationRecord nativeApproval,nativeTerminal;
        boolean priced=false;Integer nativeVersion=null;Long nativeQuoteReference=1801L;BigDecimal nativeMoneyTax=new BigDecimal("13");
        final DecisionActionProposal action;
        final String key,allocation;
        final PuCertifiedPurchaseWriteService writer;
        final ErpCertifiedPurchaseDecisionAdapter adapter;
        final DecisionActionExecutionResult original;
        String reqStatus="DRAFT",orderStatus;int reqVersion=0;
        Long requisitionUpdatedBy,approvalUpdatedBy,orderUpdatedBy;long transferActor=901L,orderActor=901L;
        Date requisitionUpdatedAt=NOW,approvalUpdatedAt=NOW,orderCreatedAt=NOW,orderUpdatedAt=NOW;
        RequisitionLine req=new RequisitionLine(T,401L,601L,201L,"501",BigDecimal.TEN,BigDecimal.ZERO,"MRP",301L,"2026-10-08","0");
        Fixture() throws Exception {
            var payload=new LinkedHashMap<String,Object>(Map.ofEntries(Map.entry("purchase_payload_version",2),Map.entry("runId","10"),
                Map.entry("materialId","201"),Map.entry("vendorId","1501"),Map.entry("windowEnd","2026-10-16"),Map.entry("materialVersion","b".repeat(64)),
                Map.entry("vendorVersion","c".repeat(64)),Map.entry("supplierTermsId","1601"),Map.entry("supplierTermsVersion","terms-v1"),
                Map.entry("baseUomId","501"),Map.entry("orderUomId","501"),Map.entry("baseUomVersion","uom-v1"),Map.entry("orderUomVersion","uom-v1"),
                Map.entry("conversionVersion","uom-v1"),Map.entry("expectedSourceFingerprint","b".repeat(64)),Map.entry("requirement_detail_ids",List.of("301")),
                Map.entry("frozenLines",List.of(Map.of("detailId","301","baseUomId","501","orderUomId","501","coveredDemandBase","10","requestedOrderQty","10","requiredDate","2026-10-08")))));
            var parsed=DecisionPurchaseContractCodec.parse(payload,null);key=DecisionPurchaseContractCodec.idempotencyKey(T,10L,TARGET,parsed);allocation=hash(T,key,"301");
            action=new DecisionActionProposal(101L,DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,"离线采购","冻结来源",TARGET,payload,
                DecisionRiskLevel.HIGH,true,"pu:requisition:add",key,"purchaseRequisitionDraft",1,List.of());
            var command=new PuCertifiedPurchaseCommand(key,201L,1501L,LocalDate.parse("2026-10-16"),List.of(301L),"b".repeat(64));
            var frozen=List.of(new FrozenLine(301L,501L,BigDecimal.TEN,501L,BigDecimal.TEN,LocalDate.parse("2026-10-08")));
            var contract=new FrozenContract(DecisionPurchaseContractCodec.payloadFingerprint(action,ACTOR),DecisionPurchaseContractCodec.contractMetadata(parsed),11L);
            // 复用已有摘要实现构造固定永久事实；认证行为仍由真实 writer 独立执行。
            var digest=PuCertifiedPurchaseWriteService.class.getDeclaredMethod("requestHash",PuCertifiedPurchaseCommand.class,DecisionActor.class,String.class,Long.class,Long.class,List.class,FrozenContract.class);
            digest.setAccessible(true);String requestHash=(String)digest.invoke(null,command,ACTOR,"AI_DECISION",10L,101L,frozen,contract);
            String resultHash=hash(hash("401","301","201","501","10","2026-10-08"));
            physical.row.setAllocationKey(allocation);physical.row.setDecisionIdempotencyKey(key);physical.history.get(0).setEventKey("CREATE:"+allocation);
            when(physical.locks.lockExisting(T,List.of(201L))).thenReturn(Map.of(201L,5));
            when(physical.ledgers.lockByKey(T,allocation)).thenAnswer(a->physical.row);
            when(physical.mapper.lockCommand(T,key)).thenReturn(new CommandSource(T,key,requestHash,901L,"AI_DECISION",201L,1501L,601L,"REQ-1","b".repeat(64),resultHash,"COMMITTED","d".repeat(64),"2026-10-16"));
            when(physical.mapper.lockCommandAllocations(T,key)).thenAnswer(a->List.of(physical.row));
            when(physical.mapper.lockRequisitionHead(T,601L)).thenAnswer(a->new RequisitionHead(T,601L,"REQ-1","AI_DECISION",reqStatus,key,reqVersion,"0"));
            // 独立 audit 来自原生提交参数和持久行，不读取或默认复制待认证事件的 actor。
            when(physical.mapper.lockRequisitionStateAudit(T,601L)).thenAnswer(a->new RequisitionStateAudit(T,601L,key,reqStatus,reqVersion,requisitionUpdatedBy,requisitionUpdatedAt,null,"0"));
            when(physical.mapper.lockRequisitionApprovalAudits(T,601L)).thenAnswer(a->List.of(new RequisitionApprovalAudit(T,401L,601L,req.approvedQuantity(),approvalUpdatedBy,approvalUpdatedAt,"0")));
            when(physical.mapper.lockOrderStateAudit(T,801L)).thenAnswer(a->new OrderStateAudit(T,801L,601L,transferActor,orderCreatedAt,orderUpdatedBy,orderUpdatedAt,"0"));
            when(physical.mapper.lockRequisitionLine(T,401L)).thenAnswer(a->req);when(physical.mapper.lockRequisitionLines(T,601L)).thenAnswer(a->List.of(req));
            when(physical.events.lockByKey(eq(T),anyString())).thenAnswer(a->physical.history.stream().filter(e->e.getEventKey().equals(a.getArgument(1))).findFirst().orElse(null));
            when(physical.ledgers.updateWithVersion(eq(T),any(),any())).thenAnswer(a->{physical.row=a.getArgument(1);physical.row.setVersion((Integer)a.getArgument(2)+1);return 1;});
            when(physical.events.insert(any())).thenAnswer(a->{physical.history.add(a.getArgument(0));return 1;});
            PuCertifiedPurchaseOrderWorkflowMapperTest.stubOrderDefinitions(physical.mapper);
            when(physical.mapper.lockOrderAllocations(T,801L)).thenAnswer(a->List.of(physical.row));
            prepareNativeQuote();
            when(physical.mapper.lockOrderApprovalSource(T,801L)).thenAnswer(a->quote(nativeQuote.quoteHash()));
            when(physical.mapper.lockOrderHead(T,801L)).thenAnswer(a->new OrderHead(T,801L,601L,orderStatus,"0"));
            when(physical.mapper.lockOrdersForRequisitionLine(T,401L)).thenAnswer(a->physical.orderLine==null?List.of():List.of(physical.orderLine));
            when(physical.mapper.lockOrderLines(T,801L)).thenAnswer(a->physical.orderLine==null?List.of():List.of(physical.orderLine));
            when(physical.mapper.updateOrderCounters(any(),any(),any(),any(),any(),any())).thenAnswer(a->{physical.orderLine=new OrderLine(T,701L,801L,401L,201L,"501",BigDecimal.TEN,a.getArgument(2),a.getArgument(3),a.getArgument(4),"0");return 1;});
            when(physical.mapper.updateOrderStatus(any(),any(),any(),any(),any())).thenAnswer(a->{orderStatus=a.getArgument(3);return 1;});
            when(write.commandTableEngine()).thenReturn("InnoDB");when(write.pinCommandDefinition()).thenReturn(List.of());
            when(write.commandUniqueKeyColumns()).thenReturn(List.of(new CommandUniqueKeyColumn("PRIMARY",0,1,"tenant_id",null),new CommandUniqueKeyColumn("PRIMARY",0,2,"request_key",null),
                new CommandUniqueKeyColumn("uk_certified_purchase_requisition",0,1,"tenant_id",null),new CommandUniqueKeyColumn("uk_certified_purchase_requisition",0,2,"requisition_id",null)));
            when(write.lockCommand(T,key)).thenReturn(new CommandReceipt(key,requestHash,901L,"AI_DECISION",201L,1501L,601L,"REQ-1","b".repeat(64),resultHash,"COMMITTED","d".repeat(64)));
            when(write.lockDocument(T,601L)).thenAnswer(a->new DocumentReceipt(601L,"REQ-1",reqStatus,reqVersion,key,"0","AI_DECISION",901L,10L,101L));
            when(write.lockLines(T,601L)).thenReturn(List.of(new LineReceipt(401L,201L,301L,"501",BigDecimal.TEN,"2026-10-08 00:00:00","MRP","0")));
            when(write.lockCreationEvents(T,101L)).thenAnswer(a->physical.history.stream().filter(e->"CREATE".equals(e.getEventType())).toList());
            // 按实际构造注入契约组装领域服务，不启动 Spring、登录或运行服务。
            var dependencies=List.of(mock(PuCertifiedPurchaseAuthorizer.class),physical.executor,physical.qualification,physical.locks,certification,write,
                physical.ledgers,physical.events,numbers,physical.mapper,scope,physical.service,nativeWorkflow,nativeMapper);
            var constructor=Arrays.stream(PuCertifiedPurchaseWriteService.class.getConstructors()).filter(c->c.isAnnotationPresent(Autowired.class)).findFirst().orElseThrow();
            Object[] args=Arrays.stream(constructor.getParameterTypes()).map(type->dependencies.stream().filter(type::isInstance).findFirst().orElseThrow()).toArray();
            writer=(PuCertifiedPurchaseWriteService)constructor.newInstance(args);adapter=new ErpCertifiedPurchaseDecisionAdapter(writer);
            var replayed=adapter.execute(action,ACTOR);assertTrue(replayed.success(),"合成永久创建基线必须可被真实 writer 独立核对");
            var originalFields=new LinkedHashMap<>(replayed.result());originalFields.put("replayed",false);originalFields.put("beforeState","ABSENT");
            original=new DecisionActionExecutionResult(true,false,replayed.resultBizType(),replayed.resultBizId(),replayed.resultBizCode(),originalFields,null,null);
        }
        OrderApprovalSource quote(String confirmed){return new OrderApprovalSource(T,801L,1501L,1801L,1,new BigDecimal("113"),new BigDecimal("100"),new BigDecimal("13"),
            "2026-10-12",201L,501L,key,"d".repeat(64),nativeQuote.quoteHash(),902L,903L,confirmed,"pu:order:terms:confirm",NOW);}
        void apply(Operation op,Long line){applyAs(op,line,901L);}

        void applyAs(Operation op, Long line, long actor) {
            physical.login.when(LoginHelper::getUserId).thenReturn(actor);
            try{physical.service.applyLocked(new Command(T,actor,201L,allocation,key,physical.row.getVersion(),op,line));
            } finally {
                physical.login.when(LoginHelper::getUserId).thenReturn(901L);
            }
        }

        void approve(){approveAs(901L);}
        void approveAs(long actor){reqStatus="APPROVED";reqVersion++;requisitionUpdatedBy=actor;approvalUpdatedBy=actor;
            req=new RequisitionLine(T,401L,601L,201L,"501",BigDecimal.TEN,BigDecimal.TEN,"MRP",301L,"2026-10-08","0");applyAs(Operation.APPROVE,null,actor);}

        void applyOrderAs(Operation operation, long actor) {
            physical.login.when(LoginHelper::getUserId).thenReturn(actor);
            try{physical.service.applyOrderForWorkflowLocked(new OrderCommand(T,actor,201L,key,801L,operation,Map.of(allocation,physical.row.getVersion())));
            } finally {
                physical.login.when(LoginHelper::getUserId).thenReturn(901L);
            }
        }

        void transferAs(long actor){transferActor=actor;reqStatus="ORDERED";reqVersion++;requisitionUpdatedBy=actor;orderStatus="DRAFT";
            physical.orderLine=new OrderLine(T,701L,801L,401L,201L,"501",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,"PENDING","0");applyAs(Operation.TRANSFER_TO_ORDER,701L,actor);}
        void advance(int stage){
            if(stage>=1)approve();
            if(stage>=2)transferAs(transferActor);
            if(stage>=3){orderStatus="APPROVED";orderUpdatedBy=orderActor;applyOrderAs(Operation.APPROVE_ORDER,orderActor);priced=true;nativeApproval=freezeNative(Operation.APPROVE_ORDER,0);}
            if(stage>=4){physical.receipt();when(physical.mapper.lockReceiptSources(T,701L)).thenReturn(List.of(new MovementSource(T,1101L,1001L,"FINISHED","0","0")));apply(Operation.RECEIPT_POSTED,1101L);}
            if(stage>=5){physical.returned();when(physical.mapper.lockReturnSources(T,701L)).thenReturn(List.of(new MovementSource(T,1201L,1301L,"EXECUTED","0","0")));apply(Operation.RETURN_POSTED,1201L);}
            if(stage>=6)endOrder(Operation.CLOSE_ORDER);
        }
        void endRequisition(Operation operation){reqStatus=operation==Operation.CLOSE_REQUISITION?"CLOSED":"CANCELLED";reqVersion++;requisitionUpdatedBy=901L;apply(operation,null);}
        void endOrder(Operation operation){endOrderAs(operation,901L);}
        void endOrderAs(Operation operation,long actor){orderStatus=operation==Operation.CLOSE_ORDER?"CLOSED":"CANCELLED";orderUpdatedBy=actor;applyOrderAs(operation,actor);nativeTerminal=freezeNative(operation,priced?1:0);}
        void prepareNativeQuote() throws Exception {
            var q=new PuCertifiedPurchaseOrderWorkflowMapper.Quote(1801L,T,801L,1,"native-quote-0001","a".repeat(64),902L,NOW,1501L,201L,501L,key,"b".repeat(64),"d".repeat(64),
                hash(T,"801","PO-1","601","1501",hash(hash("701","401","201","501","10"))),BigDecimal.TEN,BigDecimal.TEN,new BigDecimal("0.13"),"CNY",BigDecimal.ONE,
                "2026-10-12","2026-10-01","2026-10-15","离线报价引用","离线供方联系人","离线附件引用","a".repeat(64),"30天付款","供方交付","LINE_NET_TAX_HALF_UP_2_GROSS_UNIT_HALF_UP_4",null);
            var digest=PuCertifiedPurchaseOrderWorkflowService.class.getDeclaredMethod("quoteHash",PuCertifiedPurchaseOrderWorkflowMapper.Quote.class);digest.setAccessible(true);
            var withHash=PuCertifiedPurchaseOrderWorkflowService.class.getDeclaredMethod("withQuoteHash",PuCertifiedPurchaseOrderWorkflowMapper.Quote.class,String.class);withHash.setAccessible(true);
            nativeQuote=(PuCertifiedPurchaseOrderWorkflowMapper.Quote)withHash.invoke(null,q,digest.invoke(null,q));
            when(nativeMapper.findMaterial(T,801L)).thenReturn(201L);
            when(nativeMapper.lockHead(T,801L)).thenAnswer(a->nativeHead());
            when(nativeMapper.lockMoneyLines(T,801L)).thenAnswer(a->List.of(new PuCertifiedPurchaseOrderWorkflowMapper.LineMoney(T,701L,801L,priced?new BigDecimal("11.3"):null,priced?BigDecimal.TEN:BigDecimal.ZERO,priced?new BigDecimal("0.13"):null,priced?new BigDecimal("113"):null,priced?new BigDecimal("100"):BigDecimal.ZERO,priced?nativeMoneyTax:BigDecimal.ZERO,BigDecimal.ZERO)));
            when(nativeMapper.lockQuote(T,1801L)).thenAnswer(a->nativeQuote);
            when(nativeMapper.lockConfirmation(T,1801L)).thenAnswer(a->new PuCertifiedPurchaseOrderWorkflowMapper.Confirmation(1901L,T,1801L,nativeQuote.quoteHash(),"native-confirm-0001",
                hash("QUOTE_CONFIRM",T,"903","801","1801","native-confirm-0001",nativeQuote.quoteHash(),"离线独立复核"),903L,"pu:order:terms:confirm",NOW,"离线独立复核"));
            when(nativeMapper.lockApprovalOperation(T,801L)).thenAnswer(a->nativeApproval);when(nativeMapper.lockTerminalOperation(T,801L)).thenAnswer(a->nativeTerminal);
        }
        PuCertifiedPurchaseOrderWorkflowMapper.Head nativeHead() {
            int version=nativeVersion!=null?nativeVersion:nativeTerminal!=null?(priced?2:1):priced?1:0;
            return new PuCertifiedPurchaseOrderWorkflowMapper.Head(T,801L,"PO-1",601L,1501L,orderStatus,version,priced?nativeQuoteReference:null,
                priced?"2026-10-12":null,"0",null,"CNY",BigDecimal.ONE,priced?new BigDecimal("113"):null,priced?new BigDecimal("100"):BigDecimal.ZERO,priced?new BigDecimal("13"):null,
                priced?BigDecimal.ZERO:null,priced?BigDecimal.ZERO:null,priced?BigDecimal.ZERO:null,priced?"30天付款":null,priced?"供方交付":null);
        }
        PuCertifiedPurchaseOrderWorkflowMapper.OperationRecord freezeNative(Operation operation,int version) {
            long actor=operation==Operation.APPROVE_ORDER?orderActor:orderUpdatedBy;
            String request=operation==Operation.APPROVE_ORDER?"native-approve-0001":"native-terminal-0001";
            String state=operation==Operation.APPROVE_ORDER?"APPROVED":operation==Operation.CLOSE_ORDER?"CLOSED":"CANCELLED";
            var events=physical.history.stream().filter(e->operation.name().equals(e.getEventType())).map(e->new EventReceipt(e.getEventId(),e.getAllocationId(),operation,e.getFromStatus(),e.getToStatus(),e.getOccupiedBaseBefore(),e.getOccupiedBaseAfter(),e.getRequisitionLineId(),e.getOrderLineId(),e.getActorId(),true)).toList();
            var result=new PuCertifiedPurchaseOrderWorkflowService.Result(801L,"PO-1",request,state,version+1,priced?1801L:null,priced?nativeQuote.quoteHash():null,priced?"2026-10-12":null,priced?new BigDecimal("113"):null,priced?new BigDecimal("100"):null,priced?new BigDecimal("13"):null,true,events);
            String fingerprint;
            try {var digest=PuCertifiedPurchaseOrderWorkflowService.class.getDeclaredMethod("resultFingerprint",String.class,PuCertifiedPurchaseOrderWorkflowService.Result.class);digest.setAccessible(true);fingerprint=(String)digest.invoke(null,T,result);}
            catch(ReflectiveOperationException failure){throw new AssertionError("离线原生回执摘要构造失败",failure);}
            return new PuCertifiedPurchaseOrderWorkflowMapper.OperationRecord(2001L,T,801L,request,hash("ORDER_OPERATION",T,Long.toString(actor),"801",operation.name(),Integer.toString(version),operation==Operation.APPROVE_ORDER?"1801":"-",request),actor,operation.name(),version,version+1,state,
                result.quoteId(),result.quoteHash(),result.expectedArrivalDate(),result.totalAmount(),result.amountExcludingTax(),result.taxAmount(),fingerprint,NOW);
        }
        void corrupt(String kind){switch(kind){
            case "missingApprove"->remove("APPROVE");case "missingTransfer"->remove("TRANSFER_TO_ORDER");case "missingOrderApprove"->remove("APPROVE_ORDER");
            case "wrongEventKey"->physical.history.get(1).setEventKey("LIFECYCLE:"+"f".repeat(64));case "wrongCreateActor"->physical.history.get(0).setActorId(902L);
            case "wrongTenant"->physical.history.get(1).setTenantId("000002");case "missingQuote"->when(physical.mapper.lockOrderApprovalSource(T,801L)).thenReturn(null);
            case "wrongQuote"->when(physical.mapper.lockOrderApprovalSource(T,801L)).thenReturn(quote("f".repeat(64)));
            case "duplicateOrder"->when(physical.mapper.lockOrdersForRequisitionLine(T,401L)).thenReturn(List.of(physical.orderLine,physical.orderLine));
            case "wrongOrderParent"->when(physical.mapper.lockOrderHead(T,801L)).thenReturn(new OrderHead(T,801L,699L,orderStatus,"0"));
            case "changedCounter"->physical.orderLine=new OrderLine(T,701L,801L,401L,201L,"501",BigDecimal.TEN,BigDecimal.ONE,BigDecimal.ZERO,"PARTIAL_RECEIVED","0");
            case "missingPhysicalReceipt"->when(physical.mapper.lockReceiptSources(T,701L)).thenReturn(List.of());case "missingStockEffect"->when(physical.mapper.lockStockEffects(T,"WM_MATERIAL_RECEIVE",1001L)).thenReturn(List.of());
            case "wrongReleaseReason"->physical.row.setReleaseReason("CANCEL_ORDER");case "changedHeadVersion"->reqVersion++;
            case "extraAllocation"->when(physical.mapper.lockCommandAllocations(T,key)).thenReturn(List.of(physical.row,physical.row));
            case "unknownRead"->when(physical.events.lockByAllocation(T,101L)).thenThrow(new ServiceException("追加历史读取未知"));default->throw new IllegalArgumentException("未知离线破坏项");
        }}
        void remove(String type){physical.history.removeIf(e->type.equals(e.getEventType()));for(int i=0;i<physical.history.size();i++)physical.history.get(i).setEventSequence(i);physical.row.setVersion(physical.history.size()-1);}
        void clearWrites(){clearInvocations(write,numbers,certification,scope,physical.ledgers,physical.events,physical.guards,physical.mapper,physical.locks,nativeMapper);}
        void assertNoWrites(){verifyNoInteractions(numbers,certification,scope);verify(write,never()).insertCommand(any(),any(),any());verify(write,never()).insertDocument(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
            verify(write,never()).insertLine(any(),any(),any(),any(),any(),any(),any(),any(),any());verify(physical.ledgers,never()).insert(any());
            verify(physical.ledgers,never()).updateWithVersion(any(),any(),any());verify(physical.events,never()).insert(any());verify(physical.guards,never()).advanceVersion(any(),any(),any());
            verify(physical.mapper,never()).updateOrderCounters(any(),any(),any(),any(),any(),any());verify(physical.mapper,never()).updateOrderStatus(any(),any(),any(),any(),any());verify(physical.locks,never()).lock(any(),any());verify(nativeMapper,never()).insertOperation(any());verify(nativeMapper,never()).approveHead(any(),any(),any(),any(),any());verify(nativeMapper,never()).closeHead(any(),any(),any(),any());verify(nativeMapper,never()).priceLine(any(),any(),any(),any());verify(nativeMapper,never()).insertQuote(any());verify(nativeMapper,never()).insertConfirmation(any());}
        public void close(){physical.close();}
    }
}

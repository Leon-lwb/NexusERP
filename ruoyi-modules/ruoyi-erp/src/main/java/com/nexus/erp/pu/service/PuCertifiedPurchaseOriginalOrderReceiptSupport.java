package com.nexus.erp.pu.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.OriginalOrderObservation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Supplier;
import static com.nexus.erp.pu.service.support.PuCertifiedPurchaseLifecycleSupport.*;

/** 复用原生批准重放门禁；未定价终态只核永久空报价回执，不引入第二套定价规则。 */
final class PuCertifiedPurchaseOriginalOrderReceiptSupport {
    private PuCertifiedPurchaseOriginalOrderReceiptSupport() { }
    static void verify(String tenant,OriginalOrderObservation observation,PuCertifiedPurchaseOrderWorkflowService workflow,
                       PuCertifiedPurchaseOrderWorkflowMapper mapper) {
        require(workflow!=null&&mapper!=null,"原创建恢复缺少原生订单永久回执核验");
        if(observation.approved()) {
            workflow.requireApprovedReplayLocked(tenant,observation.orderId());
            var approval=io(()->mapper.lockApprovalOperation(tenant,observation.orderId()));
            require(approval!=null&&PuCertifiedPurchaseOriginalReceiptHistory.Time.nativeNotBeforeEvent(approval.occurredAt(),observation.transferAt())
                &&PuCertifiedPurchaseOriginalReceiptHistory.Time.nativeNotBeforeEvent(approval.occurredAt(),observation.approvalAt()),"原订单批准命令早于创建承接/原批准事件");
            for(var parameter:observation.transferParameterTimes())require(PuCertifiedPurchaseOriginalReceiptHistory.Time.parameterBetweenSubmissionBounds(parameter,observation.orderCreatedAt(),approval.occurredAt()),
                "参数转单Date不能发生在原生批准命令之后");
            if("CLOSED".equals(observation.status())||"CANCELLED".equals(observation.status())) {
                var terminal=io(()->mapper.lockTerminalOperation(tenant,observation.orderId()));
                requireTerminalTime(observation,terminal,approval.occurredAt());
            }
            return;
        }
        workflow.requireUnpricedReplayLocked(tenant,observation.orderId());
        var head=io(()->mapper.lockHead(tenant,observation.orderId()));
        require(head!=null&&tenant.equals(head.tenantId())&&observation.orderId().equals(head.orderId())
            &&observation.requisitionId().equals(head.requisitionId())&&observation.vendorId().equals(head.vendorId())
            &&observation.status().equals(head.status())&&text(head.orderNo(),64)&&"0".equals(head.delFlag())
            &&head.selectFlowId()==null&&head.quoteId()==null&&head.expectedArrivalDate()==null
            &&absentOrZero(head.totalAmount())&&absentOrZero(head.amountExcludingTax())&&absentOrZero(head.taxAmount()),
            "未批准订单头出现非原始父绑定或未经永久批准的定价事实");
        require(io(()->mapper.lockApprovalOperation(tenant,head.orderId()))==null,"未批准历史存在原生批准永久回执");
        var terminal=io(()->mapper.lockTerminalOperation(tenant,head.orderId()));
        if("DRAFT".equals(head.status())) {
            require(Integer.valueOf(0).equals(head.version())&&terminal==null,"订单草稿版本或原生终态记录与历史不一致");return;
        }
        String operation="CLOSED".equals(head.status())?"CLOSE_ORDER":"CANCEL_ORDER";
        require(terminal!=null&&positive(terminal.commandId())&&tenant.equals(terminal.tenantId())&&head.orderId().equals(terminal.orderId())
            &&positive(terminal.actorId())&&text(terminal.requestKey(),128)&&operation.equals(terminal.operation())
            &&head.status().equals(terminal.resultStatus())&&Integer.valueOf(0).equals(terminal.expectedVersion())
            &&Integer.valueOf(1).equals(terminal.resultVersion())&&terminal.resultVersion().equals(head.version())
            &&terminal.quoteId()==null&&terminal.quoteHash()==null&&terminal.expectedArrivalDate()==null
            &&terminal.totalAmount()==null&&terminal.amountExcludingTax()==null&&terminal.taxAmount()==null
            &&PuCertifiedPurchaseOriginalReceiptHistory.Time.exactNotBefore(observation.now(),terminal.occurredAt())
            &&hash("ORDER_OPERATION",tenant,terminal.actorId().toString(),head.orderId().toString(),operation,"0","-",terminal.requestKey()).equals(terminal.requestHash()),
            "未定价订单终态缺少完整永久命令/原版本/空报价合同");
        requireTerminalTime(observation,terminal,observation.transferAt());
        var events=new ArrayList<String>();
        for(var e:observation.terminalEvents().stream().sorted(Comparator.comparing(PuCertifiedPurchaseLifecycleService.EventReceipt::allocationId)).toList()) {
            require(operation.equals(e.operation().name())&&terminal.actorId().equals(e.actorId()),"未定价终态永久操作人与逐来源释放事件不一致");
            events.add(hash(e.eventId().toString(),e.allocationId().toString(),e.operation().name(),e.fromStatus(),e.toStatus(),
                e.occupiedBefore().stripTrailingZeros().toPlainString(),e.occupiedAfter().stripTrailingZeros().toPlainString(),
                e.requisitionLineId().toString(),e.orderLineId().toString(),e.actorId().toString()));
        }
        // 空报价专用合同保持原生 ORDER_RESULT 摘要；不计算或构造报价、金额、税额与 ETA。
        require(!events.isEmpty()&&hash("ORDER_RESULT",tenant,head.orderId().toString(),head.orderNo(),terminal.requestKey(),head.status(),"1",
            "-","-","-","-","-","-",hash(events.toArray(String[]::new))).equals(terminal.resultFingerprint()),"未定价终态原生结果与全来源释放摘要不一致");
    }
    /** 原生终态记录在头 CAS/全部追加事件之后，且不得越过原创建/批准因果下界。 */
    private static void requireTerminalTime(OriginalOrderObservation observation,PuCertifiedPurchaseOrderWorkflowMapper.OperationRecord terminal,java.util.Date previous) {
        for(var parameter:observation.releaseParameterTimes())require(terminal!=null&&PuCertifiedPurchaseOriginalReceiptHistory.Time.parameterBetweenSubmissionBounds(parameter,previous,terminal.occurredAt()),
            "参数释放Date不能越过原生前序提交与终态命令的精确窗口");
        require(terminal!=null&&terminal.actorId().equals(observation.casActor())
            &&PuCertifiedPurchaseOriginalReceiptHistory.Time.exactNotBefore(observation.now(),terminal.occurredAt())
            &&PuCertifiedPurchaseOriginalReceiptHistory.Time.nativeNotBeforeEvent(terminal.occurredAt(),observation.latestHistoryAt())
            &&(observation.approved()?PuCertifiedPurchaseOriginalReceiptHistory.Time.exactNotBefore(terminal.occurredAt(),previous)
                :PuCertifiedPurchaseOriginalReceiptHistory.Time.nativeNotBeforeEvent(terminal.occurredAt(),previous))
            &&PuCertifiedPurchaseOriginalReceiptHistory.Time.notBefore(observation.casAt(),previous)
            &&PuCertifiedPurchaseOriginalReceiptHistory.Time.notBefore(observation.terminalAt(),observation.casAt()),"原生终态命令/头 CAS/对应事件与前序提交因果不一致");
    }
    private static <T>T io(Supplier<T> read){try{return read.get();}catch(RuntimeException failure){throw new ServiceException("可信采购原生历史读取失败，结果不可确认");}}
    private static boolean absentOrZero(java.math.BigDecimal value){return value==null||value.signum()==0;}
    private static void require(boolean condition,String message){if(!condition)throw new ServiceException("可信采购原生历史拒绝："+message);}
}

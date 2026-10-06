package com.nexus.erp.pu.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.domain.PuRequirementAllocationEvent;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import static com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.Operation;

/** 仅在内存重演已锁定的永久事件；复用原账本策略，不写入、不重新授予占额。 */
final class PuCertifiedPurchaseOriginalReceiptHistory {
    record Proof(String requisitionStatus, int requisitionVersion, boolean approved,
                 boolean orderApproved, String orderTerminal) { }
    private PuCertifiedPurchaseOriginalReceiptHistory() { }

    static Proof prove(PuRequirementAllocationLedger current, List<PuRequirementAllocationEvent> history, Date now) {
        var seed=new PuRequirementAllocationLedger();
        seed.setAllocationId(current.getAllocationId());seed.setTenantId(current.getTenantId());seed.setMaterialId(current.getMaterialId());
        seed.setSourceType(current.getSourceType());seed.setDetailId(current.getDetailId());seed.setRequisitionLineId(current.getRequisitionLineId());
        seed.setAllocationNo(current.getAllocationNo());seed.setBaseUomId(current.getBaseUomId());seed.setCoveredDemandBaseQty(current.getCoveredDemandBaseQty());
        seed.setAllocationKey(current.getAllocationKey());seed.setDecisionIdempotencyKey(current.getDecisionIdempotencyKey());
        String requisition="DRAFT",terminal=null;int version=0;boolean approved=false,orderApproved=false;
        Date previous=history.get(0).getOccurredAt();
        require(PuCertifiedPurchaseOriginalReceiptHistory.Time.nativeNotBeforeEvent(now,previous),"原创建时间未知或超出当前数据库时钟");
        // 时间实证另核独立持久来源；固定标记仅满足纯策略参数，不冒充原始 context Date。
        Date arithmeticMarker=new Date(0);
        PuRequirementAllocationLedger replay;
        try {
            replay=PuRequirementAllocationPolicy.createRequestIntent(seed,arithmeticMarker).ledger();
            for(int i=1;i<history.size();i++) {
                var e=history.get(i);Date at=e.getOccurredAt();
                require(PuCertifiedPurchaseOriginalReceiptHistory.Time.notBefore(at,previous)&&PuCertifiedPurchaseOriginalReceiptHistory.Time.nativeNotBeforeEvent(now,at),"追加事件业务时间不连续或来自未来");
                require(Objects.equals(replay.getAllocationStatus(),e.getFromStatus())
                    &&replay.getOccupiedBaseQty().compareTo(e.getOccupiedBaseBefore())==0,"重演事件前态与原账本策略不一致");
                Operation op=Operation.valueOf(e.getEventType());
                switch(op) {
                    case APPROVE -> {
                        require("DRAFT".equals(requisition)&&!approved,"申请批准缺少唯一草稿前态");
                        replay=PuRequirementAllocationPolicy.approveRequestIntent(replay,arithmeticMarker).ledger();
                        requisition="APPROVED";approved=true;version++;
                    }
                    case TRANSFER_TO_ORDER -> {
                        require("APPROVED".equals(requisition)&&approved,"转单缺少原申请完整批准历史");
                        replay=PuRequirementAllocationPolicy.commitToOrder(replay,e.getOrderLineId(),arithmeticMarker).ledger();
                        requisition="ORDERED";version++;
                    }
                    case CLOSE_REQUISITION,CANCEL_REQUISITION,REJECT_REQUISITION -> {
                        require("DRAFT".equals(requisition)||"APPROVED".equals(requisition),"申请终态缺少未转单前态");
                        replay=PuRequirementAllocationPolicy.releaseRequestIntent(replay,arithmeticMarker,op.name()).ledger();
                        requisition=op==Operation.CLOSE_REQUISITION?"CLOSED":op==Operation.CANCEL_REQUISITION?"CANCELLED":"REJECTED";version++;
                    }
                    case APPROVE_ORDER -> {
                        require("ORDERED".equals(requisition)&&!orderApproved&&terminal==null
                            &&replay.getFulfilledBaseQty().signum()==0&&replay.getReleasedBaseQty().signum()==0,"订单批准历史前态不完整或重复");
                        require("ORDER_COMMITTED_NOT_TIMELY_SUPPLY".equals(replay.getAllocationStatus()),"订单批准缺少真实转单继承");
                        orderApproved=true;
                    }
                    case RECEIPT_POSTED -> {
                        require(orderApproved&&terminal==null,"收货缺少原订单批准或发生于终态之后");
                        replay=PuRequirementAllocationPolicy.receiveOrder(replay,e.getOccupiedBaseBefore().subtract(e.getOccupiedBaseAfter()),arithmeticMarker).ledger();
                    }
                    case RETURN_POSTED -> {
                        require(orderApproved&&terminal==null,"退货缺少原订单批准或已结案冻结来源");
                        replay=PuRequirementAllocationPolicy.returnOrder(replay,e.getOccupiedBaseAfter().subtract(e.getOccupiedBaseBefore()),arithmeticMarker).ledger();
                    }
                    case CLOSE_ORDER,CANCEL_ORDER -> {
                        require("ORDERED".equals(requisition)&&terminal==null,"订单释放缺少唯一转单前态");
                        replay=PuRequirementAllocationPolicy.releaseOrder(replay,arithmeticMarker,op.name()).ledger();
                        terminal=op==Operation.CLOSE_ORDER?"CLOSED":"CANCELLED";
                    }
                    case COMPENSATE_DRAFT -> throw failed("已补偿创建不能恢复为原成功提交");
                }
                require(Objects.equals(replay.getOrderLineId(),e.getOrderLineId())&&Objects.equals(replay.getAllocationStatus(),e.getToStatus())
                    &&replay.getOccupiedBaseQty().compareTo(e.getOccupiedBaseAfter())==0,"追加事件后态或订单继承违反原账本策略");
                replay.setVersion(i);previous=at;
            }
        } catch(IllegalArgumentException invalid) {throw failed("追加历史不符合已支持的原账本生命周期");}
        require(replay.getOccupiedBaseQty().compareTo(current.getOccupiedBaseQty())==0
            &&replay.getFulfilledBaseQty().compareTo(current.getFulfilledBaseQty())==0
            &&replay.getReleasedBaseQty().compareTo(current.getReleasedBaseQty())==0
            &&Objects.equals(replay.getAllocationStatus(),current.getAllocationStatus())
            &&Objects.equals(replay.getOrderLineId(),current.getOrderLineId())

            &&Objects.equals(replay.getReleaseReason(),current.getReleaseReason()),"历史重演末态与当前完整守恒账本不一致");
        require(Time.nativeNotBeforeEvent(history.get(0).getOccurredAt(),current.getCreateTime()),"原CREATE和当前账本创建默认时钟来源不一致");
        if(history.size()>1)require(Time.notBefore(previous,current.getUpdateTime())
            &&Time.notBefore(current.getUpdateTime(),history.get(history.size()-2).getOccurredAt()),"账本最后NOW写时钟与原追加次序不一致");
        return new Proof(requisition,version,approved,orderApproved,terminal);
    }
    /** 明确区分SQL默认秒、参数Date区间和原生毫秒；不使用统一容差或改写时间/hash。 */
    static final class Time {
        private Time() { }
        static boolean notBefore(Date later,Date earlier) {
            return later!=null&&earlier!=null&&Math.floorDiv(later.getTime(),1000)>=Math.floorDiv(earlier.getTime(),1000);
        }
        /** 原生操作与数据库时钟同为 datetime(3)，必须保留精确毫秒因果。 */
        static boolean exactNotBefore(Date later,Date earlier){return later!=null&&earlier!=null&&!later.before(earlier);}
        /** 事件DEFAULT/NOW(0)为INSERT时钟floor；后写原生命令不能早于该严格秒下界。 */
        static boolean nativeNotBeforeEvent(Date nativeTime,Date eventTime){
            return nativeTime!=null&&eventTime!=null&&nativeTime.getTime()>=sqlLower(eventTime);
        }
        /** 参数Date落datetime(0)的逆舍入区间为存储秒前后各500毫秒；只用于该来源的列，不舍入SQL时钟。 */
        static boolean parameterWithinCommit(Date persisted,Date previousSql,Date eventSql,Date observedNative) {
            if(persisted==null||previousSql==null||eventSql==null||observedNative==null||Math.floorMod(persisted.getTime(),1000)!=0)return false;
            long lower=Math.max(persisted.getTime()-500,sqlLower(previousSql));
            long upper=Math.min(persisted.getTime()+500,Math.min(sqlLower(eventSql)+1000,observedNative.getTime()+1));
            return lower<upper;
        }
        /** 前序为独立认证的SQL秒下界或原生毫秒；后序命令始终保留精确毫秒。 */
        static boolean parameterBetweenSubmissionBounds(Date persisted,Date previousLowerBound,Date nextNative) {
            return persisted!=null&&previousLowerBound!=null&&nextNative!=null&&exactNotBefore(nextNative,previousLowerBound)
                &&Math.max(persisted.getTime()-500,previousLowerBound.getTime())<Math.min(persisted.getTime()+500,nextNative.getTime()+1);
        }
        private static long sqlLower(Date value){return Math.floorDiv(value.getTime(),1000)*1000;}
        static Date copy(Date value){return value==null?null:new Date(value.getTime());}
    }
    private static void require(boolean condition,String message){if(!condition)throw failed(message);}
    private static ServiceException failed(String message){return new ServiceException("可信采购原创建历史拒绝："+message);}
}

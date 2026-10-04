package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.pu.service.PuCertifiedPurchaseReceiptBatchService;
import com.nexus.erp.pu.service.PuCertifiedPurchaseReceiptBatchService.LineReceipt;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.Operation;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

/** 永久收货恢复事实在同一ERP独占RR内读回；不创建数据、不恢复MQ或签发来源/运行证书。 */
@Service
@DS("erp")
public class WmCertifiedReceiptRecoveryService {
    public enum ConsumptionState { PENDING, CONSUMED }
    public record RecoveryView(Long receiveId, Long orderId, Long materialId, BigDecimal quantity,
        String sourceFingerprint, String postedAt, String notificationStatus,
        ConsumptionState consumptionState, List<LineReceipt> consumptionEvents) {
        public RecoveryView { consumptionEvents=List.copyOf(consumptionEvents); }
    }
    private final WmCertifiedReceiptPostingAuthorizer authorizer;
    private final PuRequirementErpTransactionExecutor executor;
    private final PuRequirementErpTransactionQualification qualification;
    private final WmCertifiedReceiptPostingService posting;
    private final PuCertifiedPurchaseReceiptBatchService batches;

    public WmCertifiedReceiptRecoveryService(WmCertifiedReceiptPostingAuthorizer authorizer,
        PuRequirementErpTransactionExecutor executor, PuRequirementErpTransactionQualification qualification,
        WmCertifiedReceiptPostingService posting, PuCertifiedPurchaseReceiptBatchService batches) {
        this.authorizer=Objects.requireNonNull(authorizer);this.executor=Objects.requireNonNull(executor);
        this.qualification=Objects.requireNonNull(qualification);this.posting=Objects.requireNonNull(posting);
        this.batches=Objects.requireNonNull(batches);
    }
    public RecoveryView read(Long receiveId) {
        DecisionActor actor=authorizer.requireQuery();
        require(actor!=null && actor.tenantId()!=null && actor.tenantId().matches("[0-9]{6,20}")
            && TenantHelper.isEnable() && actor.tenantId().equals(TenantHelper.getTenantId())
            && positive(actor.userId()) && positive(receiveId), "查询身份/租户或原收货无效");
        return executor.executeRepeatableRead(actor.tenantId(),()->readLocked(actor.tenantId(),receiveId));
    }
    private RecoveryView readLocked(String tenant,Long receiveId) {
        qualification.requireSourceChainTransaction();
        var original=posting.readPostedLocked(tenant,receiveId);
        require(original!=null && positive(original.orderId()) && original.result()!=null, "原永久过账范围未知");
        var result=original.result();
        require(receiveId.equals(result.receiveId()) && positive(result.materialId()) && result.replay()
            && result.quantity()!=null && result.quantity().signum()>0 && result.sourceFingerprint()!=null
            && result.sourceFingerprint().matches("[0-9a-f]{64}")
            && Set.of("PENDING","ENQUEUED").contains(original.notificationStatus()), "原过账结果不可核验");
        var committed=batches.readCommittedReceiptLocked(tenant,receiveId,original.orderId());
        require(committed!=null,"永久消费读取结果未知");
        List<LineReceipt> events=List.of();ConsumptionState state=ConsumptionState.PENDING;
        if(committed.isPresent()) {
            var receipt=committed.get();
            require(tenant.equals(receipt.tenantId()) && receiveId.equals(receipt.receiveId())
                && original.orderId().equals(receipt.orderId()) && receipt.certified() && receipt.replayed()
                && !receipt.lines().isEmpty(),"永久消费父链或恢复性质不一致");
            Set<Long> lines=new HashSet<>(),ids=new HashSet<>();BigDecimal consumed=BigDecimal.ZERO;
            for(var line:receipt.lines()) {
                require(line!=null && positive(line.receiveLineId()) && lines.add(line.receiveLineId())
                    && line.event()!=null && positive(line.event().eventId()) && ids.add(line.event().eventId())
                    && line.event().operation()==Operation.RECEIPT_POSTED && line.event().replayed()
                    && line.event().occupiedBefore()!=null && line.event().occupiedAfter()!=null
                    && line.event().occupiedBefore().compareTo(line.event().occupiedAfter())>0,
                    "永久逐行消费事件缺失/重复/量不一致");
                consumed=consumed.add(line.event().occupiedBefore().subtract(line.event().occupiedAfter()));
            }
            require(consumed.compareTo(result.quantity())==0,"永久消费总量不等于原物理过账");
            events=receipt.lines();state=ConsumptionState.CONSUMED;
        }
        qualification.requireSourceChainTransaction();
        return new RecoveryView(receiveId,original.orderId(),result.materialId(),result.quantity(),
            result.sourceFingerprint(),original.postedAt(),original.notificationStatus(),state,events);
    }
    private static boolean positive(Long id){return id!=null && id>0;}
    private static void require(boolean condition,String reason){if(!condition)throw new ServiceException("永久收货恢复拒绝："+reason);}
}

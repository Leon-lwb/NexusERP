package com.nexus.erp.pu;

import com.nexus.erp.pu.domain.*;
import com.nexus.erp.pu.domain.vo.PuCertifiedPurchaseResult;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.nexus.erp.pu.PuCertifiedPurchaseLifecycleServiceTest.hash;

/** 整单部分履约必须覆盖未收货来源行；只使用离线物理替身和真实生命周期服务。 */
class PuCertifiedPurchaseOriginalReceiptLifecycleTest {
    @Test void partialReceiptOnOneLineDoesNotEraseTheOtherOriginalCreationLine() throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            var current=twoLines(f,"PARTIALLY_RECEIVED");
            f.clearWrites();
            assertDoesNotThrow(()->{f.physical.service.verifyOriginalCreationLocked("000001",201L,f.key,current);},
                "整单部分履约时未收货行也必须独立保留原创建证明");
            f.assertNoWrites();
        }
    }
    @Test void fullReceiptHeaderCannotHideAnUnfulfilledSecondLine() throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            var current=twoLines(f,"RECEIVED");f.clearWrites();
            assertThrows(com.nexus.common.core.exception.ServiceException.class,
                ()->f.physical.service.verifyOriginalCreationLocked("000001",201L,f.key,current),
                "整单已收货必须有全部行的物理履约证据");f.assertNoWrites();
        }
    }
    private static PuCertifiedPurchaseResult twoLines(PuCertifiedPurchaseOriginalReceiptTest.Fixture f,String status) {
        String tenant="000001";f.advance(4);f.orderStatus=status;
        String secondKey=hash(tenant,f.key,"302");
        var second=new PuRequirementAllocationLedger();BeanUtils.copyProperties(f.physical.row,second);
        second.setAllocationId(102L);second.setDetailId(302L);second.setRequisitionLineId(402L);second.setOrderLineId(702L);second.setAllocationKey(secondKey);
        second.setOccupiedBaseQty(BigDecimal.TEN);second.setFulfilledBaseQty(BigDecimal.ZERO);second.setVersion(3);
        var secondReq=new RequisitionLine(tenant,402L,601L,201L,"501",BigDecimal.TEN,BigDecimal.TEN,"MRP",302L,"2026-10-08","0");
        var secondOrder=new OrderLine(tenant,702L,801L,402L,201L,"501",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,"PENDING","0");
        List<PuRequirementAllocationEvent> history=new ArrayList<>();
        for(var e:f.physical.history.subList(0,4)) {
            var copy=new PuRequirementAllocationEvent();BeanUtils.copyProperties(e,copy);copy.setAllocationId(102L);copy.setEventId(e.getEventId()+100);copy.setRequisitionLineId(402L);
            copy.setOrderLineId(e.getOrderLineId()==null?null:702L);
            String source="TRANSFER_TO_ORDER".equals(e.getEventType())?"702":"APPROVE_ORDER".equals(e.getEventType())?"801":"601";
            copy.setEventKey("CREATE".equals(e.getEventType())?"CREATE:"+secondKey:"LIFECYCLE:"+hash(tenant,secondKey,e.getEventType(),source));history.add(copy);
        }
        when(f.physical.ledgers.lockByKey(tenant,secondKey)).thenReturn(second);
        when(f.physical.events.lockByAllocation(tenant,102L)).thenReturn(history);
        when(f.physical.mapper.lockRelatedAllocations(eq(tenant),eq(402L),any())).thenReturn(List.of(second));
        when(f.physical.mapper.lockRequisitionLine(tenant,402L)).thenReturn(secondReq);
        when(f.physical.mapper.lockRequisitionLines(tenant,601L)).thenReturn(List.of(f.req,secondReq));
        when(f.physical.mapper.lockRequisitionApprovalAudits(tenant,601L)).thenAnswer(a->List.of(
            new RequisitionApprovalAudit(tenant,401L,601L,BigDecimal.TEN,f.approvalUpdatedBy,f.approvalUpdatedAt,"0"),
            new RequisitionApprovalAudit(tenant,402L,601L,BigDecimal.TEN,f.approvalUpdatedBy,f.approvalUpdatedAt,"0")));
        when(f.physical.mapper.lockDemandDetail(tenant,302L)).thenReturn(new DemandDetail(tenant,302L,1401L,201L,1L,"0"));
        when(f.physical.mapper.lockOrdersForRequisitionLine(tenant,402L)).thenReturn(List.of(secondOrder));
        when(f.physical.mapper.lockOrderLines(tenant,801L)).thenAnswer(a->List.of(f.physical.orderLine,secondOrder));
        when(f.physical.mapper.lockOrderAllocations(tenant,801L)).thenAnswer(a->List.of(f.physical.row,second));
        when(f.physical.mapper.lockRequisitionAllocations(tenant,601L)).thenAnswer(a->List.of(f.physical.row,second));
        when(f.physical.mapper.lockCommandAllocations(tenant,f.key)).thenAnswer(a->List.of(f.physical.row,second));
        when(f.physical.mapper.lockReceiptSources(tenant,702L)).thenReturn(List.of());when(f.physical.mapper.lockReturnSources(tenant,702L)).thenReturn(List.of());
        var source=f.physical.mapper.lockCommand(tenant,f.key);
        when(f.physical.mapper.lockCommand(tenant,f.key)).thenReturn(new CommandSource(source.tenantId(),source.requestKey(),source.requestHash(),source.actorId(),source.entryType(),source.materialId(),source.vendorId(),source.requisitionId(),source.requisitionNo(),source.sourceFingerprint(),
            hash(hash("401","301","201","501","10","2026-10-08"),hash("402","302","201","501","10","2026-10-08")),source.commandStatus(),source.catalogFingerprint(),source.windowEnd()));
        return new PuCertifiedPurchaseResult(601L,"REQ-1","ORDERED",2,true,"b".repeat(64),List.of(
            new PuCertifiedPurchaseResult.Line(401L,301L,201L,501L,BigDecimal.TEN,LocalDate.parse("2026-10-08")),
            new PuCertifiedPurchaseResult.Line(402L,302L,201L,501L,BigDecimal.TEN,LocalDate.parse("2026-10-08"))));
    }
}

package com.nexus.erp.ai.adapter;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.service.PuRequirementCompetitionPoolReader.RawLockedFacts;
import com.nexus.erp.pu.service.PuRequirementMaterialLockService;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.function.*;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.*;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;

/** 完整预览的原始竞争池读取，禁止初始化 guard；不替换认证核心或复制采购写入口。 */
@Service
public class ErpPurchaseCompetitionSourceReader {
    private final PuRequirementCompetitionPoolMapper mapper;
    private final PuRequirementMaterialLockService locks;
    private final PuRequirementErpTransactionQualification qualification;
    public ErpPurchaseCompetitionSourceReader(PuRequirementCompetitionPoolMapper mapper,PuRequirementMaterialLockService locks,
            PuRequirementErpTransactionQualification qualification){this.mapper=mapper;this.locks=locks;this.qualification=qualification;}
    public RawLockedFacts readLocked(String tenant,Long material,LocalDate end){
        need(TenantHelper.isEnable()&&tenant!=null&&tenant.equals(TenantHelper.getTenantId())&&material!=null&&material>0
            &&end!=null&&end.getYear()>=1000&&end.getYear()<=9999,"预览租户、物料或窗口不符");
        qualification.requireSourceChainTransaction();locks.lockExisting(tenant,List.of(material));
        var details=materialRows(io(()->mapper.lockRequirementDetails(tenant,material,end)),tenant,material,RawRequirementDetail::getTenantId,RawRequirementDetail::getMaterialId,RawRequirementDetail::getDetailId);
        var requirementIds=ids(details,RawRequirementDetail::getRequirementId);
        var requirements=headRows(requirementIds.isEmpty()?List.of():io(()->mapper.lockRequirementHeads(tenant,requirementIds)),tenant,requirementIds,RawRequirementHead::getTenantId,RawRequirementHead::getRequirementId);
        var stocks=materialRows(io(()->mapper.lockStocks(tenant,material)),tenant,material,RawStock::getTenantId,RawStock::getMaterialId,RawStock::getStockId);
        var orderLines=materialRows(io(()->mapper.lockOrderLines(tenant,material)),tenant,material,RawOrderLine::getTenantId,RawOrderLine::getMaterialId,RawOrderLine::getLineId);
        var orderIds=ids(orderLines,RawOrderLine::getOrderId);
        var orders=headRows(orderIds.isEmpty()?List.of():io(()->mapper.lockOrderHeads(tenant,orderIds)),tenant,orderIds,RawOrderHead::getTenantId,RawOrderHead::getOrderId);
        var requestLines=materialRows(io(()->mapper.lockRequisitionLines(tenant,material)),tenant,material,RawRequisitionLine::getTenantId,RawRequisitionLine::getMaterialId,RawRequisitionLine::getLineId);
        var requestIds=ids(requestLines,RawRequisitionLine::getRequisitionId);
        var requests=headRows(requestIds.isEmpty()?List.of():io(()->mapper.lockRequisitionHeads(tenant,requestIds)),tenant,requestIds,RawRequisitionHead::getTenantId,RawRequisitionHead::getRequisitionId);
        var allocations=materialRows(io(()->mapper.lockAllocations(tenant,material)),tenant,material,PuRequirementAllocationLedger::getTenantId,PuRequirementAllocationLedger::getMaterialId,PuRequirementAllocationLedger::getAllocationId);
        return new RawLockedFacts(details,requirements,stocks,orderLines,orders,requestLines,requests,allocations);
    }
    private static <T>List<T> materialRows(List<T> rows,String tenant,Long material,Function<T,String> owner,Function<T,Long> mat,Function<T,Long> identity){
        need(rows!=null,"预览原始来源未知");var keys=new HashSet<Long>();
        for(T row:rows)need(row!=null&&tenant.equals(owner.apply(row))&&material.equals(mat.apply(row))&&identity.apply(row)!=null&&identity.apply(row)>0&&keys.add(identity.apply(row)),"预览来源身份或租户不符");
        return List.copyOf(rows);
    }
    private static <T>List<Long> ids(List<T> rows,Function<T,Long> head){
        var ids=new TreeSet<Long>();for(T row:rows){need(head.apply(row)!=null&&head.apply(row)>0,"预览关联头身份缺失");ids.add(head.apply(row));}return List.copyOf(ids);
    }
    private static <T>List<T> headRows(List<T> rows,String tenant,List<Long> expected,Function<T,String> owner,Function<T,Long> identity){
        need(rows!=null,"预览原始关联头未知");var ids=new HashSet<Long>();
        for(T row:rows)need(row!=null&&tenant.equals(owner.apply(row))&&expected.contains(identity.apply(row))&&ids.add(identity.apply(row)),"预览关联头身份或租户不符");
        need(ids.equals(Set.copyOf(expected)),"预览关联头来源不完整");return List.copyOf(rows);
    }
    private static <T>T io(Supplier<T> query){try{return query.get();}catch(RuntimeException failure){throw new ServiceException("采购完整预览来源读取失败");}}
    private static void need(boolean yes,String reason){if(!yes)throw new ServiceException("采购完整预览拒绝："+reason);}
}

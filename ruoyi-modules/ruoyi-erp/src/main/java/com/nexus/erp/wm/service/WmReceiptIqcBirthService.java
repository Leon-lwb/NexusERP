package com.nexus.erp.wm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.iqc.IqcNativeSchema;
import com.nexus.common.mq.iqc.ReceiptIqcBirthPort;
import com.nexus.common.mq.iqc.ReceiptIqcBirthSupport;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.pu.service.PuRequirementMaterialLockService;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService;
import com.nexus.erp.wm.mapper.WmReceiptIqcBirthMapper;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

/** 真正IQC创建前的ERP永久准备；同物料守卫/独占RR冻结，跨库未提交时只重放同登记。 */
@Service
public class WmReceiptIqcBirthService implements ReceiptIqcBirthPort {
    private final WmReceiptIqcBirthMapper mapper;
    private final WmReceiptIqcEvidenceService scopes;
    private final PuRequirementErpTransactionExecutor transactions;
    private final PuRequirementErpTransactionQualification qualification;
    private final PuRequirementMaterialLockService locks;
    private final PuCertifiedPurchaseOrderWorkflowService orders;
    public WmReceiptIqcBirthService(WmReceiptIqcBirthMapper mapper, WmReceiptIqcEvidenceService scopes,
        PuRequirementErpTransactionExecutor transactions, PuRequirementErpTransactionQualification qualification,
        PuRequirementMaterialLockService locks, PuCertifiedPurchaseOrderWorkflowService orders) {
        this.mapper=Objects.requireNonNull(mapper);this.scopes=Objects.requireNonNull(scopes);
        this.transactions=Objects.requireNonNull(transactions);this.qualification=Objects.requireNonNull(qualification);
        this.locks=Objects.requireNonNull(locks);this.orders=Objects.requireNonNull(orders);
    }
    @Override public Birth prepare(String tenantId, Long receiveId, Long materialId) {
        tenant(tenantId); need(positive(receiveId)&&positive(materialId),"IQC_BIRTH_IDENTITY_INVALID");
        return transactions.executeRepeatableRead(tenantId, () -> {
            qualification.requireSourceChainTransaction(); locks.lock(tenantId, List.of(materialId));
            IqcNativeSchema.requireErpBirthDefinition(mapper.columns(),mapper.indexes());
            var scope=scopes.captureBirthScopeLocked(tenantId,receiveId,materialId);
            var d=mapper.lockDescription(tenantId,receiveId,materialId);
            need(d!=null&&tenantId.equals(d.getTenantId())&&receiveId.equals(d.getReceiveId())
                &&materialId.equals(d.getMaterialId())&&"0".equals(d.getDelFlag())&&"APPROVED".equals(d.getStatus())
                &&positive(d.getPurId())&&positive(d.getVendorId()),"IQC_BIRTH_SOURCE_NOT_CERTIFIED");
            orders.requireApprovedLocked(tenantId,d.getPurId());
            var existing=mapper.lockScope(tenantId,receiveId,materialId);
            if(existing!=null) {
                Birth born=existing.value(); ReceiptIqcBirthSupport.validate(born);
                need(born.capturedAt()<=System.currentTimeMillis()&&tenantId.equals(born.tenantId())&&receiveId.equals(born.receiveId())&&materialId.equals(born.materialId())
                    &&scope.baseUomId().equals(born.baseUomId())&&scope.quantityScale()==born.quantityScale()
                    &&scope.quantity().compareTo(born.quantity())==0&&scope.sha256().equals(born.receiptScopeSha256())
                    &&d.getVendorId().equals(born.vendorId()),"IQC_BIRTH_SOURCE_CHANGED");
                qualification.requireSourceChainTransaction(); return born;
            }
            long now=System.currentTimeMillis(); need(now>=scope.receiptCreatedAt(),"IQC_BIRTH_CLOCK_INVALID");
            Birth raw=new Birth(tenantId,IdWorker.getId(),receiveId,materialId,scope.baseUomId(),scope.quantityScale(),
                scope.quantity(),scope.sha256(),null,now,d.getReceiveCode(),d.getMaterialCode(),d.getMaterialName(),d.getVendorId(),d.getVendorName());
            Birth born=new Birth(raw.tenantId(),raw.birthId(),raw.receiveId(),raw.materialId(),raw.baseUomId(),raw.quantityScale(),
                raw.quantity(),raw.receiptScopeSha256(),ReceiptIqcBirthSupport.birthHash(raw),raw.capturedAt(),raw.receiveCode(),
                raw.materialCode(),raw.materialName(),raw.vendorId(),raw.vendorName());
            ReceiptIqcBirthSupport.validate(born); need(mapper.insert(born)==1,"IQC_BIRTH_INSERT_CONFLICT");
            var saved=mapper.lockId(tenantId,born.birthId());
            need(saved!=null&&born.equals(saved.value()),"IQC_BIRTH_WRITEBACK_MISMATCH");
            qualification.requireSourceChainTransaction(); return born;
        });
    }
    /** 恢复只核实际永久登记，不要求终态订单重新批准，不新建或修改任何出生事实。 */
    @Override public Birth readPermanent(String tenantId, Long receiveId, Long materialId) {
        tenant(tenantId); need(positive(receiveId)&&positive(materialId),"IQC_BIRTH_IDENTITY_INVALID");
        return transactions.executeRepeatableRead(tenantId, () -> {
            qualification.requireSourceChainTransaction(); locks.lock(tenantId, List.of(materialId));
            IqcNativeSchema.requireErpBirthDefinition(mapper.columns(),mapper.indexes());
            var saved=mapper.lockScope(tenantId,receiveId,materialId);
            need(saved!=null,"IQC_BIRTH_REGISTRY_MISSING"); Birth born=saved.value();ReceiptIqcBirthSupport.validate(born);
            need(tenantId.equals(born.tenantId())&&receiveId.equals(born.receiveId())&&materialId.equals(born.materialId())
                &&born.capturedAt()<=System.currentTimeMillis(),"IQC_BIRTH_REGISTRY_CONFLICT");
            qualification.requireSourceChainTransaction(); return born;
        });
    }
    private static void tenant(String id) { need(TenantHelper.isEnable()&&id!=null&&id.matches("[0-9]{6,20}")
        &&id.equals(TenantHelper.getTenantId()),"IQC_TENANT_MISMATCH"); }
    private static boolean positive(Long id) { return id!=null&&id>0; }
    private static void need(boolean yes,String code) { if(!yes)throw new ServiceException(code); }
}

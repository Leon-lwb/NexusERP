package com.nexus.erp.ai.adapter;

import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedLine;
import com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader.CatalogSnapshot;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper.StoredBirth;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import static com.nexus.erp.ai.adapter.ErpCertifiedSourceBirthSupport.*;

/** 无持久写入的完整来源预览；借用专属ERP事务持当前读锁，但不授予写权限或写编号/guard。 */
@Service
public class ErpCertifiedSourcePreviewService {
    private final PuRequirementErpTransactionExecutor transactions;
    private final ErpPurchaseSourceCertificationService certification;
    public ErpCertifiedSourcePreviewService(PuRequirementErpTransactionExecutor transactions,ErpPurchaseSourceCertificationService certification){
        this.transactions=Objects.requireNonNull(transactions);this.certification=Objects.requireNonNull(certification);
    }
    public SourcePreview preview(PreviewRequest request){
        var actor=actor("pu:requisition:add");
        need(request!=null&&positive(request.materialId())&&positive(request.vendorId())&&request.windowEnd()!=null
            &&request.detailIds()!=null&&!request.detailIds().isEmpty()&&request.detailIds().size()<=1000
            &&request.detailIds().stream().allMatch(ErpCertifiedSourceBirthSupport::positive),"完整预览物料、供应商、窗口或MRP来源缺失");
        var details=Set.copyOf(request.detailIds());
        return transactions.executeRepeatableRead(actor.tenantId(),()->{
            recheck(actor);var scope=certification.previewLocked(actor.tenantId(),request.materialId(),request.vendorId(),request.windowEnd(),details);
            recheck(actor);
            return new SourcePreview(scope.materialId(),scope.vendorId(),scope.snapshotDate(),scope.windowEnd(),scope.catalog(),scope.sourceBirth(),
                scope.sourceBirth()==null?"ORIGINAL_STOCK_LINEAGE":"INITIAL_EMPTY_WITH_PERSISTENT_BIRTH",scope.lines(),scope.sourceVersions(),scope.sourceFingerprint());
        });
    }
    /** 仅身份和窗口输入，不接受数量、供应条款、出生事实或客户端摘要。 */
    public record PreviewRequest(Long materialId,Long vendorId,LocalDate windowEnd,Set<Long> detailIds) { }
    /** 正式来源全摘要由同一写前认证生成，出生摘要单独展示不能代替它。 */
    public record SourcePreview(Long materialId,Long vendorId,LocalDate snapshotDate,LocalDate windowEnd,CatalogSnapshot catalog,
            StoredBirth sourceBirth,String inventoryEvidence,List<CertifiedLine> lines,Map<String,String> sourceVersions,String sourceFingerprint){
        public SourcePreview { lines=List.copyOf(lines);sourceVersions=Map.copyOf(sourceVersions); }
    }
}

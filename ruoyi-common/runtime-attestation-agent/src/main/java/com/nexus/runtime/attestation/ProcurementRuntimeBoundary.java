package com.nexus.runtime.attestation;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 新 Born 实写与 V2 读回边界；不沿用仅覆盖旧采购类的发布证明。 */
public final class ProcurementRuntimeBoundary {
    public static final String VERSION = "PROCUREMENT_RECEIPT_IQC_BIRTH_V2";
    public static final List<String> REQUIRED_CLASSES = List.of(
        "com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService",
        "com.nexus.erp.pu.service.PuCertifiedPurchaseDocumentGuard",
        "com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService",
        "com.nexus.erp.pu.service.PuCertifiedPurchaseWorkflowService",
        "com.nexus.erp.pu.service.impl.PuPurchaseOrderServiceImpl",
        "com.nexus.erp.wm.service.impl.WmMaterialReceiveCrudServiceImpl",
        "com.nexus.erp.wm.service.impl.WmMaterialReceiveExecutorServiceImpl",
        "com.nexus.erp.mq.consumer.StockInConsumer",
        "com.nexus.erp.wm.service.impl.WmMqSendService",
        "com.nexus.erp.mq.message.StockInMessage",
        "com.nexus.erp.mq.consumer.PurchaseReceiveWritebackConsumer",
        "com.nexus.common.mq.consumer.BaseMessageConsumer",
        "com.nexus.erp.wm.service.WmReceiptIqcBirthService",
        "com.nexus.mes.qc.service.QcReceiptIqcBirthService",
        "com.nexus.erp.wm.service.WmReceiptIqcEvidenceService",
        "com.nexus.mes.qc.service.QcReceiptIqcEvidenceService",
        "com.nexus.erp.wm.service.WmCertifiedReceiptPostingService",
        "com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard",
        "com.nexus.erp.wm.service.PdaReceiveShelfAllocationService",
        "com.nexus.erp.wm.service.impl.PdaReceiveServiceImpl",
        "com.nexus.erp.mq.consumer.ErpIqcCompletedConsumer",
        "com.nexus.mes.common.mq.consumer.IqcAutoCreateConsumer",
        "com.nexus.common.mq.iqc.ReceiptIqcBirthSupport",
        "com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService",
        "com.nexus.erp.pu.service.PuCertifiedPurchaseReceiptBatchService"
    );

    private ProcurementRuntimeBoundary() { }
    public static String requiredClassSetSha256() {
        return Digests.of((String.join("\n", REQUIRED_CLASSES.stream().sorted().toList()) + "\n").getBytes(StandardCharsets.US_ASCII));
    }
}

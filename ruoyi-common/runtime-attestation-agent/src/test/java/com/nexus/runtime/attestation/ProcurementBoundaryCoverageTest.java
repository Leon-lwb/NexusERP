package com.nexus.runtime.attestation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 发布清单必须覆盖实际出生、MQ预检、上架及PDA写边界，不能只覆盖早期采购类。 */
class ProcurementBoundaryCoverageTest {
    @Test void allCurrentWriteAndRecoveryBoundariesMustBeRequired() {
        for(String name:List.of("com.nexus.erp.wm.service.WmCertifiedReceiptPostingService",
            "com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard","com.nexus.erp.wm.service.PdaReceiveShelfAllocationService",
            "com.nexus.erp.wm.service.impl.PdaReceiveServiceImpl","com.nexus.erp.mq.consumer.ErpIqcCompletedConsumer",
            "com.nexus.mes.common.mq.consumer.IqcAutoCreateConsumer","com.nexus.common.mq.iqc.ReceiptIqcBirthSupport",
            "com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService","com.nexus.erp.pu.service.PuCertifiedPurchaseReceiptBatchService")) {
            assertTrue(ProcurementRuntimeBoundary.REQUIRED_CLASSES.contains(name),name);
        }
    }
}

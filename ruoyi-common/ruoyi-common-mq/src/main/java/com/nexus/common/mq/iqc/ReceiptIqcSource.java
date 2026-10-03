package com.nexus.common.mq.iqc;

import java.math.BigDecimal;

/**
 * 内部只读 MES IQC 来源接口，供跨模块共享且不引入 ERP/MES 循环依赖。
 * 实现必须在自身独立的只读快照中读取 MES 当前已提交记录。
 * 调用方提供的已验证标记、合格结论、数量或单位均不构成授权。
 */
public interface ReceiptIqcSource {
    Inspection readCompleted(String tenantId, Long inspectionId);

    /** 当前已完成检验的身份和结果仅用于不合格保护；绝不提供数量、单位或合格授权。 */
    default Inspection readCompletedIdentity(String tenantId, Long inspectionId) {
        return readCompleted(tenantId, inspectionId);
    }

    /**
     * 未包含数量单位的原生观测，不是入库授权。V1 缺少原始数量单位及
     * 创建时的来源契约；绝不能用当前 ERP 单位或检验测量单位填补此缺口。
     */
    record Inspection(String tenantId, Long inspectionId, String inspectionCode,
                      Long receiveId, Long materialId, BigDecimal quantity,
                      String status, String result, Long inspectorId,
                      Long createdAt, Long completedAt, Long updatedAt,
                      long observedAt, String actualBodySha256, String sourceVersion) { }
}

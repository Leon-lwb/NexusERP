package com.nexus.common.mq.iqc;

import java.math.BigDecimal;

/** 内部出生来源写契约：从真实ERP记录准备快照，不接收客户端数量、单位或核验标志。 */
public interface ReceiptIqcBirthPort {
    Birth prepare(String tenantId, Long receiveId, Long materialId);

    /** 只读永久登记供已存在的原生IQC恢复；不能用此返回对象创建新检验。 */
    default Birth readPermanent(String tenantId, Long receiveId, Long materialId) {
        throw new com.nexus.common.core.exception.ServiceException("UNIT_ORIGIN_UNPROVEN");
    }

    /** 持久登记的原单位与范围；返回对象本身不授权，消费端必须重读原生登记。 */
    record Birth(String tenantId, Long birthId, Long receiveId, Long materialId,
                 Long baseUomId, Integer quantityScale, BigDecimal quantity,
                 String receiptScopeSha256, String birthSha256, long capturedAt,
                 String receiveCode, String materialCode, String materialName,
                 Long vendorId, String vendorName) { }
}

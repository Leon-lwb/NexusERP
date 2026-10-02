package com.nexus.erp.pu.domain.bo;

import java.time.LocalDate;
import java.util.List;

/** 客户端只提交来源身份和请求键；数量、单位、编号和租户全部由服务端决定。 */
public record PuCertifiedPurchaseCommand(String requestKey, Long materialId, Long vendorId,
    LocalDate windowEnd, List<Long> requirementDetailIds, String expectedSourceFingerprint) {
    public PuCertifiedPurchaseCommand {
        if (requirementDetailIds != null) requirementDetailIds = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(requirementDetailIds));
    }
}

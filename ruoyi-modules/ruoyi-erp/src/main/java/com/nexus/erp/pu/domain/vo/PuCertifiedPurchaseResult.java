package com.nexus.erp.pu.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 原事务实际提交后才可返回；replayed表示恢复原单据，不代表重新创建或重复占用。 */
public record PuCertifiedPurchaseResult(Long requisitionId, String requisitionNo, String status,
    Integer version, boolean replayed, String sourceFingerprint, List<Line> lines) {
    public PuCertifiedPurchaseResult { lines = List.copyOf(lines); }
    public record Line(Long lineId, Long requirementDetailId, Long materialId, Long baseUomId,
        BigDecimal applyQuantity, LocalDate requiredDate) { }
}

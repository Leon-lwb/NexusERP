package com.nexus.common.ai.decision.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 仅服务端从认证结果冻结的逐来源采购意图，不注册为任何控制器请求类型。
 * 冻结版本、数量和来源摘要不代替 ERP 在专属事务内的重新认证。
 */
public record DecisionPurchaseContractV2(
    Long materialId, Long vendorId, LocalDate windowEnd,
    String materialVersion, String vendorVersion,
    Long supplierTermsId, String supplierTermsVersion,
    Long baseUomId, String baseUomVersion,
    Long orderUomId, String orderUomVersion, String conversionVersion,
    List<Long> requirementDetailIds, List<Line> frozenLines,
    String expectedSourceFingerprint) {

    /** 防御复制输入集合；字段合法性统一由唯一 wire 编解码器校验。 */
    public DecisionPurchaseContractV2 {
        requirementDetailIds = requirementDetailIds == null ? null
            : Collections.unmodifiableList(new ArrayList<>(requirementDetailIds));
        frozenLines = frozenLines == null ? null : Collections.unmodifiableList(new ArrayList<>(frozenLines));
    }

    /** 行数量是冻结意图；写入仍须 ERP 重新认证并逐项比较。 */
    public record Line(Long detailId, Long baseUomId, BigDecimal coveredDemandBase,
                       Long orderUomId, BigDecimal requestedOrderQty, LocalDate requiredDate) { }
}

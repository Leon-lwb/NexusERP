package com.nexus.erp.pu.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import org.springframework.stereotype.Component;

/**
 * ERP 公共草稿边界。在可信需求、单位和账本契约完成集成之前，
 * AI 身份（包括内部调用方）不能构成创建草稿的授权。
 * 客户端提供的 run、action 或幂等键不是采购资格的证明。
 */
@Component
public class PuDecisionDraftReadinessGuard {

    public void require(PuPurchaseRequisitionBo bo) {
        if (bo == null) return; /** 服务保留常规输入校验。 */
        boolean claimsAi = "AI_DECISION".equalsIgnoreCase(StringUtils.trimToEmpty(bo.getSourceType()))
            || bo.getDecisionRunId() != null || bo.getDecisionActionId() != null
            || StringUtils.isNotBlank(bo.getDecisionIdempotencyKey());
        if (bo.getPuPurchaseRequisitionLineList() != null) {
            claimsAi |= bo.getPuPurchaseRequisitionLineList().stream().anyMatch(line -> line != null
                && "AI_DECISION".equalsIgnoreCase(StringUtils.trimToEmpty(line.getSourceType())));
        }
        if (claimsAi) {
            throw new ServiceException("AI采购来源占用及订购单位合同尚未核验，禁止创建草稿；请重新分析");
        }
    }
}
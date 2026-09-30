package com.nexus.erp.ai.adapter;

import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

/**
 * P0 ERP 边界采用校验失败即拒绝的策略。旧版采购提案既不包含可归因的需求明细行，
 * 也不包含经过核验的订购单位。仅有冻结版本标记绝不构成创建草稿的授权。
 */
@Component
public class ErpDecisionPurchaseGate {

    public void require(DecisionActionProposal action, DecisionActor actor) {
        /**
         * 仅当请求来源、单位以及 ERP 占用事务均已具备时，P1 可信来源校验器才可替换此默认拒绝逻辑；
         * 当前不据此宣称 P1 写入门禁已经开放。
         */
        throw new ServiceException("采购动作缺少可信需求来源及订购单位，禁止创建草稿；请重新分析");
    }
}
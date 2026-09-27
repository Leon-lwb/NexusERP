package com.nexus.erp.ai.adapter;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionFreshnessResult;
import com.nexus.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 审批前重算 ERP 关键业务指纹并判断决策结果是否过期。 */
@Component
@RequiredArgsConstructor
public class ErpDecisionFreshnessChecker {

    private final DeliveryContextAssembler assembler;

    public DecisionFreshnessResult check(DecisionBusinessRef ref, String expectedHash, DecisionActor actor) {
        if (expectedHash == null || expectedHash.isBlank()) {
            throw new ServiceException("预期快照哈希不能为空");
        }
        String current = assembler.computeFreshnessHash(ref, actor);
        boolean fresh = current.equals(expectedHash);
        return new DecisionFreshnessResult(
            fresh,
            current,
            fresh ? List.of() : List.of("criticalSnapshotHash"));
    }
}

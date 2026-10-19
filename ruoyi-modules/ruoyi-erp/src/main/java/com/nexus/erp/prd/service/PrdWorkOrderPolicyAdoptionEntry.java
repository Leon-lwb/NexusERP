package com.nexus.erp.prd.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * 原档策略采纳编排入口，按独立授权工件与预期版本提供预览和应用。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class PrdWorkOrderPolicyAdoptionEntry {
    private final PrdWorkOrderPolicyAdoptionService writer;
    private final PrdWorkOrderTrackingPolicyService bank;
    private final PrdWorkOrderPolicyAdoptionAuthorizer authorizer;

    @Transactional(rollbackFor = Exception.class)
    public String apply(Long id, String command, long expectedVersion) {
        return writer.adoptApproved(id, command, expectedVersion);
    }

    @Transactional(rollbackFor = Exception.class)
    public PrdWorkOrderTrackingPolicyService.AdoptionSource preview(Long woId) {
        authorizer.requireApprover();
        return bank.previewAdoption(woId);
    }
}

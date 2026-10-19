package com.nexus.erp.prd.service;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

/**
 * 先核验专用权限，再在工单父行锁内独立核对原档审批人。
 */
@Component
public class PrdWorkOrderPolicyAdoptionAuthorizer {
    public static final String PERMISSION = "prd:work:trackingPolicyAdopt";

    public Long requireApprover() {
        StpUtil.checkPermission(PERMISSION);
        Long actor = LoginHelper.getUserId();
        if (actor == null || actor <= 0) {
            throw new ServiceException("策略采纳需要真实认证审批人");
        }
        return actor;
    }
}

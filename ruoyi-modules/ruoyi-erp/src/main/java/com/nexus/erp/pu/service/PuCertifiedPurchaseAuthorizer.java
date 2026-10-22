package com.nexus.erp.pu.service;

import cn.dev33.satoken.stp.StpUtil;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;

import org.springframework.stereotype.Component;

import java.util.Set;

/** 每次新写与重放都核对当前身份，HTTP请求不能提供tenant/actor/permissions。 */
@Component
public class PuCertifiedPurchaseAuthorizer {
    public DecisionActor requireUser() {
        StpUtil.checkLogin();
        StpUtil.checkPermission("pu:requisition:add");
        String tenant = TenantHelper.getTenantId();
        Long userId = LoginHelper.getUserId();
        if (!TenantHelper.isEnable() || tenant == null || !tenant.matches("[0-9]{6,20}")
            || !tenant.equals(LoginHelper.getTenantId()) || userId == null || userId <= 0)
            throw new ServiceException("可信采购身份与当前租户不一致");
        return new DecisionActor(
                tenant, userId, LoginHelper.getUsername(), Set.of("pu:requisition:add"));
    }
}

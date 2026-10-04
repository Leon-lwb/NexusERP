package com.nexus.erp.wm.service;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class WmCertifiedReceiptPostingAuthorizer {
    public DecisionActor requireUser() { return require("wm:receive:execute"); }
    public DecisionActor requireShelf() { return require("wm:receive:shelf"); }
    /** 永久恢复读回只要求查询权限，不借用上架/执行身份。 */
    public DecisionActor requireQuery() { return require("wm:receive:query"); }
    private DecisionActor require(String permission) {
        StpUtil.checkLogin();
        StpUtil.checkPermission(permission);
        String tenant=TenantHelper.getTenantId(); Long user=LoginHelper.getUserId();
        if (!TenantHelper.isEnable() || tenant==null || !tenant.matches("[0-9]{6,20}")
            || !tenant.equals(LoginHelper.getTenantId()) || user==null || user<=0)
            throw new ServiceException("精确入库身份与当前租户不一致");
        return new DecisionActor(tenant,user,LoginHelper.getUsername(),Set.of(permission));
    }
}

package com.nexus.ai.engineering.security;

import static com.nexus.common.ai.engineering.EngineeringException.require;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.ai.engineering.config.EngineeringProperties;
import com.nexus.common.ai.engineering.*;
import com.nexus.common.ai.mapper.AiToolMapper;
import com.nexus.common.satoken.utils.LoginHelper;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 每次工程操作均读取当前用户、租户与权限，配对快照不能代替实时授权。 */
@Component
@RequiredArgsConstructor
@DS("master")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringAccess {
    private final EngineeringProperties properties;
    private final EngineeringIdentityPort identity;
    private final AiToolMapper tools;

    /** 从浏览器认证会话建立服务端身份。 */
    public EngineeringActor current(String permission, boolean toolRequired) {
        return requireActor(
                new EngineeringActor(LoginHelper.getTenantId(), LoginHelper.getUserId(), Set.of()),
                permission,
                toolRequired);
    }

    /** 复核当前身份及可选的工具开关，不信任传入权限快照。 */
    public EngineeringActor requireActor(
            EngineeringActor actor, String permission, boolean toolRequired) {
        require(properties.isEnabled(), 403, "ENGINEERING_DISABLED", "工程功能未启用");
        var status = owner(actor);
        require(status.enabled() && status.tenantEnabled(), 403, "OWNER_REVOKED", "用户或租户已停用，请重新配对");
        var fresh = new EngineeringActor(actor.tenantId(), actor.userId(), status.permissions());
        require(fresh.permits(permission), 403, "OWNER_REVOKED", "当前用户无工程操作权限");
        if (toolRequired)
            require(
                    tools.engineeringEnabled(actor.tenantId()) > 0,
                    403,
                    "TOOL_DISABLED",
                    "工程工具已停用");
        return fresh;
    }

    /** 查询内部认证状态，仅供凭证撤销比较。 */
    public EngineeringOwnerStatus owner(EngineeringActor actor) {
        require(
                actor != null && actor.tenantId() != null && actor.userId() != null,
                401,
                "OWNER_REVOKED",
                "缺少有效用户身份");
        var status = identity.resolve(actor.tenantId(), actor.userId());
        require(status != null, 403, "OWNER_REVOKED", "用户身份已失效");
        return status;
    }
}

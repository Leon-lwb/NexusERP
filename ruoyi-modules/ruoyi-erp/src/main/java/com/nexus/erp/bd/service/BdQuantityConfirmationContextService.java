package com.nexus.erp.bd.service;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.nexus.erp.bd.service.BdCatalogConfirmationSupport.*;

/**
 * 单位数量核验的纯登录上下文；不注入 Mapper、数据源、事务或写服务，不获取业务连接。
 * <p>复用既有资料确认的当前租户及操作者边界，并核对实际登录租户、姓名与部门元数据。
 * 权限布尔值仅供页面提示，不是认证凭据，不改变既有数量确认的编辑权限与服务端核验。</p>
 */
@Service
public class BdQuantityConfirmationContextService {
    /** 当前实际登录主体及数量确认按钮提示；不包含来源认证或客户端可回传的审计字段。 */
    public record Context(String tenantId, Long userId, boolean confirmAllowed) { }

    /**
     * 仅以 {@code bd:measure:query} 读取上下文，不依赖请购查询或数量编辑权限。
     * <p>当前用户必须处于有效租户且有完整登录元数据；允许未关联部门，不把缺失部门默认成零。
     * 核对权限提示前后再次检查主体，避免把动态租户或登录切换后的提示绑定到旧主体。</p>
     *
     * @return 当前租户、登录用户与编辑权限提示
     */
    public Context currentContext() {
        StpUtil.checkLogin();
        StpUtil.checkPermission("bd:measure:query");
        ActorContext actor = currentActor();
        String name = LoginHelper.getUsername();
        Long deptId = LoginHelper.getDeptId();
        require(actor.tenantId().length() <= 20 && actor.tenantId().equals(LoginHelper.getTenantId())
            && name != null && !name.isBlank() && name.length() <= 64
            && (deptId == null || deptId > 0), "单位数量上下文的实际登录租户或操作者元数据无效");
        checkActor(actor);
        boolean allowed = StpUtil.hasPermission("bd:measure:edit");
        checkActor(actor);
        require(actor.tenantId().equals(LoginHelper.getTenantId()) && name.equals(LoginHelper.getUsername())
            && Objects.equals(deptId, LoginHelper.getDeptId()), "读取单位数量上下文期间实际登录主体已改变");
        return new Context(actor.tenantId(), actor.actorId(), allowed);
    }
}

package com.nexus.ai.service.governance;

import com.nexus.common.report.ReportAuthorizationPort;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.CancellationException;

/** 恢复与继续执行时以持久化账号/角色为准，不使用线程里遗留的登录身份。 */
@Component
public class AiExecutionAuthorization {
    private final ObjectProvider<ReportAuthorizationPort> provider;

    public AiExecutionAuthorization(ObjectProvider<ReportAuthorizationPort> provider) {
        this.provider = provider;
    }

    public void require(String tenant, Long user, String permission) {
        var port = provider.getIfAvailable();
        if (port == null || user == null) throw new IllegalStateException("缺少可信执行授权适配器");
        var actor = port.resolve(tenant, user);
        if (actor == null
                || !tenant.equals(actor.tenantId())
                || actor.userId() != user
                || !actor.has(permission)) throw new CancellationException("执行权限已撤回");
    }
}

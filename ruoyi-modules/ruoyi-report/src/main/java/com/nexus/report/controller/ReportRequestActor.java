package com.nexus.report.controller;

import com.nexus.common.report.ReportActor;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.service.ReportDefinitionService;

import org.springframework.stereotype.Component;

/** 租户与用户来自已认证会话，不接受请求对象提供身份。 */
@Component
public class ReportRequestActor {
    /** 保存当前 {@code definitions} 值。 */
    private final ReportDefinitionService definitions;

    /**
     * 初始化报表请求身份并保存所需依赖或不可变值。
     *
     * @param definitions 当前操作的 {@code definitions} 输入
     */
    public ReportRequestActor(ReportDefinitionService definitions) {
        this.definitions = definitions;
    }

    /**
     * 完成 {@code current} 对应的报表请求身份处理。
     *
     * @return 当前处理返回的固定结果
     */
    public ReportActor current() {
        Long user = LoginHelper.getUserId();
        String tenant = TenantHelper.getTenantId();
        if (user == null || tenant == null || tenant.isBlank()) throw ReportFault.denied();
        return definitions.resolve(tenant, user);
    }
}

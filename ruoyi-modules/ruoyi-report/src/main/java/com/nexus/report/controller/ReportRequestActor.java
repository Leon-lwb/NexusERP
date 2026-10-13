package com.nexus.report.controller;
import com.nexus.common.report.ReportActor;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.service.ReportDefinitionService;
import org.springframework.stereotype.Component;
/** Tenant and user come from the authenticated session, never from request DTOs. */
@Component
public class ReportRequestActor {
    private final ReportDefinitionService definitions;
    public ReportRequestActor(ReportDefinitionService definitions){this.definitions=definitions;}
    public ReportActor current(){Long user=LoginHelper.getUserId();String tenant=TenantHelper.getTenantId();if(user==null||tenant==null||tenant.isBlank())throw ReportFault.denied();return definitions.resolve(tenant,user);}
}

package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.system.runtime.RuntimeAttestationProvider;
import com.nexus.system.runtime.RuntimeAttestationReport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 经过认证的只读诊断；不提供 apply 开关、证明标志、写入权限或执行路由。 */
@RestController
@RequestMapping("/system/runtime")
public final class SysRuntimeAttestationController {
    private final RuntimeAttestationProvider provider;
    public SysRuntimeAttestationController(RuntimeAttestationProvider provider) { this.provider = provider; }

    @GetMapping("/attestation")
    @SaCheckPermission(RuntimeAttestationProvider.PERMISSION)
    public R<RuntimeAttestationReport> attest(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        if (!"GET".equals(request.getMethod())) return R.fail(405, "ATTESTATION_GET_ONLY");
        Map<String, String[]> query = request.getParameterMap();
        String[] challenge = query.get("challenge");
        if (query.size() != 1 || challenge == null || challenge.length != 1
            || challenge[0] == null || !challenge[0].matches("[0-9a-f]{64}")
            || request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null) {
            return R.fail(400, "ATTESTATION_CHALLENGE_INVALID");
        }
        try {
            RuntimeAttestationReport report = provider.inspect(challenge[0]);
            R<RuntimeAttestationReport> result = R.fail(report.diagnosticCategory(), report);
            result.setCode(412); // 绝不返回 200：本阶段没有已加载构建证明的可信签发方。
            return result;
        } catch (RuntimeAttestationProvider.Rejected rejected) {
            return R.fail(rejected.code(), rejected.getMessage());
        }
    }
}

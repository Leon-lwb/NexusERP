package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.ai.service.decision.DecisionActionExecutionService;
import com.nexus.ai.service.decision.DecisionApprovalService;
import com.nexus.ai.service.decision.DecisionQueryService;
import com.nexus.ai.service.decision.DeliveryRiskDecisionOrchestrator;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionApprovalRequest;
import com.nexus.common.ai.decision.dto.DecisionRunAcceptedVo;
import com.nexus.common.ai.decision.dto.DeliveryRiskRunRequest;
import com.nexus.common.ai.domain.entity.AiDecisionEvidence;
import com.nexus.common.ai.domain.vo.AiDecisionRunVo;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工业决策接口，用户身份和租户权限均来自服务端登录会话。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/decision")
public class AiDecisionController {
    private final DeliveryRiskDecisionOrchestrator orchestrator;
    private final DecisionApprovalService approvals;
    private final DecisionActionExecutionService actions;
    private final DecisionQueryService queries;

    @SaCheckPermission("ai:decision:run")
    @PostMapping("/delivery-risk/run")
    public R<DecisionRunAcceptedVo> run(@Valid @RequestBody DeliveryRiskRunRequest request) {
        return R.ok(orchestrator.start(request, actor()));
    }

    @SaCheckPermission("ai:decision:query")
    @GetMapping("/run/list")
    public TableDataInfo<AiDecisionRunVo> list(@RequestParam(required = false) String status,
        @RequestParam(required = false) String riskLevel, PageQuery pageQuery) {
        DecisionActor current = actor();
        return TenantHelper.dynamic(current.tenantId(), () ->
            queries.list(current, status, riskLevel, pageQuery));
    }

    @SaCheckPermission("ai:decision:query")
    @GetMapping("/run/{runId}")
    public R<Map<String, Object>> detail(@PathVariable Long runId) {
        DecisionActor current = actor();
        return TenantHelper.dynamic(current.tenantId(), () -> R.ok(queries.view(runId, current)));
    }

    @SaCheckPermission("ai:decision:query")
    @GetMapping("/run/{runId}/evidence")
    public R<List<AiDecisionEvidence>> evidence(@PathVariable Long runId) {
        DecisionActor current = actor();
        return TenantHelper.dynamic(current.tenantId(), () -> R.ok(queries.evidence(runId, current)));
    }

    @SaCheckPermission("ai:decision:query")
    @GetMapping("/run/{runId}/timeline")
    public R<List<Map<String, Object>>> timeline(@PathVariable Long runId) {
        DecisionActor current = actor();
        return TenantHelper.dynamic(current.tenantId(), () -> R.ok(queries.timeline(runId, current)));
    }

    @SaCheckPermission("ai:decision:approve")
    @PostMapping("/run/{runId}/approve")
    public R<Void> approve(@PathVariable Long runId, @Valid @RequestBody DecisionApprovalRequest request) {
        approvals.approve(runId, request, actor());
        return R.ok();
    }

    @SaCheckPermission("ai:decision:approve")
    @PostMapping("/run/{runId}/reject")
    public R<Void> reject(@PathVariable Long runId, @RequestBody Map<String, String> request) {
        approvals.reject(runId, request == null ? null : request.get("reason"), actor());
        return R.ok();
    }

    @SaCheckPermission("ai:decision:run")
    @PostMapping("/run/{runId}/recalculate")
    public R<DecisionRunAcceptedVo> recalculate(@PathVariable Long runId) {
        DecisionActor current = actor();
        DeliveryRiskRunRequest request = TenantHelper.dynamic(current.tenantId(), () ->
            queries.recalculate(runId, current));
        return R.ok(orchestrator.start(request, current));
    }

    @SaCheckPermission("ai:decision:execute")
    @PostMapping("/action/{actionId}/retry")
    public R<Void> retry(@PathVariable Long actionId) {
        actions.retry(actionId, actor());
        return R.ok();
    }

    @SaCheckPermission("ai:decision:export")
    @GetMapping("/run/{runId}/export")
    public ResponseEntity<String> export(@PathVariable Long runId) {
        DecisionActor current = actor();
        String markdown = TenantHelper.dynamic(current.tenantId(), () ->
            queries.exportMarkdown(runId, current));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=decision-" + runId + ".md")
            .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
            .body(markdown);
    }

    private DecisionActor actor() {
        LoginUser user = LoginHelper.getLoginUser();
        String tenant = LoginHelper.getTenantId();
        Long userId = LoginHelper.getUserId();
        String name = LoginHelper.getUsername();
        if (user == null || tenant == null || !tenant.matches("[A-Za-z0-9_-]{1,20}")
            || userId == null || userId <= 0 || name == null || name.isBlank())
            throw new ServiceException("无法获取可信的决策登录身份");
        Set<String> rights = user.getMenuPermission();
        return new DecisionActor(tenant, userId, name, rights == null ? Set.of() : Set.copyOf(rights));
    }
}

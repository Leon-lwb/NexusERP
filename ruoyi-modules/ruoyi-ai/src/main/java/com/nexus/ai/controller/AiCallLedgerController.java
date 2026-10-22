package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.ai.service.governance.*;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/** 新台账与旧日统计分别提供，不汇总混合币种或伪造历史账单。 */
@RestController
@RequestMapping("/ai/token-usage")
@RequiredArgsConstructor
public class AiCallLedgerController {
    private final AiCallLedgerService ledger;
    private final AiGovernanceProperties properties;

    public record BudgetRequest(
            String budgetId,
            String currency,
            String scopeType,
            String scopeId,
            BigDecimal limitAmount,
            boolean enabled) {}

    public record ReconcileRequest(
            BigDecimal confirmedAmount, String currency, String reason, String idempotencyKey) {}

    private void enabled() {
        if (!properties.isEnabled()) throw new ServiceException("调用台账未启用，请先完成第二阶段数据库迁移", 503);
    }

    @SaCheckPermission("ai:tokenUsage:list")
    @GetMapping("/governance")
    public R<Map<String, Object>> capabilities() {
        return R.ok(
                Map.of(
                        "enabled",
                        properties.isEnabled(),
                        "historicalCostBasis",
                        "LEGACY_UNVERIFIED"));
    }

    @SaCheckPermission("ai:tokenUsage:query")
    @GetMapping("/calls")
    public R<List<Map<String, Object>>> calls(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String requestId,
            @RequestParam(defaultValue = "100") int limit) {
        enabled();
        return R.ok(wire(ledger.calls(LoginHelper.getTenantId(), userId, requestId, limit)));
    }

    @SaCheckPermission("ai:tokenUsage:list")
    @GetMapping("/cost-summary")
    public R<List<Map<String, Object>>> summary() {
        enabled();
        return R.ok(wire(ledger.summary(LoginHelper.getTenantId())));
    }

    @SaCheckPermission("ai:tokenUsage:list")
    @GetMapping("/budgets")
    public R<List<Map<String, Object>>> budgets() {
        enabled();
        return R.ok(wire(ledger.budgets(LoginHelper.getTenantId())));
    }

    @SaCheckPermission("ai:tokenUsage:budget")
    @PutMapping("/budgets")
    public R<Void> budget(@RequestBody BudgetRequest request) {
        enabled();
        ledger.configureBudget(
                LoginHelper.getTenantId(),
                request.budgetId(),
                request.currency(),
                request.scopeType(),
                Objects.toString(request.scopeId(), ""),
                request.limitAmount(),
                request.enabled(),
                LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission("ai:tokenUsage:reconcile")
    @PostMapping("/calls/{callId}/reconcile")
    public R<Void> reconcile(@PathVariable String callId, @RequestBody ReconcileRequest request) {
        enabled();
        ledger.reconcile(
                LoginHelper.getTenantId(),
                callId,
                request.confirmedAmount(),
                request.currency(),
                request.reason(),
                LoginHelper.getUserId(),
                request.idempotencyKey());
        return R.ok();
    }

    /** 明确字符串金额，并统一数据库列名大小写，避免不同驱动改变API字段。 */
    static List<Map<String, Object>> wire(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(
                        row -> {
                            var out = new LinkedHashMap<String, Object>();
                            row.forEach(
                                    (key, value) -> {
                                        var words = key.toLowerCase(Locale.ROOT).split("_");
                                        var name = new StringBuilder(words[0]);
                                        for (int i = 1; i < words.length; i++)
                                            name.append(Character.toUpperCase(words[i].charAt(0)))
                                                    .append(words[i].substring(1));
                                        out.put(
                                                name.toString(),
                                                value instanceof BigDecimal amount
                                                        ? amount.toPlainString()
                                                        : value);
                                    });
                            return (Map<String, Object>) out;
                        })
                .toList();
    }
}

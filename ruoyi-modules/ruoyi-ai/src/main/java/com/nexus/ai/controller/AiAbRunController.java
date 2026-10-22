package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.ai.service.governance.AiAbRunService;
import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/** A/B批次接口；所有资源同时校验租户和发起者。 */
@RestController
@RequestMapping("/ai/ab-test")
@RequiredArgsConstructor
public class AiAbRunController {
    private final AiAbRunService runs;

    public record StartRequest(String startKey) {}

    @SaCheckPermission("ai:abtest:list")
    @GetMapping("/capabilities")
    public R<Map<String, Object>> capabilities() {
        return R.ok(Map.of("runsEnabled", runs.enabled()));
    }

    @SaCheckPermission("ai:abtest:edit")
    @PostMapping("/{definitionId}/runs")
    public R<Map<String, Object>> start(
            @PathVariable Long definitionId, @RequestBody(required = false) StartRequest body) {
        String id =
                runs.start(
                        tenant(),
                        user(),
                        definitionId,
                        body == null || body.startKey() == null
                                ? UUID.randomUUID().toString()
                                : body.startKey());
        return info(id);
    }

    @SaCheckPermission("ai:abtest:list")
    @GetMapping("/runs")
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) Long definitionId) {
        return R.ok(AiCallLedgerController.wire(runs.list(tenant(), user(), definitionId)));
    }

    @SaCheckPermission("ai:abtest:query")
    @GetMapping("/runs/{runId}")
    public R<Map<String, Object>> info(@PathVariable String runId) {
        return R.ok(
                AiCallLedgerController.wire(List.of(runs.info(tenant(), user(), runId))).get(0));
    }

    @SaCheckPermission("ai:abtest:query")
    @GetMapping("/runs/{runId}/items")
    public R<List<Map<String, Object>>> items(@PathVariable String runId) {
        return R.ok(AiCallLedgerController.wire(runs.items(tenant(), user(), runId)));
    }

    @SaCheckPermission("ai:abtest:query")
    @GetMapping("/runs/{runId}/report")
    public R<Map<String, Object>> report(@PathVariable String runId) {
        var result = runs.report(tenant(), user(), runId);
        var out = new LinkedHashMap<String, Object>();
        out.put(
                "run",
                AiCallLedgerController.wire(List.of(runs.info(tenant(), user(), runId))).get(0));
        out.put(
                "models",
                AiCallLedgerController.wire((List<Map<String, Object>>) result.get("models")));
        out.put(
                "costs",
                AiCallLedgerController.wire((List<Map<String, Object>>) result.get("costs")));
        return R.ok(out);
    }

    @SaCheckPermission("ai:abtest:edit")
    @PostMapping("/runs/{runId}/cancel")
    public R<Map<String, Object>> cancel(@PathVariable String runId) {
        return R.ok(
                AiCallLedgerController.wire(List.of(runs.cancel(tenant(), user(), runId))).get(0));
    }

    @SaCheckPermission("ai:abtest:edit")
    @PostMapping("/runs/{runId}/resume")
    public R<Void> resume(@PathVariable String runId) {
        runs.resume(tenant(), user(), runId);
        return R.ok();
    }

    @SaCheckPermission("ai:abtest:edit")
    @PutMapping("/runs/{runId}/items/{itemId}/score")
    public R<Void> score(
            @PathVariable String runId, @PathVariable String itemId, @RequestParam Integer score) {
        runs.score(tenant(), user(), runId, itemId, score);
        return R.ok();
    }

    private String tenant() {
        return LoginHelper.getTenantId();
    }

    private Long user() {
        return LoginHelper.getUserId();
    }
}

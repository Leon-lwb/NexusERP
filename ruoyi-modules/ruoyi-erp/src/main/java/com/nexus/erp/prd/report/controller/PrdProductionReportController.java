package com.nexus.erp.prd.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.domain.R;
import com.nexus.erp.prd.report.service.PrdProductionReportServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RequiredArgsConstructor
@RestController
@DS("erp")
@RequestMapping("/erp/prd/report")
public class PrdProductionReportController {

    private final PrdProductionReportServiceImpl reportService;

    @SaCheckPermission("prd:workOrder:list")
    @GetMapping("/completionRate")
    public R<List<Map<String, Object>>> workOrderCompletionRate(
        @RequestParam(required = false) Integer months) {
        return R.ok(reportService.getWorkOrderCompletionRate(months));
    }

    @SaCheckPermission("prd:workOrder:list")
    @GetMapping("/efficiency")
    public R<List<Map<String, Object>>> productionEfficiency(
        @RequestParam(required = false) Integer months) {
        return R.ok(reportService.getProductionEfficiency(months));
    }
}

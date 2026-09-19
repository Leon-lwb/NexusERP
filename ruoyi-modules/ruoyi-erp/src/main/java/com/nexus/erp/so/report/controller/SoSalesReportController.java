package com.nexus.erp.so.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.erp.so.report.service.SoSalesReportServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/so/report")
public class SoSalesReportController {

    private final SoSalesReportServiceImpl reportService;

    @SaCheckPermission("so:orderheader:list")
    @GetMapping("/execution")
    public R<List<Map<String, Object>>> salesExecution(
        @RequestParam(required = false) Integer months) {
        return R.ok(reportService.getSalesExecution(months));
    }

    @SaCheckPermission("so:orderheader:list")
    @GetMapping("/customerRanking")
    public R<List<Map<String, Object>>> customerRanking(
        @RequestParam(required = false) Integer months,
        @RequestParam(required = false) Integer topN) {
        return R.ok(reportService.getCustomerRanking(months, topN));
    }
}

package com.nexus.erp.pu.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.erp.pu.report.service.PuPurchaseReportServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/pu/report")
public class PuPurchaseReportController {

    private final PuPurchaseReportServiceImpl reportService;

    @SaCheckPermission("pu:purchaseOrder:list")
    @GetMapping("/execution")
    public R<List<Map<String, Object>>> purchaseExecution(
        @RequestParam(required = false) Integer months) {
        return R.ok(reportService.getPurchaseExecution(months));
    }

    @SaCheckPermission("pu:purchaseOrder:list")
    @GetMapping("/deliveryRate")
    public R<List<Map<String, Object>>> vendorDeliveryRate(
        @RequestParam(required = false) Integer months) {
        return R.ok(reportService.getVendorDeliveryRate(months));
    }
}

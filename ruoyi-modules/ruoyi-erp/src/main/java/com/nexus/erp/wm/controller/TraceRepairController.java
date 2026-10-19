package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.wm.trace.TraceRepairAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/erp/wm/trace/repair") @RequiredArgsConstructor
public class TraceRepairController {
    private final TraceRepairAuditService audits;
    @SaCheckPermission("wm:trace:query") @GetMapping("/runs")
    public R<Map<String,Object>> runs(@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="10") int pageSize,
        @RequestParam(required=false) Long materialId,@RequestParam(required=false) String status) {
        return R.ok(audits.runs(pageNum,pageSize,materialId,status));
    }
    @SaCheckPermission("wm:trace:query") @GetMapping("/runs/{id}")
    public R<Map<String,Object>> detail(@PathVariable String id){return R.ok(audits.detail(id));}
    @SaCheckPermission("wm:trace:audit") @PostMapping("/audit")
    public R<Map<String,Object>> audit(@RequestBody(required=false) TraceRepairAuditService.Request request){return R.ok(audits.audit(request));}
}

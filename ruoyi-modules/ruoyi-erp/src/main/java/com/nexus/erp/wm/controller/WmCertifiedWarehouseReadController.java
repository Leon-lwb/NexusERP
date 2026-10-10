package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.wm.service.WmCertifiedWarehouseReadService;
import com.nexus.erp.wm.service.WmCertifiedWarehouseReadService.Context;
import com.nexus.erp.wm.service.WmCertifiedWarehouseReadService.Detail;
import org.springframework.web.bind.annotation.*;

/** 分领域 query-only 工作台入口；不新增或调用任何业务写操作。 */
@RestController
@RequestMapping("/erp/wm")
public class WmCertifiedWarehouseReadController {
    private final WmCertifiedWarehouseReadService reads;
    public WmCertifiedWarehouseReadController(WmCertifiedWarehouseReadService reads) { this.reads=reads; }
    @GetMapping("/arrivalnotice/certified/workflow-context")
    @SaCheckPermission("wm:arrivalnotice:query")
    public R<Context> arrivalContext() { return R.ok(reads.arrivalContext()); }
    @GetMapping("/receive/certified/workflow-context")
    @SaCheckPermission("wm:receive:query")
    public R<Context> receiveContext() { return R.ok(reads.receiveContext()); }
    @GetMapping("/arrivalnotice/certified/order/{id}/workflow-detail")
    @SaCheckPermission("wm:arrivalnotice:query")
    public R<Detail> arrivalOrder(@PathVariable Long id) { return R.ok(reads.arrivalOrder(id)); }
    @GetMapping("/receive/certified/order/{id}/workflow-detail")
    @SaCheckPermission("wm:receive:query")
    public R<Detail> receiveOrder(@PathVariable Long id) { return R.ok(reads.receiveOrder(id)); }
    @GetMapping("/arrivalnotice/certified/{id}/workflow-detail")
    @SaCheckPermission("wm:arrivalnotice:query")
    public R<Detail> arrivalDetail(@PathVariable Long id) { return R.ok(reads.withWorkflow(reads.arrivalDetail(id))); }
    @GetMapping("/receive/certified/{id}/workflow-detail")
    @SaCheckPermission("wm:receive:query")
    public R<Detail> receiveDetail(@PathVariable Long id) { return R.ok(reads.withWorkflow(reads.receiveDetail(id))); }
}

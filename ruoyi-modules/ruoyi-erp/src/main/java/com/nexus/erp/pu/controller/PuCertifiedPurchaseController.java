package com.nexus.erp.pu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.erp.pu.domain.bo.PuCertifiedPurchaseCommand;
import com.nexus.erp.pu.domain.vo.PuCertifiedPurchaseResult;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService;
import org.springframework.web.bind.annotation.*;

/** 统一来源采购的实际API：不暴露租户、单据编号、供应量或verified参数。 */
@RestController
@RequestMapping("/erp/pu/requisition/certified")
public class PuCertifiedPurchaseController {
    private final PuCertifiedPurchaseWriteService writer;
    public PuCertifiedPurchaseController(PuCertifiedPurchaseWriteService writer) { this.writer=writer; }
    /** 页面跨标签页恢复前读取实际请求主体，不接受缓存身份作为证明。 */
    @GetMapping("/context")
    @SaCheckPermission("pu:requisition:add")
    public R<ActorContext> context() {
        var actor = writer.currentActorContext();
        return R.ok(new ActorContext(actor.tenantId(), actor.userId()));
    }
    public record ActorContext(String tenantId, Long userId) { }

    @PostMapping
    @SaCheckPermission("pu:requisition:add")
    @Log(title="可信来源采购申请",businessType=BusinessType.INSERT)
    public R<PuCertifiedPurchaseResult> create(@RequestBody PuCertifiedPurchaseCommand command) {
        return R.ok(writer.createManual(command));
    }
    @PostMapping("/mrp")
    @SaCheckPermission("pu:requisition:add")
    @Log(title="可信MRP采购申请",businessType=BusinessType.INSERT)
    public R<PuCertifiedPurchaseResult> fromMrp(@RequestBody PuCertifiedPurchaseCommand command) {
        return R.ok(writer.createMrp(command));
    }
    @PostMapping("/low-stock")
    @SaCheckPermission("pu:requisition:add")
    @Log(title="可信低库存来源采购申请",businessType=BusinessType.INSERT)
    public R<PuCertifiedPurchaseResult> fromLowStock(@RequestBody PuCertifiedPurchaseCommand command) {
        return R.ok(writer.createLowStock(command));
    }
}

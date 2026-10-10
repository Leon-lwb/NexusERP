package com.nexus.erp.pu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderReadService;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 认证订单页面只读入口；查询不需要报价、批准或关闭权限，不复用任何写重放。 */
@Validated
@RestController
@RequestMapping("/erp/pu/order/certified")
public class PuCertifiedPurchaseOrderReadController {
    private final PuCertifiedPurchaseOrderWorkflowService workflow;
    private final PuCertifiedPurchaseOrderReadService reads;
    public PuCertifiedPurchaseOrderReadController(PuCertifiedPurchaseOrderWorkflowService workflow,
        PuCertifiedPurchaseOrderReadService reads) { this.workflow = workflow; this.reads = reads; }

    /** @return 实际登录主体及按钮提示，不获取采购业务连接 */
    @SaCheckPermission("pu:order:query")
    @GetMapping("/workflow-context")
    public R<PuCertifiedPurchaseOrderWorkflowService.WorkflowContext> context() {
        return R.ok(workflow.currentWorkflowContext());
    }

    /** @return 原生订单、NULL历史版本和按权限可见的不可变报价 */
    @SaCheckPermission("pu:order:query")
    @GetMapping("/{id}/workflow-detail")
    public R<PuCertifiedPurchaseOrderReadService.Detail> detail(@PathVariable @Positive Long id) {
        return R.ok(reads.readDetail(id));
    }
}

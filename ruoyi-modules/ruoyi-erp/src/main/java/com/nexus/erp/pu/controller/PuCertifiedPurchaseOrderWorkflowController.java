package com.nexus.erp.pu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 真实订单入口；不启用外层事务、不接受客户端数量/租户/verified；处置中的原操作人仅作登录主体一致性断言。 */
@Validated
@RestController
@RequestMapping("/erp/pu/order/certified")
public class PuCertifiedPurchaseOrderWorkflowController {
    private final PuCertifiedPurchaseOrderWorkflowService service;
    public PuCertifiedPurchaseOrderWorkflowController(PuCertifiedPurchaseOrderWorkflowService service){this.service=service;}
    public record ConfirmRequest(@NotBlank String requestKey,@NotBlank String quoteHash,@NotBlank @Size(max=500) String reviewNote) { }
    public record ApproveRequest(@NotNull @PositiveOrZero Integer expectedVersion,@NotNull @Positive Long quoteId,@NotBlank String requestKey) { }
    public record VersionRequest(@NotNull @PositiveOrZero Integer expectedVersion,@NotBlank String requestKey) { }
    @SaCheckPermission("pu:order:terms:add")
    @PostMapping("/{id}/terms")
    public R<QuoteResult> recordTerms(@PathVariable @Positive Long id,@Valid @RequestBody SupplierTermsRequest body){return R.ok(service.recordTerms(id,body));}
    /**
     * 显式核对原报价未知结果；本次处置审计不声明历史未知请求已成功。
     *
     * @param id 原采购订单 ID
     * @param body 原请求全文及显式处置模式
     * @return 只读核对、既有提交或本次新录入的区分结果
     */
    @SaCheckPermission({"pu:order:terms:add","pu:order:query"})
    @Log(title="原供应报价显式核对处置", businessType=BusinessType.OTHER)
    @PostMapping("/{id}/terms/reconcile")
    public R<TermsRecoveryResult> reconcileTerms(@PathVariable @Positive Long id,
        @Valid @RequestBody TermsRecoveryRequest body) {
        return R.ok(service.reconcileTerms(id,body));
    }
    @SaCheckPermission("pu:order:terms:confirm")
    @PostMapping("/{id}/terms/{quoteId}/confirm")
    public R<QuoteResult> confirmTerms(@PathVariable @Positive Long id,@PathVariable @Positive Long quoteId,@Valid @RequestBody ConfirmRequest body){return R.ok(service.confirmTerms(id,quoteId,body.requestKey(),body.quoteHash(),body.reviewNote()));}
    @SaCheckPermission({"pu:order:approve","pu:order:price"})
    @PostMapping("/{id}/approve")
    public R<Result> approve(@PathVariable @Positive Long id,@Valid @RequestBody ApproveRequest body){return R.ok(service.approve(id,body.expectedVersion(),body.quoteId(),body.requestKey()));}
    @SaCheckPermission("pu:order:close")
    @PostMapping("/{id}/close")
    public R<Result> close(@PathVariable @Positive Long id,@Valid @RequestBody VersionRequest body){return R.ok(service.close(id,body.expectedVersion(),body.requestKey()));}
    @SaCheckPermission("pu:order:cancel")
    @PostMapping("/{id}/cancel")
    public R<Result> cancel(@PathVariable @Positive Long id,@Valid @RequestBody VersionRequest body){return R.ok(service.cancel(id,body.expectedVersion(),body.requestKey()));}
}

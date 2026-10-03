package com.nexus.erp.pu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 真实订单入口；不启用外层事务，也不接受调用方提供的数量、租户、操作人或 verified 标志。 */
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

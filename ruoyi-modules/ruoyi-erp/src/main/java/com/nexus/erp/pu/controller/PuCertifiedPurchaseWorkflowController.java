package com.nexus.erp.pu.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWorkflowService;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWorkflowService.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** No outer @Transactional: dedicated ERP transaction ownership must remain provable. */
@Validated
@RestController
@RequestMapping("/erp/pu/requisition/certified")
public class PuCertifiedPurchaseWorkflowController {
    private final PuCertifiedPurchaseWorkflowService service;
    public PuCertifiedPurchaseWorkflowController(PuCertifiedPurchaseWorkflowService service) {this.service=service;}
    public record VersionRequest(@NotNull @PositiveOrZero Integer expectedVersion) { }
    @SaCheckPermission("pu:requisition:approve")
    @PostMapping("/{id}/approve")
    public R<Result> approve(@PathVariable @Positive Long id,@Valid @RequestBody VersionRequest body) {return R.ok(service.approve(id,body.expectedVersion()));}
    @SaCheckPermission("pu:requisition:convert")
    @PostMapping("/{id}/convert")
    public R<Result> convert(@PathVariable @Positive Long id,@Valid @RequestBody VersionRequest body) {return R.ok(service.convert(id,body.expectedVersion()));}
    @SaCheckPermission("pu:requisition:close")
    @PostMapping("/{id}/close")
    public R<Result> close(@PathVariable @Positive Long id,@Valid @RequestBody VersionRequest body) {return R.ok(service.close(id,body.expectedVersion()));}
    @SaCheckPermission("pu:requisition:cancel")
    @PostMapping("/{id}/cancel")
    public R<Result> cancel(@PathVariable @Positive Long id,@Valid @RequestBody VersionRequest body) {return R.ok(service.cancel(id,body.expectedVersion()));}
}

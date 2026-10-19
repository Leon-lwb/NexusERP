package com.nexus.erp.prd.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.prd.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
/** Only an already approved immutable authorization ID may enter the ERP apply transaction. */
@RestController @RequestMapping("/erp/prd/work/tracking-policy") @Validated @RequiredArgsConstructor
public class PrdWorkOrderPolicyAdoptionController {
 private final PrdWorkOrderPolicyAdoptionEntry entry;
 public record Command(@NotNull @Positive Long adoptionId,@NotBlank @Size(max=128) String commandId,@Positive long expectedVersion) { }
 public record Result(String snapshotHash) { }
 @PostMapping("/adopt") @SaCheckPermission(PrdWorkOrderPolicyAdoptionAuthorizer.PERMISSION)
 public R<Result> apply(@Valid @RequestBody Command command) {return R.ok(new Result(entry.apply(command.adoptionId(),command.commandId(),command.expectedVersion())));}
 @GetMapping("/preview/{woId}") @SaCheckPermission(PrdWorkOrderPolicyAdoptionAuthorizer.PERMISSION)
 public R<PrdWorkOrderTrackingPolicyService.AdoptionSource> preview(@PathVariable @Positive Long woId) {return R.ok(entry.preview(woId));}
}

package com.nexus.erp.prd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.prd.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 库存采纳事务只接受已获独立审批且不可变的授权记录标识。
 */
@RestController
@RequestMapping("/erp/prd/work/tracking-policy")
@Validated
@RequiredArgsConstructor
public class PrdWorkOrderPolicyAdoptionController {
    private final PrdWorkOrderPolicyAdoptionEntry entry;

    /**
     * 引用独立审批工件的原档策略采纳命令。
     *
     * @param adoptionId 已独立批准的策略采纳工件标识。
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param expectedVersion 调用方预期的原记录版本，用于并发条件校验。
     */
    public record Command(
            @NotNull @Positive Long adoptionId,
            @NotBlank @Size(max = 128) String commandId,
            @Positive long expectedVersion) {}

    /**
     * 原档策略采纳后的冻结快照摘要。
     *
     * @param snapshotHash 冻结策略或业务快照的完整摘要。
     */
    public record Result(String snapshotHash) {}

    @PostMapping("/adopt")
    @SaCheckPermission(PrdWorkOrderPolicyAdoptionAuthorizer.PERMISSION)
    public R<Result> apply(@Valid @RequestBody Command command) {
        return R.ok(
                new Result(
                        entry.apply(
                                command.adoptionId(),
                                command.commandId(),
                                command.expectedVersion())));
    }

    @GetMapping("/preview/{woId}")
    @SaCheckPermission(PrdWorkOrderPolicyAdoptionAuthorizer.PERMISSION)
    public R<PrdWorkOrderTrackingPolicyService.AdoptionSource> preview(
            @PathVariable @Positive Long woId) {
        return R.ok(entry.preview(woId));
    }
}

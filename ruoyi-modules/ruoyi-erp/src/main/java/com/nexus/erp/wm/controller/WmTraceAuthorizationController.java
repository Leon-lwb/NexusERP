package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.wm.domain.bo.WmTraceAuthorizationBo;
import com.nexus.erp.wm.service.WmTraceAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 实际来源额度授权入口，核对原单、身份和数量后提供制造使用凭据。
 */
@RestController
@RequestMapping("/erp/wm/trace-authorization")
@RequiredArgsConstructor
public class WmTraceAuthorizationController {
    private final com.nexus.erp.wm.trace.TraceAuthorizationOrchestrator authorizations;

    @PostMapping("/production-lot")
    @SaCheckPermission("wm:productionrecpt:add")
    public R<WmTraceAuthorizationService.Authorization> lot(
            @Valid @RequestBody WmTraceAuthorizationBo bo) {
        return R.ok(authorizations.productionLot(bo));
    }

    @PostMapping("/backflush-reservation")
    @SaCheckPermission("wm:productionissue:edit")
    public R<java.util.List<WmTraceAuthorizationService.Authorization>> reserve(
            @Valid @RequestBody WmTraceAuthorizationBo bo) {
        return R.ok(authorizations.reserveBackflush(bo));
    }
}

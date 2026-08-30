package com.nexus.mes.pda.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.mes.pda.annotation.PdaIdempotent;
import com.nexus.mes.pda.domain.bo.PdaSnBindBo;
import com.nexus.mes.pda.domain.bo.PdaSnUnbindBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaSnBindingVo;
import com.nexus.mes.pda.service.IPdaSnService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDA SN 绑定/解绑接口 (登录态)
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/mes/sn")
public class AppPdaSnController {

    private final IPdaSnService pdaSnService;

    /** 按 SN 查询绑定 (未绑定 data=null) */
    @SaCheckPermission("mes:lt:binding:query")
    @GetMapping("/query")
    public R<PdaSnBindingVo> query(@RequestParam String sn) {
        return R.ok(pdaSnService.query(sn));
    }

    /** SN 绑定批次 (幂等键: SN码:0:bind) */
    @SaCheckPermission("mes:lt:binding:add")
    @PdaIdempotent
    @Log(title = "PDA SN绑定", businessType = BusinessType.INSERT)
    @PostMapping("/bind")
    public R<MesActionResultVo> bind(@Validated @RequestBody @NotNull PdaSnBindBo bo) {
        return R.ok(pdaSnService.bind(bo));
    }

    /** SN 解绑 (幂等键: SN码:0:unbind) */
    @SaCheckPermission("mes:lt:binding:remove")
    @PdaIdempotent
    @Log(title = "PDA SN解绑", businessType = BusinessType.DELETE)
    @PostMapping("/unbind")
    public R<MesActionResultVo> unbind(@Validated @RequestBody @NotNull PdaSnUnbindBo bo) {
        return R.ok(pdaSnService.unbind(bo));
    }
}

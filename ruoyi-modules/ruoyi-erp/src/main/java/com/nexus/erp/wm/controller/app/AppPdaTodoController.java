package com.nexus.erp.wm.controller.app;

import com.nexus.common.core.domain.R;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoSummaryVo;
import com.nexus.erp.wm.service.IPdaTodoService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDA 任务中心待办聚合接口 (登录态)
 * <p>
 * 作业台首屏拉取: 各流程可作业单据计数, 按权限点过滤可见流程。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/todo")
public class AppPdaTodoController {

    private final IPdaTodoService pdaTodoService;

    /** 待办聚合 */
    @GetMapping("/summary")
    public R<PdaTodoSummaryVo> summary() {
        return R.ok(pdaTodoService.summary());
    }
}

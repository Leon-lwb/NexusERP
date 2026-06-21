package com.nexus.mes.pro.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.pro.domain.vo.TraceabilityResult;
import com.nexus.mes.pro.service.IProTraceabilityService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 组件追溯 Controller
 * <p>
 * 基于MBOM快照 + SN装配链实现组件级正反向追溯。
 * 反向: 成品SN/工单 -> 所需组件清单 + 实际装配组件SN
 * 正向: 组件物料/SN -> 消费该组件的成品工单 + 父SN链
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/pro/trace")
public class ProTraceabilityController extends BaseController {

    private final IProTraceabilityService traceabilityService;

    /**
     * 反向追溯: 成品工单/SN -> 组件清单 + 实际装配组件SN
     */
    @SaCheckPermission("mes:pro:trace:query")
    @GetMapping("/backward")
    public R<TraceabilityResult> backward(@RequestParam Long mesWoId,
                                           @RequestParam(required = false) String snCode) {
        TraceabilityResult result = traceabilityService.backward(mesWoId, snCode);
        return R.ok(result);
    }

    /**
     * 正向追溯: 组件物料/SN -> 消费该组件的成品工单
     */
    @SaCheckPermission("mes:pro:trace:query")
    @GetMapping("/forward")
    public R<List<TraceabilityResult>> forward(
            @RequestParam(required = false) Long componentMaterialId,
            @RequestParam(required = false) String componentSnCode) {
        List<TraceabilityResult> results = traceabilityService.forward(componentMaterialId, componentSnCode);
        return R.ok(results);
    }
}

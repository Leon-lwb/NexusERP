package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.wm.domain.vo.WmTraceResultVo;
import com.nexus.erp.wm.service.IWmTraceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 追溯查询 Controller
 * <p>
 * 提供物料追溯查询的 RESTful API 接口，支持正向追溯和反向追溯。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/trace")
public class WmTraceController extends BaseController {

    /** 追溯业务服务，提供追溯相关能力 */
    private final IWmTraceService wmTraceService;

    /**
     * 正向追溯（原材料 → 成品）
     * <p>
     * 权限标识：{@code wm:trace:query}</p>
     *
     * @param materialId 物料ID
     * @param batchCode  批次编码（可选）
     * @param snCode     序列号编码（可选）
     * @return 正向追溯结果
     */
    @SaCheckPermission("wm:trace:query")
    @Log(title = "正向追溯", businessType = BusinessType.OTHER)
    @GetMapping("/forward")
    public R<WmTraceResultVo> traceForward(@RequestParam Long materialId,
                                            @RequestParam(required = false) String batchCode,
                                            @RequestParam(required = false) String snCode) {
        return R.ok(wmTraceService.traceForward(materialId, batchCode, snCode));
    }

    /**
     * 反向追溯（成品 → 原材料）
     * <p>
     * 权限标识：{@code wm:trace:query}</p>
     *
     * @param materialId 物料ID
     * @param batchCode  批次编码（可选）
     * @param snCode     序列号编码（可选）
     * @return 反向追溯结果
     */
    @SaCheckPermission("wm:trace:query")
    @Log(title = "反向追溯", businessType = BusinessType.OTHER)
    @GetMapping("/backward")
    public R<WmTraceResultVo> traceBackward(@RequestParam Long materialId,
                                             @RequestParam(required = false) String batchCode,
                                             @RequestParam(required = false) String snCode) {
        return R.ok(wmTraceService.traceBackward(materialId, batchCode, snCode));
    }
}

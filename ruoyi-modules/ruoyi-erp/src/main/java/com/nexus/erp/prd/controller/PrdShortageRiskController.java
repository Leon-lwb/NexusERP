package com.nexus.erp.prd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.prd.domain.vo.PrdShortageRiskVo;
import com.nexus.erp.prd.service.IPrdShortageRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应链缺料风险预测 Controller
 * <p>
 * MRP 毛需求 + 库存(扣预留) + 安全库存 + 在途采购 → 未来 N 天逐日缺料推演。
 * 推演型接口, 不落库, 结果缓存 30 分钟。
 * </p>
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/shortage")
public class PrdShortageRiskController extends BaseController {

    private final IPrdShortageRiskService shortageRiskService;

    /**
     * 缺料风险预测: 按最早缺料日升序返回风险清单
     *
     * @param days 推演天数(默认30, 上限180)
     */
    @SaCheckPermission("prd:shortage:query")
    @GetMapping("/predict")
    public R<List<PrdShortageRiskVo>> predict(@RequestParam(required = false, defaultValue = "30") Integer days) {
        int horizon = (days == null || days <= 0) ? 30 : Math.min(days, 180);
        return R.ok(shortageRiskService.predict(horizon));
    }

    /**
     * 强制刷新: 跳过缓存重新推演
     */
    @SaCheckPermission("prd:shortage:query")
    @GetMapping("/predict/refresh")
    public R<List<PrdShortageRiskVo>> predictRefresh(@RequestParam(required = false, defaultValue = "30") Integer days) {
        int horizon = (days == null || days <= 0) ? 30 : Math.min(days, 180);
        return R.ok(shortageRiskService.predictRefresh(horizon));
    }
}

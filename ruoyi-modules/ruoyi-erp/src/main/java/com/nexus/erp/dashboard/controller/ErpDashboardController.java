package com.nexus.erp.dashboard.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.dashboard.domain.vo.ErpDashboardStatsVo;
import com.nexus.erp.dashboard.service.IErpDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页工作台统计 Controller
 * <p>只读聚合, 不设菜单权限点: 任何已登录用户(租户内)可查看,
 * 数据按租户自动隔离, 不含明细级敏感数据。</p>
 *
 * @author Nexus
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/dashboard")
public class ErpDashboardController extends BaseController {

    private final IErpDashboardService dashboardService;

    /**
     * 获取首页统计数据(本月销售/进行中订单/客户/物料/库存预警/最新订单/销售趋势)
     *
     * @return 统计数据
     */
    @GetMapping("/stats")
    public R<ErpDashboardStatsVo> getStats() {
        return R.ok(dashboardService.getStats());
    }
}

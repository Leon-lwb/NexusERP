package com.nexus.erp.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
 * <p>只读聚合。含全租户销售额/订单额, 属经营数据, <b>必须带权限码</b>(ERP-DASH-01):
 * 原实现无 {@code @SaCheckPermission}, 任意登录用户即可看到全租户本月销售额。
 * 权限码 {@code erp:dashboard:stats} 的 {@code sys_menu} 新装种子见
 * {@code script/sql/ry-vue.sql}(非超管需角色授权后方可见；存量库另行审核迁移)。</p>
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
    @SaCheckPermission("erp:dashboard:stats")
    @GetMapping("/stats")
    public R<ErpDashboardStatsVo> getStats() {
        return R.ok(dashboardService.getStats());
    }
}

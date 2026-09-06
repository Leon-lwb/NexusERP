package com.nexus.erp.dashboard.service;

import com.nexus.erp.dashboard.domain.vo.ErpDashboardStatsVo;

/**
 * 首页工作台统计 Service 接口
 *
 * @author Nexus
 */
public interface IErpDashboardService {

    /**
     * 聚合首页统计数据(销售/主数据/库存预警/趋势)
     *
     * @return 统计结果
     */
    ErpDashboardStatsVo getStats();
}

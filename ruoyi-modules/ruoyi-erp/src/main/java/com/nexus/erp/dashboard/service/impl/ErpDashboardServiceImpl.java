package com.nexus.erp.dashboard.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nexus.erp.bd.domain.BdClient;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdClientMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.dashboard.domain.vo.ErpDashboardStatsVo;
import com.nexus.erp.dashboard.service.IErpDashboardService;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.service.ISoSalesOrderHeaderService;
import com.nexus.erp.wm.domain.WmStockAlert;
import com.nexus.erp.wm.mapper.WmStockAlertMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页工作台统计 Service 实现
 * <p>全部走单条聚合 SQL / 有限行查询, 数据由多租户插件自动隔离;
 * 端点仅要求登录态(无菜单权限点), 供所有已登录用户首页展示。
 * <p>数据源: ERP 业务表(销售/主数据/库存预警)在 ry-erp 库, 类级 {@code @DS("erp")} 路由,
 * 不标注会落到默认 master(ry-vue) 导致 Table doesn't exist。</p>
 *
 * @author Nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class ErpDashboardServiceImpl implements IErpDashboardService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SoSalesOrderHeaderMapper soHeaderMapper;
    private final BdClientMapper clientMapper;
    private final BdMaterialMapper materialMapper;
    private final WmStockAlertMapper stockAlertMapper;

    @Override
    public ErpDashboardStatsVo getStats() {
        ErpDashboardStatsVo vo = new ErpDashboardStatsVo();
        fillMonthlySummary(vo);
        fillCounts(vo);
        vo.setRecentOrders(selectRecentOrders());
        vo.setStockAlerts(selectTopStockAlerts());
        vo.setSalesTrend(selectSalesTrend());
        return vo;
    }

    /** 本月销售额/单数 + 进行中订单数 */
    private void fillMonthlySummary(ErpDashboardStatsVo vo) {
        Date monthStart = Date.from(LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        Map<String, Object> monthly = soHeaderMapper.selectMaps(new QueryWrapper<SoSalesOrderHeader>()
            .select("IFNULL(SUM(total_amount), 0) AS stat_amount", "COUNT(*) AS stat_count")
            .ge("create_time", monthStart)
            // order_status 列允许 NULL(SQL 三值逻辑 ne 会漏掉 NULL 行), 手工/历史数据可能无状态, 一并计入
            .and(w -> w.ne("order_status", ISoSalesOrderHeaderService.STATUS_CANCELLED)
                .or().isNull("order_status"))).stream()
            .findFirst().orElse(Map.of());
        vo.setMonthSalesAmount(asBigDecimal(monthly.get("stat_amount"), BigDecimal.ZERO));
        vo.setMonthSalesCount(asLong(monthly.get("stat_count"), 0L));

        vo.setOpenSalesOrderCount(soHeaderMapper.selectCount(new LambdaQueryWrapper<SoSalesOrderHeader>()
            .in(SoSalesOrderHeader::getOrderStatus,
                ISoSalesOrderHeaderService.STATUS_DRAFT,
                ISoSalesOrderHeaderService.STATUS_APPROVING,
                ISoSalesOrderHeaderService.STATUS_APPROVED,
                ISoSalesOrderHeaderService.STATUS_CONFIRMED)));
    }

    /** 主数据与预警计数 */
    private void fillCounts(ErpDashboardStatsVo vo) {
        vo.setClientCount(clientMapper.selectCount(null));
        vo.setMaterialCount(materialMapper.selectCount(null));
        vo.setStockAlertCount(stockAlertMapper.selectCount(new LambdaQueryWrapper<WmStockAlert>()
            .eq(WmStockAlert::getHandledFlag, 0)));
    }

    /** 最新订单前5 */
    private List<ErpDashboardStatsVo.RecentOrder> selectRecentOrders() {
        List<SoSalesOrderHeader> headers = soHeaderMapper.selectList(new LambdaQueryWrapper<SoSalesOrderHeader>()
            .select(SoSalesOrderHeader::getSoNo, SoSalesOrderHeader::getClientName,
                SoSalesOrderHeader::getTotalAmount, SoSalesOrderHeader::getOrderStatus,
                SoSalesOrderHeader::getCreateTime)
            .orderByDesc(SoSalesOrderHeader::getCreateTime)
            .last("LIMIT 5"));
        List<ErpDashboardStatsVo.RecentOrder> orders = new ArrayList<>(headers.size());
        for (SoSalesOrderHeader h : headers) {
            ErpDashboardStatsVo.RecentOrder o = new ErpDashboardStatsVo.RecentOrder();
            o.setSoNo(h.getSoNo());
            o.setClientName(h.getClientName());
            o.setTotalAmount(h.getTotalAmount());
            o.setOrderStatus(h.getOrderStatus());
            o.setCreateTime(h.getCreateTime());
            orders.add(o);
        }
        return orders;
    }

    /** 未处理预警前5, 库存水位(current-safety)最低优先 */
    private List<ErpDashboardStatsVo.StockAlertItem> selectTopStockAlerts() {
        List<WmStockAlert> alerts = stockAlertMapper.selectList(new QueryWrapper<WmStockAlert>()
            .eq("handled_flag", 0)
            .last("ORDER BY IFNULL(current_qty, 0) - IFNULL(safety_stock, 0) ASC LIMIT 5"));
        List<ErpDashboardStatsVo.StockAlertItem> items = new ArrayList<>(alerts.size());
        for (WmStockAlert a : alerts) {
            ErpDashboardStatsVo.StockAlertItem item = new ErpDashboardStatsVo.StockAlertItem();
            item.setAlertId(a.getAlertId());
            item.setMaterialCode(a.getMaterialCode());
            item.setMaterialName(a.getMaterialName());
            item.setWarehouseName(a.getWarehouseName());
            item.setCurrentQty(a.getCurrentQty());
            item.setSafetyStock(a.getSafetyStock());
            item.setAlertLevel(a.getAlertLevel());
            items.add(item);
        }
        return items;
    }

    /** 近12个月销售额+单数趋势, 无订单月份补零 */
    private List<ErpDashboardStatsVo.MonthlyPoint> selectSalesTrend() {
        LocalDate now = LocalDate.now();
        LocalDate windowStart = now.minusMonths(11).withDayOfMonth(1);
        Date startDate = Date.from(windowStart.atStartOfDay(ZoneId.systemDefault()).toInstant());

        Map<String, ErpDashboardStatsVo.MonthlyPoint> points = new LinkedHashMap<>(16);
        List<Map<String, Object>> rows = soHeaderMapper.selectMaps(new QueryWrapper<SoSalesOrderHeader>()
            .select("DATE_FORMAT(create_time, '%Y-%m') AS stat_month",
                "IFNULL(SUM(total_amount), 0) AS stat_amount", "COUNT(*) AS stat_count")
            .ge("create_time", startDate)
            .and(w -> w.ne("order_status", ISoSalesOrderHeaderService.STATUS_CANCELLED)
                .or().isNull("order_status"))
            .groupBy("stat_month")
            .orderByAsc("stat_month"));
        for (Map<String, Object> row : rows) {
            String month = row.get("stat_month") == null ? null : String.valueOf(row.get("stat_month"));
            if (month == null) {
                continue;
            }
            ErpDashboardStatsVo.MonthlyPoint p = new ErpDashboardStatsVo.MonthlyPoint();
            p.setMonth(month);
            p.setAmount(asBigDecimal(row.get("stat_amount"), BigDecimal.ZERO));
            p.setOrderCount(asLong(row.get("stat_count"), 0L));
            points.put(month, p);
        }
        // 缺月补零, 保证前端坐标轴连续12个月
        List<ErpDashboardStatsVo.MonthlyPoint> trend = new ArrayList<>(12);
        for (int i = 11; i >= 0; i--) {
            String month = now.minusMonths(i).format(MONTH_FMT);
            ErpDashboardStatsVo.MonthlyPoint p = points.get(month);
            if (p == null) {
                p = new ErpDashboardStatsVo.MonthlyPoint();
                p.setMonth(month);
                p.setAmount(BigDecimal.ZERO);
                p.setOrderCount(0L);
            }
            trend.add(p);
        }
        return trend;
    }

    private BigDecimal asBigDecimal(Object v, BigDecimal fallback) {
        if (v == null) {
            return fallback;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Long asLong(Object v, Long fallback) {
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

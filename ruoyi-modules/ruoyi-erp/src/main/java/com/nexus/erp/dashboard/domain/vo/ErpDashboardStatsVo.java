package com.nexus.erp.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 首页工作台统计
 * <p>聚合销售/主数据/库存预警的真实数据, 供前端首页替换演示数据。</p>
 *
 * @author Nexus
 */
@Data
public class ErpDashboardStatsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 本月销售订单金额(不含已取消)
     */
    private BigDecimal monthSalesAmount;

    /**
     * 本月销售订单数
     */
    private Long monthSalesCount;

    /**
     * 进行中销售订单数(草稿/审批中/已审批/已确认)
     */
    private Long openSalesOrderCount;

    /**
     * 客户总数
     */
    private Long clientCount;

    /**
     * 物料总数
     */
    private Long materialCount;

    /**
     * 未处理库存预警数
     */
    private Long stockAlertCount;

    /**
     * 最新销售订单(前5, 按创建时间倒序)
     */
    private List<RecentOrder> recentOrders;

    /**
     * 未处理库存预警(前5, 库存水位最低优先)
     */
    private List<StockAlertItem> stockAlerts;

    /**
     * 近12个月销售趋势(缺月补零)
     */
    private List<MonthlyPoint> salesTrend;

    @Data
    public static class RecentOrder implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 订单号
         */
        private String soNo;

        /**
         * 客户名称
         */
        private String clientName;

        /**
         * 订单总额
         */
        private BigDecimal totalAmount;

        /**
         * 订单状态(DRAFT/APPROVING/APPROVED/CONFIRMED/FULFILLED/CLOSED/CANCELLED)
         */
        private String orderStatus;

        /**
         * 创建时间
         */
        private Date createTime;
    }

    @Data
    public static class StockAlertItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 预警ID
         */
        private Long alertId;

        /**
         * 物料编码
         */
        private String materialCode;

        /**
         * 物料名称
         */
        private String materialName;

        /**
         * 仓库名称
         */
        private String warehouseName;

        /**
         * 当前库存
         */
        private BigDecimal currentQty;

        /**
         * 安全库存
         */
        private BigDecimal safetyStock;

        /**
         * 预警级别(CRITICAL/WARNING/INFO)
         */
        private String alertLevel;
    }

    @Data
    public static class MonthlyPoint implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 月份, 格式 yyyy-MM
         */
        private String month;

        /**
         * 销售额(不含已取消)
         */
        private BigDecimal amount;

        /**
         * 订单数(不含已取消)
         */
        private Long orderCount;
    }
}

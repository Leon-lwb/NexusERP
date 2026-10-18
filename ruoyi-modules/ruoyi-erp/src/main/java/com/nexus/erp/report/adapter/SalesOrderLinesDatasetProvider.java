package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 销售订单执行明细的固定只读投影；字段、条件与联表规则均由代码审核维护。 */
@Service
@DS("erp")
public class SalesOrderLinesDatasetProvider extends JdbcDatasetProvider {
    /** @return 与业务单据一致的币种字典，原币种仍用于金额分组 */
    @Override
    public Map<String, String> displayDictionaries() {
        return Map.of("currency", "so_currency_type");
    }

    /**
     * 装配业务数据源上的只读数据集。
     *
     * @param jdbc 随动态数据源切换的查询入口
     */
    public SalesOrderLinesDatasetProvider(JdbcTemplate jdbc) {
        super(
                jdbc,
                definition(),
                "so_sales_order_header p JOIN so_sales_order_line l ON l.so_id=p.so_id AND"
                        + " l.tenant_id=p.tenant_id AND l.del_flag='0'",
                "p",
                "l.line_id",
                columns());
    }

    /**
     * 声明版本一的字段权限、精确类型与汇总维度。
     *
     * @return 不可变数据集契约
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "ERP_SALES_ORDER_LINES",
                "销售订单执行明细",
                1,
                "so:orderheader:list",
                false,
                List.of(
                        new DatasetField("lineId", "明细ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "orderNo", "销售订单号", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "clientName", "客户", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "orderDate", "订单日期", ValueType.DATE, null, List.of(), true),
                        new DatasetField(
                                "materialCode", "物料编码", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialName", "物料名称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "specification", "规格型号", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "orderedQuantity",
                                "订购数量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField(
                                "deliveredQuantity",
                                "已发货数量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField(
                                "returnedQuantity",
                                "退货数量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField(
                                "remainingQuantity",
                                "待发数量（不含退货重开）",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField("unitName", "单位", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "unitPrice",
                                "单价",
                                ValueType.DECIMAL,
                                "so:orderheader:query",
                                List.of(),
                                false),
                        new DatasetField(
                                "lineAmount",
                                "明细金额",
                                ValueType.DECIMAL,
                                "so:orderheader:query",
                                List.of("currency"),
                                true),
                        new DatasetField("currency", "币种", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "requestedDate", "需求日期", ValueType.DATE, null, List.of(), true),
                        new DatasetField(
                                "promiseDate", "承诺交期", ValueType.DATE, null, List.of(), true),
                        new DatasetField(
                                "status", "明细状态", ValueType.STRING, null, List.of(), true)),
                List.of(
                        new DatasetParameter("orderId", "销售订单", ValueType.ID, false, null),
                        new DatasetParameter("clientId", "客户", ValueType.ID, false, null),
                        new DatasetParameter("materialId", "物料", ValueType.ID, false, null),
                        new DatasetParameter("fromDate", "订单开始日期", ValueType.DATE, false, null),
                        new DatasetParameter("toDate", "订单结束日期", ValueType.DATE, false, null)),
                List.of(
                        "orderNo",
                        "clientName",
                        "orderDate",
                        "materialCode",
                        "materialName",
                        "specification",
                        "orderedQuantity",
                        "deliveredQuantity",
                        "returnedQuantity",
                        "remainingQuantity",
                        "unitName",
                        "unitPrice",
                        "lineAmount",
                        "currency",
                        "requestedDate",
                        "promiseDate",
                        "status"));
    }

    /**
     * 建立逻辑字段到代码固定SQL表达式的映射，未知状态保留原值。
     *
     * @return 可信投影目录
     */
    private static Map<String, String> columns() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("lineId", "l.line_id");
        values.put("orderNo", "p.so_no");
        values.put("clientName", "p.client_name");
        values.put("orderDate", "p.order_date");
        values.put("materialCode", "l.material_code");
        values.put("materialName", "l.material_name");
        values.put("specification", "l.material_spec");
        values.put("orderedQuantity", "l.ordered_qty");
        values.put("deliveredQuantity", "l.delivered_qty");
        values.put("returnedQuantity", "l.returned_qty");
        values.put(
                "remainingQuantity",
                "CASE WHEN COALESCE(l.ordered_qty,0) > COALESCE(l.delivered_qty,0) THEN"
                        + " COALESCE(l.ordered_qty,0)-COALESCE(l.delivered_qty,0) ELSE 0 END");
        values.put("unitName", "l.unit");
        values.put("unitPrice", "l.unit_price");
        values.put("lineAmount", "l.line_amount");
        values.put("currency", "p.currency");
        values.put("requestedDate", "l.requested_date");
        values.put("promiseDate", "l.promise_date");
        values.put(
                "status",
                "CASE l.line_status WHEN 'PENDING' THEN '待处理' WHEN 'SCHEDULED' THEN '已排产或采购' WHEN"
                        + " 'PARTIALLY_FULFILLED' THEN '部分履行' WHEN 'FULFILLED' THEN '已履行' WHEN"
                        + " 'CANCELLED' THEN '已取消' ELSE l.line_status END");
        return values;
    }

    /**
     * 仅对已声明参数生成绑定条件，业务范围由公共底座附加。
     *
     * @param query 已校验的有界查询
     * @return 只包含可信表达式和绑定值的条件列表
     */
    @Override
    protected List<Filter> filters(DatasetQuery query) {
        List<Filter> filters = new ArrayList<>();
        filters.addAll(dates(query, "p.order_date"));
        equal(filters, query, "orderId", "p.so_id");
        equal(filters, query, "clientId", "p.client_id");
        equal(filters, query, "materialId", "l.material_id");
        filters.add(
                new Filter("p.order_status IS NULL OR p.order_status <> 'CANCELLED'", List.of()));
        filters.add(new Filter("l.line_status IS NULL OR l.line_status <> 'CANCELLED'", List.of()));
        return filters;
    }
}

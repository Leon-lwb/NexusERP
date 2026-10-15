package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 采购订单到货明细的固定只读投影；字段、条件与联表规则均由代码审核维护。 */
@Service
@DS("erp")
public class PurchaseOrderLinesDatasetProvider extends JdbcDatasetProvider {
    /**
     * 装配业务数据源上的只读数据集。
     *
     * @param jdbc 随动态数据源切换的查询入口
     */
    public PurchaseOrderLinesDatasetProvider(JdbcTemplate jdbc) {
        super(
                jdbc,
                definition(),
                "pu_purchase_order p JOIN pu_purchase_order_line l ON l.order_id=p.order_id AND"
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
                "ERP_PURCHASE_ORDER_LINES",
                "采购订单到货明细",
                1,
                "pu:order:list",
                false,
                List.of(
                        new DatasetField("lineId", "明细ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "orderNo", "采购订单号", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "vendorName", "供应商", ValueType.STRING, null, List.of(), true),
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
                                "receivedQuantity",
                                "已收货数量",
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
                                "待收数量（不含退货重开）",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField("unitName", "单位", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "unitPrice",
                                "单价",
                                ValueType.DECIMAL,
                                "pu:order:query",
                                List.of(),
                                false),
                        new DatasetField(
                                "lineAmount",
                                "明细金额",
                                ValueType.DECIMAL,
                                "pu:order:query",
                                List.of("currency"),
                                true),
                        new DatasetField("currency", "币种", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "expectedArrivalDate",
                                "预计到货日",
                                ValueType.DATE,
                                null,
                                List.of(),
                                true),
                        new DatasetField(
                                "status", "收货状态", ValueType.STRING, null, List.of(), true)),
                List.of(
                        new DatasetParameter("orderId", "采购订单", ValueType.ID, false, null),
                        new DatasetParameter("vendorId", "供应商", ValueType.ID, false, null),
                        new DatasetParameter("materialId", "物料", ValueType.ID, false, null),
                        new DatasetParameter("fromDate", "订单开始日期", ValueType.DATE, false, null),
                        new DatasetParameter("toDate", "订单结束日期", ValueType.DATE, false, null)),
                List.of(
                        "orderNo",
                        "vendorName",
                        "orderDate",
                        "materialCode",
                        "materialName",
                        "specification",
                        "orderedQuantity",
                        "receivedQuantity",
                        "returnedQuantity",
                        "remainingQuantity",
                        "unitName",
                        "unitPrice",
                        "lineAmount",
                        "currency",
                        "expectedArrivalDate",
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
        values.put("orderNo", "p.order_no");
        values.put("vendorName", "p.vendor_name");
        values.put("orderDate", "p.order_date");
        values.put("materialCode", "l.material_code");
        values.put("materialName", "l.material_name");
        values.put("specification", "l.material_spec");
        values.put("orderedQuantity", "l.order_quantity");
        values.put("receivedQuantity", "l.received_quantity");
        values.put("returnedQuantity", "l.returned_quantity");
        values.put(
                "remainingQuantity",
                "CASE WHEN COALESCE(l.order_quantity,0) > COALESCE(l.received_quantity,0) THEN"
                    + " COALESCE(l.order_quantity,0)-COALESCE(l.received_quantity,0) ELSE 0 END");
        values.put("unitName", "l.unit");
        values.put("unitPrice", "l.unit_price");
        values.put("lineAmount", "l.line_amount");
        values.put("currency", "p.currency");
        values.put("expectedArrivalDate", "p.expected_arrival_date");
        values.put(
                "status",
                "CASE l.receive_status WHEN 'PENDING' THEN '待收货' WHEN 'FULL_RECEIVED' THEN '全收'"
                        + " WHEN 'PARTIAL_RECEIVED' THEN '部分收' WHEN 'OVER_RECEIVED' THEN '超收' WHEN"
                        + " 'SHORT_RECEIVED' THEN '短收' ELSE l.receive_status END");
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
        equal(filters, query, "orderId", "p.order_id");
        equal(filters, query, "vendorId", "p.vendor_id");
        equal(filters, query, "materialId", "l.material_id");
        filters.add(new Filter("p.status IS NULL OR p.status <> 'CANCELLED'", List.of()));
        return filters;
    }
}

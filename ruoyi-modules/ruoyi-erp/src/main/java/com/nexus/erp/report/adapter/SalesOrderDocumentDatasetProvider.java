package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 销售订单单据的固定只读投影，表头和明细始终处于同一租户。 */
@Service
@DS("erp")
public class SalesOrderDocumentDatasetProvider extends JdbcDatasetProvider {
    /** @return 与业务单据一致的币种字典，原币种仍用于金额分组 */
    @Override
    public Map<String, String> displayDictionaries() {
        return Map.of("currency", "so_currency_type");
    }

    /**
     * 装配销售订单真实表结构，删除标志和业务范围由公共底座及固定联表约束生效。
     *
     * @param jdbc 随ERP动态数据源切换的查询入口
     */
    public SalesOrderDocumentDatasetProvider(JdbcTemplate jdbc) {
        super(
                jdbc,
                definition(),
                "so_sales_order_header p INNER JOIN so_sales_order_line l ON l.so_id=p.so_id"
                        + " AND l.tenant_id=p.tenant_id AND l.del_flag='0'",
                "p",
                "l.line_id",
                columns());
    }

    /**
     * 声明独立版本一单据契约，金额权限与销售执行明细保持一致。
     *
     * @return 必填销售订单ID且默认不展示金额的不可变描述
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "ERP_SALES_ORDER_DOCUMENT",
                "销售订单打印",
                1,
                "so:orderheader:query",
                true,
                List.of(
                        new DatasetField("lineId", "订单行ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "orderNo", "销售订单号", ValueType.STRING, null, List.of(), true),
                        new DatasetField("clientName", "客户", ValueType.STRING, null, List.of(), true),
                        new DatasetField("orderDate", "订单日期", ValueType.DATE, null, List.of(), true),
                        new DatasetField(
                                "requestedDate", "要求交期", ValueType.DATE, null, List.of(), true),
                        new DatasetField(
                                "promiseDate", "承诺交期", ValueType.DATE, null, List.of(), true),
                        new DatasetField("currency", "币种", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialCode", "物料编码", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialName", "物料名称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "specification", "规格型号", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "quantity",
                                "订购数量",
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
                                "行金额",
                                ValueType.DECIMAL,
                                "so:orderheader:query",
                                List.of("currency"),
                                true)),
                List.of(new DatasetParameter("orderId", "销售订单", ValueType.ID, true, null)),
                List.of("materialCode", "materialName", "specification", "quantity", "unitName"));
    }

    /**
     * 将业务字段绑定到审核维护的表头和明细列，不接收外部SQL标识符。
     *
     * @return 固定字段表达式目录
     */
    private static Map<String, String> columns() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("lineId", "l.line_id");
        values.put("orderNo", "p.so_no");
        values.put("clientName", "p.client_name");
        values.put("orderDate", "p.order_date");
        values.put("requestedDate", "p.requested_date");
        values.put("promiseDate", "p.promise_date");
        values.put("currency", "p.currency");
        values.put("materialCode", "l.material_code");
        values.put("materialName", "l.material_name");
        values.put("specification", "l.material_spec");
        values.put("quantity", "l.ordered_qty");
        values.put("unitName", "l.unit");
        values.put("unitPrice", "l.unit_price");
        values.put("lineAmount", "l.line_amount");
        return values;
    }

    /**
     * 将必填订单参数精确绑定到真实销售订单主键。
     *
     * @param query 已通过公共契约校验的查询
     * @return 不包含外部拼接内容的筛选条件
     */
    @Override
    protected List<Filter> filters(DatasetQuery query) {
        List<Filter> filters = new ArrayList<>();
        equal(filters, query, "orderId", "p.so_id");
        return filters;
    }
}

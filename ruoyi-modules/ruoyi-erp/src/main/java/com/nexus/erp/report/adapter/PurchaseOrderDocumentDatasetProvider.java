package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 代码维护的业务只读投影，物理标识符不会来自报表定义。 */
@Service
@DS("erp")
public class PurchaseOrderDocumentDatasetProvider extends JdbcDatasetProvider {
    /** @return 与业务单据一致的币种字典，原币种仍用于金额分组 */
    @Override
    public Map<String, String> displayDictionaries() {
        return Map.of("currency", "so_currency_type");
    }

    /**
     * 初始化采购订单单据数据集提供者并保存所需依赖或不可变值。
     *
     * @param jdbc 当前操作的 {@code jdbc} 输入
     */
    public PurchaseOrderDocumentDatasetProvider(JdbcTemplate jdbc) {
        super(
                jdbc,
                definition(),
                "pu_purchase_order p INNER JOIN pu_purchase_order_line l ON l.order_id=p.order_id"
                        + " AND l.tenant_id=p.tenant_id AND l.del_flag='0'",
                "p",
                "l.line_id",
                columns());
    }

    /**
     * 读取满足操作权限的报表定义。
     *
     * @return 当前处理返回的固定结果
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "ERP_PURCHASE_ORDER_DOCUMENT",
                "采购订单打印",
                1,
                "pu:order:query",
                true,
                List.of(
                        new DatasetField("lineId", "订单行ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "orderNo", "采购订单号", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "vendorName", "供应商", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "orderDate", "订单日期", ValueType.DATE, null, List.of(), true),
                        new DatasetField("currency", "币种", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "orderAmount",
                                "订单金额",
                                ValueType.DECIMAL,
                                null,
                                List.of("currency"),
                                true),
                        new DatasetField(
                                "materialCode", "物料编码", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialName", "物料名称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "specification", "规格", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "quantity",
                                "采购数量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField("unitName", "单位", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "unitPrice",
                                "单价",
                                ValueType.DECIMAL,
                                null,
                                List.of("currency", "materialCode", "unitName"),
                                true),
                        new DatasetField(
                                "lineAmount",
                                "行金额",
                                ValueType.DECIMAL,
                                null,
                                List.of("currency"),
                                true)),
                List.of(new DatasetParameter("orderId", "采购订单ID", ValueType.ID, true, null)),
                List.of("materialCode", "materialName", "quantity", "unitName", "lineAmount"));
    }

    /**
     * 声明逻辑字段到可信投影的映射。
     *
     * @return 当前处理返回的固定结果
     */
    private static Map<String, String> columns() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("lineId", "l.line_id");
        values.put("orderNo", "p.order_no");
        values.put("vendorName", "p.vendor_name");
        values.put("orderDate", "p.order_date");
        values.put("currency", "p.currency");
        values.put("orderAmount", "p.total_amount");
        values.put("materialCode", "l.material_code");
        values.put("materialName", "l.material_name");
        values.put("specification", "l.material_spec");
        values.put("quantity", "l.order_quantity");
        values.put("unitName", "l.unit");
        values.put("unitPrice", "l.unit_price");
        values.put("lineAmount", "l.line_amount");
        return values;
    }

    /**
     * 生成契约允许的绑定筛选条件。
     *
     * @param query 已声明字段和筛选参数
     * @return 当前处理返回的固定结果
     */
    protected List<Filter> filters(DatasetQuery query) {
        List<Filter> filters = new ArrayList<>();
        equal(filters, query, "orderId", "p.order_id");
        return filters;
    }
}

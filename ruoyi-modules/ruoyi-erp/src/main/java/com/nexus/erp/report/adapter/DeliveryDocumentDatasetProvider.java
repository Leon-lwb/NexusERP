package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 代码维护的业务只读投影，物理标识符不会来自报表定义。 */
@Service
@DS("erp")
public class DeliveryDocumentDatasetProvider extends JdbcDatasetProvider {
    /**
     * 初始化送货单据数据集提供者并保存所需依赖或不可变值。
     *
     * @param jdbc 当前操作的 {@code jdbc} 输入
     */
    public DeliveryDocumentDatasetProvider(JdbcTemplate jdbc) {
        super(
                jdbc,
                definition(),
                "wm_sales_issue i INNER JOIN wm_sales_issue_line l ON l.issue_id=i.issue_id AND"
                    + " l.tenant_id=i.tenant_id AND l.del_flag='0' LEFT JOIN so_sales_order_header"
                    + " s ON s.so_id=i.so_id AND s.tenant_id=i.tenant_id AND s.del_flag='0'",
                "i",
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
                "ERP_DELIVERY_DOCUMENT",
                "送货单打印",
                1,
                "wm:salesIssue:query",
                true,
                List.of(
                        new DatasetField("lineId", "发货行ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "issueNo", "送货单号", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "clientName", "客户", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "issueDate", "发货日期", ValueType.DATE, null, List.of(), true),
                        new DatasetField(
                                "warehouseName", "仓库", ValueType.STRING, null, List.of(), true),
                        new DatasetField("currency", "币种", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialCode", "物料编码", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialName", "物料名称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "specification", "规格", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "quantity",
                                "发货数量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField("unitName", "单位", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "batchCode", "批次", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "lineAmount",
                                "行金额",
                                ValueType.DECIMAL,
                                null,
                                List.of("currency"),
                                true)),
                List.of(new DatasetParameter("issueId", "送货单ID", ValueType.ID, true, null)),
                List.of(
                        "materialCode",
                        "materialName",
                        "specification",
                        "quantity",
                        "unitName",
                        "batchCode"));
    }

    /**
     * 声明逻辑字段到可信投影的映射。
     *
     * @return 当前处理返回的固定结果
     */
    private static Map<String, String> columns() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("lineId", "l.line_id");
        values.put("issueNo", "i.issue_code");
        values.put("clientName", "i.client_name");
        values.put("issueDate", "i.issue_date");
        values.put("warehouseName", "i.warehouse_name");
        values.put("currency", "s.currency");
        values.put("materialCode", "l.material_code");
        values.put("materialName", "l.material_name");
        values.put("specification", "l.material_spec");
        values.put("quantity", "l.quantity_issue");
        values.put("unitName", "l.base_uom_name");
        values.put("batchCode", "l.batch_code");
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
        equal(filters, query, "issueId", "i.issue_id");
        return filters;
    }
}

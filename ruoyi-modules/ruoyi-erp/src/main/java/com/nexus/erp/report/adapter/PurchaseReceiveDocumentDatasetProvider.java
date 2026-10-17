package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 采购收货单固定只读投影，直接读取物料入库单和实收明细，不查询采购金额。 */
@Service
@DS("erp")
public class PurchaseReceiveDocumentDatasetProvider extends JdbcDatasetProvider {
    /**
     * 装配真实收货表并约束明细所属租户及删除状态。
     *
     * @param jdbc 随ERP动态数据源切换的查询入口
     */
    public PurchaseReceiveDocumentDatasetProvider(JdbcTemplate jdbc) {
        super(
                jdbc,
                definition(),
                "wm_material_receive p INNER JOIN wm_material_receive_line l ON l.receive_id=p.receive_id"
                        + " AND l.tenant_id=p.tenant_id AND l.del_flag='0'",
                "p",
                "l.line_id",
                columns());
    }

    /**
     * 声明仅含收货业务事实的独立版本一单据契约。
     *
     * @return 必填收货单ID且不含金额字段的不可变描述
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "ERP_PURCHASE_RECEIVE_DOCUMENT",
                "采购收货单打印",
                1,
                "wm:receive:query",
                true,
                List.of(
                        new DatasetField("lineId", "收货行ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "receiveNo", "收货单号", ValueType.STRING, null, List.of(), true),
                        new DatasetField("vendorName", "供应商", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "purchaseOrderNo", "来源采购单", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "warehouseName", "仓库", ValueType.STRING, null, List.of(), true),
                        new DatasetField("receiveDate", "收货日期", ValueType.DATE, null, List.of(), true),
                        new DatasetField(
                                "materialCode", "物料编码", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialName", "物料名称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "specification", "规格型号", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "quantity",
                                "实收数量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField("unitName", "单位", ValueType.STRING, null, List.of(), true),
                        new DatasetField("batchCode", "批次", ValueType.STRING, null, List.of(), true)),
                List.of(new DatasetParameter("receiveId", "采购收货单", ValueType.ID, true, null)),
                List.of(
                        "materialCode",
                        "materialName",
                        "specification",
                        "quantity",
                        "unitName",
                        "batchCode"));
    }

    /**
     * 将来源采购单号和实收数量等字段绑定到实际收货表列。
     *
     * @return 不联查采购金额表的固定投影目录
     */
    private static Map<String, String> columns() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("lineId", "l.line_id");
        values.put("receiveNo", "p.receive_code");
        values.put("vendorName", "p.vendor_name");
        values.put("purchaseOrderNo", "p.pur_code");
        values.put("warehouseName", "p.warehouse_name");
        values.put("receiveDate", "p.receive_date");
        values.put("materialCode", "l.material_code");
        values.put("materialName", "l.material_name");
        values.put("specification", "l.material_spec");
        values.put("quantity", "l.quantity_received");
        values.put("unitName", "l.base_uom_name");
        values.put("batchCode", "l.batch_code");
        return values;
    }

    /**
     * 将必填收货参数精确绑定到物料入库单主键。
     *
     * @param query 已通过公共契约校验的查询
     * @return 只包含固定表达式和绑定值的筛选条件
     */
    @Override
    protected List<Filter> filters(DatasetQuery query) {
        List<Filter> filters = new ArrayList<>();
        equal(filters, query, "receiveId", "p.receive_id");
        return filters;
    }
}

package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 库存收发流水的固定只读投影；字段、条件与联表规则均由代码审核维护。 */
@Service
@DS("erp")
public class StockMovementDatasetProvider extends JdbcDatasetProvider {
    /**
     * 装配业务数据源上的只读数据集。
     *
     * @param jdbc 随动态数据源切换的查询入口
     */
    public StockMovementDatasetProvider(JdbcTemplate jdbc) {
        super(jdbc, definition(), "wm_material_stock_tx p", "p", "p.tx_id", columns());
    }

    /**
     * 声明版本一的字段权限、精确类型与汇总维度。
     *
     * @return 不可变数据集契约
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "ERP_STOCK_MOVEMENT",
                "库存收发流水",
                1,
                "wm:stocktx:list",
                false,
                List.of(
                        new DatasetField(
                                "transactionId", "流水ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "transactionTime",
                                "发生时间",
                                ValueType.DATETIME,
                                null,
                                List.of(),
                                true),
                        new DatasetField(
                                "transactionType", "事务类型", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "sourceDocumentNo",
                                "来源单号",
                                ValueType.STRING,
                                null,
                                List.of(),
                                true),
                        new DatasetField(
                                "materialCode", "物料编码", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialName", "物料名称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "specification", "规格型号", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "warehouseName", "仓库", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "direction", "收发方向", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "quantity", "发生数量（原始值）", ValueType.DECIMAL, null, List.of(), false),
                        new DatasetField(
                                "beforeQuantity",
                                "变动前现存量",
                                ValueType.DECIMAL,
                                null,
                                List.of(),
                                false),
                        new DatasetField(
                                "afterQuantity",
                                "变动后现存量",
                                ValueType.DECIMAL,
                                null,
                                List.of(),
                                false),
                        new DatasetField(
                                "unitName", "单位", ValueType.STRING, null, List.of(), true)),
                List.of(
                        new DatasetParameter(
                                "fromDate", "发生开始日期", ValueType.DATE, false, "MONTHS_AGO_6"),
                        new DatasetParameter("toDate", "发生结束日期", ValueType.DATE, false, "TODAY"),
                        new DatasetParameter("warehouseId", "仓库", ValueType.ID, false, null),
                        new DatasetParameter("materialId", "物料", ValueType.ID, false, null)),
                List.of(
                        "transactionTime",
                        "transactionType",
                        "sourceDocumentNo",
                        "materialCode",
                        "materialName",
                        "specification",
                        "warehouseName",
                        "direction",
                        "quantity",
                        "beforeQuantity",
                        "afterQuantity",
                        "unitName"));
    }

    /**
     * 建立逻辑字段到代码固定SQL表达式的映射，未知状态保留原值。
     *
     * @return 可信投影目录
     */
    private static Map<String, String> columns() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("transactionId", "p.tx_id");
        values.put("transactionTime", "p.tx_date");
        values.put(
                "transactionType",
                "CASE p.tx_type WHEN 'COUNT_ADJUST' THEN '盘点调整' WHEN 'MISC_OUT' THEN '其他出库' WHEN"
                    + " 'MISC_IN' THEN '其他入库' WHEN 'PRODUCTION_IN' THEN '生产入库' WHEN 'RECEIVE_IN'"
                    + " THEN '采购收货' WHEN 'TRANSFER_OUT' THEN '调拨出库' WHEN 'TRANSFER_IN' THEN '调拨入库'"
                    + " WHEN 'SUBCONTRACT_RECEIVE' THEN '委外入库' WHEN 'PRODUCTION_ISSUE' THEN '生产领料'"
                    + " WHEN 'SUBCONTRACT_ISSUE' THEN '委外发料' WHEN 'SALES_OUT' THEN '销售出库' WHEN"
                    + " 'REWORK_ISSUE' THEN '返工领料' WHEN 'REWORK_RECEIVE' THEN '返工入库' ELSE p.tx_type"
                    + " END");
        values.put("sourceDocumentNo", "p.source_doc_code");
        values.put("materialCode", "p.material_code");
        values.put("materialName", "p.material_name");
        values.put("specification", "p.material_spec");
        values.put("warehouseName", "p.warehouse_name");
        values.put(
                "direction",
                "CASE p.tx_flag WHEN '0' THEN '不影响现存量' WHEN '1' THEN '入库' WHEN '-1' THEN '出库' ELSE"
                        + " CAST(p.tx_flag AS CHAR(32)) END");
        values.put("quantity", "p.tx_quantity");
        values.put("beforeQuantity", "p.before_quantity");
        values.put("afterQuantity", "p.after_quantity");
        values.put("unitName", "p.base_uom_name");
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
        filters.addAll(dates(query, "p.tx_date"));
        equal(filters, query, "warehouseId", "p.warehouse_id");
        equal(filters, query, "materialId", "p.material_id");
        return filters;
    }
}

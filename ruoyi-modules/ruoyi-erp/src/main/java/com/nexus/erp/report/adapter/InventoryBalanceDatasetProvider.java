package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 代码维护的业务只读投影，物理标识符不会来自报表定义。 */
@Service
@DS("erp")
public class InventoryBalanceDatasetProvider extends JdbcDatasetProvider {
    /**
     * 初始化库存余额数据集提供者并保存所需依赖或不可变值。
     *
     * @param jdbc 当前操作的 {@code jdbc} 输入
     */
    public InventoryBalanceDatasetProvider(JdbcTemplate jdbc) {
        super(jdbc, definition(), "wm_material_stock s", "s", "s.stock_id", columns());
    }

    /**
     * 读取满足操作权限的报表定义。
     *
     * @return 当前处理返回的固定结果
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "ERP_STOCK_BALANCE",
                "库存余额",
                1,
                "wm:stock:list",
                false,
                List.of(
                        new DatasetField("stockId", "库存ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "materialCode", "物料编码", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialName", "物料名称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "warehouseName", "仓库", ValueType.STRING, null, List.of(), true),
                        new DatasetField("unitName", "单位", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "quantity",
                                "库存数量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField(
                                "reservedQuantity",
                                "预留数量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField(
                                "unitCost",
                                "单位成本",
                                ValueType.DECIMAL,
                                "wm:stock:query",
                                List.of(),
                                false),
                        new DatasetField(
                                "totalCost",
                                "库存成本",
                                ValueType.DECIMAL,
                                "wm:stock:query",
                                List.of(),
                                false)),
                List.of(
                        new DatasetParameter("warehouseId", "仓库ID", ValueType.ID, false, null),
                        new DatasetParameter(
                                "materialCode", "物料编码", ValueType.STRING, false, null)),
                List.of("materialCode", "materialName", "warehouseName", "quantity", "unitName"));
    }

    /**
     * 声明逻辑字段到可信投影的映射。
     *
     * @return 当前处理返回的固定结果
     */
    private static Map<String, String> columns() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("stockId", "s.stock_id");
        values.put("materialCode", "s.material_code");
        values.put("materialName", "s.material_name");
        values.put("warehouseName", "s.warehouse_name");
        values.put("unitName", "s.base_uom_name");
        values.put("quantity", "s.stock_quantity");
        values.put("reservedQuantity", "s.reserved_quantity");
        values.put("unitCost", "s.unit_cost");
        values.put("totalCost", "s.total_cost");
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
        equal(filters, query, "warehouseId", "s.warehouse_id");
        equal(filters, query, "materialCode", "s.material_code");
        return filters;
    }
}

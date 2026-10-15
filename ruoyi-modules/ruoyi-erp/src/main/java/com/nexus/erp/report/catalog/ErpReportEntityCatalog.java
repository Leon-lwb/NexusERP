package com.nexus.erp.report.catalog;

import com.nexus.common.report.*;
import com.nexus.common.report.catalog.*;

import org.springframework.stereotype.Component;

import java.util.*;

/** 首批ERP受控实体目录，物理列与范围来自实际业务表，客户端仅接触逻辑标识。 */
@Component
public class ErpReportEntityCatalog implements ReportEntityCatalogProvider {
    /**
     * 固定一版目录，后续变更使用新的目录版本并保留旧版。
     *
     * @return 当前受控处理的固定结果
     */
    @Override
    public ReportEntityCatalog catalog() {
        return new ReportEntityCatalog(
                1,
                List.of(
                        entity(
                                "stock",
                                "库存",
                                "wm_material_stock",
                                "stock_id",
                                "wm:stock:list",
                                null,
                                List.of(
                                        field("id", "库存标识", "stock_id", ValueType.ID),
                                        field("materialId", "物料标识", "material_id", ValueType.ID),
                                        field(
                                                "materialCode",
                                                "物料编码",
                                                "material_code",
                                                ValueType.STRING),
                                        field(
                                                "materialName",
                                                "物料名称",
                                                "material_name",
                                                ValueType.STRING),
                                        field("warehouseId", "仓库标识", "warehouse_id", ValueType.ID),
                                        field(
                                                "warehouseName",
                                                "仓库名称",
                                                "warehouse_name",
                                                ValueType.STRING),
                                        field("unit", "计量单位", "base_uom_name", ValueType.STRING),
                                        new ReportEntityCatalog.Field(
                                                "quantity",
                                                "库存数量",
                                                "stock_quantity",
                                                ValueType.DECIMAL,
                                                null,
                                                List.of("stock.materialCode", "stock.unit"),
                                                true),
                                        new ReportEntityCatalog.Field(
                                                "reservedQuantity",
                                                "预留数量",
                                                "reserved_quantity",
                                                ValueType.DECIMAL,
                                                null,
                                                List.of("stock.materialCode", "stock.unit"),
                                                true),
                                        new ReportEntityCatalog.Field(
                                                "unitCost",
                                                "单位成本",
                                                "unit_cost",
                                                ValueType.DECIMAL,
                                                "wm:stock:query",
                                                List.of(),
                                                false))),
                        entity(
                                "material",
                                "物料",
                                "bd_material",
                                "material_id",
                                "bd:material:list",
                                null,
                                List.of(
                                        field("id", "物料标识", "material_id", ValueType.ID),
                                        field("code", "物料编码", "material_code", ValueType.STRING),
                                        field("name", "物料名称", "material_name", ValueType.STRING),
                                        field("unitId", "基础单位", "base_uom_id", ValueType.ID))),
                        entity(
                                "warehouse",
                                "仓库",
                                "wm_warehouse",
                                "warehouse_id",
                                "wm:warehouse:list",
                                null,
                                List.of(
                                        field("id", "仓库标识", "warehouse_id", ValueType.ID),
                                        field("code", "仓库编码", "warehouse_code", ValueType.STRING),
                                        field("name", "仓库名称", "warehouse_name", ValueType.STRING))),
                        entity(
                                "client",
                                "客户",
                                "bd_client",
                                "client_id",
                                "bd:client:list",
                                null,
                                List.of(
                                        field("id", "客户标识", "client_id", ValueType.ID),
                                        field("code", "客户编码", "client_code", ValueType.STRING),
                                        field("name", "客户名称", "client_name", ValueType.STRING))),
                        entity(
                                "vendor",
                                "供应商",
                                "bd_vendor",
                                "vendor_id",
                                "bd:vendor:list",
                                null,
                                List.of(
                                        field("id", "供应商标识", "vendor_id", ValueType.ID),
                                        field("code", "供应商编码", "vendor_code", ValueType.STRING),
                                        field("name", "供应商名称", "vendor_name", ValueType.STRING))),
                        entity(
                                "purchase",
                                "采购订单",
                                "pu_purchase_order",
                                "order_id",
                                "pu:order:list",
                                null,
                                List.of(
                                        field("id", "订单标识", "order_id", ValueType.ID),
                                        field("number", "订单编号", "order_no", ValueType.STRING),
                                        field("vendorId", "供应商标识", "vendor_id", ValueType.ID),
                                        field("date", "订单日期", "order_date", ValueType.DATE),
                                        field("currency", "币种", "currency", ValueType.STRING),
                                        field("status", "状态", "status", ValueType.STRING),
                                        new ReportEntityCatalog.Field(
                                                "amount",
                                                "订单金额",
                                                "total_amount",
                                                ValueType.DECIMAL,
                                                "pu:order:query",
                                                List.of("purchase.currency"),
                                                true))),
                        entity(
                                "purchase_line",
                                "采购明细",
                                "pu_purchase_order_line",
                                "line_id",
                                "pu:order:list",
                                new ReportEntityCatalog.ParentPolicy(
                                        "purchase", "order_id", "order_id"),
                                List.of(
                                        field("id", "明细标识", "line_id", ValueType.ID),
                                        field("orderId", "订单标识", "order_id", ValueType.ID),
                                        field("materialId", "物料标识", "material_id", ValueType.ID),
                                        field(
                                                "materialCode",
                                                "物料编码",
                                                "material_code",
                                                ValueType.STRING),
                                        field("unit", "计量单位", "unit", ValueType.STRING),
                                        new ReportEntityCatalog.Field(
                                                "quantity",
                                                "订单数量",
                                                "order_quantity",
                                                ValueType.DECIMAL,
                                                null,
                                                List.of(
                                                        "purchase_line.materialCode",
                                                        "purchase_line.unit"),
                                                true),
                                        new ReportEntityCatalog.Field(
                                                "amount",
                                                "明细金额",
                                                "line_amount",
                                                ValueType.DECIMAL,
                                                "pu:order:query",
                                                List.of("purchase.currency"),
                                                true))),
                        entity(
                                "sales",
                                "销售订单",
                                "so_sales_order_header",
                                "so_id",
                                "so:orderheader:list",
                                null,
                                List.of(
                                        field("id", "订单标识", "so_id", ValueType.ID),
                                        field("number", "订单编号", "so_no", ValueType.STRING),
                                        field("clientId", "客户标识", "client_id", ValueType.ID),
                                        field("date", "订单日期", "order_date", ValueType.DATE),
                                        field("currency", "币种", "currency", ValueType.STRING),
                                        field("status", "状态", "order_status", ValueType.STRING),
                                        new ReportEntityCatalog.Field(
                                                "amount",
                                                "订单金额",
                                                "total_amount",
                                                ValueType.DECIMAL,
                                                "so:orderheader:query",
                                                List.of("sales.currency"),
                                                true))),
                        entity(
                                "sales_line",
                                "销售明细",
                                "so_sales_order_line",
                                "line_id",
                                "so:orderheader:list",
                                new ReportEntityCatalog.ParentPolicy("sales", "so_id", "so_id"),
                                List.of(
                                        field("id", "明细标识", "line_id", ValueType.ID),
                                        field("orderId", "订单标识", "so_id", ValueType.ID),
                                        field(
                                                "materialCode",
                                                "物料编码",
                                                "material_code",
                                                ValueType.STRING),
                                        field("unit", "计量单位", "unit", ValueType.STRING),
                                        new ReportEntityCatalog.Field(
                                                "quantity",
                                                "订单数量",
                                                "ordered_qty",
                                                ValueType.DECIMAL,
                                                null,
                                                List.of(
                                                        "sales_line.materialCode",
                                                        "sales_line.unit"),
                                                true),
                                        new ReportEntityCatalog.Field(
                                                "amount",
                                                "明细金额",
                                                "line_amount",
                                                ValueType.DECIMAL,
                                                "so:orderheader:query",
                                                List.of("sales.currency"),
                                                true)))),
                List.of(
                        relation(
                                "stock.material",
                                "stock",
                                "material",
                                "material_id",
                                "material_id",
                                "MANY_TO_ONE"),
                        relation(
                                "stock.warehouse",
                                "stock",
                                "warehouse",
                                "warehouse_id",
                                "warehouse_id",
                                "MANY_TO_ONE"),
                        relation(
                                "purchase.vendor",
                                "purchase",
                                "vendor",
                                "vendor_id",
                                "vendor_id",
                                "MANY_TO_ONE"),
                        relation(
                                "purchase.lines",
                                "purchase",
                                "purchase_line",
                                "order_id",
                                "order_id",
                                "ONE_TO_MANY"),
                        relation(
                                "purchase_line.order",
                                "purchase_line",
                                "purchase",
                                "order_id",
                                "order_id",
                                "MANY_TO_ONE"),
                        relation(
                                "purchase_line.material",
                                "purchase_line",
                                "material",
                                "material_id",
                                "material_id",
                                "MANY_TO_ONE"),
                        relation(
                                "sales.client",
                                "sales",
                                "client",
                                "client_id",
                                "client_id",
                                "MANY_TO_ONE"),
                        relation(
                                "sales.lines",
                                "sales",
                                "sales_line",
                                "so_id",
                                "so_id",
                                "ONE_TO_MANY"),
                        relation(
                                "sales_line.order",
                                "sales_line",
                                "sales",
                                "so_id",
                                "so_id",
                                "MANY_TO_ONE")));
    }

    /**
     * 明细组织范围以父单据为准，不将两套部门条件交叉应用。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param label 用户可见标签
     * @param table 当前契约的table配置或状态
     * @param key 当前契约的key配置或状态
     * @param permission 当前契约的permission配置或状态
     * @param parent 当前契约的parent配置或状态
     * @param fields 当前契约的fields配置或状态
     * @return 当前受控处理的固定结果
     */
    private static ReportEntityCatalog.Entity entity(
            String id,
            String label,
            String table,
            String key,
            String permission,
            ReportEntityCatalog.ParentPolicy parent,
            List<ReportEntityCatalog.Field> fields) {
        return new ReportEntityCatalog.Entity(
                id,
                label,
                table,
                key,
                permission,
                "tenant_id",
                "del_flag",
                parent == null ? "create_dept" : null,
                parent == null ? "create_by" : null,
                parent,
                fields);
    }

    /**
     * 声明不涉及数值聚合的固定业务字段。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param label 用户可见标签
     * @param column 当前契约的column配置或状态
     * @param type 受控类型
     * @return 当前受控处理的固定结果
     */
    private static ReportEntityCatalog.Field field(
            String id, String label, String column, ValueType type) {
        return new ReportEntityCatalog.Field(id, label, column, type, null, List.of(), true);
    }

    /**
     * 首批关系均保留无可见右记录的左表业务行。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param from 当前契约的from配置或状态
     * @param to 当前契约的to配置或状态
     * @param fromColumn 当前契约的fromColumn配置或状态
     * @param toColumn 当前契约的toColumn配置或状态
     * @param cardinality 当前契约的cardinality配置或状态
     * @return 当前受控处理的固定结果
     */
    private static ReportEntityCatalog.Relation relation(
            String id,
            String from,
            String to,
            String fromColumn,
            String toColumn,
            String cardinality) {
        return new ReportEntityCatalog.Relation(
                id, from, to, fromColumn, toColumn, cardinality, "LEFT");
    }
}

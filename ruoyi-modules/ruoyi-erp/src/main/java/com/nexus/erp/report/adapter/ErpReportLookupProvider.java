package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** ERP候选遵循实体各自权限；MES引用的ERP基础资料也通过ERP数据源独立读取。 */
@Service
@DS("erp")
public class ErpReportLookupProvider extends JdbcReportLookupProvider {
    /** 固定业务目录和版本1的参数映射，不从报表定义接收物理表名。 */
    private static final List<Rule> RULES =
            List.of(
                    rule(
                            "ERP_PURCHASE_ORDER_DOCUMENT",
                            "orderId",
                            "采购订单",
                            "pu:order:query",
                            "pu_purchase_order",
                            "order_id",
                            "order_no",
                            "vendor_name",
                            ValueType.ID),
                    rule(
                            "ERP_DELIVERY_DOCUMENT",
                            "issueId",
                            "送货单",
                            "wm:salesIssue:query",
                            "wm_sales_issue",
                            "issue_id",
                            "issue_code",
                            "client_name",
                            ValueType.ID),
                    rule(
                            "ERP_SALES_EXECUTION",
                            "clientId",
                            "客户",
                            "bd:client:list",
                            "bd_client",
                            "client_id",
                            "client_code",
                            "client_name",
                            ValueType.ID),
                    rule(
                            "ERP_PURCHASE_EXECUTION",
                            "vendorId",
                            "供应商",
                            "bd:vendor:list",
                            "bd_vendor",
                            "vendor_id",
                            "vendor_code",
                            "vendor_name",
                            ValueType.ID),
                    rule(
                            "ERP_STOCK_BALANCE",
                            "warehouseId",
                            "仓库",
                            "wm:warehouse:list",
                            "wm_warehouse",
                            "warehouse_id",
                            "warehouse_code",
                            "warehouse_name",
                            ValueType.ID),
                    rule(
                            "ERP_STOCK_BALANCE",
                            "materialCode",
                            "物料",
                            "bd:material:list",
                            "bd_material",
                            "material_code",
                            "material_code",
                            "material_name",
                            ValueType.STRING),
                    rule(
                            "ERP_MATERIAL_CATALOG",
                            "materialId",
                            "物料",
                            "bd:material:list",
                            "bd_material",
                            "material_id",
                            "material_code",
                            "material_name",
                            ValueType.ID),
                    rule(
                            "ERP_CLIENT_CATALOG",
                            "clientId",
                            "客户",
                            "bd:client:list",
                            "bd_client",
                            "client_id",
                            "client_code",
                            "client_name",
                            ValueType.ID),
                    rule(
                            "ERP_VENDOR_CATALOG",
                            "vendorId",
                            "供应商",
                            "bd:vendor:list",
                            "bd_vendor",
                            "vendor_id",
                            "vendor_code",
                            "vendor_name",
                            ValueType.ID),
                    rule(
                            "ERP_STOCK_MOVEMENT",
                            "warehouseId",
                            "仓库",
                            "wm:warehouse:list",
                            "wm_warehouse",
                            "warehouse_id",
                            "warehouse_code",
                            "warehouse_name",
                            ValueType.ID),
                    rule(
                            "ERP_STOCK_MOVEMENT",
                            "materialId",
                            "物料",
                            "bd:material:list",
                            "bd_material",
                            "material_id",
                            "material_code",
                            "material_name",
                            ValueType.ID),
                    rule(
                            "ERP_SALES_ORDER_LINES",
                            "orderId",
                            "销售订单",
                            "so:orderheader:list",
                            "so_sales_order_header",
                            "so_id",
                            "so_no",
                            "client_name",
                            ValueType.ID),
                    rule(
                            "ERP_SALES_ORDER_LINES",
                            "clientId",
                            "客户",
                            "bd:client:list",
                            "bd_client",
                            "client_id",
                            "client_code",
                            "client_name",
                            ValueType.ID),
                    rule(
                            "ERP_SALES_ORDER_LINES",
                            "materialId",
                            "物料",
                            "bd:material:list",
                            "bd_material",
                            "material_id",
                            "material_code",
                            "material_name",
                            ValueType.ID),
                    rule(
                            "ERP_PURCHASE_ORDER_LINES",
                            "orderId",
                            "采购订单",
                            "pu:order:list",
                            "pu_purchase_order",
                            "order_id",
                            "order_no",
                            "vendor_name",
                            ValueType.ID),
                    rule(
                            "ERP_PURCHASE_ORDER_LINES",
                            "vendorId",
                            "供应商",
                            "bd:vendor:list",
                            "bd_vendor",
                            "vendor_id",
                            "vendor_code",
                            "vendor_name",
                            ValueType.ID),
                    rule(
                            "ERP_PURCHASE_ORDER_LINES",
                            "materialId",
                            "物料",
                            "bd:material:list",
                            "bd_material",
                            "material_id",
                            "material_code",
                            "material_name",
                            ValueType.ID),
                    rule(
                            "MES_WORK_ORDER_TRACKING",
                            "materialId",
                            "产品物料",
                            "bd:material:list",
                            "bd_material",
                            "material_id",
                            "material_code",
                            "material_name",
                            ValueType.ID),
                    rule(
                            "MES_WORK_ORDER_TRACKING",
                            "workshopId",
                            "车间",
                            "bd:workshop:list",
                            "bd_workshop",
                            "workshop_id",
                            "workshop_code",
                            "workshop_name",
                            ValueType.ID),
                    rule(
                            "MES_IQC_RECORDS",
                            "vendorId",
                            "供应商",
                            "bd:vendor:list",
                            "bd_vendor",
                            "vendor_id",
                            "vendor_code",
                            "vendor_name",
                            ValueType.ID),
                    rule(
                            "MES_IQC_RECORDS",
                            "materialId",
                            "物料",
                            "bd:material:list",
                            "bd_material",
                            "material_id",
                            "material_code",
                            "material_name",
                            ValueType.ID));

    /**
     * 构建共享查询底座，保留原有六项绑定。
     *
     * @param jdbc ERP动态数据源入口
     */
    public ErpReportLookupProvider(JdbcTemplate jdbc) {
        super(jdbc, RULES);
    }

    /**
     * 注册一个代码固定的业务参数选择器。
     *
     * @param dataset 数据集代码
     * @param parameter 参数名
     * @param label 业务对象名称
     * @param permission 对象的操作权限
     * @param table ERP业务表
     * @param key 完整ID或编码列
     * @param code 业务编码列
     * @param name 业务名称列
     * @param type 候选值类型
     * @return 不可变选择器规则
     */
    private static Rule rule(
            String dataset,
            String parameter,
            String label,
            String permission,
            String table,
            String key,
            String code,
            String name,
            ValueType type) {
        return new Rule(
                new ReportLookupBinding(dataset, 1, parameter, label, permission),
                table,
                key,
                code,
                name,
                type);
    }
}

package com.nexus.common.mq.constant;

/**
 * 路由键常量
 *
 * @author Nexus
 */
public interface RoutingKeyConstants {
    String STOCK_IN_RECEIVE = "stock.in.receive";
    String STOCK_IN_PRODUCTION = "stock.in.production";
    String STOCK_IN_REWORK = "stock.in.rework";
    String STOCK_OUT_ISSUE = "stock.out.issue";
    String STOCK_OUT_REWORK = "stock.out.rework";
    String PURCHASE_RECEIVE_WRITEBACK = "purchase.receive.writeback";
    String PRODUCTION_COMPLETE_BACKFLUSH = "production.complete.backflush";
    String VENDOR_EVALUATION_CALCULATE = "vendor.evaluation.calculate";
    String STOCK_ALERT_LOW = "stock.alert.low";
    String SUBCONTRACT_STOCK = "subcontract.stock";
    String REWORK_STOCK = "rework.stock";
    String MES_WORKORDER_RELEASE = "mes.workorder.release";
    /** MES工单完工路由键 (MES->ERP) */
    String ERP_WORKORDER_COMPLETED = "erp.workorder.completed";
    /** MES IQC检验完成路由键 (MES->ERP) */
    String ERP_IQC_COMPLETED = "erp.iqc.completed";
    /** MES OQC检验完成路由键 (MES->ERP, 回写销售订单行OQC状态) */
    String ERP_OQC_COMPLETED = "erp.oqc.completed";
    /** MES IPQC检验完成路由键 (MES->ERP, 回写工单聚合质检状态) */
    String ERP_IPQC_COMPLETED = "erp.ipqc.completed";
    /** ERP 收货审批通过 -> MES 自动创建 IQC */
    String MES_IQC_CREATE = "mes.iqc.create";
    String DELAY_ORDER_TIMEOUT = "delay.order.timeout";
    /** MES 设备状态变更路由键 */
    String MES_EQUIPMENT_STATUS = "mes.equipment.status";
}

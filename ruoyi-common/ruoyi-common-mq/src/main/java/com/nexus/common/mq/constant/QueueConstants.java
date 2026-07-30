package com.nexus.common.mq.constant;

/**
 * 队列常量
 *
 * @author Nexus
 */
public interface QueueConstants {
    String STOCK_IN_QUEUE = "erp.stock.in.queue";
    String STOCK_OUT_QUEUE = "erp.stock.out.queue";
    String PURCHASE_RECEIVE_QUEUE = "erp.purchase.receive.queue";
    String PRODUCTION_COMPLETE_QUEUE = "erp.production.complete.queue";
    String VENDOR_EVALUATION_QUEUE = "erp.vendor.evaluation.queue";
    String STOCK_ALERT_QUEUE = "erp.stock.alert.queue";
    String SUBCONTRACT_STOCK_QUEUE = "erp.subcontract.stock.queue";
    String REWORK_STOCK_QUEUE = "erp.rework.stock.queue";
    String MES_WORKORDER_QUEUE = "erp.mes.workorder.queue";
    /** MES工单完工队列 (MES->ERP) */
    String ERP_WORKORDER_COMPLETED_QUEUE = "erp.workorder.completed.queue";
    /** MES IQC检验完成队列 (MES->ERP) */
    String ERP_IQC_COMPLETED_QUEUE = "erp.iqc.completed.queue";
    /** MES OQC检验完成队列 (MES->ERP, 回写销售订单行OQC状态) */
    String ERP_OQC_COMPLETED_QUEUE = "erp.oqc.completed.queue";
    /** MES IPQC检验完成队列 (MES->ERP, 回写工单聚合质检状态) */
    String ERP_IPQC_COMPLETED_QUEUE = "erp.ipqc.completed.queue";
    /** MES IQC 自动创建队列 (ERP 收货审批通过 -> MES 自动创建 IQC) */
    String MES_IQC_CREATE_QUEUE = "erp.mes.iqc.create.queue";
    /** MES OQC 自动创建队列 (ERP 销售出库审批通过 -> MES 自动创建 OQC) */
    String MES_OQC_CREATE_QUEUE = "erp.mes.oqc.create.queue";
    String DELAY_QUEUE = "erp.delay.queue";
    String DLX_QUEUE = "erp.dlx.queue";
    /** MES 设备状态变更队列(设备状态变更广播, 供排产/工单模块感知) */
    String MES_EQUIPMENT_STATUS_QUEUE = "erp.mes.equipment.status.queue";
    /** GL 过账队列 (业务单据事件 -> AccountingPoster 异步制单, fin.gl) */
    String GL_POSTING_QUEUE = "erp.gl.posting.queue";
    /** 支付收款通知队列 (pay -> ERP, 跨库, F1 AR/AP 用) */
    String PAY_RECEIVED_QUEUE = "erp.pay.received.queue";
    /** 电票开具结果队列 (电子发票平台回调, F7 用) */
    String EINVOICE_ISSUE_RESULT_QUEUE = "erp.einvoice.issue.result.queue";
}

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
    String DELAY_QUEUE = "erp.delay.queue";
    String DLX_QUEUE = "erp.dlx.queue";
}

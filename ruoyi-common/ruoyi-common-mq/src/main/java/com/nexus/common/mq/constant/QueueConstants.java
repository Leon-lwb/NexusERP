package com.nexus.common.mq.constant;

/**
 * 队列常量
 *
 * @author Nexus
 */
public interface QueueConstants {
    String STOCK_IN_QUEUE = "erp.stock.in.queue";
    /**
     * 【预留·未启用】出库事件队列。MQ-01 定性: 全仓无生产者亦无消费者(仅 RabbitMqInitializer 声明)。
     * 出库库存过账由 wm 策略同步完成, 不依赖事件驱动; 后续接入成本结转/倒冲时再启用。
     */
    String STOCK_OUT_QUEUE = "erp.stock.out.queue";
    String PURCHASE_RECEIVE_QUEUE = "erp.purchase.receive.queue";
    String PRODUCTION_COMPLETE_QUEUE = "erp.production.complete.queue";
    /** 【预留·未启用】供应商评估计算队列。MQ-01 定性: 无生产者/无消费者, 评估由 pu/bd 同步计算落库。 */
    String VENDOR_EVALUATION_QUEUE = "erp.vendor.evaluation.queue";
    /** 【预留·未启用】库存预警通知队列。MQ-01 定性: 无生产者/无消费者, 库存预警尚未实现。 */
    String STOCK_ALERT_QUEUE = "erp.stock.alert.queue";
    /**
     * 【已废弃·无生产者】委外库存事件队列。MQ-01/ERP-PRD-05 定性: 仅 RabbitMqInitializer 声明,
     * 全仓无 convertAndSend 生产者; 委外收发货状态由
     * {@code PrdSubcontractClosureServiceImpl.accumulateActualQty} 达量判定权威维护。
     */
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
    /** MES 委外SN生成队列 (ERP 委外发料下达 -> MES 批量生成 PCBA SN) */
    String MES_SUBCONTRACT_SN_QUEUE = "erp.mes.subcontract.sn.queue";
    /** 【预留·未启用】延迟队列。MQ-01 定性: 无生产者投递 DELAY_EXCHANGE, 无消费者。 */
    String DELAY_QUEUE = "erp.delay.queue";
    String DLX_QUEUE = "erp.dlx.queue";
    /** MES 设备状态变更队列(设备状态变更广播, 供排产/工单模块感知) */
    String MES_EQUIPMENT_STATUS_QUEUE = "erp.mes.equipment.status.queue";
    /** GL 过账队列 (业务单据事件 -> AccountingPoster 异步制单, fin.gl) */
    String GL_POSTING_QUEUE = "erp.gl.posting.queue";
    /** 支付收款通知队列 (pay -> ERP, 跨库, F1 AR/AP 用) */
    String PAY_RECEIVED_QUEUE = "erp.pay.received.queue";
    /** 【预留·未启用】电票开具结果队列 (电子发票平台回调, F7 用)。MQ-01 定性: 无生产者/无消费者, 回调消费者 EInvoiceResultConsumer 尚未实现。 */
    String EINVOICE_ISSUE_RESULT_QUEUE = "erp.einvoice.issue.result.queue";
}

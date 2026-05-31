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
    String DELAY_ORDER_TIMEOUT = "delay.order.timeout";
}

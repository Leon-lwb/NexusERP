package com.nexus.common.trace;
/** 应用确认与Broker确认分别记录，WAITING不等于业务成功。 */
public record TraceDeliveryResult(String status,String resultPayload) {
 public boolean applied() {return "APPLIED".equals(status);}
}

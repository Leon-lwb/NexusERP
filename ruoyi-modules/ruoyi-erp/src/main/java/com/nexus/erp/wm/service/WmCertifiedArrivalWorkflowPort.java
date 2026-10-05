package com.nexus.erp.wm.service;

/** 认证通知实际 master 流程的只读契约；证明由内部读取器产生，绝非 HTTP 批准 DTO。 */
public interface WmCertifiedArrivalWorkflowPort {
    /**
     * businessCode 是启动时的 noticeCode；实例不可变 businessKey 及源身份须另行同时核验。
     * 业务关联的 actStatus 与回调载荷均不构成批准证明。
     */
    record Proof(String tenantId, String dataId, String businessCode, String processInstanceId,
                 String processDefinitionId, String sourceDataId, String sourceNoticeId, String nextStatus) { }

    /** 只读取指定租户、指定通知自身实际关联的流程。 */
    Proof read(String tenantId, Long noticeId);
}

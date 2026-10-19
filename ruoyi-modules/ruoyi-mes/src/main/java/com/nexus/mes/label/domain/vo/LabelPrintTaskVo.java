package com.nexus.mes.label.domain.vo;

import lombok.Data;

/**
 * 打印任务提交及状态回执；SENT仅表示发送，不包含完整指令及模板内部数据。
 */
@Data
public class LabelPrintTaskVo {
    /**
     * 持久化任务ID，补打引用此ID。
     */
    private Long taskId;

    /**
     * PENDING、SENDING、SENT、FAIL、UNKNOWN、LEGACY_SENT。
     */
    private String status;

    /**
     * 本地SN，测试任务为空，不提供SN补打入口。
     */
    private Long snId;

    /**
     * 模板ID。
     */
    private Long templateId;

    /**
     * 冻结修订，历史未知为空。
     */
    private Long templateRevision;

    /**
     * 打印来源。
     */
    private String printSource;

    /**
     * 原任务或旧日志ID。
     */
    private Long originalPrintLogId;

    /**
     * 人工补打原因。
     */
    private String reprintReason;

    /**
     * 发送领取次数，至多一次。
     */
    private Integer attemptCount;

    /**
     * 失败或人工核验说明，不包含秘密配置。
     */
    private String detail;
}

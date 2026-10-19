package com.nexus.mes.label.domain;

import lombok.Data;
import java.util.Date;

/**
 * MES 事务内持久化的打印任务；业务快照只插入一次，只有领取及终态字段可以更新。
 */
@Data
public class MesLabelPrintTask {
    /**
     * 任务ID，与本次终态日志ID一致。
     */
    private Long taskId;

    /**
     * 认证租户。
     */
    private String tenantId;

    /**
     * 稳定幂等键。
     */
    private String taskKey;

    /**
     * 业务意图摘要。
     */
    private String requestFingerprint;

    /**
     * 本地SN，可空表示测试任务。
     */
    private Long snId;

    /**
     * 模板ID。
     */
    private Long templateId;

    /**
     * 冻结修订号，历史未知为空。
     */
    private Long templateRevision;

    /**
     * AUTO、TEST或REPRINT。
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
     * 不可变完整快照JSON。
     */
    private String frozenRecord;

    /**
     * 快照原始UTF8字节摘要。
     */
    private String recordHash;

    /**
     * PENDING、SENDING、SENT、FAIL、UNKNOWN或LEGACY_SENT。
     */
    private String status;

    /**
     * 发送领取次数，至多一次。
     */
    private Integer attemptCount;

    /**
     * 一次发送的租约令牌。
     */
    private String leaseToken;

    /**
     * 数据库时钟确定的租约截止时间。
     */
    private Date leaseUntil;

    /**
     * 领取时刻。
     */
    private Date sendingStartedAt;

    /**
     * 终态记录时刻。
     */
    private Date completedAt;

    /**
     * 脱敏失败原因或待核验说明。
     */
    private String lastError;

    /**
     * 创建时刻。
     */
    private Date createdAt;

    /**
     * 状态更新时间。
     */
    private Date updatedAt;
}

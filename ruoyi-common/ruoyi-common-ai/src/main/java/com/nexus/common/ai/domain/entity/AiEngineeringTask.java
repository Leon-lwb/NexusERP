package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nexus.common.tenant.core.TenantEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/** 工程任务，所有写入必须使用所有者范围与修订号条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_engineering_task")
public class AiEngineeringTask extends TenantEntity {
    /** 任务编号。 */
    @TableId("task_id")
    private Long taskId;

    /** 任务所有者。 */
    private Long userId;

    /** 指定执行设备。 */
    private Long deviceId;

    /** 关联会话编号。 */
    private Long sessionId;

    /** 服务端聊天请求标识。 */
    private String requestId;

    /** 原任务编号。 */
    private Long parentTaskId;

    /** 受控动作。 */
    private String action;

    /** 用户作用域幂等键。 */
    private String idempotencyKey;

    /** 规范请求摘要。 */
    private String requestHash;

    /** 有界结构化请求。 */
    private String requestJson;

    /** 任务状态。 */
    private String state;

    /** 当前阶段。 */
    private String stage;

    /** 领取尝试编号。 */
    private Integer attempt;

    /** 比较交换修订号。 */
    private Long revision;

    /** 租约凭证摘要。 */
    @JsonIgnore @ToString.Exclude private String leaseHash;

    /** 租约到期时间。 */
    private LocalDateTime leaseExpiresAt;

    /** 运行截止时间。 */
    private LocalDateTime deadlineAt;

    /** 等待设备截止时间。 */
    private LocalDateTime waitingExpiresAt;

    /** 运行取消请求。 */
    private Boolean cancelRequested;

    /** 有界步骤回执日志。 */
    private String stepJournal;

    /** 已确认及预留字节总量。 */
    private Long reservedBytes;

    /** 已确认及预留文件数量。 */
    private Integer reservedFiles;

    /** 安全错误类别。 */
    private String errorCode;

    /** 中文安全错误摘要。 */
    private String errorMessage;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 修改时间。 */
    private LocalDateTime updatedAt;
}

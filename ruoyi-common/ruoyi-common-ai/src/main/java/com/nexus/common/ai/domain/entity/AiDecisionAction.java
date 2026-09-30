package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** ai_decision_action 表的持久化实体；JSON 列保持原始字符串。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_decision_action")
public class AiDecisionAction extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    private String tenantId;

    private Long runId;

    private String actionType;

    private String title;

    private String displayReason;

    private String targetBizType;

    private Long targetBizId;

    private String targetBizCode;

    private String payloadJson;

    /** NULL 为旧采购载荷；不得用旧 JSON 或客户端版本自动补值。 */
    @TableField("purchase_payload_version")
    private Integer purchasePayloadVersion;

    private String riskLevel;

    private String approvalRequired;

    private String approvalStatus;

    private String executionStatus;

    private String permissionCode;

    private String executorCode;

    private Integer sequenceNo;

    private String dependencyIdsJson;

    private String idempotencyKey;

    private Long approvedBy;

    private LocalDateTime approvedAt;

    private String approvalComment;

    private String resultBizType;

    private Long resultBizId;

    private String resultBizCode;

    private String resultJson;

    private Integer retryCount;

    private String lastErrorCode;

    private String lastErrorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Version
    private Integer version = 0;

    @TableLogic
    private String delFlag;

    private String attr1;

    private String attr2;

    private String attr3;

    private String attr4;
}

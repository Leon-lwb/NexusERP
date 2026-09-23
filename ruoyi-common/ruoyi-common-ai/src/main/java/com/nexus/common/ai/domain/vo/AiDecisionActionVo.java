package com.nexus.common.ai.domain.vo;

import com.nexus.common.ai.domain.entity.AiDecisionAction;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/** AiDecisionAction 的视图；列表请使用 Mapper 提供的轻量投影查询。 */
@Data
@AutoMapper(target = AiDecisionAction.class)
public class AiDecisionActionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long runId;

    private String actionType;

    private String title;

    private String displayReason;

    private String targetBizType;

    private Long targetBizId;

    private String targetBizCode;

    private String payloadJson;

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

    private Integer version;

    private Long createDept;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;
}

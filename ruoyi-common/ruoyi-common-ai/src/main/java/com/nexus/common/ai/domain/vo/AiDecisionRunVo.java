package com.nexus.common.ai.domain.vo;

import com.nexus.common.ai.domain.entity.AiDecisionRun;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/** AiDecisionRun 的视图；列表请使用 Mapper 提供的轻量投影查询。 */
@Data
@AutoMapper(target = AiDecisionRun.class)
public class AiDecisionRunVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String tenantId;

    private String sceneCode;

    private String bizType;

    private Long bizId;

    private String bizCode;

    private Long parentRunId;

    private Long supersededByRunId;

    private String triggerType;

    private String status;

    /** 真实运行的采购合同版本；旧运行保持空值，不由视图补写新版标记。 */
    private Integer decisionContractVersion;

    private BigDecimal riskScore;

    private String riskLevel;

    private String confidenceLevel;

    private String recommendedScenarioCode;

    private String inputSnapshotHash;

    private LocalDateTime snapshotTime;

    private LocalDateTime validUntil;

    private String resultJson;

    private String outcomeJson;

    private String summaryMode;

    private Long modelId;

    private String modelName;

    private String promptVersion;

    private Integer inputTokens;

    private Integer outputTokens;

    private BigDecimal estimatedCost;

    private Long analysisFlowInstanceId;

    private Long executionFlowInstanceId;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime analysisFinishedAt;

    private LocalDateTime finishedAt;

    private Integer version;

    private Long createDept;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;
}

package com.nexus.common.ai.domain.entity;

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

/** ai_decision_run 表的持久化实体；JSON 列保持原始字符串。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_decision_run")
public class AiDecisionRun extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("id")
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

    private BigDecimal riskScore;

    private String riskLevel;

    private String confidenceLevel;

    private String recommendedScenarioCode;

    private String inputSnapshotJson;

    private String inputSnapshotHash;

    private LocalDateTime snapshotTime;

    private LocalDateTime validUntil;

    private String scoreConfigJson;

    private String scenarioResultJson;

    private String narrativeJson;

    private String resultJson;

    private String outcomeJson;

    private String approvalContextJson;

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

    @Version
    private Integer version = 0;

    @TableLogic
    private String delFlag;

    private String attr1;

    private String attr2;

    private String attr3;

    private String attr4;
}

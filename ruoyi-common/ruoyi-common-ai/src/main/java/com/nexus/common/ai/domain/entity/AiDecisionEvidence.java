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

/** ai_decision_evidence 表的持久化实体；JSON 列保持原始字符串。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_decision_evidence")
public class AiDecisionEvidence extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    private String tenantId;

    private Long runId;

    private String evidenceKey;

    private String sourceDomain;

    private String sourceType;

    private Long sourceId;

    private String sourceCode;

    private String sourceName;

    private String metricCode;

    private String metricName;

    private String valueType;

    private String metricValue;

    private String unit;

    private LocalDateTime observedAt;

    private String dataVersion;

    private BigDecimal confidence;

    private String verifiable;

    private String normative;

    private String derivedFromJson;

    private String displayPath;

    @TableLogic
    private String delFlag;

    private String attr1;

    private String attr2;

    private String attr3;

    private String attr4;
}

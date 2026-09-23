package com.nexus.common.ai.domain.vo;

import com.nexus.common.ai.domain.entity.AiDecisionEvidence;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/** AiDecisionEvidence 的视图；列表请使用 Mapper 提供的轻量投影查询。 */
@Data
@AutoMapper(target = AiDecisionEvidence.class)
public class AiDecisionEvidenceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

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

    private Long createDept;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;
}

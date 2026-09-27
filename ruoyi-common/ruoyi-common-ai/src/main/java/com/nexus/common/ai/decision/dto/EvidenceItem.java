package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EvidenceItem(
    Long runId,
    String evidenceKey,
    String sourceDomain,
    String sourceType,
    Long sourceId,
    String sourceCode,
    String sourceName,
    String metricCode,
    String metricName,
    String valueType,
    String value,
    String unit,
    LocalDateTime observedAt,
    String dataVersion,
    BigDecimal confidence,
    boolean verifiable,
    boolean normative,
    List<String> derivedFromEvidenceKeys,
    String displayPath
) implements Serializable {
}

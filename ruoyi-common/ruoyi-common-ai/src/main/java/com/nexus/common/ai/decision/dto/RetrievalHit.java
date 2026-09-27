package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.time.LocalDate;

public record RetrievalHit(
    Long knowledgeId,
    Long documentId,
    Long chunkId,
    String documentName,
    String content,
    Double vectorScore,
    Integer keywordRank,
    Double rerankScore,
    String graphPath,
    String version,
    LocalDate effectiveDate,
    String sourceType
) implements Serializable {
}

package com.nexus.common.ai.decision.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DecisionApprovalRequest implements Serializable {
    private Integer runVersion;
    private List<Long> approvedActionIds;
    private List<Long> rejectedActionIds;
    @Size(max = 500)
    private String comment;
    private String expectedSnapshotHash;
}

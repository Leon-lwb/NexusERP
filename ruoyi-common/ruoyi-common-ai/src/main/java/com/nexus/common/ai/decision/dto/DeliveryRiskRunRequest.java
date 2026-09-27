package com.nexus.common.ai.decision.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionTriggerType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DeliveryRiskRunRequest implements Serializable {
    @NotNull
    private DecisionBusinessType bizType;
    @Positive
    private Long bizId;
    @NotNull
    @Min(1)
    @Max(90)
    private Integer horizonDays = 30;
    private List<String> scenarioCodes = List.of("BASELINE_CURRENT", "APS_EDD", "APS_CR", "APS_CR_EXPEDITE");
    private DecisionTriggerType triggerType = DecisionTriggerType.MANUAL;
    private String userQuestion;
    private String context;
    private Boolean forceRestart = false;

    /**
     * 时间窗口覆盖租户可见工单，而非单一业务记录。
     */
    @JsonIgnore
    @AssertTrue(message = "SHORTAGE_HORIZON 不允许 bizId，其他业务类型必须提供 bizId")
    public boolean isBusinessScopeValid() {
        return bizType == null || (bizType == DecisionBusinessType.SHORTAGE_HORIZON ? bizId == null : bizId != null);
    }
}

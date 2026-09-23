package com.nexus.common.ai.domain.bo;

import com.nexus.common.ai.domain.entity.AiDecisionRun;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 决策运行查询条件；时间边界作用于 createTime。 */
@Data
@AutoMapper(target = AiDecisionRun.class, reverseConvertGenerate = false)
public class AiDecisionRunBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String sceneCode;
    private String bizType;
    private Long bizId;
    private String bizCode;
    private String status;
    private String riskLevel;
    private Long createBy;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}

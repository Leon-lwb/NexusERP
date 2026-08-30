package com.nexus.erp.wm.domain.bo.pda;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/**
 * PDA 发料完成入参 (缺货逃生通道)
 * <p>
 * shortageLines 为空 = 要求整单领满后完成 (M1 语义);
 * 非空 = 声明的未领满行按缺货关闭, 单据直接完成, 库存按实发量已在行级拣货时过账。
 */
@Data
public class PdaIssueFinishBo {

    /** 缺货声明行 (可空) */
    @Valid
    private List<PdaIssueShortageBo> shortageLines;
}

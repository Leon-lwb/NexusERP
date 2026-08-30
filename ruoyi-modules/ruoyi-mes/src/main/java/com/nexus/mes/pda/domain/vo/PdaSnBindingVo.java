package com.nexus.mes.pda.domain.vo;

import lombok.Data;

/**
 * PDA SN 绑定查询结果
 */
@Data
public class PdaSnBindingVo {

    private Long bindingId;
    private String snCode;
    private String lotCode;
    private String materialCode;
    private String materialName;
    /** 绑定时间 (yyyy-MM-dd HH:mm:ss) */
    private String boundTime;
}

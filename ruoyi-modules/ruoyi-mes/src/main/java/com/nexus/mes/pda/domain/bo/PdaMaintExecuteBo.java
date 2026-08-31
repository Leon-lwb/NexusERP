package com.nexus.mes.pda.domain.bo;

import lombok.Data;

/**
 * PDA 保养执行提交 BO
 * <p>
 * result 缺省 NORMAL; content 缺省取计划保养内容。
 */
@Data
public class PdaMaintExecuteBo {

    /** 保养结果 NORMAL/ABNORMAL, 缺省 NORMAL */
    private String result;

    /** 保养内容/实际作业说明, 缺省取计划保养内容 */
    private String content;

    /** 备注(异常说明等) */
    private String remark;
}

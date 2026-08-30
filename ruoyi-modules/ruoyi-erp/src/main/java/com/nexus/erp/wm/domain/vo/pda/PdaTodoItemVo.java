package com.nexus.erp.wm.domain.vo.pda;

import lombok.Data;

/**
 * PDA 待办单据列表项 (扫码找单用, 四流程通用)
 */
@Data
public class PdaTodoItemVo {

    /** 单据ID (receiveId / issueId / transferId / planId) */
    private Long id;

    /** 单据号 (receiveCode / issueCode / transferNo / planCode) */
    private String code;

    private String status;

    /** 单据名称/摘要 */
    private String name;

    private String warehouseName;

    /** 业务日期(字符串, 如 2026-08-30) */
    private String date;

    /** 总行数 */
    private Integer lineCount;

    /** 已完成行数 */
    private Integer doneLineCount;
}

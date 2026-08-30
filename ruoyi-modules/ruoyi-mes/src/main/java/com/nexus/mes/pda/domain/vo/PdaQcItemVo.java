package com.nexus.mes.pda.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * PDA 三检待办列表项 (IQC/IPQC/OQC 通用)
 */
@Data
public class PdaQcItemVo {

    private Long id;
    /** 检验单号 */
    private String code;
    /** 主标题: 物料名称 */
    private String materialCode;
    private String materialName;
    /** 副信息: IQC=供应商 / IPQC=工单·工序 / OQC=销售订单ID */
    private String subInfo;
    /** 数量 */
    private BigDecimal quantity;
    /** 抽样数量 */
    private BigDecimal sampleQty;
    /** PENDING/INSPECTING */
    private String status;
}

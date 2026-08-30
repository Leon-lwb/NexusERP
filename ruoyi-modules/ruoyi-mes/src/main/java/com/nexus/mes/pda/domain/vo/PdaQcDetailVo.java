package com.nexus.mes.pda.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * PDA 三检单详情 (IQC/IPQC/OQC 通用; 无值字段为 null, 页面按域裁剪展示)
 */
@Data
public class PdaQcDetailVo {

    private Long id;
    /** 检验单号 */
    private String code;
    /** 检验域: iqc/ipqc/oqc */
    private String domain;
    /** PENDING/INSPECTING/COMPLETED */
    private String status;
    /** QUALIFIED/UNQUALIFIED (已检时) */
    private String inspectResult;

    private String materialCode;
    private String materialName;

    /** IQC: 供应商 */
    private String vendorName;
    /** IQC: 关联收货单ID */
    private Long receiveId;
    /** IPQC: 工单号/工序 */
    private String woNo;
    private String processName;
    /** OQC: 销售订单ID */
    private Long soId;

    /** 数量 */
    private BigDecimal quantity;
    /** 抽样数量 */
    private BigDecimal sampleQty;
    /** 不良数量(IPQC, 已检时) */
    private BigDecimal defectQty;
    /** 不良率%(IPQC, 已检时) */
    private BigDecimal defectRate;

    /** 已检回显 */
    private String defectInfo;
    private String inspectorName;
    private String inspectTime;
    private String remark;
    /** 检验照片附件 ossId 串(逗号分隔, 已检时) */
    private String photoOssIds;
}

package com.nexus.mes.qc.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * QC 检验明细录入项
 * <p>执行检验时随 inspect 请求提交: 按 detailId 定位明细行, 录入实测值(可选带样本量/备注)。</p>
 */
@Data
public class QcInspectEntryBo {
    /** 明细ID */
    private Long detailId;
    /** 实测值(定量: 数值或逗号分隔多值; 定性: OK/NG) */
    private String measuredValue;
    /** 样本量 */
    private BigDecimal sampleQty;
    /** 备注 */
    private String remark;
}

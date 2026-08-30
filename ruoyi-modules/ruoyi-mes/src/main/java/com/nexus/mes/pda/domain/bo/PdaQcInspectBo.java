package com.nexus.mes.pda.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * PDA 三检提交入参 (IQC/IPQC/OQC 共用; defectQty 仅 IPQC 消费)
 * <p>
 * photoOssIds: 检验照片附件 ossId 串(逗号分隔), 由 /resource/oss/upload 上传后回传。
 */
@Data
public class PdaQcInspectBo {

    /** 检验结果 QUALIFIED/UNQUALIFIED */
    @NotBlank(message = "检验结果不能为空")
    private String inspectResult;

    /** 不良描述(不合格时建议填写) */
    private String defectInfo;

    /** 抽样/检验数量(可空) */
    private BigDecimal sampleQty;

    /** 不良数量(仅 IPQC; 服务端自动计算不良率) */
    private BigDecimal defectQty;

    /** 备注 */
    private String remark;

    /** 检验照片附件 ossId 串(逗号分隔, 可空) */
    private String photoOssIds;
}

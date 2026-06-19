package com.nexus.mes.qc.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.qc.domain.QcIpqc;

import java.math.BigDecimal;

/**
 * MES过程检验业务对象 qc_ipqc
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = QcIpqc.class, reverseConvertGenerate = false)
public class QcIpqcBo extends BaseEntity {

    /** IPQC ID */
    private Long ipqcId;

    /** IPQC编号 */
    @NotBlank(message = "IPQC编号不能为空")
    private String ipqcNo;

    /** MES工单ID */
    @NotNull(message = "MES工单ID不能为空")
    private Long mesWoId;

    /** 工单编号 */
    private String woNo;

    /** 流转卡ID */
    private Long cardId;

    /** 流转卡工序记录ID */
    private Long stepRecordId;

    /** 工序ID */
    private Long processId;

    /** 工序编码 */
    private String processCode;

    /** 工序名称 */
    private String processName;

    /** 物料ID */
    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 抽样数量 */
    private BigDecimal sampleQty;

    /** 不良数量 */
    private BigDecimal defectQty;

    /** 检验结果 */
    private String inspectResult;

    /** 不良品信息(JSON) */
    private String defectInfo;

    /** 检验员ID */
    private Long inspectorId;

    /** 检验员姓名 */
    private String inspectorName;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}

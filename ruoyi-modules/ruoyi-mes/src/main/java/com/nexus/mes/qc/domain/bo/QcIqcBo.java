package com.nexus.mes.qc.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.qc.domain.QcIqc;

import java.math.BigDecimal;

/**
 * MES来料检验业务对象 qc_iqc
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = QcIqc.class, reverseConvertGenerate = false)
public class QcIqcBo extends BaseEntity {

    /** IQC ID */
    private Long iqcId;

    /** IQC编号 */
    @NotBlank(message = "IQC编号不能为空")
    private String iqcNo;

    /** ERP收货单ID */
    private Long receiveId;

    /** 物料ID */
    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 供应商ID */
    private Long vendorId;

    /** 供应商名称 */
    private String vendorName;

    /** 检验数量 */
    private BigDecimal quantity;

    /** 抽样数量 */
    private BigDecimal sampleQty;

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

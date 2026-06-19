package com.nexus.mes.qc.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.qc.domain.QcOqc;

import java.math.BigDecimal;

/**
 * MES出货检验业务对象 qc_oqc
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = QcOqc.class, reverseConvertGenerate = false)
public class QcOqcBo extends BaseEntity {

    /** OQC ID */
    private Long oqcId;

    /** OQC编号 */
    @NotBlank(message = "OQC编号不能为空")
    private String oqcNo;

    /** ERP销售订单ID */
    private Long soId;

    /** 物料ID */
    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

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

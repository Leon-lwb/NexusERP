package com.nexus.mes.lt.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.lt.domain.LtLot;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES批次业务对象 lt_lot
 * <p>属于 MES 批次管理模块，承载 批次主档 的增改入参。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = LtLot.class, reverseConvertGenerate = false)
public class LtLotBo extends BaseEntity {

    /** 批次ID */
    private Long lotId;

    /** 批次编码 */
    private String lotCode;

    /** 物料ID */
    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 供应商批次号 */
    private String supplierLot;

    /** 批次数量 */
    @NotNull(message = "批次数量不能为空")
    private BigDecimal quantity;

    /** 生产日期 */
    private Date producedDate;

    /** 有效期 */
    private Date expiryDate;

    /** 状态(查询用) */
    private String status;

    /** QC状态(查询用) */
    private String qcStatus;

    /** IQC检验单ID(查询用) */
    private Long iqcId;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}

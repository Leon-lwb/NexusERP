package com.nexus.mes.lt.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES批次主档对象 lt_lot
 * <p>属于 MES 批次管理模块，表达 物料批次 的领域模型。</p>
 * <p>状态机: IN_STOCK -> IN_USE -> CONSUMED
 *                  └-> QUARANTINED -> SCRAPPED</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lt_lot")
public class LtLot extends TenantEntity {

    /** 批次ID(主键) */
    @TableId(value = "lot_id")
    private Long lotId;

    /** 批次编码 */
    private String lotCode;

    /** 物料ID */
    private Long materialId;

    /** 物料编码(冗余) */
    private String materialCode;

    /** 物料名称(冗余) */
    private String materialName;

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称(冗余) */
    private String supplierName;

    /** 供应商批次号 */
    private String supplierLot;

    /** 批次数量 */
    private BigDecimal quantity;

    /** 已消耗数量 */
    private BigDecimal consumedQty;

    /** 生产日期 */
    private Date producedDate;

    /** 有效期 */
    private Date expiryDate;

    /** 状态: IN_STOCK/IN_USE/CONSUMED/QUARANTINED/SCRAPPED */
    private String status;

    /** QC状态: PENDING/PASS/FAIL */
    private String qcStatus;

    /** IQC检验单ID(关联qc_iqc.iqc_id,可空;关联后IQC完成自动回写qc_status) */
    private Long iqcId;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

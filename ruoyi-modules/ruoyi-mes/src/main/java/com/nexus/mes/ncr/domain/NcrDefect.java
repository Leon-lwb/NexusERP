package com.nexus.mes.ncr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES NCR缺陷明细对象 ncr_defect
 * <p>属于 MES NCR 不合格品管理模块，表达 NCR单 下的缺陷明细记录。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ncr_defect")
public class NcrDefect extends TenantEntity {

    /** 缺陷ID(主键) */
    @TableId(value = "defect_id")
    private Long defectId;

    /** NCR单ID */
    private Long ncrId;

    /** 缺陷代码ID */
    private Long defectCodeId;

    /** 缺陷编码(冗余) */
    private String defectCode;

    /** 缺陷名称(冗余) */
    private String defectName;

    /** 缺陷数量 */
    private BigDecimal defectQty;

    /** 缺陷原因 */
    private String defectCause;

    /** 根本原因描述 */
    private String rootCauseDesc;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
package com.nexus.mes.ncr.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.ncr.domain.NcrDefect;

import java.math.BigDecimal;

/**
 * MES NCR缺陷明细业务对象 ncr_defect
 * <p>属于 MES NCR 不合格品管理模块，承载 NCR 缺陷明细的增改入参。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = NcrDefect.class, reverseConvertGenerate = false)
public class NcrDefectBo extends BaseEntity {

    /** 缺陷ID */
    private Long defectId;

    /** NCR单ID(查询用) */
    private Long ncrId;

    /** 缺陷代码ID(查询用) */
    private Long defectCodeId;

    /** 缺陷编码 */
    private String defectCode;

    /** 缺陷名称 */
    private String defectName;

    /** 缺陷数量 */
    @NotNull(message = "缺陷数量不能为空")
    private BigDecimal defectQty;

    /** 缺陷原因(查询用) */
    private String defectCause;

    /** 根本原因描述 */
    private String rootCauseDesc;

    /** 批量删除ID集合 */
    private Long[] ids;
}
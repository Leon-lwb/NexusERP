package com.nexus.mes.eq.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.eq.domain.EqSparePart;

import java.math.BigDecimal;

/**
 * MES备件主数据业务对象 eq_spare_part
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EqSparePart.class, reverseConvertGenerate = false)
public class EqSparePartBo extends BaseEntity {

    /** 备件ID */
    private Long spareId;

    /** 备件编码 */
    @NotBlank(message = "备件编码不能为空")
    private String spareCode;

    /** 备件名称 */
    @NotBlank(message = "备件名称不能为空")
    private String spareName;

    /** 规格型号 */
    private String spec;

    /** 型号 */
    private String model;

    /** 单位 */
    private String unit;

    /** 分类 */
    private String category;

    /** 安全库存 */
    private BigDecimal safetyStock;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}

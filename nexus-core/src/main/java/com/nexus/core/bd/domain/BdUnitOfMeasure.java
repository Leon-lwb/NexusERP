package com.nexus.core.bd.domain;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 计量单位对象 bd_unit_of_measure
 *
 * @author nexus
 * @date 2025-09-30
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.bd.mapper.BdUnitOfMeasureMapper.BdUnitOfMeasureResult")
public class BdUnitOfMeasure extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 单位ID
     */
    @TableId(value = "uom_id", type = IdType.AUTO)
    private Long uomId;
    /**
     * 单位编码
     */
    @Excel(name = "单位编码")
    private String uomCode;
    /**
     * 单位名称
     */
    @Excel(name = "单位名称")
    private String uomName;
    /**
     * 单位符号(kg、m、pcs等)
     */
    @Excel(name = "单位符号(kg、m、pcs等)")
    private String uomSymbol;
    /**
     * 单位类别(重量、长度、数量等)
     */
    @Excel(name = "单位类别(重量、长度、数量等)")
    private String category;
    /**
     * 是否基本单位 0:否 1:是
     */
    @Excel(name = "是否基本单位 0:否 1:是")
    private Long isBase;
    /**
     * 基本单位ID
     */
    private Long primaryId;
    @Excel(name = "基本单位名称")
    private String primaryName;
    /**
     * 转换率(相对于基本单位)
     */
    private BigDecimal conversionRate;
    /**
     * 状态 0:停用 1:启用
     */
    @Excel(name = "状态 0:停用 1:启用")
    private Long status;
    /**
     * 排序
     */
    @Excel(name = "排序")
    private Long sortOrder;
}
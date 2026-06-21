package com.nexus.erp.bd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * BOM明细对象 bd_bom_detail
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_bom_detail")
public class BdBomDetail extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 明细ID
     */
    @TableId(value = "detail_id")
    private Long detailId;

    /**
     * BOM主表ID
     */
    @ExcelProperty("BOM主表ID")
    private Long bomId;

    /**
     * 子项物料ID
     */
    @ExcelProperty("子项物料ID")
    private Long childMaterialId;

    /**
     * 单位父项用量
     */
    @ExcelProperty("单位父项用量")
    private BigDecimal quantityPerParent;

    /**
     * 损耗率(%)
     */
    @ExcelProperty("损耗率(%)")
    private BigDecimal lossRate;

    /**
     * 工序步骤(软引用工艺路线步骤序号,制造工序/工位关联已迁移至 MES MBOM)
     */
    @ExcelProperty("工序步骤")
    private Integer operationStep;

    /**
     * 提前期(小时)
     */
    @ExcelProperty("提前期(小时)")
    private Integer leadTime;

    /**
     * 生效日期
     */
    @ExcelProperty("生效日期")
    private LocalDate effectiveDate;

    /**
     * 失效日期
     */
    @ExcelProperty("失效日期")
    private LocalDate expiryDate;

    /**
     * 显示排序
     */
    @ExcelProperty("显示排序")
    private Integer sortOrder;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 来源类型
     */
    @ExcelProperty("来源类型")
    private String sourceType;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Integer attr3;

    /**
     * 预留字段4
     */
    private Integer attr4;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

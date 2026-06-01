package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdBom;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 产品BOM视图对象 bd_bom
 *
 * @author nexus
 * @date 2025-10-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdBom.class)
public class BdBomVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * BOM主表ID
     */
    @ExcelProperty(value = "BOM主表ID")
    private Long bomId;

    /**
     * 父项物料ID
     */
    private Long parentMaterialId;

    /**
     * 父项物料编码
     */
    private String parentMaterialCode;

    /**
     * 父项物料名称
     */
    private String parentMaterialName;

    /**
     * BOM版本号
     */
    @ExcelProperty(value = "BOM版本号")
    private String bomVersion;

    /**
     * BOM名称
     */
    @ExcelProperty(value = "BOM名称")
    private String bomName;

    /**
     * 生效日期
     */
    @ExcelProperty(value = "生效日期")
    private Date effectiveDate;

    /**
     * 失效日期
     */
    @ExcelProperty(value = "失效日期")
    private Date expiryDate;

    /**
     * 是否当前版本
     */
    @ExcelProperty(value = "是否当前版本")
    private Long isCurrent;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private Long status;

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
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 来源类型（PROCUREMENT-采购, PROCESS-加工）
     */
    @ExcelProperty(value = "来源类型")
    private String sourceType;

}

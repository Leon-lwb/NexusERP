package com.nexus.mes.release.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.mes.release.domain.ReleasePkgBomItem;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MES发布包BOM选配视图对象 mes_release_pkg_bom_item
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReleasePkgBomItem.class)
public class ReleasePkgBomItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long itemId;

    /** 发布包ID */
    private Long packageId;

    /** 操作类型: REPLACE/ADD/REMOVE */
    @ExcelProperty("操作类型")
    private String actionType;

    /** 原物料ID(REPLACE/REMOVE 定位键) */
    private Long childMaterialId;

    /** 原物料编码 */
    @ExcelProperty("原物料编码")
    private String childMaterialCode;

    /** 原物料名称 */
    @ExcelProperty("原物料名称")
    private String childMaterialName;

    /** 标准BOM明细行ID(可选精确定位) */
    private Long bomDetailId;

    /** 目标物料ID */
    private Long targetMaterialId;

    /** 目标物料编码 */
    @ExcelProperty("目标物料编码")
    private String targetMaterialCode;

    /** 目标物料名称 */
    @ExcelProperty("目标物料名称")
    private String targetMaterialName;

    /** 单位用量(空=REPLACE沿用原行) */
    @ExcelProperty("单位用量")
    private BigDecimal quantityPerParent;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;
}

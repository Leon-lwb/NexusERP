package com.nexus.mes.release.domain.bo;

import com.nexus.mes.release.domain.ReleasePkgBomItem;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * MES发布包BOM选配业务对象 mes_release_pkg_bom_item
 *
 * @author Nexus
 */
@Data
@AutoMapper(target = ReleasePkgBomItem.class, reverseConvertGenerate = false)
public class ReleasePkgBomItemBo {

    /** 主键ID(保存时忽略, 先删后插) */
    private Long itemId;

    /** 操作类型: REPLACE/ADD/REMOVE */
    @NotBlank(message = "操作类型不能为空")
    private String actionType;

    /** 原物料ID(REPLACE/REMOVE 定位键) */
    private Long childMaterialId;

    /** 原物料编码(冗余) */
    private String childMaterialCode;

    /** 原物料名称(冗余) */
    private String childMaterialName;

    /** 标准BOM明细行ID(可选精确定位) */
    private Long bomDetailId;

    /** 目标物料ID(REPLACE/ADD 必填) */
    private Long targetMaterialId;

    /** 目标物料编码(冗余) */
    private String targetMaterialCode;

    /** 目标物料名称(冗余) */
    private String targetMaterialName;

    /** 单位用量(空=REPLACE沿用原行/ADD必填) */
    private BigDecimal quantityPerParent;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;
}

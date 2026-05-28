package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdMaterialCategory;

/**
 * 物料产品分类业务对象 bd_material_category
 *
 * @author ruoyi
 * @date 2025-10-01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdMaterialCategory.class, reverseConvertGenerate = false)
public class BdMaterialCategoryBo extends BaseEntity {

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 父分类ID
     */
    private Long parentId;

    /**
     * 分类编码
     */
    @NotBlank(message = "分类编码不能为空")
    private String categoryCode;

    /**
     * 分类名称
     */
    @NotBlank(message = "分类名称不能为空")
    private String categoryName;

    /**
     * 分类描述
     */
    private String categoryDesc;

    /**
     * 层级深度
     */
    private Long levelNum;

    /**
     * 路径ID串
     */
    private String pathIds;

    /**
     * 路径名称
     */
    private String pathNames;

    /**
     * 显示排序
     */
    private Long sortOrder;

    /**
     * 状态
     */
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
     * 批量删除ID集合
     */
    private Long[] ids;

}

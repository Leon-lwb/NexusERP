package com.nexus.core.bd.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 物料产品分类对象 bd_material_category
 *
 * @author nexus
 * @date 2025-10-01
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.bd.mapper.BdMaterialCategoryMapper.BdMaterialCategoryResult")
public class BdMaterialCategory extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 分类ID */
    @TableId(value = "category_id",type = IdType.AUTO)
    private Long categoryId;
    /** 父分类ID */
    private Long parentId;
    /** 分类编码 */
    @Excel(name = "分类编码")
    private String categoryCode;
    /** 分类名称 */
    @Excel(name = "分类名称")
    private String categoryName;
    /** 分类描述 */
    @Excel(name = "分类描述")
    private String categoryDesc;
    /** 层级深度 */
    private Long levelNum;
    /** 路径ID串 */
    private String pathIds;
    /** 路径名称 */
    @Excel(name = "路径名称")
    private String pathNames;
    /** 显示排序 */
    @Excel(name = "显示排序")
    private Long sortOrder;
    /** 状态 */
    @Excel(name = "状态")
    private Long status;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
}
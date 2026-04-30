package org.dromara.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.bd.domain.BdMaterialCategory;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 物料产品分类视图对象 bd_material_category
 *
 * @author ruoyi
 * @date 2025-10-01
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdMaterialCategory.class)
public class BdMaterialCategoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分类ID
     */
    @ExcelProperty(value = "分类ID")
    private Long categoryId;

    /**
     * 父分类ID
     */
    private Long parentId;

    /**
     * 父分类名称
     */
    @ExcelProperty(value = "父分类名称")
    private String parentName;

    /**
     * 分类编码
     */
    @ExcelProperty(value = "分类编码")
    private String categoryCode;

    /**
     * 分类名称
     */
    @ExcelProperty(value = "分类名称")
    private String categoryName;

    /**
     * 分类描述
     */
    @ExcelProperty(value = "分类描述")
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
    @ExcelProperty(value = "路径名称")
    private String pathNames;

    /**
     * 显示排序
     */
    @ExcelProperty(value = "显示排序")
    private Long sortOrder;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private Long status;

    /**
     * 子分类列表
     */
    private List<BdMaterialCategoryVo> children;

}

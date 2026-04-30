package org.dromara.erp.bd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * 物料产品分类对象 bd_material_category
 *
 * @author nexus
 * @date 2025-10-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_material_category")
public class BdMaterialCategory extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 分类ID
     */
    @TableId(value = "category_id")
    private Long categoryId;

    /**
     * 父分类ID
     */
    private Long parentId;

    /**
     * 分类编码
     */
    @ExcelProperty("分类编码")
    private String categoryCode;

    /**
     * 分类名称
     */
    @ExcelProperty("分类名称")
    private String categoryName;

    /**
     * 分类描述
     */
    @ExcelProperty("分类描述")
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
    @ExcelProperty("路径名称")
    private String pathNames;

    /**
     * 显示排序
     */
    @ExcelProperty("显示排序")
    private Long sortOrder;

    /**
     * 状态
     */
    @ExcelProperty("状态")
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
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}

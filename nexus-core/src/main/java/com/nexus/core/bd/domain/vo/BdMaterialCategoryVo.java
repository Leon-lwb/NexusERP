
package com.nexus.core.bd.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

/**
 * 物料产品分类对象 bd_material_category vo
 *
 * @author nexus
 * @date 2025-10-01
 */
@Data
public class BdMaterialCategoryVo extends BasePageDtoRequest {

    /**
     * 分类ID
     */
    private Long categoryId;
    /**
     * 分类编码
     */
    private String categoryCode;
    /**
     * 分类名称
     */
    private String categoryName;
    /**
     * 分类描述
     */
    private String categoryDesc;
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

}
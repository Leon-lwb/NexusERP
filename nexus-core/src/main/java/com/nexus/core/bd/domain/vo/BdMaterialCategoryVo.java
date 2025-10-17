
package com.nexus.core.bd.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.util.List;

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
     * 分类ID集合
     */
    private List<Long> categoryIds;

}
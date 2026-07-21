package com.nexus.erp.bd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 类别与物料联合视图对象（VO）
 * <p>用于物料类别树与所属物料的一次性聚合返回，便于前端联动展示类别及其下物料。</p>
 */
@Data
public class CategoryWithMaterialsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 物料类别列表 */
    private List<BdMaterialCategoryVo> categories;
    /** 物料列表 */
    private List<BdMaterialVo> materials;
}

package com.nexus.erp.wm.domain.vo;

import lombok.Data;
import com.nexus.erp.bd.domain.vo.BdMaterialCategoryVo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分类与库存视图对象
 *
 * @author nexus
 */
@Data
public class CategoryWithStockVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<BdMaterialCategoryVo> categories;
    private List<WmMaterialStockVo> stocks;
}

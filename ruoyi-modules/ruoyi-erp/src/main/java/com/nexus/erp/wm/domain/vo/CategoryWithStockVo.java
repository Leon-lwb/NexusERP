package com.nexus.erp.wm.domain.vo;

import lombok.Data;
import com.nexus.erp.bd.domain.BdMaterialCategory;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分类与库存视图对象
 *
 * @author ruoyi
 */
@Data
public class CategoryWithStockVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<BdMaterialCategory> categories;
    private List<WmMaterialStockVo> stocks;
}

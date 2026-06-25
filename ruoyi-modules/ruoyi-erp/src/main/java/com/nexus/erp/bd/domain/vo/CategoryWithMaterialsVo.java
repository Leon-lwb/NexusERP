package com.nexus.erp.bd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class CategoryWithMaterialsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<BdMaterialCategoryVo> categories;
    private List<BdMaterialVo> materials;
}

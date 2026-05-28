package com.nexus.erp.bd.domain.vo;

import lombok.Data;
import com.nexus.erp.bd.domain.BdMaterialCategory;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class CategoryWithMaterialsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<BdMaterialCategory> categories;
    private List<BdMaterialVo> materials;
}

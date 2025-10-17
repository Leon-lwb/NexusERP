package com.nexus.core.bd.domain.vo;

import com.nexus.core.bd.domain.BdMaterial;
import com.nexus.core.bd.domain.BdMaterialCategory;

import java.util.List;

public class CategoryWithMaterialsVo {
    private List<BdMaterialCategory> categories;
    private List<BdMaterial> materials;
    
    // getters and setters
    public List<BdMaterialCategory> getCategories() {
        return categories;
    }
    
    public void setCategories(List<BdMaterialCategory> categories) {
        this.categories = categories;
    }
    
    public List<BdMaterial> getMaterials() {
        return materials;
    }
    
    public void setMaterials(List<BdMaterial> materials) {
        this.materials = materials;
    }
}

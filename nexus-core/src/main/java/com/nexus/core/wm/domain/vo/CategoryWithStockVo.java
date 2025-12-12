package com.nexus.core.wm.domain.vo;

import com.nexus.core.bd.domain.BdMaterialCategory;
import com.nexus.core.wm.domain.WmMaterialStock;

import java.util.List;

public class CategoryWithStockVo {
    private List<BdMaterialCategory> categories;
    private List<WmMaterialStock> stocks;

    // getters and setters
    public List<BdMaterialCategory> getCategories() {
        return categories;
    }

    public void setCategories(List<BdMaterialCategory> categories) {
        this.categories = categories;
    }

    public List<WmMaterialStock> getStocks() {
        return stocks;
    }

    public void setStocks(List<WmMaterialStock> stocks) {
        this.stocks = stocks;
    }
}
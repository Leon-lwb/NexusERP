package com.nexus.erp.wm.service;

import com.nexus.erp.wm.domain.WmMaterialStock;

import java.math.BigDecimal;
import java.util.List;

public interface IPickingStrategyService {

    List<WmMaterialStock> selectStocks(Long materialId, Long warehouseId, BigDecimal quantity);
}

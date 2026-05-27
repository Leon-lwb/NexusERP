package org.dromara.erp.wm.service;

import org.dromara.erp.wm.domain.WmMaterialStock;

import java.math.BigDecimal;
import java.util.List;

public interface IPickingStrategyService {

    List<WmMaterialStock> selectStocks(Long materialId, Long warehouseId, BigDecimal quantity);
}

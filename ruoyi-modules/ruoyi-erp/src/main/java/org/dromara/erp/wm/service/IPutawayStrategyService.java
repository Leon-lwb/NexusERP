package org.dromara.erp.wm.service;

import org.dromara.erp.wm.domain.WmStorageLocation;

import java.math.BigDecimal;

public interface IPutawayStrategyService {

    WmStorageLocation recommendLocation(Long materialId, Long warehouseId, BigDecimal quantity);
}

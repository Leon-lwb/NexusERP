package com.nexus.erp.wm.service;

import com.nexus.erp.wm.domain.WmStorageLocation;

import java.math.BigDecimal;

public interface IPutawayStrategyService {

    WmStorageLocation recommendLocation(Long materialId, Long warehouseId, BigDecimal quantity);
}

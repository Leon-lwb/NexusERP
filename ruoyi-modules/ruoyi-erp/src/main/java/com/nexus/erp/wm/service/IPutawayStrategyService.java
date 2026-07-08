package com.nexus.erp.wm.service;

import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.vo.PutawayLocation;

import java.math.BigDecimal;

public interface IPutawayStrategyService {

    WmStorageLocation recommendLocation(Long materialId, Long warehouseId, BigDecimal quantity);

    /**
     * 规则驱动解析上架目标仓库与推荐库位
     * <p>
     * 按物料类别全局匹配上架规则（不依赖外部传入仓库），规则绑定的 warehouseId 作为目标仓库，
     * 再在该仓库内按策略类型推荐具体库位。供 MES 完工入库等"未知仓库"场景使用。
     *
     * @param materialId 物料ID
     * @param quantity   上架数量
     * @return 目标仓库 + 推荐库位
     */
    PutawayLocation resolvePutaway(Long materialId, BigDecimal quantity);
}

package com.nexus.erp.wm.service;

import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.vo.PutawayLocation;

import java.math.BigDecimal;
/**
 * 上架策略服务接口
 * <p>
 * 定义上架策略的业务契约，由对应的 ServiceImpl 实现。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

public interface IPutawayStrategyService {

    /**
     * 上架策略处理（recommendLocation）
     *
     * @param materialId 物料 ID
     * @param warehouseId 仓库 ID
     * @param quantity 数量
     * @return 结果
     */
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

package com.nexus.erp.so.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 价格引擎Service接口
 *
 * @author nexus
 */
public interface IPricingEngineService {

    /**
     * 根据客户、物料、数量和日期解析最优价格
     * <p>
     * 优先级：客户专属价 > 期间促销价 > 标准基价 > 物料参考价
     *
     * @param clientId  客户ID（可为null）
     * @param materialId 物料ID
     * @param qty       数量
     * @param orderDate 订单日期
     * @return 解析后的最终单价（已应用折扣率）
     */
    BigDecimal resolvePrice(Long clientId, Long materialId, BigDecimal qty, LocalDate orderDate);
}

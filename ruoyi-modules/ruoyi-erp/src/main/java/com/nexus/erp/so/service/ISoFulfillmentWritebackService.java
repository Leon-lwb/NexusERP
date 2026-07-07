package com.nexus.erp.so.service;

import java.math.BigDecimal;

/**
 * 销售订单履行数量回写Service接口
 * <p>
 * 销售出库、生产完工等下游业务完成时，通过本服务回写销售订单行的
 * 已发货/已履行/已投产数量，供"履行(fulfill)"门禁校验与进度展示使用。
 *
 * @author nexus
 */
public interface ISoFulfillmentWritebackService {

    /**
     * 销售出库回写：递增销售订单行的已发货数量与已履行数量。
     * <p>两者同步递增——销售出库即视为对该行的履行交付。</p>
     *
     * @param soLineId 销售订单行ID
     * @param qty      本次出库数量
     */
    void addShippedQty(Long soLineId, BigDecimal qty);

    /**
     * 生产完工回写：递增销售订单行的已投产数量。
     * <p>仅自制物料行有意义，表示该行已生产完成入库（尚未发货）。</p>
     *
     * @param soLineId 销售订单行ID
     * @param qty      本次完工数量
     */
    void addProducedQty(Long soLineId, BigDecimal qty);
}

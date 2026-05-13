package org.dromara.erp.wm.service;

/**
 * 库存过账服务
 *
 * @author ruoyi
 */
public interface IWmStockOperationService {

    /**
     * 物料入库单过账入库存并生成流水
     *
     * @param receiveId 入库单ID
     */
    void postMaterialReceive(Long receiveId);

    /**
     * 销售出库单过账扣库存并生成流水
     *
     * @param issueId 出库单ID
     */
    void postSalesIssue(Long issueId);

}

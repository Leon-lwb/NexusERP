package com.nexus.core.wm.service;

import java.util.List;
import com.nexus.core.wm.domain.WmMaterialStockTx;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 库存事务Service接口
 * 
 * @author nexus
 * @date 2025-12-12
 */
public interface IWmMaterialStockTxService extends IService<WmMaterialStockTx> {
    /**
     * 查询库存事务
     * 
     * @param txId 库存事务主键
     * @return 库存事务
     */
    public WmMaterialStockTx selectWmMaterialStockTxByTxId(Long txId);

    /**
     * 查询库存事务列表
     * 
     * @param wmMaterialStockTx 库存事务
     * @return 库存事务集合
     */
    public List<WmMaterialStockTx> selectWmMaterialStockTxList(WmMaterialStockTx wmMaterialStockTx);

    /**
     * 新增库存事务
     * 
     * @param wmMaterialStockTx 库存事务
     * @return 结果
     */
    public int insertWmMaterialStockTx(WmMaterialStockTx wmMaterialStockTx);

    /**
     * 修改库存事务
     * 
     * @param wmMaterialStockTx 库存事务
     * @return 结果
     */
    public int updateWmMaterialStockTx(WmMaterialStockTx wmMaterialStockTx);

    /**
     * 批量删除库存事务
     * 
     * @param txIds 需要删除的库存事务主键集合
     * @return 结果
     */
    public int deleteWmMaterialStockTxByTxIds(Long[] txIds);

    /**
     * 删除库存事务信息
     * 
     * @param txId 库存事务主键
     * @return 结果
     */
    public int deleteWmMaterialStockTxByTxId(Long txId);
}
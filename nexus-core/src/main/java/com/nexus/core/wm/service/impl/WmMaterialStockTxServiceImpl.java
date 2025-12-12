package com.nexus.core.wm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.core.wm.domain.WmMaterialStockTx;
import com.nexus.core.wm.service.IWmMaterialStockTxService;

/**
 * 库存事务Service业务层处理
 * 
 * @author nexus
 * @date 2025-12-12
 */
@Service
public class WmMaterialStockTxServiceImpl extends ServiceImpl<WmMaterialStockTxMapper, WmMaterialStockTx> implements IWmMaterialStockTxService {

    @Autowired
    private WmMaterialStockTxMapper wmMaterialStockTxMapper;

    /**
     * 查询库存事务
     * 
     * @param txId 库存事务主键
     * @return 库存事务
     */
    @Override
    public WmMaterialStockTx selectWmMaterialStockTxByTxId(Long txId) {
        return wmMaterialStockTxMapper.selectWmMaterialStockTxByTxId(txId);
    }

    /**
     * 查询库存事务列表
     * 
     * @param wmMaterialStockTx 库存事务
     * @return 库存事务
     */
    @Override
    public List<WmMaterialStockTx> selectWmMaterialStockTxList(WmMaterialStockTx wmMaterialStockTx) {
        return wmMaterialStockTxMapper.selectWmMaterialStockTxList(wmMaterialStockTx);
    }

    /**
     * 新增库存事务
     * 
     * @param wmMaterialStockTx 库存事务
     * @return 结果
     */
    @Override
    public int insertWmMaterialStockTx(WmMaterialStockTx wmMaterialStockTx) {
        wmMaterialStockTx.setCreateTime(DateUtils.getNowDate());
        return wmMaterialStockTxMapper.insertWmMaterialStockTx(wmMaterialStockTx);
    }

    /**
     * 修改库存事务
     * 
     * @param wmMaterialStockTx 库存事务
     * @return 结果
     */
    @Override
    public int updateWmMaterialStockTx(WmMaterialStockTx wmMaterialStockTx) {
        wmMaterialStockTx.setUpdateTime(DateUtils.getNowDate());
        return wmMaterialStockTxMapper.updateWmMaterialStockTx(wmMaterialStockTx);
    }

    /**
     * 批量删除库存事务
     * 
     * @param txIds 需要删除的库存事务主键
     * @return 结果
     */
    @Override
    public int deleteWmMaterialStockTxByTxIds(Long[] txIds) {
        return wmMaterialStockTxMapper.deleteWmMaterialStockTxByTxIds(txIds);
    }

    /**
     * 删除库存事务信息
     * 
     * @param txId 库存事务主键
     * @return 结果
     */
    @Override
    public int deleteWmMaterialStockTxByTxId(Long txId) {
        return wmMaterialStockTxMapper.deleteWmMaterialStockTxByTxId(txId);
    }
}
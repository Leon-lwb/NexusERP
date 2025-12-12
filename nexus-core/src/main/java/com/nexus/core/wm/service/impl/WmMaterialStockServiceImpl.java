package com.nexus.core.wm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.wm.mapper.WmMaterialStockMapper;
import com.nexus.core.wm.domain.WmMaterialStock;
import com.nexus.core.wm.service.IWmMaterialStockService;

/**
 * 库存清单Service业务层处理
 *
 * @author nexus
 * @date 2025-12-03
 */
@Service
public class WmMaterialStockServiceImpl extends ServiceImpl<WmMaterialStockMapper, WmMaterialStock> implements IWmMaterialStockService {

    @Autowired
    private WmMaterialStockMapper wmMaterialStockMapper;

    /**
     * 查询库存清单
     *
     * @param stockId 库存清单主键
     * @return 库存清单
     */
    @Override
    public WmMaterialStock selectWmMaterialStockByStockId(Long stockId) {
        return wmMaterialStockMapper.selectWmMaterialStockByStockId(stockId);
    }

    /**
     * 查询库存清单列表
     *
     * @param wmMaterialStock 库存清单
     * @return 库存清单
     */
    @Override
    public List<WmMaterialStock> selectWmMaterialStockList(WmMaterialStock wmMaterialStock) {
        return wmMaterialStockMapper.selectWmMaterialStockList(wmMaterialStock);
    }

    /**
     * 新增库存清单
     *
     * @param wmMaterialStock 库存清单
     * @return 结果
     */
    @Override
    public int insertWmMaterialStock(WmMaterialStock wmMaterialStock) {
                wmMaterialStock.setCreateTime(DateUtils.getNowDate());
            return wmMaterialStockMapper.insertWmMaterialStock(wmMaterialStock);
    }

    /**
     * 修改库存清单
     *
     * @param wmMaterialStock 库存清单
     * @return 结果
     */
    @Override
    public int updateWmMaterialStock(WmMaterialStock wmMaterialStock) {
                wmMaterialStock.setUpdateTime(DateUtils.getNowDate());
        return wmMaterialStockMapper.updateWmMaterialStock(wmMaterialStock);
    }

    /**
     * 批量删除库存清单
     *
     * @param stockIds 需要删除的库存清单主键
     * @return 结果
     */
    @Override
    public int deleteWmMaterialStockByStockIds(Long[] stockIds) {
        return wmMaterialStockMapper.deleteWmMaterialStockByStockIds(stockIds);
    }

    /**
     * 删除库存清单信息
     *
     * @param stockId 库存清单主键
     * @return 结果
     */
    @Override
    public int deleteWmMaterialStockByStockId(Long stockId)
    {
        return wmMaterialStockMapper.deleteWmMaterialStockByStockId(stockId);
    }

    @Override
    public List<WmMaterialStock> selectByCategoryIds(List<Long> categoryIds) {
        return wmMaterialStockMapper.selectByCategoryIds(categoryIds);
    }
}

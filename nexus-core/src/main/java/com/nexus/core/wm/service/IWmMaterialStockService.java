package com.nexus.core.wm.service;

import java.util.List;
import com.nexus.core.wm.domain.WmMaterialStock;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 库存清单Service接口
 *
 * @author nexus
 * @date 2025-12-03
 */
public interface IWmMaterialStockService extends IService<WmMaterialStock> {
    /**
     * 查询库存清单
     *
     * @param stockId 库存清单主键
     * @return 库存清单
     */
    public WmMaterialStock selectWmMaterialStockByStockId(Long stockId);

    /**
     * 查询库存清单列表
     *
     * @param wmMaterialStock 库存清单
     * @return 库存清单集合
     */
    public List<WmMaterialStock> selectWmMaterialStockList(WmMaterialStock wmMaterialStock);

    /**
     * 新增库存清单
     *
     * @param wmMaterialStock 库存清单
     * @return 结果
     */
    public int insertWmMaterialStock(WmMaterialStock wmMaterialStock);

    /**
     * 修改库存清单
     *
     * @param wmMaterialStock 库存清单
     * @return 结果
     */
    public int updateWmMaterialStock(WmMaterialStock wmMaterialStock);

    /**
     * 批量删除库存清单
     *
     * @param stockIds 需要删除的库存清单主键集合
     * @return 结果
     */
    public int deleteWmMaterialStockByStockIds(Long[] stockIds);

    /**
     * 删除库存清单信息
     *
     * @param stockId 库存清单主键
     * @return 结果
     */
    public int deleteWmMaterialStockByStockId(Long stockId);
}

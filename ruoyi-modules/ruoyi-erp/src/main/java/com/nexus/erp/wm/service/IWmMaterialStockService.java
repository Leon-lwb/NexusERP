package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMaterialStockBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;

import java.util.List;

/**
 * 库存清单Service接口
 *
 * @author ruoyi
 * @date 2025-12-03
 */
public interface IWmMaterialStockService {

    /**
     * 分页查询库存清单列表
     *
     * @param bo         库存清单业务对象
     * @param pageQuery  分页参数
     * @return 库存清单分页集合
     */
    TableDataInfo<WmMaterialStockVo> selectPageList(WmMaterialStockBo bo, PageQuery pageQuery);

    /**
     * 查询库存清单列表
     *
     * @param bo 库存清单业务对象
     * @return 库存清单集合
     */
    List<WmMaterialStockVo> selectList(WmMaterialStockBo bo);

    /**
     * 查询库存清单
     *
     * @param id 库存清单主键
     * @return 库存清单
     */
    WmMaterialStockVo selectById(Long id);

    /**
     * 新增库存清单
     *
     * @param bo 库存清单业务对象
     * @return 结果
     */
    int insertByBo(WmMaterialStockBo bo);

    /**
     * 修改库存清单
     *
     * @param bo 库存清单业务对象
     * @return 结果
     */
    int updateByBo(WmMaterialStockBo bo);

    /**
     * 批量删除库存清单
     *
     * @param ids 需要删除的库存清单主键集合
     * @return 结果
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据分类ID列表查询库存清单
     *
     * @param categoryIds 分类ID列表
     * @return 库存清单集合
     */
    List<WmMaterialStockVo> selectByCategoryIds(List<Long> categoryIds);
}

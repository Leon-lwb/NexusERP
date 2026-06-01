package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMaterialStockTxBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockTxVo;

import java.util.List;

/**
 * 库存事务Service接口
 *
 * @author nexus
 * @date 2025-12-12
 */
public interface IWmMaterialStockTxService {

    /**
     * 分页查询库存事务列表
     *
     * @param bo         库存事务业务对象
     * @param pageQuery  分页参数
     * @return 库存事务分页集合
     */
    TableDataInfo<WmMaterialStockTxVo> selectPageList(WmMaterialStockTxBo bo, PageQuery pageQuery);

    /**
     * 查询库存事务列表
     *
     * @param bo 库存事务业务对象
     * @return 库存事务集合
     */
    List<WmMaterialStockTxVo> selectList(WmMaterialStockTxBo bo);

    /**
     * 查询库存事务
     *
     * @param id 库存事务主键
     * @return 库存事务
     */
    WmMaterialStockTxVo selectById(Long id);

    /**
     * 新增库存事务
     *
     * @param bo 库存事务业务对象
     * @return 结果
     */
    int insertByBo(WmMaterialStockTxBo bo);

    /**
     * 修改库存事务
     *
     * @param bo 库存事务业务对象
     * @return 结果
     */
    int updateByBo(WmMaterialStockTxBo bo);

    /**
     * 批量删除库存事务
     *
     * @param ids 需要删除的库存事务主键集合
     * @return 结果
     */
    int deleteByIds(Long[] ids);
}

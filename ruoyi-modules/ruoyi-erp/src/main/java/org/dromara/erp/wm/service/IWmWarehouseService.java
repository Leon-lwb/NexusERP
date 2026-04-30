package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.WmWarehouse;
import org.dromara.erp.wm.domain.bo.WmWarehouseBo;
import org.dromara.erp.wm.domain.vo.WmWarehouseVo;

import java.util.List;

/**
 * 仓库设置Service接口
 *
 * @author ruoyi
 * @date 2025-09-30
 */
public interface IWmWarehouseService {

    /**
     * 分页查询仓库设置列表
     */
    TableDataInfo<WmWarehouseVo> selectPageList(WmWarehouseBo bo, PageQuery pageQuery);

    /**
     * 查询仓库设置列表
     */
    List<WmWarehouseVo> selectList(WmWarehouseBo bo);

    /**
     * 查询仓库设置
     */
    WmWarehouseVo selectById(Long id);

    /**
     * 新增仓库设置
     */
    int insertByBo(WmWarehouseBo bo);

    /**
     * 修改仓库设置
     */
    int updateByBo(WmWarehouseBo bo);

    /**
     * 批量删除仓库设置
     */
    int deleteByIds(Long[] ids);

    /**
     * 获取仓库树
     */
    List<WmWarehouse> getWareHouseTree();
}

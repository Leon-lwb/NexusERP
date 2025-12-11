package com.nexus.core.wm.service;

import java.util.List;
import com.nexus.core.wm.domain.WmWarehouse;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 仓库设置Service接口
 *
 * @author nexus
 * @date 2025-09-30
 */
public interface IWmWarehouseService extends IService<WmWarehouse> {
    /**
     * 查询仓库设置
     *
     * @param warehouseId 仓库设置主键
     * @return 仓库设置
     */
    public WmWarehouse selectWmWarehouseByWarehouseId(Long warehouseId);

    /**
     * 查询仓库设置列表
     *
     * @param wmWarehouse 仓库设置
     * @return 仓库设置集合
     */
    public List<WmWarehouse> selectWmWarehouseList(WmWarehouse wmWarehouse);

    /**
     * 新增仓库设置
     *
     * @param wmWarehouse 仓库设置
     * @return 结果
     */
    public int insertWmWarehouse(WmWarehouse wmWarehouse);

    /**
     * 修改仓库设置
     *
     * @param wmWarehouse 仓库设置
     * @return 结果
     */
    public int updateWmWarehouse(WmWarehouse wmWarehouse);

    /**
     * 批量删除仓库设置
     *
     * @param warehouseIds 需要删除的仓库设置主键集合
     * @return 结果
     */
    public int deleteWmWarehouseByWarehouseIds(Long[] warehouseIds);

    /**
     * 删除仓库设置信息
     *
     * @param warehouseId 仓库设置主键
     * @return 结果
     */
    public int deleteWmWarehouseByWarehouseId(Long warehouseId);

    /**
     * 获取仓库树
     *
     * @return
     */
    List<WmWarehouse> getWareHouseTree();
}

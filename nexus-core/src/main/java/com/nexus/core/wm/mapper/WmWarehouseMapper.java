package com.nexus.core.wm.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.wm.domain.WmWarehouse;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 仓库设置Mapper接口
 *
 * @author nexus
 * @date 2025-09-30
 */
public interface WmWarehouseMapper extends BaseMapper<WmWarehouse> {
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
     * 删除仓库设置
     *
     * @param warehouseId 仓库设置主键
     * @return 结果
     */
    public int deleteWmWarehouseByWarehouseId(Long warehouseId);

    /**
     * 批量删除仓库设置
     *
     * @param warehouseIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteWmWarehouseByWarehouseIds(Long[] warehouseIds);
}

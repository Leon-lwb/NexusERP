package com.nexus.core.wm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.wm.mapper.WmWarehouseMapper;
import com.nexus.core.wm.domain.WmWarehouse;
import com.nexus.core.wm.service.IWmWarehouseService;

/**
 * 仓库设置Service业务层处理
 *
 * @author nexus
 * @date 2025-09-30
 */
@Service
public class WmWarehouseServiceImpl extends ServiceImpl<WmWarehouseMapper, WmWarehouse> implements IWmWarehouseService {

    @Autowired
    private WmWarehouseMapper wmWarehouseMapper;

    /**
     * 查询仓库设置
     *
     * @param warehouseId 仓库设置主键
     * @return 仓库设置
     */
    @Override
    public WmWarehouse selectWmWarehouseByWarehouseId(Long warehouseId) {
        return wmWarehouseMapper.selectWmWarehouseByWarehouseId(warehouseId);
    }

    /**
     * 查询仓库设置列表
     *
     * @param wmWarehouse 仓库设置
     * @return 仓库设置
     */
    @Override
    public List<WmWarehouse> selectWmWarehouseList(WmWarehouse wmWarehouse) {
        return wmWarehouseMapper.selectWmWarehouseList(wmWarehouse);
    }

    /**
     * 新增仓库设置
     *
     * @param wmWarehouse 仓库设置
     * @return 结果
     */
    @Override
    public int insertWmWarehouse(WmWarehouse wmWarehouse) {
                wmWarehouse.setCreateTime(DateUtils.getNowDate());
            return wmWarehouseMapper.insertWmWarehouse(wmWarehouse);
    }

    /**
     * 修改仓库设置
     *
     * @param wmWarehouse 仓库设置
     * @return 结果
     */
    @Override
    public int updateWmWarehouse(WmWarehouse wmWarehouse) {
                wmWarehouse.setUpdateTime(DateUtils.getNowDate());
        return wmWarehouseMapper.updateWmWarehouse(wmWarehouse);
    }

    /**
     * 批量删除仓库设置
     *
     * @param warehouseIds 需要删除的仓库设置主键
     * @return 结果
     */
    @Override
    public int deleteWmWarehouseByWarehouseIds(Long[] warehouseIds) {
        return wmWarehouseMapper.deleteWmWarehouseByWarehouseIds(warehouseIds);
    }

    /**
     * 删除仓库设置信息
     *
     * @param warehouseId 仓库设置主键
     * @return 结果
     */
    @Override
    public int deleteWmWarehouseByWarehouseId(Long warehouseId) {
        return wmWarehouseMapper.deleteWmWarehouseByWarehouseId(warehouseId);
    }
}

package com.nexus.core.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.nexus.common.utils.DateUtils;
import com.nexus.core.wm.domain.WmStorageArea;
import com.nexus.core.wm.domain.WmStorageLocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.wm.mapper.WmWarehouseMapper;
import com.nexus.core.wm.domain.WmWarehouse;
import com.nexus.core.wm.service.IWmWarehouseService;
import com.nexus.core.wm.service.IWmStorageAreaService;
import com.nexus.core.wm.service.IWmStorageLocationService;

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
    
    @Autowired
    private IWmStorageAreaService wmStorageAreaService;
    
    @Autowired
    private IWmStorageLocationService wmStorageLocationService;

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

    /**
     * 获取仓库树
     * @return
     */
    @Override
    public List<WmWarehouse> getWareHouseTree() {
        // 查询所有仓库
        List<WmWarehouse> warehouses = this.list();
        
        // 查询所有库区
        List<WmStorageArea> areas = wmStorageAreaService.list();
        
        // 查询所有库位
        List<WmStorageLocation> locations = wmStorageLocationService.list();
        
        // 构建库位与库区的映射关系
        // 先按areaId分组库位
        Map<Long, List<WmStorageLocation>> locationMap = locations.stream()
                .collect(Collectors.groupingBy(WmStorageLocation::getAreaId));
        
        // 将库位设置到对应的库区中
        for (WmStorageArea area : areas) {
            List<WmStorageLocation> areaLocations = locationMap.get(area.getAreaId());
            if (areaLocations != null) {
                area.setChildren(areaLocations);
            }
        }
        
        // 构建库区与仓库的映射关系
        // 先按warehouseId分组库区
        Map<Long, List<WmStorageArea>> areaMap = areas.stream()
                .collect(Collectors.groupingBy(WmStorageArea::getWarehouseId));
        
        // 将库区设置到对应的仓库中
        for (WmWarehouse warehouse : warehouses) {
            List<WmStorageArea> warehouseAreas = areaMap.get(warehouse.getWarehouseId());
            if (warehouseAreas != null) {
                warehouse.setChildren(warehouseAreas);
            }
        }
        
        return warehouses;
    }
}
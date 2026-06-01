package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.bo.WmWarehouseBo;
import com.nexus.erp.wm.domain.vo.WmWarehouseVo;
import com.nexus.erp.wm.mapper.WmStorageAreaMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmWarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仓库设置Service业务层处理
 *
 * @author nexus
 * @date 2025-09-30
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmWarehouseServiceImpl implements IWmWarehouseService {

    private final WmWarehouseMapper baseMapper;
    private final WmStorageAreaMapper wmStorageAreaMapper;
    private final WmStorageLocationMapper wmStorageLocationMapper;

    @Override
    public TableDataInfo<WmWarehouseVo> selectPageList(WmWarehouseBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmWarehouse> lqw = buildQueryWrapper(bo);
        Page<WmWarehouseVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmWarehouseVo> selectList(WmWarehouseBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmWarehouseVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmWarehouseBo bo) {
        WmWarehouse entity = MapstructUtils.convert(bo, WmWarehouse.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmWarehouseBo bo) {
        WmWarehouse entity = MapstructUtils.convert(bo, WmWarehouse.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    public List<WmWarehouse> getWareHouseTree() {
        // 查询所有仓库
        List<WmWarehouse> warehouses = baseMapper.selectList(null);

        // 查询所有库区
        List<WmStorageArea> areas = wmStorageAreaMapper.selectList(null);

        // 查询所有库位
        List<WmStorageLocation> locations = wmStorageLocationMapper.selectList(null);

        // 构建库位与库区的映射关系
        Map<Long, List<WmStorageLocation>> locationMap = locations.stream()
                .collect(Collectors.groupingBy(WmStorageLocation::getAreaId));

        // 将库位设置到对应的库区中
        for (WmStorageArea area : areas) {
            area.setChildren(locationMap.get(area.getAreaId()));
        }

        // 构建库区与仓库的映射关系
        Map<Long, List<WmStorageArea>> areaMap = areas.stream()
                .collect(Collectors.groupingBy(WmStorageArea::getWarehouseId));

        // 将库区设置到对应的仓库中
        for (WmWarehouse warehouse : warehouses) {
            warehouse.setChildren(areaMap.get(warehouse.getWarehouseId()));
        }

        return warehouses;
    }

    private LambdaQueryWrapper<WmWarehouse> buildQueryWrapper(WmWarehouseBo bo) {
        LambdaQueryWrapper<WmWarehouse> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getWarehouseCode()), WmWarehouse::getWarehouseCode, bo.getWarehouseCode());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmWarehouse::getWarehouseName, bo.getWarehouseName());
        lqw.eq(bo.getStatus() != null, WmWarehouse::getStatus, bo.getStatus());
        lqw.like(StringUtils.isNotBlank(bo.getManager()), WmWarehouse::getManager, bo.getManager());
        lqw.like(StringUtils.isNotBlank(bo.getLocation()), WmWarehouse::getLocation, bo.getLocation());
        return lqw;
    }
}

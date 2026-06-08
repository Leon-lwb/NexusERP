package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.bo.WmWarehouseBo;
import com.nexus.erp.wm.domain.vo.WmStorageAreaTreeVo;
import com.nexus.erp.wm.domain.vo.WmStorageLocationTreeVo;
import com.nexus.erp.wm.domain.vo.WmWarehouseTreeVo;
import com.nexus.erp.wm.domain.vo.WmWarehouseVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
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
    private final WmMaterialStockMapper wmMaterialStockMapper;

    @Override
    public TableDataInfo<WmWarehouseVo> selectPageList(WmWarehouseBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmWarehouse> lqw = buildQueryWrapper(bo);
        Page<WmWarehouseVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        // 填充库区数量
        for (WmWarehouseVo vo : page.getRecords()) {
            long count = wmStorageAreaMapper.selectCount(
                Wrappers.<WmStorageArea>lambdaQuery()
                    .eq(WmStorageArea::getWarehouseId, vo.getWarehouseId())
            );
            vo.setAreaCount((int) count);
        }
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
        validateWarehouseCodeUnique(bo.getWarehouseCode(), null);
        WmWarehouse entity = MapstructUtils.convert(bo, WmWarehouse.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmWarehouseBo bo) {
        validateWarehouseCodeUnique(bo.getWarehouseCode(), bo.getWarehouseId());
        // 状态联动校验：仓库停用时检查是否存在启用的库区
        if (bo.getStatus() != null && !Long.valueOf(1L).equals(bo.getStatus())) {
            Long enabledAreaCount = wmStorageAreaMapper.selectCount(
                Wrappers.lambdaQuery(WmStorageArea.class)
                    .eq(WmStorageArea::getWarehouseId, bo.getWarehouseId())
                    .eq(WmStorageArea::getEnableFlag, 1L)
            );
            if (enabledAreaCount > 0) {
                throw new ServiceException("该仓库下存在启用的库区，无法停用");
            }
        }
        WmWarehouse entity = MapstructUtils.convert(bo, WmWarehouse.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long warehouseId : ids) {
            validateBeforeDelete(warehouseId);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 获取仓库树形列表
     * <p>
     * 使用轻量级 TreeVo 查询，仅获取树形展示所需字段，
     * 在内存中组装仓库-库区-库位三级树结构。
     * </p>
     *
     * @return 仓库树形数据列表
     */
    @Override
    public List<WmWarehouseTreeVo> getWareHouseTree() {
        // 查询仓库树形数据（含库区数量）
        List<WmWarehouseTreeVo> warehouses = baseMapper.selectWarehouseTreeList();
        // 查询库区树形数据
        List<WmStorageAreaTreeVo> areas = wmStorageAreaMapper.selectAreaTreeList();
        // 查询库位树形数据
        List<WmStorageLocationTreeVo> locations = wmStorageLocationMapper.selectLocationTreeList();

        // 构建库位与库区的映射关系
        Map<Long, List<WmStorageLocationTreeVo>> locationMap = locations.stream()
                .collect(Collectors.groupingBy(WmStorageLocationTreeVo::getAreaId));

        // 将库位设置到对应的库区中
        for (WmStorageAreaTreeVo area : areas) {
            area.setChildren(locationMap.get(area.getAreaId()));
        }

        // 构建库区与仓库的映射关系
        Map<Long, List<WmStorageAreaTreeVo>> areaMap = areas.stream()
                .collect(Collectors.groupingBy(WmStorageAreaTreeVo::getWarehouseId));

        // 将库区设置到对应的仓库中
        for (WmWarehouseTreeVo warehouse : warehouses) {
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

    /**
     * 删除仓库前的级联校验
     * <p>
     * 检查仓库下是否存在关联库区，如果存在则不允许删除，
     * 需要先删除或移动关联的库区后才能删除仓库。
     * </p>
     *
     * @param warehouseId 仓库ID
     */
    private void validateBeforeDelete(Long warehouseId) {
        Long areaCount = wmStorageAreaMapper.selectCount(
            Wrappers.lambdaQuery(WmStorageArea.class)
                .eq(WmStorageArea::getWarehouseId, warehouseId)
        );
        if (areaCount > 0) {
            throw new ServiceException("仓库下存在关联库区，无法删除");
        }
    }

    /**
     * 校验仓库编码唯一性
     * <p>
     * 新增时校验编码不能与已有仓库编码重复；
     * 修改时排除自身ID后校验编码不能与其他仓库编码重复。
     * </p>
     *
     * @param warehouseCode 仓库编码
     * @param warehouseId   仓库ID（新增时传null，修改时传当前仓库ID）
     */
    private void validateWarehouseCodeUnique(String warehouseCode, Long warehouseId) {
        LambdaQueryWrapper<WmWarehouse> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmWarehouse::getWarehouseCode, warehouseCode);
        if (warehouseId != null) {
            lqw.ne(WmWarehouse::getWarehouseId, warehouseId);
        }
        Long count = baseMapper.selectCount(lqw);
        if (count > 0) {
            throw new ServiceException("仓库编码'" + warehouseCode + "'已存在");
        }
    }
}

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
import com.nexus.erp.wm.domain.vo.WmLocationStockAggVo;
import com.nexus.erp.wm.domain.vo.WmStorageArea3dVo;
import com.nexus.erp.wm.domain.vo.WmStorageAreaTreeVo;
import com.nexus.erp.wm.domain.vo.WmStorageLocation3dVo;
import com.nexus.erp.wm.domain.vo.WmStorageLocationTreeVo;
import com.nexus.erp.wm.domain.vo.WmWarehouse3dVo;
import com.nexus.erp.wm.domain.vo.WmWarehouseTreeVo;
import com.nexus.erp.wm.domain.vo.WmWarehouseVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmStorageAreaMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmWarehouseService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final AutoCodeUtil autoCodeUtil;

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
        if (StringUtils.isBlank(entity.getWarehouseCode())) {
            entity.setWarehouseCode(autoCodeUtil.genSerialCode("WAREHOUSE_CODE", null));
        }
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

    /**
     * 获取仓库 3D 可视化聚合数据
     * <p>
     * 一次性查询仓库、库区、库位与库存聚合，在内存中组装：
     * 库存聚合挂库位 → 计算库位状态(EMPTY/OCCUPIED/WARNING/LOCKED) → 库位按库区分组 → 汇总计数。
     * 状态优先级：LOCKED > WARNING > OCCUPIED > EMPTY。
     * </p>
     *
     * @param warehouseId 仓库ID
     * @return 仓库 3D 视图对象
     */
    @Override
    public WmWarehouse3dVo getWarehouse3d(Long warehouseId) {
        WmWarehouse3dVo warehouse = baseMapper.selectWarehouse3dById(warehouseId);
        if (warehouse == null) {
            return null;
        }
        List<WmStorageArea3dVo> areas = wmStorageAreaMapper.selectArea3dByWarehouseId(warehouseId);
        List<WmStorageLocation3dVo> locations = wmStorageLocationMapper.selectLocation3dByWarehouseId(warehouseId);
        List<WmLocationStockAggVo> stocks = wmMaterialStockMapper.aggregateStockByWarehouse(warehouseId);

        // 库存聚合按库位建立索引
        Map<Long, WmLocationStockAggVo> stockMap = stocks.stream()
            .collect(Collectors.toMap(WmLocationStockAggVo::getLocationId, s -> s));
        // 装配库存并计算库位状态
        for (WmStorageLocation3dVo loc : locations) {
            applyStatus(loc, stockMap.get(loc.getLocationId()));
        }
        // 库位按库区分组挂载
        Map<Long, List<WmStorageLocation3dVo>> locByArea = locations.stream()
            .collect(Collectors.groupingBy(WmStorageLocation3dVo::getAreaId));
        for (WmStorageArea3dVo area : areas) {
            area.setLocations(locByArea.get(area.getAreaId()));
        }

        warehouse.setAreas(areas);
        warehouse.setTotalLocations(locations.size());
        warehouse.setOccupiedLocations(countByStatus(locations, "OCCUPIED"));
        warehouse.setEmptyLocations(countByStatus(locations, "EMPTY"));
        warehouse.setLockedLocations(countByStatus(locations, "LOCKED"));
        warehouse.setWarningLocations(countByStatus(locations, "WARNING"));
        return warehouse;
    }

    /**
     * 根据库存聚合计算库位状态并回填库存字段
     */
    private void applyStatus(WmStorageLocation3dVo loc, WmLocationStockAggVo agg) {
        if (agg == null || agg.getStockCount() == null || agg.getStockCount() == 0
            || agg.getStockQuantity() == null || agg.getStockQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            loc.setStatus("EMPTY");
            loc.setStockQuantity(BigDecimal.ZERO);
            loc.setReservedQuantity(BigDecimal.ZERO);
            loc.setStockCount(0);
            return;
        }
        loc.setStockQuantity(agg.getStockQuantity());
        loc.setReservedQuantity(agg.getReservedQuantity());
        loc.setStockCount(agg.getStockCount());
        // 锁定：库位冻结或库位下任一库存冻结
        boolean locked = Long.valueOf(1L).equals(loc.getFrozenFlag())
            || Long.valueOf(1L).equals(agg.getFrozenFlag());
        if (locked) {
            loc.setStatus("LOCKED");
            return;
        }
        // 预警：预留占比 >= 80%
        BigDecimal reserved = agg.getReservedQuantity() != null ? agg.getReservedQuantity() : BigDecimal.ZERO;
        if (reserved.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = reserved.divide(agg.getStockQuantity(), 2, RoundingMode.HALF_UP);
            if (ratio.compareTo(new BigDecimal("0.8")) >= 0) {
                loc.setStatus("WARNING");
                return;
            }
        }
        loc.setStatus("OCCUPIED");
    }

    /**
     * 统计指定状态的库位数量
     */
    private int countByStatus(List<WmStorageLocation3dVo> locations, String status) {
        return (int) locations.stream()
            .filter(l -> status.equals(l.getStatus()))
            .count();
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

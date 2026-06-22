package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
import com.nexus.erp.wm.domain.bo.WmStorageAreaBo;
import com.nexus.erp.wm.domain.vo.WmStorageAreaVo;
import com.nexus.erp.wm.domain.vo.WmWarehouseVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmStorageAreaMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmStorageAreaService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 库区Service业务层处理
 *
 * @author nexus
 * @date 2025-12-01
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmStorageAreaServiceImpl implements IWmStorageAreaService {

    private final WmStorageAreaMapper baseMapper;
    private final WmWarehouseMapper wmWarehouseMapper;
    private final WmStorageLocationMapper wmStorageLocationMapper;
    private final WmMaterialStockMapper wmMaterialStockMapper;
    private final AutoCodeUtil autoCodeUtil;

    @Override
    public TableDataInfo<WmStorageAreaVo> selectPageList(WmStorageAreaBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmStorageArea> lqw = buildQueryWrapper(bo);
        Page<WmStorageAreaVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        // 批量填充仓库名称和编码
        fillWarehouseInfo(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmStorageAreaVo> selectList(WmStorageAreaBo bo) {
        List<WmStorageAreaVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        // 批量填充仓库名称和编码
        fillWarehouseInfo(list);
        return list;
    }

    @Override
    public WmStorageAreaVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmStorageAreaBo bo) {
        // 校验仓库存在且启用
        validateWarehouseExistsAndEnabled(bo.getWarehouseId());
        // 校验库区编码唯一性
        validateAreaCodeUnique(bo.getAreaCode(), null);
        WmStorageArea entity = MapstructUtils.convert(bo, WmStorageArea.class);
        if (StringUtils.isBlank(entity.getAreaCode())) {
            entity.setAreaCode(autoCodeUtil.genSerialCode("WM_AREA_CODE", null));
        }
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmStorageAreaBo bo) {
        // 校验库区编码唯一性
        validateAreaCodeUnique(bo.getAreaCode(), bo.getAreaId());
        // 校验新仓库存在且启用
        validateWarehouseExistsAndEnabled(bo.getWarehouseId());
        // 启用标志变更校验：库区停用时检查是否存在启用的库位
        if (bo.getEnableFlag() != null && !Long.valueOf(1L).equals(bo.getEnableFlag())) {
            Long enabledLocationCount = wmStorageLocationMapper.selectCount(
                Wrappers.lambdaQuery(WmStorageLocation.class)
                    .eq(WmStorageLocation::getAreaId, bo.getAreaId())
                    .eq(WmStorageLocation::getEnableFlag, 1L)
            );
            if (enabledLocationCount > 0) {
                throw new ServiceException("该库区下存在启用的库位，无法停用");
            }
        }
        // 库位管理标志变更校验：关闭库位管理时检查是否存在库位
        if (bo.getLocationFlag() != null && !Long.valueOf(1L).equals(bo.getLocationFlag())) {
            Long locationCount = wmStorageLocationMapper.selectCount(
                Wrappers.lambdaQuery(WmStorageLocation.class)
                    .eq(WmStorageLocation::getAreaId, bo.getAreaId())
            );
            if (locationCount > 0) {
                throw new ServiceException("该库区下存在库位，无法关闭库位管理");
            }
        }
        // 所属仓库变更时，级联更新下属库位的 warehouseId
        cascadeUpdateLocationWarehouse(bo.getAreaId(), bo.getWarehouseId());
        WmStorageArea entity = MapstructUtils.convert(bo, WmStorageArea.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long areaId : ids) {
            validateBeforeDelete(areaId);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<WmStorageArea> buildQueryWrapper(WmStorageAreaBo bo) {
        LambdaQueryWrapper<WmStorageArea> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getAreaCode()), WmStorageArea::getAreaCode, bo.getAreaCode());
        lqw.like(StringUtils.isNotBlank(bo.getAreaName()), WmStorageArea::getAreaName, bo.getAreaName());
        lqw.eq(bo.getWarehouseId() != null, WmStorageArea::getWarehouseId, bo.getWarehouseId());
        lqw.eq(bo.getEnableFlag() != null, WmStorageArea::getEnableFlag, bo.getEnableFlag());
        lqw.eq(StringUtils.isNotBlank(bo.getLayoutType()), WmStorageArea::getLayoutType, bo.getLayoutType());
        return lqw;
    }

    /**
     * 删除库区前的级联校验
     * <p>
     * 检查库区下是否存在关联库位或库存记录，
     * 如果存在则不允许删除，需要先清理关联数据后才能删除库区。
     * </p>
     *
     * @param areaId 库区ID
     */
    private void validateBeforeDelete(Long areaId) {
        // 检查是否存在关联库位
        Long locationCount = wmStorageLocationMapper.selectCount(
            Wrappers.lambdaQuery(WmStorageLocation.class)
                .eq(WmStorageLocation::getAreaId, areaId)
        );
        if (locationCount > 0) {
            throw new ServiceException("库区下存在关联库位，无法删除");
        }
        // 检查是否存在关联库存记录
        Long stockCount = wmMaterialStockMapper.selectCount(
            Wrappers.lambdaQuery(WmMaterialStock.class)
                .eq(WmMaterialStock::getAreaId, areaId)
        );
        if (stockCount > 0) {
            throw new ServiceException("库区下存在关联库存记录，无法删除");
        }
    }

    /**
     * 校验仓库存在且启用
     * <p>
     * 新增库区时，必须确保所属仓库存在且状态为启用（status == 1L），
     * 否则不允许在该仓库下新增库区。
     * </p>
     *
     * @param warehouseId 仓库ID
     */
    private void validateWarehouseExistsAndEnabled(Long warehouseId) {
        WmWarehouse warehouse = wmWarehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new ServiceException("所属仓库不存在");
        }
        if (!Long.valueOf(1L).equals(warehouse.getStatus())) {
            throw new ServiceException("所属仓库未启用，无法新增库区");
        }
    }

    /**
     * 校验库区编码唯一性
     * <p>
     * 新增时校验编码不能与已有库区编码重复；
     * 修改时排除自身ID后校验编码不能与其他库区编码重复。
     * </p>
     *
     * @param areaCode 库区编码
     * @param areaId   库区ID（新增时传null，修改时传当前库区ID）
     */
    private void validateAreaCodeUnique(String areaCode, Long areaId) {
        LambdaQueryWrapper<WmStorageArea> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmStorageArea::getAreaCode, areaCode);
        if (areaId != null) {
            lqw.ne(WmStorageArea::getAreaId, areaId);
        }
        Long count = baseMapper.selectCount(lqw);
        if (count > 0) {
            throw new ServiceException("库区编码'" + areaCode + "'已存在");
        }
    }

    /**
     * 级联更新库区下属库位的仓库ID
     * <p>
     * 当库区所属仓库发生变更时，需要同步更新该库区下所有库位的 warehouseId，
     * 保证数据一致性。仅在 warehouseId 实际发生变化时才执行更新。
     * </p>
     *
     * @param areaId      库区ID
     * @param newWarehouseId 新的仓库ID
     */
    private void cascadeUpdateLocationWarehouse(Long areaId, Long newWarehouseId) {
        if (areaId == null || newWarehouseId == null) {
            return;
        }
        // 查询当前库区的原仓库ID
        WmStorageArea currentArea = baseMapper.selectById(areaId);
        if (currentArea == null || newWarehouseId.equals(currentArea.getWarehouseId())) {
            return;
        }
        // 批量更新该库区下所有库位的 warehouseId
        WmStorageLocation update = new WmStorageLocation();
        update.setWarehouseId(newWarehouseId);
        wmStorageLocationMapper.update(update,
            Wrappers.<WmStorageLocation>lambdaUpdate()
                .eq(WmStorageLocation::getAreaId, areaId)
        );
    }

    /**
     * 批量填充库区的仓库名称和编码
     * <p>
     * 收集所有库区记录的 warehouseId，一次性查询对应的仓库信息，
     * 然后通过 Map 映射填充到每条库区记录中，避免 N+1 查询问题。
     * </p>
     *
     * @param list 库区Vo列表
     */
    private void fillWarehouseInfo(List<WmStorageAreaVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Long> warehouseIds = list.stream()
            .map(WmStorageAreaVo::getWarehouseId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (warehouseIds.isEmpty()) {
            return;
        }
        List<WmWarehouseVo> warehouses = wmWarehouseMapper.selectVoByIds(warehouseIds);
        Map<Long, WmWarehouseVo> warehouseMap = warehouses.stream()
            .collect(Collectors.toMap(WmWarehouseVo::getWarehouseId, w -> w));
        for (WmStorageAreaVo vo : list) {
            WmWarehouseVo warehouse = warehouseMap.get(vo.getWarehouseId());
            if (warehouse != null) {
                vo.setWarehouseName(warehouse.getWarehouseName());
                vo.setWarehouseCode(warehouse.getWarehouseCode());
            }
        }
    }
}

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
import com.nexus.erp.wm.domain.bo.WmLocationBatchGenerateBo;
import com.nexus.erp.wm.domain.bo.WmStorageLocationBo;
import com.nexus.erp.wm.domain.vo.WmStorageLocationVo;
import com.nexus.erp.wm.domain.vo.WmStorageAreaVo;
import com.nexus.erp.wm.domain.vo.WmWarehouseVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmStorageAreaMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmStorageLocationService;
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
 * 库位Service业务层处理
 *
 * @author nexus
 * @date 2025-12-02
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmStorageLocationServiceImpl implements IWmStorageLocationService {

    private final WmStorageLocationMapper baseMapper;
    private final WmStorageAreaMapper wmStorageAreaMapper;
    private final WmWarehouseMapper wmWarehouseMapper;
    private final WmMaterialStockMapper wmMaterialStockMapper;

    @Override
    public TableDataInfo<WmStorageLocationVo> selectPageList(WmStorageLocationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmStorageLocation> lqw = buildQueryWrapper(bo);
        Page<WmStorageLocationVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        // 批量填充库区和仓库名称
        fillAreaAndWarehouseInfo(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmStorageLocationVo> selectList(WmStorageLocationBo bo) {
        List<WmStorageLocationVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        // 批量填充库区和仓库名称
        fillAreaAndWarehouseInfo(list);
        return list;
    }

    @Override
    public WmStorageLocationVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmStorageLocationBo bo) {
        // 校验库区存在且启用
        WmStorageArea area = validateAreaExistsAndEnabled(bo.getAreaId());
        // 校验库区开启了库位管理
        if (!Long.valueOf(1L).equals(area.getLocationFlag())) {
            throw new ServiceException("所属库区未开启库位管理，无法新增库位");
        }
        // 校验库位编码唯一性
        validateLocationCodeUnique(bo.getLocationCode(), null);
        WmStorageLocation entity = MapstructUtils.convert(bo, WmStorageLocation.class);
        // 自动填充仓库ID（从库区获取）
        entity.setWarehouseId(area.getWarehouseId());
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmStorageLocationBo bo) {
        // 校验库位编码唯一性
        validateLocationCodeUnique(bo.getLocationCode(), bo.getLocationId());
        WmStorageLocation entity = MapstructUtils.convert(bo, WmStorageLocation.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long locationId : ids) {
            validateBeforeDelete(locationId);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * {@inheritDoc}
     * <p>
     * 校验库区存在且开启库位管理后，按行×列×层规则批量生成库位，
     * 编码格式为 {前缀}R{行号}C{列号}L{层号}，名称格式为 行X-列X-层X。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchGenerate(WmLocationBatchGenerateBo bo) {
        // 校验库区存在且开启库位管理
        WmStorageArea area = wmStorageAreaMapper.selectById(bo.getAreaId());
        if (area == null) {
            throw new ServiceException("库区不存在");
        }
        if (area.getLocationFlag() == null || area.getLocationFlag() != 1L) {
            throw new ServiceException("库区【" + area.getAreaName() + "】未开启库位管理，无法批量生成库位");
        }

        // 确定编码前缀
        String prefix = StringUtils.isNotBlank(bo.getCodePrefix())
            ? bo.getCodePrefix()
            : area.getAreaCode() + "-";

        // 批量生成库位
        List<WmStorageLocation> locations = new ArrayList<>();
        for (int row = bo.getRowStart(); row <= bo.getRowEnd(); row++) {
            for (int col = 1; col <= bo.getColCount(); col++) {
                for (int layer = 1; layer <= bo.getLayerCount(); layer++) {
                    WmStorageLocation loc = new WmStorageLocation();
                    loc.setAreaId(bo.getAreaId());
                    loc.setWarehouseId(area.getWarehouseId());
                    loc.setLocationCode(String.format("%sR%02dC%02dL%02d", prefix, row, col, layer));
                    loc.setLocationName(String.format("行%d-列%d-层%d", row, col, layer));
                    loc.setLocationType(bo.getLocationType());
                    loc.setRowNum((long) row);
                    loc.setCol((long) col);
                    loc.setLayer((long) layer);
                    loc.setEnableFlag(1L);
                    loc.setFrozenFlag(0L);
                    loc.setProductMixing(0L);
                    loc.setBatchMixing(0L);
                    loc.setStatus(1L);
                    locations.add(loc);
                }
            }
        }

        // 逐条插入（MyBatis-Plus 批量插入需要配置，逐条更稳妥）
        int count = 0;
        for (WmStorageLocation loc : locations) {
            count += baseMapper.insert(loc);
        }
        return count;
    }

    private LambdaQueryWrapper<WmStorageLocation> buildQueryWrapper(WmStorageLocationBo bo) {
        LambdaQueryWrapper<WmStorageLocation> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getLocationCode()), WmStorageLocation::getLocationCode, bo.getLocationCode());
        lqw.like(StringUtils.isNotBlank(bo.getLocationName()), WmStorageLocation::getLocationName, bo.getLocationName());
        lqw.eq(bo.getAreaId() != null, WmStorageLocation::getAreaId, bo.getAreaId());
        lqw.eq(StringUtils.isNotBlank(bo.getLocationType()), WmStorageLocation::getLocationType, bo.getLocationType());
        lqw.eq(bo.getStatus() != null, WmStorageLocation::getStatus, bo.getStatus());
        lqw.eq(bo.getEnableFlag() != null, WmStorageLocation::getEnableFlag, bo.getEnableFlag());
        return lqw;
    }

    /**
     * 删除库位前的级联校验
     * <p>
     * 检查库位下是否存在关联库存记录，
     * 如果存在则不允许删除，需要先清理库存数据后才能删除库位。
     * </p>
     *
     * @param locationId 库位ID
     */
    private void validateBeforeDelete(Long locationId) {
        Long stockCount = wmMaterialStockMapper.selectCount(
            Wrappers.lambdaQuery(WmMaterialStock.class)
                .eq(WmMaterialStock::getLocationId, locationId)
        );
        if (stockCount > 0) {
            throw new ServiceException("库位下存在关联库存记录，无法删除");
        }
    }

    /**
     * 校验库区存在且启用
     * <p>
     * 新增库位时，必须确保所属库区存在且启用（enableFlag == 1L），
     * 否则不允许在该库区下新增库位。同时返回库区信息用于后续处理。
     * </p>
     *
     * @param areaId 库区ID
     * @return 库区实体
     */
    private WmStorageArea validateAreaExistsAndEnabled(Long areaId) {
        WmStorageArea area = wmStorageAreaMapper.selectById(areaId);
        if (area == null) {
            throw new ServiceException("所属库区不存在");
        }
        if (!Long.valueOf(1L).equals(area.getEnableFlag())) {
            throw new ServiceException("所属库区未启用，无法新增库位");
        }
        return area;
    }

    /**
     * 校验库位编码唯一性
     * <p>
     * 新增时校验编码不能与已有库位编码重复；
     * 修改时排除自身ID后校验编码不能与其他库位编码重复。
     * </p>
     *
     * @param locationCode 库位编码
     * @param locationId   库位ID（新增时传null，修改时传当前库位ID）
     */
    private void validateLocationCodeUnique(String locationCode, Long locationId) {
        LambdaQueryWrapper<WmStorageLocation> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmStorageLocation::getLocationCode, locationCode);
        if (locationId != null) {
            lqw.ne(WmStorageLocation::getLocationId, locationId);
        }
        Long count = baseMapper.selectCount(lqw);
        if (count > 0) {
            throw new ServiceException("库位编码'" + locationCode + "'已存在");
        }
    }

    /**
     * 批量填充库位的库区名称、库区编码、仓库名称
     * <p>
     * 收集所有库位记录的 areaId，一次性查询对应的库区信息，
     * 再根据库区的 warehouseId 批量查询仓库信息，
     * 通过 Map 映射填充到每条库位记录中，避免 N+1 查询问题。
     * </p>
     *
     * @param list 库位Vo列表
     */
    private void fillAreaAndWarehouseInfo(List<WmStorageLocationVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        // 批量查询库区信息
        Set<Long> areaIds = list.stream()
            .map(WmStorageLocationVo::getAreaId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (areaIds.isEmpty()) {
            return;
        }
        List<WmStorageAreaVo> areas = wmStorageAreaMapper.selectVoByIds(areaIds);
        Map<Long, WmStorageAreaVo> areaMap = areas.stream()
            .collect(Collectors.toMap(WmStorageAreaVo::getAreaId, a -> a));

        // 批量查询仓库信息
        Set<Long> warehouseIds = areas.stream()
            .map(WmStorageAreaVo::getWarehouseId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, WmWarehouseVo> warehouseMap = Map.of();
        if (!warehouseIds.isEmpty()) {
            List<WmWarehouseVo> warehouses = wmWarehouseMapper.selectVoByIds(warehouseIds);
            warehouseMap = warehouses.stream()
                .collect(Collectors.toMap(WmWarehouseVo::getWarehouseId, w -> w));
        }

        // 填充关联信息
        for (WmStorageLocationVo vo : list) {
            WmStorageAreaVo area = areaMap.get(vo.getAreaId());
            if (area != null) {
                vo.setAreaName(area.getAreaName());
                vo.setAreaCode(area.getAreaCode());
                vo.setWarehouseId(area.getWarehouseId());
                WmWarehouseVo warehouse = warehouseMap.get(area.getWarehouseId());
                if (warehouse != null) {
                    vo.setWarehouseName(warehouse.getWarehouseName());
                }
            }
        }
    }
}

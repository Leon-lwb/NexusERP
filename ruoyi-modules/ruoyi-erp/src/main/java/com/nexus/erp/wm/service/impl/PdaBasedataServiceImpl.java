package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.vo.pda.PdaBasedataSyncVo;
import com.nexus.erp.wm.mapper.WmStorageAreaMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IPdaBasedataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDA 主数据增量同步实现
 * <p>
 * 游标 = 行 update_time; 首次(lastSyncTime<=0)或增量超阈值时整包全量。
 * 客户端缓存仅用于查询/预校验, 写操作仍以服务端裁决为准。
 */
@DS("erp")
@Service
@RequiredArgsConstructor
public class PdaBasedataServiceImpl implements IPdaBasedataService {

    /** 增量行数超过阈值转全量, 避免客户端长期不同步后增量过大 */
    private static final int FULL_THRESHOLD = 5000;

    private final BdMaterialMapper materialMapper;
    private final BdUnitOfMeasureMapper uomMapper;
    private final WmStorageLocationMapper locationMapper;
    private final WmStorageAreaMapper areaMapper;
    private final WmWarehouseMapper warehouseMapper;

    @Override
    public PdaBasedataSyncVo sync(Long lastSyncTime) {
        long cursor = lastSyncTime == null ? 0L : lastSyncTime;
        boolean full = cursor <= 0;
        Date cursorDate = new Date(cursor);

        List<BdMaterial> materials = full
            ? materialMapper.selectList(Wrappers.lambdaQuery())
            : materialMapper.selectList(Wrappers.<BdMaterial>lambdaQuery().gt(BdMaterial::getUpdateTime, cursorDate));
        List<WmStorageLocation> locations = full
            ? locationMapper.selectList(Wrappers.lambdaQuery())
            : locationMapper.selectList(Wrappers.<WmStorageLocation>lambdaQuery().gt(WmStorageLocation::getUpdateTime, cursorDate));
        List<WmWarehouse> warehouses = full
            ? warehouseMapper.selectList(Wrappers.lambdaQuery())
            : warehouseMapper.selectList(Wrappers.<WmWarehouse>lambdaQuery().gt(WmWarehouse::getUpdateTime, cursorDate));

        int incrementalTotal = materials.size() + locations.size() + warehouses.size();
        if (!full && incrementalTotal > FULL_THRESHOLD) {
            full = true;
            materials = materialMapper.selectList(Wrappers.lambdaQuery());
            locations = locationMapper.selectList(Wrappers.lambdaQuery());
            warehouses = warehouseMapper.selectList(Wrappers.lambdaQuery());
        }

        // 编码映射(仓库/库区数量小, 全量载入拼接)
        Map<Long, String> warehouseCodes = new HashMap<>();
        for (WmWarehouse w : warehouseMapper.selectList(Wrappers.lambdaQuery())) {
            warehouseCodes.put(w.getWarehouseId(), w.getWarehouseCode());
        }
        Map<Long, String> areaCodes = new HashMap<>();
        for (WmStorageArea a : areaMapper.selectList(Wrappers.lambdaQuery())) {
            areaCodes.put(a.getAreaId(), a.getAreaCode());
        }
        Map<Long, String> uomNames = new HashMap<>();
        for (BdUnitOfMeasure u : uomMapper.selectList(Wrappers.lambdaQuery())) {
            uomNames.put(u.getUomId(), u.getUomName());
        }

        PdaBasedataSyncVo vo = new PdaBasedataSyncVo();
        vo.setServerTime(System.currentTimeMillis());
        vo.setFull(full);

        List<PdaBasedataSyncVo.MaterialItem> materialItems = new ArrayList<>(materials.size());
        for (BdMaterial m : materials) {
            PdaBasedataSyncVo.MaterialItem item = new PdaBasedataSyncVo.MaterialItem();
            item.setMaterialCode(m.getMaterialCode());
            item.setMaterialName(m.getMaterialName());
            item.setMaterialSpec(m.getMaterialSpec());
            item.setBaseUomName(m.getBaseUomId() != null ? uomNames.get(m.getBaseUomId()) : null);
            materialItems.add(item);
        }
        vo.setMaterials(materialItems);

        List<PdaBasedataSyncVo.LocationItem> locationItems = new ArrayList<>(locations.size());
        for (WmStorageLocation l : locations) {
            PdaBasedataSyncVo.LocationItem item = new PdaBasedataSyncVo.LocationItem();
            item.setLocationCode(l.getLocationCode());
            item.setLocationName(l.getLocationName());
            item.setWarehouseCode(l.getWarehouseId() != null ? warehouseCodes.get(l.getWarehouseId()) : null);
            item.setAreaCode(l.getAreaId() != null ? areaCodes.get(l.getAreaId()) : null);
            locationItems.add(item);
        }
        vo.setLocations(locationItems);

        List<PdaBasedataSyncVo.WarehouseItem> warehouseItems = new ArrayList<>(warehouses.size());
        for (WmWarehouse w : warehouses) {
            PdaBasedataSyncVo.WarehouseItem item = new PdaBasedataSyncVo.WarehouseItem();
            item.setWarehouseCode(w.getWarehouseCode());
            item.setWarehouseName(w.getWarehouseName());
            warehouseItems.add(item);
        }
        vo.setWarehouses(warehouseItems);
        return vo;
    }
}

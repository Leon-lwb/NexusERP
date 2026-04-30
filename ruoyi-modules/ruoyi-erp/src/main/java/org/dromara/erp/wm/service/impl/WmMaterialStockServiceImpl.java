package org.dromara.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.WmMaterialStock;
import org.dromara.erp.wm.domain.bo.WmMaterialStockBo;
import org.dromara.erp.wm.domain.vo.WmMaterialStockVo;
import org.dromara.erp.wm.mapper.WmMaterialStockMapper;
import org.dromara.erp.wm.service.IWmMaterialStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 库存清单Service业务层处理
 *
 * @author ruoyi
 * @date 2025-12-03
 */
@RequiredArgsConstructor
@Service
public class WmMaterialStockServiceImpl implements IWmMaterialStockService {

    private final WmMaterialStockMapper baseMapper;

    @Override
    public TableDataInfo<WmMaterialStockVo> selectPageList(WmMaterialStockBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMaterialStock> lqw = buildQueryWrapper(bo);
        Page<WmMaterialStockVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmMaterialStockVo> selectList(WmMaterialStockBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmMaterialStockVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMaterialStockBo bo) {
        WmMaterialStock entity = MapstructUtils.convert(bo, WmMaterialStock.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMaterialStockBo bo) {
        WmMaterialStock entity = MapstructUtils.convert(bo, WmMaterialStock.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    public List<WmMaterialStockVo> selectByCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new ArrayList<>();
        }
        return baseMapper.selectByCategoryIds(categoryIds);
    }

    private LambdaQueryWrapper<WmMaterialStock> buildQueryWrapper(WmMaterialStockBo bo) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getCategoryId() != null, WmMaterialStock::getCategoryId, bo.getCategoryId());
        lqw.eq(bo.getMaterialId() != null, WmMaterialStock::getMaterialId, bo.getMaterialId());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialCode()), WmMaterialStock::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), WmMaterialStock::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialSpec()), WmMaterialStock::getMaterialSpec, bo.getMaterialSpec());
        lqw.eq(StringUtils.isNotBlank(bo.getBaseUomId()), WmMaterialStock::getBaseUomId, bo.getBaseUomId());
        lqw.like(StringUtils.isNotBlank(bo.getBaseUomName()), WmMaterialStock::getBaseUomName, bo.getBaseUomName());
        lqw.eq(bo.getBatchId() != null, WmMaterialStock::getBatchId, bo.getBatchId());
        lqw.eq(StringUtils.isNotBlank(bo.getBatchCode()), WmMaterialStock::getBatchCode, bo.getBatchCode());
        lqw.eq(bo.getWarehouseId() != null, WmMaterialStock::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getWarehouseCode()), WmMaterialStock::getWarehouseCode, bo.getWarehouseCode());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmMaterialStock::getWarehouseName, bo.getWarehouseName());
        lqw.eq(bo.getAreaId() != null, WmMaterialStock::getAreaId, bo.getAreaId());
        lqw.eq(StringUtils.isNotBlank(bo.getAreaCode()), WmMaterialStock::getAreaCode, bo.getAreaCode());
        lqw.like(StringUtils.isNotBlank(bo.getAreaName()), WmMaterialStock::getAreaName, bo.getAreaName());
        lqw.eq(bo.getLocationId() != null, WmMaterialStock::getLocationId, bo.getLocationId());
        lqw.eq(StringUtils.isNotBlank(bo.getLocationCode()), WmMaterialStock::getLocationCode, bo.getLocationCode());
        lqw.like(StringUtils.isNotBlank(bo.getLocationName()), WmMaterialStock::getLocationName, bo.getLocationName());
        lqw.eq(bo.getVendorId() != null, WmMaterialStock::getVendorId, bo.getVendorId());
        lqw.eq(StringUtils.isNotBlank(bo.getVendorCode()), WmMaterialStock::getVendorCode, bo.getVendorCode());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), WmMaterialStock::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getVendorNick()), WmMaterialStock::getVendorNick, bo.getVendorNick());
        lqw.eq(bo.getStockQuantity() != null, WmMaterialStock::getStockQuantity, bo.getStockQuantity());
        lqw.eq(bo.getWoId() != null, WmMaterialStock::getWoId, bo.getWoId());
        lqw.eq(StringUtils.isNotBlank(bo.getWoNo()), WmMaterialStock::getWoNo, bo.getWoNo());
        lqw.eq(bo.getProductionDate() != null, WmMaterialStock::getProductionDate, bo.getProductionDate());
        lqw.eq(bo.getRecptDate() != null, WmMaterialStock::getRecptDate, bo.getRecptDate());
        lqw.eq(bo.getExpireDate() != null, WmMaterialStock::getExpireDate, bo.getExpireDate());
        lqw.eq(bo.getFrozenFlag() != null, WmMaterialStock::getFrozenFlag, bo.getFrozenFlag());
        return lqw;
    }
}

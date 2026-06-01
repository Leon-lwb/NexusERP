package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.bo.WmMaterialStockTxBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockTxVo;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.IWmMaterialStockTxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 库存事务Service业务层处理
 *
 * @author nexus
 * @date 2025-12-12
 */
@DS("erp")
@Service
public class WmMaterialStockTxServiceImpl extends ServiceImpl<WmMaterialStockTxMapper, WmMaterialStockTx> implements IWmMaterialStockTxService {

    @Override
    public TableDataInfo<WmMaterialStockTxVo> selectPageList(WmMaterialStockTxBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMaterialStockTx> lqw = buildQueryWrapper(bo);
        Page<WmMaterialStockTxVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmMaterialStockTxVo> selectList(WmMaterialStockTxBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmMaterialStockTxVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMaterialStockTxBo bo) {
        WmMaterialStockTx entity = MapstructUtils.convert(bo, WmMaterialStockTx.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMaterialStockTxBo bo) {
        WmMaterialStockTx entity = MapstructUtils.convert(bo, WmMaterialStockTx.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<WmMaterialStockTx> buildQueryWrapper(WmMaterialStockTxBo bo) {
        LambdaQueryWrapper<WmMaterialStockTx> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getTxType()), WmMaterialStockTx::getTxType, bo.getTxType());
        lqw.eq(bo.getMaterialId() != null, WmMaterialStockTx::getMaterialId, bo.getMaterialId());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialCode()), WmMaterialStockTx::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), WmMaterialStockTx::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialSpec()), WmMaterialStockTx::getMaterialSpec, bo.getMaterialSpec());
        lqw.eq(StringUtils.isNotBlank(bo.getBaseUomId()), WmMaterialStockTx::getBaseUomId, bo.getBaseUomId());
        lqw.like(StringUtils.isNotBlank(bo.getBaseUomName()), WmMaterialStockTx::getBaseUomName, bo.getBaseUomName());
        lqw.eq(bo.getBatchId() != null, WmMaterialStockTx::getBatchId, bo.getBatchId());
        lqw.eq(StringUtils.isNotBlank(bo.getBatchCode()), WmMaterialStockTx::getBatchCode, bo.getBatchCode());
        lqw.eq(bo.getWarehouseId() != null, WmMaterialStockTx::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getWarehouseCode()), WmMaterialStockTx::getWarehouseCode, bo.getWarehouseCode());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmMaterialStockTx::getWarehouseName, bo.getWarehouseName());
        lqw.eq(bo.getAreaId() != null, WmMaterialStockTx::getAreaId, bo.getAreaId());
        lqw.eq(StringUtils.isNotBlank(bo.getAreaCode()), WmMaterialStockTx::getAreaCode, bo.getAreaCode());
        lqw.like(StringUtils.isNotBlank(bo.getAreaName()), WmMaterialStockTx::getAreaName, bo.getAreaName());
        lqw.eq(bo.getLocationId() != null, WmMaterialStockTx::getLocationId, bo.getLocationId());
        lqw.eq(StringUtils.isNotBlank(bo.getLocationCode()), WmMaterialStockTx::getLocationCode, bo.getLocationCode());
        lqw.like(StringUtils.isNotBlank(bo.getLocationName()), WmMaterialStockTx::getLocationName, bo.getLocationName());
        lqw.eq(bo.getPackageId() != null, WmMaterialStockTx::getPackageId, bo.getPackageId());
        lqw.eq(StringUtils.isNotBlank(bo.getPackageCode()), WmMaterialStockTx::getPackageCode, bo.getPackageCode());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceDocType()), WmMaterialStockTx::getSourceDocType, bo.getSourceDocType());
        lqw.eq(bo.getSourceDocId() != null, WmMaterialStockTx::getSourceDocId, bo.getSourceDocId());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceDocCode()), WmMaterialStockTx::getSourceDocCode, bo.getSourceDocCode());
        lqw.eq(bo.getSourceDocLineId() != null, WmMaterialStockTx::getSourceDocLineId, bo.getSourceDocLineId());
        lqw.eq(bo.getMaterialStockId() != null, WmMaterialStockTx::getMaterialStockId, bo.getMaterialStockId());
        lqw.eq(bo.getTxFlag() != null, WmMaterialStockTx::getTxFlag, bo.getTxFlag());
        lqw.eq(bo.getTxQuantity() != null, WmMaterialStockTx::getTxQuantity, bo.getTxQuantity());
        lqw.eq(bo.getBeforeQuantity() != null, WmMaterialStockTx::getBeforeQuantity, bo.getBeforeQuantity());
        lqw.eq(bo.getAfterQuantity() != null, WmMaterialStockTx::getAfterQuantity, bo.getAfterQuantity());
        lqw.eq(bo.getTxDate() != null, WmMaterialStockTx::getTxDate, bo.getTxDate());
        lqw.eq(bo.getRelatedTxId() != null, WmMaterialStockTx::getRelatedTxId, bo.getRelatedTxId());
        lqw.eq(bo.getErpDate() != null, WmMaterialStockTx::getErpDate, bo.getErpDate());
        lqw.eq(bo.getRecptDate() != null, WmMaterialStockTx::getRecptDate, bo.getRecptDate());
        return lqw;
    }
}

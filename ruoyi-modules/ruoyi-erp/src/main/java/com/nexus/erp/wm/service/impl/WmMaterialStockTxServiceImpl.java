package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.core.exception.ServiceException;
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

import java.util.List;

/**
 * 库存事务Service业务层处理
 *
 * @author nexus
 * @date 2025-12-12
 */
@DS("erp")
@Service
public class WmMaterialStockTxServiceImpl
        extends ServiceImpl<WmMaterialStockTxMapper, WmMaterialStockTx>
        implements IWmMaterialStockTxService {

    /**
     * 分页查询物料库存流水列表
     *
     * @param bo 物料库存流水业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmMaterialStockTxVo> selectPageList(
            WmMaterialStockTxBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMaterialStockTx> lqw = buildQueryWrapper(bo);
        Page<WmMaterialStockTxVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询物料库存流水列表
     *
     * @param bo 物料库存流水业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmMaterialStockTxVo> selectList(WmMaterialStockTxBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询物料库存流水
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmMaterialStockTxVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增物料库存流水
     *
     * @param bo 物料库存流水业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMaterialStockTxBo bo) {
        throw new ServiceException("库存和已过账事实只允许由来源单据产生；修正请使用来源单据");
    }

    /**
     * 修改物料库存流水
     *
     * @param bo 物料库存流水业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMaterialStockTxBo bo) {
        throw new ServiceException("库存和已过账事实只允许由来源单据产生；修正请使用来源单据");
    }

    /**
     * 删除物料库存流水
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        throw new ServiceException("库存和已过账事实只允许由来源单据产生；修正请使用来源单据");
    }

    private LambdaQueryWrapper<WmMaterialStockTx> buildQueryWrapper(WmMaterialStockTxBo bo) {
        LambdaQueryWrapper<WmMaterialStockTx> lqw = Wrappers.lambdaQuery();
        // 事务类型支持逗号分隔多值（如 "RESERVE,UNRESERVE"），单值时退化为 eq
        if (StringUtils.isNotBlank(bo.getTxType())) {
            String[] types = bo.getTxType().split(",");
            lqw.in(WmMaterialStockTx::getTxType, (Object[]) types);
        }
        lqw.eq(bo.getMaterialId() != null, WmMaterialStockTx::getMaterialId, bo.getMaterialId());
        lqw.eq(
                StringUtils.isNotBlank(bo.getMaterialCode()),
                WmMaterialStockTx::getMaterialCode,
                bo.getMaterialCode());
        lqw.like(
                StringUtils.isNotBlank(bo.getMaterialName()),
                WmMaterialStockTx::getMaterialName,
                bo.getMaterialName());
        lqw.eq(
                StringUtils.isNotBlank(bo.getMaterialSpec()),
                WmMaterialStockTx::getMaterialSpec,
                bo.getMaterialSpec());
        lqw.eq(
                StringUtils.isNotBlank(bo.getBaseUomId()),
                WmMaterialStockTx::getBaseUomId,
                bo.getBaseUomId());
        lqw.like(
                StringUtils.isNotBlank(bo.getBaseUomName()),
                WmMaterialStockTx::getBaseUomName,
                bo.getBaseUomName());
        lqw.eq(bo.getBatchId() != null, WmMaterialStockTx::getBatchId, bo.getBatchId());
        lqw.eq(
                StringUtils.isNotBlank(bo.getBatchCode()),
                WmMaterialStockTx::getBatchCode,
                bo.getBatchCode());
        lqw.eq(bo.getWarehouseId() != null, WmMaterialStockTx::getWarehouseId, bo.getWarehouseId());
        lqw.eq(
                StringUtils.isNotBlank(bo.getWarehouseCode()),
                WmMaterialStockTx::getWarehouseCode,
                bo.getWarehouseCode());
        lqw.like(
                StringUtils.isNotBlank(bo.getWarehouseName()),
                WmMaterialStockTx::getWarehouseName,
                bo.getWarehouseName());
        lqw.eq(bo.getAreaId() != null, WmMaterialStockTx::getAreaId, bo.getAreaId());
        lqw.eq(
                StringUtils.isNotBlank(bo.getAreaCode()),
                WmMaterialStockTx::getAreaCode,
                bo.getAreaCode());
        lqw.like(
                StringUtils.isNotBlank(bo.getAreaName()),
                WmMaterialStockTx::getAreaName,
                bo.getAreaName());
        lqw.eq(bo.getLocationId() != null, WmMaterialStockTx::getLocationId, bo.getLocationId());
        lqw.eq(
                StringUtils.isNotBlank(bo.getLocationCode()),
                WmMaterialStockTx::getLocationCode,
                bo.getLocationCode());
        lqw.like(
                StringUtils.isNotBlank(bo.getLocationName()),
                WmMaterialStockTx::getLocationName,
                bo.getLocationName());
        lqw.eq(bo.getPackageId() != null, WmMaterialStockTx::getPackageId, bo.getPackageId());
        lqw.eq(
                StringUtils.isNotBlank(bo.getPackageCode()),
                WmMaterialStockTx::getPackageCode,
                bo.getPackageCode());
        lqw.eq(
                StringUtils.isNotBlank(bo.getSourceDocType()),
                WmMaterialStockTx::getSourceDocType,
                bo.getSourceDocType());
        lqw.eq(bo.getSourceDocId() != null, WmMaterialStockTx::getSourceDocId, bo.getSourceDocId());
        lqw.eq(
                StringUtils.isNotBlank(bo.getSourceDocCode()),
                WmMaterialStockTx::getSourceDocCode,
                bo.getSourceDocCode());
        lqw.eq(
                bo.getSourceDocLineId() != null,
                WmMaterialStockTx::getSourceDocLineId,
                bo.getSourceDocLineId());
        lqw.eq(
                bo.getMaterialStockId() != null,
                WmMaterialStockTx::getMaterialStockId,
                bo.getMaterialStockId());
        lqw.eq(bo.getTxFlag() != null, WmMaterialStockTx::getTxFlag, bo.getTxFlag());
        lqw.eq(bo.getTxQuantity() != null, WmMaterialStockTx::getTxQuantity, bo.getTxQuantity());
        lqw.eq(
                bo.getBeforeQuantity() != null,
                WmMaterialStockTx::getBeforeQuantity,
                bo.getBeforeQuantity());
        lqw.eq(
                bo.getAfterQuantity() != null,
                WmMaterialStockTx::getAfterQuantity,
                bo.getAfterQuantity());
        lqw.eq(bo.getTxDate() != null, WmMaterialStockTx::getTxDate, bo.getTxDate());
        lqw.eq(bo.getRelatedTxId() != null, WmMaterialStockTx::getRelatedTxId, bo.getRelatedTxId());
        lqw.eq(bo.getErpDate() != null, WmMaterialStockTx::getErpDate, bo.getErpDate());
        lqw.eq(bo.getRecptDate() != null, WmMaterialStockTx::getRecptDate, bo.getRecptDate());
        return lqw;
    }
}

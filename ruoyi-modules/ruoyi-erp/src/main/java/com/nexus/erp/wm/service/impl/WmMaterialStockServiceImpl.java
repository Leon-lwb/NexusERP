package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.bo.WmMaterialStockBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.service.IWmMaterialStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 库存清单Service业务层处理
 *
 * @author nexus
 * @date 2025-12-03
 */
@Slf4j
@DS("erp")
@Service
public class WmMaterialStockServiceImpl extends ServiceImpl<WmMaterialStockMapper, WmMaterialStock> implements IWmMaterialStockService {

    /** 物料库存数据访问映射器（Mapper） */
    private final BdMaterialMapper bdMaterialMapper;

    /** 收货单行 Mapper（IQC 冻结时按收货单定位批次号） */
    private final WmMaterialReceiveLineMapper materialReceiveLineMapper;

    /**
     * 物料库存处理（WmMaterialStockServiceImpl）
     *
     * @param bdMaterialMapper         参数 bdMaterialMapper
     * @param materialReceiveLineMapper 收货单行 Mapper
     */
    public WmMaterialStockServiceImpl(BdMaterialMapper bdMaterialMapper,
                                      WmMaterialReceiveLineMapper materialReceiveLineMapper) {
        this.bdMaterialMapper = bdMaterialMapper;
        this.materialReceiveLineMapper = materialReceiveLineMapper;
    }

    /**
     * 分页查询库存清单列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 库存清单分页列表
     */
    @Override
    public TableDataInfo<WmMaterialStockVo> selectPageList(WmMaterialStockBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMaterialStock> lqw = buildQueryWrapper(bo);
        Page<WmMaterialStockVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询库存清单列表
     *
     * @param bo 查询参数
     * @return 库存清单列表
     */
    @Override
    public List<WmMaterialStockVo> selectList(WmMaterialStockBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取库存清单详情
     *
     * @param id 库存ID
     * @return 库存清单详情
     */
    @Override
    public WmMaterialStockVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增库存清单
     * <p>
     * 虚拟件物料不入库（虚拟件跳层，不产生实际库存）。
     *
     * @param bo 库存清单业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMaterialStockBo bo) {
        throw new com.nexus.common.core.exception.ServiceException("库存和已过账事实只允许由来源单据产生；修正请使用来源单据");
    }

    /**
     * 修改库存清单
     *
     * @param bo 库存清单业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMaterialStockBo bo) {
        throw new com.nexus.common.core.exception.ServiceException("库存和已过账事实只允许由来源单据产生；修正请使用来源单据");
    }

    /**
     * 批量删除库存清单
     *
     * @param ids 库存ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        throw new com.nexus.common.core.exception.ServiceException("库存和已过账事实只允许由来源单据产生；修正请使用来源单据");
    }

    /**
     * 根据物料分类ID查询库存清单
     *
     * @param categoryIds 物料分类ID列表
     * @return 库存清单列表
     */
    @Override
    public List<WmMaterialStockVo> selectByCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new ArrayList<>();
        }
        return baseMapper.selectVoList(
            Wrappers.lambdaQuery(WmMaterialStock.class)
                .in(WmMaterialStock::getCategoryId, categoryIds)
        );
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return MyBatis查询包装器
     */
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

    /**
     * IQC 不合格冻结:按收货单+物料定位批次号,冻结对应库存行(与 WmBatchServiceImpl.markQcStatusByReceive 同源定位)
     * <p>定位顺序: 收货行批次号 -&gt; 收货行批次ID; 两者都没有时记 warn 返回 0(单据级门禁兜底)。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int freezeStockByReceiveBatches(Long receiveId, Long materialId) {
        if (receiveId == null || materialId == null) {
            return 0;
        }
        List<WmMaterialReceiveLine> lines = materialReceiveLineMapper.selectList(
            Wrappers.<WmMaterialReceiveLine>lambdaQuery()
                .eq(WmMaterialReceiveLine::getReceiveId, receiveId)
                .eq(WmMaterialReceiveLine::getMaterialId, materialId));
        List<String> batchCodes = lines.stream()
            .map(WmMaterialReceiveLine::getBatchCode)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .collect(Collectors.toList());
        List<Long> batchIds = lines.stream()
            .map(WmMaterialReceiveLine::getBatchId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (batchCodes.isEmpty() && batchIds.isEmpty()) {
            // 无批次号也无批次ID: 无法定位库存行 —— 显式记录, 由 confirmShelf 的单据级 iqc_status 门禁兜底,
            // 不再"定位不到批次就当作没发生"(审计 MES-X-01 旁路)
            log.warn("IQC不合格冻结跳过: 收货行无批次号/批次ID, 无法定位库存行: receiveId={}, materialId={}",
                receiveId, materialId);
            return 0;
        }
        LambdaUpdateWrapper<WmMaterialStock> wrapper = Wrappers.<WmMaterialStock>lambdaUpdate()
            .set(WmMaterialStock::getFrozenFlag, 1L)
            // ERP-WM-09: 冻结必须递增 version，否则乐观锁失效——在途出库仍能用旧 version 扣减被冻结库存
            .setSql("version = version + 1")
            .eq(WmMaterialStock::getMaterialId, materialId)
            .eq(WmMaterialStock::getFrozenFlag, 0L);
        if (!batchCodes.isEmpty()) {
            wrapper.in(WmMaterialStock::getBatchCode, batchCodes);
        } else {
            wrapper.in(WmMaterialStock::getBatchId, batchIds);
        }
        int rows = baseMapper.update(null, wrapper);
        log.info("IQC不合格冻结库存: receiveId={}, materialId={}, batchCodes={}, batchIds={}, 影响行数={}",
            receiveId, materialId, batchCodes, batchIds, rows);
        return rows;
    }

    /**
     * 解冻库存(按物料+批次号): frozen_flag 1 -&gt; 0 的条件更新(幂等, 已解冻行不再受影响)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unfreezeByBatch(Long materialId, String batchCode, String reason) {
        if (materialId == null || StringUtils.isBlank(batchCode)) {
            return 0;
        }
        int rows = baseMapper.update(null, Wrappers.<WmMaterialStock>lambdaUpdate()
            .set(WmMaterialStock::getFrozenFlag, 0L)
            // ERP-WM-09: 解冻同样递增 version，使在途的旧版本扣减立即失效
            .setSql("version = version + 1")
            .eq(WmMaterialStock::getMaterialId, materialId)
            .eq(WmMaterialStock::getBatchCode, batchCode)
            .eq(WmMaterialStock::getFrozenFlag, 1L));
        log.info("库存解冻(批次): materialId={}, batchCode={}, 影响行数={}, reason={}",
            materialId, batchCode, rows, reason);
        return rows;
    }

    /**
     * 解冻库存(按收货单+物料): 定位该收货单该物料的批次号(无则回退批次ID)后解冻
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unfreezeByReceive(Long receiveId, Long materialId, String reason) {
        if (receiveId == null || materialId == null) {
            return 0;
        }
        List<WmMaterialReceiveLine> lines = materialReceiveLineMapper.selectList(
            Wrappers.<WmMaterialReceiveLine>lambdaQuery()
                .eq(WmMaterialReceiveLine::getReceiveId, receiveId)
                .eq(WmMaterialReceiveLine::getMaterialId, materialId));
        List<String> batchCodes = lines.stream()
            .map(WmMaterialReceiveLine::getBatchCode)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .collect(Collectors.toList());
        List<Long> batchIds = lines.stream()
            .map(WmMaterialReceiveLine::getBatchId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (batchCodes.isEmpty() && batchIds.isEmpty()) {
            log.warn("库存解冻跳过: 收货行无批次号/批次ID, 无法定位库存行: receiveId={}, materialId={}, reason={}",
                receiveId, materialId, reason);
            return 0;
        }
        LambdaUpdateWrapper<WmMaterialStock> wrapper = Wrappers.<WmMaterialStock>lambdaUpdate()
            .set(WmMaterialStock::getFrozenFlag, 0L)
            // ERP-WM-09: 解冻同样递增 version，使在途的旧版本扣减立即失效
            .setSql("version = version + 1")
            .eq(WmMaterialStock::getMaterialId, materialId)
            .eq(WmMaterialStock::getFrozenFlag, 1L);
        if (!batchCodes.isEmpty()) {
            wrapper.in(WmMaterialStock::getBatchCode, batchCodes);
        } else {
            wrapper.in(WmMaterialStock::getBatchId, batchIds);
        }
        int rows = baseMapper.update(null, wrapper);
        log.info("库存解冻(收货单): receiveId={}, materialId={}, batchCodes={}, batchIds={}, 影响行数={}, reason={}",
            receiveId, materialId, batchCodes, batchIds, rows, reason);
        return rows;
    }
}

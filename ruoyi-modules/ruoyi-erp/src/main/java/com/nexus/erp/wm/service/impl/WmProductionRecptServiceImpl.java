package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmProductionRecpt;
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.domain.vo.WmProductionRecptVo;
import com.nexus.erp.wm.mapper.WmProductionRecptMapper;
import com.nexus.erp.wm.service.IWmProductionRecptService;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmProductionRecptServiceImpl implements IWmProductionRecptService {

    /** 生产完工入库单编码规则编码（sys_auto_code_rule.rule_code） */
    private static final String PRODUCTION_RECPT_CODE_RULE = "WM_PRODUCTION_RECPT_CODE";

    private final WmProductionRecptMapper baseMapper;
    private final IWmStockOperationService stockOperationService;
    private final AutoCodeUtil autoCodeUtil;

    @Override
    public TableDataInfo<WmProductionRecptVo> selectPageList(WmProductionRecptBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmProductionRecpt> lqw = buildQueryWrapper(bo);
        Page<WmProductionRecptVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmProductionRecptVo> selectList(WmProductionRecptBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmProductionRecptVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmProductionRecptBo bo) {
        WmProductionRecpt entity = MapstructUtils.convert(bo, WmProductionRecpt.class);
        // 入库单号为空时按规则自动生成（前缀 SCRK + yyyyMMdd + 4位日循环流水号）
        if (StringUtils.isBlank(entity.getRecptCode())) {
            entity.setRecptCode(autoCodeUtil.genSerialCode(PRODUCTION_RECPT_CODE_RULE, null));
        }
        if (entity.getStatus() == null) {
            entity.setStatus("DRAFT");
        }
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmProductionRecptBo bo) {
        WmProductionRecpt entity = MapstructUtils.convert(bo, WmProductionRecpt.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long recptId) {
        WmProductionRecpt recpt = baseMapper.selectById(recptId);
        if (recpt == null) {
            throw new ServiceException("生产入库单不存在");
        }
        if ("EXECUTED".equals(recpt.getStatus())) {
            throw new ServiceException("生产入库单已执行");
        }
        if (recpt.getQuantity() == null || recpt.getQuantity().signum() <= 0) {
            throw new ServiceException("入库数量必须大于0");
        }

        stockOperationService.postProductionCompletion(
            recpt.getWoId(),
            recpt.getMaterialId(),
            recpt.getMaterialCode(),
            recpt.getMaterialName(),
            recpt.getQuantity(),
            recpt.getWarehouseId(),
            recpt.getLocationId(),
            new Date()
        );

        recpt.setStatus("EXECUTED");
        baseMapper.updateById(recpt);
        log.info("生产入库单执行完成, recptId={}", recptId);
    }

    /**
     * 创建生产入库单并立即执行（用于工单完工自动入库）
     * <p>
     * 一次调用内完成"生成单据 + 过账入库"。
     * <p>
     * 幂等保障（双重）：
     * <ol>
     *   <li>单据级：同工单已存在 EXECUTED 入库单时直接返回，避免 MQ 重投产生重复单据</li>
     *   <li>库存级：底层 ProductionCompletionStrategy.hasPosted(woId) 保证同工单不重复入库</li>
     * </ol>
     * 注：单据级幂等假设"一工单一入库单"，与底层 hasPosted 的"一工单一入库"语义一致。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndExecute(Long woId, Long materialId, String materialCode, String materialName,
                                  String materialSpec, BigDecimal quantity,
                                  Long warehouseId, String warehouseCode, String warehouseName,
                                  Long locationId, String locationCode, String locationName,
                                  Date recptDate) {
        // 单据级幂等：同工单已存在已执行入库单则直接返回（防 MQ 重投产生重复单据）
        List<WmProductionRecpt> existing = baseMapper.selectList(
            Wrappers.<WmProductionRecpt>lambdaQuery()
                .eq(WmProductionRecpt::getWoId, woId)
                .eq(WmProductionRecpt::getStatus, "EXECUTED")
                .last("LIMIT 1"));
        if (!existing.isEmpty()) {
            log.info("工单[{}]已存在已执行的生产入库单, 跳过重复创建: recptId={}",
                woId, existing.get(0).getRecptId());
            return existing.get(0).getRecptId();
        }

        WmProductionRecpt recpt = new WmProductionRecpt();
        recpt.setRecptCode(autoCodeUtil.genSerialCode(PRODUCTION_RECPT_CODE_RULE, null));
        recpt.setWoId(woId);
        recpt.setMaterialId(materialId);
        recpt.setMaterialCode(materialCode);
        recpt.setMaterialName(materialName);
        recpt.setMaterialSpec(materialSpec);
        recpt.setQuantity(quantity);
        recpt.setWarehouseId(warehouseId);
        recpt.setWarehouseCode(warehouseCode);
        recpt.setWarehouseName(warehouseName);
        recpt.setLocationId(locationId);
        recpt.setLocationCode(locationCode);
        recpt.setLocationName(locationName);
        recpt.setRecptDate(recptDate != null ? recptDate : new Date());
        recpt.setStatus("DRAFT");
        recpt.setRemark("工单完工自动入库");
        baseMapper.insert(recpt);

        // 立即过账：底层 hasPosted(woId) 保证同工单不重复入库
        try {
            stockOperationService.postProductionCompletion(
                woId, materialId, materialCode, materialName,
                quantity, warehouseId, locationId, recpt.getRecptDate());
        } catch (Exception e) {
            log.error("生产入库单过账失败, recptId={}, woId={}", recpt.getRecptId(), woId, e);
            throw e;
        }
        recpt.setStatus("EXECUTED");
        baseMapper.updateById(recpt);
        log.info("生产入库单自动创建并执行完成, recptId={}, recptCode={}, woId={}",
            recpt.getRecptId(), recpt.getRecptCode(), woId);
        return recpt.getRecptId();
    }

    private LambdaQueryWrapper<WmProductionRecpt> buildQueryWrapper(WmProductionRecptBo bo) {
        LambdaQueryWrapper<WmProductionRecpt> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getRecptCode()), WmProductionRecpt::getRecptCode, bo.getRecptCode());
        lqw.eq(bo.getWoId() != null, WmProductionRecpt::getWoId, bo.getWoId());
        lqw.like(StringUtils.isNotBlank(bo.getWoNo()), WmProductionRecpt::getWoNo, bo.getWoNo());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmProductionRecpt::getStatus, bo.getStatus());
        lqw.orderByDesc(WmProductionRecpt::getCreateTime);
        return lqw;
    }
}

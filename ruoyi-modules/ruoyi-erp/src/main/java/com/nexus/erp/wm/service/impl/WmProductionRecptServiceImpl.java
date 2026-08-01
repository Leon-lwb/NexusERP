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
import com.nexus.erp.wm.domain.vo.WmBatchVo;
import com.nexus.erp.wm.domain.vo.WmProductionRecptVo;
import com.nexus.erp.wm.mapper.WmProductionRecptMapper;
import com.nexus.erp.wm.service.IWmBatchService;
import com.nexus.erp.wm.service.IWmProductionRecptService;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
/**
 * 生产收货服务实现
 * <p>
 * 实现生产收货服务接口，处理生产收货的增删改查、业务校验与库存联动。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmProductionRecptServiceImpl implements IWmProductionRecptService {

    /** 生产完工入库单编码规则编码（sys_auto_code_rule.rule_code） */
    private static final String PRODUCTION_RECPT_CODE_RULE = "WM_PRODUCTION_RECPT_CODE";
    /** 完工入库为成品自动生成批次的来源单据类型 */
    private static final String BATCH_SOURCE_DOC_TYPE = "PRD_WORK_ORDER";

    /** 基础数据访问映射器（Mapper） */
    private final WmProductionRecptMapper baseMapper;
    /** 库存操作业务服务，提供库存操作相关能力 */
    private final IWmStockOperationService stockOperationService;
    /** 自动编码工具类 */
    private final AutoCodeUtil autoCodeUtil;
    /** 批次业务服务，提供批次相关能力 */
    private final IWmBatchService batchService;

    /**
     * 分页查询生产收货列表
     *
     * @param bo 生产收货业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmProductionRecptVo> selectPageList(WmProductionRecptBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmProductionRecpt> lqw = buildQueryWrapper(bo);
        Page<WmProductionRecptVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询生产收货列表
     *
     * @param bo 生产收货业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmProductionRecptVo> selectList(WmProductionRecptBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询生产收货
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmProductionRecptVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增生产收货
     *
     * @param bo 生产收货业务对象（BO）
     * @return 结果
     */
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

    /**
     * 修改生产收货
     *
     * @param bo 生产收货业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmProductionRecptBo bo) {
        WmProductionRecpt entity = MapstructUtils.convert(bo, WmProductionRecpt.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 删除生产收货
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 执行生产收货相关操作
     *
     * @param recptId 收货 ID
     * @return 结果
     */
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
        // 并发防重：条件更新抢占（非 EXECUTED → EXECUTING），影响行数为 0 说明已被并发执行
        int claimed = baseMapper.update(null, Wrappers.<WmProductionRecpt>lambdaUpdate()
            .set(WmProductionRecpt::getStatus, "EXECUTING")
            .eq(WmProductionRecpt::getRecptId, recptId)
            .ne(WmProductionRecpt::getStatus, "EXECUTED"));
        if (claimed == 0) {
            throw new ServiceException("生产入库单已执行");
        }
        if (recpt.getQuantity() == null || recpt.getQuantity().signum() <= 0) {
            throw new ServiceException("入库数量必须大于0");
        }

        // 为成品补生成批次（若单据未携带），与库存记录共用同一批次
        Long batchId = recpt.getBatchId();
        String batchCode = recpt.getBatchCode();
        if (batchId == null) {
            WmBatchVo batch = batchService.createBatchByMaterial(recpt.getMaterialId(),
                BATCH_SOURCE_DOC_TYPE, recpt.getWoId(), recpt.getWoNo(), recpt.getWarehouseId(), null);
            batchId = batch.getBatchId();
            batchCode = batch.getBatchCode();
        }

        Date now = new Date();
        stockOperationService.postProductionCompletion(
            recpt.getWoId(),
            recpt.getWoNo(),
            recpt.getMaterialId(),
            recpt.getMaterialCode(),
            recpt.getMaterialName(),
            recpt.getQuantity(),
            recpt.getWarehouseId(),
            recpt.getLocationId(),
            batchId,
            batchCode,
            now
        );

        // 回写单据上的批次（若本次补生成）
        if (recpt.getBatchId() == null) {
            recpt.setBatchId(batchId);
            recpt.setBatchCode(batchCode);
        }
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
    public Long createAndExecute(Long woId, String woNo, Long materialId, String materialCode, String materialName,
                                  String materialSpec, BigDecimal quantity,
                                  Long warehouseId, String warehouseCode, String warehouseName,
                                  Long locationId, String locationCode, String locationName,
                                  Date recptDate) {
        // 单据级幂等：同工单已存在已执行入库单则补差额入库（支持分次完工/增量完工），
        // 避免"一工单一入库"假设下第二次完工消息被整体跳过导致成品入库量短缺
        List<WmProductionRecpt> existing = baseMapper.selectList(
            Wrappers.<WmProductionRecpt>lambdaQuery()
                .eq(WmProductionRecpt::getWoId, woId)
                .eq(WmProductionRecpt::getStatus, "EXECUTED")
                .orderByDesc(WmProductionRecpt::getCreateTime)
                .last("LIMIT 1"));
        if (!existing.isEmpty()) {
            WmProductionRecpt last = existing.get(0);
            BigDecimal alreadyIn = last.getQuantity() == null ? BigDecimal.ZERO : last.getQuantity();
            if (quantity.compareTo(alreadyIn) <= 0) {
                log.info("工单[{}]已入库数量 {} 不小于本次 {}，跳过: recptId={}", woId, alreadyIn, quantity, last.getRecptId());
                return last.getRecptId();
            }
            // 本次消息数量大于已入库数量：补差额（增量完工场景）
            BigDecimal delta = quantity.subtract(alreadyIn);
            log.info("工单[{}]增量完工补入库: 已入库={}, 本次累计={}, 补差额={}", woId, alreadyIn, quantity, delta);
            WmBatchVo batch = batchService.createBatchByMaterial(materialId,
                BATCH_SOURCE_DOC_TYPE, woId, woNo, warehouseId, null);
            stockOperationService.postProductionCompletion(
                woId, woNo, materialId, materialCode, materialName,
                delta, warehouseId, locationId,
                batch.getBatchId(), batch.getBatchCode(), recptDate != null ? recptDate : new Date());
            // 更新单据数量为累计值
            baseMapper.update(null, Wrappers.<WmProductionRecpt>lambdaUpdate()
                .set(WmProductionRecpt::getQuantity, quantity)
                .eq(WmProductionRecpt::getRecptId, last.getRecptId()));
            return last.getRecptId();
        }

        // 为成品自动生成批次（生产入库单据留痕 + 库存记录共用同一批次）
        WmBatchVo batch = batchService.createBatchByMaterial(materialId,
            BATCH_SOURCE_DOC_TYPE, woId, woNo, warehouseId, null);

        WmProductionRecpt recpt = new WmProductionRecpt();
        recpt.setRecptCode(autoCodeUtil.genSerialCode(PRODUCTION_RECPT_CODE_RULE, null));
        recpt.setWoId(woId);
        recpt.setWoNo(woNo);
        recpt.setMaterialId(materialId);
        recpt.setMaterialCode(materialCode);
        recpt.setMaterialName(materialName);
        recpt.setMaterialSpec(materialSpec);
        recpt.setQuantity(quantity);
        recpt.setBatchId(batch.getBatchId());
        recpt.setBatchCode(batch.getBatchCode());
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
                woId, woNo, materialId, materialCode, materialName,
                quantity, warehouseId, locationId,
                batch.getBatchId(), batch.getBatchCode(), recpt.getRecptDate());
        } catch (Exception e) {
            log.error("生产入库单过账失败, recptId={}, woId={}", recpt.getRecptId(), woId, e);
            throw e;
        }
        recpt.setStatus("EXECUTED");
        baseMapper.updateById(recpt);
        log.info("生产入库单自动创建并执行完成, recptId={}, recptCode={}, woId={}, woNo={}, batchCode={}",
            recpt.getRecptId(), recpt.getRecptCode(), woId, woNo, batch.getBatchCode());
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

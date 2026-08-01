package com.nexus.mes.lt.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.lt.domain.LtLot;
import com.nexus.mes.lt.domain.bo.LtLotBo;
import com.nexus.mes.lt.domain.vo.LtLotVo;
import com.nexus.mes.lt.domain.vo.LtSnBindingVo;
import com.nexus.mes.lt.domain.vo.LtWoConsumptionVo;
import com.nexus.mes.lt.mapper.LtLotMapper;
import com.nexus.mes.lt.mapper.LtSnBindingMapper;
import com.nexus.mes.lt.mapper.LtWoConsumptionMapper;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MES批次 Service 业务层实现
 * <p>
 * 状态机: IN_STOCK -> IN_USE -> CONSUMED
 *                  └-> QUARANTINED -> SCRAPPED
 * FEFO: 按 expiry_date ASC, produced_date ASC, lot_id ASC 排序推荐
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("ltLotService")
public class LtLotServiceImpl implements ILtLotService {

    private final LtLotMapper baseMapper;
    private final LtWoConsumptionMapper consumptionMapper;
    private final LtSnBindingMapper bindingMapper;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 分页查询批次列表
     * <p>结果中填充 remainingQty 计算字段。</p>
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 批次分页列表
     */
    @Override
    public TableDataInfo<LtLotVo> selectPageList(LtLotBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<LtLot> lqw = buildQueryWrapper(bo);
        Page<LtLotVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        // 填充 remainingQty
        page.getRecords().forEach(this::fillRemainingQty);
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID获取批次详情
     * <p>返回时填充 remainingQty 计算字段。</p>
     *
     * @param lotId 批次ID
     * @return 批次详情(含剩余数量)
     */
    @Override
    public LtLotVo selectById(Long lotId) {
        LtLotVo vo = baseMapper.selectVoById(lotId);
        if (vo != null) {
            fillRemainingQty(vo);
        }
        return vo;
    }

    /**
     * 根据批次编码查询
     * <p>返回时填充 remainingQty 计算字段。</p>
     *
     * @param lotCode 批次编码
     * @return 批次详情(含剩余数量)
     */
    @Override
    public LtLotVo selectByCode(String lotCode) {
        LtLotVo vo = baseMapper.selectVoOne(Wrappers.<LtLot>lambdaQuery()
            .eq(LtLot::getLotCode, lotCode));
        if (vo != null) {
            fillRemainingQty(vo);
        }
        return vo;
    }

    /**
     * 新增批次(自动生成编码,初始状态 IN_STOCK, QC PENDING)。
     * <p>编码为空时自动走 MES_LOT_CODE 规则生成; 编码唯一性校验(uk_lot_code 兜底)。</p>
     *
     * @param bo 批次业务对象
     * @return 新增成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(LtLotBo bo) {
        // 数量校验：必须非空且大于 0，防止下游 consume/release 解引用 NPE 或产生负剩余批次
        if (bo.getQuantity() == null || bo.getQuantity().signum() <= 0) {
            throw new RuntimeException("批次数量必须大于0");
        }
        if (StringUtils.isBlank(bo.getLotCode())) {
            bo.setLotCode(generateLotCode());
        }
        // 校验编码唯一
        Long existCount = baseMapper.selectCount(Wrappers.<LtLot>lambdaQuery()
            .eq(LtLot::getLotCode, bo.getLotCode()));
        if (existCount != null && existCount > 0) {
            throw new RuntimeException("批次编码已存在: " + bo.getLotCode());
        }
        LtLot lot = new LtLot();
        lot.setLotCode(bo.getLotCode());
        lot.setMaterialId(bo.getMaterialId());
        lot.setMaterialCode(bo.getMaterialCode());
        lot.setMaterialName(bo.getMaterialName());
        lot.setSupplierId(bo.getSupplierId());
        lot.setSupplierName(bo.getSupplierName());
        lot.setSupplierLot(bo.getSupplierLot());
        lot.setQuantity(bo.getQuantity());
        lot.setConsumedQty(BigDecimal.ZERO);
        lot.setProducedDate(bo.getProducedDate());
        lot.setExpiryDate(bo.getExpiryDate());
        lot.setStatus(STATUS_IN_STOCK);
        lot.setQcStatus(QC_STATUS_PENDING);
        lot.setRemark(bo.getRemark());
        int rows = baseMapper.insert(lot);
        log.info("新增批次成功: lotCode={}, materialId={}", bo.getLotCode(), bo.getMaterialId());
        return rows > 0;
    }

    /**
     * 修改批次(仅 IN_STOCK/IN_USE 状态可编辑)。
     * <p>数量守卫: 不可小于已消耗数量(否则剩余为负,破坏 consume 不变量)。
     * 不允许通过编辑修改 status/consumedQty/qcStatus(走专用接口)。</p>
     *
     * @param bo 批次业务对象
     * @return 修改成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(LtLotBo bo) {
        LtLot lot = baseMapper.selectById(bo.getLotId());
        if (lot == null) {
            throw new RuntimeException("批次不存在: " + bo.getLotId());
        }
        // 状态校验: 仅 IN_STOCK/IN_USE 可编辑
        if (!STATUS_IN_STOCK.equals(lot.getStatus()) && !STATUS_IN_USE.equals(lot.getStatus())) {
            throw new RuntimeException("当前状态不可编辑: " + lot.getStatus());
        }
        // 数量守卫: 不可小于已消耗数量(否则剩余为负,破坏 consume 不变量)
        if (bo.getQuantity() != null && lot.getConsumedQty() != null
            && bo.getQuantity().compareTo(lot.getConsumedQty()) < 0) {
            throw new RuntimeException("批次数量不可小于已消耗数量: " + lot.getConsumedQty());
        }
        // 不允许通过编辑修改 status/consumedQty/qcStatus(走专用接口)
        lot.setSupplierId(bo.getSupplierId());
        lot.setSupplierName(bo.getSupplierName());
        lot.setSupplierLot(bo.getSupplierLot());
        lot.setQuantity(bo.getQuantity());
        lot.setProducedDate(bo.getProducedDate());
        lot.setExpiryDate(bo.getExpiryDate());
        lot.setRemark(bo.getRemark());
        int rows = baseMapper.updateById(lot);
        log.info("修改批次成功: lotId={}", bo.getLotId());
        return rows > 0;
    }

    /**
     * 批量删除批次(仅 IN_STOCK/SCRAPPED 状态可删)。
     * <p>孤儿守卫: 存在消耗/绑定记录则禁止删除,防止追溯链断裂。</p>
     *
     * @param ids 批次ID数组
     * @return 删除成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        // 校验状态: 仅 IN_STOCK/SCRAPPED 可删
        for (Long id : ids) {
            LtLot lot = baseMapper.selectById(id);
            if (lot == null) {
                continue;
            }
            if (!STATUS_IN_STOCK.equals(lot.getStatus()) && !STATUS_SCRAPPED.equals(lot.getStatus())) {
                throw new RuntimeException("批次" + lot.getLotCode() + "状态非在库/报废,不可删除");
            }
        }
        List<Long> idList = java.util.Arrays.asList(ids);
        // 孤儿守卫: 存在消耗/绑定记录则禁止删除(无 FK/级联,删除会断裂追溯链)
        Long consumptionCount = consumptionMapper.selectCount(
            Wrappers.<com.nexus.mes.lt.domain.LtWoConsumption>lambdaQuery()
                .in(com.nexus.mes.lt.domain.LtWoConsumption::getLotId, idList));
        if (consumptionCount != null && consumptionCount > 0) {
            throw new RuntimeException("存在工单批次消耗记录,不可删除(追溯完整性)");
        }
        Long bindingCount = bindingMapper.selectCount(
            Wrappers.<com.nexus.mes.lt.domain.LtSnBinding>lambdaQuery()
                .in(com.nexus.mes.lt.domain.LtSnBinding::getLotId, idList));
        if (bindingCount != null && bindingCount > 0) {
            throw new RuntimeException("存在SN批次绑定记录,不可删除(追溯完整性)");
        }
        int rows = baseMapper.deleteByIds(idList);
        log.info("批量删除批次: count={}", ids.length);
        return rows > 0;
    }

    /**
     * 批次隔离(IN_STOCK/IN_USE -> QUARANTINED)。
     * <p>CAS: .in(status, IN_STOCK, IN_USE) + rows==0 抛"状态已变更"。</p>
     *
     * @param lotId  批次ID
     * @param remark 隔离原因(可空)
     * @return 隔离成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean quarantine(Long lotId, String remark) {
        LtLot lot = baseMapper.selectById(lotId);
        if (lot == null) {
            throw new RuntimeException("批次不存在: " + lotId);
        }
        if (!STATUS_IN_STOCK.equals(lot.getStatus()) && !STATUS_IN_USE.equals(lot.getStatus())) {
            throw new RuntimeException("仅 IN_STOCK/IN_USE 状态可隔离,当前: " + lot.getStatus());
        }
        int rows = baseMapper.update(null, Wrappers.<LtLot>lambdaUpdate()
            .set(LtLot::getStatus, STATUS_QUARANTINED)
            .set(StringUtils.isNotBlank(remark), LtLot::getRemark, remark)
            .eq(LtLot::getLotId, lotId)
            .in(LtLot::getStatus, STATUS_IN_STOCK, STATUS_IN_USE));
        if (rows == 0) {
            throw new RuntimeException("批次状态已变更,隔离失败");
        }
        log.info("批次隔离: lotId={}", lotId);
        return true;
    }

    /**
     * 批次报废(QUARANTINED -> SCRAPPED)。
     * <p>CAS: .eq(status, QUARANTINED) + rows==0 抛"状态已变更"。</p>
     *
     * @param lotId  批次ID
     * @param remark 报废原因(可空)
     * @return 报废成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean scrap(Long lotId, String remark) {
        LtLot lot = baseMapper.selectById(lotId);
        if (lot == null) {
            throw new RuntimeException("批次不存在: " + lotId);
        }
        if (!STATUS_QUARANTINED.equals(lot.getStatus())) {
            throw new RuntimeException("仅 QUARANTINED 状态可报废,当前: " + lot.getStatus());
        }
        int rows = baseMapper.update(null, Wrappers.<LtLot>lambdaUpdate()
            .set(LtLot::getStatus, STATUS_SCRAPPED)
            .set(StringUtils.isNotBlank(remark), LtLot::getRemark, remark)
            .eq(LtLot::getLotId, lotId)
            .eq(LtLot::getStatus, STATUS_QUARANTINED));
        if (rows == 0) {
            throw new RuntimeException("批次状态已变更,报废失败");
        }
        log.info("批次报废: lotId={}", lotId);
        return true;
    }

    /**
     * 批次放行(QUARANTINED -> 恢复状态,隔离解除)。
     * <p>按剩余/已消耗恢复: 数量归零->CONSUMED, 部分消耗->IN_USE, 全剩余->IN_STOCK。
     * CAS: .eq(status, QUARANTINED) + rows==0 抛"状态已变更"。</p>
     *
     * @param lotId 批次ID
     * @return 放行成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean release(Long lotId) {
        LtLot lot = baseMapper.selectById(lotId);
        if (lot == null) {
            throw new RuntimeException("批次不存在: " + lotId);
        }
        if (!STATUS_QUARANTINED.equals(lot.getStatus())) {
            throw new RuntimeException("仅 QUARANTINED 状态可放行,当前: " + lot.getStatus());
        }
        // 放行: 按剩余/已消耗恢复状态(保留 IN_USE 语义: 部分消耗=使用中)
        BigDecimal remaining = lot.getQuantity().subtract(lot.getConsumedQty());
        String newStatus;
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            newStatus = STATUS_CONSUMED;
        } else if (lot.getConsumedQty() != null && lot.getConsumedQty().compareTo(BigDecimal.ZERO) > 0) {
            newStatus = STATUS_IN_USE;
        } else {
            newStatus = STATUS_IN_STOCK;
        }
        int rows = baseMapper.update(null, Wrappers.<LtLot>lambdaUpdate()
            .set(LtLot::getStatus, newStatus)
            .eq(LtLot::getLotId, lotId)
            .eq(LtLot::getStatus, STATUS_QUARANTINED));
        if (rows == 0) {
            throw new RuntimeException("批次状态已变更,放行失败");
        }
        log.info("批次放行: lotId={}, newStatus={}", lotId, newStatus);
        return true;
    }

    /**
     * 更新批次QC状态(PENDING -> PASS/FAIL)。
     * <p>CAS: .eq(qcStatus, PENDING) + rows==0 抛"状态已变更"。
     * FAIL 自动调用 quarantine 隔离。</p>
     *
     * @param lotId    批次ID
     * @param qcStatus 目标QC状态(PASS/FAIL)
     * @return 更新成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateQcStatus(Long lotId, String qcStatus) {
        if (!QC_STATUS_PASS.equals(qcStatus) && !QC_STATUS_FAIL.equals(qcStatus)) {
            throw new RuntimeException("QC状态仅支持 PASS/FAIL,当前: " + qcStatus);
        }
        LtLot lot = baseMapper.selectById(lotId);
        if (lot == null) {
            throw new RuntimeException("批次不存在: " + lotId);
        }
        if (!QC_STATUS_PENDING.equals(lot.getQcStatus())) {
            throw new RuntimeException("QC状态已确认,不可重复检验: " + lot.getQcStatus());
        }
        int rows = baseMapper.update(null, Wrappers.<LtLot>lambdaUpdate()
            .set(LtLot::getQcStatus, qcStatus)
            .eq(LtLot::getLotId, lotId)
            .eq(LtLot::getQcStatus, QC_STATUS_PENDING));
        if (rows == 0) {
            throw new RuntimeException("QC状态已变更,更新失败");
        }
        // QC 不合格自动隔离
        if (QC_STATUS_FAIL.equals(qcStatus)) {
            return quarantine(lotId, "IQC不合格自动隔离");
        }
        log.info("批次QC放行: lotId={}, qcStatus={}", lotId, qcStatus);
        return true;
    }

    /**
     * 应用IQC检验结果到关联批次(按 iqc_id 查 lot; 回写 qc_status; FAIL 自动隔离)
     * <p>最佳努力: IQC结果已落库后由 QcIqcServiceImpl.inspect() afterCommit 调用;
     * 未关联批次(iqc_id 无对应 lot)返回 false 不报错。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean applyIqcResult(Long iqcId, String inspectResult) {
        if (iqcId == null) {
            return false;
        }
        String qcStatus;
        if ("QUALIFIED".equals(inspectResult)) {
            qcStatus = QC_STATUS_PASS;
        } else if ("UNQUALIFIED".equals(inspectResult)) {
            qcStatus = QC_STATUS_FAIL;
        } else {
            throw new RuntimeException("IQC检验结果必须为 QUALIFIED/UNQUALIFIED: " + inspectResult);
        }
        LtLot lot = baseMapper.selectOne(Wrappers.<LtLot>lambdaQuery().eq(LtLot::getIqcId, iqcId));
        if (lot == null) {
            // IQC 未关联批次, 跳过(全量自动待收货->lot 联动)
            return false;
        }
        // 回写 qc_status + 关联 iqc_id(幂等)
        baseMapper.update(null, Wrappers.<LtLot>lambdaUpdate()
            .set(LtLot::getQcStatus, qcStatus)
            .set(LtLot::getIqcId, iqcId)
            .eq(LtLot::getLotId, lot.getLotId()));
        // FAIL 自动隔离(仅 IN_STOCK/IN_USE 可隔离; 终态则仅置 FAIL, 记录无法隔离)
        if (QC_STATUS_FAIL.equals(qcStatus)) {
            if (STATUS_IN_STOCK.equals(lot.getStatus()) || STATUS_IN_USE.equals(lot.getStatus())) {
                quarantine(lot.getLotId(), "IQC(" + iqcId + ")不合格自动隔离");
            } else {
                log.warn("IQC不合格但批次已终态,仅置FAIL未隔离: lotId={}, status={}",
                    lot.getLotId(), lot.getStatus());
            }
        }
        log.info("IQC回写批次QC状态: iqcId={}, lotId={}, qcStatus={}", iqcId, lot.getLotId(), qcStatus);
        return true;
    }

    /**
     * NCR 触发批次隔离(契约方法: P0-4 nr 模块落地后由 NCR 创建流调用)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean quarantineByNcr(Long lotId, Long ncrId, String reason) {
        String remark = "NCR(" + ncrId + "): " + (reason == null ? "" : reason);
        return quarantine(lotId, remark);
    }

    /**
     * 校验批次是否可用于进站/领料(契约方法: P0-3 进站出站校验调用)
     */
    @Override
    public Map<String, Object> checkLotUsable(Long materialId, Long lotId) {
        Map<String, Object> result = new HashMap<>();
        result.put("usable", false);
        LtLot lot = baseMapper.selectById(lotId);
        if (lot == null) {
            result.put("reason", "批次不存在: " + lotId);
            return result;
        }
        result.put("lotId", lot.getLotId());
        result.put("lotCode", lot.getLotCode());
        if (materialId != null && !materialId.equals(lot.getMaterialId())) {
            result.put("reason", "批次物料与要求物料不符");
            return result;
        }
        if (!STATUS_IN_STOCK.equals(lot.getStatus())) {
            result.put("reason", "批次状态非在库: " + lot.getStatus());
            return result;
        }
        if (!QC_STATUS_PASS.equals(lot.getQcStatus())) {
            result.put("reason", "批次QC未通过: " + lot.getQcStatus());
            return result;
        }
        BigDecimal consumed = lot.getConsumedQty() == null ? BigDecimal.ZERO : lot.getConsumedQty();
        BigDecimal remaining = lot.getQuantity().subtract(consumed);
        result.put("remainingQty", remaining);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            result.put("reason", "批次剩余数量为0");
            return result;
        }
        result.put("usable", true);
        result.put("reason", "可用");
        return result;
    }

    /**
     * FEFO 推荐: 按 expiry_date ASC, produced_date ASC, lot_id ASC 排序
     * 过滤: status=IN_STOCK 且 qc_status=PASS 且 剩余数量>0
     */
    @Override
    public List<LtLotVo> recommendFefo(Long materialId, BigDecimal requiredQty) {
        if (materialId == null) {
            throw new RuntimeException("物料ID不能为空");
        }
        List<LtLotVo> all = baseMapper.selectVoList(Wrappers.<LtLot>lambdaQuery()
            .eq(LtLot::getMaterialId, materialId)
            .eq(LtLot::getStatus, STATUS_IN_STOCK)
            .eq(LtLot::getQcStatus, QC_STATUS_PASS)
            .orderByAsc(LtLot::getExpiryDate)
            .orderByAsc(LtLot::getProducedDate)
            .orderByAsc(LtLot::getLotId));
        all.forEach(this::fillRemainingQty);
        // 过滤剩余数量 > 0
        List<LtLotVo> available = new ArrayList<>();
        for (LtLotVo vo : all) {
            if (vo.getRemainingQty() != null && vo.getRemainingQty().compareTo(BigDecimal.ZERO) > 0) {
                available.add(vo);
            }
        }
        // 若指定需求数量,累加直到满足
        if (requiredQty == null || requiredQty.compareTo(BigDecimal.ZERO) <= 0) {
            return available;
        }
        List<LtLotVo> recommended = new ArrayList<>();
        BigDecimal accumulated = BigDecimal.ZERO;
        for (LtLotVo vo : available) {
            recommended.add(vo);
            accumulated = accumulated.add(vo.getRemainingQty());
            if (accumulated.compareTo(requiredQty) >= 0) {
                break;
            }
        }
        log.info("FEFO推荐: materialId={}, requiredQty={}, 推荐批次数={}", materialId, requiredQty, recommended.size());
        return recommended;
    }

    /**
     * 批次消耗(扣减数量,状态机流转)
     */
    @Lock4j(keys = {"#lotId"})
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean consume(Long lotId, BigDecimal consumedQty) {
        if (consumedQty == null || consumedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("消耗数量必须大于0");
        }
        LtLot lot = baseMapper.selectById(lotId);
        if (lot == null) {
            throw new RuntimeException("批次不存在: " + lotId);
        }
        if (!STATUS_IN_STOCK.equals(lot.getStatus()) && !STATUS_IN_USE.equals(lot.getStatus())) {
            throw new RuntimeException("批次状态不可消耗: " + lot.getStatus());
        }
        BigDecimal newConsumed = lot.getConsumedQty().add(consumedQty);
        BigDecimal remaining = lot.getQuantity().subtract(newConsumed);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("消耗数量超过剩余数量,剩余: "
                + lot.getQuantity().subtract(lot.getConsumedQty()));
        }
        // 状态机流转
        String newStatus;
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            newStatus = STATUS_CONSUMED;
        } else {
            newStatus = STATUS_IN_USE;
        }
        int rows = baseMapper.update(null, Wrappers.<LtLot>lambdaUpdate()
            .set(LtLot::getConsumedQty, newConsumed)
            .set(LtLot::getStatus, newStatus)
            .eq(LtLot::getLotId, lotId)
            .in(LtLot::getStatus, STATUS_IN_STOCK, STATUS_IN_USE));
        if (rows == 0) {
            throw new RuntimeException("批次状态已变更,消耗失败");
        }
        log.info("批次消耗: lotId={}, consumed={}, remaining={}, newStatus={}",
            lotId, consumedQty, remaining, newStatus);
        return true;
    }

    /**
     * 批次追溯链
     */
    @Override
    public Map<String, Object> trace(Long lotId) {
        Map<String, Object> result = new HashMap<>();
        LtLotVo lot = baseMapper.selectVoById(lotId);
        if (lot == null) {
            throw new RuntimeException("批次不存在: " + lotId);
        }
        fillRemainingQty(lot);
        result.put("lot", lot);

        // 工单消耗列表
        List<LtWoConsumptionVo> consumptions = consumptionMapper.selectVoList(
            Wrappers.<com.nexus.mes.lt.domain.LtWoConsumption>lambdaQuery()
                .eq(com.nexus.mes.lt.domain.LtWoConsumption::getLotId, lotId)
                .orderByDesc(com.nexus.mes.lt.domain.LtWoConsumption::getConsumedTime));
        result.put("consumptions", consumptions);

        // SN绑定列表
        List<LtSnBindingVo> bindings = bindingMapper.selectVoList(
            Wrappers.<com.nexus.mes.lt.domain.LtSnBinding>lambdaQuery()
                .eq(com.nexus.mes.lt.domain.LtSnBinding::getLotId, lotId)
                .orderByDesc(com.nexus.mes.lt.domain.LtSnBinding::getBoundTime));
        result.put("bindings", bindings);

        // 关联工单ID去重列表
        List<Long> woIds = consumptions.stream()
            .map(LtWoConsumptionVo::getWoId)
            .distinct()
            .toList();
        result.put("woIds", woIds);

        // 关联SN ID列表
        List<Long> snIds = bindings.stream()
            .map(LtSnBindingVo::getSnId)
            .toList();
        result.put("snIds", snIds);

        return result;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成批次编码: 走系统自动编码规则 MES_LOT_CODE (LOT + yyyyMMdd + 3位日循环流水号)
     * <p>对齐 ERP 全模块 (BdMaterial/PrdWorkOrder 等) 的 autoCodeUtil.genSerialCode 模式:
     * synchronized + REQUIRES_NEW 落 sys_auto_code_result, 流水号日循环归零, uk_lot_code 兜底。
     * 旧实现为本地 count(likeRight)+1, 无流水号持久化、与项目标准不一致, 已废弃。</p>
     */
    private String generateLotCode() {
        return autoCodeUtil.genSerialCode("MES_LOT_CODE", null);
    }

    /**
     * 填充剩余数量
     */
    private void fillRemainingQty(LtLotVo vo) {
        if (vo == null || vo.getQuantity() == null) {
            return;
        }
        BigDecimal consumed = vo.getConsumedQty() == null ? BigDecimal.ZERO : vo.getConsumedQty();
        vo.setRemainingQty(vo.getQuantity().subtract(consumed));
    }

    /**
     * 构建批次查询条件包装器(按 lotCode/status/qcStatus/iqcId/materialId/supplierId/supplierLot 筛选)。
     */
    private LambdaQueryWrapper<LtLot> buildQueryWrapper(LtLotBo bo) {
        return Wrappers.<LtLot>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getLotCode()), LtLot::getLotCode, bo.getLotCode())
            .eq(StringUtils.isNotBlank(bo.getStatus()), LtLot::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getQcStatus()), LtLot::getQcStatus, bo.getQcStatus())
            .eq(bo.getIqcId() != null, LtLot::getIqcId, bo.getIqcId())
            .eq(bo.getMaterialId() != null, LtLot::getMaterialId, bo.getMaterialId())
            .eq(bo.getSupplierId() != null, LtLot::getSupplierId, bo.getSupplierId())
            .like(StringUtils.isNotBlank(bo.getSupplierLot()), LtLot::getSupplierLot, bo.getSupplierLot())
            .orderByDesc(LtLot::getCreateTime);
    }
}

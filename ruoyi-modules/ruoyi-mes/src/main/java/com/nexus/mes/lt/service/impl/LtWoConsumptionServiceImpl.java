package com.nexus.mes.lt.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.lt.domain.LtLot;
import com.nexus.mes.lt.domain.LtSnBinding;
import com.nexus.mes.lt.domain.LtWoConsumption;
import com.nexus.mes.lt.domain.bo.LtSnBindingBo;
import com.nexus.mes.lt.domain.bo.LtWoConsumptionBo;
import com.nexus.mes.lt.domain.bo.LtWoPickBo;
import com.nexus.mes.lt.domain.vo.LtLotVo;
import com.nexus.mes.lt.domain.vo.LtWoConsumptionVo;
import com.nexus.mes.lt.mapper.LtLotMapper;
import com.nexus.mes.lt.mapper.LtSnBindingMapper;
import com.nexus.mes.lt.mapper.LtWoConsumptionMapper;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.lt.service.ILtSnBindingService;
import com.nexus.mes.lt.service.ILtWoConsumptionService;
import com.nexus.mes.pro.domain.ProWorkOrderBomLine;
import com.nexus.mes.pro.mapper.ProWorkOrderBomLineMapper;
import com.nexus.common.satoken.utils.LoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * MES工单批次消耗 Service 业务层实现
 * <p>核心联动: 新增消耗时调用 ILtLotService.consume 扣减批次数量</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("LtWoConsumptionService")
public class LtWoConsumptionServiceImpl implements ILtWoConsumptionService {

    private final LtWoConsumptionMapper baseMapper;
    private final LtLotMapper lotMapper;
    private final ILtLotService lotService;
    private final LtSnBindingMapper snBindingMapper;
    private final ILtSnBindingService snBindingService;
    /** 工单BOM快照(超领校验上界: total_quantity 需求量) */
    private final ProWorkOrderBomLineMapper woBomLineMapper;

    /**
     * 分页查询工单批次消耗列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 工单批次消耗分页列表
     */
    @Override
    public TableDataInfo<LtWoConsumptionVo> selectPageList(LtWoConsumptionBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<LtWoConsumption> lqw = buildQueryWrapper(bo);
        Page<LtWoConsumptionVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID获取消耗记录详情
     *
     * @param consumptionId 消耗记录ID
     * @return 消耗记录详情
     */
    @Override
    public LtWoConsumptionVo selectById(Long consumptionId) {
        return baseMapper.selectVoById(consumptionId);
    }

    /**
     * 新增工单批次消耗(联动扣减批次数量)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(LtWoConsumptionBo bo) {
        // 1. 查批次(获取冗余字段 + 校验存在)
        LtLot lot = lotMapper.selectById(bo.getLotId());
        if (lot == null) {
            throw new RuntimeException("批次不存在: " + bo.getLotId());
        }
        // 2. 批次可用性校验(物料一致性/QC/效期/剩余量; 复用既有 checkLotUsable, MES-LT-02/03/06)
        Map<String, Object> usable = lotService.checkLotUsable(bo.getMaterialId(), bo.getLotId());
        if (usable == null || !Boolean.TRUE.equals(usable.get("usable"))) {
            throw new ServiceException("批次不可领用: "
                + (usable == null ? "批次可用性校验无返回" : usable.get("reason")));
        }
        // 3. 超领校验(按工单BOM快照需求量; 无快照时跳过并记 warn, MES-LT-02)
        Long lotMaterialId = lot.getMaterialId() != null ? lot.getMaterialId() : bo.getMaterialId();
        assertNotOverPick(bo.getWoId(), lotMaterialId, bo.getConsumedQty());
        // 4. 联动扣减批次数量(consume 内部为原子 SQL + 守卫)
        lotService.consume(bo.getLotId(), bo.getConsumedQty());
        // 5. 写消耗记录(冗余字段; materialId 以批次为准, 不信客户端)
        LtWoConsumption record = new LtWoConsumption();
        record.setWoId(bo.getWoId());
        record.setLotId(bo.getLotId());
        record.setLotCode(lot.getLotCode());
        record.setMaterialId(lotMaterialId);
        record.setMaterialCode(lot.getMaterialCode());
        record.setConsumedQty(bo.getConsumedQty());
        record.setConsumedTime(new Date());
        // 操作员: 优先取 BO 传入,未传则取当前登录用户
        record.setOperatorId(bo.getOperatorId() != null ? bo.getOperatorId() : LoginHelper.getUserId());
        record.setRemark(bo.getRemark());
        int rows = baseMapper.insert(record);
        log.info("新增工单批次消耗: woId={}, lotId={}, consumed={}",
            bo.getWoId(), bo.getLotId(), bo.getConsumedQty());
        return rows > 0;
    }

    /**
     * 批量删除消耗记录(不回滚已扣减数量,仅删记录)
     *
     * @param ids 消耗记录ID数组
     * @return 删除成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        // 注意: 仅删除记录,不回滚已扣减数量(已消耗的物料已用于生产,无法回滚)
        int rows = baseMapper.deleteByIds(java.util.Arrays.asList(ids));
        log.info("批量删除工单批次消耗记录: count={}", ids.length);
        return rows > 0;
    }

    /**
     * 按工单ID查询所有批次消耗
     *
     * @param woId 工单ID
     * @return 消耗记录列表(按消耗时间降序)
     */
    @Override
    public List<LtWoConsumptionVo> selectByWoId(Long woId) {
        return baseMapper.selectVoList(Wrappers.<LtWoConsumption>lambdaQuery()
            .eq(LtWoConsumption::getWoId, woId)
            .orderByDesc(LtWoConsumption::getConsumedTime));
    }

    /**
     * 按批次ID查询所有消耗记录(追溯用)
     *
     * @param lotId 批次ID
     * @return 消耗记录列表(按消耗时间降序)
     */
    @Override
    public List<LtWoConsumptionVo> selectByLotId(Long lotId) {
        return baseMapper.selectVoList(Wrappers.<LtWoConsumption>lambdaQuery()
            .eq(LtWoConsumption::getLotId, lotId)
            .orderByDesc(LtWoConsumption::getConsumedTime));
    }

    /**
     * 工单 FEFO 推荐: 委托 ILtLotService.recommendFefo 按到期升序返回可用批次
     */
    @Override
    public List<LtLotVo> recommendForWo(Long woId, Long materialId, BigDecimal requiredQty) {
        log.info("工单FEFO推荐: woId={}, materialId={}, requiredQty={}", woId, materialId, requiredQty);
        return lotService.recommendFefo(materialId, requiredQty);
    }

    /**
     * 工单 FEFO 领料确认: 逐条联动消耗 + 写消耗记录(+可选 SN↔批次绑定)
     * <p>事务性: 任一条失败回滚全部。SN 绑定前先查重避免重复(uk 兜底)。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean confirmPick(LtWoPickBo pickBo) {
        if (pickBo == null || pickBo.getPicks() == null || pickBo.getPicks().isEmpty()) {
            throw new RuntimeException("领料明细不能为空");
        }
        Long woId = pickBo.getWoId();
        Long materialId = pickBo.getMaterialId();
        Long snId = pickBo.getSnId();
        for (LtWoConsumptionBo pick : pickBo.getPicks()) {
            pick.setWoId(woId);
            pick.setMaterialId(materialId);
            // 联动 consume(扣减批次数量+状态机) + 写消耗记录
            insertByBo(pick);
            // 若指定 SN, 写 SN↔批次绑定(组件消费追溯); 先查重避免重复绑定
            if (snId != null) {
                Long exist = snBindingMapper.selectCount(Wrappers.<LtSnBinding>lambdaQuery()
                    .eq(LtSnBinding::getSnId, snId)
                    .eq(LtSnBinding::getMaterialId, materialId));
                if (exist == null || exist == 0) {
                    LtSnBindingBo bindBo = new LtSnBindingBo();
                    bindBo.setSnId(snId);
                    bindBo.setLotId(pick.getLotId());
                    bindBo.setMaterialId(materialId);
                    snBindingService.insertByBo(bindBo);
                }
            }
        }
        log.info("工单FEFO领料确认: woId={}, materialId={}, picks={}, snId={}",
            woId, materialId, pickBo.getPicks().size(), snId);
        return true;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 超领校验: 同工单同物料累计领料量 + 本次 <= 工单BOM快照需求量(pro_work_order_bom_line.total_quantity)。
     * <p>MES-LT-02: 旧实现无任何上限校验, 同一工单可反复领料直到批次耗尽。
     * 无 BOM 快照(手工工单/非 BOM 物料)时跳过并记 warn, 不阻断领料。</p>
     */
    private void assertNotOverPick(Long woId, Long materialId, BigDecimal qty) {
        if (woId == null || materialId == null || qty == null) {
            return;
        }
        ProWorkOrderBomLine bomLine = woBomLineMapper.selectOne(Wrappers.<ProWorkOrderBomLine>lambdaQuery()
            .eq(ProWorkOrderBomLine::getMesWoId, woId)
            .eq(ProWorkOrderBomLine::getChildMaterialId, materialId)
            .orderByDesc(ProWorkOrderBomLine::getWoBomLineId)
            .last("LIMIT 1"));
        if (bomLine == null || bomLine.getTotalQuantity() == null) {
            log.warn("工单无对应BOM快照, 跳过超领校验: woId={}, materialId={}", woId, materialId);
            return;
        }
        List<LtWoConsumption> exists = baseMapper.selectList(Wrappers.<LtWoConsumption>lambdaQuery()
            .eq(LtWoConsumption::getWoId, woId)
            .eq(LtWoConsumption::getMaterialId, materialId));
        BigDecimal consumed = BigDecimal.ZERO;
        if (exists != null) {
            for (LtWoConsumption c : exists) {
                if (c.getConsumedQty() != null) {
                    consumed = consumed.add(c.getConsumedQty());
                }
            }
        }
        if (consumed.add(qty).compareTo(bomLine.getTotalQuantity()) > 0) {
            throw new ServiceException(String.format("超领: 工单需求量 %s, 已领 %s, 本次 %s",
                bomLine.getTotalQuantity().stripTrailingZeros().toPlainString(),
                consumed.stripTrailingZeros().toPlainString(),
                qty.stripTrailingZeros().toPlainString()));
        }
    }

    /**
     * 构建工单批次消耗查询条件包装器(按 woId/lotId/materialId/operatorId 筛选)。
     */
    private LambdaQueryWrapper<LtWoConsumption> buildQueryWrapper(LtWoConsumptionBo bo) {
        return Wrappers.<LtWoConsumption>lambdaQuery()
            .eq(bo.getWoId() != null, LtWoConsumption::getWoId, bo.getWoId())
            .eq(bo.getLotId() != null, LtWoConsumption::getLotId, bo.getLotId())
            .eq(bo.getMaterialId() != null, LtWoConsumption::getMaterialId, bo.getMaterialId())
            .eq(bo.getOperatorId() != null, LtWoConsumption::getOperatorId, bo.getOperatorId())
            .orderByDesc(LtWoConsumption::getConsumedTime);
    }
}

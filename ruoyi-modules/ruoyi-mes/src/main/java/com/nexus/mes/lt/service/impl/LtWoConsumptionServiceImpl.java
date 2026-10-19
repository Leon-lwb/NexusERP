package com.nexus.mes.lt.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.mes.trace.*;
import com.nexus.common.trace.port.MaterialPolicyPort;
import org.springframework.transaction.annotation.Propagation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.lt.domain.LtWoConsumption;
import com.nexus.mes.lt.domain.bo.LtWoConsumptionBo;
import com.nexus.mes.lt.domain.bo.LtWoPickBo;
import com.nexus.mes.lt.domain.vo.LtLotVo;
import com.nexus.mes.lt.domain.vo.LtWoConsumptionVo;
import com.nexus.mes.lt.mapper.LtWoConsumptionMapper;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.lt.service.ILtWoConsumptionService;
import com.nexus.common.satoken.utils.LoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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
    private final ILtLotService lotService;
    private final MesMaterialTraceWriter traceWriter;
    private final MaterialPolicyPort policies;
    private final com.nexus.mes.trace.MesPreparedWorkOrderPolicies preparedPolicies;
    private final MesTraceContext traceContext;

    /**
     * 分页查询工单批次消耗列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 工单批次消耗分页列表
     */
    @Override
    public TableDataInfo<LtWoConsumptionVo> selectPageList(
            LtWoConsumptionBo bo, PageQuery pageQuery) {
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
    @Transactional(propagation = Propagation.NEVER)
    public Boolean insertByBo(LtWoConsumptionBo bo) {
        if (bo == null) {
            throw new ServiceException("消耗请求不能为空");
        }
        traceContext.requireNoTransaction();
        var pick =
                new MesMaterialTraceWriter.Pick(
                        bo.getCommandId(),
                        bo.getWoId(),
                        bo.getMaterialId(),
                        null,
                        bo.getProcessId(),
                        bo.getOperationRef(),
                        List.of(
                                new MesMaterialTraceWriter.PickLine(
                                        bo.getLotId(),
                                        bo.getConsumedQty(),
                                        bo.getSourceAllocationId())),
                        LoginHelper.getUserId());
        var result =
                traceWriter.pick(
                        pick,
                        preparedPolicies.enabled()
                                ? preparedPolicies.componentForWorkOrder(
                                        "MATERIAL_PICK",
                                        bo.getCommandId(),
                                        bo.getWoId(),
                                        bo.getMaterialId())
                                : policies.policy(traceContext.tenant(), bo.getMaterialId()));
        bo.setConsumptionId(result.get(0));
        return true;
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
        throw new ServiceException("实际消耗流水不允许删除，请通过组件冲正或业务纠正保留原凭据");
    }

    /**
     * 按工单ID查询所有批次消耗
     *
     * @param woId 工单ID
     * @return 消耗记录列表(按消耗时间降序)
     */
    @Override
    public List<LtWoConsumptionVo> selectByWoId(Long woId) {
        return baseMapper.selectVoList(
                Wrappers.<LtWoConsumption>lambdaQuery()
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
        return baseMapper.selectVoList(
                Wrappers.<LtWoConsumption>lambdaQuery()
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
    @Transactional(propagation = Propagation.NEVER)
    public Boolean confirmPick(LtWoPickBo bo) {
        if (bo == null || bo.getPicks() == null || bo.getPicks().isEmpty()) {
            throw new ServiceException("领料明细不能为空");
        }
        traceContext.requireNoTransaction();
        var lines =
                bo.getPicks().stream()
                        .map(
                                p ->
                                        new MesMaterialTraceWriter.PickLine(
                                                p.getLotId(),
                                                p.getConsumedQty(),
                                                p.getSourceAllocationId()))
                        .toList();
        var request =
                new MesMaterialTraceWriter.Pick(
                        bo.getCommandId(),
                        bo.getWoId(),
                        bo.getMaterialId(),
                        bo.getSnId(),
                        bo.getProcessId(),
                        bo.getOperationRef(),
                        lines,
                        LoginHelper.getUserId());
        traceWriter.pick(
                request,
                preparedPolicies.enabled()
                        ? preparedPolicies.componentForWorkOrder(
                                "MATERIAL_PICK",
                                bo.getCommandId(),
                                bo.getWoId(),
                                bo.getMaterialId())
                        : policies.policy(traceContext.tenant(), bo.getMaterialId()));
        return true;
    }

    // ==================== 私有辅助方法 ====================

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

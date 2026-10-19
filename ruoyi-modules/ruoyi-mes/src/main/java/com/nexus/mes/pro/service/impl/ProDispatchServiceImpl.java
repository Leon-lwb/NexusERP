package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.ProDispatch;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.bo.ProDispatchBo;
import com.nexus.mes.pro.domain.vo.ProDispatchVo;
import com.nexus.mes.pro.mapper.ProDispatchMapper;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.service.IProDispatchService;
import com.nexus.mes.pro.service.IProFlowCardService;
import com.nexus.mes.pro.service.ProWorkOrderStepProgressService;
import com.nexus.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * MES派工 Service 业务层实现
 * <p>
 * 状态机: PENDING -> IN_PROGRESS -> COMPLETED / CANCELLED
 * 完工聚合: 派工完工 -> 累加good/defect/scrap到工序 + 累加completed/scrap到流转卡
 *          -> 工序下所有派工完工 -> 自动完成工序(checkAndCompleteStep)
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("proDispatchService")
public class ProDispatchServiceImpl implements IProDispatchService {

    private final ProDispatchMapper baseMapper;
    private final ProFlowCardStepMapper stepMapper;
    private final ProWorkOrderStepProgressService progressService;
    private final ProFlowCardMapper flowCardMapper;
    private final IProFlowCardService proFlowCardService;
    private final LockTemplate lockTemplate;
    private final com.nexus.mes.trace.MesPreparedWorkOrderPolicies preparedPolicies;

    @Override
    /**
     * 查询
     * @param bo bo
     * @param pageQuery pageQuery
     * @return 分页结果
     */
    public TableDataInfo<ProDispatchVo> selectPageList(ProDispatchBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ProDispatch> lqw = buildQueryWrapper(bo);
        Page<ProDispatchVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    /**
     * 查询
     * @param dispatchId 派工ID
     * @return 返回视图对象
     */
    public ProDispatchVo selectById(Long dispatchId) {
        return baseMapper.selectVoById(dispatchId);
    }

    @Override
    /**
     * 查询
     * @param stepRecordId 工序RecordId
     * @return 结果列表
     */
    public List<ProDispatchVo> selectByStepRecordId(Long stepRecordId) {
        LambdaQueryWrapper<ProDispatch> lqw = Wrappers.<ProDispatch>lambdaQuery()
            .eq(ProDispatch::getStepRecordId, stepRecordId)
            .orderByAsc(ProDispatch::getCreateTime);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    /**
     * 查询
     * @param operatorId ID
     * @return 结果列表
     */
    public List<ProDispatchVo> selectByOperatorId(Long operatorId) {
        LambdaQueryWrapper<ProDispatch> lqw = Wrappers.<ProDispatch>lambdaQuery()
            .eq(ProDispatch::getOperatorId, operatorId)
            .orderByDesc(ProDispatch::getCreateTime);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 创建派工单(从流转卡工序)
     */
    @Lock4j(keys = {"#stepRecordId"})
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProDispatchVo createDispatch(Long cardId, Long stepRecordId, Long operatorId,
                                         String operatorName, BigDecimal quantity) {
        // 1. 查流转卡工序记录(校验存在)
        ProFlowCardStep step = stepMapper.selectById(stepRecordId);
        if (step == null) {
            throw new RuntimeException("流转卡工序记录不存在: " + stepRecordId);
        }
        if (!step.getCardId().equals(cardId)) {
            throw new RuntimeException("工序记录与流转卡不匹配");
        }

        // 2. 校验工序状态(仅PENDING或IN_PROGRESS可派工)
        String stepStatus = step.getStepStatus();
        if (!IProFlowCardService.STEP_PENDING.equals(stepStatus)
            && !IProFlowCardService.STEP_IN_PROGRESS.equals(stepStatus)) {
            throw new RuntimeException("工序状态为" + stepStatus + ", 仅待开始或进行中可派工");
        }

        // 3. 校验防超派: 已有派工planned_qty之和 + 本次 <= 工序planned_qty
        List<ProDispatch> existing = baseMapper.selectList(Wrappers.<ProDispatch>lambdaQuery()
            .eq(ProDispatch::getStepRecordId, stepRecordId)
            .ne(ProDispatch::getDispatchStatus, STATUS_CANCELLED));
        BigDecimal existingQty = existing.stream()
            .map(ProDispatch::getPlannedQty)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (existingQty.add(quantity).compareTo(step.getPlannedQty()) > 0) {
            throw new RuntimeException("派工数量超过工序计划数量: 已派" + existingQty + " + 本次" + quantity + " > " + step.getPlannedQty());
        }

        // 4. 查流转卡(用于冗余字段)
        ProFlowCard card = flowCardMapper.selectById(cardId);
        if (card == null) {
            throw new RuntimeException("流转卡不存在: " + cardId);
        }

        // 5. 创建派工单(PENDING), 冗余关联字段
        ProDispatch dispatch = new ProDispatch();
        dispatch.setDispatchNo(generateDispatchNo(card.getCardNo()));
        dispatch.setMesWoId(step.getMesWoId());
        dispatch.setWoNo(card.getWoNo());
        dispatch.setCardId(cardId);
        dispatch.setCardNo(card.getCardNo());
        dispatch.setStepRecordId(stepRecordId);
        dispatch.setSequenceNum(step.getSequenceNum());
        dispatch.setProcessId(step.getProcessId());
        dispatch.setProcessCode(step.getProcessCode());
        dispatch.setProcessName(step.getProcessName());
        dispatch.setWorkcenterId(step.getWorkcenterId());
        dispatch.setWorkcenterCode(step.getWorkcenterCode());
        dispatch.setOperatorId(operatorId);
        dispatch.setOperatorName(operatorName);
        dispatch.setPlannedQty(quantity);
        dispatch.setCompletedQty(BigDecimal.ZERO);
        dispatch.setGoodQty(BigDecimal.ZERO);
        dispatch.setDefectQty(BigDecimal.ZERO);
        dispatch.setScrapQty(BigDecimal.ZERO);
        dispatch.setDispatchStatus(STATUS_PENDING);
        baseMapper.insert(dispatch);

        log.info("派工单创建成功: dispatchNo={}, cardId={}, stepRecordId={}, qty={}",
            dispatch.getDispatchNo(), cardId, stepRecordId, quantity);
        return baseMapper.selectVoById(dispatch.getDispatchId());
    }

    /**
     * 开工派工(PENDING -> IN_PROGRESS), 同步开工工序
     */
    @Lock4j(keys = {"#dispatchId"})
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startDispatch(Long dispatchId) {
        ProDispatch reference = baseMapper.selectById(dispatchId);
        if (reference == null) throw new ServiceException("派工单不存在");
        progressService.lockForCard(reference.getCardId());
        ProDispatch dispatch = baseMapper.selectOne(Wrappers.<ProDispatch>lambdaQuery()
            .eq(ProDispatch::getDispatchId, dispatchId).last("FOR UPDATE"));
        if (dispatch != null && !java.util.Objects.equals(reference.getCardId(), dispatch.getCardId()))
            throw new ServiceException("派工锁定后的父卡发生变化");
        if (dispatch == null) {
            throw new RuntimeException("派工单不存在: " + dispatchId);
        }

        // 1. CAS派工 PENDING -> IN_PROGRESS
        int rows = baseMapper.update(null, Wrappers.<ProDispatch>lambdaUpdate()
            .set(ProDispatch::getDispatchStatus, STATUS_IN_PROGRESS)
            .set(ProDispatch::getActualStartTime, new Date())
            .eq(ProDispatch::getDispatchId, dispatchId)
            .eq(ProDispatch::getDispatchStatus, STATUS_PENDING));
        if (rows == 0) {
            throw new RuntimeException("派工状态已变更, 仅待开始可开工");
        }

        Long cardId = dispatch.getCardId();
        Long stepRecordId = dispatch.getStepRecordId();
        Long operatorId = dispatch.getOperatorId();

        // 2. 查工序当前状态
        ProFlowCardStep step = stepMapper.selectOne(Wrappers.<ProFlowCardStep>lambdaQuery().eq(ProFlowCardStep::getStepRecordId, stepRecordId).last("FOR UPDATE"));
        if (step == null) {
            throw new RuntimeException("工序记录不存在: " + stepRecordId);
        }

        // 3. 若工序为PENDING, 同步开工工序(会触发流转卡/工单转IN_PROGRESS)
        if (IProFlowCardService.STEP_PENDING.equals(step.getStepStatus())) {
            proFlowCardService.startStep(cardId, stepRecordId, operatorId);
        }

        // 4. 补充设置工序的operatorName(现有startStep只设operatorId不设name)
        if (operatorId != null && dispatch.getOperatorName() != null) {
            stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
                .set(ProFlowCardStep::getOperatorName, dispatch.getOperatorName())
                .eq(ProFlowCardStep::getStepRecordId, stepRecordId));
        }

        progressService.rebuildForCard(cardId);
        log.info("派工开工成功: dispatchId={}, stepRecordId={}", dispatchId, stepRecordId);
    }

    /**
     * 完工派工(IN_PROGRESS -> COMPLETED), 累加数量到工序+流转卡, 自动完成工序
     */
    /**
     * 完工派工(IN_PROGRESS -> COMPLETED), 累加数量到工序+流转卡, 自动完成工序
     * <p>入口在事务中读取引用并先取工单父行锁，再以「卡锁 + stepRecordId 锁」执行内部方法。
     * 锁必须覆盖整个事务——同工序两张派工单并发完工时, 若锁释放在事务提交前, 后者读不到前者未提交的
     * 状态变更, 会导致 activeCount 判定竞态、工序永不自动完成(M1); 卡锁同时消除卡完工聚合判定的竞态(MES-PRO-02)。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeDispatch(Long dispatchId, BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty) {
        ProDispatch dispatch = baseMapper.selectById(dispatchId);
        if (dispatch == null) {
            throw new RuntimeException("派工单不存在: " + dispatchId);
        }
        preparedPolicies.prepareCard(dispatch.getCardId());
        progressService.lockForCard(dispatch.getCardId());
        // 锁顺序固定为工单数据库父锁 -> 卡锁 -> 步锁，不能持卡锁再等待父锁。
        // 卡锁必须覆盖 doCompleteDispatch 的整个事务(含卡完工聚合判定): 若卡锁在事务提交前释放,
        // "工序是否全完成"的判定读不到并发事务未提交的完工记录 → 工序全完成而卡不完工(MES-PRO-02)。
        LockInfo cardLock = lockTemplate.lock("mes:flowcard:" + dispatch.getCardId(), 30000L, 5000L);
        if (cardLock == null) {
            throw new RuntimeException("该流转卡有完工处理进行中, 请稍后重试");
        }
        try {
            LockInfo stepLock = lockTemplate.lock("mes:dispatch:step:" + dispatch.getStepRecordId(), 30000L, 5000L);
            if (stepLock == null) {
                throw new RuntimeException("该工序有完工处理进行中, 请稍后重试");
            }
            try {
                // 经 AOP 代理调用以使事务生效(同类直调绕过代理)
                SpringUtils.getAopProxy(this).doCompleteDispatch(dispatchId, goodQty, defectQty, scrapQty);
            } finally {
                lockTemplate.releaseLock(stepLock);
            }
        } finally {
            lockTemplate.releaseLock(cardLock);
        }
    }

    /**
     * 完工派工执行(带事务)。仅供 {@link #completeDispatch} 经 AOP 代理调用, 步骤级锁由入口持有。
     */
    @Transactional(rollbackFor = Exception.class)
    public void doCompleteDispatch(Long dispatchId, BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty) {
        ProDispatch reference = baseMapper.selectById(dispatchId);
        if (reference == null) throw new ServiceException("派工单不存在");
        preparedPolicies.prepareCard(reference.getCardId());
        progressService.lockForCard(reference.getCardId());
        ProDispatch dispatch = baseMapper.selectOne(Wrappers.<ProDispatch>lambdaQuery()
            .eq(ProDispatch::getDispatchId, dispatchId).last("FOR UPDATE"));
        if (dispatch != null && !java.util.Objects.equals(reference.getCardId(), dispatch.getCardId()))
            throw new ServiceException("派工锁定后的父卡发生变化");
        if (dispatch == null) {
            throw new RuntimeException("派工单不存在: " + dispatchId);
        }
        // 数量校验：非负且合计不超过派工计划数量，防止负数/超量污染工序与工单完工数量
        validateReportQuantities(goodQty, defectQty, scrapQty, dispatch.getPlannedQty());

        // 1. CAS派工 IN_PROGRESS -> COMPLETED
        int rows = baseMapper.update(null, Wrappers.<ProDispatch>lambdaUpdate()
            .set(ProDispatch::getDispatchStatus, STATUS_COMPLETED)
            .set(ProDispatch::getGoodQty, goodQty)
            .set(ProDispatch::getDefectQty, defectQty)
            .set(ProDispatch::getScrapQty, scrapQty)
            .set(ProDispatch::getCompletedQty, goodQty)
            .set(ProDispatch::getActualEndTime, new Date())
            .eq(ProDispatch::getDispatchId, dispatchId)
            .eq(ProDispatch::getDispatchStatus, STATUS_IN_PROGRESS));
        if (rows == 0) {
            throw new RuntimeException("派工状态已变更, 仅进行中可完工");
        }

        Long cardId = dispatch.getCardId();
        Long stepRecordId = dispatch.getStepRecordId();

        // 2. 累加到工序(增量, 原子操作)
        stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
            .setSql("good_qty = good_qty + {0}", goodQty)
            .setSql("defect_qty = defect_qty + {0}", defectQty)
            .setSql("scrap_qty = scrap_qty + {0}", scrapQty)
            .eq(ProFlowCardStep::getStepRecordId, stepRecordId));

        // 3. 累加到流转卡(增量, 原子操作)
        flowCardMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
            .setSql("completed_qty = completed_qty + {0}", goodQty)
            .setSql("scrap_qty = scrap_qty + {0}", scrapQty)
            .eq(ProFlowCard::getCardId, cardId));

        // 4. 检查工序下所有派工是否均完成 -> 自动完成工序(锁内执行, 无并发判定竞态)
        Long activeCount = baseMapper.selectCount(Wrappers.<ProDispatch>lambdaQuery()
            .eq(ProDispatch::getStepRecordId, stepRecordId)
            .ne(ProDispatch::getDispatchStatus, STATUS_COMPLETED)
            .ne(ProDispatch::getDispatchStatus, STATUS_CANCELLED).last("FOR UPDATE"));
        if (activeCount == 0) {
            // completeDispatch 已持有 mes:flowcard:{cardId} 卡锁且覆盖本事务提交, 故走不取锁实现(MES-PRO-02)
            proFlowCardService.checkAndCompleteStepUnderLock(cardId, stepRecordId);
        }

        progressService.rebuildForCard(cardId);
        log.info("派工完工成功: dispatchId={}, goodQty={}, defectQty={}, scrapQty={}",
            dispatchId, goodQty, defectQty, scrapQty);
    }

    /**
     * 取消派工(仅PENDING可取消)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelDispatch(Long dispatchId) {
        int rows = baseMapper.update(null, Wrappers.<ProDispatch>lambdaUpdate()
            .set(ProDispatch::getDispatchStatus, STATUS_CANCELLED)
            .eq(ProDispatch::getDispatchId, dispatchId)
            .eq(ProDispatch::getDispatchStatus, STATUS_PENDING));
        if (rows == 0) {
            throw new RuntimeException("派工状态已变更, 仅待开始可取消");
        }
        log.info("派工取消成功: dispatchId={}", dispatchId);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成派工单编号: DIS + 流转卡编号 + 时间戳后4位
     */
    private String generateDispatchNo(String cardNo) {
        long suffix = System.currentTimeMillis() % 10000L;
        return "DIS-" + cardNo + "-" + String.format("%04d", suffix);
    }

    /**
     * 校验报工数量：非负且三者合计必须等于派工计划数量
     * <p>派工为"一次报工即完工"模型(good_qty/completed_qty 覆写非累加, 状态直接 COMPLETED),
     * 故报工时良品+不良+报废须全数分流等于计划数量, 不允许少报(少报会导致卡/工单 completed_qty 与
     * planned_qty 脱节, 卡已COMPLETED但完工数不足, 污染工单完工判定)。</p>
     */
    private void validateReportQuantities(BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty, BigDecimal plannedQty) {
        if (goodQty == null || defectQty == null || scrapQty == null) {
            throw new RuntimeException("报工数量不能为空");
        }
        if (goodQty.compareTo(BigDecimal.ZERO) < 0
            || defectQty.compareTo(BigDecimal.ZERO) < 0
            || scrapQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("报工数量不能为负数");
        }
        BigDecimal total = goodQty.add(defectQty).add(scrapQty);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("报工数量合计必须大于0");
        }
        if (plannedQty != null && total.compareTo(plannedQty) != 0) {
            throw new RuntimeException(String.format(
                "报工数量合计必须等于派工计划数量 %s(良品+不良+报废全数分流), 当前合计 %s",
                plannedQty.stripTrailingZeros().toPlainString(),
                total.stripTrailingZeros().toPlainString()));
        }
    }

    private LambdaQueryWrapper<ProDispatch> buildQueryWrapper(ProDispatchBo bo) {
        return Wrappers.<ProDispatch>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getDispatchNo()), ProDispatch::getDispatchNo, bo.getDispatchNo())
            .eq(bo.getMesWoId() != null, ProDispatch::getMesWoId, bo.getMesWoId())
            .eq(bo.getCardId() != null, ProDispatch::getCardId, bo.getCardId())
            .eq(bo.getOperatorId() != null, ProDispatch::getOperatorId, bo.getOperatorId())
            .eq(bo.getWorkcenterId() != null, ProDispatch::getWorkcenterId, bo.getWorkcenterId())
            .eq(StringUtils.isNotBlank(bo.getDispatchStatus()), ProDispatch::getDispatchStatus, bo.getDispatchStatus())
            .orderByDesc(ProDispatch::getCreateTime);
    }
}

package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import com.nexus.mes.pro.domain.bo.ProFlowCardBo;
import com.nexus.mes.pro.domain.vo.ProFlowCardStepVo;
import com.nexus.mes.pro.domain.vo.ProFlowCardVo;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderStepMapper;
import com.nexus.mes.pro.service.IProFlowCardService;
import com.nexus.mes.pro.service.IProWorkOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * MES流转卡 Service 业务层实现
 * <p>
 * 状态机: CREATED -> RELEASED -> IN_PROGRESS -> COMPLETED -> CLOSED
 * 完工聚合: 流转卡完工 -> 更新工单completed_qty -> 工单所有流转卡完工 -> 触发工单完工
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("proFlowCardService")
public class ProFlowCardServiceImpl implements IProFlowCardService {

    private final ProFlowCardMapper baseMapper;
    private final ProFlowCardStepMapper stepMapper;
    private final ProWorkOrderMapper workOrderMapper;
    private final ProWorkOrderStepMapper workOrderStepMapper;
    private final IProWorkOrderService proWorkOrderService;

    @Override
    public TableDataInfo<ProFlowCardVo> selectPageList(ProFlowCardBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ProFlowCard> lqw = buildQueryWrapper(bo);
        Page<ProFlowCardVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public ProFlowCardVo selectById(Long cardId) {
        ProFlowCardVo vo = baseMapper.selectVoById(cardId);
        if (vo != null) {
            vo.setSteps(selectStepsByCardId(cardId));
        }
        return vo;
    }

    @Override
    public List<ProFlowCardVo> selectByWoId(Long mesWoId) {
        LambdaQueryWrapper<ProFlowCard> lqw = Wrappers.<ProFlowCard>lambdaQuery()
            .eq(ProFlowCard::getMesWoId, mesWoId)
            .orderByDesc(ProFlowCard::getCreateTime);
        return baseMapper.selectVoList(lqw);
    }

    private List<ProFlowCardStepVo> selectStepsByCardId(Long cardId) {
        LambdaQueryWrapper<ProFlowCardStep> lqw = Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, cardId)
            .orderByAsc(ProFlowCardStep::getSequenceNum);
        return stepMapper.selectVoList(lqw);
    }

    /**
     * 创建流转卡(从MES工单)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProFlowCardVo createFlowCard(Long mesWoId, BigDecimal quantity) {
        log.info("创建流转卡: mesWoId={}, quantity={}", mesWoId, quantity);

        // 1. 校验工单状态
        ProWorkOrder workOrder = workOrderMapper.selectById(mesWoId);
        if (workOrder == null) {
            throw new RuntimeException("MES工单不存在: " + mesWoId);
        }
        if (!IProWorkOrderService.STATUS_RELEASED.equals(workOrder.getWoStatus())
            && !IProWorkOrderService.STATUS_IN_PROGRESS.equals(workOrder.getWoStatus())) {
            throw new RuntimeException("仅已下达或生产中的工单可创建流转卡,当前状态:" + workOrder.getWoStatus());
        }

        // 2. 创建流转卡主记录
        ProFlowCard flowCard = new ProFlowCard();
        flowCard.setCardNo(generateCardNo(workOrder));
        flowCard.setMesWoId(mesWoId);
        flowCard.setWoNo(workOrder.getWoNo());
        flowCard.setMaterialId(workOrder.getMaterialId());
        flowCard.setMaterialCode(workOrder.getMaterialCode());
        flowCard.setMaterialName(workOrder.getMaterialName());
        flowCard.setPlannedQty(quantity);
        flowCard.setCompletedQty(BigDecimal.ZERO);
        flowCard.setScrapQty(BigDecimal.ZERO);
        flowCard.setCardStatus(STATUS_CREATED);
        baseMapper.insert(flowCard);
        flowCard.setBarcode("FC" + String.format("%016d", flowCard.getCardId()));
        baseMapper.updateById(flowCard);
        log.info("流转卡创建成功: cardId={}, cardNo={}", flowCard.getCardId(), flowCard.getCardNo());

        // 3. 查工单工序步骤，自动生成流转卡工序记录
        generateCardStepsFromWorkOrder(flowCard, mesWoId);

        return selectById(flowCard.getCardId());
    }

    /**
     * 下达流转卡(CREATED -> RELEASED)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseCard(Long cardId) {
        int rows = baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
            .set(ProFlowCard::getCardStatus, STATUS_RELEASED)
            .eq(ProFlowCard::getCardId, cardId)
            .eq(ProFlowCard::getCardStatus, STATUS_CREATED));
        if (rows == 0) {
            throw new RuntimeException("流转卡状态已变更或不存在，仅新建状态可下达");
        }
        log.info("流转卡下达成功: cardId={}", cardId);
    }

    /**
     * 开始工序
     */
    @Lock4j(keys = {"#cardId"})
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void startStep(Long cardId, Long stepRecordId, Long operatorId) {
        log.info("开始工序: cardId={}, stepRecordId={}, operatorId={}", cardId, stepRecordId, operatorId);

        // 1. CAS更新工序状态 PENDING -> IN_PROGRESS
        int stepRows = stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
            .set(ProFlowCardStep::getStepStatus, STEP_IN_PROGRESS)
            .set(ProFlowCardStep::getOperatorId, operatorId)
            .set(ProFlowCardStep::getActualStartTime, new Date())
            .eq(ProFlowCardStep::getStepRecordId, stepRecordId)
            .eq(ProFlowCardStep::getCardId, cardId)
            .eq(ProFlowCardStep::getStepStatus, STEP_PENDING));
        if (stepRows == 0) {
            throw new RuntimeException("工序状态已变更或不存在，仅待开始状态可开工");
        }

        // 2. 若流转卡为RELEASED，更新为IN_PROGRESS
        baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
            .set(ProFlowCard::getCardStatus, STATUS_IN_PROGRESS)
            .set(ProFlowCard::getCurrentStepId, stepRecordId)
            .eq(ProFlowCard::getCardId, cardId)
            .eq(ProFlowCard::getCardStatus, STATUS_RELEASED));

        // 3. 工单状态若为RELEASED，更新为IN_PROGRESS
        ProFlowCard card = baseMapper.selectById(cardId);
        if (card != null) {
            workOrderMapper.update(null, Wrappers.<ProWorkOrder>lambdaUpdate()
                .set(ProWorkOrder::getWoStatus, IProWorkOrderService.STATUS_IN_PROGRESS)
                .set(ProWorkOrder::getActualStartDate, new Date())
                .eq(ProWorkOrder::getMesWoId, card.getMesWoId())
                .eq(ProWorkOrder::getWoStatus, IProWorkOrderService.STATUS_RELEASED));
        }
    }

    /**
     * 完成工序
     */
    @Lock4j(keys = {"#cardId"})
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void completeStep(Long cardId, Long stepRecordId,
                             BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty) {
        log.info("完成工序: cardId={}, stepRecordId={}, good={}, defect={}, scrap={}",
            cardId, stepRecordId, goodQty, defectQty, scrapQty);

        // 1. CAS更新工序状态 IN_PROGRESS -> COMPLETED
        int stepRows = stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
            .set(ProFlowCardStep::getStepStatus, STEP_COMPLETED)
            .set(ProFlowCardStep::getGoodQty, goodQty)
            .set(ProFlowCardStep::getDefectQty, defectQty)
            .set(ProFlowCardStep::getScrapQty, scrapQty)
            .set(ProFlowCardStep::getActualEndTime, new Date())
            .eq(ProFlowCardStep::getStepRecordId, stepRecordId)
            .eq(ProFlowCardStep::getCardId, cardId)
            .eq(ProFlowCardStep::getStepStatus, STEP_IN_PROGRESS));
        if (stepRows == 0) {
            throw new RuntimeException("工序状态已变更或不存在，仅进行中状态可完工");
        }

        // 2. 更新流转卡数量: completed_qty 用当前工序goodQty覆写（串行流程，每道工序处理同一批工件）
        //    scrap_qty 累加（各工序独立报废，需累计）
        baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
            .setSql("completed_qty = {0}", goodQty)
            .setSql("scrap_qty = scrap_qty + {0}", scrapQty)
            .eq(ProFlowCard::getCardId, cardId));

        // 3. 检查是否所有工序完成
        Long pendingCount = stepMapper.selectCount(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, cardId)
            .ne(ProFlowCardStep::getStepStatus, STEP_COMPLETED)
            .ne(ProFlowCardStep::getStepStatus, STEP_SKIPPED));
        if (pendingCount == 0) {
            log.info("流转卡所有工序完成，触发completeCard: cardId={}", cardId);
            completeCard(cardId);
        }
    }

    /**
     * 完成流转卡
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeCard(Long cardId) {
        // 1. CAS更新流转卡状态 -> COMPLETED
        int rows = baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
            .set(ProFlowCard::getCardStatus, STATUS_COMPLETED)
            .eq(ProFlowCard::getCardId, cardId)
            .in(ProFlowCard::getCardStatus, STATUS_IN_PROGRESS, STATUS_RELEASED));
        if (rows == 0) {
            log.warn("流转卡状态不满足完工条件，跳过: cardId={}", cardId);
            return;
        }

        ProFlowCard card = baseMapper.selectById(cardId);
        if (card == null) {
            return;
        }

        // 2. 从工序记录重算流转卡数量（确保派工路径和直接路径都正确）
        //    completed_qty = 最后工序的 good_qty（串行流程产出）
        //    scrap_qty = 所有工序 scrap_qty 之和（各工序独立报废）
        List<ProFlowCardStep> allSteps = stepMapper.selectList(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, cardId)
            .orderByDesc(ProFlowCardStep::getSequenceNum));
        BigDecimal correctCompletedQty = BigDecimal.ZERO;
        BigDecimal correctScrapQty = BigDecimal.ZERO;
        for (int i = 0; i < allSteps.size(); i++) {
            ProFlowCardStep s = allSteps.get(i);
            BigDecimal stepGood = s.getGoodQty() != null ? s.getGoodQty() : BigDecimal.ZERO;
            BigDecimal stepScrap = s.getScrapQty() != null ? s.getScrapQty() : BigDecimal.ZERO;
            if (i == 0) {
                // 最后工序（sequenceNum 最大）的 good_qty 就是串行产出
                correctCompletedQty = stepGood;
            }
            correctScrapQty = correctScrapQty.add(stepScrap);
        }
        baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
            .set(ProFlowCard::getCompletedQty, correctCompletedQty)
            .set(ProFlowCard::getScrapQty, correctScrapQty)
            .eq(ProFlowCard::getCardId, cardId));
        card.setCompletedQty(correctCompletedQty);
        card.setScrapQty(correctScrapQty);
        log.info("流转卡完工: cardId={}, completedQty={}, scrapQty={}", cardId, correctCompletedQty, correctScrapQty);

        // 3. 聚合到工单 completed_qty（card 的 completedQty 已在步骤2中重算为正确值）
        workOrderMapper.update(null, Wrappers.<ProWorkOrder>lambdaUpdate()
            .setSql("completed_qty = completed_qty + {0}", correctCompletedQty)
            .setSql("scrap_qty = scrap_qty + {0}", correctScrapQty)
            .eq(ProWorkOrder::getMesWoId, card.getMesWoId()));

        // 4. 检查工单下是否还有非COMPLETED/CLOSED流转卡
        Long activeCardCount = baseMapper.selectCount(Wrappers.<ProFlowCard>lambdaQuery()
            .eq(ProFlowCard::getMesWoId, card.getMesWoId())
            .notIn(ProFlowCard::getCardStatus, STATUS_COMPLETED, STATUS_CLOSED));
        if (activeCardCount == 0) {
            log.info("工单所有流转卡完工，触发工单完工: mesWoId={}", card.getMesWoId());
            proWorkOrderService.completeWo(card.getMesWoId());
        }
    }

    /**
     * 关闭流转卡(COMPLETED -> CLOSED)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeCard(Long cardId) {
        int rows = baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
            .set(ProFlowCard::getCardStatus, STATUS_CLOSED)
            .eq(ProFlowCard::getCardId, cardId)
            .eq(ProFlowCard::getCardStatus, STATUS_COMPLETED));
        if (rows == 0) {
            throw new RuntimeException("仅已完工状态的流转卡可关闭");
        }
        log.info("流转卡关闭: cardId={}", cardId);
    }

    /**
     * 按条码查询流转卡(含工序)
     */
    @Override
    public ProFlowCardVo selectByBarcode(String barcode) {
        ProFlowCardVo vo = baseMapper.selectVoOne(Wrappers.<ProFlowCard>lambdaQuery()
            .eq(ProFlowCard::getBarcode, barcode));
        if (vo == null) {
            throw new RuntimeException("条码对应的流转卡不存在: " + barcode);
        }
        vo.setSteps(selectStepsByCardId(vo.getCardId()));
        return vo;
    }

    /**
     * 检查并完成工序(派工完工聚合后调用)
     * CAS工序→COMPLETED + 检查所有工序完成→completeCard
     */
    @Lock4j(keys = {"#cardId"})
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void checkAndCompleteStep(Long cardId, Long stepRecordId) {
        // CAS 工序 IN_PROGRESS -> COMPLETED
        int rows = stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
            .set(ProFlowCardStep::getStepStatus, STEP_COMPLETED)
            .set(ProFlowCardStep::getActualEndTime, new Date())
            .eq(ProFlowCardStep::getStepRecordId, stepRecordId)
            .eq(ProFlowCardStep::getCardId, cardId)
            .eq(ProFlowCardStep::getStepStatus, STEP_IN_PROGRESS));
        if (rows == 0) {
            return;
        }
        // 检查所有工序完成 -> completeCard
        Long pendingCount = stepMapper.selectCount(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, cardId)
            .ne(ProFlowCardStep::getStepStatus, STEP_COMPLETED)
            .ne(ProFlowCardStep::getStepStatus, STEP_SKIPPED));
        if (pendingCount == 0) {
            completeCard(cardId);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据工单工序步骤生成流转卡工序记录
     */
    private void generateCardStepsFromWorkOrder(ProFlowCard flowCard, Long mesWoId) {
        LambdaQueryWrapper<ProWorkOrderStep> lqw = Wrappers.<ProWorkOrderStep>lambdaQuery()
            .eq(ProWorkOrderStep::getMesWoId, mesWoId)
            .orderByAsc(ProWorkOrderStep::getSequenceNum);
        List<ProWorkOrderStep> woSteps = workOrderStepMapper.selectList(lqw);

        if (woSteps.isEmpty()) {
            log.warn("工单无工序步骤，流转卡不生成工序记录: mesWoId={}", mesWoId);
            return;
        }

        List<ProFlowCardStep> steps = new ArrayList<>();
        for (ProWorkOrderStep woStep : woSteps) {
            ProFlowCardStep step = new ProFlowCardStep();
            step.setCardId(flowCard.getCardId());
            step.setCardNo(flowCard.getCardNo());
            step.setMesWoId(mesWoId);
            step.setWoStepId(woStep.getStepId());
            step.setSequenceNum(woStep.getSequenceNum());
            step.setProcessId(woStep.getProcessId());
            step.setProcessCode(woStep.getProcessCode());
            step.setProcessName(woStep.getProcessName());
            step.setWorkcenterId(woStep.getWorkcenterId());
            step.setWorkcenterCode(woStep.getWorkcenterCode());
            step.setIsKeyProcess(woStep.getIsKeyProcess());
            step.setPlannedQty(flowCard.getPlannedQty());
            step.setGoodQty(BigDecimal.ZERO);
            step.setDefectQty(BigDecimal.ZERO);
            step.setScrapQty(BigDecimal.ZERO);
            step.setStepStatus(STEP_PENDING);
            steps.add(step);
        }

        for (ProFlowCardStep step : steps) {
            stepMapper.insert(step);
        }
        log.info("流转卡工序记录生成完成: cardId={}, stepCount={}", flowCard.getCardId(), steps.size());
    }

    /**
     * 生成流转卡编号: FC + 工单编号 + 时间戳后4位
     * 使用时间戳避免并发竞争导致的编号重复
     */
    private String generateCardNo(ProWorkOrder workOrder) {
        long suffix = System.currentTimeMillis() % 10000L;
        return "FC-" + workOrder.getWoNo() + "-" + String.format("%04d", suffix);
    }

    private LambdaQueryWrapper<ProFlowCard> buildQueryWrapper(ProFlowCardBo bo) {
        return Wrappers.<ProFlowCard>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getCardNo()), ProFlowCard::getCardNo, bo.getCardNo())
            .eq(bo.getMesWoId() != null, ProFlowCard::getMesWoId, bo.getMesWoId())
            .eq(StringUtils.isNotBlank(bo.getCardStatus()), ProFlowCard::getCardStatus, bo.getCardStatus())
            .orderByDesc(ProFlowCard::getCreateTime);
    }
}

package com.nexus.mes.pro.service;

import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderStep;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nexus.mes.pro.service.IProFlowCardService.STATUS_CLOSED;
import static com.nexus.mes.pro.service.IProFlowCardService.STATUS_COMPLETED;
import static com.nexus.mes.pro.service.IProFlowCardService.STATUS_CREATED;
import static com.nexus.mes.pro.service.IProFlowCardService.STATUS_IN_PROGRESS;
import static com.nexus.mes.pro.service.IProFlowCardService.STATUS_RELEASED;
import static com.nexus.mes.pro.service.IProFlowCardService.STEP_COMPLETED;
import static com.nexus.mes.pro.service.IProFlowCardService.STEP_IN_PROGRESS;
import static com.nexus.mes.pro.service.IProFlowCardService.STEP_PENDING;
import static com.nexus.mes.pro.service.IProFlowCardService.STEP_SKIPPED;

/**
 * 根据完整、可信的流转卡快照计算工单步骤进度，不访问外部资源或修改输入对象。
 * <p>调用方负责提前过滤逻辑删除记录，以及锁、事务和投影持久化。</p>
 */
public final class WorkOrderStepProgressCalculator {

    private static final Set<String> CARD_STATUSES = Set.of(
        STATUS_CREATED, STATUS_RELEASED, STATUS_IN_PROGRESS, STATUS_COMPLETED, STATUS_CLOSED);
    private static final Set<String> STEP_STATUSES = Set.of(
        STEP_PENDING, STEP_IN_PROGRESS, STEP_COMPLETED, STEP_SKIPPED);
    private static final Set<String> UNSTARTED_ORDER_STATUSES = Set.of(
        IProWorkOrderService.STATUS_CREATED, IProWorkOrderService.STATUS_APPROVING,
        IProWorkOrderService.STATUS_RELEASED, IProWorkOrderService.STATUS_CANCELLED);

    private WorkOrderStepProgressCalculator() {
    }

    /**
     * 按工单步骤顺序返回只读投影，来源不完整或不可信时拒绝计算。
     * <p>每张卡必须唯一映射工单的全部步骤；已完工归档的卡仍参与计算。</p>
     *
     * @param wo 工单快照
     * @param woSteps 工单全部有效步骤
     * @param cards 工单全部有效流转卡
     * @param cardSteps 上述流转卡的全部有效工序记录
     * @return 与输入工单步骤同序的投影
     * @throws IllegalArgumentException 输入缺失、引用错误、数量非法或缺少执行来源
     */
    public static List<StepProjection> calculate(ProWorkOrder wo, List<ProWorkOrderStep> woSteps,
                                                  List<ProFlowCard> cards, List<ProFlowCardStep> cardSteps) {
        require(wo != null && woSteps != null && cards != null && cardSteps != null, "工单和来源列表不能为空");
        requirePositiveId(wo.getMesWoId(), "工单主键");
        require(wo.getTenantId() != null && !wo.getTenantId().isBlank(), "工单租户不能为空");
        requireNonnegative(wo.getPlannedQty(), "工单计划数量");
        requireOptionalNonnegative(wo.getCompletedQty(), "工单已有完成数量");
        requireOptionalNonnegative(wo.getScrapQty(), "工单已有报废数量");

        Map<Long, ProWorkOrderStep> stepsById = new LinkedHashMap<>();
        for (ProWorkOrderStep step : woSteps) {
            require(step != null, "工单步骤不能为空");
            requirePositiveId(step.getStepId(), "工单步骤主键");
            requireOwner(wo, step.getTenantId(), step.getMesWoId());
            require(step.getPlannedQty() != null && step.getPlannedQty().signum() > 0, "工单步骤计划数量必须为正");
            requireOptionalNonnegative(step.getCompletedQty(), "工单步骤已有完成数量");
            requireOptionalNonnegative(step.getScrapQty(), "工单步骤已有报废数量");
            require(stepsById.putIfAbsent(step.getStepId(), step) == null, "工单步骤主键重复");
        }

        Map<Long, ProFlowCard> cardsById = new LinkedHashMap<>();
        BigDecimal coverage = BigDecimal.ZERO;
        for (ProFlowCard card : cards) {
            require(card != null, "流转卡不能为空");
            requirePositiveId(card.getCardId(), "流转卡主键");
            requireOwner(wo, card.getTenantId(), card.getMesWoId());
            require(card.getCardStatus() != null && CARD_STATUSES.contains(card.getCardStatus()), "流转卡状态不可信");
            requireNonnegative(card.getPlannedQty(), "流转卡计划数量");
            requireOptionalNonnegative(card.getCompletedQty(), "流转卡已有完成数量");
            requireOptionalNonnegative(card.getScrapQty(), "流转卡已有报废数量");
            require(cardsById.putIfAbsent(card.getCardId(), card) == null, "流转卡主键重复");
            coverage = coverage.add(card.getPlannedQty());
        }
        require(coverage.compareTo(wo.getPlannedQty()) <= 0, "流转卡计划总量不能超过工单计划数量");

        if (cards.isEmpty()) {
            require(cardSteps.isEmpty(), "无流转卡时不能存在卡工序记录");
            return calculateWithoutCards(wo, woSteps);
        }
        require(!woSteps.isEmpty(), "有流转卡时必须提供工单步骤来源");

        Map<Long, Map<Long, ProFlowCardStep>> mappedSteps = new LinkedHashMap<>();
        for (Long cardId : cardsById.keySet()) {
            mappedSteps.put(cardId, new LinkedHashMap<>());
        }
        Set<Long> recordIds = new HashSet<>();
        for (ProFlowCardStep step : cardSteps) {
            require(step != null, "卡工序记录不能为空");
            requirePositiveId(step.getStepRecordId(), "卡工序记录主键");
            require(recordIds.add(step.getStepRecordId()), "卡工序记录主键重复");
            requireOwner(wo, step.getTenantId(), step.getMesWoId());
            requirePositiveId(step.getCardId(), "卡工序父卡主键");
            ProFlowCard card = cardsById.get(step.getCardId());
            require(card != null, "卡工序引用了未知流转卡");
            requirePositiveId(step.getWoStepId(), "卡工序来源步骤主键");
            require(stepsById.containsKey(step.getWoStepId()), "卡工序引用了未知工单步骤");
            requireNonnegative(step.getPlannedQty(), "卡工序计划数量");
            validateExecutionQuantities(step, card.getPlannedQty());
            require(mappedSteps.get(step.getCardId()).putIfAbsent(step.getWoStepId(), step) == null,
                "同一卡内不能重复映射工单步骤");
        }
        for (Map<Long, ProFlowCardStep> mapping : mappedSteps.values()) {
            require(mapping.size() == stepsById.size(), "流转卡必须包含工单的全部步骤");
        }

        List<StepProjection> projections = new ArrayList<>(woSteps.size());
        for (ProWorkOrderStep woStep : woSteps) {
            BigDecimal good = BigDecimal.ZERO;
            BigDecimal scrap = BigDecimal.ZERO;
            boolean allTerminal = true;
            boolean allSkipped = true;
            boolean hasProgress = false;
            Date start = null;
            Date end = null;
            for (Map<Long, ProFlowCardStep> mapping : mappedSteps.values()) {
                ProFlowCardStep step = mapping.get(woStep.getStepId());
                // 仅汇总当前来源步骤，不能把不同工序的良品数量叠加成成品数量。
                good = good.add(zeroIfNull(step.getGoodQty()));
                scrap = scrap.add(zeroIfNull(step.getScrapQty()));
                boolean skipped = STEP_SKIPPED.equals(step.getStepStatus());
                allTerminal &= skipped || STEP_COMPLETED.equals(step.getStepStatus());
                allSkipped &= skipped;
                hasProgress |= !STEP_PENDING.equals(step.getStepStatus()) || hasExecution(step)
                    || step.getActualEndTime() != null;
                start = earliest(start, step.getActualStartTime());
                end = latest(end, step.getActualEndTime());
            }
            // 建卡覆盖不足时，即使已有卡全收口也仍有未建卡部分，不得提前完成。
            boolean closed = coverage.compareTo(woStep.getPlannedQty()) >= 0 && allTerminal;
            String status = closed ? (allSkipped ? STEP_SKIPPED : STEP_COMPLETED)
                : (hasProgress ? STEP_IN_PROGRESS : STEP_PENDING);
            projections.add(new StepProjection(woStep.getStepId(), good, scrap, status, start, closed ? end : null));
        }
        return List.copyOf(projections);
    }

    private static List<StepProjection> calculateWithoutCards(ProWorkOrder wo, List<ProWorkOrderStep> woSteps) {
        require(wo.getWoStatus() != null && UNSTARTED_ORDER_STATUSES.contains(wo.getWoStatus())
            && !positive(wo.getCompletedQty()) && !positive(wo.getScrapQty())
            && wo.getActualStartDate() == null && wo.getActualEndDate() == null,
            "工单已执行或状态不可信但缺少流转卡来源，不能猜测零进度");
        List<StepProjection> projections = new ArrayList<>(woSteps.size());
        for (ProWorkOrderStep step : woSteps) {
            require((STEP_PENDING.equals(step.getStepStatus()) || IProWorkOrderService.STEP_READY.equals(step.getStepStatus()))
                && !positive(step.getCompletedQty()) && !positive(step.getScrapQty())
                && step.getStartTime() == null && step.getEndTime() == null,
                "工单步骤已执行或状态不可信但缺少流转卡来源，不能猜测零进度");
            projections.add(new StepProjection(step.getStepId(), BigDecimal.ZERO, BigDecimal.ZERO, STEP_PENDING, null, null));
        }
        return List.copyOf(projections);
    }

    private static void validateExecutionQuantities(ProFlowCardStep step, BigDecimal cardPlannedQty) {
        require(step.getStepStatus() != null && STEP_STATUSES.contains(step.getStepStatus()), "卡工序状态不可信");
        requireOptionalNonnegative(step.getGoodQty(), "卡工序良品数量");
        requireOptionalNonnegative(step.getDefectQty(), "卡工序不良品数量");
        requireOptionalNonnegative(step.getScrapQty(), "卡工序报废数量");
        // 跳过的结束时间是实际收口记录，不代表执行；开工或非零数量则必须有完整执行数量。
        boolean needsQuantities = STEP_COMPLETED.equals(step.getStepStatus()) || STEP_IN_PROGRESS.equals(step.getStepStatus())
            || hasExecution(step) || (step.getActualEndTime() != null && !STEP_SKIPPED.equals(step.getStepStatus()));
        if (needsQuantities) {
            require(step.getGoodQty() != null && step.getDefectQty() != null && step.getScrapQty() != null,
                "已执行卡工序的良品、不良品和报废数量必须完整");
        }
        BigDecimal accounted = zeroIfNull(step.getGoodQty()).add(zeroIfNull(step.getDefectQty())).add(zeroIfNull(step.getScrapQty()));
        require(accounted.compareTo(cardPlannedQty) <= 0, "卡工序良品、不良品和报废总量不能超过对应流转卡计划数量");
    }

    private static boolean hasExecution(ProFlowCardStep step) {
        return step.getActualStartTime() != null || positive(step.getGoodQty())
            || positive(step.getDefectQty()) || positive(step.getScrapQty());
    }

    private static void requireOwner(ProWorkOrder wo, String tenantId, Long mesWoId) {
        require(wo.getTenantId().equals(tenantId), "来源记录必须属于同一租户");
        requirePositiveId(mesWoId, "来源记录父工单主键");
        require(wo.getMesWoId().equals(mesWoId), "来源记录必须属于同一工单");
    }

    private static void requirePositiveId(Long id, String field) {
        require(id != null && id > 0, field + "必须为正");
    }

    private static void requireNonnegative(BigDecimal quantity, String field) {
        require(quantity != null && quantity.signum() >= 0, field + "必须为非负可信数量");
    }

    private static void requireOptionalNonnegative(BigDecimal quantity, String field) {
        if (quantity != null) {
            requireNonnegative(quantity, field);
        }
    }

    private static boolean positive(BigDecimal quantity) {
        return quantity != null && quantity.signum() > 0;
    }

    private static BigDecimal zeroIfNull(BigDecimal quantity) {
        return quantity == null ? BigDecimal.ZERO : quantity;
    }

    private static Date earliest(Date current, Date candidate) {
        return candidate != null && (current == null || candidate.before(current)) ? candidate : current;
    }

    private static Date latest(Date current, Date candidate) {
        return candidate != null && (current == null || candidate.after(current)) ? candidate : current;
    }

    private static Date copyDate(Date date) {
        return date == null ? null : new Date(date.getTime());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 工单步骤只读投影；日期在构造及读取时防御拷贝，防止输入输出共享可变对象。
     *
     * @param stepId 工单步骤主键
     * @param completedQty 当前步骤实际良品合计
     * @param scrapQty 当前步骤实际报废合计
     * @param stepStatus 按覆盖量及卡工序执行状态计算的状态
     * @param startTime 当前步骤最早实际开始时间，可为空
     * @param endTime 当前步骤收口后的最晚实际结束时间，可为空
     */
    public record StepProjection(Long stepId, BigDecimal completedQty, BigDecimal scrapQty,
                                 String stepStatus, Date startTime, Date endTime) {
        public StepProjection {
            startTime = copyDate(startTime);
            endTime = copyDate(endTime);
        }

        @Override
        public Date startTime() {
            return copyDate(startTime);
        }

        @Override
        public Date endTime() {
            return copyDate(endTime);
        }
    }
}

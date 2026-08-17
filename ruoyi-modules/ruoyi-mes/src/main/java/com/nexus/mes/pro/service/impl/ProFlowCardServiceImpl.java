package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.flash.domain.FlashRecord;
import com.nexus.mes.flash.mapper.FlashRecordMapper;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import com.nexus.mes.pro.domain.bo.ProEntryBo;
import com.nexus.mes.pro.domain.bo.ProExitBo;
import com.nexus.mes.pro.domain.bo.ProFlowCardBo;
import com.nexus.mes.pro.domain.bo.ProSkipBo;
import com.nexus.mes.pro.domain.vo.ProCheckResultVo;
import com.nexus.mes.pro.domain.vo.ProFlowCardStepVo;
import com.nexus.mes.pro.domain.vo.ProFlowCardVo;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderStepMapper;
import com.nexus.mes.pro.service.IProEntryCheckService;
import com.nexus.mes.pro.service.IProFlowCardService;
import com.nexus.mes.pro.service.IProWorkOrderService;
import com.nexus.mes.qc.domain.bo.QcIpqcBo;
import com.nexus.mes.qc.service.IQcIpqcService;
import com.nexus.mes.sop.service.ISopProcessBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    /** IPQC 服务：关键工序报工时自动建过程检验单 */
    private final IQcIpqcService iqcIpqcService;
    /** 进站/出站校验规则引擎(P0-3) */
    private final IProEntryCheckService proEntryCheckService;
    /** SOP 工序绑定服务(P0-3 进站推送 SOP) */
    private final ISopProcessBindingService sopBindingService;
    /** NCR 不合格品服务(P0-4)：出站报工不良(defectQty>0)时自动创建 NCR 单。 */
    private final INcrOrderService ncrOrderService;
    /** SN 服务：(刷写门禁) 按流转卡反查在制 SN 列表 */
    private final ProSnMapper proSnMapper;
    /** 刷写记录：(刷写门禁) 校验固件刷写 PASS 记录 */
    private final FlashRecordMapper flashRecordMapper;
    /** JSON 解析(step_flash_config 多类型绑定) */
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    /** 刷写门禁模式: FORCE=FAIL拦截 / WARN=告警放行 / 其他=关闭 */
    @Value("${mes.flash.gate.mode:FORCE}")
    private String flashGateMode;

    @Override
    /**
     * 查询
     * @param bo bo
     * @param pageQuery pageQuery
     * @return 分页结果
     */
    public TableDataInfo<ProFlowCardVo> selectPageList(ProFlowCardBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ProFlowCard> lqw = buildQueryWrapper(bo);
        Page<ProFlowCardVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    /**
     * 查询
     * @param cardId 流转卡ID
     * @return 返回视图对象
     */
    public ProFlowCardVo selectById(Long cardId) {
        ProFlowCardVo vo = baseMapper.selectVoById(cardId);
        if (vo != null) {
            vo.setSteps(selectStepsByCardId(cardId));
        }
        return vo;
    }

    @Override
    /**
     * 查询
     * @param mesWoId MES工单ID
     * @return 结果列表
     */
    public List<ProFlowCardVo> selectByWoId(Long mesWoId) {
        LambdaQueryWrapper<ProFlowCard> lqw = Wrappers.<ProFlowCard>lambdaQuery()
            .eq(ProFlowCard::getMesWoId, mesWoId)
            .orderByDesc(ProFlowCard::getCreateTime);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 统计工单下所有流转卡计划数量之和(含已完工/已关闭,逻辑删除已自动过滤)
     */
    private BigDecimal sumPlannedQtyByWoId(Long mesWoId) {
        List<ProFlowCard> cards = baseMapper.selectList(Wrappers.<ProFlowCard>lambdaQuery()
            .eq(ProFlowCard::getMesWoId, mesWoId));
        BigDecimal sum = BigDecimal.ZERO;
        for (ProFlowCard card : cards) {
            if (card.getPlannedQty() != null) {
                sum = sum.add(card.getPlannedQty());
            }
        }
        return sum;
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
    @Lock4j(keys = {"#mesWoId"})
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

        // 2. 校验计划数量: 工单下所有流转卡计划数量之和不得超过工单计划数量(防止重复/超量创建)
        BigDecimal plannedQty = workOrder.getPlannedQty() == null ? BigDecimal.ZERO : workOrder.getPlannedQty();
        BigDecimal usedQty = sumPlannedQtyByWoId(mesWoId);
        BigDecimal remaining = plannedQty.subtract(usedQty);
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("流转卡计划数量必须大于0");
        }
        if (quantity.compareTo(remaining) > 0) {
            throw new RuntimeException(String.format(
                "流转卡计划数量超过剩余可建数量(工单计划数量:%s,已建:%s,剩余可建:%s)",
                plannedQty.stripTrailingZeros().toPlainString(),
                usedQty.stripTrailingZeros().toPlainString(),
                remaining.stripTrailingZeros().toPlainString()));
        }

        // 3. 创建流转卡主记录
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

        // 4. 查工单工序步骤，自动生成流转卡工序记录
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

        // 2. 若流转卡为CREATED/RELEASED，更新为IN_PROGRESS(首次开工自动推进,避免卡停留在CREATED导致后续完工聚合断链)
        baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
            .set(ProFlowCard::getCardStatus, STATUS_IN_PROGRESS)
            .set(ProFlowCard::getCurrentStepId, stepRecordId)
            .eq(ProFlowCard::getCardId, cardId)
            .in(ProFlowCard::getCardStatus, STATUS_CREATED, STATUS_RELEASED));

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

        // 数量校验：三个数量必须非负且合计不超过计划数量，防止负数/超量污染工单完工数量
        validateReportQuantities(goodQty, defectQty, scrapQty);

        // 刷写门禁: 工单步骤指定 firmware_id 的刷写工序, 完工前须该卡全部在制 SN 均已有对应固件 PASS 刷写记录
        checkFlashStepGate(stepRecordId, cardId);

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

        // 关键工序报工完成 -> 自动建 IPQC(幂等，失败不阻断报工，由 completeWo 门禁兜底)
        triggerIpqcIfKeyProcess(stepRecordId);

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
     * 校验报工数量：非负且三者合计不超过流转卡计划数量，防止负数/超量污染完工数据
     */
    private void validateReportQuantities(BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty) {
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
    }

    /**
     * 刷写工序门禁(固件刷写纳入工序步骤: 门禁强制)
     * <p>工单步骤指定 firmware_id 的工序视为刷写工序: 完工前须该流转卡上全部在制 SN
     * 均已存在对应固件的 PASS 刷写记录, 否则拒绝完工(未刷写不能过站)。</p>
     */
    private void checkFlashStepGate(Long stepRecordId, Long cardId) {
        // 门禁模式: FORCE=拦截 / WARN=告警放行 / 其他=关闭
        if (!"FORCE".equals(flashGateMode) && !"WARN".equals(flashGateMode)) {
            return;
        }
        ProFlowCardStep step = stepMapper.selectById(stepRecordId);
        if (step == null || step.getWoStepId() == null) {
            return;
        }
        ProWorkOrderStep woStep = workOrderStepMapper.selectById(step.getWoStepId());
        if (woStep == null) {
            return;
        }
        // 解析刷写绑定: step_flash_config 多类型优先, 否则单类型 content_ref_id(固件类)
        List<FlashBinding> bindings = parseFlashBindings(woStep);
        if (bindings.isEmpty()) {
            return;
        }
        // 卡上在制 SN 列表(无 SN 的批量卡不适用门禁)
        List<ProSn> sns = proSnMapper.selectList(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getCardId, cardId));
        if (sns == null || sns.isEmpty()) {
            return;
        }
        // 多类型门禁: 每个绑定项 × 每个 SN 均须最新一条 PASS
        List<String> unpassed = new ArrayList<>();
        for (FlashBinding b : bindings) {
            for (ProSn sn : sns) {
                FlashRecord latest = flashRecordMapper.selectOne(Wrappers.<FlashRecord>lambdaQuery()
                    .eq(FlashRecord::getSnCode, sn.getSnCode())
                    .eq(FlashRecord::getContentRefId, b.contentRefId())
                    .orderByDesc(FlashRecord::getFlashTime)
                    .orderByDesc(FlashRecord::getFlashRecordId)
                    .last("LIMIT 1"));
                if (latest == null || !"PASS".equals(latest.getResult())) {
                    unpassed.add(sn.getSnCode() + "(" + b.flashTypeCode() + ": " + (latest == null ? "未刷写" : latest.getResult()) + ")");
                }
            }
        }
        if (!unpassed.isEmpty()) {
            String msg = "刷写门禁未通过: 工序[" + step.getProcessName() + "]要求刷写内容已PASS, 未通过: " + String.join(", ", unpassed);
            if ("WARN".equals(flashGateMode)) {
                log.warn(msg + " (WARN模式放行)");
                return;
            }
            throw new ServiceException(msg);
        }
    }

    /**
     * 解析工序刷写绑定: step_flash_config(JSON 多类型数组)优先, 否则单类型 content_ref_id(固件类)。
     * <p>step_flash_config 格式: [{"flashTypeCode":"firmware_file","contentRefId":123},{"flashTypeCode":"mac","contentRefId":456}]</p>
     */
    private List<FlashBinding> parseFlashBindings(ProWorkOrderStep woStep) {
        List<FlashBinding> bindings = new ArrayList<>();
        if (StringUtils.isNotBlank(woStep.getStepFlashConfig())) {
            try {
                com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(woStep.getStepFlashConfig());
                for (com.fasterxml.jackson.databind.JsonNode b : arr) {
                    if (b.has("contentRefId") && b.get("contentRefId").canConvertToInt()) {
                        bindings.add(new FlashBinding(
                            b.has("flashTypeCode") ? b.get("flashTypeCode").asText() : "firmware_file",
                            b.get("contentRefId").asLong()));
                    }
                }
            } catch (Exception e) {
                log.warn("解析 step_flash_config 失败, 回退单类型: woStepId={}, err={}", woStep.getStepId(), e.getMessage());
            }
        }
        if (bindings.isEmpty() && woStep.getContentRefId() != null) {
            bindings.add(new FlashBinding("firmware_file", woStep.getContentRefId()));
        }
        return bindings;
    }

    /** 刷写绑定项(flashTypeCode + contentRefId) */
    private record FlashBinding(String flashTypeCode, Long contentRefId) {}

    /**
     * 完成流转卡
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeCard(Long cardId) {
        // 1. CAS更新流转卡状态 -> COMPLETED(兼容CREATED: 经派工聚合完工或手动完工的卡可能尚未推进到IN_PROGRESS)
        int rows = baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
            .set(ProFlowCard::getCardStatus, STATUS_COMPLETED)
            .eq(ProFlowCard::getCardId, cardId)
            .in(ProFlowCard::getCardStatus, STATUS_CREATED, STATUS_RELEASED, STATUS_IN_PROGRESS));
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
        // 刷写门禁(派工聚合完工路径, 与 completeStep 一致; 覆盖 completeDispatch→本方法路径)
        checkFlashStepGate(stepRecordId, cardId);
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
        // 关键工序报工完成 -> 自动建 IPQC(幂等，失败不阻断报工，由 completeWo 门禁兜底)
        triggerIpqcIfKeyProcess(stepRecordId);
        // 检查所有工序完成 -> completeCard
        Long pendingCount = stepMapper.selectCount(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, cardId)
            .ne(ProFlowCardStep::getStepStatus, STEP_COMPLETED)
            .ne(ProFlowCardStep::getStepStatus, STEP_SKIPPED));
        if (pendingCount == 0) {
            completeCard(cardId);
        }
    }

    // ==================== P0-3 进站/出站/跳站 ====================

    /**
     * 进站(P0-3): ENTRY 校验 -> 通过则 startStep + 推送SOP + 记录进站校验结果
     */
    @Lock4j(keys = {"#bo.cardId"})
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ProCheckResultVo entry(ProEntryBo bo) {
        log.info("工序进站: cardId={}, stepRecordId={}, operatorId={}", bo.getCardId(), bo.getStepRecordId(), bo.getOperatorId());

        // 1. 执行进站校验(含内置 SERIAL_GATE)
        ProCheckResultVo result = proEntryCheckService.runEntryChecks(bo.getStepRecordId(), bo);
        if (Boolean.FALSE.equals(result.getPassed())) {
            // 校验失败: 记录 entry_check_result=FAIL,不流转状态(操作员可整改后重试)
            stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
                .set(ProFlowCardStep::getEntryCheckResult, "FAIL")
                .set(ProFlowCardStep::getEntryCheckRemark, buildCheckRemark(result))
                .eq(ProFlowCardStep::getStepRecordId, bo.getStepRecordId()));
            return result;
        }

        // 2. 校验通过: 开工(复用既有 startStep 状态机)
        startStep(bo.getCardId(), bo.getStepRecordId(), bo.getOperatorId());

        // 3. 推送 SOP 到工位(失败不阻断)
        try {
            sopBindingService.pushSopToWorkstation(bo.getStepRecordId(), bo.getOperatorId());
            result.setSopPushed(true);
        } catch (Exception e) {
            log.warn("进站推送SOP失败(不阻断): stepRecordId={}", bo.getStepRecordId(), e);
            result.setSopPushed(false);
        }

        // 4. 记录 entry_check_result=PASS
        stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
            .set(ProFlowCardStep::getEntryCheckResult, "PASS")
            .eq(ProFlowCardStep::getStepRecordId, bo.getStepRecordId()));
        return result;
    }

    /**
     * 出站(P0-3): EXIT 校验 -> 通过则 completeStep + 记录出站校验结果
     */
    @Lock4j(keys = {"#bo.cardId"})
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ProCheckResultVo exit(ProExitBo bo) {
        log.info("工序出站: cardId={}, stepRecordId={}, good={}, defect={}, scrap={}",
            bo.getCardId(), bo.getStepRecordId(), bo.getGoodQty(), bo.getDefectQty(), bo.getScrapQty());

        // 1. 执行出站校验
        ProCheckResultVo result = proEntryCheckService.runExitChecks(bo.getStepRecordId(), bo);
        if (Boolean.FALSE.equals(result.getPassed())) {
            stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
                .set(ProFlowCardStep::getExitCheckResult, "FAIL")
                .set(ProFlowCardStep::getExitCheckRemark, buildCheckRemark(result))
                .eq(ProFlowCardStep::getStepRecordId, bo.getStepRecordId()));
            return result;
        }

        // 2. 校验通过: 完工(复用既有 completeStep 状态机)
        completeStep(bo.getCardId(), bo.getStepRecordId(), bo.getGoodQty(), bo.getDefectQty(), bo.getScrapQty());

        // P0-4: 出站报工不良(defectQty>0)自动创建NCR单(幂等;关联流转卡/工单)
        if (bo.getDefectQty() != null && bo.getDefectQty().compareTo(BigDecimal.ZERO) > 0) {
            ProFlowCard card = baseMapper.selectById(bo.getCardId());
            Long ncrMesWoId = card != null ? card.getMesWoId() : null;
            ncrOrderService.autoCreateFromExit(bo.getCardId(), bo.getStepRecordId(), ncrMesWoId, bo.getDefectQty(), null);
        }

        // 3. 记录 exit_check_result=PASS
        stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
            .set(ProFlowCardStep::getExitCheckResult, "PASS")
            .eq(ProFlowCardStep::getStepRecordId, bo.getStepRecordId()));
        return result;
    }

    /**
     * 跳站(P0-3): 防错校验 -> 置 SKIPPED + 记录原因 + 推进下一工序
     */
    @Lock4j(keys = {"#bo.cardId"})
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void skip(ProSkipBo bo) {
        log.info("工序跳站: cardId={}, stepRecordId={}", bo.getCardId(), bo.getStepRecordId());

        ProFlowCardStep step = stepMapper.selectById(bo.getStepRecordId());
        if (step == null) {
            throw new RuntimeException("工序记录不存在: " + bo.getStepRecordId());
        }
        // 首道工序不允许跳过(防跳过 mandatory 工序)
        if (step.getSequenceNum() == null || step.getSequenceNum() <= 1) {
            throw new RuntimeException("首道工序不允许跳过");
        }
        // CAS 置 SKIPPED(仅 PENDING 可跳过)
        int rows = stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
            .set(ProFlowCardStep::getStepStatus, STEP_SKIPPED)
            .set(ProFlowCardStep::getSkipReason, bo.getReason())
            .eq(ProFlowCardStep::getStepRecordId, bo.getStepRecordId())
            .eq(ProFlowCardStep::getCardId, bo.getCardId())
            .eq(ProFlowCardStep::getStepStatus, STEP_PENDING));
        if (rows == 0) {
            throw new RuntimeException("工序状态已变更或不存在，仅待开始状态可跳过");
        }

        // 推进流转卡 current_step_id 到下一未完成工序
        ProFlowCardStep next = stepMapper.selectOne(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, bo.getCardId())
            .gt(ProFlowCardStep::getSequenceNum, step.getSequenceNum())
            .orderByAsc(ProFlowCardStep::getSequenceNum)
            .last("LIMIT 1"));
        if (next != null) {
            baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
                .set(ProFlowCard::getCurrentStepId, next.getStepRecordId())
                .eq(ProFlowCard::getCardId, bo.getCardId()));
        }

        // 检查是否所有工序完成/跳过 -> completeCard
        Long pendingCount = stepMapper.selectCount(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, bo.getCardId())
            .ne(ProFlowCardStep::getStepStatus, STEP_COMPLETED)
            .ne(ProFlowCardStep::getStepStatus, STEP_SKIPPED));
        if (pendingCount == 0) {
            log.info("跳站后流转卡所有工序完成/跳过，触发completeCard: cardId={}", bo.getCardId());
            completeCard(bo.getCardId());
        }
    }

    /**
     * 拼装校验未通过备注(failures 拼接,超 500 字符截断)
     */
    private String buildCheckRemark(ProCheckResultVo result) {
        if (result.getFailures() == null || result.getFailures().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ProCheckResultVo.CheckFailure f : result.getFailures()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(f.getCheckType()).append(": ").append(f.getErrorMsg());
            if (sb.length() > 480) {
                break;
            }
        }
        String remark = sb.toString();
        return remark.length() > 500 ? remark.substring(0, 500) : remark;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 关键工序报工完成时自动建 IPQC。
     * <p>
     * 仅 isKeyProcess=1 的工序触发；按 (mesWoId, processId) 幂等（同工单同工序仅一条 IPQC）。
     * IPQC 创建走独立事务(REQUIRES_NEW)，失败仅告警不阻断报工，由 completeWo 门禁兜底。
     *
     * @param stepRecordId 流转卡工序记录 ID
     */
    private void triggerIpqcIfKeyProcess(Long stepRecordId) {
        try {
            ProFlowCardStep step = stepMapper.selectById(stepRecordId);
            if (step == null || !Integer.valueOf(1).equals(step.getIsKeyProcess())) {
                return;
            }
            ProWorkOrder wo = workOrderMapper.selectById(step.getMesWoId());
            if (wo == null) {
                log.warn("[IPQC自动建单] 工单不存在，跳过: mesWoId={}", step.getMesWoId());
                return;
            }
            QcIpqcBo bo = new QcIpqcBo();
            bo.setMesWoId(step.getMesWoId());
            bo.setWoNo(wo.getWoNo());
            bo.setCardId(step.getCardId());
            bo.setStepRecordId(step.getStepRecordId());
            bo.setProcessId(step.getProcessId());
            bo.setProcessCode(step.getProcessCode());
            bo.setProcessName(step.getProcessName());
            bo.setMaterialId(wo.getMaterialId());
            bo.setMaterialCode(wo.getMaterialCode());
            bo.setMaterialName(wo.getMaterialName());
            iqcIpqcService.autoCreateForKeyProcess(bo);
        } catch (Exception e) {
            log.error("[IPQC自动建单] 关键工序报工自动建 IPQC 失败(不影响报工，由 completeWo 门禁兜底): stepRecordId={}", stepRecordId, e);
        }
    }

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

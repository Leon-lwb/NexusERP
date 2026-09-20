package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.SpringUtils;
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
import java.util.Map;

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

    /**
     * 卡级程序化锁 key 前缀。
     * <p>完工聚合判定(工序是否全完成 → 卡完工 → 工单数量聚合)必须由同一把卡级锁串行化,
     * 且锁必须在事务提交之后才释放, 否则并发工序完工时读不到对方未提交的完工记录(MES-PRO-02)。</p>
     */
    private static final String CARD_LOCK_PREFIX = "mes:flowcard:";
    /** 卡级锁持有时长(ms) */
    private static final long CARD_LOCK_EXPIRE = 30000L;
    /** 卡级锁获取超时(ms) */
    private static final long CARD_LOCK_ACQUIRE_TIMEOUT = 5000L;

    private final ProFlowCardMapper baseMapper;
    private final ProFlowCardStepMapper stepMapper;
    private final ProWorkOrderMapper workOrderMapper;
    private final ProWorkOrderStepMapper workOrderStepMapper;
    /** 程序化分布式锁模板: 卡级锁覆盖整个事务(见 CARD_LOCK_PREFIX 注释) */
    private final LockTemplate lockTemplate;
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
            && !IProWorkOrderService.STATUS_IN_PROGRESS.equals(workOrder.getWoStatus())
            && !IProWorkOrderService.STATUS_PARTIAL_COMPLETED.equals(workOrder.getWoStatus())) {
            throw new RuntimeException("仅已下达、生产中或部分完成的工单可创建流转卡,当前状态:" + workOrder.getWoStatus());
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
     * <p>入口不加事务: 先取卡级程序化锁, 再经 AOP 代理调用带事务的实现方法, 使锁覆盖整个事务
     * (锁在事务提交之后释放)。若锁释放在事务提交前, 并发工序完工时"所有工序是否完成"的判定
     * 读不到对方未提交的完工记录, 流转卡不会完工(MES-PRO-02)。</p>
     */
    @Override
    public void completeStep(Long cardId, Long stepRecordId,
                             BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty) {
        LockInfo lock = lockTemplate.lock(CARD_LOCK_PREFIX + cardId, CARD_LOCK_EXPIRE, CARD_LOCK_ACQUIRE_TIMEOUT);
        if (lock == null) {
            throw new ServiceException("该流转卡有工序完工处理进行中, 请稍后重试: cardId=" + cardId);
        }
        try {
            // 经 AOP 代理调用以使事务生效(同类直调绕过代理)
            SpringUtils.getAopProxy(this).doCompleteStep(cardId, stepRecordId, goodQty, defectQty, scrapQty);
        } finally {
            lockTemplate.releaseLock(lock);
        }
    }

    /**
     * 完成工序执行(带事务)。仅供 {@link #completeStep} 经 AOP 代理调用, 卡级锁由入口持有。
     */
    @Transactional(rollbackFor = Exception.class)
    public void doCompleteStep(Long cardId, Long stepRecordId,
                               BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty) {
        log.info("完成工序: cardId={}, stepRecordId={}, good={}, defect={}, scrap={}",
            cardId, stepRecordId, goodQty, defectQty, scrapQty);

        // 数量校验：非负且合计等于流转卡计划数量(良品+不良+报废全数分流), 防止少报污染完工数据
        ProFlowCard cardForCheck = baseMapper.selectById(cardId);
        validateReportQuantities(goodQty, defectQty, scrapQty, cardForCheck);

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
            // 本方法已在 completeStep 的事务与卡级锁内, 直接调用聚合实现(不再重复取锁)
            doCompleteCard(cardId, false);
        }
    }

    /**
     * 校验报工数量：非负, 合计必须等于流转卡计划数量(良品+不良+报废全数分流)
     * <p>出站完工为"一次报工即完工"模型(completed_qty 用 goodQty 覆写非累加), 故报工时
     * 三者合计须等于卡 planned_qty, 不允许少报(少报会导致卡 completed_qty < planned_qty,
     * 卡已COMPLETED但完工数不足, 污染工单完工数量判定)。</p>
     */
    private void validateReportQuantities(BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty, ProFlowCard card) {
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
        if (card != null && card.getPlannedQty() != null
            && total.compareTo(card.getPlannedQty()) != 0) {
            throw new RuntimeException(String.format(
                "报工数量合计必须等于流转卡计划数量 %s(良品+不良+报废全数分流), 当前合计 %s, 卡号 %s",
                card.getPlannedQty().stripTrailingZeros().toPlainString(),
                total.stripTrailingZeros().toPlainString(),
                card.getCardNo()));
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
        // E: SN 作用域 — 刷写记录是 SN 级的, 故须校验卡上各 SN 的刷写记录; 无 SN 时:
        //    刷写工序(有绑定)无 SN = 无法产生刷写记录 = 未刷写, 直接拦截(FORCE)/告警(WARN), 防派工绕过门禁
        //    (不查 ERP 物料 SN 管理标志: 事务内 @DS("mes") 无法切 erp 数据源; 且绑定了刷写内容的工序业务上必须逐 SN 刷写)
        List<ProSn> sns = proSnMapper.selectList(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getCardId, cardId));
        if (sns == null || sns.isEmpty()) {
            String msg = "刷写门禁未通过: 工序[" + step.getProcessName() + "]为刷写工序但该卡无SN"
                + "(刷写为SN级作业, 请先在流转卡详情页点「生成SN」生成SN后逐台刷写)";
            if ("WARN".equals(flashGateMode)) {
                log.warn(msg + " (WARN模式放行)");
                return;
            }
            throw new ServiceException(msg);
        }
        // 多类型门禁: 每个(绑定项 × SN) 均须该工序序号对应的最新一条 PASS
        List<String> unpassed = new ArrayList<>();
        for (FlashBinding b : bindings) {
            for (ProSn sn : sns) {
                // MES-FLASH-03: contentRefId 为 null 时不得参与比较(无条件拼 content_ref_id = NULL 会永不匹配、门禁误拦);
                // 未配置即按既有约定退化为"该 SN 该刷写类型的最新一条"(对齐 selectPendingTasks 的守卫写法),
                // 同时按 flashTypeCode 收敛, 避免 firmware 的 PASS 记录冒充 mac/sn 绑定通过门禁
                FlashRecord latest = flashRecordMapper.selectOne(Wrappers.<FlashRecord>lambdaQuery()
                    .eq(FlashRecord::getSnCode, sn.getSnCode())
                    .eq(b.contentRefId() != null, FlashRecord::getContentRefId, b.contentRefId())
                    .eq(StringUtils.isNotBlank(b.flashTypeCode()), FlashRecord::getFlashTypeCode, b.flashTypeCode())
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
                    if (b.has("contentRefId") && b.get("contentRefId").canConvertToLong()) {
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
     * 完成流转卡(聚合路径)
     * <p>入口不加事务: 取卡级程序化锁覆盖整个事务, 经 AOP 代理调用带事务的实现方法。
     * 门禁: 所有工序必须已完成/已跳过; 聚合路径(派工/出站/设备通道/跳站)遇未完成工序仅记 warn 返回,
     * 由最后一个工序完工的路径再次触发; UI 手工完工走 {@link #manualCompleteCard} 强校验拒绝。</p>
     */
    @Override
    public void completeCard(Long cardId) {
        LockInfo lock = lockTemplate.lock(CARD_LOCK_PREFIX + cardId, CARD_LOCK_EXPIRE, CARD_LOCK_ACQUIRE_TIMEOUT);
        if (lock == null) {
            throw new ServiceException("该流转卡有完工处理进行中, 请稍后重试: cardId=" + cardId);
        }
        try {
            SpringUtils.getAopProxy(this).doCompleteCard(cardId, false);
        } finally {
            lockTemplate.releaseLock(lock);
        }
    }

    /**
     * 手工完工流转卡(UI 一键完工入口)。
     * <p>MES-PRO-01: 手工完工不得绕过工序 —— 存在未完成工序时直接拒绝并列出未完成工序。
     * 旧实现允许 CREATED/RELEASED 直接完工且完工数被重算为 0, 导致此后真实完工被 CAS 挡回、
     * 工单永久 PARTIAL_COMPLETED、ERP 收不到完工消息。</p>
     */
    @Override
    public void manualCompleteCard(Long cardId) {
        LockInfo lock = lockTemplate.lock(CARD_LOCK_PREFIX + cardId, CARD_LOCK_EXPIRE, CARD_LOCK_ACQUIRE_TIMEOUT);
        if (lock == null) {
            throw new ServiceException("该流转卡有完工处理进行中, 请稍后重试: cardId=" + cardId);
        }
        try {
            SpringUtils.getAopProxy(this).doCompleteCard(cardId, true);
        } finally {
            lockTemplate.releaseLock(lock);
        }
    }

    /**
     * 完成流转卡执行(带事务)。仅供 {@link #completeCard}/{@link #manualCompleteCard} 经 AOP 代理调用, 卡级锁由入口持有。
     *
     * @param strict true=手工完工(存在未完成工序时抛异常拒绝); false=聚合路径(存在未完成工序时记 warn 返回)
     */
    @Transactional(rollbackFor = Exception.class)
    public void doCompleteCard(Long cardId, boolean strict) {
        ProFlowCard card = baseMapper.selectById(cardId);
        if (card == null) {
            throw new ServiceException("流转卡不存在: " + cardId);
        }

        // 1. 完工门禁: 所有工序必须已完成/已跳过(手工完工不得绕过工序, MES-PRO-01)
        List<ProFlowCardStep> allSteps = stepMapper.selectList(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, cardId)
            .orderByAsc(ProFlowCardStep::getSequenceNum));
        if (allSteps.isEmpty()) {
            throw new ServiceException("流转卡无工序记录, 不能完工: cardId=" + cardId);
        }
        List<String> pendingSteps = new ArrayList<>();
        for (ProFlowCardStep s : allSteps) {
            if (!STEP_COMPLETED.equals(s.getStepStatus()) && !STEP_SKIPPED.equals(s.getStepStatus())) {
                pendingSteps.add((s.getSequenceNum() == null ? "?" : String.valueOf(s.getSequenceNum()))
                    + ":" + s.getProcessName() + "(" + s.getStepStatus() + ")");
            }
        }
        if (!pendingSteps.isEmpty()) {
            String msg = "流转卡存在未完成工序, 不能完工: " + String.join(", ", pendingSteps);
            if (strict) {
                throw new ServiceException(msg);
            }
            log.warn(msg + " (聚合路径跳过完工): cardId=" + cardId);
            return;
        }

        // 2. CAS更新流转卡状态 -> COMPLETED
        int rows = baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
            .set(ProFlowCard::getCardStatus, STATUS_COMPLETED)
            .eq(ProFlowCard::getCardId, cardId)
            .in(ProFlowCard::getCardStatus, STATUS_CREATED, STATUS_RELEASED, STATUS_IN_PROGRESS));
        if (rows == 0) {
            // 已完工: 继续走步骤4的幂等入账(自愈: 工序已全完成而卡曾完工但未入账); 其它状态(如CLOSED)直接返回
            ProFlowCard fresh = baseMapper.selectById(cardId);
            if (fresh == null || !STATUS_COMPLETED.equals(fresh.getCardStatus())) {
                log.warn("流转卡状态不满足完工条件，跳过: cardId={}, status={}", cardId,
                    fresh == null ? null : fresh.getCardStatus());
                return;
            }
            card = fresh;
        }

        // 3. 从工序记录重算流转卡数量（确保派工路径和直接路径都正确）
        //    completed_qty = 最后一个 COMPLETED 工序的 good_qty（SKIPPED 工序不产出, 不参与; MES-PRO-07）
        //    scrap_qty = 所有工序 scrap_qty 之和（各工序独立报废）
        BigDecimal correctCompletedQty = BigDecimal.ZERO;
        BigDecimal correctScrapQty = BigDecimal.ZERO;
        for (ProFlowCardStep s : allSteps) {
            if (STEP_COMPLETED.equals(s.getStepStatus())) {
                correctCompletedQty = s.getGoodQty() != null ? s.getGoodQty() : BigDecimal.ZERO;
            }
            correctScrapQty = correctScrapQty.add(s.getScrapQty() != null ? s.getScrapQty() : BigDecimal.ZERO);
        }
        baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
            .set(ProFlowCard::getCompletedQty, correctCompletedQty)
            .set(ProFlowCard::getScrapQty, correctScrapQty)
            .eq(ProFlowCard::getCardId, cardId));
        card.setCompletedQty(correctCompletedQty);
        card.setScrapQty(correctScrapQty);
        log.info("流转卡完工: cardId={}, completedQty={}, scrapQty={}", cardId, correctCompletedQty, correctScrapQty);

        // 4. 一次性入账: CAS aggregated_flag 0 -> 1, 成功才向工单聚合(重复完工/自愈重入不重复入账)
        int bookRows = baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
            .set(ProFlowCard::getAggregatedFlag, 1)
            .eq(ProFlowCard::getCardId, cardId)
            .eq(ProFlowCard::getAggregatedFlag, 0));
        if (bookRows == 0) {
            log.info("流转卡完工数量已入账, 跳过工单聚合(幂等): cardId={}", cardId);
            return;
        }

        // 5. 读取工单(增量前快照, 用于完工数量判定; 本事务首次读 wo, 非缓存旧值)
        ProWorkOrder wo = workOrderMapper.selectById(card.getMesWoId());
        BigDecimal plannedQty = wo != null && wo.getPlannedQty() != null ? wo.getPlannedQty() : BigDecimal.ZERO;
        BigDecimal woCompletedBefore = wo != null && wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
        BigDecimal woScrapBefore = wo != null && wo.getScrapQty() != null ? wo.getScrapQty() : BigDecimal.ZERO;

        // 聚合到工单 completed_qty（card 的 completedQty 已在步骤3中重算为正确值）
        workOrderMapper.update(null, Wrappers.<ProWorkOrder>lambdaUpdate()
            .setSql("completed_qty = completed_qty + {0}", correctCompletedQty)
            .setSql("scrap_qty = scrap_qty + {0}", correctScrapQty)
            .eq(ProWorkOrder::getMesWoId, card.getMesWoId()));

        // 6. 判定工单是否全部完工: 累计(完工+报废) >= 计划数量 才转 COMPLETED, 否则 PARTIAL_COMPLETED
        //    (避免: 计划10仅完工3/6, 即便所有流转卡已COMPLETED, 工单也不应判已完工; 以数量为准非卡数)
        BigDecimal totalCompleted = woCompletedBefore.add(correctCompletedQty);
        BigDecimal totalScrap = woScrapBefore.add(correctScrapQty);
        if (totalCompleted.add(totalScrap).compareTo(plannedQty) >= 0) {
            log.info("工单累计完工+报废({}+{})达计划({}), 触发工单完工: mesWoId={}",
                totalCompleted.stripTrailingZeros().toPlainString(),
                totalScrap.stripTrailingZeros().toPlainString(),
                plannedQty.stripTrailingZeros().toPlainString(),
                card.getMesWoId());
            proWorkOrderService.completeWo(card.getMesWoId());
        } else {
            // 部分完工: 累计未达计划, 工单标记 PARTIAL_COMPLETED(不通知ERP, 不触发完工入库)
            // CAS: RELEASED/IN_PROGRESS -> PARTIAL_COMPLETED; 兼容未开工补 actualStartDate
            Date now = new Date();
            int partialRows = workOrderMapper.update(null, Wrappers.<ProWorkOrder>lambdaUpdate()
                .set(ProWorkOrder::getWoStatus, IProWorkOrderService.STATUS_PARTIAL_COMPLETED)
                .eq(ProWorkOrder::getMesWoId, card.getMesWoId())
                .in(ProWorkOrder::getWoStatus,
                    IProWorkOrderService.STATUS_RELEASED,
                    IProWorkOrderService.STATUS_IN_PROGRESS));
            if (partialRows > 0 && wo != null && wo.getActualStartDate() == null) {
                workOrderMapper.update(null, Wrappers.<ProWorkOrder>lambdaUpdate()
                    .set(ProWorkOrder::getActualStartDate, now)
                    .eq(ProWorkOrder::getMesWoId, card.getMesWoId())
                    .isNull(ProWorkOrder::getActualStartDate));
            }
            log.info("工单部分完工: 累计完工+报废({}+{})未达计划({}), 标记 PARTIAL_COMPLETED: mesWoId={}",
                totalCompleted.stripTrailingZeros().toPlainString(),
                totalScrap.stripTrailingZeros().toPlainString(),
                plannedQty.stripTrailingZeros().toPlainString(),
                card.getMesWoId());
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
     * <p>入口不加事务: 卡级程序化锁覆盖整个事务, 经 AOP 代理调用带事务的实现方法。
     * 旧实现把 @Lock4j 加在本方法上, 锁在方法返回时释放而外层 doCompleteDispatch 事务尚未提交,
     * 导致"工序是否全完成"的判定读不到并发事务未提交的完工记录, 工序全 COMPLETED 而卡不完工(MES-PRO-02)。</p>
     */
    @Override
    public void checkAndCompleteStep(Long cardId, Long stepRecordId) {
        LockInfo lock = lockTemplate.lock(CARD_LOCK_PREFIX + cardId, CARD_LOCK_EXPIRE, CARD_LOCK_ACQUIRE_TIMEOUT);
        if (lock == null) {
            throw new ServiceException("该流转卡有工序完工处理进行中, 请稍后重试: cardId=" + cardId);
        }
        try {
            SpringUtils.getAopProxy(this).checkAndCompleteStepUnderLock(cardId, stepRecordId);
        } finally {
            lockTemplate.releaseLock(lock);
        }
    }

    /**
     * 检查并完成工序(带事务, 不取锁)。
     * <p><b>调用方必须已持有 {@code mes:flowcard:{cardId}} 程序化锁, 且锁覆盖自身事务提交</b>
     * (如 {@code ProDispatchServiceImpl.completeDispatch}); 否则存在 MES-PRO-02 竞态。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void checkAndCompleteStepUnderLock(Long cardId, Long stepRecordId) {
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
        // 检查所有工序完成 -> completeCard(直接调用聚合实现, 锁由入口/本方法事务持有)
        Long pendingCount = stepMapper.selectCount(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, cardId)
            .ne(ProFlowCardStep::getStepStatus, STEP_COMPLETED)
            .ne(ProFlowCardStep::getStepStatus, STEP_SKIPPED));
        if (pendingCount == 0) {
            doCompleteCard(cardId, false);
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
    @Override
    public ProCheckResultVo exit(ProExitBo bo) {
        LockInfo lock = lockTemplate.lock(CARD_LOCK_PREFIX + bo.getCardId(), CARD_LOCK_EXPIRE, CARD_LOCK_ACQUIRE_TIMEOUT);
        if (lock == null) {
            throw new ServiceException("该流转卡有工序完工处理进行中, 请稍后重试: cardId=" + bo.getCardId());
        }
        try {
            return SpringUtils.getAopProxy(this).doExit(bo);
        } finally {
            lockTemplate.releaseLock(lock);
        }
    }

    /**
     * 出站执行(带事务)。仅供 {@link #exit} 经 AOP 代理调用, 卡级锁由入口持有。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProCheckResultVo doExit(ProExitBo bo) {
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

        // 2. 校验通过: 完工(复用既有工序完工状态机; 卡级锁与事务已由 exit 入口持有)
        doCompleteStep(bo.getCardId(), bo.getStepRecordId(), bo.getGoodQty(), bo.getDefectQty(), bo.getScrapQty());

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
    @Override
    public void skip(ProSkipBo bo) {
        LockInfo lock = lockTemplate.lock(CARD_LOCK_PREFIX + bo.getCardId(), CARD_LOCK_EXPIRE, CARD_LOCK_ACQUIRE_TIMEOUT);
        if (lock == null) {
            throw new ServiceException("该流转卡有完工处理进行中, 请稍后重试: cardId=" + bo.getCardId());
        }
        try {
            SpringUtils.getAopProxy(this).doSkip(bo);
        } finally {
            lockTemplate.releaseLock(lock);
        }
    }

    /**
     * 跳站执行(带事务)。仅供 {@link #skip} 经 AOP 代理调用, 卡级锁由入口持有。
     */
    @Transactional(rollbackFor = Exception.class)
    public void doSkip(ProSkipBo bo) {
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
            doCompleteCard(bo.getCardId(), false);
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

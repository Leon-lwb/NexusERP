package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.pro.domain.ProAndon;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProStepCheckRule;
import com.nexus.mes.pro.domain.bo.ProEntryBo;
import com.nexus.mes.pro.domain.bo.ProExitBo;
import com.nexus.mes.pro.domain.vo.ProCheckResultVo;
import com.nexus.mes.pro.mapper.ProAndonMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProStepCheckRuleMapper;
import com.nexus.mes.pro.service.IProEntryCheckService;
import com.nexus.mes.pro.service.IProFlowCardService;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.mes.sop.domain.vo.SopDocumentVo;
import com.nexus.mes.sop.service.ISopProcessBindingService;
import com.nexus.mes.sop.service.ISopViewLogService;
import com.nexus.mes.tg.service.ITgToolingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * MES工序进站/出站校验规则引擎 Service 实现(P0-3)
 * <p>按 {@code pro_step_check_rule} 配置执行 ENTRY/EXIT 校验。
 * 规则按 sort_order 顺序执行,任一 FAIL 即计入 failures;passed = failures 为空。</p>
 *
 * <p>v1.0 实现的校验类型：SERIAL_GATE/SOP_VERSION/SOP_ACKNOWLEDGED/TOOLING_AVAILABLE/LOT_AVAILABLE
 * /EQUIPMENT_STATUS/QUANTITY_REACHED/INSPECTION_REQUIRED/EXCEPTION_CLOSED。
 * 存根类型(MATERIAL_KITTING/OPERATOR_SKILL/PARAMETER_OK)默认禁用,启用时返回 PASS 并告警。</p>
 *
 * <p><b>MES-TG-02</b>: TOOLING_AVAILABLE 规则要求工装但上下文未带 toolingId 时<b>拒绝</b>进站
 * （此前静默 return，等于门禁可被「不传参数」绕过）。</p>
 * <p><b>MES-SOP-02</b>: 新增 SOP_ACKNOWLEDGED 规则 —— 校验操作员对当前发布版本产生过
 * SOP_VIEW 电子签名，把「作业指导书已阅」变成真正的操作前门禁。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("proEntryCheckService")
public class ProEntryCheckServiceImpl implements IProEntryCheckService {

    /** SOP 已发布状态(对齐 sop_document.status) */
    private static final String SOP_PUBLISHED = "PUBLISHED";
    /** IPQC 检验合格 */
    private static final String IPQC_QUALIFIED = "QUALIFIED";
    /** 安灯活跃状态: 已呼叫 */
    private static final String ANDON_CALLED = "CALLED";
    /** 安灯活跃状态: 已响应 */
    private static final String ANDON_RESPONDED = "RESPONDED";
    /** 设备故障状态 */
    private static final String EQ_FAULT = "FAULT";

    private final ProFlowCardStepMapper stepMapper;
    private final ProStepCheckRuleMapper ruleMapper;
    private final ProAndonMapper andonMapper;
    private final QcIpqcMapper qcIpqcMapper;
    private final EqEquipmentMapper eqEquipmentMapper;
    private final ISopProcessBindingService sopBindingService;
    private final ISopViewLogService sopViewLogService;
    private final ITgToolingService tgToolingService;
    private final ILtLotService ltLotService;

    @Override
    public ProCheckResultVo runEntryChecks(Long stepRecordId, ProEntryBo context) {
        ProFlowCardStep step = loadStep(stepRecordId);
        ProCheckResultVo result = new ProCheckResultVo();
        result.setStepRecordId(stepRecordId);
        result.setPassed(true);

        // 1. 内置串行门禁(always-on): 非首道工序时,上一工序须 COMPLETED/SKIPPED
        checkSerialGate(step, result);

        // 2. 加载并执行已启用的 ENTRY 规则
        List<ProStepCheckRule> rules = listEnabledRules(step.getProcessId(), STAGE_ENTRY);
        for (ProStepCheckRule rule : rules) {
            dispatchEntry(rule, step, context, result);
        }

        result.setPassed(result.getFailures().isEmpty());
        return result;
    }

    @Override
    public ProCheckResultVo runExitChecks(Long stepRecordId, ProExitBo context) {
        ProFlowCardStep step = loadStep(stepRecordId);
        ProCheckResultVo result = new ProCheckResultVo();
        result.setStepRecordId(stepRecordId);
        result.setPassed(true);

        List<ProStepCheckRule> rules = listEnabledRules(step.getProcessId(), STAGE_EXIT);
        for (ProStepCheckRule rule : rules) {
            dispatchExit(rule, step, context, result);
        }

        result.setPassed(result.getFailures().isEmpty());
        return result;
    }

    // ==================== ENTRY 规则处理器 ====================

    private void dispatchEntry(ProStepCheckRule rule, ProFlowCardStep step, ProEntryBo context, ProCheckResultVo result) {
        switch (rule.getCheckType()) {
            case TYPE_SOP_VERSION:
                checkSopVersion(rule, step, result);
                break;
            case TYPE_SOP_ACKNOWLEDGED:
                checkSopAcknowledged(rule, step, context, result);
                break;
            case TYPE_TOOLING_AVAILABLE:
                checkToolingAvailable(rule, context, result);
                break;
            case TYPE_LOT_AVAILABLE:
                checkLotAvailable(rule, step, context, result);
                break;
            case TYPE_EQUIPMENT_STATUS:
                checkEquipmentStatus(rule, step, result);
                break;
            case TYPE_MATERIAL_KITTING:
            case TYPE_OPERATOR_SKILL:
                // 存根: 默认禁用, 启用时返回 PASS 并告警(待 P0-1/资质表落地)
                log.warn("[进站校验] {} 规则为存根实现,默认PASS: processId={}", rule.getCheckType(), step.getProcessId());
                break;
            default:
                log.warn("[进站校验] 未知的 ENTRY 校验类型,跳过: {}", rule.getCheckType());
        }
    }

    /**
     * 串行门禁: 非首道工序时,按同一流转卡内上一条工序记录(sequenceNum递增, 但可能不连续如 10/20/30)
     * 查询小于当前 sequenceNum 的工序中最大的那条,须为 COMPLETED 或 SKIPPED
     */
    private void checkSerialGate(ProFlowCardStep step, ProCheckResultVo result) {
        if (step.getCardId() == null || step.getSequenceNum() == null) {
            return;
        }
        // 查询 cardId 下是否有比当前 sequenceNum 更小的工序
        ProFlowCardStep prev = stepMapper.selectOne(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, step.getCardId())
            .lt(ProFlowCardStep::getSequenceNum, step.getSequenceNum())
            .orderByDesc(ProFlowCardStep::getSequenceNum)
            .last("LIMIT 1"));
        if (prev == null) {
            // 没有更小的工序(即当前是第一道工序),跳过门禁
            return;
        }
        if (!IProFlowCardService.STEP_COMPLETED.equals(prev.getStepStatus())
            && !IProFlowCardService.STEP_SKIPPED.equals(prev.getStepStatus())) {
            addFailure(result, TYPE_SERIAL_GATE, "上一工序[" + prev.getProcessName() + "]尚未完成,不可进站");
        }
    }

    /**
     * SOP 版本校验: 工序须绑定已发布(PUBLISHED)的 SOP
     */
    private void checkSopVersion(ProStepCheckRule rule, ProFlowCardStep step, ProCheckResultVo result) {
        SopDocumentVo sop = sopBindingService.getSopForStep(step.getStepRecordId());
        if (sop == null || !SOP_PUBLISHED.equals(sop.getStatus())) {
            addFailure(result, TYPE_SOP_VERSION, resolveMsg(rule, "工序未绑定已发布的SOP文档"));
        }
    }

    /**
     * SOP 已阅确认校验(MES-SOP-02): 操作员必须对当前发布版本产生过 SOP_VIEW 电子签名。
     * <p>此前进站只校验「工序绑了已发布 SOP」，完全不看操作人是否打开/确认过 ——
     * 不打开查看器也能开工，「操作前已阅」在合规上不可证明。现在按
     * {@code (operatorId, sopId, currentVersion)} 查 {@code sop_view_log} 且
     * {@code signature_id} 非空，缺失即拦截。</p>
     */
    private void checkSopAcknowledged(ProStepCheckRule rule, ProFlowCardStep step,
                                      ProEntryBo context, ProCheckResultVo result) {
        if (context == null || context.getOperatorId() == null) {
            addFailure(result, TYPE_SOP_ACKNOWLEDGED, resolveMsg(rule, "未提供操作员,无法确认作业指导书已阅"));
            return;
        }
        SopDocumentVo sop = sopBindingService.getSopForStep(step.getStepRecordId());
        if (sop == null || !SOP_PUBLISHED.equals(sop.getStatus())) {
            addFailure(result, TYPE_SOP_ACKNOWLEDGED,
                resolveMsg(rule, "工序未绑定已发布的SOP文档,无法确认已阅"));
            return;
        }
        String versionNo = sop.getCurrentVersion();
        if (!sopViewLogService.hasAcknowledged(sop.getSopId(), versionNo, context.getOperatorId())) {
            addFailure(result, TYPE_SOP_ACKNOWLEDGED, resolveMsg(rule,
                "未确认作业指导书" + (versionNo == null ? "" : " V" + versionNo)
                    + ", 请先打开SOP查看器并完成「已阅确认」"));
        }
    }

    /**
     * 工装可用性校验: 上下文 toolingId 非空时校验工装可用; <b>为空时拒绝</b>(MES-TG-02)。
     * <p>规则既然配了 TOOLING_AVAILABLE，就说明该工序要求工装受控。此前为空直接
     * {@code return}（静默放行），门禁可被「不传 toolingId」整体绕过。</p>
     */
    private void checkToolingAvailable(ProStepCheckRule rule, ProEntryBo context, ProCheckResultVo result) {
        if (context == null || context.getToolingId() == null) {
            addFailure(result, TYPE_TOOLING_AVAILABLE, resolveMsg(rule,
                "未提供工装信息(该工序要求校验工装可用性), 请扫码/选择工装后重试"));
            return;
        }
        Map<String, Object> ret = tgToolingService.checkToolingAvailable(context.getToolingId());
        if (ret == null || !Boolean.TRUE.equals(ret.get("usable"))) {
            String reason = ret != null ? String.valueOf(ret.get("reason")) : "工装不可用";
            addFailure(result, TYPE_TOOLING_AVAILABLE, resolveMsg(rule, "工装不可用: " + reason));
        }
    }

    /**
     * 批次可用性校验: 上下文 lotId 非空时校验批次可用;为空则跳过
     */
    private void checkLotAvailable(ProStepCheckRule rule, ProFlowCardStep step, ProEntryBo context, ProCheckResultVo result) {
        if (context == null || context.getLotId() == null) {
            return;
        }
        Long materialId = context.getMaterialId();
        if (materialId == null) {
            // 无物料上下文时无法校验物料匹配,跳过
            return;
        }
        Map<String, Object> ret = ltLotService.checkLotUsable(materialId, context.getLotId());
        if (ret == null || !Boolean.TRUE.equals(ret.get("usable"))) {
            String reason = ret != null ? String.valueOf(ret.get("reason")) : "批次不可用";
            addFailure(result, TYPE_LOT_AVAILABLE, resolveMsg(rule, "批次不可用: " + reason));
        }
    }

    /**
     * 设备状态校验: 工位关联设备若存在 FAULT 则阻断;无设备则 PASS
     */
    private void checkEquipmentStatus(ProStepCheckRule rule, ProFlowCardStep step, ProCheckResultVo result) {
        if (step.getWorkcenterId() == null) {
            return;
        }
        Long faultCount = eqEquipmentMapper.selectCount(Wrappers.<EqEquipment>lambdaQuery()
            .eq(EqEquipment::getWorkcenterId, step.getWorkcenterId())
            .eq(EqEquipment::getStatus, EQ_FAULT));
        if (faultCount != null && faultCount > 0) {
            addFailure(result, TYPE_EQUIPMENT_STATUS, resolveMsg(rule, "工位存在故障设备,不可进站"));
        }
    }

    // ==================== EXIT 规则处理器 ====================

    private void dispatchExit(ProStepCheckRule rule, ProFlowCardStep step, ProExitBo context, ProCheckResultVo result) {
        switch (rule.getCheckType()) {
            case TYPE_QUANTITY_REACHED:
                checkQuantityReached(rule, step, context, result);
                break;
            case TYPE_INSPECTION_REQUIRED:
                checkInspectionRequired(rule, step, result);
                break;
            case TYPE_EXCEPTION_CLOSED:
                checkExceptionClosed(rule, step, result);
                break;
            case TYPE_PARAMETER_OK:
                // 存根: 默认禁用, 启用时返回 PASS 并告警(待 dc 参数达标算法落地)
                log.warn("[出站校验] PARAMETER_OK 规则为存根实现,默认PASS: processId={}", step.getProcessId());
                break;
            default:
                log.warn("[出站校验] 未知的 EXIT 校验类型,跳过: {}", rule.getCheckType());
        }
    }

    /**
     * 数量达标校验: goodQty + defectQty + scrapQty 须等于 plannedQty(投入=产出)
     */
    private void checkQuantityReached(ProStepCheckRule rule, ProFlowCardStep step, ProExitBo context, ProCheckResultVo result) {
        if (context == null || context.getGoodQty() == null) {
            addFailure(result, TYPE_QUANTITY_REACHED, resolveMsg(rule, "完工数量未填写"));
            return;
        }
        BigDecimal planned = nz(step.getPlannedQty());
        BigDecimal good = nz(context.getGoodQty());
        BigDecimal defect = nz(context.getDefectQty());
        BigDecimal scrap = nz(context.getScrapQty());
        BigDecimal total = good.add(defect).add(scrap);
        if (total.compareTo(planned) != 0) {
            addFailure(result, TYPE_QUANTITY_REACHED, resolveMsg(rule,
                String.format("数量不达标: 良品+不良+报废=%s, 计划=%s", total.stripTrailingZeros().toPlainString(),
                    planned.stripTrailingZeros().toPlainString())));
        }
    }

    /**
     * 检验完成校验: 关键工序须有 IPQC 且 inspectResult=QUALIFIED;非关键工序 PASS
     */
    private void checkInspectionRequired(ProStepCheckRule rule, ProFlowCardStep step, ProCheckResultVo result) {
        if (!Integer.valueOf(1).equals(step.getIsKeyProcess())) {
            return;
        }
        QcIpqc ipqc = qcIpqcMapper.selectOne(Wrappers.<QcIpqc>lambdaQuery()
            .eq(QcIpqc::getStepRecordId, step.getStepRecordId())
            .last("LIMIT 1"));
        if (ipqc == null || !IPQC_QUALIFIED.equals(ipqc.getInspectResult())) {
            addFailure(result, TYPE_INSPECTION_REQUIRED, resolveMsg(rule, "关键工序未完成过程检验(IPQC)或检验未通过"));
        }
    }

    /**
     * 异常已关闭校验: 本工序须无活跃安灯(CALLED/RESPONDED)
     */
    private void checkExceptionClosed(ProStepCheckRule rule, ProFlowCardStep step, ProCheckResultVo result) {
        Long activeCount = andonMapper.selectCount(Wrappers.<ProAndon>lambdaQuery()
            .eq(ProAndon::getStepRecordId, step.getStepRecordId())
            .in(ProAndon::getAndonStatus, ANDON_CALLED, ANDON_RESPONDED));
        if (activeCount != null && activeCount > 0) {
            addFailure(result, TYPE_EXCEPTION_CLOSED, resolveMsg(rule, "本工序存在未解决的安灯异常,不可出站"));
        }
    }

    // ==================== 辅助方法 ====================

    private ProFlowCardStep loadStep(Long stepRecordId) {
        ProFlowCardStep step = stepMapper.selectById(stepRecordId);
        if (step == null) {
            throw new RuntimeException("工序记录不存在: " + stepRecordId);
        }
        return step;
    }

    private List<ProStepCheckRule> listEnabledRules(Long processId, String stage) {
        return ruleMapper.selectList(Wrappers.<ProStepCheckRule>lambdaQuery()
            .eq(ProStepCheckRule::getProcessId, processId)
            .eq(ProStepCheckRule::getCheckStage, stage)
            .eq(ProStepCheckRule::getEnabled, ENABLED)
            .orderByAsc(ProStepCheckRule::getSortOrder));
    }

    private void addFailure(ProCheckResultVo result, String checkType, String errorMsg) {
        result.getFailures().add(new ProCheckResultVo.CheckFailure(checkType, errorMsg));
    }

    /**
     * 解析失败提示: 优先取规则配置的 error_msg,为空则用默认消息
     */
    private String resolveMsg(ProStepCheckRule rule, String defaultMsg) {
        return (rule.getErrorMsg() != null && !rule.getErrorMsg().isBlank()) ? rule.getErrorMsg() : defaultMsg;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

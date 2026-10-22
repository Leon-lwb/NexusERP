package com.nexus.mes.pro.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.lt.service.ILtLotService;
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MES 工序进站/出站校验规则引擎 单元测试(P0-7, 测试 P0-3 新增逻辑)
 * <p>覆盖 9 种校验类型处理器 + ENTRY/EXIT 编排 + SERIAL_GATE 内置门禁。</p>
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class ProEntryCheckServiceImplTest {

    @Mock
    private ProFlowCardStepMapper stepMapper;
    @Mock
    private ProStepCheckRuleMapper ruleMapper;
    @Mock
    private ProAndonMapper andonMapper;
    @Mock
    private QcIpqcMapper qcIpqcMapper;
    @Mock
    private EqEquipmentMapper eqEquipmentMapper;
    @Mock
    private ISopProcessBindingService sopBindingService;
    @Mock
    private ISopViewLogService sopViewLogService;
    @Mock
    private ITgToolingService tgToolingService;
    @Mock
    private ILtLotService ltLotService;

    @InjectMocks
    private ProEntryCheckServiceImpl proEntryCheckService;

    // ==================== ENTRY: SERIAL_GATE ====================

    @Test
    void runEntryChecksPassesForFirstStepWithNoRules() {
        // 首道工序(sequenceNum=1), 无配置规则 -> 仅 SERIAL_GATE(首道跳过) -> PASS
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());

        ProCheckResultVo result = proEntryCheckService.runEntryChecks(1L, new ProEntryBo());

        assertTrue(result.getPassed());
        assertTrue(result.getFailures().isEmpty());
    }

    @Test
    void runEntryChecksFailsWhenPreviousStepNotCompleted() {
        // 非首道工序, 上一工序仍 IN_PROGRESS -> SERIAL_GATE 拦截
        ProFlowCardStep step = buildStep(2L, 100L, 2, 200L);
        ProFlowCardStep prev = buildStep(1L, 100L, 1, 200L);
        prev.setStepStatus(IProFlowCardService.STEP_IN_PROGRESS);
        when(stepMapper.selectById(2L)).thenReturn(step);
        when(stepMapper.selectOne(any(Wrapper.class))).thenReturn(prev);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());

        ProCheckResultVo result = proEntryCheckService.runEntryChecks(2L, new ProEntryBo());

        assertFalse(result.getPassed());
        assertTrue(result.getFailures().stream().anyMatch(f -> f.getCheckType().equals(IProEntryCheckService.TYPE_SERIAL_GATE)));
    }

    @Test
    void runEntryChecksPassesWhenPreviousStepCompleted() {
        ProFlowCardStep step = buildStep(2L, 100L, 2, 200L);
        ProFlowCardStep prev = buildStep(1L, 100L, 1, 200L);
        prev.setStepStatus(IProFlowCardService.STEP_COMPLETED);
        when(stepMapper.selectById(2L)).thenReturn(step);
        when(stepMapper.selectOne(any(Wrapper.class))).thenReturn(prev);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());

        ProCheckResultVo result = proEntryCheckService.runEntryChecks(2L, new ProEntryBo());

        assertTrue(result.getPassed());
    }

    // ==================== ENTRY: SOP_VERSION ====================

    @Test
    void runEntryChecksFailsWhenSopUnpublished() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_SOP_VERSION, IProEntryCheckService.STAGE_ENTRY)));
        when(sopBindingService.getSopForStep(1L)).thenReturn(null); // 无 SOP

        ProCheckResultVo result = proEntryCheckService.runEntryChecks(1L, new ProEntryBo());

        assertFalse(result.getPassed());
        assertTrue(result.getFailures().stream().anyMatch(f -> f.getCheckType().equals(IProEntryCheckService.TYPE_SOP_VERSION)));
    }

    @Test
    void runEntryChecksPassesWhenSopPublished() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        SopDocumentVo sop = new SopDocumentVo();
        sop.setStatus("PUBLISHED");
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_SOP_VERSION, IProEntryCheckService.STAGE_ENTRY)));
        when(sopBindingService.getSopForStep(1L)).thenReturn(sop);

        ProCheckResultVo result = proEntryCheckService.runEntryChecks(1L, new ProEntryBo());

        assertTrue(result.getPassed());
    }

    // ==================== ENTRY: TOOLING_AVAILABLE ====================

    @Test
    void runEntryChecksFailsWhenToolingUnavailable() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        ProEntryBo bo = new ProEntryBo();
        bo.setToolingId(300L);
        Map<String, Object> ret = new HashMap<>();
        ret.put("usable", false);
        ret.put("reason", "工装使用中");
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_TOOLING_AVAILABLE, IProEntryCheckService.STAGE_ENTRY)));
        when(tgToolingService.checkToolingAvailable(300L)).thenReturn(ret);

        ProCheckResultVo result = proEntryCheckService.runEntryChecks(1L, bo);

        assertFalse(result.getPassed());
        assertTrue(result.getFailures().stream().anyMatch(f -> f.getCheckType().equals(IProEntryCheckService.TYPE_TOOLING_AVAILABLE)));
    }

    @Test
    void runEntryChecksFailsWhenNoToolingContextButRuleRequiresTooling() {
        // MES-TG-02: 规则要求工装时未传 toolingId 必须拒绝(修复前静默放行, 门禁可被"不传参数"绕过)
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_TOOLING_AVAILABLE, IProEntryCheckService.STAGE_ENTRY)));

        ProCheckResultVo result = proEntryCheckService.runEntryChecks(1L, new ProEntryBo());

        assertFalse(result.getPassed(), "未提供工装信息时必须拒绝");
        assertTrue(result.getFailures().stream()
            .anyMatch(f -> f.getCheckType().equals(IProEntryCheckService.TYPE_TOOLING_AVAILABLE)));
        verifyNoToolingCall();
    }

    @Test
    void runEntryChecksPassesToolingCheckWhenContextProvided() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        ProEntryBo bo = new ProEntryBo();
        bo.setToolingId(300L);
        Map<String, Object> ret = new HashMap<>();
        ret.put("usable", true);
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_TOOLING_AVAILABLE, IProEntryCheckService.STAGE_ENTRY)));
        when(tgToolingService.checkToolingAvailable(300L)).thenReturn(ret);

        assertTrue(proEntryCheckService.runEntryChecks(1L, bo).getPassed());
    }

    // ==================== ENTRY: SOP_ACKNOWLEDGED (MES-SOP-02) ====================

    @Test
    void runEntryChecksFailsWhenSopNotAcknowledged() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        ProEntryBo bo = new ProEntryBo();
        bo.setOperatorId(5L);
        SopDocumentVo sop = new SopDocumentVo();
        sop.setSopId(77L);
        sop.setStatus("PUBLISHED");
        sop.setCurrentVersion("1.0");
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_SOP_ACKNOWLEDGED, IProEntryCheckService.STAGE_ENTRY)));
        when(sopBindingService.getSopForStep(1L)).thenReturn(sop);
        when(sopViewLogService.hasAcknowledged(77L, "1.0", 5L)).thenReturn(false);

        ProCheckResultVo result = proEntryCheckService.runEntryChecks(1L, bo);

        assertFalse(result.getPassed(), "未确认作业指导书必须拦截进站");
        assertTrue(result.getFailures().stream()
            .anyMatch(f -> f.getCheckType().equals(IProEntryCheckService.TYPE_SOP_ACKNOWLEDGED)));
    }

    @Test
    void runEntryChecksPassesWhenSopAcknowledged() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        ProEntryBo bo = new ProEntryBo();
        bo.setOperatorId(5L);
        SopDocumentVo sop = new SopDocumentVo();
        sop.setSopId(77L);
        sop.setStatus("PUBLISHED");
        sop.setCurrentVersion("1.0");
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_SOP_ACKNOWLEDGED, IProEntryCheckService.STAGE_ENTRY)));
        when(sopBindingService.getSopForStep(1L)).thenReturn(sop);
        when(sopViewLogService.hasAcknowledged(77L, "1.0", 5L)).thenReturn(true);

        assertTrue(proEntryCheckService.runEntryChecks(1L, bo).getPassed());
    }

    @Test
    void runEntryChecksFailsAckWhenSopNotPublished() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        ProEntryBo bo = new ProEntryBo();
        bo.setOperatorId(5L);
        SopDocumentVo sop = new SopDocumentVo();
        sop.setSopId(78L);
        sop.setStatus("DRAFT");
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_SOP_ACKNOWLEDGED, IProEntryCheckService.STAGE_ENTRY)));
        when(sopBindingService.getSopForStep(1L)).thenReturn(sop);

        ProCheckResultVo result = proEntryCheckService.runEntryChecks(1L, bo);

        assertFalse(result.getPassed());
        verify(sopViewLogService, Mockito.never()).hasAcknowledged(any(), any(), any());
    }

    // ==================== ENTRY: EQUIPMENT_STATUS ====================

    @Test
    void runEntryChecksFailsWhenEquipmentFaulty() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        step.setWorkcenterId(400L);
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_EQUIPMENT_STATUS, IProEntryCheckService.STAGE_ENTRY)));
        when(eqEquipmentMapper.selectCount(any(Wrapper.class))).thenReturn(1L); // 1 台故障设备

        ProCheckResultVo result = proEntryCheckService.runEntryChecks(1L, new ProEntryBo());

        assertFalse(result.getPassed());
        assertTrue(result.getFailures().stream().anyMatch(f -> f.getCheckType().equals(IProEntryCheckService.TYPE_EQUIPMENT_STATUS)));
    }

    // ==================== EXIT: QUANTITY_REACHED ====================

    @Test
    void runExitChecksFailsWhenQuantityNotReached() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        step.setPlannedQty(new BigDecimal("50"));
        ProExitBo bo = new ProExitBo();
        bo.setGoodQty(new BigDecimal("40"));
        bo.setDefectQty(new BigDecimal("5"));
        bo.setScrapQty(new BigDecimal("0")); // 总=45 != 50
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_QUANTITY_REACHED, IProEntryCheckService.STAGE_EXIT)));

        ProCheckResultVo result = proEntryCheckService.runExitChecks(1L, bo);

        assertFalse(result.getPassed());
        assertTrue(result.getFailures().stream().anyMatch(f -> f.getCheckType().equals(IProEntryCheckService.TYPE_QUANTITY_REACHED)));
    }

    @Test
    void runExitChecksPassesWhenQuantityReached() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        step.setPlannedQty(new BigDecimal("50"));
        ProExitBo bo = new ProExitBo();
        bo.setGoodQty(new BigDecimal("48"));
        bo.setDefectQty(new BigDecimal("1"));
        bo.setScrapQty(new BigDecimal("1")); // 总=50 = 50
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_QUANTITY_REACHED, IProEntryCheckService.STAGE_EXIT)));

        ProCheckResultVo result = proEntryCheckService.runExitChecks(1L, bo);

        assertTrue(result.getPassed());
    }

    // ==================== EXIT: EXCEPTION_CLOSED ====================

    @Test
    void runExitChecksFailsWhenActiveAndonExists() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_EXCEPTION_CLOSED, IProEntryCheckService.STAGE_EXIT)));
        when(andonMapper.selectCount(any(Wrapper.class))).thenReturn(2L); // 2 个活跃安灯

        ProCheckResultVo result = proEntryCheckService.runExitChecks(1L, new ProExitBo());

        assertFalse(result.getPassed());
        assertTrue(result.getFailures().stream().anyMatch(f -> f.getCheckType().equals(IProEntryCheckService.TYPE_EXCEPTION_CLOSED)));
    }

    // ==================== EXIT: INSPECTION_REQUIRED ====================

    @Test
    void runExitChecksFailsWhenKeyProcessIpqcNotPassed() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        step.setIsKeyProcess(1);
        QcIpqc ipqc = new QcIpqc();
        ipqc.setInspectResult("PENDING"); // 未通过
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_INSPECTION_REQUIRED, IProEntryCheckService.STAGE_EXIT)));
        when(qcIpqcMapper.selectOne(any(Wrapper.class))).thenReturn(ipqc);

        ProCheckResultVo result = proEntryCheckService.runExitChecks(1L, new ProExitBo());

        assertFalse(result.getPassed());
        assertTrue(result.getFailures().stream().anyMatch(f -> f.getCheckType().equals(IProEntryCheckService.TYPE_INSPECTION_REQUIRED)));
    }

    @Test
    void runExitChecksPassesWhenKeyProcessIpqcQualified() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        step.setIsKeyProcess(1);
        QcIpqc ipqc = new QcIpqc();
        ipqc.setInspectResult("QUALIFIED");
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_INSPECTION_REQUIRED, IProEntryCheckService.STAGE_EXIT)));
        when(qcIpqcMapper.selectOne(any(Wrapper.class))).thenReturn(ipqc);

        ProCheckResultVo result = proEntryCheckService.runExitChecks(1L, new ProExitBo());

        assertTrue(result.getPassed());
    }

    @Test
    void runExitChecksSkipsInspectionWhenNotKeyProcess() {
        ProFlowCardStep step = buildStep(1L, 100L, 1, 200L);
        step.setIsKeyProcess(0); // 非关键工序
        when(stepMapper.selectById(1L)).thenReturn(step);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            rule(IProEntryCheckService.TYPE_INSPECTION_REQUIRED, IProEntryCheckService.STAGE_EXIT)));

        ProCheckResultVo result = proEntryCheckService.runExitChecks(1L, new ProExitBo());

        assertTrue(result.getPassed());
        verifyNoIpqcCall();
    }

    // ==================== 异常: 工序不存在 ====================

    @Test
    void runEntryChecksThrowsWhenStepNotFound() {
        when(stepMapper.selectById(999L)).thenReturn(null);
        assertThrows(RuntimeException.class,
            () -> proEntryCheckService.runEntryChecks(999L, new ProEntryBo()));
    }

    // ==================== fixture 辅助 ====================

    private ProFlowCardStep buildStep(Long stepRecordId, Long cardId, int sequenceNum, Long processId) {
        ProFlowCardStep step = new ProFlowCardStep();
        step.setStepRecordId(stepRecordId);
        step.setCardId(cardId);
        step.setSequenceNum(sequenceNum);
        step.setProcessId(processId);
        step.setIsKeyProcess(0);
        return step;
    }

    private ProStepCheckRule rule(String checkType, String stage) {
        ProStepCheckRule r = new ProStepCheckRule();
        r.setCheckType(checkType);
        r.setCheckStage(stage);
        r.setEnabled(IProEntryCheckService.ENABLED);
        r.setProcessId(200L);
        r.setSortOrder(0);
        return r;
    }

    private void verifyNoToolingCall() {
        Mockito.verifyNoInteractions(tgToolingService);
    }

    private void verifyNoIpqcCall() {
        Mockito.verify(qcIpqcMapper, Mockito.never()).selectOne(any(Wrapper.class));
    }
}

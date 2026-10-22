package com.nexus.mes.pro.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.flash.domain.MesFlashType;
import com.nexus.mes.flash.mapper.FlashRecordMapper;
import com.nexus.mes.flash.mapper.MesFlashTypeMapper;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import com.nexus.mes.pro.domain.bo.ProExitBo;
import com.nexus.mes.pro.domain.vo.ProCheckResultVo;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderStepMapper;
import com.nexus.mes.pro.service.IProEntryCheckService;
import com.nexus.mes.pro.service.IProWorkOrderService;
import com.nexus.mes.pro.service.ProWorkOrderStepProgressService;
import com.nexus.mes.qc.service.IQcIpqcService;
import com.nexus.mes.sop.service.ISopProcessBindingService;
import com.nexus.mes.trace.MesPreparedWorkOrderPolicies;
import com.nexus.mes.trace.MesProductLotOutputWriter;
import com.nexus.mes.trace.MesProductionDetailFactsService;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * ProFlowCardServiceImpl 卡完工聚合单测
 * <p>覆盖 MES-PRO-01(手工完工门禁 / 完工数取工序实际报工 / 一次性入账幂等 + 自愈)
 * 与 MES-PRO-07(跳站工序不参与完工数)。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class ProFlowCardServiceImplTest {

    @Mock
    private MesProductionDetailFactsService detailFacts;
    @Mock
    private ProFlowCardMapper baseMapper;
    @Mock
    private ProFlowCardStepMapper stepMapper;
    @Mock
    private ProWorkOrderMapper workOrderMapper;
    @Mock
    private ProWorkOrderStepMapper workOrderStepMapper;
    @Mock
    private LockTemplate lockTemplate;
    @Mock
    private IProWorkOrderService proWorkOrderService;
    @Mock
    private IQcIpqcService iqcIpqcService;
    @Mock
    private IProEntryCheckService proEntryCheckService;
    @Mock
    private ISopProcessBindingService sopBindingService;
    @Mock
    private INcrOrderService ncrOrderService;
    @Mock
    private ProSnMapper proSnMapper;
    @Mock
    private FlashRecordMapper flashRecordMapper;
    @Mock
    private MesFlashTypeMapper mesFlashTypeMapper;
    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ProWorkOrderStepProgressService progressService;

    @Mock
    private MesProductLotOutputWriter productLotOutputs;

    @Mock
    private MesPreparedWorkOrderPolicies preparedPolicies;
    @InjectMocks
    private ProFlowCardServiceImpl service;

    private MockedStatic<SpringUtils> aopProxyMock;
    private MockedStatic<TenantHelper> tenantMock;

    @BeforeAll
    static void initTableInfo() {
        // lambdaUpdate 立即解析实体列缓存, 纯单测须手动初始化表元数据
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProFlowCard.class);
        TableInfoHelper.initTableInfo(assistant, ProFlowCardStep.class);
        TableInfoHelper.initTableInfo(assistant, ProWorkOrder.class);
        TableInfoHelper.initTableInfo(assistant, ProSn.class);
        TableInfoHelper.initTableInfo(assistant, MesFlashType.class);
    }

    @BeforeEach
    void setUp() {
        // 纯单测无容器: SpringUtils.getAopProxy 返回当前实例(等价于经代理调用)
        tenantMock = mockStatic(TenantHelper.class);
        tenantMock.when(TenantHelper::getTenantId).thenReturn("000000");
        aopProxyMock = mockStatic(SpringUtils.class, withSettings().lenient());
        aopProxyMock.when(() -> SpringUtils.getAopProxy(any())).thenReturn(service);
        lenient()
                .when(lockTemplate.lock(anyString(), anyLong(), anyLong()))
                .thenReturn(mock(LockInfo.class));
        lenient()
                .when(progressService.lockForCard(anyLong()))
                .thenAnswer(i -> baseMapper.selectById((Long) i.getArgument(0)));
        lenient()
                .when(workOrderMapper.selectOne(any()))
                .thenAnswer(i -> workOrderMapper.selectById(100L));
        lenient()
                .when(stepMapper.selectOne(any()))
                .thenAnswer(
                        i -> {
                            var query = (LambdaQueryWrapper<ProFlowCardStep>) i.getArgument(0);
                            query.getSqlSegment();
                            Long id =
                                    query.getParamNameValuePairs().values().stream()
                                            .filter(Long.class::isInstance)
                                            .map(Long.class::cast)
                                            .findFirst()
                                            .orElse(null);
                            return id == null ? null : stepMapper.selectById(id);
                        });
        // @Value 字段纯单测不注入, 手动置为 FORCE 以便覆盖刷写门禁分支
        ReflectionTestUtils.setField(service, "flashGateMode", "FORCE");
    }

    @AfterEach
    void tearDown() {
        tenantMock.close();
        if (aopProxyMock != null) {
            aopProxyMock.close();
        }
    }

    @Test
    void exitClassifiesPhysicalProductLotsBeforeStepCanAggregateAndFreeze() {
        var bo = new ProExitBo();
        bo.setCardId(11L);
        bo.setStepRecordId(31L);
        bo.setGoodQty(BigDecimal.TEN);
        bo.setDefectQty(BigDecimal.ZERO);
        bo.setScrapQty(BigDecimal.ZERO);
        bo.setProductOutputCommandId("exit-output");
        bo.setProductOutputs(
                List.of(new MesProductLotOutputWriter.OutputLine(7L, 9L, BigDecimal.TEN, "GOOD")));
        var checks = new ProCheckResultVo();
        checks.setPassed(true);
        when(proEntryCheckService.runExitChecks(31L, bo)).thenReturn(checks);
        lenient()
                .when(
                        productLotOutputs.record(
                                eq("exit-output"),
                                eq(11L),
                                eq(31L),
                                eq(bo.getProductOutputs()),
                                eq(1L)))
                .thenThrow(new ServiceException("output registration refused"));
        try (var auth = mockStatic(LoginHelper.class)) {
            auth.when(LoginHelper::getUserId).thenReturn(1L);
            var error = assertThrows(ServiceException.class, () -> service.doExit(bo));
            assertEquals("output registration refused", error.getMessage());
        }
        var ordered = inOrder(proEntryCheckService, productLotOutputs);
        ordered.verify(proEntryCheckService).runExitChecks(31L, bo);
        ordered.verify(productLotOutputs)
                .record("exit-output", 11L, 31L, bo.getProductOutputs(), 1L);
        verify(stepMapper, never()).update(isNull(), any());
        verify(proWorkOrderService, never()).completeWo(anyLong());
    }

    // ==================== MES-PRO-01 手工完工门禁 ====================

    @Test
    void manualCompleteRejectsWhenStepsPending() {
        when(baseMapper.selectById(1L)).thenReturn(card(1L, "RELEASED", BigDecimal.ZERO));
        when(stepMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                step(1, "OP10", "COMPLETED", "10"),
                                step(2, "OP20", "PENDING", null)));

        ServiceException ex =
                assertThrows(ServiceException.class, () -> service.manualCompleteCard(1L));

        assertTrue(ex.getMessage().contains("未完成工序"), "应提示未完成工序");
        assertTrue(ex.getMessage().contains("OP20"), "应列出具体未完成工序");
        // 修复前: CAS 允许 CREATED/RELEASED 直接完工并把完工数重算为 0 → 工单永久卡死
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void aggregationPathSkipsCompletionWhenStepsPending() {
        when(baseMapper.selectById(1L)).thenReturn(card(1L, "IN_PROGRESS", BigDecimal.ZERO));
        when(stepMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                step(1, "OP10", "COMPLETED", "10"),
                                step(2, "OP20", "IN_PROGRESS", null)));

        service.completeCard(1L);

        verify(baseMapper, never()).update(any(), any());
    }

    // ==================== MES-PRO-01 自愈聚合 + 一次性入账幂等 ====================

    @Test
    void completeCardSelfHealsAggregationWhenAllStepsCompletedButCardNotCompleted() {
        // 工序全部 COMPLETED 而卡仍 IN_PROGRESS(并发竞态残留) → 再次聚合必须入账并触发工单完工
        when(baseMapper.selectById(1L)).thenReturn(card(1L, "IN_PROGRESS", BigDecimal.ZERO));
        when(stepMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                step(1, "OP10", "COMPLETED", "10"),
                                step(2, "OP20", "COMPLETED", "10")));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(workOrderMapper.selectById(100L)).thenReturn(workOrder(new BigDecimal("10")));

        service.completeCard(1L);

        // 卡完工数取末道已完工工序的实际报工数 10(而非重算为 0)
        LambdaUpdateWrapper<ProFlowCard> qtyWrapper = capturedCardQtyUpdate();
        assertTrue(qtyWrapper.getParamNameValuePairs().containsValue(new BigDecimal("10")));
        // 工单聚合: completed_qty = completed_qty + 10
        ArgumentCaptor<Wrapper> woCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(workOrderMapper).update(isNull(), woCaptor.capture());
        LambdaUpdateWrapper<?> woWrapper = (LambdaUpdateWrapper<?>) woCaptor.getValue();
        assertTrue(woWrapper.getSqlSet().contains("completed_qty = completed_qty +"));
        assertTrue(woWrapper.getParamNameValuePairs().containsValue(new BigDecimal("10")));
        // 累计达计划 → 触发工单完工(ERP 完工消息由此发出)
        verify(proWorkOrderService).completeWo(100L);
    }

    @Test
    void completeCardIsIdempotentWhenCardAlreadyBooked() {
        // 卡已 COMPLETED 且已入账(aggregated_flag=1) → 重复完工不得二次聚合
        when(baseMapper.selectById(1L)).thenReturn(card(1L, "COMPLETED", new BigDecimal("10")));
        when(stepMapper.selectList(any())).thenReturn(List.of(step(1, "OP10", "COMPLETED", "10")));
        // 调用序: ①卡状态CAS=0(已完工) ②卡数量重算=1 ③入账CAS=0(已入账)
        when(baseMapper.update(any(), any())).thenReturn(0, 1, 0);

        service.completeCard(1L);

        verify(workOrderMapper, never()).update(any(), any());
        verify(proWorkOrderService, never()).completeWo(any());
    }

    @Test
    void bookedCardStillRebuildsStepProjectionWithoutRepeatingWorkOrderQuantity() {
        when(baseMapper.selectById(1L)).thenReturn(card(1L, "COMPLETED", new BigDecimal("10")));
        when(stepMapper.selectList(any())).thenReturn(List.of(step(1, "OP10", "COMPLETED", "10")));
        when(baseMapper.update(any(), any())).thenReturn(0, 1, 0);

        service.completeCard(1L);

        // 已入账不能提前绕过步骤投影，但仍不得再次增加工单头或发送完工事件。
        verify(progressService).rebuildForCard(1L);
        verify(workOrderMapper, never()).update(any(), any());
        verify(proWorkOrderService, never()).completeWo(any());
    }

    @Test
    void completeCardSelfHealsWhenCardCompletedButNeverBooked() {
        // 自愈: 卡已 COMPLETED 但 aggregated_flag=0(历史上被手工提前完工, 数量从未入账) → 重入补入账
        when(baseMapper.selectById(1L)).thenReturn(card(1L, "COMPLETED", BigDecimal.ZERO));
        when(stepMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                step(1, "OP10", "COMPLETED", "10"),
                                step(2, "OP20", "COMPLETED", "10")));
        // 调用序: ①卡状态CAS=0(已完工) ②卡数量重算=1 ③入账CAS=1(本次抢到入账权)
        when(baseMapper.update(any(), any())).thenReturn(0, 1, 1);
        when(workOrderMapper.selectById(100L)).thenReturn(workOrder(new BigDecimal("10")));

        service.completeCard(1L);

        verify(workOrderMapper).update(isNull(), any());
        verify(proWorkOrderService).completeWo(100L);
    }

    // ==================== MES-PRO-07 跳站工序不参与完工数 ====================

    @Test
    void completedQtyIgnoresSkippedLastStep() {
        when(baseMapper.selectById(1L)).thenReturn(card(1L, "IN_PROGRESS", BigDecimal.ZERO));
        when(stepMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                step(1, "OP10", "COMPLETED", "10"),
                                step(2, "OP20", "SKIPPED", null)));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(workOrderMapper.selectById(100L)).thenReturn(workOrder(new BigDecimal("10")));

        service.completeCard(1L);

        // 修复前: 取 sequenceNum 最大的工序(SKIPPED, good_qty=NULL) → 完工数被算成 0
        LambdaUpdateWrapper<ProFlowCard> qtyWrapper = capturedCardQtyUpdate();
        assertTrue(qtyWrapper.getParamNameValuePairs().containsValue(new BigDecimal("10")));
    }

    // ==================== 刷写门禁 gate_enabled 过滤 ====================

    /**
     * 刷写类型 gate_enabled='0'(不参与完工门禁)时, 门禁不得拦截: 绑定项被过滤后
     * 不再查询刷写记录, 工序完工继续执行(CAS 0 行则提前返回)。
     */
    @Test
    void checkFlashStepGate_skipsGateDisabledType() {
        when(stepMapper.selectById(1L)).thenReturn(flashStep(1L, 1L, "OP10"));
        when(workOrderStepMapper.selectById(1L)).thenReturn(woStepWithFlashContent(1L, 9L));
        when(mesFlashTypeMapper.selectList(any()))
                .thenReturn(List.of(flashType("firmware_file", "0")));
        when(stepMapper.update(any(), any())).thenReturn(0);

        assertDoesNotThrow(() -> service.checkAndCompleteStepUnderLock(1L, 1L));

        // 修复前: 无 gate_enabled 过滤 → 该类型无 PASS 记录会抛"刷写门禁未通过"
        verify(flashRecordMapper, never()).selectOne(any());
    }

    /**
     * 反向对照: gate_enabled='1' 时同场景必须拦截(证明上面的放行来自过滤而非门禁失效)。
     */
    @Test
    void checkFlashStepGate_blocksWhenGateEnabledTypeHasNoPassRecord() {
        when(stepMapper.selectById(1L)).thenReturn(flashStep(1L, 1L, "OP10"));
        when(workOrderStepMapper.selectById(1L)).thenReturn(woStepWithFlashContent(1L, 9L));
        when(mesFlashTypeMapper.selectList(any()))
                .thenReturn(List.of(flashType("firmware_file", "1")));
        when(proSnMapper.selectList(any())).thenReturn(List.of(sn(7L, "SN-1", 1L)));
        when(flashRecordMapper.selectOne(any())).thenReturn(null);

        ServiceException ex =
                assertThrows(
                        ServiceException.class,
                        () -> service.checkAndCompleteStepUnderLock(1L, 1L));

        assertTrue(ex.getMessage().contains("刷写门禁未通过"), "应拦截未刷写工序: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("SN-1"), "应列出未通过 SN: " + ex.getMessage());
    }

    // ==================== 测试辅助 ====================

    private ProFlowCardStep flashStep(Long stepRecordId, Long woStepId, String processName) {
        ProFlowCardStep s = new ProFlowCardStep();
        s.setStepRecordId(stepRecordId);
        s.setCardId(1L);
        s.setTenantId("000000");
        s.setWoStepId(woStepId);
        s.setProcessName(processName);
        s.setStepStatus("IN_PROGRESS");
        return s;
    }

    /**
     * 单类型刷写绑定(content_ref_id 路径, 免 JSON 解析): flashTypeCode 固定 firmware_file
     */
    private ProWorkOrderStep woStepWithFlashContent(Long stepId, Long contentRefId) {
        ProWorkOrderStep ws = new ProWorkOrderStep();
        ws.setStepId(stepId);
        ws.setContentRefId(contentRefId);
        return ws;
    }

    private MesFlashType flashType(String typeCode, String gateEnabled) {
        MesFlashType t = new MesFlashType();
        t.setTypeCode(typeCode);
        t.setGateEnabled(gateEnabled);
        return t;
    }

    private ProSn sn(Long snId, String snCode, Long cardId) {
        ProSn s = new ProSn();
        s.setSnId(snId);
        s.setSnCode(snCode);
        s.setCardId(cardId);
        return s;
    }

    /**
     * 取"卡数量重算"那一次 update(区别于状态 CAS 与入账 CAS)
     */
    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<ProFlowCard> capturedCardQtyUpdate() {
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper, atLeastOnce()).update(isNull(), captor.capture());
        List<Wrapper> matched = new ArrayList<>();
        for (Wrapper w : captor.getAllValues()) {
            LambdaUpdateWrapper<ProFlowCard> lw = (LambdaUpdateWrapper<ProFlowCard>) w;
            if (lw.getSqlSet() != null && lw.getSqlSet().contains("scrap_qty=")) {
                matched.add(w);
            }
        }
        assertEquals(1, matched.size(), "应恰好一次卡完工数量重算");
        return (LambdaUpdateWrapper<ProFlowCard>) matched.get(0);
    }

    private ProFlowCard card(Long cardId, String status, BigDecimal completedQty) {
        ProFlowCard card = new ProFlowCard();
        card.setCardId(cardId);
        card.setMesWoId(100L);
        card.setCardStatus(status);
        card.setPlannedQty(new BigDecimal("10"));
        card.setCompletedQty(completedQty);
        card.setScrapQty(BigDecimal.ZERO);
        card.setAggregatedFlag(0);
        return card;
    }

    private ProFlowCardStep step(int seq, String processName, String status, String goodQty) {
        ProFlowCardStep s = new ProFlowCardStep();
        s.setStepRecordId((long) seq);
        s.setCardId(1L);
        s.setTenantId("000000");
        s.setSequenceNum(seq);
        s.setProcessName(processName);
        s.setStepStatus(status);
        s.setGoodQty(goodQty == null ? null : new BigDecimal(goodQty));
        s.setScrapQty(BigDecimal.ZERO);
        return s;
    }

    private ProWorkOrder workOrder(BigDecimal plannedQty) {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(100L);
        wo.setPlannedQty(plannedQty);
        wo.setCompletedQty(BigDecimal.ZERO);
        wo.setScrapQty(BigDecimal.ZERO);
        return wo;
    }
}

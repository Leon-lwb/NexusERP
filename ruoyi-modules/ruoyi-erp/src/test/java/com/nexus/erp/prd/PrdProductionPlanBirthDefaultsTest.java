package com.nexus.erp.prd;

import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.domain.bo.PrdProductionPlanBo;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.service.impl.PrdProductionPlanServiceImpl;
import com.nexus.erp.testsupport.SpringStaticBootstrap;
import com.nexus.system.strategy.AutoCodeUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 生产计划新建初始值回归测试，只模拟持久化与编号，不连接外部服务。
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PrdProductionPlanBirthDefaultsTest {

    @Mock
    private PrdProductionPlanMapper baseMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;

    private PrdProductionPlanServiceImpl productionPlanService;

    @BeforeAll
    static void initializeStaticConversion() {
        // 沿用模块的同名属性转换引导，不启动应用或连接数据库、缓存、消息队列。
        SpringStaticBootstrap.ensureActive();
    }

    @BeforeEach
    void setUp() {
        productionPlanService = new PrdProductionPlanServiceImpl(
            baseMapper, null, null, null, null, null, null, null, null, autoCodeUtil, null, null);
    }

    @DisplayName("未提供或空白状态的新计划由服务端补齐草稿及初始版本")
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n"})
    void insertByBo_blankStatusInitializesBirthDefaults(String status) {
        PrdProductionPlanBo bo = newPlan();
        bo.setStatus(status);

        PrdProductionPlan inserted = insertAndCapture(bo);

        assertAll(
            () -> assertEquals("DRAFT", inserted.getStatus(), "新计划只能以草稿状态入库"),
            () -> assertEquals(1L, inserted.getVersion(), "缺失版本号应初始化为一"),
            () -> assertEquals(1L, inserted.getIsCurrent(), "缺失当前版本标记应初始化为一"),
            () -> assertEquals("SCJH-TEST-0001", inserted.getPlanNo(), "缺失编号仍沿用自动编号"),
            () -> assertEquals(status, bo.getStatus(), "初始化不应改写请求对象的状态"),
            () -> assertNull(bo.getVersion(), "初始化不应改写请求对象的版本号"),
            () -> assertNull(bo.getIsCurrent(), "初始化不应改写请求对象的当前版本标记")
        );
        verify(autoCodeUtil).genSerialCode("PRD_PLAN_CODE", null);
    }

    @Test
    @DisplayName("明确提供草稿状态时保留已有编号与业务字段")
    void insertByBo_explicitDraftPreservesPlanFields() {
        PrdProductionPlanBo bo = newPlan();
        bo.setPlanNo("SCJH-CLIENT-0001");
        bo.setStatus("DRAFT");
        bo.setVersion(1L);
        bo.setIsCurrent(1L);

        PrdProductionPlan inserted = insertAndCapture(bo);

        assertAll(
            () -> assertEquals("DRAFT", inserted.getStatus(), "明确的草稿状态应允许新建"),
            () -> assertEquals(bo.getPlanNo(), inserted.getPlanNo(), "已有计划编号不应被替换"),
            () -> assertEquals(bo.getPlanName(), inserted.getPlanName(), "计划名称不应被默认值逻辑改写"),
            () -> assertEquals(bo.getBomId(), inserted.getBomId(), "所选物料清单不应被默认值逻辑改写"),
            () -> assertEquals(bo.getTotalQuantity(), inserted.getTotalQuantity(), "计划数量不应被默认值逻辑改写")
        );
        verifyNoInteractions(autoCodeUtil);
    }

    @DisplayName("非草稿状态必须在编号生成与插入前拒绝")
    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMED", "RELEASED", "COMPLETED", "CLOSED", "CANCELLED", "draft", "UNKNOWN", " DRAFT "})
    void insertByBo_nonDraftStatusIsRejectedWithoutSideEffects(String status) {
        PrdProductionPlanBo bo = newPlan();
        bo.setStatus(status);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> productionPlanService.insertByBo(bo), "客户端不得绕过草稿状态创建计划");

        assertEquals("新增生产计划只能为草稿状态", exception.getMessage(), "非法初始状态应给出明确中文错误");
        verifyNoInteractions(baseMapper, autoCodeUtil);
    }

    @Test
    @DisplayName("仅缺失版本号时补齐初始版本一")
    void insertByBo_missingVersionInitializesOne() {
        PrdProductionPlanBo bo = newPlan();
        bo.setStatus("DRAFT");
        bo.setIsCurrent(1L);

        PrdProductionPlan inserted = insertAndCapture(bo);

        assertEquals(1L, inserted.getVersion(), "缺失版本号必须由服务端显式补齐为一");
    }

    @Test
    @DisplayName("仅缺失当前版本标记时补齐一而不依赖表默认零")
    void insertByBo_missingCurrentFlagInitializesOne() {
        PrdProductionPlanBo bo = newPlan();
        bo.setStatus("DRAFT");
        bo.setVersion(1L);

        PrdProductionPlan inserted = insertAndCapture(bo);

        assertEquals(1L, inserted.getIsCurrent(), "缺失当前版本标记必须由服务端补齐为一");
    }

    @DisplayName("本次仅补缺失默认值不覆盖显式版本字段")
    @ParameterizedTest
    @CsvSource({"0, 0", "4, 1"})
    void insertByBo_explicitVersionFieldsAreNotReset(long version, long isCurrent) {
        PrdProductionPlanBo bo = newPlan();
        bo.setStatus("DRAFT");
        bo.setVersion(version);
        bo.setIsCurrent(isCurrent);

        PrdProductionPlan inserted = insertAndCapture(bo);

        assertAll(
            () -> assertEquals(version, inserted.getVersion(), "已有版本号不应被缺失默认值策略覆盖"),
            () -> assertEquals(isCurrent, inserted.getIsCurrent(), "已有当前版本标记不应被缺失默认值策略覆盖")
        );
    }

    @DisplayName("空白编号仍沿用现有自动编号逻辑")
    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void insertByBo_blankPlanNumberStillUsesAutoCode(String planNo) {
        PrdProductionPlanBo bo = newPlan();
        bo.setPlanNo(planNo);
        bo.setStatus("DRAFT");

        PrdProductionPlan inserted = insertAndCapture(bo);

        assertEquals("SCJH-TEST-0001", inserted.getPlanNo(), "空白计划编号应使用原编号规则生成");
        verify(autoCodeUtil).genSerialCode("PRD_PLAN_CODE", null);
    }

    @Test
    @DisplayName("普通修改入口不套用新建初始状态与版本默认值")
    void updateByBo_birthDefaultsDoNotChangeUpdatePolicy() {
        PrdProductionPlanBo bo = newPlan();
        bo.setPlanId(1001L);
        bo.setStatus("CONFIRMED");
        when(baseMapper.updateById(any(PrdProductionPlan.class))).thenReturn(1);

        assertEquals(1, productionPlanService.updateByBo(bo), "本次修复不应改变普通修改入口的返回值");

        ArgumentCaptor<PrdProductionPlan> captor = ArgumentCaptor.forClass(PrdProductionPlan.class);
        verify(baseMapper).updateById(captor.capture());
        PrdProductionPlan updated = captor.getValue();
        assertAll(
            () -> assertEquals("CONFIRMED", updated.getStatus(), "新建状态限制不应扩散到普通修改入口"),
            () -> assertNull(updated.getVersion(), "普通修改入口不应补新建版本默认值"),
            () -> assertNull(updated.getIsCurrent(), "普通修改入口不应补新建当前版本标记")
        );
        verifyNoInteractions(autoCodeUtil);
    }

    private PrdProductionPlan insertAndCapture(PrdProductionPlanBo bo) {
        if (bo.getPlanNo() == null || bo.getPlanNo().isBlank()) {
            when(autoCodeUtil.genSerialCode("PRD_PLAN_CODE", null)).thenReturn("SCJH-TEST-0001");
        }
        when(baseMapper.insert(any(PrdProductionPlan.class))).thenReturn(1);

        assertEquals(1, productionPlanService.insertByBo(bo), "正常新建应返回原持久化影响行数");

        ArgumentCaptor<PrdProductionPlan> captor = ArgumentCaptor.forClass(PrdProductionPlan.class);
        verify(baseMapper).insert(captor.capture());
        return captor.getValue();
    }

    private PrdProductionPlanBo newPlan() {
        PrdProductionPlanBo bo = new PrdProductionPlanBo();
        bo.setPlanName("正常生产计划");
        bo.setBomId(4001L);
        bo.setTotalQuantity(new BigDecimal("6.0000"));
        return bo;
    }
}
